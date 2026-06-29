<template>
  <main class="admin-page">
    <header class="page-header">
      <div>
        <span class="page-eyebrow">ADMIN CONSOLE</span>
        <h1>用户管理</h1>
        <p>查看平台用户资料、角色与注册信息。</p>
      </div>
      <div class="header-stat">
        <span>用户总数</span>
        <strong>{{ total }}</strong>
      </div>
    </header>

    <section class="filter-panel">
      <div class="filter-title">
        <FilterOutlined />
        <span>筛选条件</span>
      </div>
      <a-form layout="inline" :model="searchParams" class="filter-form" @finish="doSearch">
        <a-form-item label="账号">
          <a-input v-model:value="searchParams.userAccount" allow-clear placeholder="输入账号" />
        </a-form-item>
        <a-form-item label="用户名">
          <a-input v-model:value="searchParams.userName" allow-clear placeholder="输入用户名" />
        </a-form-item>
        <a-form-item label="用户角色">
          <a-select
            v-model:value="searchParams.userRole"
            allow-clear
            placeholder="选择角色"
            style="width: 150px"
          >
            <a-select-option value="user">普通用户</a-select-option>
            <a-select-option value="admin">管理员</a-select-option>
          </a-select>
        </a-form-item>
        <a-form-item class="filter-actions">
          <a-button @click="resetSearch">重置</a-button>
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
          <h2>用户列表</h2>
          <p>当前筛选结果共 {{ total }} 条</p>
        </div>
        <a-button @click="fetchData">
          <ReloadOutlined />
          刷新
        </a-button>
      </div>

      <a-table
        row-key="id"
        :columns="columns"
        :data-source="data"
        :loading="loading"
        :pagination="pagination"
        :scroll="{ x: 900 }"
        @change="doTableChange"
      >
        <template #bodyCell="{ column, record }">
          <template v-if="column.dataIndex === 'userAvatar'">
            <div class="user-identity">
              <a-avatar :size="38" :src="record.userAvatar || defaultAvatar" />
              <div>
                <strong>{{ record.userName || record.userAccount || '未设置用户名' }}</strong>
                <span>{{ record.userAccount }}</span>
              </div>
            </div>
          </template>
          <template v-else-if="column.dataIndex === 'userRole'">
            <a-tag :color="record.userRole === 'admin' ? 'purple' : 'blue'">
              {{ record.userRole === 'admin' ? '管理员' : '普通用户' }}
            </a-tag>
          </template>
          <template v-else-if="column.dataIndex === 'createTime'">
            {{ formatTime(record.createTime) }}
          </template>
          <template v-else-if="column.key === 'action'">
            <a-popconfirm title="确定删除该用户吗？" @confirm="doDelete(record.id)">
              <a-button type="text" danger>
                <DeleteOutlined />
                删除
              </a-button>
            </a-popconfirm>
          </template>
        </template>
      </a-table>
    </section>
  </main>
</template>

<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import {
  DeleteOutlined,
  FilterOutlined,
  ReloadOutlined,
  SearchOutlined,
} from '@ant-design/icons-vue'
import { message } from 'ant-design-vue'
import { deleteUser, listUserVoByPage } from '@/api/userController'
import defaultAvatar from '@/assets/default-avatar.png'

const columns = [
  { title: '用户', dataIndex: 'userAvatar', width: 240 },
  { title: '用户 ID', dataIndex: 'id', width: 100 },
  { title: '个人简介', dataIndex: 'userProfile', ellipsis: true },
  { title: '用户角色', dataIndex: 'userRole', width: 130 },
  { title: '注册时间', dataIndex: 'createTime', width: 190 },
  { title: '操作', key: 'action', width: 110, fixed: 'right' },
]

const data = ref<API.UserVO[]>([])
const total = ref(0)
const loading = ref(false)
const searchParams = reactive<API.UserQueryRequest>({
  pageNum: 1,
  pageSize: 10,
})

const pagination = computed(() => ({
  current: searchParams.pageNum,
  pageSize: searchParams.pageSize,
  total: total.value,
  showSizeChanger: true,
  showTotal: (totalCount: number) => `共 ${totalCount} 条`,
}))

const formatTime = (time?: string) => {
  return time ? new Date(time).toLocaleString('zh-CN') : '-'
}

const fetchData = async () => {
  loading.value = true
  try {
    const res = await listUserVoByPage({ ...searchParams })
    if (res.data.data) {
      data.value = res.data.data.records ?? []
      total.value = res.data.data.totalRow ?? 0
      return
    }
    message.error(`获取用户失败：${res.data.message || '请稍后重试'}`)
  } finally {
    loading.value = false
  }
}

const doDelete = async (id?: number) => {
  if (!id) {
    return
  }
  const res = await deleteUser({ id })
  if (res.data.code === 0) {
    message.success('删除成功')
    fetchData()
    return
  }
  message.error(`删除失败：${res.data.message || '请稍后重试'}`)
}

const doTableChange = (page: { current?: number; pageSize?: number }) => {
  searchParams.pageNum = page.current ?? 1
  searchParams.pageSize = page.pageSize ?? 10
  fetchData()
}

const doSearch = () => {
  searchParams.pageNum = 1
  fetchData()
}

const resetSearch = () => {
  Object.assign(searchParams, {
    pageNum: 1,
    pageSize: 10,
    userAccount: undefined,
    userName: undefined,
    userRole: undefined,
  })
  fetchData()
}

onMounted(fetchData)
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
}

.user-identity {
  display: flex;
  align-items: center;
  gap: 10px;
}

.user-identity strong,
.user-identity span {
  display: block;
}

.user-identity strong {
  color: #344054;
  font-size: 12px;
}

.user-identity span {
  margin-top: 2px;
  color: #98a2b3;
  font-size: 10px;
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
