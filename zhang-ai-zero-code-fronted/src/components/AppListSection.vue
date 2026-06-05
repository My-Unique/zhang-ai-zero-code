<template>
  <section class="list-section">
    <div class="section-header">
      <div class="section-title">
        <span class="title-icon" :class="{ featured: variant === 'featured' }">
          <StarOutlined v-if="variant === 'featured'" />
          <AppstoreOutlined v-else />
        </span>
        <div>
          <h3>{{ title }}</h3>
          <p>{{ subtitle }}</p>
        </div>
      </div>

      <a-input
        v-model:value="keyword"
        allow-clear
        placeholder="搜索应用"
        class="search-input"
        @press-enter="$emit('search', keyword.trim())"
        @change="handleSearchChange"
      >
        <template #prefix>
          <SearchOutlined />
        </template>
      </a-input>
    </div>

    <a-spin :spinning="loading">
      <div v-if="apps.length" class="app-grid">
        <AppCard
          v-for="app in apps"
          :key="app.id"
          :app="app"
          :editable="editable"
          :featured="variant === 'featured'"
          @work="$emit('work', $event)"
          @chat="$emit('chat', $event)"
          @edit="$emit('edit', $event)"
          @delete="$emit('delete', $event)"
        />
      </div>
      <a-empty v-else class="empty-state" description="暂无应用，开始创建你的第一个作品吧" />
    </a-spin>

    <div v-if="total > 6" class="pagination-wrap">
      <span>共 {{ total }} 个应用</span>
      <a-pagination
        :current="pageNum"
        :page-size="6"
        :total="total"
        :show-size-changer="false"
        size="small"
        @change="$emit('pageChange', $event)"
      />
    </div>
  </section>
</template>

<script setup lang="ts">
import { ref } from 'vue'
import { AppstoreOutlined, SearchOutlined, StarOutlined } from '@ant-design/icons-vue'
import AppCard from '@/components/AppCard.vue'

withDefaults(
  defineProps<{
    title: string
    subtitle: string
    apps: API.AppVO[]
    loading: boolean
    total: number
    pageNum: number
    editable?: boolean
    variant?: 'default' | 'featured'
  }>(),
  {
    editable: false,
    variant: 'default',
  },
)

const emit = defineEmits<{
  search: [keyword: string]
  pageChange: [page: number]
  work: [app: API.AppVO]
  chat: [app: API.AppVO]
  edit: [app: API.AppVO]
  delete: [app: API.AppVO]
}>()

const keyword = ref('')

const handleSearchChange = () => {
  if (!keyword.value) {
    emit('search', '')
  }
}
</script>

<style scoped>
.list-section {
  padding: 26px;
  border: 1px solid #eaecf0;
  border-radius: 20px;
  background: #fff;
  box-shadow: var(--shadow-card);
}

.list-section + .list-section {
  margin-top: 28px;
}

.section-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 20px;
  margin-bottom: 22px;
}

.section-title {
  display: flex;
  align-items: center;
  gap: 11px;
}

.title-icon {
  display: grid;
  flex-shrink: 0;
  width: 38px;
  height: 38px;
  color: #5b5cf0;
  border-radius: 11px;
  background: #eeeeff;
  place-items: center;
}

.title-icon.featured {
  color: #c57918;
  background: #fff6df;
}

h3 {
  margin: 0;
  color: #182230;
  font-size: 17px;
  font-weight: 650;
}

p {
  margin: 3px 0 0;
  color: #98a2b3;
  font-size: 11px;
}

.search-input {
  width: 220px;
  border-color: #eaecf0;
  border-radius: 9px;
  background: #fafbfc;
}

.search-input :deep(.anticon) {
  color: #98a2b3;
}

.app-grid {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 20px;
}

.empty-state {
  padding: 42px 0;
}

.pagination-wrap {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-top: 24px;
  padding-top: 20px;
  color: #98a2b3;
  font-size: 11px;
  border-top: 1px solid #f0f1f4;
}

@media (max-width: 960px) {
  .app-grid {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
}

@media (max-width: 640px) {
  .list-section {
    padding: 18px;
  }

  .section-header {
    align-items: stretch;
    flex-direction: column;
  }

  .search-input {
    width: 100%;
  }

  .app-grid {
    grid-template-columns: 1fr;
  }
}
</style>
