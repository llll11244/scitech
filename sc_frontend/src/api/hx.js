import request from './request'

/* ==================== 横向项目教师端(N3 三流程) ==================== */

/* ---------- 草稿保存 / 提交 ---------- */
/** 登记草稿保存(flowId 空=新建,非空=整体覆盖)→ flowId */
export const saveRegisterDraft = (data) => request.post('/hx/register/draft', data)
export const submitRegister = (flowId) => request.post('/hx/register/submit', { flowId })
/** 变更草稿保存(data 必含 projectId;提交强校验项目已确认)→ flowId */
export const saveChangeDraft = (data) => request.post('/hx/change/draft', data)
export const submitChange = (flowId) => request.post('/hx/change/submit', { flowId })
/** 验收草稿保存(members 全量覆盖,必传完整数组)→ flowId */
export const saveAcceptanceDraft = (data) => request.post('/hx/acceptance/draft', data)
export const submitAcceptance = (flowId) => request.post('/hx/acceptance/submit', { flowId })

/* ---------- 流程操作 ---------- */
/** 撤回:已提交(1)→草稿(0);已回扫(2)→已提交(1) */
export const withdraw = (flowId) => request.post(`/hx/${flowId}/withdraw`)
/** 盖章扫描件上传(重传同类型覆盖) */
export const uploadScan = (flowId, file, docType) => {
  const form = new FormData()
  form.append('file', file)
  form.append('docType', docType)
  return request.post(`/hx/${flowId}/scan`, form, {
    headers: { 'Content-Type': 'multipart/form-data' },
    timeout: 60000
  })
}
/** 删草稿(仅 status=0;登记草稿连带删项目/成员/预算) */
export const deleteDraft = (flowId) => request.delete(`/hx/${flowId}`)

/* ---------- 查询 ---------- */
export const myProjects = () => request.get('/hx/my')
export const flowDetail = (flowId) => request.get(`/hx/${flowId}`)
/** 用户搜索(变更负责人远程搜索):[{id,username,name}] */
export const searchLeaders = (q) => request.get('/hx/leaders', { params: { q } })

/* ---------- 字典 / 院部(平台既有接口) ---------- */
/** 字典启用项 → [{label,value}](排序=sortOrder) */
export async function listDict(type) {
  const rows = (await request.get('/dicts', { params: { type } })) || []
  return rows.map((d) => ({ label: d.dictLabel, value: d.dictValue }))
}
/** 院部列表 → [{id,name}] */
export async function listDepts() {
  const rows = (await request.get('/depts')) || []
  return rows.map((d) => ({ id: d.id, name: d.name }))
}
export const getConfig = (key) => request.get(`/config/${key}`)
/** 修改值型配置(admin,仅白名单键:hx_mgmt_fee_rate 管理费比例%) */
export const putConfig = (key, value) => request.put(`/config/${key}`, { value })

/* ---------- blob 型:分屏预览与交付下载 ---------- */
/** 表单数据直出 docx 预览流(blob);tplType=验收流程双模板切换(hx_acceptance/hx_report) */
export const previewDocx = (flowType, data, tplType) =>
  request.post('/hx/preview', { flowType, data, tplType }, { responseType: 'blob', timeout: 60000 })

/**
 * 待签交付下载(blob,经 axios 附 token;直链 a.href 不带 Authorization 不可用)。
 * kind=docx|pdf;doc=模板类型(验收流程两张:hx_acceptance/hx_report,其余流程可省略)。
 */
export const downloadDeliver = (flowId, kind = 'docx', doc) =>
  request.get(`/hx/${flowId}/deliver/${kind}`, {
    params: doc ? { doc } : {},
    responseType: 'blob',
    timeout: 60000
  })

/** 扫描件/申报佐证附件内容下载(blob;白名单 hx_scan/hx_apply/hx_extra,后端 owner/admin 鉴权) */
export const downloadAttachment = (id) =>
  request.get(`/hx/attachment/${id}/download`, { responseType: 'blob', timeout: 60000 })

/* ==================== N4 科技处端接口(Task26) ==================== */

/* ---------- 审批(admin,spec §6.3) ---------- */
/** 审批列表:flowType/status 可选过滤 → [{flowId,projectId,flowType,status,name,leaderName,deptName,…}] */
export const auditList = (params) => request.get('/hx/audit', { params })
/** 通过:验收流程须传 verifiedAmount(核定到账,元) */
export const reviewFlow = (flowId, verifiedAmount) =>
  request.post(`/hx/${flowId}/review`, verifiedAmount === undefined ? {} : { verifiedAmount })
/** 退回:意见必填;isPrecheck 仅 status=1 可选(盖章前预检,前端守语义) */
export const rejectFlow = (flowId, opinion, isPrecheck = false) =>
  request.post(`/hx/${flowId}/reject`, { opinion, isPrecheck })
/** 教师退回单修改重交(4→1,旧待签+扫描件作废) */
export const resubmit = (flowId) => request.post(`/hx/${flowId}/resubmit`)

/* ---------- 项目管理+到账(admin,spec §6.3/§4.8) ---------- */
/** 全部横向项目聚合(九列+到账累计+执行状态+在途流程单) */
export const projectList = () => request.get('/hx/projects')
/** 到账明细+累计(admin 按项目查) */
export const adminFunding = (projectId) => request.get('/hx/funding', { params: { projectId } })
/** 录入到账(元;录入即通知教师) */
export const addFunding = (data) => request.post('/hx/funding', data)
/** 删到账(逻辑删,累计实时回落) */
export const deleteFunding = (id) => request.delete(`/hx/funding/${id}`)
/** 教师到账只读(本人项目强制)→ {records,total} */
export const teacherFunding = (projectId) => request.get(`/hx/${projectId}/funding`)

/* ---------- 合同附件(教师,登记第三步可选上传) ---------- */
export const uploadContract = (flowId, file, docType = '合同') => {
  const form = new FormData()
  form.append('file', file)
  form.append('docType', docType)
  return request.post(`/hx/${flowId}/attach`, form, {
    headers: { 'Content-Type': 'multipart/form-data' },
    timeout: 60000
  })
}

/* ---------- 终版交付下载(验收确认后归档件,教师本人/admin) ---------- */
export const downloadDeliverFinal = (flowId, kind = 'docx', doc) =>
  request.get(`/hx/${flowId}/deliver-final/${kind}`, {
    params: doc ? { doc } : {},
    responseType: 'blob',
    timeout: 60000
  })
