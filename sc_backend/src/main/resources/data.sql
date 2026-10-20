-- =============================================================
-- 字典初值(spring.sql.init 每次启动执行,幂等口径=「首启种子、此后不碰」:
-- 固定 id INSERT IGNORE——已存在(含管理页停用/调序/改名后)的行完全不动,
-- 仅首启或代码新增种子行时入库;**不做 DELETE 重置**,否则 Task26 字典管理的
-- 停用/调序/自建取值每次重启即丢。id 手工固定编号;种子全部 is_system=1
-- 可停用不可改名删除。升级新增种子行靠 IGNORE 冲突跳过自然补插。
-- 2026-10-19 决策#32 前缀对齐(client_nature/change_type/member_type→hx_ 前缀、budget_subject_hx→
-- hx_budget_subject,存量库靠文末 UPDATE 幂等迁移)+ 新增平台级职称字典 title)
-- 横向无申报窗口配置(决策#34-①);sys_config 两类口径:
-- ①代码所有值型开关(管理页不可改)→ 先删后插重置无害:
--   deliver_pdf_visible=待签文件 PDF 入口显隐(2026-10-07 直出 Word 决策,
--   false=页面仅显示 Word 交付;docx+PDF 双出落盘不受开关影响)
-- ②admin 可改业务参数(「系统参数」页维护)→ INSERT IGNORE 首启种子、此后不碰
--   (重启不得重置管理页改动,与字典同口径):
--   hx_mgmt_fee_rate=横向管理费比例(%)——金额=合同经费×比例自动换算,教师端只读(2026-10-20)
-- =============================================================

DELETE FROM sys_config WHERE config_key = 'deliver_pdf_visible';

INSERT INTO sys_config (config_key, config_value, remark) VALUES
('deliver_pdf_visible', 'false', '待签文件PDF入口显隐:false=隐藏,页面仅显示Word交付');

INSERT IGNORE INTO sys_config (config_key, config_value, remark) VALUES
('hx_mgmt_fee_rate', '2', '横向项目管理费比例(%):金额=合同经费×比例自动换算,教师端只读');


INSERT IGNORE INTO sys_dict (id, dict_type, dict_value, dict_label, is_system, sort_order, status, create_by) VALUES
-- 横向项目类型(模板联动:登记表/变更表/验收表勾选组)
(1001, 'hx_project_type', '技术开发', '技术开发', 1, 1, 1, 'system'),
(1002, 'hx_project_type', '技术咨询', '技术咨询', 1, 2, 1, 'system'),
(1003, 'hx_project_type', '技术服务', '技术服务', 1, 3, 1, 'system'),
-- 委托单位性质(模板联动:勾选组)
(1011, 'hx_client_nature', '政府部门', '政府部门', 1, 1, 1, 'system'),
(1012, 'hx_client_nature', '企业', '企业', 1, 2, 1, 'system'),
(1013, 'hx_client_nature', '事业单位', '事业单位', 1, 3, 1, 'system'),
(1014, 'hx_client_nature', '研究机构', '研究机构', 1, 4, 1, 'system'),
(1015, 'hx_client_nature', '其他', '其他', 1, 5, 1, 'system'),
-- 成果形式(模板联动:验收表勾选组)
(1021, 'hx_outcome_form', '技术报告', '技术报告', 1, 1, 1, 'system'),
(1022, 'hx_outcome_form', '鉴定报告', '鉴定报告', 1, 2, 1, 'system'),
(1023, 'hx_outcome_form', '专利', '专利', 1, 3, 1, 'system'),
(1024, 'hx_outcome_form', '咨询报告', '咨询报告', 1, 4, 1, 'system'),
(1025, 'hx_outcome_form', '其他', '其他', 1, 5, 1, 'system'),
-- 变更类型(模板联动:变更表勾选组,可多选)
(1031, 'hx_change_type', '项目经费', '项目经费', 1, 1, 1, 'system'),
(1032, 'hx_change_type', '执行周期', '执行周期', 1, 2, 1, 'system'),
(1033, 'hx_change_type', '项目负责人', '项目负责人', 1, 3, 1, 'system'),
(1034, 'hx_change_type', '委托内容', '委托内容', 1, 4, 1, 'system'),
(1035, 'hx_change_type', '其他约定', '其他约定', 1, 5, 1, 'system'),
-- 横向预算科目(模板联动:预算科目行)
(1041, 'hx_budget_subject', '业务费', '业务费', 1, 1, 1, 'system'),
(1042, 'hx_budget_subject', '劳务费', '劳务费', 1, 2, 1, 'system'),
(1043, 'hx_budget_subject', '绩效', '绩效', 1, 3, 1, 'system'),
(1044, 'hx_budget_subject', '业务接待费', '业务接待费', 1, 4, 1, 'system'),
(1045, 'hx_budget_subject', '协作费', '协作费', 1, 5, 1, 'system'),
(1046, 'hx_budget_subject', '其它', '其它', 1, 6, 1, 'system'),
-- 成员类型
(1051, 'hx_member_type', '教师', '教师', 1, 1, 1, 'system'),
(1052, 'hx_member_type', '学生', '学生', 1, 2, 1, 'system'),
(1053, 'hx_member_type', '校外人员', '校外人员', 1, 3, 1, 'system'),
-- 盖章扫描件文档类型(每类仅最新一份生效,重传覆盖旧版作废,决策#34-④)
(1061, 'hx_scan_doc_type', '登记表', '登记表', 1, 1, 1, 'system'),
(1062, 'hx_scan_doc_type', '变更表', '变更表', 1, 2, 1, 'system'),
(1063, 'hx_scan_doc_type', '验收登记表', '验收登记表', 1, 3, 1, 'system'),
(1064, 'hx_scan_doc_type', '验收报告', '验收报告', 1, 4, 1, 'system'),
-- 职称(平台级,登记表成员下拉可输入;不联动模板,四序列+其他兜底,管理页可增删)
(1081, 'title', '教授', '教授', 1, 1, 1, 'system'),
(1082, 'title', '副教授', '副教授', 1, 2, 1, 'system'),
(1083, 'title', '讲师', '讲师', 1, 3, 1, 'system'),
(1084, 'title', '助教', '助教', 1, 4, 1, 'system'),
(1085, 'title', '研究员', '研究员', 1, 5, 1, 'system'),
(1086, 'title', '副研究员', '副研究员', 1, 6, 1, 'system'),
(1087, 'title', '正高级工程师', '正高级工程师', 1, 7, 1, 'system'),
(1088, 'title', '高级工程师', '高级工程师', 1, 8, 1, 'system'),
(1089, 'title', '工程师', '工程师', 1, 9, 1, 'system'),
(1090, 'title', '助理工程师', '助理工程师', 1, 10, 1, 'system'),
(1091, 'title', '正高级实验师', '正高级实验师', 1, 11, 1, 'system'),
(1092, 'title', '高级实验师', '高级实验师', 1, 12, 1, 'system'),
(1093, 'title', '实验师', '实验师', 1, 13, 1, 'system'),
(1094, 'title', '其他', '其他', 1, 14, 1, 'system');


-- 存量库字典改名迁移(2026-10-19 决策#32 命名对齐;每次启动执行,新库/已迁移库 0 行幂等)
UPDATE sys_dict SET dict_type = 'hx_client_nature'  WHERE dict_type = 'client_nature';
UPDATE sys_dict SET dict_type = 'hx_change_type'    WHERE dict_type = 'change_type';
UPDATE sys_dict SET dict_type = 'hx_member_type'    WHERE dict_type = 'member_type';
UPDATE sys_dict SET dict_type = 'hx_budget_subject' WHERE dict_type = 'budget_subject_hx';
