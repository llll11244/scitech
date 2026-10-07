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
