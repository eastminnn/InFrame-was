-- 예약 부하 테스트용 호스트와 체험 (id 고정: 900001~)
-- 실행: docker exec -i inframe-mysql mysql -uinframe -pinframe inframe < loadtest/sql/seed.sql
-- 호스트 등록 API 는 외부 사업자번호 검증을 거치므로 SQL 로 직접 넣는다

INSERT IGNORE INTO user (id, email, password, nickname, name, role)
VALUES (900001, 'loadtest-host@test.com', 'not-used', 'lt-host', '부하테스트호스트', 'HOST');

INSERT IGNORE INTO host (id, user_id, business_number, category, business_name, business_phone_number,
                         business_email, kakao_address, address_base, latitude, longitude)
VALUES (900001, 900001, '9000000001', 'MASTER_ARTISAN', '부하테스트 공방', '010-0000-0000',
        'lt@test.com', 'http://pf.kakao.com/test', '경산시 대학로 1', 35.82, 128.74);

-- 체험 1개: 매일 09:00 ~ 18:00 정각마다 예약 가능 (하루 10타임)
INSERT IGNORE INTO experience (id, host_id, title, description, professional_field, detail_field,
                               price, duration_in_hours, max_capacity_per_slot)
VALUES (900001, 900001, '부하테스트 도자기 체험', '부하 테스트용', 'CRAFT_CREATION', 'CERAMIC_CRAFT', 10000, 1, 4);

INSERT IGNORE INTO experience_available_days (experience_id, day_of_week)
VALUES (900001, 'MONDAY'), (900001, 'TUESDAY'), (900001, 'WEDNESDAY'), (900001, 'THURSDAY'),
       (900001, 'FRIDAY'), (900001, 'SATURDAY'), (900001, 'SUNDAY');

INSERT IGNORE INTO experience_available_times (experience_id, available_time)
VALUES (900001, '09:00'), (900001, '10:00'), (900001, '11:00'), (900001, '12:00'), (900001, '13:00'),
       (900001, '14:00'), (900001, '15:00'), (900001, '16:00'), (900001, '17:00'), (900001, '18:00');
