<template>
  <div class="chat-message-content">
    <template v-for="(block, index) in blocks" :key="index">
      <div v-if="block.type === 'code'" class="code-block">
        <div class="code-heading">
          <span class="code-language">{{ block.language || 'code' }}</span>
          <a-tooltip title="复制代码">
            <a-button type="text" size="small" @click="copyCode(block.content)">
              <CopyOutlined />
            </a-button>
          </a-tooltip>
        </div>
        <pre><code v-html="highlightCode(block.content, block.language)"></code></pre>
      </div>

      <div v-else-if="block.type === 'status'" :class="['status-block', statusClass(block.content)]">
        {{ block.content }}
      </div>

      <div v-else-if="block.type === 'summary'" class="summary-block">
        <strong v-if="block.title">{{ block.title }}</strong>
        <ol v-if="block.items?.length">
          <li v-for="item in block.items" :key="item">{{ item }}</li>
        </ol>
        <p v-else>{{ block.content }}</p>
      </div>

      <p v-else class="text-block">{{ block.content }}</p>
    </template>
  </div>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import { CopyOutlined } from '@ant-design/icons-vue'
import { message } from 'ant-design-vue'

const props = defineProps<{
  content: string
}>()

type MessageBlock = {
  type: 'text' | 'code' | 'status' | 'summary'
  content: string
  language?: string
  title?: string
  items?: string[]
}

const codeLanguages = new Set([
  'vue',
  'html',
  'css',
  'scss',
  'less',
  'js',
  'javascript',
  'ts',
  'typescript',
  'json',
  'tsx',
  'jsx',
])

const statusLinePattern = /^\[(?:[^\]]*(?:tool|write|file|call|工具|写入|完成|选择|正在)[^\]]*)\]/i
const numberedLinePattern = /^\s*(?:\d+[\.\u3001]|[-*])\s+/
const summaryTitlePattern = /(?:修改说明|变更说明|更新说明|说明|Summary|Changes)\s*[:：]?/i

type HighlightPattern = {
  regex: RegExp
  className: string
}

const escapeHtml = (value: string) =>
  value
    .replace(/&/g, '&amp;')
    .replace(/</g, '&lt;')
    .replace(/>/g, '&gt;')
    .replace(/"/g, '&quot;')
    .replace(/'/g, '&#39;')

const highlightWithPatterns = (source: string, patterns: HighlightPattern[]) => {
  let cursor = 0
  let highlighted = ''

  while (cursor < source.length) {
    let bestMatch: RegExpExecArray | null = null
    let bestPattern: HighlightPattern | undefined

    for (const pattern of patterns) {
      pattern.regex.lastIndex = cursor
      const match = pattern.regex.exec(source)
      if (!match || match.index < cursor || match[0].length === 0) {
        continue
      }
      if (!bestMatch || match.index < bestMatch.index) {
        bestMatch = match
        bestPattern = pattern
      }
    }

    if (!bestMatch || !bestPattern) {
      highlighted += escapeHtml(source.slice(cursor))
      break
    }

    highlighted += escapeHtml(source.slice(cursor, bestMatch.index))
    highlighted += `<span class="${bestPattern.className}">${escapeHtml(bestMatch[0])}</span>`
    cursor = bestMatch.index + bestMatch[0].length
  }

  return highlighted
}

const scriptPatterns: HighlightPattern[] = [
  { regex: /\/\/.*|\/\*[\s\S]*?\*\//g, className: 'tok-comment' },
  { regex: /`(?:\\[\s\S]|[^`\\])*`|"(?:\\.|[^"\\])*"|'(?:\\.|[^'\\])*'/g, className: 'tok-string' },
  {
    regex:
      /\b(?:import|from|export|default|const|let|var|function|return|if|else|for|while|async|await|class|extends|new|try|catch|finally|throw|typeof|interface|type|enum|public|private|protected|readonly|true|false|null|undefined|ref|reactive|computed|watch|watchEffect|onMounted|onUnmounted|onBeforeMount|onBeforeUnmount|onUpdated|onBeforeUpdate|onActivated|onDeactivated|onErrorCaptured|defineProps|defineEmits|defineExpose|withDefaults|provide|inject|useRouter|useRoute|nextTick|toRefs|toRef|shallowRef|triggerRef|customRef|shallowReactive|markRaw|toRaw|isRef|isReactive|isReadonly|isProxy|effectScope|getCurrentScope|onScopeDispose|unref|toValue|maybeRef|maybeRefOrGetter)\b/g,
    className: 'tok-keyword',
  },
  { regex: /\b[A-Z][A-Za-z0-9_]*(?=[<({\s])/g, className: 'tok-type' },
  { regex: /\b[A-Za-z_$][\w$]*(?=\s*\()/g, className: 'tok-function' },
  { regex: /\b\d+(?:\.\d+)?\b/g, className: 'tok-number' },
]

const htmlPatterns: HighlightPattern[] = [
  { regex: /<!--[\s\S]*?-->/g, className: 'tok-comment' },
  { regex: /<\/?[A-Za-z][\w:-]*/g, className: 'tok-tag' },
  { regex: /\b[@:A-Za-z_][\w:.-]*(?=\=)/g, className: 'tok-attr' },
  { regex: /"(?:\\.|[^"\\])*"|'(?:\\.|[^'\\])*'/g, className: 'tok-string' },
  // Vue 模板指令
  { regex: /\bv-(?:if|else-if|else|for|model|bind|on|show|html|text|once|cloak|pre|memo)\b/g, className: 'tok-directive' },
  { regex: /@\w+/g, className: 'tok-directive' },
  { regex: /\{\{|\}\}/g, className: 'tok-interpolation' },
]

const cssPatterns: HighlightPattern[] = [
  { regex: /\/\*[\s\S]*?\*\//g, className: 'tok-comment' },
  { regex: /"(?:\\.|[^"\\])*"|'(?:\\.|[^'\\])*'/g, className: 'tok-string' },
  { regex: /[#.][A-Za-z_-][\w-]*/g, className: 'tok-selector' },
  { regex: /\b[A-Za-z-]+(?=\s*:)/g, className: 'tok-attr' },
  { regex: /#[\da-fA-F]{3,8}\b|\b\d+(?:\.\d+)?(?:px|rem|em|%|vh|vw|s|ms)?\b/g, className: 'tok-number' },
]

const jsonPatterns: HighlightPattern[] = [
  { regex: /"(?:\\.|[^"\\])*"(?=\s*:)/g, className: 'tok-attr' },
  { regex: /"(?:\\.|[^"\\])*"/g, className: 'tok-string' },
  { regex: /\b(?:true|false|null)\b/g, className: 'tok-keyword' },
  { regex: /-?\b\d+(?:\.\d+)?\b/g, className: 'tok-number' },
]

const highlightCode = (content: string, language = '') => {
  const normalizedLanguage = language.toLowerCase()
  if (['html', 'vue', 'xml'].includes(normalizedLanguage)) {
    return highlightWithPatterns(content, htmlPatterns)
  }
  if (['css', 'scss', 'less'].includes(normalizedLanguage)) {
    return highlightWithPatterns(content, cssPatterns)
  }
  if (['json'].includes(normalizedLanguage)) {
    return highlightWithPatterns(content, jsonPatterns)
  }
  if (['js', 'javascript', 'ts', 'typescript', 'jsx', 'tsx'].includes(normalizedLanguage)) {
    return highlightWithPatterns(content, scriptPatterns)
  }
  return highlightWithPatterns(content, [...scriptPatterns, ...htmlPatterns])
}

const looksLikeCodeLine = (line: string) => {
  const text = line.trim()
  return (
    text.startsWith('<') ||
    text.startsWith('{') ||
    text.startsWith('[') ||
    text.startsWith('*') ||
    text.startsWith('}') ||
    text.startsWith('.') ||
    text.startsWith('#') ||
    text.startsWith('@') ||
    /^[A-Za-z_-][\w-]*\s*:/.test(text) ||
    /^[A-Za-z_*#.:>[\]=~^$|'" -]+[,{]\s*$/.test(text) ||
    /^(import|export|const|let|var|function|class|interface|type|async|await|return)\b/.test(text)
  )
}

const isGenerationStatusLine = (line: string) => {
  return statusLinePattern.test(line.trim())
}

const statusClass = (content: string) => {
  const text = content.trim()
  if (/^\[正在编写\]/.test(text)) return 'status-writing'
  if (/^\[工具调用\]/.test(text)) return 'status-executed'
  if (/^\[选择工具\]/.test(text)) return 'status-selecting'
  return ''
}

const isOnlyGenerationStatusContent = (content: string) => {
  const lines = content
    .replace(/\r\n/g, '\n')
    .split('\n')
    .map((line) => line.trim())
    .filter(Boolean)
  return lines.length > 0 && lines.every(isGenerationStatusLine)
}

const appendTextBlock = (result: MessageBlock[], content: string) => {
  const lines = content
    .replace(/\r\n/g, '\n')
    .split('\n')
    .map((line) => line.trim())
    .filter(Boolean)

  if (lines.length && lines.every(isGenerationStatusLine)) {
    lines.forEach((line) => result.push({ type: 'status', content: line }))
    return
  }

  // 混合内容：将状态行从普通文本中分离，各自使用对应的渲染样式
  if (lines.some(isGenerationStatusLine)) {
    let currentTextLines: string[] = []
    for (const line of lines) {
      if (isGenerationStatusLine(line)) {
        if (currentTextLines.length > 0) {
          result.push({ type: 'text', content: currentTextLines.join('\n') })
          currentTextLines = []
        }
        result.push({ type: 'status', content: line })
      } else {
        currentTextLines.push(line)
      }
    }
    if (currentTextLines.length > 0) {
      result.push({ type: 'text', content: currentTextLines.join('\n') })
    }
    return
  }

  const summaryTitleIndex = lines.findIndex((line) => summaryTitlePattern.test(line))
  const listStartIndex = lines.findIndex((line) => numberedLinePattern.test(line))

  if (summaryTitleIndex >= 0 && listStartIndex > summaryTitleIndex) {
    const beforeSummary = lines.slice(0, summaryTitleIndex).join('\n')
    if (beforeSummary) {
      result.push({ type: 'text', content: beforeSummary })
    }

    result.push({
      type: 'summary',
      title: lines[summaryTitleIndex].replace(/\s*[:：]\s*$/, ''),
      content,
      items: lines
        .slice(listStartIndex)
        .filter((line) => numberedLinePattern.test(line))
        .map((line) => line.replace(numberedLinePattern, '').trim()),
    })
    return
  }

  if (lines.length > 1 && lines.every((line) => numberedLinePattern.test(line))) {
    result.push({
      type: 'summary',
      title: '',
      content,
      items: lines.map((line) => line.replace(numberedLinePattern, '').trim()),
    })
    return
  }

  result.push({ type: 'text', content })
}

const appendCodeBlock = (result: MessageBlock[], language: string, content: string) => {
  const normalizedContent = content.trimEnd()
  if (isOnlyGenerationStatusContent(normalizedContent)) {
    appendTextBlock(result, normalizedContent)
    return
  }
  result.push({
    type: 'code',
    language,
    content: normalizedContent,
  })
}

const appendTextOrLooseCode = (result: MessageBlock[], segment: string) => {
  const lines = segment.replace(/\r\n/g, '\n').split('\n')
  let index = 0

  while (index < lines.length) {
    const language = lines[index].trim().toLowerCase()
    const nextCodeLineIndex = lines.findIndex(
      (line, lineIndex) => lineIndex > index && line.trim(),
    )

    if (
      codeLanguages.has(language) &&
      nextCodeLineIndex > index &&
      looksLikeCodeLine(lines[nextCodeLineIndex])
    ) {
      const text = lines.slice(0, index).join('\n').trim()
      if (text) {
        appendTextBlock(result, text)
      }

      let nextLanguageIndex = index + 1
      while (nextLanguageIndex < lines.length) {
        if (isGenerationStatusLine(lines[nextLanguageIndex])) {
          break
        }

        const nextLanguage = lines[nextLanguageIndex].trim().toLowerCase()
        const nextCodeIndex = lines.findIndex(
          (line, lineIndex) => lineIndex > nextLanguageIndex && line.trim(),
        )
        if (
          codeLanguages.has(nextLanguage) &&
          nextCodeIndex > nextLanguageIndex &&
          looksLikeCodeLine(lines[nextCodeIndex])
        ) {
          break
        }
        nextLanguageIndex += 1
      }

      appendCodeBlock(result, language, lines.slice(index + 1, nextLanguageIndex).join('\n'))
      appendTextOrLooseCode(result, lines.slice(nextLanguageIndex).join('\n'))
      return
    }

    index += 1
  }

  const text = segment.trim()
  if (text) {
    appendTextBlock(result, text)
  }
}

const blocks = computed<MessageBlock[]>(() => {
  const result: MessageBlock[] = []
  let cursor = 0

  while (cursor < props.content.length) {
    const fenceStart = props.content.indexOf('```', cursor)
    if (fenceStart < 0) {
      appendTextOrLooseCode(result, props.content.slice(cursor))
      break
    }

    appendTextOrLooseCode(result, props.content.slice(cursor, fenceStart))

    const languageStart = fenceStart + 3
    const firstLineBreak = props.content.indexOf('\n', languageStart)
    if (firstLineBreak < 0) {
      appendCodeBlock(result, props.content.slice(languageStart).trim(), '')
      break
    }

    const language = props.content.slice(languageStart, firstLineBreak).trim()
    const codeStart = firstLineBreak + 1
    const fenceEnd = props.content.indexOf('```', codeStart)
    if (fenceEnd < 0) {
      appendCodeBlock(result, language, props.content.slice(codeStart))
      break
    }

    appendCodeBlock(result, language, props.content.slice(codeStart, fenceEnd))
    cursor = fenceEnd + 3
  }
  return result
})

const copyCode = async (content: string) => {
  await navigator.clipboard.writeText(content)
  message.success('代码已复制')
}
</script>

<style scoped>
.chat-message-content {
  display: grid;
  gap: 10px;
  font-family:
    -apple-system, BlinkMacSystemFont, 'Segoe UI', 'Microsoft YaHei', 'PingFang SC', Arial,
    sans-serif;
}

.chat-message-content p {
  margin: 0;
  white-space: pre-wrap;
}

.text-block {
  color: #3f4654;
  font-size: 14px;
  line-height: 1.85;
}

.code-block {
  overflow: hidden;
  color: #313846;
  border: 1px solid #e4e7ec;
  border-radius: 10px;
  background: #f7f8fb;
  box-shadow: inset 0 1px 0 rgb(255 255 255 / 78%);
}

.code-heading {
  display: flex;
  align-items: center;
  justify-content: space-between;
  height: 34px;
  padding: 0 8px 0 12px;
  color: #667085;
  font-size: 11px;
  border-bottom: 1px solid #eaecf0;
  background: #fff;
}

.code-language {
  display: inline-flex;
  align-items: center;
  height: 20px;
  padding: 0 7px;
  color: #526070;
  font-size: 10px;
  font-weight: 600;
  line-height: 20px;
  border: 1px solid #e8ebf1;
  border-radius: 999px;
  background: #f8fafc;
}

.code-heading :deep(.ant-btn) {
  display: inline-grid;
  width: 26px;
  min-width: 26px;
  height: 26px;
  padding: 0;
  color: #667085;
  border-radius: 7px;
  place-items: center;
}

.code-heading :deep(.ant-btn:hover) {
  color: #4f50d8;
  background: #f0f2ff;
}

pre {
  max-height: 430px;
  margin: 0;
  padding: 15px 18px;
  overflow: auto;
  color: #334155;
  font-family:
    'JetBrains Mono', 'Fira Code', 'SFMono-Regular', Consolas, 'Liberation Mono', Menlo, monospace;
  font-size: 13px;
  line-height: 1.7;
  white-space: pre;
  background: #fbfcfe;
  tab-size: 2;
}

code {
  font: inherit;
}

code :deep(.tok-comment) {
  color: #8a94a6;
  font-style: italic;
}

code :deep(.tok-keyword) {
  color: #7c3aed;
  font-weight: 600;
}

code :deep(.tok-string) {
  color: #27845f;
}

code :deep(.tok-number) {
  color: #c2410c;
}

code :deep(.tok-tag) {
  color: #0ea5e9;
  font-weight: 600;
}

code :deep(.tok-attr) {
  color: #2563eb;
}

code :deep(.tok-function) {
  color: #0284c7;
}

code :deep(.tok-type) {
  color: #9333ea;
}

code :deep(.tok-selector) {
  color: #0891b2;
}

code :deep(.tok-directive) {
  color: #0891b2;
  font-weight: 500;
}

code :deep(.tok-interpolation) {
  color: #d97706;
  font-weight: 600;
}

pre::-webkit-scrollbar {
  width: 10px;
  height: 10px;
}

pre::-webkit-scrollbar-thumb {
  border: 3px solid #fbfcfe;
  border-radius: 999px;
  background: #9aa3b2;
}

pre::-webkit-scrollbar-track {
  background: #f1f3f7;
}

/* 默认状态块：紫色（选择工具） */
.status-block {
  display: flex;
  align-items: center;
  min-height: 30px;
  padding: 7px 11px;
  color: #526070;
  font-size: 13px;
  line-height: 1.5;
  border: 1px solid #dfe4ff;
  border-radius: 8px;
  background: #f6f7ff;
}

.status-block::before {
  width: 6px;
  height: 6px;
  margin-right: 8px;
  border-radius: 50%;
  background: #6f63f6;
  box-shadow: 0 0 0 3px rgb(111 99 246 / 12%);
  content: '';
}

/* [选择工具] — 蓝色：AI 正在选择将要使用的工具 */
.status-selecting {
  color: #3b5998;
  border-color: #d6e4ff;
  background: #f0f5ff;
}

.status-selecting::before {
  background: #3b82f6;
  box-shadow: 0 0 0 3px rgb(59 130 246 / 14%);
}

/* [正在编写] — 琥珀色：AI 正在将代码写入文件 */
.status-writing {
  color: #92400e;
  border-color: #fde68a;
  background: #fffbeb;
}

.status-writing::before {
  background: #f59e0b;
  box-shadow: 0 0 0 3px rgb(245 158 11 / 14%);
  animation: status-pulse 1.6s ease-in-out infinite;
}

/* [工具调用] — 翠绿色：工具已执行完成 */
.status-executed {
  color: #065f46;
  border-color: #a7f3d0;
  background: #ecfdf5;
}

.status-executed::before {
  background: #10b981;
  box-shadow: 0 0 0 3px rgb(16 185 129 / 14%);
}

@keyframes status-pulse {
  0%,
  100% {
    box-shadow: 0 0 0 3px rgb(245 158 11 / 14%);
  }
  50% {
    box-shadow: 0 0 0 6px rgb(245 158 11 / 6%);
  }
}

.summary-block {
  padding: 12px 14px;
  color: #344054;
  border: 1px solid #e7eaf0;
  border-radius: 10px;
  background: #fff;
}

.summary-block strong {
  display: block;
  margin-bottom: 8px;
  color: #303846;
  font-size: 14px;
}

.summary-block ol {
  margin: 0;
  padding-left: 20px;
}

.summary-block li {
  padding-left: 2px;
  font-size: 14px;
  line-height: 1.85;
}

.summary-block p {
  color: #344054;
  font-size: 14px;
  line-height: 1.8;
}
</style>
