<template>
  <div class="version-workspace">
    <aside class="file-sidebar" :style="{ width: `${fileSidebarWidth}px` }">
      <div class="sidebar-heading">
        <div>
          <span class="sidebar-label">EXPLORER</span>
          <strong>项目文件</strong>
        </div>
        <span>{{ filteredFiles.length }} / {{ workspaceFiles.length }}</span>
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
          <span v-if="selectedFileGenerated">
            生成中 · {{ selectedGeneratedFile?.actionLabel || '更新文件' }}
          </span>
          <span v-else-if="selectedVersionNo">
            v{{ selectedVersionNo }}
            <template v-if="dirty"> · 有未保存修改</template>
          </span>
        </div>
        <div class="toolbar-actions">
          <a-button
            v-if="showFollowLatestButton"
            size="small"
            type="primary"
            ghost
            @click="followLatestGeneratedFile"
          >
            查看最新
          </a-button>
          <a-button size="small" :disabled="generationActive || !selectedVersionNo" @click="openVersionPreview">
            <ExportOutlined />
            预览
          </a-button>
          <div class="compare-controls">
            <span class="compare-label">对比</span>
            <a-select
              v-model:value="compareBaseVersionNo"
              size="small"
              class="compare-select"
              :disabled="generationActive || versionOptions.length < 2"
              :options="versionOptions"
            />
            <span class="compare-separator">与</span>
            <a-select
              v-model:value="compareTargetVersionNo"
              size="small"
              class="compare-select"
              :disabled="generationActive || versionOptions.length < 2"
              :options="versionOptions"
            />
            <a-tooltip :title="compareTooltip">
              <span class="tooltip-button-wrap">
                <a-button size="small" :disabled="generationActive || !canCompare" @click="compareSelectedVersions">
                  <DiffOutlined />
                  对比
                </a-button>
              </span>
            </a-tooltip>
          </div>
          <a-popconfirm title="确定将该版本设为当前版本吗？" @confirm="rollbackSelectedVersion">
            <a-button
              size="small"
              :disabled="generationActive || !selectedVersionNo || selectedVersionNo === currentVersionNo"
            >
              <RollbackOutlined />
              回滚
            </a-button>
          </a-popconfirm>
          <a-button
            type="primary"
            size="small"
            :loading="saving"
            :disabled="generationActive || !selectedFile || !dirty"
            @click="saveFile"
          >
            <SaveOutlined />
            保存为新版本
          </a-button>
        </div>
      </div>

      <div v-if="diffVisible" class="diff-heading">
        <div class="diff-title">
          <strong>文件版本对比</strong>
          <span>{{ diffRelationText }}</span>
        </div>
        <a-button type="text" size="small" @click="diffVisible = false">返回编辑</a-button>
      </div>

      <a-spin :spinning="loadingFile || loadingDiff">
        <div v-if="diffVisible" class="diff-view">
          <section class="diff-pane">
            <header>
              <div>
                <strong>{{ previousDiffTitle }}</strong>
                <span class="diff-source-role">对比基线</span>
                <span class="diff-stat removed">删除 {{ removalCount }}</span>
              </div>
              <a-tooltip title="复制旧版本代码">
                <a-button
                  type="text"
                  size="small"
                  shape="circle"
                  aria-label="复制旧版本代码"
                  @click="copyDiffCode(diffData.oldCode || '')"
                >
                  <CopyOutlined />
                </a-button>
              </a-tooltip>
            </header>
            <div class="diff-code">
              <div
                v-for="(row, index) in diffRows"
                :key="`old-${index}`"
                class="diff-line"
                :class="{ removed: row.type === 'remove', blank: row.type === 'add' }"
              >
                <span class="line-no">{{ row.oldLineNo || '' }}</span>
                <code v-html="highlightCode(row.oldText, selectedFile)"></code>
              </div>
            </div>
          </section>
          <section class="diff-pane">
            <header>
              <div>
                <strong>{{ selectedDiffTitle }}</strong>
                <span class="diff-source-role">对比目标</span>
                <span class="diff-stat added">新增 {{ additionCount }}</span>
              </div>
              <a-tooltip title="复制当前版本代码">
                <a-button
                  type="text"
                  size="small"
                  shape="circle"
                  aria-label="复制当前版本代码"
                  @click="copyDiffCode(diffData.newCode || '')"
                >
                  <CopyOutlined />
                </a-button>
              </a-tooltip>
            </header>
            <div class="diff-code">
              <div
                v-for="(row, index) in diffRows"
                :key="`new-${index}`"
                class="diff-line"
                :class="{ added: row.type === 'add', blank: row.type === 'remove' }"
              >
                <span class="line-no">{{ row.newLineNo || '' }}</span>
                <code v-html="highlightCode(row.newText, selectedFile)"></code>
              </div>
            </div>
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
        <div v-else class="code-editor-shell">
          <pre ref="codeHighlightRef" class="code-highlight" aria-hidden="true"><code v-html="highlightedFileContent"></code></pre>
          <textarea
            ref="codeEditorRef"
            v-model="fileContent"
            class="code-editor"
            spellcheck="false"
            :disabled="generationActive || !selectedFile"
            @input="dirty = true"
            @scroll.passive="handleCodeEditorScroll"
          ></textarea>
        </div>
      </a-spin>
    </section>

    <aside class="version-sidebar">
      <div class="sidebar-heading">
        <div>
          <span class="sidebar-label">VERSIONS</span>
          <strong>版本历史</strong>
        </div>
      </div>
      <button
        v-if="generationActive"
        type="button"
        class="version-item active"
      >
        <span>
          <strong>生成中</strong>
          <a-tag color="blue">当前</a-tag>
        </span>
        <small>{{ generatedFiles.length ? '正在写入或修改文件' : '等待工具调用' }}</small>
        <time>实时</time>
      </button>
      <button
        v-for="version in versions"
        :key="version.versionNo"
        type="button"
        class="version-item"
        :class="{ active: !generationActive && version.versionNo === selectedVersionNo }"
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
import { computed, nextTick, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import {
  CopyOutlined,
  DiffOutlined,
  ExclamationCircleOutlined,
  ExportOutlined,
  FileOutlined,
  FolderOpenOutlined,
  FolderOutlined,
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

type GeneratedWorkspaceFile = {
  path: string
  content: string
  actionLabel?: string
}

const props = defineProps<{
  appId: string
  currentVersionNo: number
  generationActive?: boolean
  generatedFiles?: GeneratedWorkspaceFile[]
  generatedFilePaths?: string[]
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
const followGeneratedFile = ref(true)
const codeEditorRef = ref<HTMLTextAreaElement>()
const codeHighlightRef = ref<HTMLElement>()
const autoScrollCodeEditor = ref(true)
const compareBaseVersionNo = ref(0)
const compareTargetVersionNo = ref(0)
let stopResize: (() => void) | undefined

type FileTreeNode = {
  key: string
  title: string
  isLeaf?: boolean
  children?: FileTreeNode[]
}

type DiffRow = {
  type: 'equal' | 'remove' | 'add'
  oldLineNo?: number
  newLineNo?: number
  oldText: string
  newText: string
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

const generationActive = computed(() => Boolean(props.generationActive))
const generatedFiles = computed(() => (generationActive.value ? props.generatedFiles || [] : []))
const generatedFilePaths = computed(() => (generationActive.value ? props.generatedFilePaths || [] : []))
const generatedFileMap = computed(() => {
  const map = new Map<string, GeneratedWorkspaceFile>()
  generatedFiles.value.forEach((file) => {
    if (file.path) {
      map.set(file.path, file)
    }
  })
  return map
})
const workspaceFiles = computed(() => {
  const baseFiles = generatedFilePaths.value.length ? generatedFilePaths.value : files.value
  return Array.from(new Set([...baseFiles, ...generatedFileMap.value.keys()]))
    .sort((a, b) => a.localeCompare(b))
})
const selectedGeneratedFile = computed(() => generatedFileMap.value.get(selectedFile.value))
const selectedFileGenerated = computed(() => Boolean(selectedGeneratedFile.value))
const latestGeneratedFile = computed(() => generatedFiles.value[generatedFiles.value.length - 1])
const showFollowLatestButton = computed(() => {
  if (!generationActive.value || !latestGeneratedFile.value) {
    return false
  }
  return (
    !followGeneratedFile.value ||
    !autoScrollCodeEditor.value ||
    selectedFile.value !== latestGeneratedFile.value.path
  )
})

const filteredFiles = computed(() => {
  const keyword = fileKeyword.value.trim().toLowerCase()
  return keyword
    ? workspaceFiles.value.filter((file) => file.toLowerCase().includes(keyword))
    : workspaceFiles.value
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

const versionOptions = computed(() =>
  orderedVersionNos.value.map((versionNo) => ({
    label: `v${versionNo}${versionNo === props.currentVersionNo ? ' 当前' : ''}`,
    value: versionNo,
  })),
)

const canCompare = computed(() =>
  Boolean(
    selectedFile.value &&
      compareBaseVersionNo.value &&
      compareTargetVersionNo.value &&
      compareBaseVersionNo.value !== compareTargetVersionNo.value,
  ),
)
const getVersionRole = (versionNo: number) =>
  versionNo === props.currentVersionNo ? '当前应用版本' : '历史版本'
const previousDiffTitle = computed(() =>
  compareBaseVersionNo.value ? `${getVersionRole(compareBaseVersionNo.value)} v${compareBaseVersionNo.value}` : '基准版本',
)
const selectedDiffTitle = computed(() =>
  compareTargetVersionNo.value ? `${getVersionRole(compareTargetVersionNo.value)} v${compareTargetVersionNo.value}` : '目标版本',
)
const diffRelationText = computed(() => {
  if (!selectedFile.value || !compareBaseVersionNo.value || !compareTargetVersionNo.value) {
    return '请选择一个可对比的文件和版本'
  }
  return `${selectedFile.value}：${previousDiffTitle.value} 对比 ${selectedDiffTitle.value}`
})
const compareTooltip = computed(() => {
  if (generationActive.value) {
    return '生成中暂不可对比'
  }
  if (!selectedFile.value) {
    return '请先选择要对比的文件'
  }
  if (versionOptions.value.length < 2) {
    return '至少需要两个版本才能对比'
  }
  if (!compareBaseVersionNo.value || !compareTargetVersionNo.value) {
    return '请选择对比双方'
  }
  if (compareBaseVersionNo.value === compareTargetVersionNo.value) {
    return '对比双方不能是同一个版本'
  }
  return `对比 ${selectedFile.value}：v${compareBaseVersionNo.value} 与 v${compareTargetVersionNo.value}`
})

const splitCodeLines = (code?: string) => {
  if (!code) {
    return []
  }
  return code.replace(/\r\n/g, '\n').replace(/\r/g, '\n').split('\n')
}

const buildFallbackDiffRows = (oldLines: string[], newLines: string[]): DiffRow[] => {
  const rowCount = Math.max(oldLines.length, newLines.length)
  const rows: DiffRow[] = []
  for (let index = 0; index < rowCount; index += 1) {
    const oldText = oldLines[index]
    const newText = newLines[index]
    if (oldText === newText) {
      rows.push({
        type: 'equal',
        oldLineNo: oldText === undefined ? undefined : index + 1,
        newLineNo: newText === undefined ? undefined : index + 1,
        oldText: oldText || '',
        newText: newText || '',
      })
    } else {
      if (oldText !== undefined) {
        rows.push({ type: 'remove', oldLineNo: index + 1, oldText, newText: '' })
      }
      if (newText !== undefined) {
        rows.push({ type: 'add', newLineNo: index + 1, oldText: '', newText })
      }
    }
  }
  return rows
}

const buildDiffRows = (oldCode?: string, newCode?: string): DiffRow[] => {
  const oldLines = splitCodeLines(oldCode)
  const newLines = splitCodeLines(newCode)
  if (!oldLines.length && !newLines.length) {
    return []
  }
  if (oldLines.length * newLines.length > 200_000) {
    return buildFallbackDiffRows(oldLines, newLines)
  }

  const dp = Array.from({ length: oldLines.length + 1 }, () =>
    Array<number>(newLines.length + 1).fill(0),
  )

  for (let oldIndex = oldLines.length - 1; oldIndex >= 0; oldIndex -= 1) {
    for (let newIndex = newLines.length - 1; newIndex >= 0; newIndex -= 1) {
      dp[oldIndex][newIndex] =
        oldLines[oldIndex] === newLines[newIndex]
          ? dp[oldIndex + 1][newIndex + 1] + 1
          : Math.max(dp[oldIndex + 1][newIndex], dp[oldIndex][newIndex + 1])
    }
  }

  const rows: DiffRow[] = []
  let oldIndex = 0
  let newIndex = 0
  while (oldIndex < oldLines.length && newIndex < newLines.length) {
    if (oldLines[oldIndex] === newLines[newIndex]) {
      rows.push({
        type: 'equal',
        oldLineNo: oldIndex + 1,
        newLineNo: newIndex + 1,
        oldText: oldLines[oldIndex],
        newText: newLines[newIndex],
      })
      oldIndex += 1
      newIndex += 1
    } else if (dp[oldIndex + 1][newIndex] >= dp[oldIndex][newIndex + 1]) {
      rows.push({
        type: 'remove',
        oldLineNo: oldIndex + 1,
        oldText: oldLines[oldIndex],
        newText: '',
      })
      oldIndex += 1
    } else {
      rows.push({
        type: 'add',
        newLineNo: newIndex + 1,
        oldText: '',
        newText: newLines[newIndex],
      })
      newIndex += 1
    }
  }

  while (oldIndex < oldLines.length) {
    rows.push({
      type: 'remove',
      oldLineNo: oldIndex + 1,
      oldText: oldLines[oldIndex],
      newText: '',
    })
    oldIndex += 1
  }

  while (newIndex < newLines.length) {
    rows.push({
      type: 'add',
      newLineNo: newIndex + 1,
      oldText: '',
      newText: newLines[newIndex],
    })
    newIndex += 1
  }

  return rows
}

const diffRows = computed(() => buildDiffRows(diffData.value.oldCode, diffData.value.newCode))
const removalCount = computed(() => diffRows.value.filter((row) => row.type === 'remove').length)
const additionCount = computed(() => diffRows.value.filter((row) => row.type === 'add').length)

const escapeHtml = (value: string) =>
  value
    .replace(/&/g, '&amp;')
    .replace(/</g, '&lt;')
    .replace(/>/g, '&gt;')
    .replace(/"/g, '&quot;')
    .replace(/'/g, '&#39;')

const protectToken = (
  source: string,
  pattern: RegExp,
  className: string,
  tokens: string[],
) =>
  source.replace(pattern, (match) => {
    const token = `@@ZH_TOKEN_${tokens.length}@@`
    tokens.push(`<span class="code-token ${className}">${match}</span>`)
    return token
  })

const restoreTokens = (source: string, tokens: string[]) =>
  source.replace(/@@ZH_TOKEN_(\d+)@@/g, (_, index) => tokens[Number(index)] || '')

const highlightCode = (source: string, filePath: string) => {
  const tokens: string[] = []
  let html = escapeHtml(source)
  html = protectToken(html, /(&lt;!--[\s\S]*?--&gt;|\/\*[\s\S]*?\*\/|\/\/[^\n]*)/g, 'comment', tokens)
  html = protectToken(html, /(`(?:\\[\s\S]|[^`])*`|&quot;(?:\\.|[^&])*?&quot;|&#39;(?:\\.|[^&])*?&#39;)/g, 'string', tokens)
  html = protectToken(html, /\b(\d+(?:\.\d+)?)(px|rem|em|vh|vw|%|s|ms)?\b/g, 'number', tokens)

  html = protectToken(
    html,
    /\b(import|from|export|default|const|let|var|function|return|if|else|for|while|switch|case|break|continue|new|class|extends|async|await|try|catch|finally|throw|type|interface|as|in|of|true|false|null|undefined|ref|reactive|computed|watch|onMounted|defineProps|defineEmits)\b/g,
    'keyword',
    tokens,
  )
  html = protectToken(html, /([\w-]+)(?=\s*:)/g, 'property', tokens)

  if (/\.(html|vue)$/i.test(filePath)) {
    html = html.replace(/(&lt;\/?)([a-zA-Z][\w.-]*)/g, '$1<span class="code-token tag">$2</span>')
    html = html.replace(/\s([:@#\w-]+)(?==)/g, ' <span class="code-token attr">$1</span>')
  }
  return restoreTokens(html, tokens)
}

const highlightedFileContent = computed(() => highlightCode(fileContent.value, selectedFile.value))

const formatTime = (time?: string) => (time ? new Date(time).toLocaleString('zh-CN') : '-')

const copyDiffCode = async (code: string) => {
  await navigator.clipboard.writeText(code)
  message.success('代码已复制')
}

const isCodeEditorNearBottom = () => {
  const editor = codeEditorRef.value
  if (!editor) {
    return true
  }
  return editor.scrollHeight - editor.scrollTop - editor.clientHeight <= 80
}

const handleCodeEditorScroll = () => {
  const editor = codeEditorRef.value
  const highlight = codeHighlightRef.value
  if (editor && highlight) {
    highlight.scrollTop = editor.scrollTop
    highlight.scrollLeft = editor.scrollLeft
  }
  const nearBottom = isCodeEditorNearBottom()
  autoScrollCodeEditor.value = nearBottom
  if (generationActive.value) {
    followGeneratedFile.value = nearBottom
  }
}

const scrollCodeEditorToBottom = async (force = false) => {
  await nextTick()
  const editor = codeEditorRef.value
  if (!editor) {
    return
  }
  if (force || autoScrollCodeEditor.value || isCodeEditorNearBottom()) {
    editor.scrollTop = editor.scrollHeight
    if (codeHighlightRef.value) {
      codeHighlightRef.value.scrollTop = editor.scrollTop
      codeHighlightRef.value.scrollLeft = editor.scrollLeft
    }
  }
}

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

const ensureCompareSelection = () => {
  const versionNos = orderedVersionNos.value
  if (!versionNos.length) {
    compareBaseVersionNo.value = 0
    compareTargetVersionNo.value = 0
    return
  }
  if (!versionNos.includes(compareTargetVersionNo.value)) {
    compareTargetVersionNo.value =
      selectedVersionNo.value && versionNos.includes(selectedVersionNo.value)
        ? selectedVersionNo.value
        : versionNos[versionNos.length - 1] || 0
  }
  if (!versionNos.includes(compareBaseVersionNo.value) || compareBaseVersionNo.value === compareTargetVersionNo.value) {
    const targetIndex = versionNos.indexOf(compareTargetVersionNo.value)
    compareBaseVersionNo.value =
      targetIndex > 0
        ? versionNos[targetIndex - 1] || 0
        : versionNos.find((versionNo) => versionNo !== compareTargetVersionNo.value) || 0
  }
}

const loadVersions = async () => {
  loadingVersions.value = true
  try {
    const res = await listAppVersions({ appId: props.appId })
    versions.value = res.data.data || []
    ensureCompareSelection()
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
  compareTargetVersionNo.value = versionNo
  ensureCompareSelection()
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

const selectFile = async (filePath: string, options: { forceScroll?: boolean } = {}) => {
  selectedFile.value = filePath
  selectedKeys.value = [filePath]
  autoScrollCodeEditor.value = true
  diffVisible.value = false
  const generatedFile = generatedFileMap.value.get(filePath)
  if (generatedFile) {
    fileContent.value = generatedFile.content
    dirty.value = false
    scrollCodeEditorToBottom(options.forceScroll ?? true)
    return
  }
  if (!selectedVersionNo.value) {
    fileContent.value = ''
    dirty.value = false
    return
  }
  loadingFile.value = true
  try {
    const res = await readVersionFile({
      appId: props.appId,
      versionNo: selectedVersionNo.value,
      filePath,
    })
    fileContent.value = res.data.data?.content || ''
    dirty.value = false
    scrollCodeEditorToBottom(options.forceScroll ?? true)
  } finally {
    loadingFile.value = false
  }
}

const followLatestGeneratedFile = async () => {
  followGeneratedFile.value = true
  autoScrollCodeEditor.value = true
  if (latestGeneratedFile.value?.path) {
    await selectFile(latestGeneratedFile.value.path, { forceScroll: true })
    return
  }
  await scrollCodeEditorToBottom(true)
}

const handleTreeSelect = (keys: Array<string | number>, info: { node: FileTreeNode }) => {
  if (info.node.isLeaf && keys[0]) {
    followGeneratedFile.value = false
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

const compareSelectedVersions = async () => {
  if (!canCompare.value) return
  loadingDiff.value = true
  try {
    const res = await diffVersion({
      appId: props.appId,
      oldVersionNo: compareBaseVersionNo.value,
      newVersionNo: compareTargetVersionNo.value,
      fileName: selectedFile.value,
    })
    if (res.data.code !== 0) {
      message.error(res.data.message || '对比失败')
      return
    }
    diffData.value = res.data.data || {}
    diffVisible.value = true
    message.success(`正在对比 ${selectedFile.value}：v${compareBaseVersionNo.value} 与 v${compareTargetVersionNo.value}`)
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
    ensureCompareSelection()
  },
)

watch([compareBaseVersionNo, compareTargetVersionNo, selectedFile], () => {
  diffVisible.value = false
})

watch(fileKeyword, (keyword) => {
  if (keyword.trim()) expandedKeys.value = getDirectoryKeys(fileTree.value)
})

watch(
  () => props.generationActive,
  (active) => {
    if (active) {
      followGeneratedFile.value = true
      autoScrollCodeEditor.value = true
    }
  },
)

watch(
  generatedFiles,
  async (files) => {
    const latestFile = files[files.length - 1]
    if (generationActive.value && latestFile?.path && !selectedFile.value) {
      await selectFile(latestFile.path, { forceScroll: true })
      return
    }
    if (
      generationActive.value &&
      latestFile?.path &&
      followGeneratedFile.value &&
      autoScrollCodeEditor.value &&
      selectedFile.value !== latestFile.path
    ) {
      await selectFile(latestFile.path, { forceScroll: true })
      return
    }
    const generatedFile = selectedGeneratedFile.value
    if (generatedFile) {
      fileContent.value = generatedFile.content
      dirty.value = false
      scrollCodeEditorToBottom()
    }
  },
  { deep: true },
)

watch(
  generatedFilePaths,
  async (paths) => {
    if (!generationActive.value || !paths.length) {
      return
    }
    expandedKeys.value = getDirectoryKeys(fileTree.value)
    if (selectedFile.value && !workspaceFiles.value.includes(selectedFile.value)) {
      selectedFile.value = ''
      selectedKeys.value = []
      fileContent.value = ''
    }
    if (!selectedFile.value && latestGeneratedFile.value?.path) {
      await selectFile(latestGeneratedFile.value.path, { forceScroll: true })
    }
  },
  { deep: true },
)

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

.tooltip-button-wrap {
  display: inline-flex;
}

.compare-controls {
  display: inline-flex;
  align-items: center;
  gap: 5px;
  min-width: 0;
  padding: 3px;
  border: 1px solid #ecebff;
  border-radius: 8px;
  background: #faf9ff;
}

.compare-label,
.compare-separator {
  color: #667085;
  font-size: 10px;
  white-space: nowrap;
}

.compare-label {
  padding-left: 4px;
  font-weight: 600;
}

.compare-select {
  width: 92px;
}

.file-meta span {
  margin-top: 2px;
  color: #98a2b3;
  font-size: 8px;
}

.diff-heading {
  min-height: 46px;
  gap: 12px;
  padding: 8px 12px;
  color: #667085;
  font-size: 9px;
  border-bottom: 1px solid #eaecf0;
  background: #f9fafb;
}

.diff-title {
  display: grid;
  gap: 3px;
  min-width: 0;
}

.diff-title strong {
  color: #344054;
  font-size: 11px;
}

.diff-title span {
  color: #667085;
  font-size: 9px;
  line-height: 1.5;
}

.code-panel :deep(.ant-spin-nested-loading),
.code-panel :deep(.ant-spin-container) {
  flex: 1;
  min-height: 0;
  height: 100%;
}

.code-editor-shell {
  position: relative;
  width: 100%;
  height: 100%;
  min-height: 0;
  background: #fff;
  overflow: hidden;
}

.code-highlight,
.code-editor {
  position: absolute;
  inset: 0;
  width: 100%;
  height: 100%;
  margin: 0;
  padding: 20px;
  font-family: 'Cascadia Code', Consolas, monospace;
  font-size: 12px;
  line-height: 1.75;
  white-space: pre;
  tab-size: 2;
  overflow: auto;
}

.code-highlight {
  color: #344054;
  background: #fff;
  pointer-events: none;
}

.code-editor {
  color: transparent;
  caret-color: #4f50d8;
  border: 0;
  outline: none;
  background: transparent;
  resize: none;
  -webkit-text-fill-color: transparent;
}

.code-editor::selection {
  background: rgb(124 92 255 / 20%);
}

.code-highlight :deep(.code-token.comment),
.diff-code :deep(.code-token.comment) {
  color: #8a94a6;
  font-style: italic;
}

.code-highlight :deep(.code-token.string),
.diff-code :deep(.code-token.string) {
  color: #0f766e;
}

.code-highlight :deep(.code-token.number),
.diff-code :deep(.code-token.number) {
  color: #b45309;
}

.code-highlight :deep(.code-token.keyword),
.diff-code :deep(.code-token.keyword) {
  color: #7c3aed;
  font-weight: 600;
}

.code-highlight :deep(.code-token.tag),
.diff-code :deep(.code-token.tag) {
  color: #2563eb;
  font-weight: 600;
}

.code-highlight :deep(.code-token.attr),
.diff-code :deep(.code-token.attr) {
  color: #c2410c;
}

.code-highlight :deep(.code-token.property),
.diff-code :deep(.code-token.property) {
  color: #0369a1;
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
  background: #fff;
}

.diff-pane {
  display: flex;
  min-width: 0;
  min-height: 0;
  flex-direction: column;
  border-right: 1px solid #e4e7ec;
}

.diff-pane header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  flex-shrink: 0;
  min-height: 42px;
  padding: 8px 12px;
  color: #344054;
  font-size: 11px;
  border-bottom: 1px solid #eaecf0;
  background: #fff;
}

.diff-pane header > div {
  display: flex;
  align-items: center;
  gap: 10px;
  min-width: 0;
}

.diff-pane header strong {
  color: #344054;
  font-size: 12px;
}

.diff-source-role {
  color: #7a5af8;
  font-size: 10px;
  font-weight: 600;
}

.diff-pane header :deep(.ant-btn) {
  color: #667085;
}

.diff-pane header :deep(.ant-btn:hover) {
  color: #5b5cf0;
  background: #f6f4ff;
}

.diff-stat {
  display: inline-flex;
  align-items: center;
  height: 22px;
  padding: 0 8px;
  font-size: 11px;
  font-weight: 600;
  border-radius: 999px;
}

.diff-stat.removed {
  color: #b42318;
  background: #fee4e2;
}

.diff-stat.added {
  color: #067647;
  background: #dcfae6;
}

.diff-code {
  flex: 1;
  min-height: 0;
  margin: 0;
  overflow-x: hidden;
  overflow-y: auto;
  color: #344054;
  font-family: 'Cascadia Code', Consolas, monospace;
  font-size: 10px;
  line-height: 1.7;
  white-space: pre-wrap;
  background: #fff;
}

.diff-line {
  display: grid;
  grid-template-columns: 44px minmax(0, 1fr);
  width: 100%;
  min-height: 22px;
  align-items: stretch;
}

.diff-line .line-no {
  padding: 0 10px 0 8px;
  color: #98a2b3;
  text-align: right;
  user-select: none;
  background: #f8f9fc;
  border-right: 1px solid #eef0f4;
}

.diff-line code {
  display: block;
  width: 100%;
  min-width: 0;
  padding: 0 12px;
  color: inherit;
  font-family: inherit;
  background: transparent;
  white-space: pre-wrap;
  overflow-wrap: anywhere;
  word-break: break-word;
}

.diff-line.removed {
  color: #7a271a;
  background: #fff1f0;
}

.diff-line.removed .line-no {
  color: #b42318;
  background: #fee4e2;
}

.diff-line.added {
  color: #054f31;
  background: #edfcf2;
}

.diff-line.added .line-no {
  color: #067647;
  background: #dcfae6;
}

.diff-line.blank {
  background: #fff;
}

@media (max-width: 1200px) {
  .toolbar-actions :deep(.ant-btn) {
    padding-inline: 7px;
    font-size: 9px;
  }

  .toolbar-actions :deep(.ant-btn span:not(.anticon)) {
    display: none;
  }

  .compare-label,
  .compare-separator {
    display: none;
  }

  .compare-select {
    width: 78px;
  }
}
</style>
