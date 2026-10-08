import request from './request'

/* ==================== 备份子系统(Task27,仅 admin,spec §八) ==================== */

/** 状态:{available,message,cron,keep,running}(自检失败 available=false→按钮置灰提示) */
export const backupStatus = () => request.get('/backup/status')
/** 备份列表(新→旧):[{name,size,time}] */
export const backupList = () => request.get('/backup/list')
/** 手动触发(异步):{started,message}——false=已有备份进行中 */
export const backupRun = () => request.post('/backup/run')
/** 下载备份 zip → Blob(经 axios 附 token;后端白名单+canonical 防路径遍历) */
export const backupDownload = (name) =>
  request.get('/backup/download', { params: { name }, responseType: 'blob', timeout: 120000 })
/** 删除备份(物理删) */
export const backupDelete = (name) => request.delete('/backup', { params: { name } })
