# 부하 테스트

## 준비
```bash
docker compose up -d                       # MySQL, pgvector, Prometheus, Grafana
SPRING_PROFILES_ACTIVE=local bash gradlew bootRun   # 앱 (8081)
```

## 실행
```bash
bash loadtest/run.sh loadtest/smoke.js [testid]
```
`testid`를 생략하면 `스크립트이름-시분초`로 붙는다. Grafana에서 실행별로 구분하는 값이다.

## 결과 보기
- Grafana: http://localhost:3001 → InFrame 폴더
  - `k6 부하 테스트`: 요청 수, 응답시간 p95/p99, 실패율
  - `InFrame 서버`: 서버 응답시간, DB 커넥션 사용·대기, 톰캣 스레드, 힙
- Prometheus: http://localhost:9090

| 스크립트 | 내용 |
|---|---|
| `smoke.js` | 측정 환경 확인용. 공개 조회 API 2개에 VU 5, 30초 |
