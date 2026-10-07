import request from './request'

/* ==================== 消息通知(平台级) ==================== */

export function pageNotify(params) {
  return request.get('/notify/my', { params })
}

export function getUnreadCount() {
  return request.get('/notify/unread-count')
}

export function readNotify(id) {
  return request.put(`/notify/${id}/read`)
}

export function readAllNotify() {
  return request.put('/notify/read-all')
}
