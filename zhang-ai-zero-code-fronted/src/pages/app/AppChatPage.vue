<template>
  <div class="studio-page">
    <header class="studio-header">
      <div class="header-left">
        <a-button type="text" shape="circle" @click="router.push('/')">
          <ArrowLeftOutlined />
        </a-button>
        <span class="header-divider"></span>
        <img src="@/assets/logo.jpg" alt="" />
        <div class="app-title">
          <strong>{{ appInfo.appName || '未命名应用' }}</strong>
          <span>
            <i :class="{ active: generating }"></i>
            {{ generating ? 'AI 正在生成应用' : '应用已保存' }}
          </span>
        </div>
      </div>

      <div class="header-actions">
        <a-button
          :loading="downloadingCode"
          :disabled="
            generating ||
            previewBuilding ||
            deploying ||
            undeploying ||
            !canDownloadCode
          "
          @click="downloadAppCode"
        >
          <DownloadOutlined />
          {{ downloadButtonText }}
        </a-button>
        <a-button
          v-if="appInfo.deployKey"
          danger
          :loading="undeploying"
          :disabled="generating || previewBuilding || deploying || downloadingCode"
          @click="undeploy"
        >
          下线应用
        </a-button>
        <a-button
          type="primary"
          :loading="deploying"
          :disabled="
            generating ||
            previewBuilding ||
            undeploying ||
            downloadingCode ||
            (appInfo.versionNo || 0) <= 0
          "
          @click="deploy()"
        >
          <CloudUploadOutlined />
          部署应用
        </a-button>
      </div>
    </header>

    <main class="studio-workspace">
      <section class="chat-panel">
        <div class="panel-heading chat-heading">
          <div class="chat-title">
            <h2>创作对话</h2>
            <a-tag class="chat-count-tag" color="blue">{{ chatRoundCount }} 轮</a-tag>
          </div>
          <div class="panel-actions">
            <a-tag class="status-tag" :color="generating ? 'processing' : 'default'">
              {{ generating ? '生成中' : generationError ? '生成失败' : '等待指令' }}
            </a-tag>
            <a-tooltip title="导出 Markdown">
              <a-button
                class="panel-icon-button"
                size="small"
                type="text"
                :disabled="generating || historyLoading || chatRoundCount <= 0"
                :loading="exportingHistory"
                @click="exportChatHistory"
              >
                <DownloadOutlined />
              </a-button>
            </a-tooltip>
            <a-tooltip title="清空对话">
              <a-button
                class="panel-icon-button danger"
                size="small"
                type="text"
                :disabled="generating || historyLoading || !messages.length"
                :loading="clearingHistory"
                @click="clearChatHistory"
              >
                <DeleteOutlined />
              </a-button>
            </a-tooltip>
          </div>
        </div>

        <div ref="messageListRef" class="message-list" @scroll.passive="handleMessageListScroll">
          <a-button
            v-if="hasMoreHistory"
            type="link"
            block
            :loading="historyLoading"
            @click="loadMoreHistory"
          >
            加载更早消息
          </a-button>

          <div v-if="!messages.length" class="welcome-state">
            <span class="welcome-icon">
              <RobotOutlined />
            </span>
            <h3>描述你想创建的应用</h3>
            <p>你可以说明页面结构、视觉风格和交互需求，AI 将自动完成实现。</p>
            <div class="suggestion-list">
              <button
                v-for="suggestion in suggestions"
                :key="suggestion"
                type="button"
                @click="inputMessage = suggestion"
              >
                {{ suggestion }}
                <ArrowRightOutlined />
              </button>
            </div>
          </div>

          <div
            v-for="item in messages"
            :key="item.key"
            class="message-row"
            :class="{ user: item.type === 'user' }"
          >
            <a-avatar v-if="item.type === 'ai'" :size="30" :src="logo" />
            <div class="message-bubble">
              <div class="message-owner">{{ item.type === 'user' ? '你' : 'AI 助手' }}</div>
              <div
                class="message-content"
                :class="{ typing: generating && item.key === activeAiMessageKey && !item.content }"
              >
                <details v-if="item.thinking" class="thinking-block" open>
                  <summary>思考过程</summary>
                  <p>{{ item.thinking }}</p>
                </details>
                <ChatMessageContent v-if="item.content" :content="item.content" />
                <div v-if="item.type === 'user' && item.visualElement" class="sent-selected-element">
                  <div class="sent-selected-title">已选择页面元素</div>
                  <div>
                    <strong>元素：</strong>
                    {{ formatVisualElementLabel(item.visualElement) }}
                  </div>
                  <div v-if="getVisualElementText(item.visualElement)">
                    <strong>内容：</strong>
                    {{ getVisualElementText(item.visualElement) }}
                  </div>
                  <div v-if="item.visualElement.pagePath">
                    <strong>页面：</strong>
                    {{ item.visualElement.pagePath }}
                  </div>
                  <div>
                    <strong>选择器：</strong>
                    {{ item.visualElement.selector }}
                  </div>
                </div>
                <span v-if="generating && item.key === activeAiMessageKey && !item.content" class="typing-dots">
                  <i></i>
                  <i></i>
                  <i></i>
                </span>
              </div>
              <div class="message-actions">
                <a-tooltip title="复制">
                  <a-button
                    type="text"
                    size="small"
                    shape="circle"
                    :disabled="!getCopyMessageContent(item)"
                    aria-label="复制"
                    @click="copyMessage(item)"
                  >
                    <CopyOutlined />
                  </a-button>
                </a-tooltip>
                <a-tooltip v-if="item.type === 'user'" title="编辑">
                  <a-button
                    type="text"
                    size="small"
                    shape="circle"
                    :disabled="!getEditableUserMessageContent(item)"
                    aria-label="编辑"
                    @click="editUserMessage(item)"
                  >
                    <EditOutlined />
                  </a-button>
                </a-tooltip>
              </div>
              <span class="message-time">{{ formatTime(item.createTime) }}</span>
            </div>
            <a-avatar v-if="item.type === 'user'" :size="30" :src="userAvatar" />
          </div>
        </div>

        <div class="composer-wrap">
          <a-alert
            v-if="selectedVisualElement"
            class="selected-element-alert"
            type="info"
            closable
            @close="clearSelectedVisualElement"
          >
            <template #message>
              <div class="selected-element-info">
                <div>
                  <strong>选中元素：</strong>
                  {{ selectedVisualElementLabel }}
                </div>
                <div v-if="selectedVisualElementText">
                  <strong>内容：</strong>
                  {{ selectedVisualElementText }}
                </div>
                <div v-if="selectedVisualElement.pagePath">
                  <strong>页面路径：</strong>
                  {{ selectedVisualElement.pagePath }}
                </div>
                <div>
                  <strong>选择器：</strong>
                  {{ selectedVisualElement.selector }}
                </div>
              </div>
            </template>
          </a-alert>
          <div v-if="attachments.length" class="attachment-list">
            <span
              v-for="attachment in attachments"
              :key="attachment.id"
              class="attachment-chip"
            >
              <PaperClipOutlined />
              <span>{{ attachment.name }}</span>
              <button type="button" @click="removeAttachment(attachment.id)">
                <CloseOutlined />
              </button>
            </span>
          </div>
          <div class="composer">
            <a-textarea
              v-model:value="inputMessage"
              :auto-size="{ minRows: 3, maxRows: 7 }"
              :placeholder="composerPlaceholder"
              @keydown.meta.enter.prevent="sendMessage"
              @keydown.ctrl.enter.prevent="sendMessage"
            />
            <div class="composer-footer">
              <div class="composer-hint">
                <input
                  ref="attachmentInputRef"
                  class="attachment-input"
                  type="file"
                  multiple
                  accept="image/*,.txt,.md,.json,.csv,.yml,.yaml"
                  @change="handleAttachmentChange"
                />
                <a-button
                  class="attachment-button"
                  size="small"
                  type="text"
                  :loading="uploadingAttachment"
                  :disabled="generating"
                  @click="triggerAttachmentInput"
                >
                  <PaperClipOutlined /> 附件
                </a-button>
                <span><CodeOutlined /> 支持详细页面需求</span>
                <span>Ctrl + Enter 发送</span>
              </div>
              <a-button
                type="primary"
                :shape="generating ? undefined : 'circle'"
                :class="{ 'pause-generate-button': generating }"
                @click="generating ? stopGeneration() : sendMessage()"
              >
                <template #icon>
                  <span v-if="generating" class="stop-square-icon"></span>
                  <ArrowUpOutlined v-else />
                </template>
              </a-button>
            </div>
          </div>
          <p>AI 生成内容可能存在偏差，请在部署前检查应用效果。</p>
        </div>
      </section>

      <section class="preview-panel">
        <div class="preview-heading">
          <div>
            <span class="panel-label">{{
              rightPanelLabel
            }}</span>
            <h2>
              {{ rightPanelTitle }}
              <a-tag
                v-if="rightPanelMode === 'preview' && historicalPreviewVersionNo"
                color="purple"
              >
                历史版本 v{{ historicalPreviewVersionNo }}
              </a-tag>
            </h2>
          </div>
          <div class="preview-actions">
            <div class="right-mode-switch">
              <button
                type="button"
                :class="{ active: rightPanelMode === 'preview' }"
                @click="rightPanelMode = 'preview'"
              >
                <DesktopOutlined /> 预览
              </button>
              <button
                type="button"
                :class="{ active: rightPanelMode === 'code' }"
                :disabled="!generating && (appInfo.versionNo || 0) <= 0"
                @click="rightPanelMode = 'code'"
              >
                <CodeOutlined /> 代码
              </button>
            </div>
            <a-tooltip v-if="rightPanelMode === 'preview'" title="可视化选择页面元素">
              <a-button
                :type="visualEditEnabled ? 'primary' : 'text'"
                shape="circle"
                :disabled="generating || !previewReady || !previewUrl || !previewFrameLoaded"
                @click="toggleVisualEditMode"
              >
                <EditOutlined />
              </a-button>
            </a-tooltip>
            <a-tooltip v-if="rightPanelMode === 'preview'" title="刷新">
              <a-button
                type="text"
                shape="circle"
                :disabled="!previewUrl || generating || previewBuilding || deploying"
                @click="refreshPreview"
              >
                <ReloadOutlined />
              </a-button>
            </a-tooltip>
            <a-tooltip
              v-if="rightPanelMode === 'preview' && historicalPreviewVersionNo"
              title="返回当前最终版本"
            >
              <a-button type="text" shape="circle" @click="showCurrentPreview">
                <RollbackOutlined />
              </a-button>
            </a-tooltip>
            <a-tooltip v-if="rightPanelMode === 'preview'" title="新窗口打开">
              <a-button
                v-if="previewReady && previewUrl"
                type="text"
                shape="circle"
                :href="previewUrl"
                target="_blank"
              >
                <ExportOutlined />
              </a-button>
            </a-tooltip>
          </div>
        </div>

        <VersionCodeWorkspace
          v-if="rightPanelMode === 'code'"
          :app-id="appId"
          :current-version-no="appInfo.versionNo || 0"
          :generation-active="generating"
          :generated-files="generatedFiles"
          :generated-file-paths="generatedFilePaths"
          @version-change="handleVersionChange"
          @preview-version="handleVersionPreview"
        />

        <div v-else class="browser-shell">
          <div class="browser-toolbar">
            <div class="browser-dots">
              <i></i>
              <i></i>
              <i></i>
            </div>
            <div class="address-bar">
              <LockOutlined />
              <span>{{ previewAddressText }}</span>
            </div>
            <MoreOutlined />
          </div>

          <div
            v-if="previewReady && previewUrl"
            ref="previewViewportRef"
            class="preview-viewport"
            :class="{ 'visual-editing': visualEditEnabled }"
          >
            <div class="preview-frame" :style="previewFrameStyle">
              <iframe
                :key="previewKey"
                ref="previewIframeRef"
                :src="previewUrl"
                :style="previewIframeStyle"
                title="应用预览"
                @load="handlePreviewFrameLoad"
              ></iframe>
            </div>
            <div
              v-if="visualEditEnabled"
              class="visual-edit-layer"
              @mousemove="handleVisualEditPointerMove"
              @mouseleave="handleVisualEditPointerLeave"
              @click.prevent="handleVisualEditPointerClick"
              @wheel.prevent="handleVisualEditWheel"
            >
              <span
                v-if="visualEditHoverRect"
                class="visual-edit-box hover"
                :style="visualEditRectStyle(visualEditHoverRect)"
              ></span>
              <span
                v-if="visualEditSelectedRect"
                class="visual-edit-box selected"
                :style="visualEditRectStyle(visualEditSelectedRect)"
              ></span>
            </div>
          </div>
          <div v-else class="preview-empty">
            <div class="preview-visual" :class="{ generating: generating || previewBuilding }">
              <a-spin v-if="generating || previewBuilding" size="large" />
              <ExclamationCircleOutlined v-else-if="generationError" />
              <LayoutOutlined v-else />
            </div>
            <h3>
              {{
                previewBuilding
                  ? '正在准备应用预览'
                  : generating
                    ? '正在构建你的应用'
                    : generationError
                      ? '应用生成失败'
                      : '等待开始创作'
              }}
            </h3>
            <p>
              {{
                previewBuilding
                  ? '项目已经生成，正在构建临时预览地址，通常需要几秒钟。'
                  : generating
                  ? 'AI 正在编写页面与样式，完成后会自动刷新预览。'
                  : generationError
                    ? generationError
                    : '在左侧发送需求，生成结果将在这里实时展示。'
              }}
            </p>
            <a-button v-if="generationError" class="retry-button" @click="restoreFailedMessage">
              重新编辑需求
            </a-button>
            <div v-if="generating || previewBuilding" class="progress-steps">
              <span class="done">理解需求</span>
              <i></i>
              <span :class="{ done: previewBuilding, active: generating }">生成代码</span>
              <i></i>
              <span :class="{ active: previewBuilding }">渲染预览</span>
            </div>
          </div>
        </div>
      </section>
    </main>

    <a-modal v-model:open="deployModalOpen" title="部署成功" :footer="null">
      <div class="deploy-success">
        <span><CheckCircleFilled /></span>
        <h3>应用已成功部署</h3>
        <p>现在可以通过以下地址访问并分享你的应用。</p>
        <div class="deploy-link-box">
          <a-input :value="deployUrl" readonly />
          <div class="deploy-link-actions">
            <a-button @click="copyDeployUrl">复制链接</a-button>
            <a-button class="visit-app-button" type="primary" :href="deployUrl" target="_blank">
              访问应用
            </a-button>
          </div>
        </div>
      </div>
    </a-modal>
  </div>
</template>

<script setup lang="ts">
import { computed, nextTick, onBeforeUnmount, onMounted, reactive, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import {
  ArrowLeftOutlined,
  ArrowRightOutlined,
  ArrowUpOutlined,
  CheckCircleFilled,
  CloseOutlined,
  CloudUploadOutlined,
  CodeOutlined,
  CopyOutlined,
  DeleteOutlined,
  DesktopOutlined,
  DownloadOutlined,
  EditOutlined,
  ExclamationCircleOutlined,
  ExportOutlined,
  LayoutOutlined,
  LockOutlined,
  MoreOutlined,
  PaperClipOutlined,
  ReloadOutlined,
  RobotOutlined,
  RollbackOutlined,
} from '@ant-design/icons-vue'
import { message, Modal } from 'ant-design-vue'
import {
  deployApp,
  getAppVoById,
  previewApp,
  stopGeneration as stopAppGeneration,
  undeployApp,
} from '@/api/appController'
import { deleteAppChatHistory, listAppChatHistory } from '@/api/chatHistoryController'
import ChatMessageContent from '@/components/ChatMessageContent.vue'
import VersionCodeWorkspace from '@/components/VersionCodeWorkspace.vue'
import { useLoginUserStore } from '@/stores/loginUser'
import request from '@/request'
import {
  enableVisualEdit,
  getVisualEditTargetFromPoint,
  listenVisualEditSelection,
  type VisualEditElementInfo,
  type VisualEditOverlayRect,
} from '@/utils/visualEdit'
import logo from '@/assets/logo.jpg'
import defaultAvatar from '@/assets/default-avatar.png'

type ChatMessage = {
  key: string
  type: 'user' | 'ai'
  content: string
  rawContent?: string
  visualElement?: VisualEditElementInfo
  thinking?: string
  createTime?: string
}

type StreamChunk = {
  content: string
  thinking: string
  generatedFile?: GeneratedFileSnapshot
}

type UploadedAttachment = {
  id: string
  name: string
  url?: string
  contentType?: string
  size?: number
  textContent?: string
}

type GeneratedFilePanel = {
  path: string
  language: string
  content: string
  action: 'write' | 'modify' | 'stream'
  actionLabel: string
}

type GeneratedFileSnapshot = {
  type: 'generated_file_snapshot'
  toolName?: string
  action?: 'write' | 'modify' | 'delete'
  actionLabel?: string
  path?: string
  language?: string
  content?: string
  files?: string[]
  deleted?: boolean
}

const route = useRoute()
const router = useRouter()
const loginUserStore = useLoginUserStore()
const appId = String(route.params.id || '')
const appInfo = ref<API.AppVO>({})
const messages = ref<ChatMessage[]>([])
const inputMessage = ref('')
const generating = ref(false)
const attachments = ref<UploadedAttachment[]>([])
const uploadingAttachment = ref(false)
const attachmentInputRef = ref<HTMLInputElement>()
const generatedFiles = ref<GeneratedFilePanel[]>([])
const generatedFilePaths = ref<string[]>([])
const previewBuilding = ref(false)
const deploying = ref(false)
const undeploying = ref(false)
const downloadingCode = ref(false)
const deployModalOpen = ref(false)
const deployUrl = ref('')
const generatedPreviewUrl = ref('')
const historicalPreviewUrl = ref('')
const historicalPreviewVersionNo = ref(0)
const previewReady = ref(false)
const previewKey = ref(0)
const generationError = ref('')
const activeAiMessageKey = ref('')
const rightPanelMode = ref<'preview' | 'code'>('preview')
const messageListRef = ref<HTMLElement>()
const previewViewportRef = ref<HTMLElement>()
const previewIframeRef = ref<HTMLIFrameElement>()
const historyLoading = ref(false)
const clearingHistory = ref(false)
const exportingHistory = ref(false)
const visualEditEnabled = ref(false)
const selectedVisualElement = ref<VisualEditElementInfo>()
const visualEditHoverRect = ref<VisualEditOverlayRect>()
const visualEditSelectedRect = ref<VisualEditOverlayRect>()
const previewFrameLoaded = ref(false)
const autoScrollToBottom = ref(true)
const hasMoreHistory = ref(false)
const lastCreateTime = ref<string>()
let eventSource: EventSource | undefined
let lastSentMessage = ''
let streamSucceeded = false
let generationStoppedByUser = false
let pageLeftDuringGeneration = false
let typewriterTimer: ReturnType<typeof setInterval> | undefined
let typewriterQueue: string[] = []
let typewriterCursor = 0
let typewriterScrollCounter = 0
const typewriterDoneResolvers: Array<() => void> = []
const PREVIEW_DESKTOP_WIDTH = 1440
const PREVIEW_MIN_HEIGHT = 900
const MESSAGE_BOTTOM_THRESHOLD = 80
let previewResizeObserver: ResizeObserver | undefined
let cleanupVisualEdit: (() => void) | undefined
let cleanupVisualEditSelection: (() => void) | undefined
let generationCodeCaptureBuffer = ''
const GENERATED_CODE_CAPTURE_LIMIT = 300_000

const suggestions = ['增加一个现代化首页', '优化页面配色和间距', '添加联系我们表单']
const codeGenerationFormatInstruction = [
  '',
  '---',
  '代码输出格式要求：',
  '1. 严格按照“一个文件一个代码块”输出：每个文件先输出工具调用说明，例如：[工具调用] 写入文件 src/App.vue，然后只跟这个文件自己的一个 Markdown 代码块。',
  '2. 每个文件的完整代码必须放进独立 Markdown 代码围栏中，格式必须是 ```vue、```html、```css、```ts、```js 等语言标识开头，并用 ``` 结束。',
  '3. 禁止把多个文件写进同一个代码块；禁止一个代码块中同时包含 src/App.vue、src/main.ts 等多个文件内容。',
  '4. Vue 文件必须使用 Vue3 组合式 API 语法。',
  '5. 不要输出裸露的 vue/html/css/js 代码；没有代码围栏的代码无法被系统正确解析。',
  '6. appId 必须始终按字符串处理，不能转成 Number。',
].join('\n')
const userAvatar = computed(() => loginUserStore.loginUser.userAvatar || defaultAvatar)
const chatRoundCount = computed(() => {
  const loadedCount = messages.value.filter((item) => item.type === 'user').length
  return Math.max(appInfo.value.chatCount ?? 0, loadedCount)
})
const canDownloadCode = computed(
  () => Boolean(appInfo.value.deployKey) && (appInfo.value.versionNo || 0) > 0,
)
const downloadButtonText = computed(() => {
  if (!appInfo.value.deployKey) {
    return '部署后下载'
  }
  const downloadCount = appInfo.value.downloadCount || 0
  return downloadCount > 0 ? `下载代码 ${downloadCount} 次` : '下载代码'
})
const rightPanelLabel = computed(() => {
  if (rightPanelMode.value === 'code') {
    return 'SOURCE & VERSIONS'
  }
  return 'LIVE PREVIEW'
})
const rightPanelTitle = computed(() => {
  if (rightPanelMode.value === 'code') {
    return '代码与版本'
  }
  return '应用预览'
})
const normalizePreviewUrl = (url: string) => {
  if (!url) {
    return ''
  }

  try {
    const parsedUrl = new URL(url, window.location.origin)
    if (
      parsedUrl.hostname === window.location.hostname &&
      parsedUrl.port === '8123' &&
      parsedUrl.pathname.startsWith('/api/')
    ) {
      return `${parsedUrl.pathname}${parsedUrl.search}${parsedUrl.hash}`
    }
  } catch (error) {
    console.warn('预览地址解析失败：', error)
  }

  return url
}

const previewUrl = computed(() => {
  if (historicalPreviewUrl.value) {
    return normalizePreviewUrl(historicalPreviewUrl.value)
  }
  if (deployUrl.value) {
    return normalizePreviewUrl(deployUrl.value)
  }
  if (generatedPreviewUrl.value) {
    return normalizePreviewUrl(generatedPreviewUrl.value)
  }
  if (appInfo.value.deployKey) {
    return `/api/static/${appInfo.value.deployKey}/`
  }
  return ''
})
const previewAddressText = computed(() => {
  if (previewReady.value && previewUrl.value) {
    return previewUrl.value
  }
  if (previewBuilding.value) {
    return '正在构建临时预览...'
  }
  if ((appInfo.value.versionNo || 0) > 0) {
    return '应用已生成，等待预览地址'
  }
  return '应用生成完成后将自动展示'
})
const previewScale = ref(1)
const previewFrameHeight = ref(PREVIEW_MIN_HEIGHT)
const previewFrameStyle = computed(() => ({
  width: `${PREVIEW_DESKTOP_WIDTH * previewScale.value}px`,
  height: `${previewFrameHeight.value * previewScale.value}px`,
}))
const previewIframeStyle = computed(() => ({
  width: `${PREVIEW_DESKTOP_WIDTH}px`,
  height: `${previewFrameHeight.value}px`,
  transform: `scale(${previewScale.value})`,
}))
const visualEditRectStyle = (rect: VisualEditOverlayRect) => ({
  top: `${rect.top}px`,
  left: `${rect.left}px`,
  width: `${rect.width}px`,
  height: `${rect.height}px`,
})
const formatVisualElementLabel = (element?: VisualEditElementInfo) => {
  if (!element) {
    return ''
  }

  const idText = element.id ? `#${element.id}` : ''
  const classText = element.className
    ? `.${element.className.split(/\s+/).filter(Boolean).join('.')}`
    : ''
  return `${element.tagName.toLowerCase()}${idText}${classText}`
}
const getVisualElementText = (element?: VisualEditElementInfo) => {
  const text = element?.textContent || element?.text || ''
  return text.length > 80 ? `${text.substring(0, 80)}...` : text
}
const cloneVisualElement = (element?: VisualEditElementInfo) => {
  if (!element) {
    return undefined
  }
  return { ...element }
}
const selectedVisualElementLabel = computed(() => {
  return formatVisualElementLabel(selectedVisualElement.value)
})
const selectedVisualElementText = computed(() => {
  return getVisualElementText(selectedVisualElement.value)
})
const composerPlaceholder = computed(() =>
  selectedVisualElement.value
    ? `正在编辑 ${selectedVisualElement.value.tagName.toLowerCase()} 元素，描述您想要的修改...`
    : '继续描述要新增或修改的内容...',
)

const updatePreviewScale = () => {
  const viewport = previewViewportRef.value
  if (!viewport) {
    return
  }

  const scale = Math.min(1, viewport.clientWidth / PREVIEW_DESKTOP_WIDTH)
  previewScale.value = Math.max(scale, 0.1)
  previewFrameHeight.value = Math.max(1, viewport.clientHeight / previewScale.value)
}

const observePreviewViewport = async () => {
  await nextTick()
  previewResizeObserver?.disconnect()
  if (!previewViewportRef.value) {
    return
  }
  updatePreviewScale()
  previewResizeObserver = new ResizeObserver(updatePreviewScale)
  previewResizeObserver.observe(previewViewportRef.value)
}

const clearPreviewVisualEdit = () => {
  cleanupVisualEdit?.()
  cleanupVisualEdit = undefined
  visualEditHoverRect.value = undefined
  visualEditSelectedRect.value = undefined
}

const resolveVisualEditTarget = (event: MouseEvent) => {
  const iframe = previewIframeRef.value
  const viewport = previewViewportRef.value
  if (!iframe || !viewport) {
    return undefined
  }
  try {
    return getVisualEditTargetFromPoint(iframe, viewport, event.clientX, event.clientY)
  } catch (error) {
    console.warn('可视化编辑命中检测失败：', error)
    return undefined
  }
}

const handleVisualEditPointerMove = (event: MouseEvent) => {
  if (!visualEditEnabled.value) {
    return
  }
  const target = resolveVisualEditTarget(event)
  visualEditHoverRect.value = target?.overlayRect
}

const handleVisualEditPointerLeave = () => {
  visualEditHoverRect.value = undefined
}

const handleVisualEditPointerClick = (event: MouseEvent) => {
  if (!visualEditEnabled.value) {
    return
  }
  const target = resolveVisualEditTarget(event)
  if (!target) {
    return
  }
  selectedVisualElement.value = target.info
  visualEditSelectedRect.value = target.overlayRect
  visualEditHoverRect.value = target.overlayRect
  message.success('已选中页面元素')
}

const handleVisualEditWheel = (event: WheelEvent) => {
  const iframeWindow = previewIframeRef.value?.contentWindow
  iframeWindow?.scrollBy({
    left: event.deltaX,
    top: event.deltaY,
    behavior: 'auto',
  })
  window.requestAnimationFrame(() => {
    handleVisualEditPointerMove(event)
  })
}

const applyPreviewVisualEdit = async () => {
  await nextTick()
  clearPreviewVisualEdit()
  if (
    !visualEditEnabled.value ||
    rightPanelMode.value !== 'preview' ||
    !previewReady.value ||
    !previewIframeRef.value
  ) {
    return false
  }

  try {
    cleanupVisualEdit = enableVisualEdit(previewIframeRef.value)
    return true
  } catch (error) {
    console.warn('启用可视化编辑失败：', error)
    visualEditEnabled.value = false
    message.warning(error instanceof Error ? error.message : '预览页面尚未准备好，请稍后再进入编辑模式')
    return false
  }
}

const clearSelectedVisualElement = () => {
  selectedVisualElement.value = undefined
  visualEditSelectedRect.value = undefined
}

const disableVisualEditMode = () => {
  visualEditEnabled.value = false
  clearPreviewVisualEdit()
}

const clearVisualEditMode = () => {
  disableVisualEditMode()
  clearSelectedVisualElement()
}

const toggleVisualEditMode = async () => {
  if (visualEditEnabled.value) {
    clearVisualEditMode()
    message.info('已退出可视化编辑模式')
    return
  }
  if (
    !previewReady.value ||
    !previewUrl.value ||
    !previewFrameLoaded.value ||
    rightPanelMode.value !== 'preview'
  ) {
    message.warning('请先打开可预览的应用页面')
    return
  }

  visualEditEnabled.value = true
  const enabled = await applyPreviewVisualEdit()
  if (enabled) {
    message.success('已进入可视化编辑模式，请在预览页面点击要修改的元素')
  }
}

const handlePreviewFrameLoad = () => {
  previewFrameLoaded.value = true
  observePreviewViewport()
  window.setTimeout(updatePreviewScale, 100)
  window.setTimeout(updatePreviewScale, 500)
  if (visualEditEnabled.value) {
    applyPreviewVisualEdit()
  }
}

const isMessageListNearBottom = () => {
  const list = messageListRef.value
  if (!list) {
    return true
  }
  return list.scrollHeight - list.scrollTop - list.clientHeight <= MESSAGE_BOTTOM_THRESHOLD
}

const handleMessageListScroll = () => {
  autoScrollToBottom.value = isMessageListNearBottom()
}

const scrollToBottom = async (force = false) => {
  await nextTick()
  if (!messageListRef.value) {
    return
  }
  if (force || autoScrollToBottom.value || isMessageListNearBottom()) {
    messageListRef.value.scrollTop = messageListRef.value.scrollHeight
    autoScrollToBottom.value = true
  }
}

const formatTime = (time?: string) => {
  if (!time) {
    return '刚刚'
  }
  return new Date(time).toLocaleTimeString('zh-CN', {
    hour: '2-digit',
    minute: '2-digit',
  })
}

const loadApp = async () => {
  const res = await getAppVoById({ id: appId })
  if (res.data.code === 0 && res.data.data) {
    appInfo.value = res.data.data
    return
  }
  message.error(`获取应用失败：${res.data.message || '请稍后重试'}`)
}

const parseVisualElementPrompt = (content: string) => {
  const markerMatch = /\r?\n\r?\n选中元素信息：/.exec(content)
  const markerIndex = markerMatch?.index ?? -1
  if (markerIndex < 0) {
    return {
      displayContent: content,
      rawContent: content,
      visualElement: undefined,
    }
  }

  const displayContent = content.slice(0, markerIndex).trim()
  const promptContent = content.slice(markerIndex + (markerMatch?.[0].length || 0))
  const readLine = (label: string) => {
    const match = promptContent.match(new RegExp(`^- ${label}:\\s*(.*)$`, 'm'))
    return match?.[1]?.trim()
  }
  const tagName = readLine('标签')
  const selector = readLine('选择器')
  if (!tagName || !selector) {
    return {
      displayContent,
      rawContent: displayContent,
      visualElement: undefined,
    }
  }

  return {
    displayContent,
    rawContent: displayContent,
    visualElement: {
      tagName,
      id: '',
      className: '',
      selector,
      pagePath: readLine('页面路径') || '',
      textContent: readLine('当前内容') || '',
      text: readLine('当前内容') || '',
      rect: {
        width: 0,
        height: 0,
        top: 0,
        left: 0,
      },
    },
  }
}

const normalizeUserMessageForDisplay = (content: string) => {
  const strippedContent = stripGenerationFormatInstruction(content)
  return parseVisualElementPrompt(strippedContent)
}

const loadHistory = async (append = false) => {
  historyLoading.value = true
  try {
    const previousScrollHeight = append ? messageListRef.value?.scrollHeight || 0 : 0
    const res = await listAppChatHistory({
      appId,
      pageSize: 20,
      lastCreateTime: append ? lastCreateTime.value : undefined,
    })
    const records = res.data.data?.records ?? []
    // 后端按创建时间倒序返回，聊天窗口需要按旧到新展示。
    const orderedRecords = [...records].reverse()
    const list = orderedRecords.map((item, index): ChatMessage => {
      if (item.messageType !== 'user') {
        return {
          key: `history-${item.id || index}`,
          type: 'ai',
          content: item.message || '',
          createTime: item.createTime,
        }
      }
      const normalizedMessage = normalizeUserMessageForDisplay(item.message || '')
      return {
        key: `history-${item.id || index}`,
        type: 'user',
        content: normalizedMessage.displayContent,
        rawContent: normalizedMessage.rawContent,
        visualElement: normalizedMessage.visualElement,
        createTime: item.createTime,
      }
    })

    messages.value = append ? [...list, ...messages.value] : list
    // 下一页应查询比当前页最旧消息更早的数据。
    lastCreateTime.value = records[records.length - 1]?.createTime
    hasMoreHistory.value = records.length === 20
    if (append) {
      await nextTick()
      if (messageListRef.value) {
        messageListRef.value.scrollTop += messageListRef.value.scrollHeight - previousScrollHeight
      }
    } else {
      await scrollToBottom(true)
    }
  } finally {
    historyLoading.value = false
  }
}

const loadMoreHistory = () => {
  loadHistory(true)
}

const escapeMarkdown = (content: string) => {
  return content.replace(/\r\n/g, '\n').trim()
}

const formatExportTime = (time?: string) => {
  return time ? new Date(time).toLocaleString('zh-CN') : ''
}

const buildChatMarkdown = (historyMessages: ChatMessage[]) => {
  const title = appInfo.value.appName || '应用对话记录'
  const lines = [
    `# ${title} - 对话记录`,
    '',
    `- 应用 ID: ${appId}`,
    `- 对话轮次: ${historyMessages.filter((item) => item.type === 'user').length}`,
    `- 导出时间: ${new Date().toLocaleString('zh-CN')}`,
    '',
  ]

  historyMessages.forEach((item, index) => {
    lines.push(`## ${index + 1}. ${item.type === 'user' ? '用户' : 'AI'}`)
    if (item.createTime) {
      lines.push(`时间: ${formatExportTime(item.createTime)}`)
      lines.push('')
    }
    lines.push(escapeMarkdown(item.content || ''))
    lines.push('')
  })

  return `${lines.join('\n')}\n`
}

const downloadTextFile = (content: string, filename: string) => {
  const blob = new Blob([content], { type: 'text/markdown;charset=utf-8' })
  const url = URL.createObjectURL(blob)
  const link = document.createElement('a')
  link.href = url
  link.download = filename
  document.body.appendChild(link)
  link.click()
  document.body.removeChild(link)
  URL.revokeObjectURL(url)
}

const downloadBlobFile = (blob: Blob, filename: string) => {
  const url = URL.createObjectURL(blob)
  const link = document.createElement('a')
  link.href = url
  link.download = filename
  document.body.appendChild(link)
  link.click()
  document.body.removeChild(link)
  URL.revokeObjectURL(url)
}

const parseDownloadFilename = (contentDisposition?: string) => {
  if (!contentDisposition) {
    return ''
  }

  const utf8FilenameMatch = contentDisposition.match(/filename\*=UTF-8''([^;]+)/i)
  if (utf8FilenameMatch?.[1]) {
    return decodeURIComponent(utf8FilenameMatch[1].replace(/^"|"$/g, ''))
  }

  const filenameMatch = contentDisposition.match(/filename="?([^"]+)"?/i)
  if (filenameMatch?.[1]) {
    return decodeURIComponent(filenameMatch[1].trim())
  }

  return ''
}

const sanitizeFilename = (name: string) => {
  return name.replace(/[\\/:*?"<>|]/g, '_').slice(0, 80) || 'app-chat-history'
}

const fetchAllChatHistoryForExport = async () => {
  const pageSize = 50
  let cursor: string | undefined
  const allRecords: API.ChatHistory[] = []

  while (true) {
    const res = await listAppChatHistory({
      appId,
      pageSize,
      lastCreateTime: cursor,
    })
    const records = res.data.data?.records ?? []
    allRecords.push(...records)
    if (records.length < pageSize) {
      break
    }
    cursor = records[records.length - 1]?.createTime
    if (!cursor) {
      break
    }
  }

  return allRecords
    .reverse()
    .map(
      (item, index): ChatMessage => ({
        key: `export-${item.id || index}`,
        type: item.messageType === 'user' ? 'user' : 'ai',
        content:
          item.messageType === 'user'
            ? stripGenerationFormatInstruction(item.message || '')
            : item.message || '',
        createTime: item.createTime,
      }),
    )
}

const exportChatHistory = async () => {
  if (exportingHistory.value || chatRoundCount.value <= 0) {
    return
  }
  exportingHistory.value = true
  try {
    const historyMessages = await fetchAllChatHistoryForExport()
    if (!historyMessages.length) {
      message.warning('暂无可导出的对话记录')
      return
    }
    const markdown = buildChatMarkdown(historyMessages)
    const filename = `${sanitizeFilename(appInfo.value.appName || `app-${appId}`)}-对话记录.md`
    downloadTextFile(markdown, filename)
    message.success('对话记录已导出')
  } catch (error) {
    console.error('导出对话记录失败：', error)
    message.error('导出对话记录失败')
  } finally {
    exportingHistory.value = false
  }
}

const clearChatHistory = () => {
  if (!appId || generating.value || clearingHistory.value) {
    return
  }
  Modal.confirm({
    title: '确认清空当前应用的全部对话？',
    content: '只会删除聊天记录，不会删除应用代码、版本和部署内容。',
    okText: '确认清空',
    okType: 'danger',
    cancelText: '取消',
    async onOk() {
      clearingHistory.value = true
      try {
        const res = await deleteAppChatHistory({ appId })
        if (res.data.code !== 0 || !res.data.data) {
          throw new Error(res.data.message || '清空对话失败')
        }
        messages.value = []
        appInfo.value.chatCount = 0
        lastCreateTime.value = undefined
        hasMoreHistory.value = false
        message.success('对话已清空')
      } catch (error) {
        const errorText = error instanceof Error ? error.message : '清空对话失败'
        message.error(errorText)
      } finally {
        clearingHistory.value = false
      }
    },
  })
}

const addNotGeneratedReply = async () => {
  if ((appInfo.value.versionNo || 0) > 0 || !messages.value.length) {
    return
  }

  const lastMessage = messages.value[messages.value.length - 1]
  if (lastMessage?.type !== 'user') {
    return
  }

  messages.value.push({
    key: 'local-not-generated',
    type: 'ai',
    content: '这个应用还没有成功生成。请重新发送刚才的需求，或补充修改内容，我会继续为你生成应用。',
  })
  await scrollToBottom(true)
}

const getStringField = (value: Record<string, unknown>, keys: string[]) => {
  for (const key of keys) {
    const fieldValue = value[key]
    if (typeof fieldValue === 'string') {
      return fieldValue
    }
  }
  return ''
}

const parseSseChunk = (raw: string): StreamChunk => {
  if (!raw || raw === '[DONE]') {
    return { content: '', thinking: '' }
  }
  try {
    const value = JSON.parse(raw)
    if (typeof value === 'string') {
      return { content: value, thinking: '' }
    }
    if (!value || typeof value !== 'object') {
      return { content: '', thinking: '' }
    }

    const data = (value as Record<string, unknown>).data
    const dataContent = typeof data === 'string' ? data : ''
    const record = value as Record<string, unknown>
    const wrappedContent =
      getStringField(record, ['d', 'content', 'text', 'message', 'answer']) || dataContent
    const generatedFile = parseGeneratedFileSnapshot(wrappedContent)
    if (generatedFile) {
      return {
        content: '',
        thinking: '',
        generatedFile,
      }
    }
    return {
      content: wrappedContent,
      thinking: getStringField(record, [
        'partialThinking',
        'thinking',
        'reasoning',
        'reasoningContent',
        'reasoning_content',
        'thought',
      ]),
    }
  } catch {
    return { content: raw, thinking: '' }
  }
}

const parseGeneratedFileSnapshot = (content: string): GeneratedFileSnapshot | undefined => {
  if (!content || !content.trim().startsWith('{')) {
    return undefined
  }
  try {
    const value = JSON.parse(content) as Partial<GeneratedFileSnapshot>
    if (value?.type !== 'generated_file_snapshot') {
      return undefined
    }
    return {
      type: 'generated_file_snapshot',
      toolName: typeof value.toolName === 'string' ? value.toolName : undefined,
      action: value.action,
      actionLabel: typeof value.actionLabel === 'string' ? value.actionLabel : undefined,
      path: typeof value.path === 'string' ? value.path : undefined,
      language: typeof value.language === 'string' ? value.language : undefined,
      content: typeof value.content === 'string' ? value.content : undefined,
      files: Array.isArray(value.files)
        ? value.files.filter((item): item is string => typeof item === 'string')
        : undefined,
      deleted: Boolean(value.deleted),
    }
  } catch {
    return undefined
  }
}

const parseSseError = (event: Event) => {
  if (!(event instanceof MessageEvent) || !event.data) {
    return ''
  }
  try {
    const value = JSON.parse(event.data)
    return value.e || value.message || ''
  } catch {
    return String(event.data)
  }
}

const isGenerationTaskMissingError = (errorText: string) =>
  errorText.includes('没有正在生成') ||
  errorText.includes('没有正在生成的任务') ||
  errorText.includes('任务不存在') ||
  errorText.includes('no active') ||
  errorText.includes('not found')

const resolveTypewriterDone = () => {
  typewriterDoneResolvers.splice(0).forEach((resolve) => resolve())
}

const stopTypewriter = () => {
  if (typewriterTimer) {
    clearInterval(typewriterTimer)
    typewriterTimer = undefined
  }
}

const resetTypewriter = () => {
  stopTypewriter()
  typewriterQueue = []
  typewriterCursor = 0
  typewriterScrollCounter = 0
  resolveTypewriterDone()
}

const startTypewriter = (aiMessage: ChatMessage) => {
  if (typewriterTimer) {
    return
  }

  typewriterTimer = setInterval(() => {
    if (typewriterCursor >= typewriterQueue.length) {
      stopTypewriter()
      resolveTypewriterDone()
      return
    }

    const batchSize = typewriterQueue.length - typewriterCursor > 3000 ? 120 : 50
    const nextCursor = Math.min(typewriterCursor + batchSize, typewriterQueue.length)
    aiMessage.content += typewriterQueue.slice(typewriterCursor, nextCursor).join('')
    typewriterCursor = nextCursor
    typewriterScrollCounter += 1
    if (typewriterScrollCounter % 2 === 0 || typewriterCursor >= typewriterQueue.length) {
      scrollToBottom()
    }
  }, 16)
}

const enqueueTypewriterText = (aiMessage: ChatMessage, text: string) => {
  if (!text) {
    return
  }
  typewriterQueue = typewriterQueue.concat(Array.from(text))
  startTypewriter(aiMessage)
}

const waitForTypewriter = () => {
  if (typewriterCursor >= typewriterQueue.length && !typewriterTimer) {
    return Promise.resolve()
  }
  return new Promise<void>((resolve) => {
    typewriterDoneResolvers.push(resolve)
  })
}

const finishGeneration = async (aiMessage: ChatMessage, errorText = '') => {
  if (generationStoppedByUser) {
    return
  }
  eventSource?.close()
  eventSource = undefined
  await waitForTypewriter()
  generating.value = false
  activeAiMessageKey.value = ''

  if (pageLeftDuringGeneration) {
    return
  }

  if (streamSucceeded && !errorText) {
    generationError.value = ''
    await buildPreview()
    rightPanelMode.value = 'preview'
    return
  }

  generationError.value = errorText || '生成连接异常中断，未确认代码已成功保存，请重新生成。'
  previewReady.value = false
  if (!aiMessage.content) {
    aiMessage.content = `生成失败：${generationError.value}`
  } else if (!aiMessage.content.includes('生成失败')) {
    aiMessage.content += `\n\n生成失败：${generationError.value}`
  }
  message.error('应用生成失败，请检查提示词后重试')
  scrollToBottom()
}

const restoreFailedMessage = () => {
  inputMessage.value = lastSentMessage
}

const triggerAttachmentInput = () => {
  attachmentInputRef.value?.click()
}

const removeAttachment = (id: string) => {
  attachments.value = attachments.value.filter((attachment) => attachment.id !== id)
}

const handleAttachmentChange = async (event: Event) => {
  const input = event.target as HTMLInputElement
  const files = Array.from(input.files || [])
  input.value = ''
  if (!files.length) {
    return
  }

  uploadingAttachment.value = true
  try {
    for (const file of files) {
      const formData = new FormData()
      formData.append('file', file)
      const res = await request.post<{
        code: number
        data?: UploadedAttachment
        message?: string
      }>(`/app/assets/upload?appId=${encodeURIComponent(appId)}`, formData, {
        headers: { 'Content-Type': 'multipart/form-data' },
      })
      if (res.data.code !== 0 || !res.data.data) {
        message.error(res.data.message || `${file.name} 上传失败`)
        continue
      }
      attachments.value.push({
        ...res.data.data,
        id: `${Date.now()}-${Math.random().toString(16).slice(2)}`,
      })
    }
  } finally {
    uploadingAttachment.value = false
  }
}

const buildAttachmentPrompt = () => {
  if (!attachments.value.length) {
    return ''
  }
  const lines = ['上传附件信息：']
  attachments.value.forEach((attachment, index) => {
    lines.push(`${index + 1}. 文件名：${attachment.name}`)
    if (attachment.contentType) {
      lines.push(`   类型：${attachment.contentType}`)
    }
    if (attachment.url) {
      lines.push(`   图片 URL：${attachment.url}`)
      lines.push('   请将该图片作为页面素材使用；如果需要识别图片中文字，请以用户描述为准。')
    }
    if (attachment.textContent) {
      const text = attachment.textContent.length > 8000
        ? `${attachment.textContent.slice(0, 8000)}\n<!-- 附件内容过长，后续已省略 -->`
        : attachment.textContent
      lines.push(`   文本内容：\n<uploaded_file name="${attachment.name}">\n${text}\n</uploaded_file>`)
    }
  })
  return `\n\n${lines.join('\n')}`
}

const appendAttachmentPrompt = (content: string) => {
  return `${content.trim() || '请根据上传附件生成或修改应用'}${buildAttachmentPrompt()}`
}

const resetGeneratedCodePanel = () => {
  generationCodeCaptureBuffer = ''
  generatedFiles.value = []
  generatedFilePaths.value = []
}

const getDefaultGeneratedFilePath = (language = '', index = 0) => {
  const codeGenType = appInfo.value.codeGenType
  const normalizedLanguage = language.toLowerCase()
  if (codeGenType === 'html') {
    return 'index.html'
  }
  if (codeGenType === 'multi_file') {
    if (normalizedLanguage === 'html') return 'index.html'
    if (normalizedLanguage === 'css') return 'style.css'
    if (['js', 'javascript'].includes(normalizedLanguage)) return 'script.js'
  }
  if (normalizedLanguage === 'vue') return `generated-${index + 1}.vue`
  if (normalizedLanguage === 'html') return `generated-${index + 1}.html`
  if (normalizedLanguage === 'css') return `generated-${index + 1}.css`
  if (['js', 'javascript'].includes(normalizedLanguage)) return `generated-${index + 1}.js`
  if (normalizedLanguage === 'ts') return `generated-${index + 1}.ts`
  return `generated-${index + 1}.txt`
}

const inferFilePathBeforeFence = (prefix: string, language: string, index: number) => {
  const tail = prefix.slice(-300)
  const fileNameMatch = tail.match(/([A-Za-z0-9_./@-]+\.(?:vue|html|css|js|ts|tsx|jsx|json|md|txt))\s*$/)
  if (fileNameMatch?.[1]) {
    return fileNameMatch[1].replace(/^[-*#\s]+/, '')
  }
  return getDefaultGeneratedFilePath(language, index)
}

const inferLanguage = (filePath: string, fallback = '') => {
  if (fallback) {
    return fallback
  }
  const suffix = filePath.split('.').pop()?.toLowerCase()
  if (suffix === 'vue') return 'vue'
  if (suffix === 'js') return 'javascript'
  if (suffix === 'ts') return 'typescript'
  if (suffix === 'css') return 'css'
  if (suffix === 'html') return 'html'
  if (suffix === 'json') return 'json'
  return 'text'
}

const extractFencedCode = (text: string) => {
  const complete = text.match(/```([a-zA-Z0-9_-]*)\s*\n([\s\S]*?)```/)
  if (complete) {
    return {
      language: complete[1] || '',
      content: complete[2],
    }
  }
  const partial = text.match(/```([a-zA-Z0-9_-]*)\s*\n([\s\S]*)/)
  if (partial) {
    return {
      language: partial[1] || '',
      content: partial[2],
    }
  }
  return undefined
}

const upsertGeneratedFile = (file: GeneratedFilePanel) => {
  const index = generatedFiles.value.findIndex((item) => item.path === file.path)
  if (index >= 0) {
    generatedFiles.value[index] = file
  } else {
    generatedFiles.value.push(file)
  }
}

const applyGeneratedFileSnapshot = (snapshot: GeneratedFileSnapshot) => {
  if (snapshot.files) {
    generatedFilePaths.value = snapshot.files
  }

  const filePath = snapshot.path?.trim()
  if (!filePath) {
    return
  }

  if (snapshot.deleted) {
    generatedFiles.value = generatedFiles.value.filter((item) => item.path !== filePath)
    generatedFilePaths.value = generatedFilePaths.value.filter((path) => path !== filePath)
    return
  }

  if (typeof snapshot.content !== 'string') {
    return
  }

  upsertGeneratedFile({
    path: filePath,
    language: inferLanguage(filePath, snapshot.language || ''),
    content: snapshot.content,
    action: snapshot.action === 'modify' ? 'modify' : 'write',
    actionLabel: snapshot.actionLabel || (snapshot.action === 'modify' ? '修改文件' : '写入文件'),
  })
}

const syncFencedCodeBlocksFromBuffer = () => {
  const fencePattern = /```([a-zA-Z0-9_-]*)[^\S\r\n]*\r?\n/g
  let match: RegExpExecArray | null
  let index = 0
  while ((match = fencePattern.exec(generationCodeCaptureBuffer))) {
    const language = match[1] || ''
    const contentStart = fencePattern.lastIndex
    const contentEnd = generationCodeCaptureBuffer.indexOf('```', contentStart)
    const isClosed = contentEnd >= 0
    const content = isClosed
      ? generationCodeCaptureBuffer.slice(contentStart, contentEnd)
      : generationCodeCaptureBuffer.slice(contentStart)

    if (content.trim()) {
      const prefix = generationCodeCaptureBuffer.slice(0, match.index)
      const path = inferFilePathBeforeFence(prefix, language, index)
      upsertGeneratedFile({
        path,
        language: inferLanguage(path, language),
        content,
        action: 'stream',
        actionLabel: isClosed ? '流式代码' : '生成中',
      })
    }

    if (!isClosed) {
      break
    }
    fencePattern.lastIndex = contentEnd + 3
    index += 1
  }
}

const captureGeneratedCodeFromChunk = (content: string) => {
  if (!content) {
    return
  }
  generationCodeCaptureBuffer += content
  if (generationCodeCaptureBuffer.length > GENERATED_CODE_CAPTURE_LIMIT) {
    generationCodeCaptureBuffer = generationCodeCaptureBuffer.slice(-240_000)
  }

  if (appInfo.value.codeGenType === 'vue_project') {
    return
  }
  syncFencedCodeBlocksFromBuffer()
}

const buildGenerationRequestMessage = (content: string) => {
  return content.trim()
}

const appendVisualElementPrompt = (content: string, element?: VisualEditElementInfo) => {
  if (!element) {
    return content
  }

  let elementContext = '\n\n选中元素信息：'
  if (element.pagePath) {
    elementContext += `\n- 页面路径: ${element.pagePath}`
  }
  elementContext += `\n- 标签: ${element.tagName.toLowerCase()}\n- 选择器: ${element.selector}`
  const textContent = element.textContent || element.text
  if (textContent) {
    elementContext += `\n- 当前内容: ${textContent.substring(0, 100)}`
  }
  elementContext += [
    '',
    '修改范围约束：',
    `- 用户本次输入“${content.trim()}”只作用于上述选择器对应的元素。`,
    '- 如果用户输入是“改成 XXX / 替换为 XXX / 改为 XXX”，请将该元素的文本内容替换为 XXX。',
    '- 不要把该需求扩展为全站主题替换，不要修改其他标题、段落、卡片或导航内容，除非用户明确要求。',
  ].join('\n')

  return `${content.trim()}${elementContext}`
}

const stripGenerationFormatInstruction = (content: string) => {
  return content.endsWith(codeGenerationFormatInstruction)
    ? content.slice(0, -codeGenerationFormatInstruction.length).trimEnd()
    : content
}

const handleGenerationStreamError = async (aiMessage: ChatMessage, event: Event, url: string) => {
  const errorText = parseSseError(event)
  if (url.includes('/chat/gen/code/stream')) {
    if (isGenerationTaskMissingError(errorText)) {
      await loadApp()
      if ((appInfo.value.versionNo || 0) > 0 || appInfo.value.generationStatus === 'succeeded') {
        streamSucceeded = true
        await finishGeneration(aiMessage)
        return
      }
      await finishGeneration(aiMessage, '生成任务已结束或不存在，请重新发送需求。')
      return
    }
    await loadApp()
    if ((appInfo.value.versionNo || 0) > 0 || appInfo.value.generationStatus === 'succeeded') {
      streamSucceeded = true
      await finishGeneration(aiMessage)
      return
    }
    if (appInfo.value.generationStatus === 'generating') {
      await finishGeneration(aiMessage, errorText || '实时生成连接中断，请刷新页面重连。')
      return
    }
  }
  await finishGeneration(aiMessage, errorText)
}

const bindGenerationEventSource = (url: string, aiMessage: ChatMessage) => {
  eventSource?.close()
  eventSource = new EventSource(url, { withCredentials: true })
  const handleStreamMessage = (event: MessageEvent, immediate = false) => {
    const chunk = parseSseChunk(event.data)
    if (chunk.generatedFile) {
      applyGeneratedFileSnapshot(chunk.generatedFile)
    }
    if (chunk.thinking) {
      aiMessage.thinking = `${aiMessage.thinking || ''}${chunk.thinking}`
      scrollToBottom()
    }
    if (chunk.content) {
      captureGeneratedCodeFromChunk(chunk.content)
      if (immediate) {
        aiMessage.content += chunk.content
        scrollToBottom()
      } else {
        enqueueTypewriterText(aiMessage, chunk.content)
      }
    }
    if (chunk.content.includes('版本保存完成')) {
      streamSucceeded = true
    }
  }
  eventSource.onmessage = handleStreamMessage
  eventSource.addEventListener('snapshot', (event) => {
    if (event instanceof MessageEvent) {
      handleStreamMessage(event, true)
    }
  })
  eventSource.addEventListener('done', async () => {
    if (generationStoppedByUser) {
      return
    }
    streamSucceeded = true
    await finishGeneration(aiMessage)
  })
  eventSource.addEventListener('error', async (event) => {
    if (generationStoppedByUser || !(event instanceof MessageEvent) || !event.data) {
      return
    }
    await handleGenerationStreamError(aiMessage, event, url)
  })
  eventSource.onerror = async (event) => {
    if (generationStoppedByUser) {
      return
    }
    if (event instanceof MessageEvent && event.data) {
      return
    }
    if (url.includes('/chat/gen/code/stream')) {
      return
    }
    await handleGenerationStreamError(aiMessage, event, url)
  }
}

const getEditableUserMessageContent = (item: ChatMessage) => {
  return (item.rawContent || item.content || '').trim()
}

const getCopyMessageContent = (item: ChatMessage) => {
  if (item.type === 'user') {
    return getEditableUserMessageContent(item)
  }
  const thinkingText = item.thinking ? `思考过程：\n${item.thinking.trim()}\n\n` : ''
  return `${thinkingText}${item.content || ''}`.trim()
}

const focusComposer = async () => {
  await nextTick()
  const textarea = document.querySelector<HTMLTextAreaElement>('.composer textarea')
  textarea?.focus()
}

const copyMessage = async (item: ChatMessage) => {
  const content = getCopyMessageContent(item)
  if (!content) {
    return
  }
  try {
    await navigator.clipboard.writeText(content)
    message.success('消息已复制')
  } catch (error) {
    console.warn('复制消息失败：', error)
    message.error('复制失败，请手动选择文本复制')
  }
}

const editUserMessage = async (item: ChatMessage) => {
  const content = getEditableUserMessageContent(item)
  if (!content) {
    return
  }
  inputMessage.value = content
  clearSelectedVisualElement()
  await focusComposer()
}

const sendMessage = () => {
  const content = inputMessage.value.trim()
  if ((!content && !attachments.value.length) || generating.value || uploadingAttachment.value) {
    return
  }
  const selectedElement = selectedVisualElement.value
  const contentWithAttachments = appendAttachmentPrompt(content)
  const requestContent = appendVisualElementPrompt(contentWithAttachments, selectedElement)

  inputMessage.value = ''
  attachments.value = []
  lastSentMessage = content
  if (selectedElement || visualEditEnabled.value) {
    clearVisualEditMode()
  }
  streamSucceeded = false
  generationStoppedByUser = false
  pageLeftDuringGeneration = false
  generationError.value = ''
  resetTypewriter()
  resetGeneratedCodePanel()
  rightPanelMode.value = 'code'
  messages.value.push({
    key: `user-${Date.now()}`,
    type: 'user',
    content: content || '请根据上传附件生成或修改应用',
    rawContent: contentWithAttachments,
    visualElement: cloneVisualElement(selectedElement),
  })
  // SSE 回调必须修改响应式对象，否则分片到达时页面不会立即重新渲染。
  const aiMessage = reactive<ChatMessage>({
    key: `ai-${Date.now()}`,
    type: 'ai',
    content: '',
  })
  activeAiMessageKey.value = aiMessage.key
  messages.value.push(aiMessage)
  generating.value = true
  previewReady.value = false
  scrollToBottom(true)

  const requestMessage = buildGenerationRequestMessage(requestContent)
  const url = `http://localhost:8123/api/app/chat/gen/code?appId=${appId}&message=${encodeURIComponent(requestMessage)}`
  bindGenerationEventSource(url, aiMessage)
}

const reconnectGeneration = async () => {
  if (generating.value) {
    return
  }

  streamSucceeded = false
  generationStoppedByUser = false
  pageLeftDuringGeneration = false
  generationError.value = ''
  resetTypewriter()
  resetGeneratedCodePanel()
  rightPanelMode.value = 'code'

  const aiMessage = reactive<ChatMessage>({
    key: `ai-reconnect-${Date.now()}`,
    type: 'ai',
    content: '',
  })
  activeAiMessageKey.value = aiMessage.key
  messages.value.push(aiMessage)
  generating.value = true
  previewReady.value = false
  await scrollToBottom(true)

  const url = `http://localhost:8123/api/app/chat/gen/code/stream?appId=${appId}`
  bindGenerationEventSource(url, aiMessage)
}

const stopGeneration = async () => {
  if (!generating.value) {
    return
  }

  generationStoppedByUser = true
  try {
    await stopAppGeneration({ appId })
  } catch (error) {
    console.warn('停止生成状态更新失败：', error)
  } finally {
    eventSource?.close()
    eventSource = undefined
    resetTypewriter()
  }

  const activeAiMessage = messages.value.find((item) => item.key === activeAiMessageKey.value)
  if (activeAiMessage) {
    const stopText = '[系统] 已手动停止生成，本次不会保存新版本。'
    activeAiMessage.content = activeAiMessage.content
      ? `${activeAiMessage.content}\n\n${stopText}`
      : stopText
  }

  generating.value = false
  activeAiMessageKey.value = ''
  streamSucceeded = false
  generationError.value = '已手动停止生成'
  previewReady.value = false
  message.warning('已停止生成')
  await scrollToBottom()
}

const refreshPreview = () => {
  if (generationError.value) {
    message.warning('当前生成任务失败，没有可刷新的预览')
    return
  }
  if (!previewUrl.value) {
    message.warning('当前应用尚未生成可预览版本')
    return
  }
  previewReady.value = true
  previewKey.value += 1
  observePreviewViewport()
}

const buildPreview = async () => {
  previewBuilding.value = true
  try {
    const res = await previewApp({ appId })
    if (res.data.code !== 0 || !res.data.data) {
      throw new Error(res.data.message || '预览构建失败')
    }
    generatedPreviewUrl.value = res.data.data
    historicalPreviewUrl.value = ''
    historicalPreviewVersionNo.value = 0
    await loadApp()
    previewReady.value = true
    previewKey.value += 1
    observePreviewViewport()
  } catch (error) {
    const errorText = error instanceof Error ? error.message : '预览构建失败'
    generationError.value = `生成完成，但预览构建失败：${errorText}`
    previewReady.value = false
    message.error(generationError.value)
  } finally {
    previewBuilding.value = false
  }
}

const handleVersionChange = async () => {
  historicalPreviewUrl.value = ''
  historicalPreviewVersionNo.value = 0
  deployUrl.value = ''
  generatedPreviewUrl.value = ''
  await loadApp()
  await buildPreview()
  rightPanelMode.value = 'preview'
}

const handleVersionPreview = async (url: string, versionNo: number) => {
  historicalPreviewUrl.value = url
  historicalPreviewVersionNo.value = versionNo
  previewReady.value = true
  previewKey.value += 1
  rightPanelMode.value = 'preview'
  await observePreviewViewport()
}

const showCurrentPreview = async () => {
  historicalPreviewUrl.value = ''
  historicalPreviewVersionNo.value = 0
  if (!generatedPreviewUrl.value && (appInfo.value.versionNo || 0) > 0) {
    await buildPreview()
    return
  }
  previewReady.value = Boolean(previewUrl.value)
  previewKey.value += 1
  await observePreviewViewport()
}

const downloadAppCode = async () => {
  if (!appInfo.value.deployKey) {
    message.warning('请先部署应用，再下载代码')
    return
  }
  if ((appInfo.value.versionNo || 0) <= 0) {
    message.warning('应用生成完成后才能下载代码')
    return
  }
  if (
    downloadingCode.value ||
    generating.value ||
    previewBuilding.value ||
    deploying.value ||
    undeploying.value ||
    !canDownloadCode.value
  ) {
    return
  }

  downloadingCode.value = true
  try {
    const res = await request<Blob>(`/app/download/${encodeURIComponent(appId)}`, {
      method: 'GET',
      responseType: 'blob',
    })
    const contentDisposition = res.headers['content-disposition']
    const fallbackFilename = `${sanitizeFilename(appInfo.value.appName || `app-${appId}`)}.zip`
    const filename = parseDownloadFilename(contentDisposition) || fallbackFilename
    downloadBlobFile(res.data, filename.endsWith('.zip') ? filename : `${filename}.zip`)
    appInfo.value.downloadCount = (appInfo.value.downloadCount || 0) + 1
    await loadApp()
    message.success('代码已开始下载')
  } catch (error) {
    console.error('下载代码失败：', error)
    message.error('下载代码失败，请稍后重试')
  } finally {
    downloadingCode.value = false
  }
}

const deploy = async () => {
  if (
    generating.value ||
    previewBuilding.value ||
    deploying.value ||
    undeploying.value ||
    downloadingCode.value
  ) {
    return
  }
  deploying.value = true
  try {
    const res = await deployApp({ appId })
    if (res.data.code === 0 && res.data.data) {
      deployUrl.value = res.data.data
      await loadApp()
      previewReady.value = true
      previewKey.value += 1
      observePreviewViewport()
      deployModalOpen.value = true
      return
    }
    const errorText = res.data.message || '请稍后重试'
    message.error(`部署失败：${errorText}`)
  } catch (error) {
    const errorText = error instanceof Error ? error.message : '请稍后重试'
    message.error(`部署失败：${errorText}`)
  } finally {
    deploying.value = false
  }
}

const undeploy = () => {
  if (
    generating.value ||
    previewBuilding.value ||
    deploying.value ||
    undeploying.value ||
    downloadingCode.value
  ) {
    return
  }

  Modal.confirm({
    title: '确认下线应用？',
    content: '下线后外部访问地址会失效，应用源码和历史版本仍然保留。',
    okText: '下线',
    cancelText: '取消',
    okButtonProps: { danger: true },
    async onOk() {
      undeploying.value = true
      try {
        const res = await undeployApp({ appId })
        if (res.data.code !== 0 || !res.data.data) {
          throw new Error(res.data.message || '下线失败')
        }
        deployUrl.value = ''
        appInfo.value.deployKey = ''
        appInfo.value.deployedVersionNo = 0
        await loadApp()
        message.success('应用已下线')
      } catch (error) {
        const errorText = error instanceof Error ? error.message : '下线失败'
        message.error(errorText)
      } finally {
        undeploying.value = false
      }
    },
  })
}

const copyDeployUrl = async () => {
  await navigator.clipboard.writeText(deployUrl.value)
  message.success('部署链接已复制')
}

watch(rightPanelMode, (mode) => {
  if (mode !== 'preview') {
    disableVisualEditMode()
  }
})

watch(previewKey, () => {
  previewFrameLoaded.value = false
  previewFrameHeight.value = PREVIEW_MIN_HEIGHT
  clearPreviewVisualEdit()
})

onMounted(async () => {
  cleanupVisualEditSelection = listenVisualEditSelection((element) => {
    selectedVisualElement.value = element
    message.success('已选中页面元素')
  })

  if (!appId) {
    message.error('应用 ID 无效')
    await router.replace('/')
    return
  }

  const shouldAutoSend = route.query.autoSend === '1'
  if (shouldAutoSend) {
    const query = { ...route.query }
    delete query.autoSend
    await router.replace({ query })
  }

  await Promise.all([loadApp(), loadHistory()])
  const initPrompt = appInfo.value.initPrompt?.trim()
  const initPromptAlreadySent = messages.value.some(
    (item) => item.type === 'user' && item.content.trim() === initPrompt,
  )

  if (!shouldAutoSend && appInfo.value.generationStatus === 'generating') {
    await reconnectGeneration()
    return
  }

  if (shouldAutoSend && initPrompt && !initPromptAlreadySent) {
    inputMessage.value = initPrompt
    sendMessage()
  } else if (messages.value.length && appInfo.value.deployKey) {
    previewReady.value = true
    observePreviewViewport()
  } else if ((appInfo.value.versionNo || 0) > 0) {
    await buildPreview()
  } else {
    await addNotGeneratedReply()
  }
})

onBeforeUnmount(() => {
  cleanupVisualEditSelection?.()
  cleanupVisualEditSelection = undefined
  clearPreviewVisualEdit()
  if (generating.value) {
    pageLeftDuringGeneration = true
  } else {
    eventSource?.close()
  }
  previewResizeObserver?.disconnect()
  if (!generating.value) {
    resetTypewriter()
  }
})
</script>

<style scoped>
.studio-page {
  height: 100vh;
  overflow: hidden;
  background: #f3f5f9;
}

.studio-header {
  position: relative;
  z-index: 10;
  display: grid;
  grid-template-columns: minmax(0, 1fr) auto;
  align-items: center;
  height: 64px;
  padding: 0 18px;
  border-bottom: 1px solid #e6e9ef;
  background: #fff;
  box-shadow: 0 1px 3px rgb(16 24 40 / 3%);
}

.header-left,
.header-actions {
  display: flex;
  align-items: center;
}

.header-left {
  gap: 10px;
}

.header-left img {
  width: 34px;
  height: 34px;
  border-radius: 10px;
}

.header-divider {
  width: 1px;
  height: 24px;
  background: #eaecf0;
}

.app-title strong,
.app-title span {
  display: block;
}

.app-title strong {
  max-width: 230px;
  overflow: hidden;
  color: #182230;
  font-size: 13px;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.app-title span {
  display: flex;
  align-items: center;
  gap: 5px;
  margin-top: 2px;
  color: #98a2b3;
  font-size: 9px;
}

.app-title i {
  width: 5px;
  height: 5px;
  border-radius: 50%;
  background: #12a594;
}

.app-title i.active {
  background: #5b5cf0;
  box-shadow: 0 0 0 3px #eeeeff;
}

.header-actions {
  justify-content: flex-end;
  gap: 8px;
}

.studio-workspace {
  display: grid;
  grid-template-columns: minmax(360px, 34%) minmax(0, 1fr);
  gap: 10px;
  height: calc(100vh - 64px);
  padding: 10px;
}

.chat-panel,
.preview-panel {
  min-height: 0;
  overflow: hidden;
  border: 1px solid #e4e7ec;
  border-radius: 14px;
  background: #fff;
  box-shadow: 0 3px 10px rgb(16 24 40 / 4%);
}

.chat-panel,
.preview-panel {
  display: flex;
  flex-direction: column;
}

.panel-heading,
.preview-heading {
  display: flex;
  flex-shrink: 0;
  align-items: center;
  justify-content: space-between;
  min-height: 58px;
  padding: 0 14px 0 18px;
  border-bottom: 1px solid #f0f1f4;
}

.panel-heading > div:first-child,
.preview-heading > div:first-child {
  flex-shrink: 0;
  min-width: max-content;
}

.panel-label {
  display: block;
  color: #8b8cf3;
  font-size: 8px;
  font-weight: 700;
  letter-spacing: 1.2px;
}

.panel-heading h2,
.preview-heading h2 {
  margin: 3px 0 0;
  color: #182230;
  font-size: 14px;
  white-space: nowrap;
}

.chat-heading {
  gap: 12px;
}

.chat-title {
  display: flex;
  align-items: center;
  gap: 8px;
  min-width: 0;
}

.chat-title h2 {
  margin: 0;
}

.chat-count-tag,
.status-tag {
  margin-inline-end: 0;
  border-radius: 999px;
  font-size: 11px;
  line-height: 20px;
}

.status-tag {
  color: #667085;
  background: #f8fafc;
}

.panel-actions {
  display: flex;
  align-items: center;
  justify-content: flex-end;
  flex-wrap: wrap;
  gap: 6px;
  min-width: 0;
}

.panel-icon-button {
  display: inline-grid;
  width: 30px;
  min-width: 30px;
  height: 30px;
  padding: 0;
  color: #475467;
  border-radius: 8px;
  place-items: center;
}

.panel-icon-button:hover {
  color: #4f50d8;
  background: #f3f3ff;
}

.panel-icon-button.danger {
  color: #d92d20;
}

.panel-icon-button.danger:hover {
  color: #b42318;
  background: #fff4f3;
}

.panel-icon-button :deep(.anticon) {
  font-size: 15px;
}

.message-list {
  flex: 1;
  min-height: 0;
  overflow-y: auto;
  padding: 18px;
  background: #fafbfc;
}

.welcome-state {
  display: flex;
  align-items: center;
  justify-content: center;
  flex-direction: column;
  min-height: 100%;
  padding: 30px 10px;
  text-align: center;
}

.welcome-icon {
  display: grid;
  width: 52px;
  height: 52px;
  color: #5b5cf0;
  font-size: 22px;
  border-radius: 16px;
  background: #eeeeff;
  box-shadow: 0 10px 20px rgb(91 92 240 / 15%);
  place-items: center;
}

.welcome-state h3 {
  margin: 16px 0 5px;
  color: #344054;
  font-size: 15px;
}

.welcome-state p {
  max-width: 280px;
  margin: 0;
  color: #98a2b3;
  font-size: 10px;
  line-height: 1.7;
}

.suggestion-list {
  display: grid;
  width: 100%;
  gap: 7px;
  margin-top: 24px;
}

.suggestion-list button {
  display: flex;
  align-items: center;
  justify-content: space-between;
  height: 36px;
  padding: 0 11px;
  color: #667085;
  font-size: 10px;
  border: 1px solid #eaecf0;
  border-radius: 8px;
  background: #fff;
  cursor: pointer;
}

.suggestion-list button:hover {
  color: #5b5cf0;
  border-color: #d9dafe;
  background: #f8f8ff;
}

.message-row {
  display: flex;
  align-items: flex-start;
  gap: 8px;
  margin-bottom: 18px;
}

.message-row.user {
  justify-content: flex-end;
}

.message-bubble {
  max-width: 92%;
}

.message-owner {
  margin: 0 0 4px 3px;
  color: #98a2b3;
  font-size: 9px;
}

.user .message-owner {
  margin-right: 3px;
  text-align: right;
}

.message-content {
  padding: 12px 14px;
  color: #475467;
  font-family:
    -apple-system, BlinkMacSystemFont, 'Segoe UI', 'Microsoft YaHei', 'PingFang SC', Arial,
    sans-serif;
  font-size: 14px;
  line-height: 1.8;
  white-space: pre-wrap;
  word-break: break-word;
  border: 1px solid #e4e7ec;
  border-radius: 8px 14px 14px;
  background: #fff;
  box-shadow: 0 8px 22px rgb(16 24 40 / 6%);
}

.message-content,
.message-content :deep(*) {
  user-select: text;
}

.message-content::selection,
.message-content :deep(*)::selection {
  color: #111827;
  background: #bfdbfe;
}

.message-content.typing {
  min-width: 48px;
}

.thinking-block {
  margin-bottom: 10px;
  padding: 8px 10px;
  color: #667085;
  border: 1px solid #e4e7ec;
  border-radius: 8px;
  background: #f8fafc;
}

.thinking-block summary {
  color: #475467;
  font-size: 10px;
  font-weight: 600;
  cursor: pointer;
}

.thinking-block p {
  margin: 7px 0 0;
  font-size: 10px;
  line-height: 1.7;
  white-space: pre-wrap;
}

.typing-dots {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  height: 14px;
}

.typing-dots i {
  width: 5px;
  height: 5px;
  border-radius: 50%;
  background: #8b8cf3;
  animation: typing-dot 1.2s ease-in-out infinite;
}

.typing-dots i:nth-child(2) {
  animation-delay: 0.15s;
}

.typing-dots i:nth-child(3) {
  animation-delay: 0.3s;
}

@keyframes typing-dot {
  0%,
  60%,
  100% {
    opacity: 0.35;
    transform: translateY(0);
  }

  30% {
    opacity: 1;
    transform: translateY(-3px);
  }
}

.user .message-content {
  color: #fff;
  border-color: #5b5cf0;
  border-radius: 12px 4px 12px 12px;
  background: #5b5cf0;
  box-shadow: 0 6px 14px rgb(91 92 240 / 15%);
}

.user .message-content::selection,
.user .message-content :deep(*)::selection {
  color: #312e81;
  background: #eee7ff;
}

.user .message-content :deep(.text-block),
.user .message-content :deep(.summary-block),
.user .message-content :deep(.summary-block p),
.user .message-content :deep(.summary-block li),
.user .message-content :deep(.summary-block strong) {
  color: #fff;
}

.sent-selected-element {
  margin-top: 10px;
  padding: 9px 10px;
  color: #eef2ff;
  font-size: 12px;
  line-height: 1.7;
  border: 1px solid rgb(255 255 255 / 24%);
  border-radius: 8px;
  background: rgb(255 255 255 / 10%);
}

.sent-selected-element strong {
  color: #fff;
}

.sent-selected-title {
  margin-bottom: 2px;
  color: #fff;
  font-weight: 700;
}

.message-actions {
  display: flex;
  justify-content: flex-end;
  gap: 5px;
  margin-top: 5px;
}

.message-actions :deep(.ant-btn) {
  width: 24px;
  height: 24px;
  padding: 0;
  color: #7a7f92;
  font-size: 12px;
  border: 1px solid transparent;
}

.message-actions :deep(.ant-btn:hover) {
  color: #5b5cf0;
  border-color: #e6e4ff;
  background: #f6f4ff;
}

.message-actions :deep(.ant-btn[disabled]) {
  color: #c2c7d0;
  background: transparent;
}

.message-time {
  display: block;
  margin-top: 3px;
  color: #b1b7c2;
  font-size: 8px;
}

.user .message-time {
  text-align: right;
}

.composer-wrap {
  flex-shrink: 0;
  padding: 10px 12px 8px;
  border-top: 1px solid #f0f1f4;
  background: #fff;
}

.composer {
  padding: 9px;
  border: 1px solid #dfe3ea;
  border-radius: 11px;
  background: #fff;
  transition: border 0.2s ease;
}

.composer:focus-within {
  border-color: #bfc0ff;
  box-shadow: 0 0 0 3px rgb(91 92 240 / 7%);
}

.selected-element-alert {
  margin-bottom: 8px;
}

.selected-element-alert :deep(.ant-alert-description) {
  white-space: pre-wrap;
  word-break: break-word;
  font-size: 11px;
  line-height: 1.6;
}

.attachment-list {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
  margin-bottom: 8px;
}

.attachment-chip {
  display: inline-flex;
  align-items: center;
  gap: 5px;
  max-width: 100%;
  height: 26px;
  padding: 0 7px;
  color: #475467;
  font-size: 10px;
  border: 1px solid #dfe3ea;
  border-radius: 999px;
  background: #f8fafc;
}

.attachment-chip > span {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.attachment-chip button {
  display: inline-grid;
  width: 16px;
  height: 16px;
  padding: 0;
  color: #98a2b3;
  border: 0;
  background: transparent;
  cursor: pointer;
  place-items: center;
}

.attachment-input {
  display: none;
}

.attachment-button {
  height: 24px;
  padding-inline: 4px;
  color: #667085;
  font-size: 9px;
}

.composer :deep(textarea) {
  padding: 2px;
  border: 0;
  box-shadow: none;
  font-size: 11px;
  resize: none;
}

.composer .pause-generate-button {
  display: inline-grid;
  width: 32px;
  min-width: 32px;
  height: 32px;
  padding: 0;
  border-color: #6f63f6;
  border-radius: 50%;
  background: #6f63f6;
  place-items: center;
}

.composer .pause-generate-button:hover,
.composer .pause-generate-button:focus {
  border-color: #5f54e8;
  background: #5f54e8;
}

.composer .pause-generate-button :deep(.ant-btn-icon) {
  display: grid;
  width: 100%;
  height: 100%;
  margin: 0;
  place-items: center;
}

.stop-square-icon {
  display: block;
  width: 10px;
  height: 10px;
  border-radius: 2px;
  background: #fff;
}

.composer-footer,
.composer-hint {
  display: flex;
  align-items: center;
}

.composer-footer {
  justify-content: space-between;
  gap: 10px;
  margin-top: 7px;
}

.composer-hint {
  gap: 10px;
  color: #b1b7c2;
  font-size: 8px;
}

.composer-wrap > p {
  margin: 6px 0 0;
  color: #b1b7c2;
  font-size: 8px;
  text-align: center;
}

.preview-actions {
  display: flex;
  align-items: center;
  gap: 2px;
}

.right-mode-switch {
  display: flex;
  gap: 2px;
  margin-right: 6px;
  padding: 3px;
  border: 1px solid #eaecf0;
  border-radius: 8px;
  background: #f7f8fa;
}

.right-mode-switch button {
  display: flex;
  align-items: center;
  gap: 5px;
  height: 27px;
  padding: 0 10px;
  color: #98a2b3;
  font-size: 9px;
  border: 0;
  border-radius: 6px;
  background: transparent;
  cursor: pointer;
}

.right-mode-switch button.active {
  color: #4f50d8;
  background: #fff;
  box-shadow: 0 1px 4px rgb(16 24 40 / 8%);
}

.right-mode-switch button:disabled {
  cursor: not-allowed;
  opacity: 0.45;
}

.browser-shell {
  display: flex;
  flex: 1;
  min-height: 0;
  flex-direction: column;
  margin: 10px;
  overflow: hidden;
  border: 1px solid #dfe3ea;
  border-radius: 10px;
  background: #fff;
  box-shadow: 0 8px 24px rgb(16 24 40 / 6%);
}

.browser-toolbar {
  display: grid;
  grid-template-columns: 80px minmax(0, 1fr) 24px;
  flex-shrink: 0;
  align-items: center;
  height: 38px;
  padding: 0 11px;
  border-bottom: 1px solid #eaecf0;
  background: #f7f8fa;
}

.browser-dots {
  display: flex;
  gap: 4px;
}

.browser-dots i {
  width: 7px;
  height: 7px;
  border-radius: 50%;
}

.browser-dots i:nth-child(1) {
  background: #ff6b64;
}

.browser-dots i:nth-child(2) {
  background: #ffc44f;
}

.browser-dots i:nth-child(3) {
  background: #45c65a;
}

.address-bar {
  display: flex;
  align-items: center;
  gap: 6px;
  min-width: 0;
  height: 24px;
  padding: 0 10px;
  color: #98a2b3;
  font-size: 8px;
  border: 1px solid #eaecf0;
  border-radius: 6px;
  background: #fff;
}

.address-bar span {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.preview-viewport {
  position: relative;
  flex: 1;
  min-height: 0;
  overflow: hidden;
  background: #eef1f5;
}

.preview-viewport.visual-editing {
  box-shadow: inset 0 0 0 2px #5b5cf0;
}

.visual-edit-layer {
  position: absolute;
  inset: 0;
  z-index: 5;
  cursor: crosshair;
}

.visual-edit-box {
  position: absolute;
  pointer-events: none;
  border-radius: 2px;
}

.visual-edit-box.hover {
  border: 2px dashed #7c8cff;
  background: rgb(124 140 255 / 8%);
}

.visual-edit-box.selected {
  border: 3px solid #3949f5;
  box-shadow: 0 0 0 4px rgb(57 73 245 / 18%);
}

.preview-frame {
  position: relative;
  margin: 0 auto;
  overflow: hidden;
  background: #fff;
}

.preview-frame iframe {
  display: block;
  border: 0;
  background: #fff;
  transform-origin: top left;
}

.preview-scale {
  position: absolute;
  right: 12px;
  bottom: 12px;
  z-index: 2;
  padding: 3px 7px;
  color: #667085;
  font-size: 9px;
  border: 1px solid rgb(255 255 255 / 75%);
  border-radius: 6px;
  background: rgb(255 255 255 / 82%);
  box-shadow: 0 4px 12px rgb(16 24 40 / 8%);
  pointer-events: none;
  backdrop-filter: blur(8px);
}

.preview-empty {
  display: flex;
  align-items: center;
  justify-content: center;
  flex: 1;
  flex-direction: column;
  padding: 30px;
  text-align: center;
  background: radial-gradient(circle at 50% 45%, rgb(238 239 255 / 80%), transparent 30%), #fcfcfd;
}

.preview-visual {
  display: grid;
  width: 76px;
  height: 76px;
  color: #5b5cf0;
  font-size: 28px;
  border: 1px solid #e4e5ff;
  border-radius: 22px;
  background: #f3f3ff;
  box-shadow: 0 14px 34px rgb(91 92 240 / 13%);
  place-items: center;
}

.preview-visual.generating {
  background: #fff;
}

.preview-visual:has(.anticon-exclamation-circle) {
  color: #d92d20;
  border-color: #fee4e2;
  background: #fff4f3;
  box-shadow: 0 14px 34px rgb(217 45 32 / 10%);
}

.preview-empty h3 {
  margin: 20px 0 5px;
  color: #344054;
  font-size: 16px;
}

.preview-empty p {
  max-width: 330px;
  margin: 0;
  color: #98a2b3;
  font-size: 10px;
  line-height: 1.7;
}

.retry-button {
  margin-top: 18px;
}

.progress-steps {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-top: 24px;
  color: #b1b7c2;
  font-size: 8px;
}

.progress-steps span.done {
  color: #12a594;
}

.progress-steps span.active {
  color: #5b5cf0;
  font-weight: 600;
}

.progress-steps i {
  width: 26px;
  height: 1px;
  background: #dfe3ea;
}

.deploy-success {
  padding: 12px 0 8px;
  text-align: center;
}

.deploy-success > span {
  color: #12a594;
  font-size: 42px;
}

.deploy-success h3 {
  margin: 10px 0 4px;
  color: #182230;
}

.deploy-success p {
  margin: 0 0 20px;
  color: #98a2b3;
  font-size: 11px;
}

.deploy-link-box {
  display: grid;
  gap: 12px;
}

.deploy-link-box :deep(.ant-input) {
  height: 38px;
  color: #475467;
  border-color: #e4e7ec;
  border-radius: 999px;
  background: #fafbfc;
  text-align: center;
}

.deploy-link-actions {
  display: flex;
  justify-content: center;
  gap: 10px;
}

.deploy-link-actions :deep(.ant-btn) {
  min-width: 104px;
  height: 40px;
  border-radius: 999px;
  font-weight: 600;
}

.visit-app-button {
  padding-inline: 24px;
  box-shadow: 0 8px 18px rgb(91 92 240 / 22%);
}

@media (max-width: 900px) {
  .studio-page {
    height: auto;
    min-height: 100vh;
    overflow: auto;
  }

  .studio-header {
    grid-template-columns: 1fr auto;
  }

  .studio-workspace {
    grid-template-columns: 1fr;
    height: auto;
  }

  .chat-panel,
  .preview-panel {
    min-height: 650px;
  }
}
</style>
