INSERT IGNORE INTO grade (grade_name, min, max, point_rate)
VALUES ('GENERAL', 0, 100000, 0.01);

INSERT IGNORE INTO grade (grade_name, min, max, point_rate)
VALUES ('ROYAL', 100000, 300000, 0.03);

INSERT IGNORE INTO grade (grade_name, min, max, point_rate)
VALUES ('GOLD', 300000, 500000, 0.05);

INSERT IGNORE INTO grade (grade_name, min, max, point_rate)
VALUES ('PLATINUM', 500000, NULL, 0.10);