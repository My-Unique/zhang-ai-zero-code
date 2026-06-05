<template>
  <main class="admin-page">
    <header class="page-header">
      <div>
        <span class="page-eyebrow">ADMIN CONSOLE</span>
        <h1>对话管理</h1>
        <p>查看、筛选平台内的应用对话历史，定位用户与 AI 的交互记录。</p>
      </div>
      <div class="header-stat">
        <span>消息总数</span>
        <strong>{{ total }}</strong>
      </div>
    </header>

    <section class="filter-panel">
      <div class="filter-title">
        <FilterOutlined />
        <span>筛选条件</span>
      </div>
      <a-form layout="inline" :model="query" class="filter-form" @finish="search">
        <a-form-item label="消息内容">
          <a-input v-model:value="query.message" allow-clear placeholder="输入消息关键词" />
        </a-form-item>
        <a-form-item label="消息类型">
          <a-select
            v-model:value="query.messageType"
            allow-clear
            placeholder="全部类型"
            :options="messageTypeOptions"
          />
        </a-form-item>
        <a-form-item label="应用 ID">
          <a-input v-model:value="query.appId" allow-clear placeholder="输入应用 ID" />
        </a-form-item>
        <a-form-item label="用户 ID">
          <a-input-number v-model:value="query.userId" placeholder="输入用户 ID" />
        </a-form-item>
        <a-form-item class="filter-actions">
          <a-button @click="resetQuery">重置</a-button>
          <a-button type="primary" html-type="submit">
            <SearchOutlined />
            查询
          </a-button>
        </a-form-item>
      </a-form>
    </section>

    <section class="table-panel">
      <div class="table-heading">
        <div>
          <h2>对话列表</h2>
          <p>当前筛选结果共 {{ total }} 条</p>
        </div>
        <a-button @click="fetchChatHistories">
          <ReloadOutlined />
          刷新
        </a-button>
      </div>

      <a-table
        row-key="id"
        :columns="columns"
        :data-source="chatHistories"
        :loading="loading"
        :pagination="pagination"
        :scroll="{ x: 1100 }"
        @change="tableChange"
      >
        <template #bodyCell="{ column, record }">
          <template v-if="column.dataIndex === 'message'">
            <a-tooltip :title="record.message">
              <div class="message-text">{{ record.message || '-' }}</div>
            </a-tooltip>
          </template>
          <template v-else-if="column.dataIndex === 'messageType'">
            <a-tag :color="record.messageType === 'user' ? 'blue' : 'green'">
              {{ record.messageType === 'user' ? '用户消息' : 'AI 消息' }}
            </a-tag>
          </template>
          <template v-else-if="column.dataIndex === 'createTime'">
            {{ formatTime(record.createTime) }}
          </template>
          <template v-else-if="column.key === 'action'">
            <a-space :size="2">
              <a-tooltip title="查看应用对话">
                <a-button type="text" :disabled="!record.appId" @click="viewAppChat(record.appId)">
                  <EyeOutlined />
                </a-button>
              </a-tooltip>
            </a-space>
          </template>
        </template>
      </a-table>
    </section>
  </main>
</template>

<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import {
  EyeOutlined,
  FilterOutlined,
  ReloadOutlined,
  SearchOutlined,
} from '@ant-design/icons-vue'
import { message } from 'ant-design-vue'
import { listAllChatHistoryByPageForAdmin } from '@/api/chatHistoryController'

const router = useRouter()
const chatHistories = ref<API.ChatHistory[]>([])
const total = ref(0)
const loading = ref(false)
const query = reactive<API.ChatHistoryQueryRequest>({
  pageNum: 1,
  pageSize: 10,
  sortField: 'createTime',
  sortOrder: 'descend',
})

const messageTypeOptions = [
  { label: '用户消息', value: 'user' },
  { label: 'AI 消息', value: 'assistant' },
]

const columns = [
  { title: 'ID', dataIndex: 'id', width: 180, fixed: 'left' },
  { title: '消息内容', dataIndex: 'message', width: 360 },
  { title: '消息类型', dataIndex: 'messageType', width: 110 },
  { title: '应用 ID', dataIndex: 'appId', width: 180 },
  { title: '用户 ID', dataIndex: 'userId', width: 110 },
  { title: '创建时间', dataIndex: 'createTime', width: 190 },
  { title: '操作', key: 'action', width: 100, fixed: 'right' },
]

const pagination = computed(() => ({
  current: query.pageNum,
  pageSize: query.pageSize,
  total: total.value,
  showSizeChanger: true,
  showTotal: (value: number) => `共 ${value} 条`,
}))

const formatTime = (time?: string) => {
  return time ? new Date(time).toLocaleString('zh-CN') : '-'
}

const fetchChatHistories = async () => {
  loading.value = true
  try {
    const res = await listAllChatHistoryByPageForAdmin({ ...query })
    if (res.data.code === 0 && res.data.data) {
      chatHistories.value = res.data.data.records ?? []
      total.value = res.data.data.totalRow ?? 0
      return
    }
    message.error(`获取对话历史失败：${res.data.message || '请稍后重试'}`)
  } catch (error) {
    console.error('获取对话历史失败：', error)
    message.error('获取对话历史失败')
  } finally {
    loading.value = false
  }
}

const search = () => {
  query.pageNum = 1
  fetchChatHistories()
}

const resetQuery = () => {
  Object.assign(query, {
    pageNum: 1,
    pageSize: 10,
    sortField: 'createTime',
    sortOrder: 'descend',
    message: undefined,
    messageType: undefined,
    appId: undefined,
    userId: undefined,
  })
  fetchChatHistories()
}

const tableChange = (page: { current?: number; pageSize?: number }) => {
  query.pageNum = page.current || 1
  query.pageSize = page.pageSize || 10
  fetchChatHistories()
}

const viewAppChat = (appId?: string) => {
  if (!appId) {
    return
  }
  router.push(`/app/chat/${appId}`)
}

onMounted(fetchChatHistories)
</script>

<style scoped>
.admin-page {
  width: min(var(--page-width), calc(100% - 48px));
  margin: 0 auto;
  padding: 46px 0 80px;
}

.page-header {
  display: flex;
  align-items: flex-end;
  justify-content: space-between;
  gap: 24px;
  margin-bottom: 28px;
}

.page-eyebrow {
  color: #5b5cf0;
  font-size: 10px;
  font-weight: 700;
  letter-spacing: 1.8px;
}

h1 {
  margin: 7px 0 5px;
  color: #101828;
  font-size: 30px;
  letter-spacing: -1px;
}

.page-header p,
.table-heading p {
  margin: 0;
  color: #98a2b3;
  font-size: 12px;
}

.header-stat {
  min-width: 116px;
  padding: 12px 16px;
  border: 1px solid #eaecf0;
  border-radius: 12px;
  background: #fff;
  box-shadow: var(--shadow-card);
}

.header-stat span,
.header-stat strong {
  display: block;
}

.header-stat span {
  color: #98a2b3;
  font-size: 10px;
}

.header-stat strong {
  margin-top: 2px;
  color: #182230;
  font-size: 22px;
}

.filter-panel,
.table-panel {
  border: 1px solid #eaecf0;
  border-radius: 16px;
  background: #fff;
  box-shadow: var(--shadow-card);
}

.filter-panel {
  padding: 20px;
}

.filter-title {
  display: flex;
  align-items: center;
  gap: 7px;
  margin-bottom: 16px;
  color: #344054;
  font-size: 13px;
  font-weight: 600;
}

.filter-form {
  row-gap: 10px;
}

.filter-form :deep(.ant-form-item) {
  margin-right: 12px;
  margin-bottom: 0;
}

.filter-actions {
  margin-left: auto;
}

.table-panel {
  margin-top: 20px;
  overflow: hidden;
}

.table-heading {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 20px 22px;
  border-bottom: 1px solid #f0f1f4;
}

.table-heading h2 {
  margin: 0 0 3px;
  color: #182230;
  font-size: 16px;
}

.table-panel :deep(.ant-table-thead > tr > th) {
  color: #667085;
  font-size: 11px;
  font-weight: 600;
  background: #fafbfc;
}

.table-panel :deep(.ant-table-tbody > tr > td) {
  color: #475467;
  font-size: 12px;
  vertical-align: middle;
}

.message-text {
  max-width: 340px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

@media (max-width: 720px) {
  .admin-page {
    width: calc(100% - 28px);
    padding-top: 30px;
  }

  .page-header {
    align-items: stretch;
    flex-direction: column;
  }

  .header-stat {
    align-self: flex-start;
  }
}
</style>
