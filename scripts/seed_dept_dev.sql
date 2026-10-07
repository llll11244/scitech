-- dev 环境院部种子(Task22 联调用;sys_dept 无 data.sql 种子,sys_dept 表也无 status 列)
-- 与 dev 库现状一致:10 个院部 id 9-18(前 4 个联调建,后 6 个用户手工补)
-- 幂等口径:显式 id 先删后插,重复执行结果不变
-- 用法:mysql -uroot -p --default-character-set=utf8mb4 < seed_dept_dev.sql
DELETE FROM scitech_db.sys_dept WHERE id BETWEEN 9 AND 18;
INSERT INTO scitech_db.sys_dept (id, parent_id, name, sort_order, create_by) VALUES
(9,  0, '智能制造与装备学院', 1,  'admin'),
(10, 0, '纺织与材料工程学院', 2,  'admin'),
(11, 0, '信息工程学院', 3,  'admin'),
(12, 0, '经济管理学院', 4,  'admin'),
(13, 0, '数字经济学院', 5,  'admin'),
(14, 0, '建筑工程学院', 6,  'admin'),
(15, 0, '艺术设计学院', 7,  'admin'),
(16, 0, '外语与文化旅游学院', 8,  'admin'),
(17, 0, '汽车与交通学院', 9,  'admin'),
(18, 0, '食品与药品学院', 10, 'admin');
