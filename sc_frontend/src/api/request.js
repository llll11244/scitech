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
  async (response) => {
    // blob 响应(文件下载/预览)直接返回;但后端业务异常走 HTTP 200+JSON(Result),
    // 此时拿到的是 application/json 的 Blob——解析出业务码按错误处理,防止把 JSON 存成 .docx
    if (response.config.responseType === 'blob') {
      if (response.data && response.data.type === 'application/json') {
        const text = await response.data.text()
        try {
          const res = JSON.parse(text)
          if (res.code !== 0) {
            ElMessage.error(res.msg || '请求失败')
            return Promise.reject(new Error(res.msg))
          }
        } catch (e) {
          // 非 Result 结构的 JSON(理论不会出现),原样放行
        }
      }
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
