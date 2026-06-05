<template>
  <main class="home-page">
    <section id="create" class="hero-section">
      <div class="hero-grid"></div>
      <div class="hero-orb hero-orb-left"></div>
      <div class="hero-orb hero-orb-right"></div>

      <div class="hero-inner">
        <h1>一句话，<span>生成你的应用</span></h1>
        <p class="hero-description">描述你的想法，AI 自动完成设计、编码与部署。</p>

        <div class="prompt-shell">
          <div class="prompt-topbar">
            <span><SparklesIcon /> AI 创作助手</span>
            <span class="shortcut">Ctrl + Enter 发送</span>
          </div>
          <a-textarea
            v-model:value="prompt"
            class="prompt-input"
            :auto-size="{ minRows: 4, maxRows: 7 }"
            :maxlength="1000"
            placeholder="描述你想创建的应用，例如：帮我创建一个极简风格的个人作品集网站，包含项目展示、个人介绍和联系方式..."
            @keydown.meta.enter="createApp"
            @keydown.ctrl.enter="createApp"
          />
          <div class="prompt-footer">
            <div class="example-list">
              <span>试试这些：</span>
              <button
                v-for="item in promptExamples"
                :key="item.label"
                type="button"
                @click="prompt = item.prompt"
              >
                {{ item.label }}
              </button>
            </div>
            <a-button type="primary" class="generate-button" :loading="creating" @click="createApp">
              开始生成
              <ArrowRightOutlined />
            </a-button>
          </div>
        </div>

        <div class="capability-list">
          <div v-for="item in capabilities" :key="item.title" class="capability-item">
            <span class="capability-icon">
              <component :is="item.icon" />
            </span>
            <span>
              <strong>{{ item.title }}</strong>
              <small>{{ item.description }}</small>
            </span>
          </div>
        </div>
      </div>
    </section>

    <section class="workspace-section">
      <div class="workspace-inner">
        <div class="workspace-overview">
          <div>
            <span class="section-eyebrow">CREATION WORKSPACE</span>
            <h2>你的应用工作台</h2>
            <p>管理创作进度，随时继续完善已有应用。</p>
          </div>
          <div class="overview-stats">
            <div>
              <strong>{{ myTotal }}</strong>
              <span>我的应用</span>
            </div>
            <i></i>
            <div>
              <strong>{{ goodTotal }}</strong>
              <span>精选案例</span>
            </div>
          </div>
        </div>

        <AppListSection
          title="我的应用"
          subtitle="最近创建和编辑的应用"
          :apps="myApps"
          :loading="myLoading"
          :total="myTotal"
          :page-num="myQuery.pageNum || 1"
          :editable="true"
          @search="searchMyApps"
          @page-change="changeMyPage"
          @work="viewWork"
          @chat="viewChat"
          @edit="editApp"
          @delete="removeMyApp"
        />

        <AppListSection
          title="精选应用"
          subtitle="发现社区中值得参考的优秀作品"
          :apps="goodApps"
          :loading="goodLoading"
          :total="goodTotal"
          :page-num="goodQuery.pageNum || 1"
          variant="featured"
          @search="searchGoodApps"
          @page-change="changeGoodPage"
          @work="viewWork"
          @chat="viewChat"
        />
      </div>
    </section>
  </main>
</template>

<script setup lang="ts">
import { h, onMounted, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import {
  ArrowRightOutlined,
  CloudUploadOutlined,
  CodeOutlined,
  LayoutOutlined,
} from '@ant-design/icons-vue'
import { message, Modal } from 'ant-design-vue'
import AppListSection from '@/components/AppListSection.vue'
import {
  addApp,
  deleteApp,
  getAppVoById,
  listGoodAppVoByPage,
  listMyAppVoByPage,
} from '@/api/appController'
import { useLoginUserStore } from '@/stores/loginUser'

const SparklesIcon = () =>
  h(
    'svg',
    {
      viewBox: '0 0 24 24',
      fill: 'none',
      xmlns: 'http://www.w3.org/2000/svg',
    },
    [
      h('path', {
        d: 'M12 3L13.8 8.2L19 10L13.8 11.8L12 17L10.2 11.8L5 10L10.2 8.2L12 3Z',
        fill: 'currentColor',
      }),
      h('path', {
        d: 'M19 15L19.8 17.2L22 18L19.8 18.8L19 21L18.2 18.8L16 18L18.2 17.2L19 15Z',
        fill: 'currentColor',
      }),
    ],
  )

const router = useRouter()
const loginUserStore = useLoginUserStore()
const prompt = ref('')
const creating = ref(false)
const myApps = ref<API.AppVO[]>([])
const goodApps = ref<API.AppVO[]>([])
const myTotal = ref(0)
const goodTotal = ref(0)
const myLoading = ref(false)
const goodLoading = ref(false)

const promptExamples = [
  {
    label: '个人作品集',
    prompt:
      '创建一个个人作品集网站，风格要简约、现代、有设计感。设计完整的首页，包括个人介绍、技能展示、项目作品、工作经历和联系方式等部分。',
  },
  {
    label: '数据分析看板',
    prompt:
      '创建一个数据分析看板，风格要专业、清晰、具有科技感。设计完整的仪表盘页面，包括核心指标卡片、趋势图表、数据分布、排行榜和筛选条件等部分。',
  },
  {
    label: '企业官网',
    prompt:
      '创建一个企业网站，风格要大气、商务、专业。设计一个完整的企业网站首页，包括导航栏、Hero 区域、服务介绍、公司优势、客户评价和联系我们等部分。',
  },
  {
    label: '活动落地页',
    prompt:
      '创建一个活动落地页，风格要有冲击力、年轻、富有氛围感。设计完整的活动页面，包括活动主题、亮点介绍、嘉宾阵容、活动流程、报名信息和常见问题等部分。',
  },
]
const capabilities = [
  {
    icon: LayoutOutlined,
    title: '智能页面设计',
    description: '自动生成专业视觉',
  },
  {
    icon: CodeOutlined,
    title: '完整代码生成',
    description: '直接产出可运行项目',
  },
  {
    icon: CloudUploadOutlined,
    title: '一键在线部署',
    description: '快速发布与分享',
  },
]

const myQuery = reactive<API.AppQueryRequest>({
  pageNum: 1,
  pageSize: 6,
  sortField: 'createTime',
  sortOrder: 'descend',
})
const goodQuery = reactive<API.AppQueryRequest>({
  pageNum: 1,
  pageSize: 6,
  sortField: 'createTime',
  sortOrder: 'descend',
})

const createApp = async () => {
  if (creating.value) {
    return
  }

  const value = prompt.value.trim()
  if (!value) {
    message.warning('请先描述你想创建的应用')
    return
  }
  if (!loginUserStore.loginUser.id) {
    message.info('登录后即可开始创建应用')
    await router.push('/user/login?redirect=/')
    return
  }

  creating.value = true
  try {
    const res = await addApp({ initPrompt: value })
    if (res.data.code === 0 && res.data.data) {
      await router.push(`/app/chat/${res.data.data}?autoSend=1`)
      return
    }
    message.error(`创建失败：${res.data.message || '请稍后重试'}`)
  } finally {
    creating.value = false
  }
}

const fetchMyApps = async () => {
  myLoading.value = true
  try {
    const res = await listMyAppVoByPage({ ...myQuery })
    myApps.value = res.data.data?.records ?? []
    myTotal.value = res.data.data?.totalRow ?? 0
  } finally {
    myLoading.value = false
  }
}

const fetchGoodApps = async () => {
  goodLoading.value = true
  try {
    const res = await listGoodAppVoByPage({ ...goodQuery })
    goodApps.value = res.data.data?.records ?? []
    goodTotal.value = res.data.data?.totalRow ?? 0
  } finally {
    goodLoading.value = false
  }
}

const searchMyApps = (appName: string) => {
  myQuery.appName = appName
  myQuery.pageNum = 1
  fetchMyApps()
}

const searchGoodApps = (appName: string) => {
  goodQuery.appName = appName
  goodQuery.pageNum = 1
  fetchGoodApps()
}

const changeMyPage = (page: number) => {
  myQuery.pageNum = page
  fetchMyApps()
}

const changeGoodPage = (page: number) => {
  goodQuery.pageNum = page
  fetchGoodApps()
}

const viewChat = (app: API.AppVO) => {
  if (app.id) {
    router.push(`/app/chat/${app.id}`)
  }
}

const viewWork = async (app: API.AppVO) => {
  if (!app.id) {
    return
  }

  const previewWindow = window.open('', '_blank')
  try {
    // 卡片可能来自部署前加载的旧列表，查看作品时以数据库最新状态为准。
    const res = await getAppVoById({ id: app.id })
    const latestApp = res.data.data
    if (res.data.code !== 0 || !latestApp) {
      previewWindow?.close()
      message.error(res.data.message || '获取应用信息失败')
      return
    }

    app.deployKey = latestApp.deployKey
    app.deployedTime = latestApp.deployedTime
    app.versionNo = latestApp.versionNo

    if (!latestApp.deployKey) {
      previewWindow?.close()
      message.info(
        (latestApp.versionNo || 0) > 0
          ? '项目尚未部署，请前往对话页面预览应用'
          : '项目尚未生成，请前往对话页面生成应用',
      )
      await router.push(`/app/chat/${app.id}`)
      return
    }

    const workUrl = `http://localhost:8123/api/static/${latestApp.deployKey}/`
    if (previewWindow) {
      previewWindow.location.href = workUrl
    } else {
      window.open(workUrl, '_blank', 'noopener,noreferrer')
    }
  } catch {
    previewWindow?.close()
  }
}

const editApp = (app: API.AppVO) => {
  if (app.id) {
    router.push(`/app/edit/${app.id}`)
  }
}

const removeMyApp = async (app: API.AppVO) => {
  if (!app.id) {
    return
  }

  Modal.confirm({
    title: `确认删除“${app.appName || '未命名应用'}”？`,
    content: '删除后应用信息、对话入口和已部署作品将无法继续从工作台访问，此操作不可撤销。',
    okText: '确认删除',
    okType: 'danger',
    cancelText: '取消',
    async onOk() {
      try {
        const res = await deleteApp({ id: app.id })
        if (res.data.code !== 0) {
          throw new Error(res.data.message || '删除失败，请稍后重试')
        }
        message.success('应用已删除')
        if (myApps.value.length === 1 && (myQuery.pageNum || 1) > 1) {
          myQuery.pageNum = (myQuery.pageNum || 1) - 1
        }
        await fetchMyApps()
      } catch (error) {
        const errorText = error instanceof Error ? error.message : '删除失败，请稍后重试'
        message.error(errorText)
        throw error
      }
    },
  })
}

onMounted(() => {
  if (loginUserStore.loginUser.id) {
    fetchMyApps()
  }
  fetchGoodApps()
})
</script>

<style scoped>
.hero-section {
  position: relative;
  overflow: hidden;
  padding: 82px 24px 68px;
  background:
    radial-gradient(circle at 5% 90%, rgb(197 230 255 / 45%), transparent 32%),
    radial-gradient(circle at 95% 75%, rgb(195 255 237 / 45%), transparent 30%),
    linear-gradient(180deg, #fbfdff 0%, #f7fbff 100%);
}

.hero-grid {
  position: absolute;
  inset: 0;
  background-image: radial-gradient(rgb(91 92 240 / 16%) 0.7px, transparent 0.7px);
  background-size: 22px 22px;
  mask-image: linear-gradient(to bottom, black, transparent 90%);
}

.hero-orb {
  position: absolute;
  width: 340px;
  height: 340px;
  border-radius: 50%;
  filter: blur(90px);
  opacity: 0.25;
}

.hero-orb-left {
  bottom: -190px;
  left: 4%;
  background: #49a8ff;
}

.hero-orb-right {
  right: 4%;
  bottom: -180px;
  background: #44e2b6;
}

.hero-inner {
  position: relative;
  z-index: 1;
  width: min(940px, 100%);
  margin: 0 auto;
  text-align: center;
}

h1 {
  margin: 0;
  color: #101828;
  font-size: clamp(38px, 4.5vw, 58px);
  font-weight: 750;
  line-height: 1.15;
  letter-spacing: -3px;
}

h1 span {
  color: transparent;
  background: linear-gradient(110deg, #5b5cf0 18%, #8170f4 52%, #19a98c 100%);
  background-clip: text;
}

.hero-description {
  max-width: 650px;
  margin: 16px auto 30px;
  color: #667085;
  font-size: 16px;
  line-height: 1.8;
}

.prompt-shell {
  overflow: hidden;
  padding: 16px;
  text-align: left;
  border: 1px solid rgb(216 220 234 / 85%);
  border-radius: 20px;
  background: rgb(255 255 255 / 92%);
  box-shadow:
    0 28px 70px rgb(16 24 40 / 12%),
    0 0 0 6px rgb(255 255 255 / 45%);
  backdrop-filter: blur(18px);
}

.prompt-topbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 2px 4px 11px;
  color: #667085;
  font-size: 12px;
}

.prompt-topbar span:first-child {
  display: flex;
  align-items: center;
  gap: 6px;
  color: #5b5cf0;
  font-weight: 600;
}

.prompt-topbar svg {
  width: 16px;
  height: 16px;
}

.shortcut {
  color: #98a2b3;
}

.prompt-input {
  padding: 17px 18px;
  border: 1px solid #eef0f5;
  border-radius: 12px;
  background: #fafbfc;
  box-shadow: none;
  font-size: 15px;
  line-height: 1.75;
  resize: none;
}

.prompt-input:hover,
.prompt-input:focus {
  border-color: #d9dafe;
  background: #fff;
  box-shadow: 0 0 0 3px rgb(91 92 240 / 7%);
}

.prompt-footer {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  margin-top: 14px;
}

.example-list {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 7px;
}

.example-list > span {
  color: #98a2b3;
  font-size: 12px;
}

.example-list button {
  height: 28px;
  padding: 0 10px;
  color: #667085;
  font-size: 12px;
  border: 1px solid #eaecf0;
  border-radius: 7px;
  background: #fff;
  cursor: pointer;
  transition: all 0.2s ease;
}

.example-list button:hover {
  color: #4f50d8;
  border-color: #d6d7ff;
  background: #f6f6ff;
}

.generate-button {
  flex-shrink: 0;
  height: 40px;
  padding-inline: 18px;
  border-radius: 10px;
  font-weight: 600;
  box-shadow: 0 10px 20px rgb(91 92 240 / 20%);
}

.capability-list {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 12px;
  margin-top: 30px;
}

.capability-item {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 12px 15px;
  text-align: left;
  border: 1px solid rgb(234 236 240 / 75%);
  border-radius: 12px;
  background: rgb(255 255 255 / 55%);
}

.capability-icon {
  display: grid;
  flex-shrink: 0;
  width: 32px;
  height: 32px;
  color: #5b5cf0;
  border-radius: 9px;
  background: #eeeeff;
  place-items: center;
}

.capability-item strong,
.capability-item small {
  display: block;
}

.capability-item strong {
  color: #344054;
  font-size: 12px;
}

.capability-item small {
  margin-top: 2px;
  color: #98a2b3;
  font-size: 10px;
}

.workspace-section {
  padding: 72px 24px 100px;
  background: #f7f8fc;
}

.workspace-inner {
  width: min(var(--page-width), 100%);
  margin: 0 auto;
}

.workspace-overview {
  display: flex;
  align-items: flex-end;
  justify-content: space-between;
  gap: 24px;
  margin-bottom: 36px;
}

.section-eyebrow {
  color: #5b5cf0;
  font-size: 11px;
  font-weight: 700;
  letter-spacing: 1.8px;
}

.workspace-overview h2 {
  margin: 8px 0 4px;
  color: #101828;
  font-size: 32px;
  letter-spacing: -1.2px;
}

.workspace-overview p {
  margin: 0;
  color: #98a2b3;
  font-size: 14px;
}

.overview-stats {
  display: flex;
  align-items: center;
  gap: 22px;
  padding: 13px 18px;
  border: 1px solid #eaecf0;
  border-radius: 14px;
  background: #fff;
  box-shadow: var(--shadow-card);
}

.overview-stats div {
  min-width: 64px;
}

.overview-stats strong,
.overview-stats span {
  display: block;
}

.overview-stats strong {
  color: #101828;
  font-size: 20px;
}

.overview-stats span {
  margin-top: 1px;
  color: #98a2b3;
  font-size: 10px;
}

.overview-stats i {
  width: 1px;
  height: 28px;
  background: #eaecf0;
}

@media (max-width: 760px) {
  .hero-section {
    padding: 58px 16px 48px;
  }

  h1 {
    font-size: 38px;
    letter-spacing: -2px;
  }

  .prompt-footer,
  .workspace-overview {
    align-items: stretch;
    flex-direction: column;
  }

  .generate-button {
    width: 100%;
  }

  .capability-list {
    grid-template-columns: 1fr;
  }

  .workspace-section {
    padding: 52px 16px 72px;
  }

  .overview-stats {
    align-self: flex-start;
  }
}
</style>
