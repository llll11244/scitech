import request from './request'

/** 登录:返回 { token, username, realName, role } */
export function login(data) {
  return request.post('/auth/login', data)
}

/** 当前用户信息 */
export function getMe() {
  return request.get('/users/me')
}

/** 修改自己的密码 */
export function changePassword(data) {
  return request.put('/users/password', data)
}
