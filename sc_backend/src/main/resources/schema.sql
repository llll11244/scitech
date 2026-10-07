-- =============================================================
-- 科研管理平台建库脚本(spring.sql.init 每次启动执行,全部幂等:
-- CREATE TABLE IF NOT EXISTS,绝不 DROP,避免清空数据)
-- 库:scitech_db(utf8mb4);金额列一律 DECIMAL(14,2) 单位=元(决策#25)
-- 通用字段每组都带:id/create_time/update_time/create_by/deleted(逻辑删除)
-- =============================================================

-- ---------------------------------------------------------------
-- 平台层 system 五表(自旧项目 research_db 结构平移,列一致)
-- ---------------------------------------------------------------

CREATE TABLE IF NOT EXISTS sys_user (
  id BIGINT NOT NULL AUTO_INCREMENT,
  username VARCHAR(64) NOT NULL COMMENT '登录名(工号)',
  password VARCHAR(100) NOT NULL COMMENT 'BCrypt加密',
  real_name VARCHAR(64) DEFAULT NULL COMMENT '姓名',
  role VARCHAR(32) DEFAULT NULL COMMENT 'admin管理员/viewer只读/teacher教师',
  dept_id BIGINT DEFAULT NULL COMMENT '院部id',
  phone VARCHAR(20) DEFAULT NULL,
  title VARCHAR(32) DEFAULT NULL COMMENT '职称(字典)',
  degree VARCHAR(32) DEFAULT NULL COMMENT '学位(字典)',
  status TINYINT DEFAULT 1 COMMENT '1启用 0停用',
  last_login DATETIME DEFAULT NULL COMMENT '最近登录时间(Task26 用户管理)',
  create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
  update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  create_by VARCHAR(64) DEFAULT NULL,
  deleted TINYINT DEFAULT 0 COMMENT '逻辑删除',
  PRIMARY KEY (id),
  UNIQUE KEY uk_username (username)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户表';

-- Task26(2026-10-08):老库补 last_login 列(MySQL 无 ADD COLUMN IF NOT EXISTS,
-- information_schema 判存+动态 SQL 幂等;新库由上方 CREATE TABLE 直接建出)
SET @col = (SELECT IF(COUNT(*) = 0,
  'ALTER TABLE sys_user ADD COLUMN last_login DATETIME DEFAULT NULL COMMENT ''最近登录时间(Task26 用户管理)'' AFTER status',
  'SELECT 1') FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'sys_user' AND COLUMN_NAME = 'last_login');
PREPARE stmt FROM @col;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

CREATE TABLE IF NOT EXISTS sys_dept (
  id BIGINT NOT NULL AUTO_INCREMENT,
  parent_id BIGINT DEFAULT 0 COMMENT '上级部门,0=顶级',
  name VARCHAR(128) NOT NULL COMMENT '院部名称',
  sort_order INT DEFAULT 0,
  create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
  update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  create_by VARCHAR(64) DEFAULT NULL,
  deleted TINYINT DEFAULT 0,
  PRIMARY KEY (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='院部(组织)表';

CREATE TABLE IF NOT EXISTS sys_dict (
  id BIGINT NOT NULL AUTO_INCREMENT,
  dict_type VARCHAR(64) NOT NULL COMMENT 'hx_横向模块/zs_纵向模块(预留)/平台级无前缀(title/degree等)',
  dict_value VARCHAR(64) NOT NULL COMMENT '存储值',
  dict_label VARCHAR(128) NOT NULL COMMENT '显示名',
  is_system TINYINT DEFAULT 0 COMMENT '1=系统内置(模板联动),可停用不可删改',
  sort_order INT DEFAULT 0,
  status TINYINT DEFAULT 1 COMMENT '1启用 0停用',
  create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
  update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  create_by VARCHAR(64) DEFAULT NULL,
  deleted TINYINT DEFAULT 0,
  PRIMARY KEY (id),
  UNIQUE KEY uk_type_value (dict_type, dict_value)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='数据字典表';

CREATE TABLE IF NOT EXISTS sys_config (
  id BIGINT NOT NULL AUTO_INCREMENT,
  config_key VARCHAR(64) NOT NULL,
  config_value VARCHAR(500) DEFAULT NULL,
  remark VARCHAR(255) DEFAULT NULL,
  create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
  update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  create_by VARCHAR(64) DEFAULT NULL,
  deleted TINYINT DEFAULT 0,
  PRIMARY KEY (id),
  UNIQUE KEY uk_key (config_key)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='系统配置表';

CREATE TABLE IF NOT EXISTS sys_notify (
  id BIGINT NOT NULL AUTO_INCREMENT,
  user_id BIGINT NOT NULL COMMENT '接收人→sys_user',
  title VARCHAR(255) NOT NULL,
  content VARCHAR(1000) DEFAULT NULL,
  project_id BIGINT DEFAULT NULL COMMENT '跳转关联项目',
  is_read TINYINT NOT NULL DEFAULT 0,
  create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
  update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  create_by VARCHAR(64) DEFAULT NULL,
  deleted TINYINT DEFAULT 0,
  PRIMARY KEY (id),
  KEY idx_user_read (user_id, is_read)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='站内消息表';

-- ---------------------------------------------------------------
-- 模板表(PDF 子系统,平台级)
-- 启用版唯一(tpl_type+status=1)无 partial index,由应用层事务保证
-- ---------------------------------------------------------------

CREATE TABLE IF NOT EXISTS sys_template (
  id BIGINT NOT NULL AUTO_INCREMENT,
  tpl_type VARCHAR(32) NOT NULL COMMENT 'hx_register/hx_change/hx_acceptance/hx_report',
  version INT NOT NULL COMMENT '版本号,递增',
  file_path VARCHAR(500) NOT NULL COMMENT 'upload/templates/下相对路径',
  placeholder_check TEXT COMMENT '上传时校验通过的占位符清单(JSON)',
  status TINYINT NOT NULL DEFAULT 1 COMMENT '1启用 0历史版本',
  remark VARCHAR(255) DEFAULT NULL,
  create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
  update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  create_by VARCHAR(64) DEFAULT NULL,
  deleted TINYINT DEFAULT 0,
  PRIMARY KEY (id),
  UNIQUE KEY uk_type_version (tpl_type, version)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='PDF模板表';

-- ---------------------------------------------------------------
-- 业务公共主表+横向扩展(决策#27/#30:15列最小真公共主表)
-- ---------------------------------------------------------------

CREATE TABLE IF NOT EXISTS rp_project (
  id BIGINT NOT NULL AUTO_INCREMENT,
  kind TINYINT NOT NULL COMMENT '1纵向(预留,本期无数据) 2横向(后续新类型递增)',
  audit_status TINYINT NOT NULL DEFAULT 2 COMMENT '0草稿/1已提交/2已确认(进台账)/3退回',
  apply_user_id BIGINT COMMENT '归属教师→sys_user.id',
  approval_no VARCHAR(64) COMMENT '全局唯一对外编号;横向=HX{年}-{序}',
  name VARCHAR(500) NOT NULL COMMENT '项目名称',
  leader_name VARCHAR(64) COMMENT '负责人',
  dept_id BIGINT COMMENT '院部',
  status VARCHAR(32) COMMENT '生命周期大状态(字典);横向仅用 立项/结题',
  declare_date DATE COMMENT '流程发起日(横向登记提交日)',
  finish_date DATE COMMENT '完结日(横向验收确认日)',
  create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
  update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  create_by VARCHAR(64) DEFAULT NULL,
  deleted TINYINT DEFAULT 0,
  PRIMARY KEY (id),
  UNIQUE KEY uk_approval_no (approval_no),
  KEY idx_kind_status (kind, deleted, status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='项目公共主表(15列最小真公共)';

CREATE TABLE IF NOT EXISTS rp_hx_project (
  project_id BIGINT NOT NULL COMMENT '→rp_project(1:1)',
  project_type VARCHAR(16) COMMENT '字典hx_project_type:技术开发/技术咨询/技术服务',
  client_name VARCHAR(255) COMMENT '委托单位名称',
  client_nature VARCHAR(32) COMMENT '字典client_nature:政府部门/企业/事业单位/研究机构/其他',
  contract_amount DECIMAL(14,2) COMMENT '合同经费(元)',
  start_date DATE COMMENT '合同开始',
  end_date DATE COMMENT '合同结束',
  use_school_asset TINYINT COMMENT '以学校设备场地为主0/1',
  charge_mgmt_fee TINYINT COMMENT '是否收管理费0/1',
  mgmt_fee_amount DECIMAL(14,2) COMMENT '管理费金额(元)',
  leader_email VARCHAR(128) COMMENT '负责人邮箱',
  project_summary TEXT COMMENT '项目简介(200-300字,登记表)',
  progress_plan TEXT COMMENT '项目计划进度(200-300字,登记表)',
  hx_register_date DATE COMMENT '立项登记日期(科技处确认时写入)',
  verified_amount DECIMAL(14,2) COMMENT '核定到账金额(元,验收时定格)',
  create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
  update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  create_by VARCHAR(64) DEFAULT NULL,
  deleted TINYINT DEFAULT 0,
  PRIMARY KEY (project_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='横向项目扩展表';

-- ---------------------------------------------------------------
-- 横向流程(三流程单共用骨架,spec §4.2/4.2.1)
-- ---------------------------------------------------------------

CREATE TABLE IF NOT EXISTS rp_hx_flow (
  id BIGINT NOT NULL AUTO_INCREMENT,
  project_id BIGINT NOT NULL COMMENT '→rp_project',
  flow_type VARCHAR(16) NOT NULL COMMENT 'register登记/change变更/acceptance验收',
  status TINYINT NOT NULL DEFAULT 0 COMMENT '0草稿/1已提交待回扫/2已回扫待审核/3通过/4退回',
  change_type VARCHAR(32) COMMENT '字典change_type,可多选逗号分隔',
  change_reason TEXT COMMENT '申请变更原因',
  change_content TEXT COMMENT '变更内容说明',
  new_amount DECIMAL(14,2) COMMENT '变更后经费(元,change_type含项目经费时必填)',
  new_end_date DATE COMMENT '变更后结束日(含执行周期时必填)',
  new_leader_id BIGINT COMMENT '变更后负责人→sys_user(含负责人变更时必填,须已有账号)',
  before_json JSON COMMENT '变更前字段快照(改写项目字段前存)',
  accept_summary TEXT COMMENT '项目完成情况总结(验收单)',
  is_on_time TINYINT COMMENT '是否按期完成0/1(验收单)',
  outcome_form VARCHAR(64) COMMENT '成果形式(字典hx_outcome_form,可多选逗号分隔)',
  used_amount DECIMAL(14,2) COMMENT '使用经费(元,验收单)',
  report_content MEDIUMTEXT COMMENT '附件4报告正文(纯文本分段\n\n分段;页数不限)',
  submit_time DATETIME COMMENT '提交时间',
  scan_time DATETIME COMMENT '回扫时间',
  confirm_time DATETIME COMMENT '科技处确认时间',
  create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
  update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  create_by VARCHAR(64) DEFAULT NULL,
  deleted TINYINT DEFAULT 0,
  PRIMARY KEY (id),
  KEY idx_project_type (project_id, flow_type),
  KEY idx_status_type (status, flow_type)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='横向流程单主表';

CREATE TABLE IF NOT EXISTS rp_hx_flow_record (
  id BIGINT NOT NULL AUTO_INCREMENT,
  project_id BIGINT NOT NULL COMMENT '→rp_project(冗余,按项目聚合全部流程单动作)',
  flow_id BIGINT NOT NULL COMMENT '→rp_hx_flow',
  action VARCHAR(16) NOT NULL COMMENT 'submit提交/withdraw撤回/upload_scan回扫/precheck_reject预检退回/reject退回/resubmit重交/verify核定/approve通过',
  operator_id BIGINT COMMENT '操作人→sys_user',
  operator_name VARCHAR(64) COMMENT '操作人姓名',
  opinion VARCHAR(500) COMMENT '退回意见/核定备注',
  create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
  update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  create_by VARCHAR(64) DEFAULT NULL,
  deleted TINYINT DEFAULT 0,
  PRIMARY KEY (id),
  KEY idx_flow (flow_id),
  KEY idx_project (project_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='横向流程动作表(独立于纵向审批表,决策#23)';

-- ---------------------------------------------------------------
-- 成员/预算/附件/到账(项目公共子表)
-- ---------------------------------------------------------------

CREATE TABLE IF NOT EXISTS rp_member (
  id BIGINT NOT NULL AUTO_INCREMENT,
  project_id BIGINT NOT NULL COMMENT '→rp_project',
  member_type VARCHAR(16) COMMENT '字典member_type:教师/学生/校外人员',
  name VARCHAR(64) COMMENT '姓名',
  unit VARCHAR(255) COMMENT '所在单位',
  title VARCHAR(32) COMMENT '职称(字典;纵向阶段用)',
  degree VARCHAR(32) COMMENT '学位(字典;纵向阶段用)',
  major VARCHAR(64) COMMENT '专业/学科专业(登记表/验收表成员栏)',
  duty VARCHAR(500) COMMENT '承担本项目的工作任务(描述性文本)',
  student_no VARCHAR(32) COMMENT '学号(学生成员)',
  mentor VARCHAR(64) COMMENT '指导老师(学生成员)',
  ratio DECIMAL(5,2) COMMENT '承担任务比例%(验收分工)',
  amount DECIMAL(14,2) COMMENT '承担金额(元,验收分工)',
  sort_no INT DEFAULT 0,
  create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
  update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  create_by VARCHAR(64) DEFAULT NULL,
  deleted TINYINT DEFAULT 0,
  PRIMARY KEY (id),
  KEY idx_project (project_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='项目成员表(一次建齐横向所需列,纵向阶段复用不加列)';

CREATE TABLE IF NOT EXISTS rp_budget (
  id BIGINT NOT NULL AUTO_INCREMENT,
  project_id BIGINT NOT NULL COMMENT '→rp_project',
  subject VARCHAR(64) NOT NULL COMMENT '预算科目(横向=budget_subject_hx 6科目;纵向阶段加budget_subject 11科目)',
  amount DECIMAL(14,2) NOT NULL DEFAULT 0.00 COMMENT '金额(元;原生元口径,无迁移)',
  create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
  update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  create_by VARCHAR(64) DEFAULT NULL,
  deleted TINYINT DEFAULT 0,
  PRIMARY KEY (id),
  UNIQUE KEY uk_project_subject (project_id, subject)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='项目预算明细表';

CREATE TABLE IF NOT EXISTS rp_attachment (
  id BIGINT NOT NULL AUTO_INCREMENT,
  project_id BIGINT NOT NULL COMMENT '→rp_project',
  biz_type VARCHAR(32) NOT NULL COMMENT 'hx_apply申报佐证/hx_scan盖章扫描件/hx_final_docx|pdf终版/hx_final_report_docx|pdf验收报告终版/hx_docx|pdf待签/hx_report_docx|pdf验收报告待签(含作废版)/hx_extra另附材料',
  doc_type VARCHAR(64) DEFAULT NULL COMMENT '文档类型(字典hx_scan_doc_type:登记表/变更表/验收登记表/验收报告;另含合同)',
  file_name VARCHAR(255) NOT NULL COMMENT '原始文件名',
  file_path VARCHAR(500) NOT NULL COMMENT '存储相对路径(相对上传根目录)',
  file_size BIGINT NOT NULL DEFAULT 0 COMMENT '字节',
  uploader_id BIGINT DEFAULT NULL,
  create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
  update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  create_by VARCHAR(64) DEFAULT NULL,
  deleted TINYINT DEFAULT 0,
  PRIMARY KEY (id),
  KEY idx_project_biz (project_id, biz_type)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='项目附件表(逻辑删仅标记,磁盘文件保留,决策#34-⑧)';

-- Task24(2026-10-08):终版双出 biz_type 最长 hx_final_report_docx=20 字符,历史库列宽 16 升 32
-- (幂等:MODIFY 同定义重复执行无副作用;IF NOT EXISTS 建表不会改已有表,老库靠本行补齐)
ALTER TABLE rp_attachment MODIFY biz_type VARCHAR(32) NOT NULL COMMENT 'hx_apply申报佐证/hx_scan盖章扫描件/hx_final_docx|pdf终版/hx_final_report_docx|pdf验收报告终版/hx_docx|pdf待签/hx_report_docx|pdf验收报告待签(含作废版)/hx_extra另附材料';

CREATE TABLE IF NOT EXISTS rp_funding_record (
  id BIGINT NOT NULL AUTO_INCREMENT,
  project_id BIGINT NOT NULL COMMENT '→rp_project(横向行,草稿期即存在)',
  amount DECIMAL(14,2) NOT NULL COMMENT '本笔到账(元)',
  received_date DATE COMMENT '到账日期',
  payer VARCHAR(255) COMMENT '付款方(默认=委托单位,可改)',
  bank_flow_no VARCHAR(64) COMMENT '银行流水号(选填)',
  remark VARCHAR(255) DEFAULT NULL,
  create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
  update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  create_by VARCHAR(64) DEFAULT NULL,
  deleted TINYINT DEFAULT 0,
  PRIMARY KEY (id),
  KEY idx_project_date (project_id, received_date)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='到账明细表(累计实时SUM不冗余,单一事实源;admin录入教师只读)';
