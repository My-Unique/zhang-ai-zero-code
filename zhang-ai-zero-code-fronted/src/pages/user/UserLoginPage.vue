<script setup lang="ts">
import { reactive } from 'vue'
import { useRouter } from 'vue-router'
import { message } from 'ant-design-vue'
import { userLogin } from '@/api/userController.ts'
import { useLoginUserStore } from '@/stores/loginUser.ts'

const router = useRouter()
const loginUserStore = useLoginUserStore()

const formState = reactive<API.UserLoginRequest>({
  userAccount: '',
  userPassword: '',
})

/**
 * 提交表单
 */
const handleSubmit = async (values: API.UserLoginRequest) => {
  const res = await userLogin(values)
  // 登录成功，把登录态保存到全局状态中
  if (res.data.code === 0 && res.data.data) {
    await loginUserStore.fetchLoginUser()
    message.success('登录成功')
    router.push({
      path: '/',
      replace: true,
    })
  } else {
    message.error('登录失败，' + res.data.message)
  }
}
</script>

<template>
  <div id="userLoginPage">
    <section class="login-hero">
      <div class="brand-panel">
        <img class="brand-photo" src="@/assets/auth-hero-static.png" alt="登录页动漫氛围图" />
      </div>

      <div class="login-card">
        <div class="login-header">
          <h2 class="title">用户登录</h2>
          <div class="desc">登录后开始生成你的 AI 应用</div>
        </div>

        <a-form
          class="login-form"
          :model="formState"
          name="loginForm"
          autocomplete="off"
          layout="vertical"
          @finish="handleSubmit"
        >
          <a-form-item label="账号" name="userAccount" :rules="[{ required: true, message: '请输入账号' }]">
            <a-input v-model:value="formState.userAccount" placeholder="请输入账号" size="large" />
          </a-form-item>
          <a-form-item
            label="密码"
            name="userPassword"
            :rules="[
              { required: true, message: '请输入密码' },
              { min: 8, message: '密码不能小于 8 位' },
            ]"
          >
            <a-input-password v-model:value="formState.userPassword" placeholder="请输入密码" size="large" />
          </a-form-item>

          <div class="form-extra">
            <span class="muted">没有账号？</span>
            <RouterLink to="/user/register">立即注册</RouterLink>
          </div>

          <a-form-item class="submit-item">
            <a-button class="submit-button" type="primary" html-type="submit" size="large">登录</a-button>
          </a-form-item>
        </a-form>
      </div>
    </section>
  </div>
</template>

<style scoped>
#userLoginPage {
  width: 100%;
}

.login-hero {
  display: grid;
  grid-template-columns: minmax(0, 1fr) 420px;
  gap: 56px;
  align-items: center;
  min-height: 620px;
  padding: 40px 28px;
}

.brand-panel {
  position: relative;
  overflow: hidden;
  min-height: 420px;
  isolation: isolate;
  background: #eff6ff;
  border-radius: 8px;
  box-shadow: 0 18px 48px rgba(15, 23, 42, 0.08);
}

.brand-panel::after {
  position: absolute;
  inset: 0;
  content: '';
  pointer-events: none;
  background:
    linear-gradient(180deg, rgba(255, 255, 255, 0.02), rgba(33, 98, 168, 0.14)),
    radial-gradient(circle at 24% 22%, rgba(255, 255, 255, 0.24), transparent 28%);
  z-index: 2;
}

.brand-photo {
  display: block;
  width: 100%;
  height: 100%;
  min-height: 420px;
  object-fit: cover;
  object-position: center;
}

.login-card {
  width: 100%;
  padding: 40px;
  background: #fff;
  border: 1px solid #eef0f4;
  border-radius: 8px;
  box-shadow: 0 18px 48px rgba(15, 23, 42, 0.08);
}

.login-header {
  margin-bottom: 28px;
}

.title {
  margin: 0;
  font-size: 28px;
  font-weight: 700;
  line-height: 1.3;
  color: #111827;
}

.desc {
  margin-top: 8px;
  font-size: 14px;
  color: #6b7280;
}

.login-form :deep(.ant-form-item) {
  margin-bottom: 22px;
}

.login-form :deep(.ant-form-item-label > label) {
  height: 22px;
  font-size: 14px;
  color: #374151;
}

.login-form :deep(.ant-input),
.login-form :deep(.ant-input-affix-wrapper) {
  border-radius: 6px;
}

.form-extra {
  display: flex;
  justify-content: flex-end;
  gap: 6px;
  margin: -4px 0 24px;
  font-size: 14px;
}

.muted {
  color: #8c8c8c;
}

.submit-item {
  margin-bottom: 0 !important;
}

.submit-button {
  width: 100%;
  height: 44px;
  font-weight: 600;
  border-radius: 6px;
  box-shadow: 0 8px 18px rgba(22, 119, 255, 0.22);
}

@media (max-width: 900px) {
  .login-hero {
    grid-template-columns: 1fr;
    gap: 24px;
    min-height: auto;
    padding: 24px 0;
  }

  .brand-panel {
    min-height: auto;
    aspect-ratio: 16 / 10;
  }

  .brand-photo {
    min-height: 0;
  }

  .login-card {
    padding: 32px;
  }
}

@media (max-width: 520px) {
  .login-hero {
    padding: 8px 0 16px;
  }

.brand-panel {
    aspect-ratio: 4 / 3;
  }

  .login-card {
    padding: 28px 22px;
  }
}
</style>
