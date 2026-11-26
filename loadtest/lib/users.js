// 부하 테스트 유저 토큰 준비: 로그인해 보고, 없으면 가입한다 (유저는 실행 간에 재사용)
import http from 'k6/http';

const JSON_HEADERS = { headers: { 'Content-Type': 'application/json' } };

export function prepareTokens(baseUrl, count) {
  const tokens = [];
  for (let i = 1; i <= count; i++) {
    const email = `lt-user-${i}@test.com`;
    const password = 'loadtest1234';

    let res = http.post(`${baseUrl}/api/v1/auth/sign-in`, JSON.stringify({ email, password }), JSON_HEADERS);
    if (res.status !== 200) {
      res = http.post(`${baseUrl}/api/v1/auth/sign-up`,
        JSON.stringify({ email, password, nickname: `lt${i}`, name: '부하테스트' }), JSON_HEADERS);
    }
    if (res.status !== 200 && res.status !== 201) {
      throw new Error(`유저 준비 실패 ${email}: ${res.status} ${res.body}`);
    }
    tokens.push(res.json('accessToken'));
  }
  return tokens;
}

// 오늘로부터 n일 뒤 날짜 (YYYY-MM-DD)
export function dateAfter(days) {
  const d = new Date();
  d.setDate(d.getDate() + days);
  return d.toISOString().slice(0, 10);
}
