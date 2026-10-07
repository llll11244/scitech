-- =============================================================
-- 字典初值(spring.sql.init 每次启动执行,幂等:按 dict_type 先删后插,
-- id 手工固定编号;全部 is_system=1 可停用不可改名删除)
-- 横向无申报窗口配置(决策#34-①);sys_config 仅插值型展示配置:
-- deliver_pdf_visible=待签文件 PDF 入口显隐(2026-10-07 直出 Word 决策,
-- false=页面仅显示 Word 交付;docx+PDF 双出落盘不受开关影响)
-- =============================================================

DELETE FROM sys_config WHERE config_key = 'deliver_pdf_visible';

INSERT INTO sys_config (config_key, config_value, remark) VALUES
('deliver_pdf_visible', 'false', '待签文件PDF入口显隐:false=隐藏,页面仅显示Word交付');


DELETE FROM sys_dict WHERE dict_type IN (
  'hx_project_type', 'client_nature', 'hx_outcome_form', 'change_type',
  'budget_subject_hx', 'member_type', 'hx_scan_doc_type'
);

INSERT INTO sys_dict (id, dict_type, dict_value, dict_label, is_system, sort_order, status, create_by) VALUES
-- 横向项目类型(模板联动:登记表/变更表/验收表勾选组)
(1001, 'hx_project_type', '技术开发', '技术开发', 1, 1, 1, 'system'),
(1002, 'hx_project_type', '技术咨询', '技术咨询', 1, 2, 1, 'system'),
(1003, 'hx_project_type', '技术服务', '技术服务', 1, 3, 1, 'system'),
-- 委托单位性质(模板联动:勾选组)
(1011, 'client_nature', '政府部门', '政府部门', 1, 1, 1, 'system'),
(1012, 'client_nature', '企业', '企业', 1, 2, 1, 'system'),
(1013, 'client_nature', '事业单位', '事业单位', 1, 3, 1, 'system'),
(1014, 'client_nature', '研究机构', '研究机构', 1, 4, 1, 'system'),
(1015, 'client_nature', '其他', '其他', 1, 5, 1, 'system'),
-- 成果形式(模板联动:验收表勾选组)
(1021, 'hx_outcome_form', '技术报告', '技术报告', 1, 1, 1, 'system'),
(1022, 'hx_outcome_form', '鉴定报告', '鉴定报告', 1, 2, 1, 'system'),
(1023, 'hx_outcome_form', '专利', '专利', 1, 3, 1, 'system'),
(1024, 'hx_outcome_form', '咨询报告', '咨询报告', 1, 4, 1, 'system'),
(1025, 'hx_outcome_form', '其他', '其他', 1, 5, 1, 'system'),
-- 变更类型(模板联动:变更表勾选组,可多选)
(1031, 'change_type', '项目经费', '项目经费', 1, 1, 1, 'system'),
(1032, 'change_type', '执行周期', '执行周期', 1, 2, 1, 'system'),
(1033, 'change_type', '项目负责人', '项目负责人', 1, 3, 1, 'system'),
(1034, 'change_type', '委托内容', '委托内容', 1, 4, 1, 'system'),
(1035, 'change_type', '其他约定', '其他约定', 1, 5, 1, 'system'),
-- 横向预算科目(模板联动:预算科目行)
(1041, 'budget_subject_hx', '业务费', '业务费', 1, 1, 1, 'system'),
(1042, 'budget_subject_hx', '劳务费', '劳务费', 1, 2, 1, 'system'),
(1043, 'budget_subject_hx', '绩效', '绩效', 1, 3, 1, 'system'),
(1044, 'budget_subject_hx', '业务接待费', '业务接待费', 1, 4, 1, 'system'),
(1045, 'budget_subject_hx', '协作费', '协作费', 1, 5, 1, 'system'),
(1046, 'budget_subject_hx', '其它', '其它', 1, 6, 1, 'system'),
-- 成员类型
(1051, 'member_type', '教师', '教师', 1, 1, 1, 'system'),
(1052, 'member_type', '学生', '学生', 1, 2, 1, 'system'),
(1053, 'member_type', '校外人员', '校外人员', 1, 3, 1, 'system'),
-- 盖章扫描件文档类型(每类仅最新一份生效,重传覆盖旧版作废,决策#34-④)
(1061, 'hx_scan_doc_type', '登记表', '登记表', 1, 1, 1, 'system'),
(1062, 'hx_scan_doc_type', '变更表', '变更表', 1, 2, 1, 'system'),
(1063, 'hx_scan_doc_type', '验收登记表', '验收登记表', 1, 3, 1, 'system'),
(1064, 'hx_scan_doc_type', '验收报告', '验收报告', 1, 4, 1, 'system');
