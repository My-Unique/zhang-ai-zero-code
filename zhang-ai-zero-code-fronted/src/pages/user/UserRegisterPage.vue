<script setup lang="ts">
import { reactive } from 'vue'
import { useRouter } from 'vue-router'
import { message } from 'ant-design-vue'
import { userRegister } from '@/api/userController.ts'

const router = useRouter()

const formState = reactive<API.UserRegisterRequest>({
  userAccount: '',
  userPassword: '',
  checkPassword: '',
})

/**
 * 校验确认密码
 */
const validateCheckPassword = async (_rule: unknown, value: string) => {
  if (!value) {
    return Promise.reject('请再次输入密码')
  }
  if (value !== formState.userPassword) {
    return Promise.reject('两次输入的密码不一致')
  }
  return Promise.resolve()
}

/**
 * 提交表单
 */
const handleSubmit = async (values: API.UserRegisterRequest) => {
  const res = await userRegister(values)
  if (res.data.code === 0 && res.data.data) {
    message.success('注册成功，请登录')
    router.push({
      path: '/user/login',
      replace: true,
    })
  } else {
    message.error('注册失败，' + res.data.message)
  }
}
</script>

<template>
  <div id="userRegisterPage">
    <section class="register-hero">
      <div class="brand-panel">
        <img class="brand-photo" src="@/assets/auth-hero-static.png" alt="注册页动漫氛围图" />
      </div>

      <div class="register-card">
        <div class="register-header">
          <h2 class="title">用户注册</h2>
          <div class="desc">创建账号后开始生成你的 AI 应用</div>
        </div>

        <a-form
          class="register-form"
          :model="formState"
          name="registerForm"
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

          <a-form-item
            label="确认密码"
            name="checkPassword"
            :rules="[
              { required: true, message: '请再次输入密码' },
              { min: 8, message: '确认密码不能小于 8 位' },
              { validator: validateCheckPassword, trigger: 'change' },
            ]"
          >
            <a-input-password v-model:value="formState.checkPassword" placeholder="请再次输入密码" size="large" />
          </a-form-item>

          <div class="form-extra">
            <span class="muted">已有账号？</span>
            <RouterLink to="/user/login">去登录</RouterLink>
          </div>

          <a-form-item class="submit-item">
            <a-button class="submit-button" type="primary" html-type="submit" size="large">注册</a-button>
          </a-form-item>
        </a-form>
      </div>
    </section>
  </div>
</template>

<style scoped>
#userRegisterPage {
  width: 100%;
}

.register-hero {
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
    linear-gradient(180deg, rgba(255, 255, 255, 0.02), rgba(33, 98, 168, 0.12)),
    radial-gradient(circle at 24% 22%, rgba(255, 255, 255, 0.18), transparent 28%);
}

.brand-photo {
  display: block;
  width: 100%;
  height: 100%;
  min-height: 420px;
  object-fit: cover;
  object-position: center;
}

.register-card {
  width: 100%;
  padding: 40px;
  background: #fff;
  border: 1px solid #eef0f4;
  border-radius: 8px;
  box-shadow: 0 18px 48px rgba(15, 23, 42, 0.08);
}

.register-header {
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

.register-form :deep(.ant-form-item) {
  margin-bottom: 22px;
}

.register-form :deep(.ant-form-item-label > label) {
  height: 22px;
  font-size: 14px;
  color: #374151;
}

.register-form :deep(.ant-input),
.register-form :deep(.ant-input-affix-wrapper) {
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
  .register-hero {
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

  .register-card {
    padding: 32px;
  }
}

@media (max-width: 520px) {
  .register-hero {
    padding: 8px 0 16px;
  }

  .brand-panel {
    aspect-ratio: 4 / 3;
  }

  .register-card {
    padding: 28px 22px;
  }
}
</style>
