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

const blocks = computed<MessageBlock[]>(() => {
  const result: MessageBlock[] = []
  const pattern = /```([\w-]*)\n?([\s\S]*?)```/g
  let cursor = 0
  let match: RegExpExecArray | null

  while ((match = pattern.exec(props.content))) {
    const text = props.content.slice(cursor, match.index).trim()
    if (text) {
      result.push({ type: 'text', content: text })
    }
    result.push({
      type: 'code',
      language: match[1],
      content: match[2].trimEnd(),
    })
    cursor = pattern.lastIndex
  }

  const remaining = props.content.slice(cursor).trim()
  if (remaining) {
    result.push({ type: 'text', content: remaining })
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
