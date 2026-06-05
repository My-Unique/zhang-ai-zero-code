import ACCESS_ENUM from '@/accessEnum'
import checkAccess from '@/checkAccess'
import router from '@/router'
import { useLoginUserStore } from '@/stores/loginUser'
import { message } from 'ant-design-vue'

let firstFetchLoginUser = true

router.beforeEach(async (to, from, next) => {
  const loginUserStore = useLoginUserStore()
  let loginUser = loginUserStore.loginUser

  if (firstFetchLoginUser) {
    await loginUserStore.fetchLoginUser()
    loginUser = loginUserStore.loginUser
    firstFetchLoginUser = false
  }

  const needAccess = (to.meta?.access as string) ?? ACCESS_ENUM.NOT_LOGIN
  if (!checkAccess(loginUser, needAccess)) {
    message.error('没有权限')
    next(`/user/login?redirect=${to.fullPath}`)
    return
  }

  next()
})
