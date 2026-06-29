<template>
  <article class="app-card" :class="{ featured }" @click="handleCardClick">
    <div class="cover-wrap">
      <img :src="coverImageSrc" :alt="app.appName || '应用封面'" class="cover" @error="handleCoverError" />

      <div class="cover-topbar">
        <span class="type-tag">{{ formatCodeGenType(app.codeGenType) }}</span>
        <span v-if="featured" class="featured-tag">
          <StarFilled />
          精选
        </span>
      </div>

      <div class="view-mask">
        <div class="quick-actions" :class="{ undeployed: !app.deployKey }" @click.stop>
          <span v-if="!app.deployKey" class="undeployed-hint">
            项目未部署，请前往对话界面预览应用
          </span>
          <a-button v-if="app.deployKey" type="primary" @click="$emit('work', app)">
            <ExportOutlined />
            查看作品
          </a-button>
          <a-button :type="app.deployKey ? 'default' : 'primary'" @click="$emit('chat', app)">
            <MessageOutlined />
            {{ app.deployKey ? '查看对话' : '前往对话' }}
          </a-button>
        </div>
      </div>
    </div>

    <div class="info">
      <div class="title-row">
        <h3>{{ app.appName || '未命名应用' }}</h3>
        <a-dropdown trigger="click" placement="bottomRight" @click.stop>
          <a-button type="text" shape="circle" class="more-button">
            <MoreOutlined />
          </a-button>
          <template #overlay>
            <a-menu class="card-menu" @click.stop>
              <a-menu-item key="status" disabled>
                <span class="deploy-status" :class="{ deployed: app.deployKey }">
                  <i></i>
                  {{ app.deployKey ? '已部署' : '尚未部署' }}
                </span>
              </a-menu-item>
              <a-menu-item key="id" disabled>
                <span class="menu-meta">
                  <small>应用 ID</small>
                  <strong>{{ app.id || '--' }}</strong>
                </span>
              </a-menu-item>
              <a-menu-item v-if="app.deployKey" key="deployedTime" disabled>
                <span class="menu-meta">
                  <small>部署时间</small>
                  <strong>{{ formatDateTime(app.deployedTime) }}</strong>
                </span>
              </a-menu-item>
              <a-menu-divider />
              <a-menu-item v-if="app.deployKey" key="work" @click="$emit('work', app)">
                <ExportOutlined />
                查看作品
              </a-menu-item>
              <a-menu-item key="chat" @click="$emit('chat', app)">
                <MessageOutlined />
                {{ app.deployKey ? '查看对话' : '前往对话' }}
              </a-menu-item>
              <a-menu-item v-if="editable" key="edit" @click="$emit('edit', app)">
                <EditOutlined />
                编辑信息
              </a-menu-item>
              <a-menu-divider v-if="editable" />
              <a-menu-item v-if="editable" key="delete" danger @click="$emit('delete', app)">
                <DeleteOutlined />
                删除应用
              </a-menu-item>
            </a-menu>
          </template>
        </a-dropdown>
      </div>
      <p>{{ app.initPrompt || '由 AI 生成的网站应用' }}</p>
      <div class="meta">
        <a-space :size="7">
          <a-avatar :size="24" :src="app.user?.userAvatar || defaultAvatar" />
          <span>{{ app.user?.userName || app.user?.userAccount || ownerLabel }}</span>
        </a-space>
        <span class="chat-count">
          <MessageOutlined />
          {{ app.chatCount || 0 }} 轮
        </span>
        <span class="download-count">
          <DownloadOutlined />
          {{ app.downloadCount || 0 }} 次
        </span>
        <span>{{ formatTime(app.createTime) }}</span>
      </div>
    </div>
  </article>
</template>

<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import {
  DeleteOutlined,
  DownloadOutlined,
  EditOutlined,
  ExportOutlined,
  MessageOutlined,
  MoreOutlined,
  StarFilled,
} from '@ant-design/icons-vue'
import defaultAvatar from '@/assets/default-avatar.png'
import defaultCover from '@/assets/default-cover.svg'
import { formatCodeGenType } from '@/utils/codeGenType'

const props = withDefaults(
  defineProps<{
    app: API.AppVO
    editable?: boolean
    featured?: boolean
    ownerLabel?: string
  }>(),
  {
    editable: false,
    featured: false,
    ownerLabel: '我的应用',
  },
)

const coverLoadFailed = ref(false)

const coverImageSrc = computed(() => {
  if (props.app.cover && !coverLoadFailed.value) {
    return props.app.cover
  }
  return defaultCover
})

watch(
  () => props.app.cover,
  () => {
    coverLoadFailed.value = false
  },
)

const emit = defineEmits<{
  work: [app: API.AppVO]
  chat: [app: API.AppVO]
  edit: [app: API.AppVO]
  delete: [app: API.AppVO]
}>()

const handleCardClick = () => {
  if (props.app.deployKey) {
    emit('work', props.app)
    return
  }
  emit('chat', props.app)
}

const handleCoverError = () => {
  coverLoadFailed.value = true
}

const formatTime = (time?: string) => {
  if (!time) {
    return '刚刚创建'
  }
  return new Date(time).toLocaleDateString('zh-CN')
}

const formatDateTime = (time?: string) => {
  if (!time) {
    return '已部署'
  }
  return new Date(time).toLocaleString('zh-CN', {
    month: '2-digit',
    day: '2-digit',
    hour: '2-digit',
    minute: '2-digit',
  })
}
</script>

<style scoped>
.app-card {
  overflow: hidden;
  border: 1px solid #eaecf0;
  border-radius: 16px;
  background: #fff;
  box-shadow: 0 2px 4px rgb(16 24 40 / 2%);
  cursor: pointer;
  transition:
    transform 0.25s ease,
    border-color 0.25s ease,
    box-shadow 0.25s ease;
}

.app-card:hover {
  transform: translateY(-4px);
  border-color: #dcdcff;
  box-shadow: 0 18px 42px rgb(16 24 40 / 10%);
}

.cover-wrap {
  position: relative;
  height: 190px;
  overflow: hidden;
  background: #f1f3f8;
}

.cover {
  width: 100%;
  height: 100%;
  object-fit: cover;
  transition: transform 0.35s ease;
}

.app-card:hover .cover {
  transform: scale(1.025);
}

.cover-topbar {
  position: absolute;
  top: 12px;
  left: 12px;
  display: flex;
  align-items: center;
  gap: 6px;
}

.type-tag,
.featured-tag {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  height: 24px;
  padding: 0 8px;
  font-size: 10px;
  font-weight: 600;
  border-radius: 6px;
  backdrop-filter: blur(12px);
}

.type-tag {
  color: #475467;
  background: rgb(255 255 255 / 82%);
}

.featured-tag {
  color: #a15c07;
  background: rgb(255 246 214 / 92%);
}

.view-mask {
  position: absolute;
  inset: 0;
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 18px;
  pointer-events: none;
  background: rgb(16 24 40 / 34%);
  opacity: 0;
  transition: opacity 0.25s ease;
}

.quick-actions {
  display: flex;
  align-items: center;
  gap: 8px;
  pointer-events: auto;
  transform: translateY(8px);
  transition: transform 0.25s ease;
}

.quick-actions.undeployed {
  flex-direction: column;
}

.undeployed-hint {
  max-width: 230px;
  color: #fff;
  font-size: 11px;
  line-height: 1.6;
  text-align: center;
  text-shadow: 0 1px 3px rgb(16 24 40 / 45%);
}

.quick-actions :deep(.ant-btn) {
  height: 34px;
  border: 0;
  border-radius: 8px;
  box-shadow: 0 8px 20px rgb(16 24 40 / 18%);
}

.app-card:hover .view-mask {
  opacity: 1;
}

.app-card:hover .quick-actions {
  transform: translateY(0);
}

.info {
  padding: 16px;
}

.title-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 10px;
}

h3 {
  margin: 0;
  overflow: hidden;
  color: #182230;
  font-size: 15px;
  font-weight: 650;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.more-button {
  flex-shrink: 0;
  color: #98a2b3;
}

p {
  display: -webkit-box;
  height: 38px;
  margin: 7px 0 15px;
  overflow: hidden;
  color: #98a2b3;
  font-size: 12px;
  line-height: 19px;
  -webkit-box-orient: vertical;
  -webkit-line-clamp: 2;
}

.meta {
  display: flex;
  align-items: center;
  justify-content: space-between;
  flex-wrap: wrap;
  gap: 8px;
  padding-top: 12px;
  color: #98a2b3;
  font-size: 11px;
  border-top: 1px solid #f0f1f4;
}

.chat-count,
.download-count {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  color: #667085;
}

.deploy-status {
  display: inline-flex;
  align-items: center;
  gap: 7px;
  color: #98a2b3;
}

.deploy-status i {
  width: 7px;
  height: 7px;
  border-radius: 50%;
  background: #d0d5dd;
}

.deploy-status.deployed {
  color: #039855;
}

.deploy-status.deployed i {
  background: #12b76a;
}

.menu-meta {
  display: grid;
  gap: 2px;
  min-width: 190px;
}

.menu-meta small {
  color: #98a2b3;
  font-size: 10px;
}

.menu-meta strong {
  overflow: hidden;
  color: #667085;
  font-size: 11px;
  font-weight: 500;
  text-overflow: ellipsis;
}

@media (hover: none) {
  .view-mask {
    align-items: flex-end;
    background: linear-gradient(to top, rgb(16 24 40 / 32%), transparent 60%);
    opacity: 1;
  }

  .quick-actions {
    transform: none;
  }
}
</style>
