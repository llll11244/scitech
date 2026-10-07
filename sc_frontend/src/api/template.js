import request from './request'

/** 模板类型常量(与后端 TemplatePlaceholders 一致) */
export const TPL_TYPES = [
  { type: 'hx_register', label: '登记表' },
  { type: 'hx_change', label: '变更表' },
  { type: 'hx_acceptance', label: '验收登记表' },
  { type: 'hx_report', label: '验收报告' }
]

/** 上传新版模板(校验链在后端:占位符/试渲染/页数) */
export function uploadTemplate(file, tplType, remark) {
  const form = new FormData()
  form.append('file', file)
  form.append('tplType', tplType)
  if (remark) form.append('remark', remark)
  return request.post('/template/upload', form, { timeout: 180000 })
}

/** 版本历史(启用版在前) */
export function listTemplates(tplType) {
  return request.get('/template/list', { params: { tplType } })
}

/** 下载模板 docx → Blob */
export function downloadTemplate(id) {
  return request.get(`/template/${id}/download`, { responseType: 'blob' })
}

/** 样例数据预览 PDF → Blob(iframe objectURL 展示) */
export function previewTemplate(id) {
  return request.get(`/template/${id}/preview`, { responseType: 'blob' })
}
