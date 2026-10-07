# 横向项目流程与 PDF 产出 · 设计文档

> 版本:v2.0(2026-10-05 起稿;2026-10-07 定稿——**弃用旧项目重新起**,见决策#29/#31)
> 定位:**科研管理平台**(多模块架构,后期扩展至少 6 个模块),横向项目流程为第一个业务模块,以**可直出 PDF 等可打印资源为主要产出**,纸质签字盖章流程本色保留;纵向等其余模块后续按同架构逐个生长
> 前置:旧系统(阶段0-4b,仓库 tag 归档只读)提供底座代码搬运源与踩坑经验

## 一、需求背景与核心认知

科技处提供 4 张横向项目制式表单(.doc):

| 表 | 环节 | 关键内容 | 纸面签章方 |
|---|---|---|---|
| 附件1 横向项目登记表 | 立项登记 | 项目类型(技术开发/咨询/服务)、委托单位+性质、合同经费(元+大写)、成员及分工、简介/进度(200-300字)、横向预算6科目、是否用学校设备场地、管理费 | 负责人承诺签字→所在单位(签+章)→科技处(签+章)→登记日期 |
| 附件2 重要事项变更表 | 执行中变更 | 变更类型勾选(经费/周期/负责人/委托内容/其他)、变更说明、原因 | 申请人+变更后负责人→委托单位(章)→所在单位(章)→科技处(章) |
| 附件3 验收登记表(含学生版) | 结题验收 | 到账/使用经费、成员分工比例(%及金额)、学生参与(学号/专业/指导老师)、成果形式、完成总结、科技处核定到账金额 | 负责人→所在单位(章)→委托单位评价(章)→科技处核定 |
| 附件4 验收报告 | 结题验收 | 是否按期、成果形式多选、长篇报告正文(页数不限,多页单面) | 负责人手写签字 |

表尾硬约束:**双面打印、控制在两页内**(附件1/2/3)、一式多份、签名处须手写。

**核心认知(需求方确认)**:系统的价值是「数据填报 + PDF 产出 + 流程留痕 + 归档」,真正的签字盖章在线下纸质流程完成。系统不代签、不代章。

## 二、已确认决策清单

| # | 决策 | 确认内容 |
|---|---|---|
| 1 | 审批形态 | PDF 产出 → 线下盖章 → 回扫上传 → 科技处线上确认。审批链不上线(不给院部负责人建账号),委托单位意见必然线下 |
| 2 | 流程按需 | 登记必经 1 次;变更 0..n 次(无变更不填);验收按需 1 张(退回重交不算新单) |
| 3 | 项目编号 | 系统自动生成 `HX{年份}-{序号}`(如 HX2026-001),写入 rp_project.approval_no;教师填表时留空,PDF 自动带出 |
| 4 | PDF 路线 | **LibreOffice 便携版随部署包目录携带**(免安装绿色版,~400MB,与 jar 同级,`sc.office-home` 指向)+ poi-tl 填充 docx 模板 + JODConverter 进程池转 PDF |
| 5 | 模板管理 | 管理员后台上传新 docx 模板(不做页面内编辑器);上传时**占位符完整性校验**(缺哪个报哪个)+ **样例数据试渲染预览**;模板带版本号;已生成 PDF 落盘归档不重渲染 |
| 6 | 验收报告 | 附件4 正文**在线纯文本分段编辑**(不做富文本),图片图纸走「另附上传」(表尾注释允许"插入本表或另附亦可") |
| 7 | 审批页形式 | **PDF 预览为主体**(iframe 内嵌)+ 右侧操作面板(摘要/扫描件/时间线/通过/退回/核定额录入) |
| 8 | 审批意见分层 | ①纸面意见/签字/盖章栏一律留空手写 ②系统内意见存 rp_hx_flow_record(决策#23)+时间线展示 ③核定类数据(到账核定额、登记日期)结构化录入入库 |
| 9 | 双档案 | 流程完结归档两份:盖章扫描件(纸质证据)+ 终版数据 PDF(含核定额/登记日期,数据齐全可检索) |
| 10 | 操作留痕 | 每动作(提交/回扫/核定/通过/退回/变更生效)写 rp_hx_flow_record(操作人/时间/动作/意见);变更单存**变更前值快照**;终版 PDF 写元数据(生成时间/操作人/流程单号) |
| 11 | 存储 | 校内服务器本地磁盘(upload/hx/... 目录树),数据不出校;复用 rp_attachment + 鉴权下载 |
| 12 | 备份 | 系统内置**数据备份功能**(admin):定时自动(每周,保留最近8份)+ 手动一键;产物=全库SQL+完整upload目录的zip,后台列表可下载 |
| 13 | 本期边界 | 流程+PDF+归档优先;横向**不进**现有4张统计图表(kind=1 口径冻结),横向统计与 Excel 导出二期;已登记项目进「项目管理」**独立横向页签**(见决策#21) |
| 14 | 金额单位 | ~~库内横向统一存元、纵向万元分列~~ → 被#25 取代:**全库统一存元**,万元仅存在于边界层(导入/导出/展示) |
| 15 | 字典开放度 | 开放**字典取值维护**给管理员(补上阶段2遗留的字典管理界面,横向/纵向字典统一管);横向 5 字典标记**模板联动**——增删取值时强提醒需同步换 PDF 模板,改标签名安全(渲染按字典标签动态输出);**不做**管理员自定义新表单字段(与 docx 制式模板路线冲突,PDF 上无格子可渲染) |
| 16 | 到账经费 | 分批到账常态 → **明细表 rp_funding_record**(admin 分批录入,教师只读),**累计不回写 received_funding**(单一事实源原则:明细为真源,累计实时 SUM,消灭同步逻辑;统一元后已无单位障碍,仍不冗余);录入后自动通知教师;到账与立项**完全解耦**(0笔也正常) |
| 17 | 金额三层模型 | 合同层 contract_amount(登记必填)/到账层明细累计(随时在涨)/核定层 verified_amount(验收时定格,尾款后到账核定≠累计属正常),互不强制相等 |
| 18 | 盖章前预检 | admin 在 status=1(未回扫)即可预览 PDF 并预检退回,数据问题在打印盖章前拦住,避免作废已盖章纸质件 |
| 19 | 合同扫描件 | 登记**可选**上传合同扫描件(doc_type=合同),不强制(是否立项必传由科技处运行期自行把握) |
| 20 | 项目行创建时机 | 登记草稿创建即建 rp_project 行(同纵向申报),保证「先到账后登记确认」时到账明细有处可挂;连草稿都没有的到账走线下催办,系统不做无主到账暂记 |
| 21 | 横向项目列表 | 新项目本期即「横向项目管理」页(列:编号/名称/负责人/院部/委托单位/合同经费/到账累计/执行状态/登记日期,到账累计可展开明细);纵向模块将来上线时另立入口,不混排 |
| 22 | admin 代录 | **不做** admin 代录登记单(用户确认);老师不配合场景由科技处线下推动 |
| 23 | 库表整合(2026-10-07 审查定案) | ① 横向流程动作**独立建表 rp_hx_flow_record**(2026-10-07 复议:不复用 rp_approval_record——纵向 approve=申报审核、横向 approve=流程单确认,同词不同义=语义混杂;kind 互斥故不存在跨表查询;时间线组件靠统一 DTO 复用而非共表),纵向动作表一字不动 ② rp_hx_flow **删 reject_reason 冗余列**,退回意见唯一事实源=flow_record.opinion(取最新 reject) ③ 横向行复用 **audit_status 语义**:登记草稿0/提交1/确认登记2(此后不变),变更/验收流程不碰它——台账视图/判重/列表过滤零特判 ④ 合同起止存**独立列 start_date/end_date**,不复用 approval_date/expected_finish_date(纵向立项批准时间与横向合同起止是不同业务语义,"机制共用、语义分列") |
| 24 | ~~金额单位分列原则~~(已被#25取代) | 原「纵向万元列禁写+横向元专属列」分列方案作废,改为全库统一元(见#25) |
| 25 | 金额统一为元(2026-10-07 定案;v2 简化) | **库内单一口径=元**,新项目原生成立,无存量迁移(纵向台账数据推迟导入,届时在 **Excel 导入层 ×10000** 换算入库,见§4.1.2);万元只存在于**边界层**(导入×10000/导出÷10000/展示换算);所有金额列 DECIMAL(14,2) |
| 26 | 展示口径规则(2026-10-07 修正) | 金额列提供 **元↔万元显示切换**(表头 toggle,记忆用户偏好 localStorage);默认属地口径(横向明细默认元;纵向模块上线后其列表默认万元);库内恒元,切换只影响格式化;跨类汇总万元+标注单位 |
| 27 | 类型扩展表(v2 定案) | **公共主表+类型扩展表**:rp_project=最小真公共主表(15列,§4.1);rp_hx_project 横向扩展表(§4.1.1)本期建;**rp_zs_project 纵向扩展表推迟到纵向阶段**(纵向 PDF 需求未确认前不猜字段,§4.1.2);未来新类型=新扩展表,主表永不再因类型扩张改 DDL |
| 28 | 变更改写留痕(2026-10-07 修正) | ~~update_by 补强已撤销~~(rp_project 无 update_by 列,且属冗余事实源);操作人/记录唯一权威源=rp_hx_flow_record(动作层:谁何时做了什么)+rp_hx_flow.before_json(改写层:前后值),主表不加列不冗余 |
| 29 | 弃用重起(2026-10-07 终案) | **旧项目废弃**(未部署,弃用窗口最佳;tag 归档只读不删),新项目按目标架构从第一天搭:最小主表+双扩展表+原生元口径+PDF子系统一等公民;纵向模块后续在新架构重做(复用横向流程模式+PDF子系统,届时按纵向PDF模板定字段集)。旧系统踩坑经验(CLAUDE.md 教训)全部带走。v1 演进案的三个妥协随之作废:~~主表冻结防守~~(新主表天生只有真公共列)、~~存量×10000迁移+阶段2/3回归~~(导入层换算替代)、~~纵向列冻结~~(纵向字段已进扩展表,自由演化) |
| 31 | 弃/搬/新清单(2026-10-07 定案;纵向部分后置) | **弃**:纵向42列CRUD/导入导出旧实现、纵向申报流程旧实现、旧 rp_project 宽表、统计图表旧实现;**搬**(底座整体移植,本期):JWT认证/拦截器/统一响应/异常、用户/院部/字典/消息/配置 system 模块、附件存储下载、前端骨架(Login/Layout/路由守卫/Pinia/axios)+踩坑经验(CLAUDE.md);**新建**(本期):横向全部(本设计主体)+新库结构;**推迟到纵向阶段**:rp_zs_project 建表、301条台账导入(×10000)、统计图表移植(SQL 改 JOIN 扩展表)、纵向申报 PDF 化——纵向需求(PDF 模板/字段集)确认后独立立项。数据不丢(research_db.sql 快照随旧仓库 tag 归档),教师账号在新系统重建 |
| 32 | 模块化平台架构(2026-10-07 定案) | 系统定位=**科研管理平台**,后期至少扩展 6 个模块,横向为首个业务模块。结构约定:①后端按业务域分包 `com.fz.scitech.{module}`(hx/zs/…),平台级服务(template/backup/notify/attachment/user)独立分包,业务模块不得互相 import,只准走平台层 ②前端 `views/{module}/` + 路由按模块注册,Layout 动态菜单挂模块入口 ③字典 dict_type 带模块前缀(hx_/zs_),平台级无前缀(title/degree) ④表前缀 rp_{module}_,平台级 sys_ ⑤新模块接入=新扩展表+新分包+新菜单,平台层与既有模块零改动 ⑥RBAC 暂保持单角色,若出现模块级管理员再升级 user-role 关联表(预留) |
| 33 | AI 能力接入预留(2026-10-07 定案,本期不实现) | 将来引入 AI agent(项目推荐/分析/文档问答/合同抽取填表等)以**平台级服务**身份接入(#32 第⑤条通道)。本期只留缝:平台层定义 `AiService` 防腐接口,业务模块只依赖接口;实现两阶段——先 Java(Spring AI 调 LLM),成型后可平滑外挂 FastAPI(换接口后端,业务零改动);FastAPI 若登场须遵守:无状态/不直连业务库(走 Java OpenAPI 或只读视图)/JWT 传身份/便携式随部署包。**AI 同受数据不出校约束(#11):任何出校数据必须过脱敏层;本地模型(Ollama 便携版)优先考虑**。向量检索起步不引入专门向量库(校内数据量小,嵌入内存计算足够),规模化后再评估 |
| 34 | 终审决议:防过度与补边界(2026-10-07) | **✂️ 砍(防过度开发)**:①横向**不做申报窗口控制**(合同随时签,全年开放;勿把纵向窗口机制顺手搬运)②AI 预留本期**零代码**(不写空接口类,仅本决策记载;#33 的「留缝」即本条)③模板试渲染用**内置静态样例数据**(代码常量,不建样例数据集/不选真实流程单);单位切换组件只挂横向有金额列的页面,不做全局组件。**🔒 补边界(实现铁律)**:①备份依赖 mysqldump 可执行文件,部署包携带或 sc.mysqldump-path 指向(§八)②状态迁移一律条件更新(UPDATE…WHERE status=旧值)防并发双审/双提③PDF 生成失败=提交失败可重试,不落半成品;JODConverter 进程池自愈,转换排队④盖章扫描件按 doc_type 每类只保留最新一份(登记表/变更表/验收登记表/验收报告四类),重传覆盖旧版作废⑤删登记草稿=连带逻辑删项目行+成员/预算/附件;**已有到账记录的项目禁删**⑥变更负责人生效后,原负责人即不可见该项目(一切以 apply_user_id 为准)⑦HX 编号分配防并发(计数表行锁或唯一索引冲突重试)⑧附件逻辑删除仅标记,磁盘文件保留(不写清理任务);backup zip 删除=物理删 |
| 35 | 会话安全基线与实施时机(2026-10-07 定案) | JWT+localStorage+BCrypt 机制对校内部署**够用,不换架构**;安全项按「代码结构性(开发期做)/部署动作(上线做)」划线。**开发期做(随底座搬运一次写对,归 N1)**:①token 加 userId claim(结构变更不可上线后做)②AuthInterceptor 解析后按主键查 sys_user(停用立即生效+带出最新角色,替代吊销黑名单)③secret 环境变量化 `${SC_JWT_SECRET:开发默认值}`(上线零代码改动)④DataInitializer 按分 profile 建号(dev 建测试账号/**prod 不建任何默认账号**)⑤登录失败锁定(同账号连错5次锁10分钟)⑥备份/PDF 下载接口独立鉴权(N4 开发范围内)。**上线动作(只填配置,代码零改动)**:真 secret 注入/HTTPS+证书/生产建正式账号+强密/监听收紧内网。原则:上线日的安全工作只剩配置,不碰代码 |
| 30 | 真公共判据三问(2026-10-07 定案) | 字段进主表须三条全过:①各类型都**自己产生**该数据(非推导/带出) ②同一名词各类型下**语义一致** ③跨类型查询真实需要。按此重审:members=伪公共(纵向42列导出拼接产物,横向不拼,PDF从rp_member渲染)、leader_title/leader_degree=伪公共(横向不落库,渲染时实时从sys_user带出);kind/audit_status/apply_user_id/approval_no/name/leader_name/dept_id/status(横向仅用立项/结题子集)/declare_date/finish_date=真公共留主表 |

## 三、流程模型

### 3.1 三流程单共用骨架

```
教师填报 → 提交 → 系统生成待签 PDF → 打印/线下逐级盖章 → 回扫上传
        → 科技处审批(PDF 预览+扫描件核对) → 通过:流程生效+终版 PDF 归档
                                      → 退回:意见必填,教师修改重交(重新生成 PDF,旧件作废)
```

### 3.2 流程单状态机(rp_hx_flow.status)

```
0 草稿 ──提交──▶ 1 已提交(待回扫) ──上传扫描件──▶ 2 已回扫(待审核) ──科技处通过──▶ 3 通过(归档)
  ▲                    │                            │
  └── 教师撤回 ─────────┘                            └── 科技处退回 ──▶ 4 退回 ──教师修改重交──▶ 1
```

- 状态 1 时教师可撤回回草稿;状态 2 时撤回需回到 1(扫描件仍有效,内容未变)
- **盖章前预检(决策#18)**:admin 在 status=1 即可预览 PDF 并退回(意见标注「盖章前预检」)——数据问题在打印盖章前拦住
- 退回重交生成新版本 PDF,旧 PDF **与旧扫描件一并标记作废**(保留文件不入列表),需重新盖章回扫

### 3.3 各流程单通过后的副作用

| 流程 | 副作用 |
|---|---|
| 登记 | 项目正式入台账:主表 kind=2、audit_status=2、status='立项'(在研)、approval_no=自动编号;扩展表 rp_hx_project.hx_register_date=确认日;发教师通知 |
| 变更 | 变更内容生效:按 change_type 自动改写(经费→rp_hx_project.contract_amount=new_amount、周期→rp_hx_project.end_date=new_end_date、负责人→主表 apply_user_id=new_leader_id 并同步 leader_name;改写前快照已存 before_json);发通知 |
| 验收 | 项目 status='结题'、verified_amount=核定额写入、finish_date=确认日;终版数据 PDF(含核定金额)生成归档;发通知 |

## 四、数据模型

### 4.1 rp_project 公共主表(新项目新建,15 列最小真公共)

```sql
id            BIGINT PK,
kind          TINYINT      COMMENT '1纵向(预留,本期无数据) 2横向(后续新类型递增)',
audit_status  TINYINT      COMMENT '0草稿/1已提交/2已确认(进台账)/3退回',
apply_user_id BIGINT       COMMENT '归属教师→sys_user',
approval_no   VARCHAR(64)  COMMENT '全局唯一对外编号(横向=HX{年}-{序}确认时自动生成);唯一判重索引',
name          VARCHAR(500) COMMENT '项目名称',
leader_name   VARCHAR(64)  COMMENT '负责人',
dept_id       BIGINT       COMMENT '院部',
status        VARCHAR(32)  COMMENT '生命周期大状态(字典);横向仅用 立项/结题',
declare_date  DATE         COMMENT '流程发起日(横向登记提交日)',
finish_date   DATE         COMMENT '完结日(横向验收确认日)',
-- 通用字段: create_time/update_time/create_by/deleted
```

- 横向登记草稿建行:kind=2、audit_status=0→1→**2(确认登记,此后不变)**;确认时写 approval_no、status='立项'、验收确认写 status='结题'+finish_date
- **不加** members/leader_title/leader_degree 等伪公共列(决策#30);**不加** update_by(决策#28);新列准入=三问(决策#30)
- kind=1 纵向本期仅预留枚举,无数据无代码分支

### 4.1.1 rp_hx_project 横向扩展表(新,project_id 主键 1:1)

| 字段 | 类型 | 说明 |
|---|---|---|
| project_id | BIGINT PK | →rp_project(1:1) |
| project_type | VARCHAR(16) | 字典 hx_project_type:技术开发/技术咨询/技术服务 |
| client_name | VARCHAR(255) | 委托单位名称 |
| client_nature | VARCHAR(32) | 字典 client_nature:政府部门/企业/事业单位/研究机构/其他 |
| contract_amount | DECIMAL(14,2) | 合同经费(元) |
| start_date / end_date | DATE | 合同起止时间 |
| use_school_asset | TINYINT | 是否以使用学校仪器设备和场地为主 |
| charge_mgmt_fee | TINYINT | 是否收取管理费 |
| mgmt_fee_amount | DECIMAL(14,2) | 收取管理费金额(元) |
| leader_email | VARCHAR(128) | 负责人电子邮箱 |
| hx_register_date | DATE | 立项登记日期(科技处确认时写入) |
| verified_amount | DECIMAL(14,2) | 科技处核定到账金额(元,验收时写入) |

> 到账累计=rp_funding_record 实时 SUM(单一事实源,不冗余主表);验收「使用经费」存 rp_hx_flow.used_amount;大写金额不入库(渲染时计算)。

### 4.1.2 rp_zs_project 纵向扩展表(**推迟,本期不建**)

> 2026-10-07 定案:纵向 PDF 重构需求未确认前,任何纵向字段集都是猜——34 列平移是旧台账形态,纵向 PDF 模板定稿后字段集会重定义,现在建表=白做一遍(YAGNI)。

- **本期(横向先行)**:不建 rp_zs_project、**不迁 301 条纵向台账数据**、不搬纵向台账 CRUD/导入导出/统计图表模块;主表 kind=1 仅预留
- **纵向阶段(后续独立立项)**:需求确认(纵向 PDF 模板/表单字段)→ 建 rp_zs_project(按纵向 PDF 模板定字段集,金额 DECIMAL(14,2) 元口径)→ 台账数据导入(旧快照/Excel 重放,导入层×10000)→ 统计图表模块移植(SQL 按 JOIN 扩展表改造)→ 纵向申报 PDF 化(评估复用/泛化横向流程骨架)
- **数据不丢**:research_db.sql 快照随旧仓库归档;纵向阶段从快照或原 Excel 重放导入

### 4.2 rp_hx_flow 流程单主表(新)

```sql
project_id    BIGINT       COMMENT '→rp_project',
flow_type     VARCHAR(16)  COMMENT 'register登记/change变更/acceptance验收',
status        TINYINT      COMMENT '0草稿/1已提交待回扫/2已回扫待审核/3通过/4退回',
-- 变更单字段
change_type   VARCHAR(32)  COMMENT '字典 change_type,可多选逗号分隔',
change_reason TEXT         COMMENT '申请变更原因',
change_content TEXT        COMMENT '变更内容说明',
new_amount    DECIMAL(14,2) COMMENT '变更后经费(元,change_type含项目经费时必填)',
new_end_date  DATE         COMMENT '变更后结束日(含执行周期时必填)',
new_leader_id BIGINT       COMMENT '变更后负责人→sys_user(含负责人变更时必填,须已有账号)',
before_json   JSON         COMMENT '变更前字段快照(改写项目字段前存)',
-- 验收单字段
accept_summary TEXT        COMMENT '项目完成情况总结',
is_on_time    TINYINT      COMMENT '是否按期完成',
outcome_form  VARCHAR(64)  COMMENT '成果形式(字典 hx_outcome_form,可多选逗号分隔)',
used_amount   DECIMAL(14,2) COMMENT '使用经费(元)',
report_content MEDIUMTEXT  COMMENT '附件4报告正文(纯文本分段,\\n\\n分段;页数不限可能超64KB,用MEDIUMTEXT)',
-- 时间戳
submit_time   DATETIME, scan_time DATETIME, confirm_time DATETIME
-- 注:不设 reject_reason 冗余列,退回意见唯一事实源=rp_hx_flow_record.opinion
-- 通用字段同既有约定;索引 (project_id, flow_type)、(status, flow_type)
```

### 4.2.1 rp_hx_flow_record 横向流程动作表(新,独立于 rp_approval_record)

```sql
project_id  BIGINT       COMMENT '→rp_project(冗余,按项目聚合全部流程单动作)',
flow_id     BIGINT       COMMENT '→rp_hx_flow',
action      VARCHAR(16)  COMMENT 'submit提交/withdraw撤回/upload_scan回扫/precheck_reject预检退回/reject退回/resubmit重交/verify核定/approve通过',
operator_id BIGINT, operator_name VARCHAR(64),
opinion     VARCHAR(500) COMMENT '退回意见/核定备注'
-- 通用字段;索引 (flow_id)、(project_id)
```

- **独立动作表**(旧 rp_approval_record 随旧项目废弃,新项目无纵向对应表);纵向流程将来需求明确时,再评估泛化此表或另建——现在不猜
- 时间线组件吃统一 DTO,纵向将来接入无需改组件
- project_id 冗余:项目详情一条查询聚合登记+变更+验收全部动作轨迹

### 4.3 rp_member 项目成员表(新建,一次建齐横向所需列;纵向阶段复用此表不加列)

```sql
project_id  BIGINT       COMMENT '→rp_project',
member_type VARCHAR(16)  COMMENT '字典 member_type:教师/学生/校外人员',
name        VARCHAR(64)  COMMENT '姓名',
unit        VARCHAR(255) COMMENT '所在单位',
title       VARCHAR(32)  COMMENT '职称(字典;纵向阶段用)',
degree      VARCHAR(32)  COMMENT '学位(字典;纵向阶段用)',
major       VARCHAR(64)  COMMENT '专业/学科专业(登记表/验收表成员栏)',
duty        VARCHAR(500) COMMENT '承担本项目的工作任务(描述性文本;纵向申报的承担类型亦复用此列)',
student_no  VARCHAR(32)  COMMENT '学号(学生成员)',
mentor      VARCHAR(64)  COMMENT '指导老师(学生成员)',
ratio       DECIMAL(5,2) COMMENT '承担任务比例%(验收分工)',
amount      DECIMAL(14,2) COMMENT '承担金额(元,验收分工)',
sort_no     INT DEFAULT 0
-- 通用字段;索引 (project_id)
```

> 终审核对登记表/验收表成员栏(姓名/所在单位/职称/专业/承担任务)后补 major 列、duty 扩容 varchar(64)→(500);**不拼接 members 文本列**(主表无此列,决策#30)。

### 4.4 rp_attachment biz_type 扩展

新增枚举:`hx_apply`(申报佐证材料)、`hx_scan`(盖章扫描件)、`hx_final`(终版数据PDF)、`hx_pdf`(过程生成的待签PDF,含作废版本)、`hx_extra`(验收图纸等另附材料)。

### 4.5 sys_template 模板表(新)

```sql
tpl_type    VARCHAR(32)  COMMENT 'hx_register/hx_change/hx_acceptance/hx_report',
version     INT          COMMENT '版本号,递增',
file_path   VARCHAR(500) COMMENT 'upload/templates/ 下相对路径',
placeholder_check TEXT   COMMENT '上传时校验通过的占位符清单(JSON)',
status      TINYINT      COMMENT '1启用 0历史版本',
remark      VARCHAR(255)
-- 唯一索引 (tpl_type, version);启用版本唯一(tpl_type+status=1)
```

### 4.6 新增字典

| dict_type | 取值 | 模板联动 |
|---|---|---|
| hx_project_type | 技术开发、技术咨询、技术服务 | ⚠️ 是(登记表/变更表/验收表勾选组) |
| client_nature | 政府部门、企业、事业单位、研究机构、其他 | ⚠️ 是(勾选组) |
| hx_outcome_form | 技术报告、鉴定报告、专利、咨询报告、其他 | ⚠️ 是(勾选组) |
| change_type | 项目经费、执行周期、项目负责人、委托内容、其他约定 | ⚠️ 是(勾选组) |
| budget_subject_hx | 业务费、劳务费、绩效、业务接待费、协作费、其它 | ⚠️ 是(预算科目行) |

> **模板联动约束**:上表字典的取值印在 PDF 模板勾选组/科目行中。字典管理页对此类字典:①增删取值时强提醒「需同步更新 PDF 模板并重新上传」②修改取值标签名安全(渲染按当前字典标签动态输出)③内置取值沿用既有保护(可停用不可改名删除)。纵向既有字典(title/degree 等)不受模板联动影响。

### 4.7 rp_budget 预算明细表(新建)

```sql
project_id BIGINT        COMMENT '→rp_project',
subject    VARCHAR(64)   COMMENT '预算科目(横向=budget_subject_hx 6科目;纵向阶段加 budget_subject 11科目)',
amount     DECIMAL(14,2) COMMENT '金额(元;新建即元口径,无迁移)'
-- 唯一索引 (project_id, subject);通用字段
```

### 4.8 rp_funding_record 到账明细(新)

```sql
project_id     BIGINT        COMMENT '→rp_project(横向行,草稿期即存在)',
amount         DECIMAL(14,2) COMMENT '本笔到账(元)',
received_date  DATE          COMMENT '到账日期',
payer          VARCHAR(255)  COMMENT '付款方(默认=委托单位,可改)',
bank_flow_no   VARCHAR(64)   COMMENT '银行流水号(选填)',
remark         VARCHAR(255)
-- 索引 (project_id, received_date);逻辑删除
```

- **录入者仅 admin**(到账源头=计财处/银行流水);教师端只读展示本人项目到账明细+累计
- **累计不入库**(单一事实源:明细为真源,展示/统计实时 SUM,永无不同步;主表 15 列无任何金额列,决策#30)
- 录入后自动发教师站内通知(「您的项目《XX》到账 XX 元」)
- **与项目状态完全解耦**:0 笔正常;验收后仍可补录(尾款/质保金),verified_amount 不随之变化(金额三层模型,见决策#17)

## 五、PDF 生成子系统

### 5.1 部署形态

```
浏览器 ──HTTPS(443,证书)──▶ Nginx ──HTTP──▶ Spring Boot(8080,仅监听127.0.0.1)
                            │                (TLS 终止在 Nginx;证书优先学校信息中心
                            └─ 前端打包产物    域名+证书,次选校内CA,自签亦可)
scitech/                        ← 部署包目录=一个软件,整体拷贝即部署
├── sc_backend.jar
├── libreoffice/                ← LibreOffice 便携版(免安装,~400MB)
├── mysqldump.exe               ← 备份组件(决策#34,启动自检)
├── upload/                     ← 附件/扫描件/PDF/模板(sc.upload-dir 可改指数据盘)
└── backup/                     ← 备份 zip(sc.backup-dir)
```

- Java 侧用 JODConverter LocalOfficeProcessManager 管理 soffice 进程池(常驻,转换 1-3 秒/份)
- `sc.office-home` 配置指向便携版目录;Linux 部署时替换为 Linux 版目录(同为免安装解压版)

### 5.2 渲染管线

```
结构化数据组装(含勾选状态/大写金额/万元换算/日期格式化)
  → poi-tl 填充当前启用版 docx 模板
  → JODConverter 转 PDF
  → 落盘 upload/hx/{projectId}/{flowType}/{flowId}/
  → rp_attachment 登记(biz_type=hx_pdf)
```

- **勾选框**:模板占位符输出 `☑`/`□`(Unicode 字符,LibreOffice 转 PDF 正常)
- **大写金额**:Java 工具类元→人民币大写(壹贰叁…整)
- **签字/盖章/意见栏**:模板中留空
- **页数校验**:登记表/变更表/验收登记表渲染后校验页数≤2,超页报错拒生成(提示模板或内容超长)
- **元数据**:PDF Properties 写入生成时间、流程单号、操作人

### 5.3 模板管理(admin)

- 页面:4 类模板列表(当前启用版+版本历史)、下载当前 docx、上传新版
- 上传校验:①必须是 .docx ②占位符完整性比对(缺哪些占位符明确列出)③用**内置静态样例数据**(代码常量,决策#34)试渲染生成预览 PDF 供肉眼核对 ④(登记/变更/验收)试渲染页数≤2
- 通过校验后新版本启用,旧版转历史(status=0);已归档 PDF 不受影响
- 配套《模板制作说明》文档:全部占位符清单+制作步骤(在学校新表 .doc 基础上加占位符另存 .docx)

### 5.4 模板初版制作(开发期一次性)

用本机 Word 将学校 4 张 .doc 转为 .docx,在对应位置插入占位符(示例文字替换为占位符,填表说明/表尾注释保留),作为初始模板入库。

## 六、后端接口(/hx 前缀,均鉴权)

### 6.1 教师端

| 接口 | 说明 |
|---|---|
| POST /hx/register/draft | 登记草稿暂存(project+members[]+budgets[]+横向字段一次提交) |
| POST /hx/register/submit | 提交登记(校验必填;生成待签PDF;status→1) |
| POST /hx/{flowId}/withdraw | 撤回(状态1→0;状态2→1) |
| POST /hx/{flowId}/resubmit | 退回后修改重交(重新生成PDF,旧件作废) |
| POST /hx/{flowId}/scan | 上传盖章扫描件(biz_type=hx_scan,doc_type 按单据分类四选一:登记表/变更表/验收登记表/验收报告;**每类仅最新一份生效,重传覆盖旧版作废**;status 1→2) |
| POST /hx/change/draft\|submit | 变更单(projectId 必须已登记确认;横向全年开放,**无窗口控制**决策#34) |
| POST /hx/acceptance/draft\|submit | 验收单(含成员比例/学生参与/报告正文) |
| DELETE /hx/{flowId} | 删草稿(仅 status=0;登记草稿连带逻辑删项目行+成员/预算/附件,**已有到账记录禁删**决策#34) |
| GET /hx/my | 我的横向项目及各流程单状态 |
| GET /hx/{flowId} | 详情(数据+时间线+附件列表) |

### 6.2 PDF 与下载

| 接口 | 说明 |
|---|---|
| GET /hx/{flowId}/pdf | 当前版待签 PDF(教师本人/admin;inline 预览) |
| GET /hx/{flowId}/pdf/final | 终版数据 PDF(归档后可看) |
| GET /hx/attachment/{id}/download | 附件/扫描件下载(教师仅本人项目) |

### 6.3 科技处端(admin)

| 接口 | 说明 |
|---|---|
| GET /hx/audit?flowType=&status= | 审批列表(类型标签+状态筛选) |
| POST /hx/{flowId}/review | 通过:登记/变更/验收各自副作用(§3.3);验收需先录 verified_amount |
| POST /hx/{flowId}/reject | 退回(意见必填,status→4) |
| GET/POST /hx/template… | 模板列表/下载/上传(含校验+试渲染)/启用 |
| GET/POST /system/dict… | 字典管理(补齐阶段2遗留界面;横向5字典带模板联动标记与提醒) |
| POST /hx/backup/run | 手动触发备份;GET /hx/backup/list 下载管理 |
| GET/POST/DELETE /hx/funding | 到账明细:admin 按 projectId 增删查(删除=逻辑删,累计实时 SUM 不回写);教师 GET 本人项目明细(只读) |
| GET /hx/projects | 横向项目列表(admin,「横向项目管理」页数据源;含到账累计 SUM、在途流程单状态) |

> 预检退回复用 `POST /hx/{flowId}/reject`(允许 status=1 或 2 时调用,意见栏注明预检)。

### 6.4 权限与隔离

- 教师仅见/操作本人项目(apply_user_id 过滤,后端强制);PDF/附件下载鉴权
- 模板管理、备份、审批仅 admin;viewer 不参与横向

## 七、前端页面

### 7.1 教师端(菜单「横向项目」)

| 页面 | 内容 |
|---|---|
| 我的横向项目 | 项目卡片/列表(状态:登记中/在研/变更中/已结题)+各流程单入口 |
| 登记向导 | 3步:基本信息(类型/委托单位/经费/起止…)→成员及分工→预算6科目+简介/进度+附件(合同扫描件**可选**上传,doc_type=合同);提交确认页内嵌 PDF 预览 |
| 发起变更 | 选变更类型(多选)+内容+原因 |
| 发起验收 | 经费/成员比例(rp_member 补 ratio/amount)/学生参与(=rp_member 学生行,补 student_no/mentor)/成果形式多选/完成总结/报告正文(分段纯文本) |
| 流程详情 | 数据+**PDF 在线预览**+扫描件上传/查看+时间线(谁何时做了什么,退回意见醒目) |
| 项目详情-到账 | 本人项目到账明细列表+累计(**只读**,来源科技处录入;到账自动通知跳转至此) |

### 7.2 科技处端(菜单「横向审批」「模板管理」「字典管理」「数据备份」)

- **横向审批列表**:flowType 标签(登记/变更/验收)+项目+负责人+院部+状态+提交时间;status=1 单子带「可预检」标记
- **审批页**:左侧 PDF 预览为主体(可翻页缩放),右侧操作面板:申请摘要/盖章扫描件(点击大图)/流转时间线/[通过](验收含核定额录入)/[退回](意见必填,status=1/2 均可退)
- **模板管理**:§5.3
- **字典管理**(补阶段2遗留):全部字典类型的取值增删改停;横向 5 字典显示「模板联动」标记,增删取值弹强提醒;内置值保护沿用
- **数据备份**:备份列表(时间/大小)+立即备份+下载+删除
- **横向项目管理**(决策#21):编号/名称/负责人/院部/委托单位/合同经费/到账累计/执行状态(在研/变更中/已结题)/登记日期;点行展开项目详情含**到账明细管理**(admin 增删,每次录入自动通知教师)

## 八、数据备份设计

- **定时**:Spring @Scheduled 每周一次(可配 cron);流程=mysqldump 全库 → 与 upload/ 目录一起 zip → backup/ 目录 → 超过保留数(默认8)删最旧
- **手动**:admin 触发同一流程(异步执行,列表刷新看进度)
- **部署依赖(决策#34)**:mysqldump 可执行文件随部署包携带(或 `sc.mysqldump-path` 配置指向),启动自检缺失则备份功能置灰并提示
- 备份包命名:`backup_yyyyMMdd_HHmmss.zip`(库 SQL+完整 upload)
- 仅 admin 可访问;backup 目录不在附件鉴权范围内,独立接口;zip 删除=物理删(区别于附件逻辑删)

## 九、约束清单(实现与验收依据)

### 业务约束
1. 纸面意见/签字/盖章栏一律留空——系统不代签
2. 登记确认后项目字段锁定,任何改动必须走变更单(变更自动改写+留 diff)
3. 环节按需:无变更不填变更表;验收单每项目至多 1 张生效
4. **平台定位,模块交付**:本期交付横向模块,平台底座(认证/用户/字典/消息/附件/模板/备份)按多模块架构搭建(决策#32);横向统计口径(项目数/到账经费等)二期需求确认;主表 kind=1 仅预留
5. **库内金额单一口径=元**(决策#25):新项目原生成立,无迁移回归;万元仅存在于边界层(导入×10000/导出÷10000/展示换算);页面提供元↔万元显示切换,记忆偏好(决策#26)
6. 教师填报,科技处审批;委托单位/所在单位意见线下

### 技术约束
7. 部署包含 LibreOffice 便携版(~400MB);换 Linux 服务器需替换 Linux 版目录
8. docx 模板上传强制占位符校验+试渲染预览+页数校验(≤2)
9. PDF/扫描件下载鉴权;模板上传/备份/审批仅 admin;单文件≤50MB 沿用
10. 扫描件、PDF 存校内服务器本地磁盘,数据不出校
11. 备份=全库+upload 完整目录,admin 可随时导出下载
12. rp_attachment 存相对路径(sc.upload-dir 可配),存储位置可迁移不锁死
13. 字典开放=取值维护(含模板联动提醒);**不做**管理员自定义新表单字段——制式 PDF 模板无格子可渲染,字段集与模板字段集强制一致

### 测试策略(沿用 2026-09-03 验证策略)
14. 业务逻辑脚本批量接口测试(仿 test_declare_api.py,覆盖状态机全迁移/越权隔离/编号唯一/变更diff)
15. 每类模板样例数据生成 PDF 人工核对排版+页数断言
16. UI 渲染+接口连通即止,交互流程用户手动验收

## 十、待确认(不阻塞开发)

1. 部署服务器 OS(Windows/Linux,影响 LibreOffice 便携版选哪个)
2. 变更"项目负责人"时新负责人无账号的处理(现场建号 or 驳回)——初版按"必须有账号"校验
3. 横向统计图表口径(二期):项目数/到账经费/委托单位性质分布等,二期需求确认
