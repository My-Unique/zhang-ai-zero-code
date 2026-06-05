import { createRouter, createWebHistory } from 'vue-router'
import ACCESS_ENUM from '@/accessEnum'
import HomePage from '@/pages/HomePage.vue'
import UserLoginPage from '@/pages/user/UserLoginPage.vue'
import UserRegisterPage from '@/pages/user/UserRegisterPage.vue'
import UserManagePage from '@/pages/admin/UserManagePage.vue'
import AppManagePage from '@/pages/admin/AppManagePage.vue'
import ChatManagePage from '@/pages/admin/ChatManagePage.vue'
import AppChatPage from '@/pages/app/AppChatPage.vue'
import AppEditPage from '@/pages/app/AppEditPage.vue'

const router = createRouter({
  history: createWebHistory(import.meta.env.BASE_URL),
  routes: [
    { path: '/', name: '主页', component: HomePage },
    { path: '/user/login', name: '用户登录', component: UserLoginPage },
    { path: '/user/register', name: '用户注册', component: UserRegisterPage },
    {
      path: '/app/chat/:id',
      name: '应用生成',
      component: AppChatPage,
      meta: { access: ACCESS_ENUM.USER, hideLayout: true },
    },
    {
      path: '/app/edit/:id',
      name: '编辑应用',
      component: AppEditPage,
      meta: { access: ACCESS_ENUM.USER },
    },
    {
      path: '/admin/userManage',
      name: '用户管理',
      component: UserManagePage,
      meta: { access: ACCESS_ENUM.ADMIN },
    },
    {
      path: '/admin/appManage',
      name: '应用管理',
      component: AppManagePage,
      meta: { access: ACCESS_ENUM.ADMIN },
    },
    {
      path: '/admin/chatManage',
      name: '对话管理',
      component: ChatManagePage,
      meta: { access: ACCESS_ENUM.ADMIN },
    },
  ],
})

export default router
