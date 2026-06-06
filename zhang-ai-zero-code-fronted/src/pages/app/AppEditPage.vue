<template>
  <main class="edit-page">
    <button class="back-button" type="button" @click="router.back()">
      <ArrowLeftOutlined />
      返回上一页
    </button>

    <section class="edit-layout">
      <aside class="context-panel">
        <span class="context-label">APPLICATION SETTINGS</span>
        <h1>完善应用信息</h1>
        <p>清晰的名称与封面能够帮助用户快速理解你的应用。</p>

        <div class="preview-card">
          <div class="preview-cover">
            <img v-if="form.cover" :src="form.cover" alt="" />
            <AppstoreOutlined v-else />
          </div>
          <div>
            <strong>{{ form.appName || '未命名应用' }}</strong>
            <span>{{ isAdmin ? '管理员编辑模式' : '创作者编辑模式' }}</span>
          </div>
        </div>

        <div class="permission-tip">
          <SafetyCertificateOutlined />
          <div>
            <strong>{{ isAdmin ? '管理员权限' : '所有者权限' }}</strong>
            <span>
              {{ isAdmin ? '可编辑名称、封面与优先级' : '当前仅支持修改应用名称' }}
            </span>
          </div>
        </div>
      </aside>

      <section class="form-panel">
        <a-spin :spinning="loading">
          <div class="form-heading">
            <div>
              <h2>基础信息</h2>
              <p>用于应用展示和管理的公开信息。</p>
            </div>
            <a-tag :color="isAdmin ? 'purple' : 'blue'">
              {{ isAdmin ? '管理员' : '应用所有者' }}
            </a-tag>
          </div>

          <a-form :model="form" layout="vertical" class="edit-form" @finish="save">
            <a-form-item
              label="应用名称"
              name="appName"
              :rules="[{ required: true, message: '请输入应用名称' }]"
              extra="建议使用简洁、易理解的名称，最多 100 个字符。"
            >
              <a-input
                v-model:value="form.appName"
                :maxlength="100"
                show-count
                placeholder="输入应用名称"
              />
            </a-form-item>

            <template v-if="isAdmin">
              <a-form-item label="应用封面 URL" extra="建议使用 16:9 比例的高清图片。">
                <a-input v-model:value="form.cover" placeholder="https://example.com/cover.png">
                  <template #prefix>
                    <LinkOutlined />
                  </template>
                </a-input>
              </a-form-item>

              <a-form-item label="应用优先级" extra="优先级越高，应用在精选列表中的排序越靠前。">
                <a-input-number
                  v-model:value="form.priority"
                  :min="0"
                  :max="999"
                  style="width: 100%"
                />
              </a-form-item>

              <a-form-item label="可见范围" extra="公开应用才允许进入精选列表；私有应用只对创建者和管理员可见。">
                <a-radio-group v-model:value="form.visibility">
                  <a-radio value="private">私有</a-radio>
                  <a-radio value="public">公开</a-radio>
                </a-radio-group>
              </a-form-item>

              <a-alert
                message="精选应用设置"
                description="将应用设为公开，并把优先级设置为 99，可把已部署应用加入精选列表。"
                type="info"
                show-icon
              />
            </template>

            <a-form-item label="初始提示词" extra="初始提示词不可修改。">
              <a-textarea :value="appDetail.initPrompt" :rows="3" disabled />
            </a-form-item>

            <div class="readonly-grid">
              <a-form-item label="生成类型" extra="生成类型不可修改。">
                <a-input :value="formatCodeGenType(appDetail.codeGenType)" disabled />
              </a-form-item>
              <a-form-item label="部署标识" extra="部署标识不可修改。">
                <a-input :value="appDetail.deployKey || '尚未部署'" disabled />
              </a-form-item>
            </div>

            <div class="form-actions">
              <a-button @click="router.back()">取消</a-button>
              <a-button type="link" @click="router.push(`/app/chat/${appId}`)">进入对话</a-button>
              <a-button type="primary" html-type="submit" :loading="saving">
                <SaveOutlined />
                保存修改
              </a-button>
            </div>
          </a-form>

          <section class="app-information">
            <div class="information-heading">
              <div>
                <h2>应用信息</h2>
                <p>当前应用的创建、部署与访问状态。</p>
              </div>
              <a-tag :color="appDetail.deployKey ? 'success' : 'default'">
                {{ appDetail.deployKey ? '已部署' : '尚未部署' }}
              </a-tag>
            </div>
            <a-descriptions bordered size="small" :column="2">
              <a-descriptions-item label="应用 ID">{{ appDetail.id || '-' }}</a-descriptions-item>
              <a-descriptions-item label="创建者">
                {{ appDetail.user?.userName || appDetail.userId || '-' }}
              </a-descriptions-item>
              <a-descriptions-item label="创建时间">
                {{ formatDateTime(appDetail.createTime) }}
              </a-descriptions-item>
              <a-descriptions-item label="更新时间">
                {{ formatDateTime(appDetail.updateTime) }}
              </a-descriptions-item>
              <a-descriptions-item label="部署时间">
                {{ formatDateTime(appDetail.deployedTime) }}
              </a-descriptions-item>
              <a-descriptions-item label="访问链接">
                <a
                  v-if="appDetail.deployKey"
                  :href="previewUrl"
                  target="_blank"
                  rel="noopener noreferrer"
                >
                  查看预览
                </a>
                <span v-else>尚未部署</span>
              </a-descriptions-item>
            </a-descriptions>
          </section>
        </a-spin>
      </section>
    </section>
  </main>
</template>

<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import {
  AppstoreOutlined,
  ArrowLeftOutlined,
  LinkOutlined,
  SafetyCertificateOutlined,
  SaveOutlined,
} from '@ant-design/icons-vue'
import { message } from 'ant-design-vue'
import { getAppVoById, getAppVoByIdByAdmin, updateApp, updateAppByAdmin } from '@/api/appController'
import { useLoginUserStore } from '@/stores/loginUser'
import ACCESS_ENUM from '@/accessEnum'
import { formatCodeGenType } from '@/utils/codeGenType'

const route = useRoute()
const router = useRouter()
const loginUserStore = useLoginUserStore()
const appId = String(route.params.id || '')
const loading = ref(false)
const saving = ref(false)
const appDetail = ref<API.AppVO>({})
const form = reactive<API.AppAdminUpdateRequest>({
  id: appId,
  appName: '',
  cover: '',
  visibility: 'private',
  priority: 0,
})
const isAdmin = computed(() => loginUserStore.loginUser.userRole === ACCESS_ENUM.ADMIN)
const previewUrl = computed(() =>
  appDetail.value.deployKey ? `http://localhost:8123/api/static/${appDetail.value.deployKey}/` : '',
)

const formatDateTime = (time?: string) => {
  return time ? new Date(time).toLocaleString('zh-CN') : '-'
}

const loadApp = async () => {
  loading.value = true
  try {
    const res = isAdmin.value
      ? await getAppVoByIdByAdmin({ id: appId })
      : await getAppVoById({ id: appId })

    if (res.data.code !== 0 || !res.data.data) {
      message.error(`获取应用失败：${res.data.message || '无权访问'}`)
      await router.replace('/')
      return
    }

    appDetail.value = res.data.data
    Object.assign(form, {
      id: appId,
      appName: res.data.data.appName,
      cover: res.data.data.cover,
      visibility: res.data.data.visibility || 'private',
      priority: res.data.data.priority,
    })
  } finally {
    loading.value = false
  }
}

const save = async () => {
  saving.value = true
  try {
    const res = isAdmin.value
      ? await updateAppByAdmin({
          id: appId,
          appName: form.appName,
          cover: form.cover,
          visibility: form.visibility,
          priority: form.priority,
        })
      : await updateApp({ id: appId, appName: form.appName })

    if (res.data.code === 0) {
      message.success('保存成功')
      router.back()
      return
    }
    message.error(`保存失败：${res.data.message || '请稍后重试'}`)
  } finally {
    saving.value = false
  }
}

onMounted(loadApp)
</script>

<style scoped>
.edit-page {
  width: min(1040px, calc(100% - 48px));
  margin: 0 auto;
  padding: 34px 0 80px;
}

.back-button {
  display: inline-flex;
  align-items: center;
  gap: 7px;
  margin-bottom: 18px;
  padding: 0;
  color: #667085;
  font-size: 12px;
  border: 0;
  background: transparent;
  cursor: pointer;
}

.back-button:hover {
  color: #5b5cf0;
}

.edit-layout {
  display: grid;
  grid-template-columns: 330px minmax(0, 1fr);
  overflow: hidden;
  border: 1px solid #eaecf0;
  border-radius: 20px;
  background: #fff;
  box-shadow: var(--shadow-float);
}

.context-panel {
  padding: 42px 34px;
  color: #fff;
  background:
    radial-gradient(circle at 15% 95%, rgb(71 220 191 / 25%), transparent 38%),
    linear-gradient(145deg, #25265e 0%, #4647b8 100%);
}

.context-label {
  color: #b8b9ff;
  font-size: 9px;
  font-weight: 700;
  letter-spacing: 1.8px;
}

.context-panel h1 {
  margin: 14px 0 8px;
  font-size: 28px;
  letter-spacing: -1px;
}

.context-panel > p {
  margin: 0;
  color: #d0d1f7;
  font-size: 12px;
  line-height: 1.8;
}

.preview-card {
  display: flex;
  align-items: center;
  gap: 12px;
  margin-top: 34px;
  padding: 12px;
  border: 1px solid rgb(255 255 255 / 12%);
  border-radius: 12px;
  background: rgb(255 255 255 / 8%);
}

.preview-cover {
  display: grid;
  flex-shrink: 0;
  width: 52px;
  height: 40px;
  overflow: hidden;
  color: #fff;
  border-radius: 7px;
  background: rgb(255 255 255 / 12%);
  place-items: center;
}

.preview-cover img {
  width: 100%;
  height: 100%;
  object-fit: cover;
}

.preview-card strong,
.preview-card span,
.permission-tip strong,
.permission-tip span {
  display: block;
}

.preview-card strong {
  max-width: 170px;
  overflow: hidden;
  font-size: 12px;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.preview-card span {
  margin-top: 2px;
  color: #c5c6ef;
  font-size: 10px;
}

.permission-tip {
  display: flex;
  align-items: flex-start;
  gap: 10px;
  margin-top: 24px;
  color: #b8b9ff;
}

.permission-tip strong {
  color: #fff;
  font-size: 11px;
}

.permission-tip span {
  margin-top: 3px;
  color: #c5c6ef;
  font-size: 9px;
  line-height: 1.6;
}

.form-panel {
  padding: 38px 42px;
}

.form-heading {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 20px;
  padding-bottom: 22px;
  border-bottom: 1px solid #f0f1f4;
}

.form-heading h2 {
  margin: 0;
  color: #182230;
  font-size: 18px;
}

.form-heading p {
  margin: 4px 0 0;
  color: #98a2b3;
  font-size: 11px;
}

.edit-form {
  padding-top: 24px;
}

.readonly-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 16px;
}

.edit-form :deep(.ant-form-item) {
  margin-bottom: 22px;
}

.edit-form :deep(.ant-form-item-label > label) {
  color: #344054;
  font-size: 12px;
  font-weight: 600;
}

.edit-form :deep(.ant-form-item-extra) {
  margin-top: 4px;
  color: #98a2b3;
  font-size: 10px;
}

.form-actions {
  display: flex;
  justify-content: flex-end;
  gap: 10px;
  margin-top: 30px;
  padding-top: 22px;
  border-top: 1px solid #f0f1f4;
}

.app-information {
  margin-top: 34px;
  padding-top: 28px;
  border-top: 1px solid #f0f1f4;
}

.information-heading {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 20px;
  margin-bottom: 18px;
}

.information-heading h2 {
  margin: 0;
  color: #182230;
  font-size: 18px;
}

.information-heading p {
  margin: 4px 0 0;
  color: #98a2b3;
  font-size: 11px;
}

.app-information :deep(.ant-descriptions-item-label) {
  color: #667085;
  font-size: 11px;
}

.app-information :deep(.ant-descriptions-item-content) {
  color: #344054;
  font-size: 11px;
}

@media (max-width: 800px) {
  .edit-page {
    width: calc(100% - 28px);
  }

  .edit-layout {
    grid-template-columns: 1fr;
  }

  .context-panel,
  .form-panel {
    padding: 28px 24px;
  }

  .readonly-grid {
    grid-template-columns: 1fr;
    gap: 0;
  }
}
</style>
