import axios from 'axios'
import { message } from 'ant-design-vue'

const request = axios.create({
  baseURL: 'http://localhost:8123/api',
  timeout: 60000,
  withCredentials: true,
})

request.interceptors.response.use(
  (response) => {
    if (
      response.data?.code === 40100 &&
      !response.request.responseURL.includes('user/get/login') &&
      !window.location.pathname.includes('/user/login')
    ) {
      message.warning('请先登录')
      window.location.href = `/user/login?redirect=${encodeURIComponent(window.location.href)}`
    }
    return response
  },
  (error) => {
    const status = error?.response?.status
    const errorMessage = error?.response?.data?.message
    if (!error?.response) {
      message.error('无法连接后端服务，请确认 localhost:8123 已启动')
    } else if (status === 404) {
      message.error('请求接口不存在，请重启后端服务以加载最新接口')
    } else {
      message.error(errorMessage || `请求失败（${status || '未知状态'}）`)
    }
    return Promise.reject(error)
  },
)

export default request
