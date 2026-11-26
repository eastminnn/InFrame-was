-- 실행 후 검증: 같은 체험, 같은 시작 시간에 유효한(취소되지 않은) 예약이 2건 이상인 시간대
SELECT COUNT(*)                         AS reservations,
       COUNT(DISTINCT reserved_start_time) AS slots,
       COUNT(*) - COUNT(DISTINCT reserved_start_time) AS duplicated_reservations
FROM reservation
WHERE experience_id = 900001 AND status <> 'CANCELLED';
