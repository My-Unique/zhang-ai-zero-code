<template>
  <main class="admin-page">
    <header class="page-header">
      <div>
        <span class="page-eyebrow">ADMIN CONSOLE</span>
        <h1>应用管理</h1>
        <p>查看、筛选和管理平台中的全部 AI 应用。</p>
      </div>
      <div class="header-stat">
        <span>应用总数</span>
        <strong>{{ total }}</strong>
      </div>
    </header>

    <section class="filter-panel">
      <div class="filter-title">
        <FilterOutlined />
        <span>筛选条件</span>
      </div>
      <a-form layout="vertical" :model="query" class="filter-form" @finish="search">
        <a-form-item label="应用 ID">
          <a-input v-model:value="query.id" allow-clear placeholder="输入 ID" />
        </a-form-item>
        <a-form-item label="应用名称" class="filter-item-wide">
          <a-input v-model:value="query.appName" allow-clear placeholder="输入应用名称" />
        </a-form-item>
        <a-form-item label="用户 ID">
          <a-input-number v-model:value="query.userId" placeholder="输入用户 ID" />
        </a-form-item>
        <a-form-item label="生成类型">
          <a-select
            v-model:value="query.codeGenType"
            allow-clear
            placeholder="全部类型"
            :options="codeGenTypeOptions"
          />
        </a-form-item>
        <a-form-item label="优先级">
          <a-input-number v-model:value="query.priority" :min="0" placeholder="输入优先级" />
        </a-form-item>
        <a-form-item label="部署标识" class="filter-item-wide">
          <a-input v-model:value="query.deployKey" allow-clear placeholder="输入部署标识" />
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
          <h2>应用列表</h2>
          <p>当前筛选结果共 {{ total }} 条</p>
        </div>
        <a-button @click="fetchApps">
          <ReloadOutlined />
          刷新
        </a-button>
      </div>

      <a-table
        row-key="id"
        :columns="columns"
        :data-source="apps"
        :loading="loading"
        :pagination="pagination"
        :scroll="{ x: 1050 }"
        @change="tableChange"
      >
        <template #bodyCell="{ column, record }">
          <template v-if="column.dataIndex === 'cover'">
            <div class="app-identity">
              <a-image
                v-if="record.cover"
                :src="record.cover"
                :width="54"
                :height="38"
                class="cover"
              />
              <div v-else class="cover-placeholder">
                <AppstoreOutlined />
              </div>
              <div>
                <strong>{{ record.appName || '未命名应用' }}</strong>
                <span>ID: {{ record.id }}</span>
              </div>
            </div>
          </template>
          <template v-else-if="column.dataIndex === 'priority'">
            <a-tag :color="record.priority === 99 ? 'gold' : 'default'">
              {{ record.priority === 99 ? '精选' : (record.priority ?? 0) }}
            </a-tag>
          </template>
          <template v-else-if="column.dataIndex === 'chatCount'">
            <a-tag color="blue">{{ record.chatCount || 0 }} 轮</a-tag>
          </template>
          <template v-else-if="column.dataIndex === 'createTime'">
            {{ formatTime(record.createTime) }}
          </template>
          <template v-else-if="column.key === 'action'">
            <a-space :size="2">
              <a-tooltip title="查看应用">
                <a-button type="text" @click="router.push(`/app/chat/${record.id}`)">
                  <EyeOutlined />
                </a-button>
              </a-tooltip>
              <a-tooltip title="查看作品">
                <a-button type="text" :disabled="!record.versionNo" @click="viewWork(record)">
                  <ExportOutlined />
                </a-button>
              </a-tooltip>
              <a-tooltip title="编辑应用">
                <a-button type="text" @click="router.push(`/app/edit/${record.id}`)">
                  <EditOutlined />
                </a-button>
              </a-tooltip>
              <a-tooltip title="设为精选">
                <a-button type="text" @click="feature(record)">
                  <StarOutlined />
                </a-button>
              </a-tooltip>
              <a-popconfirm title="确定删除该应用吗？" @confirm="remove(record.id)">
                <a-tooltip title="删除应用">
                  <a-button type="text" danger>
                    <DeleteOutlined />
                  </a-button>
                </a-tooltip>
              </a-popconfirm>
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
  AppstoreOutlined,
  DeleteOutlined,
  EditOutlined,
  EyeOutlined,
  ExportOutlined,
  FilterOutlined,
  ReloadOutlined,
  SearchOutlined,
  StarOutlined,
} from '@ant-design/icons-vue'
import { message } from 'ant-design-vue'
import {
  deleteAppByAdmin,
  listAppVoByPageByAdmin,
  previewApp,
  updateAppByAdmin,
} from '@/api/appController'

const router = useRouter()
const apps = ref<API.AppVO[]>([])
const total = ref(0)
const loading = ref(false)
const query = reactive<API.AppQueryRequest>({
  pageNum: 1,
  pageSize: 10,
  sortField: 'createTime',
  sortOrder: 'descend',
})
const codeGenTypeOptions = [
  { label: '原生 HTML', value: 'html' },
  { label: '原生多文件', value: 'multi_file' },
  { label: 'Vue 工程', value: 'vue_project' },
]

const columns = [
  { title: '应用', dataIndex: 'cover', width: 250 },
  { title: '生成类型', dataIndex: 'codeGenType', width: 120 },
  { title: '用户 ID', dataIndex: 'userId', width: 110 },
  { title: '对话轮次', dataIndex: 'chatCount', width: 110 },
  { title: '优先级', dataIndex: 'priority', width: 100 },
  { title: '部署标识', dataIndex: 'deployKey', width: 180, ellipsis: true },
  { title: '创建时间', dataIndex: 'createTime', width: 190 },
  { title: '操作', key: 'action', width: 210, fixed: 'right' },
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

const fetchApps = async () => {
  loading.value = true
  try {
    const res = await listAppVoByPageByAdmin({ ...query })
    apps.value = res.data.data?.records ?? []
    total.value = res.data.data?.totalRow ?? 0
  } finally {
    loading.value = false
  }
}

const search = () => {
  query.pageNum = 1
  fetchApps()
}

const resetQuery = () => {
  Object.assign(query, {
    pageNum: 1,
    pageSize: 10,
    sortField: 'createTime',
    sortOrder: 'descend',
    id: undefined,
    appName: undefined,
    userId: undefined,
    codeGenType: undefined,
    priority: undefined,
    deployKey: undefined,
  })
  fetchApps()
}

const tableChange = (page: { current?: number; pageSize?: number }) => {
  query.pageNum = page.current || 1
  query.pageSize = page.pageSize || 10
  fetchApps()
}

const feature = async (app: API.AppVO) => {
  const res = await updateAppByAdmin({ id: app.id, priority: 99, visibility: 'public' })
  if (res.data.code === 0) {
    message.success('已设为精选应用')
    fetchApps()
    return
  }
  message.error(`操作失败：${res.data.message || '请稍后重试'}`)
}

const viewWork = async (app: API.AppVO) => {
  if (!app.id) {
    return
  }
  const previewWindow = window.open('', '_blank')
  try {
    const previewUrl = app.deployKey
      ? `http://localhost:8123/api/static/${app.deployKey}/`
      : (await previewApp({ appId: app.id })).data.data
    if (!previewUrl) {
      previewWindow?.close()
      message.warning('该应用尚无可预览版本')
      return
    }
    if (previewWindow) {
      previewWindow.location.href = previewUrl
    }
  } catch {
    previewWindow?.close()
  }
}

const remove = async (id?: string) => {
  if (!id) {
    return
  }
  const res = await deleteAppByAdmin({ id })
  if (res.data.code === 0) {
    message.success('删除成功')
    fetchApps()
    return
  }
  message.error(`删除失败：${res.data.message || '请稍后重试'}`)
}

onMounted(fetchApps)
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
  display: grid;
  grid-template-columns: repeat(4, minmax(160px, 1fr));
  gap: 14px 18px;
  align-items: end;
}

.filter-form :deep(.ant-form-item) {
  margin: 0;
}

.filter-form :deep(.ant-form-item-label) {
  padding-bottom: 6px;
}

.filter-form :deep(.ant-form-item-label > label) {
  color: #344054;
  font-size: 12px;
  font-weight: 600;
}

.filter-form :deep(.ant-input),
.filter-form :deep(.ant-input-number),
.filter-form :deep(.ant-select) {
  width: 100%;
}

.filter-item-wide {
  grid-column: span 2;
}

.filter-actions {
  grid-column: 3 / 5;
}

.filter-actions :deep(.ant-form-item-control-input-content) {
  display: flex;
  justify-content: flex-end;
  gap: 10px;
}

.filter-actions :deep(.ant-btn) {
  min-width: 92px;
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
}

.app-identity {
  display: flex;
  align-items: center;
  gap: 10px;
}

.cover,
.cover-placeholder {
  overflow: hidden;
  border-radius: 7px;
  object-fit: cover;
}

.cover-placeholder {
  display: grid;
  width: 54px;
  height: 38px;
  color: #5b5cf0;
  background: #eeeeff;
  place-items: center;
}

.app-identity strong,
.app-identity span {
  display: block;
}

.app-identity strong {
  max-width: 150px;
  overflow: hidden;
  color: #344054;
  font-size: 12px;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.app-identity span {
  margin-top: 2px;
  color: #98a2b3;
  font-size: 10px;
}

@media (max-width: 720px) {
  .admin-page {
    width: calc(100% - 28px);
    padding-top: 30px;
  }

  .filter-form {
    grid-template-columns: 1fr;
  }

  .filter-item-wide,
  .filter-actions {
    grid-column: span 1;
  }

  .filter-actions :deep(.ant-form-item-control-input-content) {
    justify-content: stretch;
  }

  .filter-actions :deep(.ant-btn) {
    flex: 1;
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
