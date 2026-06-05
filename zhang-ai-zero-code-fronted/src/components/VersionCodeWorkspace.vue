<template>
  <div class="version-workspace">
    <aside class="file-sidebar" :style="{ width: `${fileSidebarWidth}px` }">
      <div class="sidebar-heading">
        <div>
          <span class="sidebar-label">EXPLORER</span>
          <strong>项目文件</strong>
        </div>
        <span>{{ filteredFiles.length }} / {{ files.length }}</span>
      </div>
      <a-input v-model:value="fileKeyword" size="small" allow-clear placeholder="搜索文件" />
      <div v-if="fileError" class="sidebar-error">
        <ExclamationCircleOutlined />
        <p>{{ fileError }}</p>
        <a-button size="small" @click="selectVersion(selectedVersionNo)">重新加载</a-button>
      </div>
      <a-tree
        v-if="fileTree.length"
        v-model:expanded-keys="expandedKeys"
        v-model:selected-keys="selectedKeys"
        class="file-tree"
        block-node
        show-icon
        :tree-data="fileTree"
        @select="handleTreeSelect"
      >
        <template #icon="{ dataRef, expanded }">
          <FolderOpenOutlined v-if="!dataRef.isLeaf && expanded" />
          <FolderOutlined v-else-if="!dataRef.isLeaf" />
          <FileOutlined v-else />
        </template>
      </a-tree>
    </aside>

    <div class="resize-handle" title="拖动调整项目文件宽度" @pointerdown="startResize">
      <span></span>
    </div>

    <section class="code-panel">
      <div class="code-toolbar">
        <div class="file-meta">
          <strong>{{ selectedFile || '选择文件查看源码' }}</strong>
          <span v-if="selectedVersionNo">
            v{{ selectedVersionNo }}
            <template v-if="dirty"> · 有未保存修改</template>
          </span>
        </div>
        <div class="toolbar-actions">
          <a-button size="small" :disabled="!selectedVersionNo" @click="openVersionPreview">
            <ExportOutlined />
            预览
          </a-button>
          <a-button size="small" :disabled="!canCompare" @click="compareWithPrevious">
            <DiffOutlined />
            对比
          </a-button>
          <a-popconfirm title="确定将该版本设为当前版本吗？" @confirm="rollbackSelectedVersion">
            <a-button
              size="small"
              :disabled="!selectedVersionNo || selectedVersionNo === currentVersionNo"
            >
              <RollbackOutlined />
              回滚
            </a-button>
          </a-popconfirm>
          <a-button
            type="primary"
            size="small"
            :loading="saving"
            :disabled="!selectedFile || !dirty"
            @click="saveFile"
          >
            <SaveOutlined />
            保存为新版本
          </a-button>
        </div>
      </div>

      <div v-if="diffVisible" class="diff-heading">
        <span>v{{ previousVersionNo }} → v{{ selectedVersionNo }} · {{ selectedFile }}</span>
        <a-button type="text" size="small" @click="diffVisible = false">返回编辑</a-button>
      </div>

      <a-spin :spinning="loadingFile || loadingDiff">
        <div v-if="diffVisible" class="diff-view">
          <section>
            <header>v{{ previousVersionNo }}</header>
            <pre>{{ diffData.oldCode || '该版本不存在此文件' }}</pre>
          </section>
          <section>
            <header>v{{ selectedVersionNo }}</header>
            <pre>{{ diffData.newCode || '该版本不存在此文件' }}</pre>
          </section>
        </div>
        <div v-else-if="!selectedFile" class="code-empty">
          <div class="empty-icon"><CodeOutlined /></div>
          <h3>{{ fileError ? '项目文件加载失败' : '选择文件查看源代码' }}</h3>
          <p>
            {{
              fileError
                ? '请检查后端服务是否已重启并加载版本文件接口。'
                : '从左侧项目文件中选择一个文件，即可查看、修改并保存为新版本。'
            }}
          </p>
        </div>
        <textarea
          v-else
          v-model="fileContent"
          class="code-editor"
          spellcheck="false"
          :disabled="!selectedFile"
          @input="dirty = true"
        ></textarea>
      </a-spin>
    </section>

    <aside class="version-sidebar">
      <div class="sidebar-heading">
        <div>
          <span class="sidebar-label">VERSIONS</span>
          <strong>版本历史</strong>
        </div>
        <a-button type="text" size="small" :loading="loadingVersions" @click="loadVersions">
          <ReloadOutlined />
        </a-button>
      </div>
      <button
        v-for="version in versions"
        :key="version.versionNo"
        type="button"
        class="version-item"
        :class="{ active: version.versionNo === selectedVersionNo }"
        @click="selectVersion(version.versionNo || 0)"
      >
        <span>
          <strong>v{{ version.versionNo }}</strong>
          <a-tag v-if="version.versionNo === currentVersionNo" color="blue">当前</a-tag>
        </span>
        <small>{{ version.userMessage || '代码版本' }}</small>
        <time>{{ formatTime(version.createTime) }}</time>
      </button>
    </aside>
  </div>
</template>

<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import {
  DiffOutlined,
  ExclamationCircleOutlined,
  ExportOutlined,
  FileOutlined,
  FolderOpenOutlined,
  FolderOutlined,
  ReloadOutlined,
  RollbackOutlined,
  SaveOutlined,
} from '@ant-design/icons-vue'
import { message } from 'ant-design-vue'
import {
  diffVersion,
  listAppVersions,
  listVersionFiles,
  previewVersion,
  readVersionFile,
  rollbackVersion,
  saveVersionFile,
} from '@/api/appVersionController'

const props = defineProps<{
  appId: string
  currentVersionNo: number
}>()

const emit = defineEmits<{
  versionChange: [versionNo: number]
  previewVersion: [url: string, versionNo: number]
}>()

const versions = ref<API.AppVersionVO[]>([])
const files = ref<string[]>([])
const selectedVersionNo = ref(0)
const selectedFile = ref('')
const fileContent = ref('')
const fileKeyword = ref('')
const dirty = ref(false)
const diffVisible = ref(false)
const diffData = ref<API.AppVersionDiffVO>({})
const loadingVersions = ref(false)
const loadingFile = ref(false)
const loadingDiff = ref(false)
const saving = ref(false)
const fileError = ref('')
const expandedKeys = ref<string[]>([])
const selectedKeys = ref<string[]>([])
const fileSidebarWidth = ref(230)
let stopResize: (() => void) | undefined

type FileTreeNode = {
  key: string
  title: string
  isLeaf?: boolean
  children?: FileTreeNode[]
}

const clamp = (value: number, min: number, max: number) => Math.min(Math.max(value, min), max)

const startResize = (event: PointerEvent) => {
  event.preventDefault()
  stopResize?.()
  const startX = event.clientX
  const startWidth = fileSidebarWidth.value

  const handleMove = (moveEvent: PointerEvent) => {
    const delta = moveEvent.clientX - startX
    fileSidebarWidth.value = clamp(startWidth + delta, 190, 460)
  }

  const handleUp = () => stopResize?.()
  document.body.classList.add('resizing-columns')
  window.addEventListener('pointermove', handleMove)
  window.addEventListener('pointerup', handleUp, { once: true })
  stopResize = () => {
    document.body.classList.remove('resizing-columns')
    window.removeEventListener('pointermove', handleMove)
    window.removeEventListener('pointerup', handleUp)
    stopResize = undefined
  }
}

const filteredFiles = computed(() => {
  const keyword = fileKeyword.value.trim().toLowerCase()
  return keyword ? files.value.filter((file) => file.toLowerCase().includes(keyword)) : files.value
})

const fileTree = computed<FileTreeNode[]>(() => {
  const root: FileTreeNode[] = []
  filteredFiles.value.forEach((filePath) => {
    const parts = filePath.split('/')
    let level = root
    parts.forEach((part, index) => {
      const key = parts.slice(0, index + 1).join('/')
      let node = level.find((item) => item.key === key)
      if (!node) {
        node = {
          key,
          title: part,
          isLeaf: index === parts.length - 1,
          children: index === parts.length - 1 ? undefined : [],
        }
        level.push(node)
      }
      if (node.children) level = node.children
    })
  })
  return root
})

const getDirectoryKeys = (nodes: FileTreeNode[]): string[] =>
  nodes.flatMap((node) => (node.children ? [node.key, ...getDirectoryKeys(node.children)] : []))

const orderedVersionNos = computed(() =>
  versions.value
    .map((version) => version.versionNo || 0)
    .filter(Boolean)
    .sort((a, b) => a - b),
)

const previousVersionNo = computed(() => {
  const index = orderedVersionNos.value.indexOf(selectedVersionNo.value)
  return index > 0 ? orderedVersionNos.value[index - 1] || 0 : 0
})

const canCompare = computed(() =>
  Boolean(selectedFile.value && selectedVersionNo.value && previousVersionNo.value),
)

const formatTime = (time?: string) => (time ? new Date(time).toLocaleString('zh-CN') : '-')

const getRequestErrorText = (error: unknown) => {
  const requestError = error as {
    response?: { status?: number; data?: { message?: string } }
  }
  if (!requestError.response) {
    return '无法连接后端服务，请确认 localhost:8123 已启动'
  }
  if (requestError.response.status === 404) {
    return '版本文件接口不存在，请重启后端服务'
  }
  return requestError.response.data?.message || '版本文件加载失败'
}

const loadVersions = async () => {
  loadingVersions.value = true
  try {
    const res = await listAppVersions({ appId: props.appId })
    versions.value = res.data.data || []
    if (!selectedVersionNo.value && versions.value.length) {
      await selectVersion(props.currentVersionNo || versions.value[0]?.versionNo || 0)
    }
  } finally {
    loadingVersions.value = false
  }
}

const selectVersion = async (versionNo: number) => {
  if (!versionNo) return
  selectedVersionNo.value = versionNo
  selectedFile.value = ''
  fileContent.value = ''
  dirty.value = false
  diffVisible.value = false
  fileError.value = ''
  loadingFile.value = true
  try {
    const res = await listVersionFiles({ appId: props.appId, versionNo })
    if (res.data.code !== 0) {
      throw new Error(res.data.message || '版本文件加载失败')
    }
    files.value = res.data.data || []
    expandedKeys.value = getDirectoryKeys(fileTree.value)
    const preferredFile =
      files.value.find((file) => file === 'src/App.vue') ||
      files.value.find((file) => file === 'index.html') ||
      files.value[0]
    if (preferredFile) await selectFile(preferredFile)
  } catch (error) {
    files.value = []
    fileError.value = getRequestErrorText(error)
  } finally {
    loadingFile.value = false
  }
}

const selectFile = async (filePath: string) => {
  selectedFile.value = filePath
  selectedKeys.value = [filePath]
  loadingFile.value = true
  diffVisible.value = false
  try {
    const res = await readVersionFile({
      appId: props.appId,
      versionNo: selectedVersionNo.value,
      filePath,
    })
    fileContent.value = res.data.data?.content || ''
    dirty.value = false
  } finally {
    loadingFile.value = false
  }
}

const handleTreeSelect = (keys: Array<string | number>, info: { node: FileTreeNode }) => {
  if (info.node.isLeaf && keys[0]) {
    selectFile(String(keys[0]))
    return
  }
  selectedKeys.value = selectedFile.value ? [selectedFile.value] : []
}

const saveFile = async () => {
  saving.value = true
  try {
    const res = await saveVersionFile({
      appId: props.appId,
      versionNo: selectedVersionNo.value,
      filePath: selectedFile.value,
      content: fileContent.value,
    })
    const newVersionNo = res.data.data?.versionNo
    if (!newVersionNo) {
      message.error(res.data.message || '保存失败')
      return
    }
    message.success(`已保存为 v${newVersionNo}`)
    dirty.value = false
    emit('versionChange', newVersionNo)
    selectedVersionNo.value = 0
    await loadVersions()
    await selectVersion(newVersionNo)
  } finally {
    saving.value = false
  }
}

const rollbackSelectedVersion = async () => {
  const res = await rollbackVersion({ appId: props.appId, versionNo: selectedVersionNo.value })
  if (res.data.code !== 0) {
    message.error(res.data.message || '回滚失败')
    return
  }
  message.success(`已回滚到 v${selectedVersionNo.value}`)
  emit('versionChange', selectedVersionNo.value)
  await loadVersions()
}

const compareWithPrevious = async () => {
  if (!canCompare.value) return
  loadingDiff.value = true
  try {
    const res = await diffVersion({
      appId: props.appId,
      oldVersionNo: previousVersionNo.value,
      newVersionNo: selectedVersionNo.value,
      fileName: selectedFile.value,
    })
    diffData.value = res.data.data || {}
    diffVisible.value = true
  } finally {
    loadingDiff.value = false
  }
}

const openVersionPreview = async () => {
  const res = await previewVersion({ appId: props.appId, versionNo: selectedVersionNo.value })
  if (res.data.data) {
    emit('previewVersion', res.data.data, selectedVersionNo.value)
    return
  }
  message.error(res.data.message || '版本预览失败')
}

watch(
  () => props.currentVersionNo,
  (versionNo) => {
    if (versionNo && !selectedVersionNo.value) selectVersion(versionNo)
  },
)

watch(fileKeyword, (keyword) => {
  if (keyword.trim()) expandedKeys.value = getDirectoryKeys(fileTree.value)
})

onMounted(loadVersions)
onBeforeUnmount(() => stopResize?.())
</script>

<style scoped>
.version-workspace {
  display: flex;
  flex: 1;
  min-height: 0;
  overflow: hidden;
  background: #fff;
  flex-wrap: nowrap;
}

.version-sidebar,
.file-sidebar {
  flex-shrink: 0;
  min-height: 0;
  padding: 14px 12px;
  overflow: auto;
  background: #f8f9fc;
}

.file-sidebar {
  border-right: 0;
}

.version-sidebar {
  width: 190px;
  border-left: 0;
}

.resize-handle {
  position: relative;
  z-index: 3;
  display: flex;
  align-items: center;
  justify-content: center;
  min-height: 0;
  width: 6px;
  flex: 0 0 6px;
  cursor: col-resize;
  background: #f4f5f8;
  transition: background 0.18s ease;
  touch-action: none;
}

.resize-handle::before {
  position: absolute;
  inset: 0 -3px;
  content: '';
}

.resize-handle span {
  width: 2px;
  height: 34px;
  border-radius: 2px;
  background: #cdd2dc;
  transition:
    height 0.18s ease,
    background 0.18s ease;
}

.resize-handle:hover {
  background: #eeeeff;
}

.resize-handle:hover span {
  height: 52px;
  background: #7778ed;
}

:global(body.resizing-columns) {
  cursor: col-resize !important;
  user-select: none !important;
}

:global(body.resizing-columns *) {
  cursor: col-resize !important;
}

.sidebar-heading,
.code-toolbar,
.diff-heading {
  display: flex;
  align-items: center;
}

.sidebar-heading {
  min-height: 38px;
  margin-bottom: 12px;
  color: #344054;
  font-size: 11px;
}

.sidebar-heading > div strong,
.sidebar-label {
  display: block;
}

.sidebar-label {
  margin-bottom: 2px;
  color: #8b8cf3;
  font-size: 7px;
  font-weight: 700;
  letter-spacing: 1px;
}

.sidebar-heading span {
  color: #98a2b3;
  font-size: 9px;
}

.sidebar-heading .sidebar-label {
  color: #8b8cf3;
  font-size: 7px;
}

.version-item {
  width: 100%;
  color: #667085;
  border: 0;
  background: transparent;
  cursor: pointer;
}

.version-item {
  display: grid;
  gap: 4px;
  margin-bottom: 7px;
  padding: 11px;
  text-align: left;
  border: 1px solid transparent;
  border-radius: 10px;
}

.version-item:hover,
.version-item.active {
  color: #4f50d8;
  border-color: #dddfff;
  background: #eeeeff;
}

.version-item > span {
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.version-item small,
.version-item time {
  overflow: hidden;
  font-size: 8px;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.version-item time {
  color: #98a2b3;
}

.file-tree {
  margin-top: 10px;
  color: #667085;
  background: transparent;
  font-size: 10px;
}

.file-tree :deep(.ant-tree-treenode) {
  width: 100%;
  padding: 1px 0;
}

.file-tree :deep(.ant-tree-node-content-wrapper) {
  min-width: 0;
  flex: 1;
  padding: 4px 6px;
  overflow: hidden;
  border-radius: 6px;
}

.file-tree :deep(.ant-tree-node-content-wrapper:hover) {
  color: #4f50d8;
  background: #f0f0ff;
}

.file-tree :deep(.ant-tree-node-selected) {
  color: #4f50d8 !important;
  background: #e9eaff !important;
}

.file-tree :deep(.ant-tree-title) {
  display: inline-block;
  max-width: calc(100% - 4px);
  overflow: hidden;
  text-overflow: ellipsis;
  vertical-align: bottom;
  white-space: nowrap;
}

.file-tree :deep(.ant-tree-node-content-wrapper:hover .ant-tree-title) {
  position: relative;
  z-index: 2;
  max-width: none;
  padding-right: 5px;
  background: #f0f0ff;
}

.file-tree :deep(.ant-tree-switcher) {
  width: 18px;
  color: #98a2b3;
}

.file-tree :deep(.ant-tree-iconEle) {
  color: #7c83a0;
}

.file-tree :deep(.ant-tree-indent-unit) {
  width: 15px;
}

.code-panel {
  display: flex;
  flex: 1 1 0;
  min-width: 0;
  min-height: 0;
  flex-direction: column;
}

.code-toolbar {
  flex-shrink: 0;
  min-height: 58px;
  gap: 12px;
  padding: 8px 14px;
  border-bottom: 1px solid #eaecf0;
  background: #fff;
  flex-wrap: wrap;
  justify-content: flex-start;
}

.sidebar-heading,
.diff-heading {
  justify-content: space-between;
}

.file-meta {
  min-width: 0;
  max-width: 100%;
  flex: 0 1 auto;
}

.file-meta strong,
.file-meta span {
  display: block;
}

.file-meta strong {
  max-width: min(360px, 100%);
  overflow: hidden;
  color: #344054;
  font-size: 11px;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.toolbar-actions {
  display: flex;
  align-items: center;
  justify-content: flex-start;
  gap: 6px;
  min-width: 0;
  flex: 0 1 auto;
  flex-wrap: wrap;
}

.file-meta span {
  margin-top: 2px;
  color: #98a2b3;
  font-size: 8px;
}

.diff-heading {
  height: 34px;
  padding: 0 12px;
  color: #667085;
  font-size: 9px;
  border-bottom: 1px solid #eaecf0;
  background: #f9fafb;
}

.code-panel :deep(.ant-spin-nested-loading),
.code-panel :deep(.ant-spin-container) {
  flex: 1;
  min-height: 0;
  height: 100%;
}

.code-editor {
  width: 100%;
  height: 100%;
  min-height: 0;
  padding: 20px;
  color: #d6deeb;
  border: 0;
  outline: none;
  background: #0f1728;
  font-family: 'Cascadia Code', Consolas, monospace;
  font-size: 12px;
  line-height: 1.75;
  resize: none;
  tab-size: 2;
}

.code-empty {
  display: flex;
  align-items: center;
  justify-content: center;
  height: 100%;
  padding: 30px;
  flex-direction: column;
  text-align: center;
  background: radial-gradient(circle at 50% 42%, rgb(238 239 255 / 70%), transparent 28%), #fcfcfd;
}

.empty-icon {
  display: grid;
  width: 58px;
  height: 58px;
  color: #5b5cf0;
  font-size: 22px;
  border: 1px solid #e4e5ff;
  border-radius: 17px;
  background: #fff;
  box-shadow: 0 12px 28px rgb(91 92 240 / 12%);
  place-items: center;
}

.code-empty h3 {
  margin: 15px 0 5px;
  color: #344054;
  font-size: 14px;
}

.code-empty p {
  max-width: 300px;
  margin: 0;
  color: #98a2b3;
  font-size: 10px;
  line-height: 1.7;
}

.sidebar-error {
  margin-top: 12px;
  padding: 14px 10px;
  color: #d92d20;
  text-align: center;
  border: 1px solid #fee4e2;
  border-radius: 10px;
  background: #fff7f6;
}

.sidebar-error > span {
  font-size: 18px;
}

.sidebar-error p {
  margin: 7px 0 10px;
  font-size: 9px;
  line-height: 1.6;
}

.diff-view {
  display: grid;
  grid-template-columns: 1fr 1fr;
  height: 100%;
  min-height: 0;
  background: #101828;
}

.diff-view section {
  display: flex;
  min-width: 0;
  min-height: 0;
  flex-direction: column;
  border-right: 1px solid #344054;
}

.diff-view header {
  flex-shrink: 0;
  padding: 7px 12px;
  color: #d0d5dd;
  font-size: 9px;
  border-bottom: 1px solid #344054;
  background: #1d2939;
}

.diff-view pre {
  flex: 1;
  min-height: 0;
  margin: 0;
  padding: 14px;
  overflow: auto;
  color: #d0d5dd;
  font-family: 'Cascadia Code', Consolas, monospace;
  font-size: 10px;
  line-height: 1.65;
  white-space: pre;
}

@media (max-width: 1200px) {
  .toolbar-actions :deep(.ant-btn) {
    padding-inline: 7px;
    font-size: 9px;
  }

  .toolbar-actions :deep(.ant-btn span:not(.anticon)) {
    display: none;
  }
}
</style>
