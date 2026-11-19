// 측정 환경 동작 확인용: 공개 조회 API 에 가벼운 부하를 준다
import http from 'k6/http';
import { check, sleep } from 'k6';

const BASE_URL = __ENV.BASE_URL || 'http://localhost:8081';

export const options = {
  vus: 5,
  duration: '30s',
  thresholds: {
    http_req_failed: ['rate<0.01'],
    http_req_duration: ['p(95)<500'],
  },
};

export default function () {
  const map = http.get(`${BASE_URL}/api/v1/host/map`);
  check(map, { 'host map 200': (r) => r.status === 200 });

  const categories = http.get(`${BASE_URL}/api/v1/enums/categories`);
  check(categories, { 'categories 200': (r) => r.status === 200 });

  sleep(0.5);
}
