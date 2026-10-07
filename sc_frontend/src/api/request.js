import axios from 'axios'
import { ElMessage } from 'element-plus'
import router from '../router'

const request = axios.create({
  baseURL: '/api',
  timeout: 15000
})

// 请求拦截:附带 token
request.interceptors.request.use((config) => {
  const token = localStorage.getItem('sc_token')
  if (token) {
    config.headers.Authorization = `Bearer ${token}`
  }
  return config
})

// 响应拦截:统一处理业务码与 401
request.interceptors.response.use(
  (response) => {
    // blob 响应(文件下载)直接返回,不走业务码判断
    if (response.config.responseType === 'blob') {
      return response.data
    }
    const res = response.data
    if (res.code !== 0) {
      ElMessage.error(res.msg || '请求失败')
      return Promise.reject(new Error(res.msg))
    }
    return res.data
  },
  (error) => {
    if (error.response?.status === 401) {
      localStorage.removeItem('sc_token')
      localStorage.removeItem('sc_user')
      ElMessage.warning('登录已过期,请重新登录')
      router.push('/login')
    } else {
      ElMessage.error(error.response?.data?.msg || '网络异常,请稍后重试')
    }
    return Promise.reject(error)
  }
)

export default request
