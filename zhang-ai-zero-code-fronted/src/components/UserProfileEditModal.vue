<template>
  <a-modal
    :open="open"
    :title="title"
    :confirm-loading="saving"
    width="460px"
    ok-text="保存资料"
    cancel-text="取消"
    @ok="submit"
    @cancel="close"
  >
    <div class="profile-editor">
      <div class="avatar-editor">
        <div class="avatar-preview">
          <img :src="avatarPreviewUrl || defaultAvatar" alt="用户头像预览" @error="handleAvatarPreviewError" />
        </div>
        <div>
          <p>支持 jpg、png、webp、gif，最大 2MB。</p>
          <input ref="fileInputRef" type="file" accept="image/*" hidden @change="handleFileChange" />
          <a-button size="small" :loading="uploading" @click="fileInputRef?.click()">
            <UploadOutlined />
            上传头像
          </a-button>
          <p class="field-hint">上传后会先在弹窗中预览，点击保存资料后才会生效。</p>
        </div>
      </div>

      <a-form layout="vertical" class="profile-form">
        <a-form-item label="用户名">
          <a-input v-model:value="form.userName" allow-clear placeholder="请输入用户名" />
        </a-form-item>
        <a-form-item label="个人简介">
          <a-textarea
            v-model:value="form.userProfile"
            :rows="4"
            :maxlength="200"
            show-count
            placeholder="介绍一下自己"
          />
        </a-form-item>
      </a-form>
    </div>
  </a-modal>
</template>

<script setup lang="ts">
import { onBeforeUnmount, reactive, ref, watch } from 'vue'
import { UploadOutlined } from '@ant-design/icons-vue'
import { message } from 'ant-design-vue'
import { updateUser, uploadUserAvatar } from '@/api/userController'
import defaultAvatar from '@/assets/default-avatar.png'

const props = defineProps<{
  open: boolean
  user?: API.UserVO | API.LoginUserVO
  title?: string
}>()

const emit = defineEmits<{
  'update:open': [open: boolean]
  saved: []
}>()

const form = reactive<API.UserUpdateRequest>({
  id: undefined,
  userName: '',
  userAvatar: '',
  userProfile: '',
})
const saving = ref(false)
const uploading = ref(false)
const fileInputRef = ref<HTMLInputElement>()
const avatarPreviewUrl = ref('')
let localPreviewUrl = ''

const revokeLocalPreview = () => {
  if (localPreviewUrl) {
    URL.revokeObjectURL(localPreviewUrl)
    localPreviewUrl = ''
  }
}

const fillForm = () => {
  form.id = props.user?.id
  form.userName = props.user?.userName || ''
  form.userAvatar = props.user?.userAvatar || ''
  form.userProfile = props.user?.userProfile || ''
  revokeLocalPreview()
  avatarPreviewUrl.value = form.userAvatar || ''
}

const close = () => {
  emit('update:open', false)
}

const preloadImage = (url: string) =>
  new Promise<void>((resolve, reject) => {
    const image = new Image()
    image.onload = () => resolve()
    image.onerror = reject
    image.src = url
  })

const handleAvatarPreviewError = () => {
  if (localPreviewUrl) {
    avatarPreviewUrl.value = localPreviewUrl
    return
  }
  avatarPreviewUrl.value = ''
}

const handleFileChange = async (event: Event) => {
  const input = event.target as HTMLInputElement
  const file = input.files?.[0]
  input.value = ''
  if (!file) return
  if (!file.type.startsWith('image/')) {
    message.warning('请选择图片文件')
    return
  }
  if (file.size > 2 * 1024 * 1024) {
    message.warning('头像不能超过 2MB')
    return
  }
  revokeLocalPreview()
  localPreviewUrl = URL.createObjectURL(file)
  avatarPreviewUrl.value = localPreviewUrl
  uploading.value = true
  try {
    const res = await uploadUserAvatar(file, form.id)
    if (res.data.code !== 0 || !res.data.data) {
      message.error(res.data.message || '头像上传失败')
      return
    }
    form.userAvatar = res.data.data
    try {
      await preloadImage(res.data.data)
      avatarPreviewUrl.value = res.data.data
      revokeLocalPreview()
      message.success('头像上传成功')
    } catch {
      avatarPreviewUrl.value = localPreviewUrl
      message.warning('头像已上传，但远程图片暂时无法预览，保存后请检查 COS 访问地址')
    }
  } catch {
    message.warning('头像上传未完成，当前仅显示本地预览')
  } finally {
    uploading.value = false
  }
}

const submit = async () => {
  if (!form.id) {
    message.error('用户 ID 缺失，无法保存')
    return
  }
  saving.value = true
  try {
    const res = await updateUser({
      id: form.id,
      userName: form.userName?.trim(),
      userAvatar: form.userAvatar?.trim(),
      userProfile: form.userProfile?.trim(),
    })
    if (res.data.code !== 0) {
      message.error(res.data.message || '保存失败')
      return
    }
    message.success('资料已更新')
    emit('saved')
    close()
  } finally {
    saving.value = false
  }
}

watch(
  () => [props.open, props.user?.id],
  () => {
    if (props.open) {
      fillForm()
    }
  },
  { immediate: true },
)

onBeforeUnmount(revokeLocalPreview)
</script>

<style scoped>
.profile-editor {
  padding-top: 6px;
}

.avatar-editor {
  display: flex;
  align-items: center;
  gap: 16px;
  margin-bottom: 20px;
  padding: 16px;
  border: 1px solid #ecebff;
  border-radius: 14px;
  background: #f8f7ff;
}

.avatar-editor p {
  display: block;
  margin: 0;
}

.avatar-editor p {
  margin: 0 0 10px;
  color: #98a2b3;
  font-size: 12px;
}

.avatar-preview {
  width: 78px;
  height: 78px;
  flex: 0 0 78px;
  overflow: hidden;
  border-radius: 50%;
  background: #e4e7ec;
}

.avatar-preview img {
  width: 100%;
  height: 100%;
  object-fit: cover;
  display: block;
}

.profile-form :deep(.ant-form-item-label > label) {
  color: #344054;
  font-size: 12px;
  font-weight: 600;
}

.field-hint {
  margin: 6px 0 0;
  color: #98a2b3;
  font-size: 11px;
  line-height: 1.5;
}
</style>
