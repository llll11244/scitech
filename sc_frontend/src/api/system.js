import request from './request'

/* ==================== 平台管理接口(N4 Task26:字典/用户管理页数据源,仅 admin) ==================== */

/* ---------- 字典管理 ---------- */
/** 管理端全量取值(含停用项;type 空=全部类型) */
export const manageDicts = (type) =>
  request.get('/dicts/manage', { params: type ? { type } : {} })
/** 新增字典项(联动类型响应 data.remind=模板同步提醒文案) */
export const addDict = (type, value, label) =>
  request.post('/dicts', null, { params: { type, value, label } })
/** 改 label/sortOrder/status(内置项 label 保护在后端) */
export const updateDict = (id, data) => request.put(`/dicts/${id}`, data)
/** 删字典项(内置拒;联动类型响应带 remind) */
export const deleteDict = (id) => request.delete(`/dicts/${id}`)

/* ---------- 用户管理(建号/停启/重置密码,无删) ---------- */
/** 分页列表(q=用户名/姓名模糊)→ {records,total}(不含密码) */
export const listUsers = (params) => request.get('/users', { params })
/** 建号(密码≥8 位含字母与数字) */
export const createUser = (data) => request.post('/users', data)
/** 启停(停用即时生效;不可停用自己) */
export const setUserStatus = (id, status) => request.put(`/users/${id}/status`, { status })
/** 重置密码 */
export const resetUserPassword = (id, password) =>
  request.put(`/users/${id}/password`, { password })
