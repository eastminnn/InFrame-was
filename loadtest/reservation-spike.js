// 시나리오 A: 100명이 같은 체험, 같은 시간대를 동시에 예약 → 정합성 확인
// 기대: 201 한 건, 나머지 409
import http from 'k6/http';
import { check } from 'k6';
import { Counter } from 'k6/metrics';
import { prepareTokens, dateAfter } from './lib/users.js';

http.setResponseCallback(http.expectedStatuses(200, 201, 409)); // 409(이미 예약됨)도 정상 응답

const BASE_URL = __ENV.BASE_URL || 'http://localhost:8081';
const EXPERIENCE_ID = Number(__ENV.EXPERIENCE_ID || 900001);
const USERS = 100;

const created = new Counter('reservation_created');    // 201
const slotTaken = new Counter('reservation_slot_taken'); // 409
const failed = new Counter('reservation_failed');        // 그 밖의 응답

export const options = {
  setupTimeout: '120s',
  thresholds: {
    'http_req_duration{name:reservation}': ['max>=0'], // 요약에 예약 요청만의 응답시간 표시용
    'http_reqs{name:reservation}': ['count>=0'],
  },
  scenarios: {
    spike: {
      executor: 'per-vu-iterations', // VU 마다 정확히 1번씩 → 100건이 거의 동시에 출발
      vus: USERS,
      iterations: 1,
      maxDuration: '60s',
    },
  },
};

export function setup() {
  return { tokens: prepareTokens(BASE_URL, USERS), date: dateAfter(7) };
}

export default function (data) {
  const token = data.tokens[__VU - 1]; // VU 마다 다른 유저
  const res = http.post(`${BASE_URL}/api/v1/reservation`,
    JSON.stringify({ experienceId: EXPERIENCE_ID, reservationDate: data.date, startTime: '10:00', numAdults: 2, numChildren: 0 }),
    { headers: { 'Content-Type': 'application/json', Authorization: `Bearer ${token}` }, tags: { name: 'reservation' } });

  if (res.status === 201 || res.status === 200) created.add(1);
  else if (res.status === 409) slotTaken.add(1);
  else failed.add(1);

  check(res, { '201 또는 409': (r) => [200, 201, 409].includes(r.status) });
}
