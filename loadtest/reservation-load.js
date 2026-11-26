// 시나리오 B: 한 체험에 예약이 일정한 속도로 계속 들어옴 → 락 비용(응답시간, 커넥션 대기) 확인
// 같은 체험의 30일 × 10타임 중 무작위 시간대를 예약한다
import http from 'k6/http';
import { check } from 'k6';
import { Counter } from 'k6/metrics';
import { prepareTokens, dateAfter } from './lib/users.js';

http.setResponseCallback(http.expectedStatuses(200, 201, 409)); // 409(이미 예약됨)도 정상 응답

const BASE_URL = __ENV.BASE_URL || 'http://localhost:8081';
const EXPERIENCE_ID = Number(__ENV.EXPERIENCE_ID || 900001);
const RATE = Number(__ENV.RATE || 100);          // 초당 요청 수
const DURATION = __ENV.DURATION || '60s';
const USERS = 50;
const TIMES = ['09:00', '10:00', '11:00', '12:00', '13:00', '14:00', '15:00', '16:00', '17:00', '18:00'];

const created = new Counter('reservation_created');
const slotTaken = new Counter('reservation_slot_taken');
const failed = new Counter('reservation_failed');

export const options = {
  setupTimeout: '120s',
  thresholds: {
    'http_req_duration{name:reservation}': ['max>=0'], // 요약에 예약 요청만의 응답시간 표시용
    'http_reqs{name:reservation}': ['count>=0'],
  },
  scenarios: {
    load: {
      executor: 'constant-arrival-rate', // 서버가 느려져도 같은 속도로 보낸다 (전후 비교용)
      rate: RATE,
      timeUnit: '1s',
      duration: DURATION,
      preAllocatedVUs: 100,
      maxVUs: 500,
    },
  },
};

export function setup() {
  return { tokens: prepareTokens(BASE_URL, USERS) };
}

export default function (data) {
  const token = data.tokens[Math.floor(Math.random() * data.tokens.length)];
  const date = dateAfter(1 + Math.floor(Math.random() * 30));
  const startTime = TIMES[Math.floor(Math.random() * TIMES.length)];

  const res = http.post(`${BASE_URL}/api/v1/reservation`,
    JSON.stringify({ experienceId: EXPERIENCE_ID, reservationDate: date, startTime, numAdults: 2, numChildren: 0 }),
    { headers: { 'Content-Type': 'application/json', Authorization: `Bearer ${token}` }, tags: { name: 'reservation' } });

  if (res.status === 201) created.add(1);
  else if (res.status === 409) slotTaken.add(1);
  else failed.add(1);

  check(res, { '201 또는 409': (r) => r.status === 201 || r.status === 409 });
}
