<template>
  <div class="chat-message-content">
    <template v-for="(block, index) in blocks" :key="index">
      <div v-if="block.type === 'code'" class="code-block">
        <div class="code-heading">
          <span>{{ block.language || 'code' }}</span>
          <a-button type="text" size="small" @click="copyCode(block.content)">
            <CopyOutlined />
            复制
          </a-button>
        </div>
        <pre><code>{{ block.content }}</code></pre>
      </div>
      <p v-else>{{ block.content }}</p>
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
  type: 'text' | 'code'
  content: string
  language?: string
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

const looksLikeCodeLine = (line: string) => {
  const text = line.trim()
  return (
    text.startsWith('<') ||
    text.startsWith('{') ||
    text.startsWith('[') ||
    text.startsWith('.') ||
    text.startsWith('#') ||
    text.startsWith('@') ||
    /^(import|export|const|let|var|function|class|interface|type|async|await|return)\b/.test(text)
  )
}

const isGenerationStatusLine = (line: string) => {
  return /^\[(正在编写|工具调用|选择工具)\]/.test(line.trim())
}

const isOnlyGenerationStatusContent = (content: string) => {
  const lines = content
    .replace(/\r\n/g, '\n')
    .split('\n')
    .map((line) => line.trim())
    .filter(Boolean)
  return lines.length > 0 && lines.every(isGenerationStatusLine)
}

const appendCodeBlock = (result: MessageBlock[], language: string, content: string) => {
  const normalizedContent = content.trimEnd()
  if (isOnlyGenerationStatusContent(normalizedContent)) {
    result.push({ type: 'text', content: normalizedContent })
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
        result.push({ type: 'text', content: text })
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
    result.push({ type: 'text', content: text })
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
.chat-message-content p {
  margin: 0;
  white-space: pre-wrap;
}

.chat-message-content p + p,
.chat-message-content p + .code-block,
.code-block + p {
  margin-top: 10px;
}

.code-block {
  overflow: hidden;
  color: #d0d5dd;
  border: 1px solid #303746;
  border-radius: 8px;
  background: #101828;
}

.code-heading {
  display: flex;
  align-items: center;
  justify-content: space-between;
  height: 30px;
  padding: 0 8px 0 11px;
  color: #98a2b3;
  font-size: 9px;
  border-bottom: 1px solid #303746;
  background: #1d2939;
}

.code-heading :deep(.ant-btn) {
  height: 24px;
  color: #b5bdc9;
  font-size: 9px;
}

pre {
  max-height: 360px;
  margin: 0;
  padding: 12px;
  overflow: auto;
  font-size: 10px;
  line-height: 1.65;
  white-space: pre;
}
</style>
