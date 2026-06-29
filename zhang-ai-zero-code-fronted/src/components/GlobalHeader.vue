<template>
  <a-layout-header class="site-header">
    <div class="header-inner">
      <RouterLink to="/" class="brand">
        <span class="brand-logo">
          <img src="@/assets/logo.jpg" alt="小张 AI 零代码" />
        </span>
        <strong class="brand-name">小张 AI 零代码</strong>
      </RouterLink>

      <a-menu
        v-model:selectedKeys="selectedKeys"
        mode="horizontal"
        :items="menuItems"
        class="nav-menu"
        @click="handleMenuClick"
      />

      <div class="header-actions">
        <a-dropdown v-if="loginUserStore.loginUser.id" placement="bottomRight">
          <button class="user-trigger" type="button">
            <a-avatar :size="34" :src="loginUserAvatar" />
            <span class="user-copy">
              <strong>{{ loginUserDisplayName }}</strong>
              <small>{{ isAdmin ? '管理员' : '创作者' }}</small>
            </span>
            <DownOutlined />
          </button>
          <template #overlay>
            <a-menu>
              <a-menu-item key="logout" @click="doLogout">
                <LogoutOutlined />
                退出登录
              </a-menu-item>
            </a-menu>
          </template>
        </a-dropdown>

        <a-button v-else type="text" class="login-button" @click="router.push('/user/login')">
          登录
        </a-button>
      </div>
    </div>
  </a-layout-header>
</template>

<script setup lang="ts">
import { computed, h, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import {
  AppstoreOutlined,
  DownOutlined,
  HomeOutlined,
  LogoutOutlined,
  MessageOutlined,
  TeamOutlined,
} from '@ant-design/icons-vue'
import { message, type MenuProps } from 'ant-design-vue'
import ACCESS_ENUM from '@/accessEnum'
import checkAccess from '@/checkAccess'
import { userLogout } from '@/api/userController'
import { useLoginUserStore } from '@/stores/loginUser'
import defaultAvatar from '@/assets/default-avatar.png'

type MenuItem = NonNullable<MenuProps['items']>[number] & {
  meta?: {
    access?: string
  }
}

const loginUserStore = useLoginUserStore()
const router = useRouter()
const route = useRoute()
const selectedKeys = ref<string[]>([route.path])

const loginUserAvatar = computed(() => loginUserStore.loginUser.userAvatar?.trim() || defaultAvatar)
const loginUserDisplayName = computed(
  () =>
    loginUserStore.loginUser.userName?.trim() ||
    loginUserStore.loginUser.userAccount?.trim() ||
    '用户',
)
const isAdmin = computed(() => loginUserStore.loginUser.userRole === ACCESS_ENUM.ADMIN)

router.afterEach((to) => {
  selectedKeys.value = [to.path]
})

const originItems: MenuItem[] = [
  {
    key: '/',
    icon: () => h(HomeOutlined),
    label: '首页',
    title: '首页',
  },
  {
    key: '/admin/appManage',
    icon: () => h(AppstoreOutlined),
    label: '应用管理',
    title: '应用管理',
    meta: { access: ACCESS_ENUM.ADMIN },
  },
  {
    key: '/admin/chatManage',
    icon: () => h(MessageOutlined),
    label: '对话管理',
    title: '对话管理',
    meta: { access: ACCESS_ENUM.ADMIN },
  },
  {
    key: '/admin/userManage',
    icon: () => h(TeamOutlined),
    label: '用户管理',
    title: '用户管理',
    meta: { access: ACCESS_ENUM.ADMIN },
  },
]

const menuItems = computed<MenuProps['items']>(() =>
  originItems.filter((item) =>
    checkAccess(loginUserStore.loginUser, item.meta?.access ?? ACCESS_ENUM.NOT_LOGIN),
  ),
)

const handleMenuClick: MenuProps['onClick'] = ({ key }) => {
  router.push(key as string)
}

const doLogout = async () => {
  const res = await userLogout()
  if (res.data.code !== 0) {
    message.error(`退出失败：${res.data.message || '请稍后重试'}`)
    return
  }

  loginUserStore.setLoginUser({ userName: '未登录' })
  message.success('已退出登录')
  await router.push('/user/login')
}
</script>

<style scoped>
.site-header {
  position: sticky;
  top: 0;
  z-index: 50;
  height: 72px;
  padding: 0;
  background: rgb(255 255 255 / 88%);
  border-bottom: 1px solid rgb(234 236 240 / 85%);
  backdrop-filter: blur(20px);
}

.header-inner {
  display: flex;
  align-items: center;
  width: min(var(--page-width), calc(100% - 48px));
  height: 100%;
  margin: 0 auto;
}

.brand {
  display: flex;
  flex-shrink: 0;
  align-items: center;
  gap: 10px;
  min-width: 176px;
  margin-right: 36px;
  overflow: visible;
}

.brand-logo {
  display: grid;
  flex-shrink: 0;
  width: 36px;
  height: 36px;
  overflow: hidden;
  border-radius: 10px;
  box-shadow: 0 6px 14px rgb(91 92 240 / 18%);
  place-items: center;
}

.brand-logo img {
  width: 100%;
  height: 100%;
  object-fit: cover;
}

.brand-name {
  color: var(--color-title);
  font-size: 15px;
  font-weight: 700;
  line-height: 1;
  white-space: nowrap;
}

.nav-menu {
  flex: 1;
  min-width: 0;
  border-bottom: 0;
  background: transparent;
}

.nav-menu :deep(.ant-menu-item) {
  display: flex;
  align-items: center;
  height: 72px;
  padding-inline: 18px;
  font-weight: 500;
}

.header-actions {
  display: flex;
  align-items: center;
  gap: 12px;
  margin-left: 24px;
}

.user-trigger {
  display: flex;
  align-items: center;
  gap: 9px;
  padding: 4px 6px 4px 4px;
  color: var(--color-text);
  border: 0;
  border-radius: 12px;
  background: transparent;
  cursor: pointer;
  transition: background 0.2s ease;
}

.user-trigger:hover {
  background: #f4f5f9;
}

.user-copy {
  display: flex;
  flex-direction: column;
  min-width: 62px;
  text-align: left;
}

.user-copy strong {
  max-width: 96px;
  overflow: hidden;
  color: var(--color-title);
  font-size: 13px;
  line-height: 17px;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.user-copy small {
  color: var(--color-muted);
  font-size: 11px;
  line-height: 15px;
}

.login-button {
  font-weight: 600;
}

@media (max-width: 880px) {
  .header-inner {
    width: calc(100% - 28px);
  }

  .brand-name,
  .user-copy {
    display: none;
  }

  .brand {
    margin-right: 14px;
  }

  .header-actions {
    margin-left: 8px;
  }
}
</style>
