INSERT IGNORE INTO grade (grade_name, min, max, point_rate)
VALUES ('GENERAL', 0, 100000, 0.01);

INSERT IGNORE INTO grade (grade_name, min, max, point_rate)
VALUES ('ROYAL', 100000, 300000, 0.03);

INSERT IGNORE INTO grade (grade_name, min, max, point_rate)
VALUES ('GOLD', 300000, 500000, 0.05);

INSERT IGNORE INTO grade (grade_name, min, max, point_rate)
VALUES ('PLATINUM', 500000, NULL, 0.10);

INSERT IGNORE INTO member (member_id, login_id, name, password, phone, email, birth_date, current_point, grade_id, role, status, last_login_at)
VALUES (1, 'test_user', '테스트', '1234', '010-1234', 'test@test.com', '2000-01-01', 10000, 1, 'USER', 'ACTIVE', NOW());

INSERT IGNORE INTO point_policy (id, signup_point, review_point, photo_point, updated_at)
VALUES (1, 5000, 200, 500, NOW());