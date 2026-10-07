-- dev 环境院部种子(Task22 联调用;sys_dept 无 data.sql 种子,sys_dept 表也无 status 列)
-- 用法:mysql -uroot -p --default-character-set=utf8mb4 < seed_dept_dev.sql
INSERT INTO scitech_db.sys_dept (parent_id, name, sort_order, create_by) VALUES
(0, '智能制造与装备学院', 1, 'admin'),
(0, '纺织与材料工程学院', 2, 'admin'),
(0, '信息工程学院', 3, 'admin'),
(0, '经济管理学院', 4, 'admin');
