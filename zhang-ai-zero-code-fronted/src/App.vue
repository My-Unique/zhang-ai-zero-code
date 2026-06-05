<script setup lang="ts">
import BasicLayout from '@/layouts/BasicLayout.vue'
import zhCN from 'ant-design-vue/es/locale/zh_CN'
import dayjs from 'dayjs'
import 'dayjs/locale/zh-cn'
import { healthCheck } from '@/api/healthController'

dayjs.locale('zh-cn')
healthCheck()
</script>

<template>
  <a-config-provider
    :locale="zhCN"
    :theme="{
      token: {
        colorPrimary: '#5b5cf0',
        colorInfo: '#5b5cf0',
        colorSuccess: '#12a594',
        colorText: '#182230',
        colorTextSecondary: '#667085',
        colorBgLayout: '#f7f8fc',
        borderRadius: 10,
        borderRadiusLG: 16,
        controlHeight: 38,
        fontFamily: 'Inter, \'SF Pro Display\', \'PingFang SC\', \'Microsoft YaHei\', sans-serif',
      },
    }"
  >
    <BasicLayout />
  </a-config-provider>
</template>

<style>
:root {
  --color-brand: #5b5cf0;
  --color-brand-dark: #4748d8;
  --color-brand-soft: #eeefff;
  --color-title: #182230;
  --color-text: #475467;
  --color-muted: #98a2b3;
  --color-line: #eaecf0;
  --color-surface: #ffffff;
  --color-page: #f7f8fc;
  --shadow-card: 0 12px 32px rgb(16 24 40 / 6%);
  --shadow-float: 0 24px 64px rgb(16 24 40 / 12%);
  --page-width: 1240px;
}

* {
  box-sizing: border-box;
}

html {
  scroll-behavior: smooth;
}

body {
  min-width: 320px;
  margin: 0;
  color: var(--color-text);
  background: var(--color-page);
  font-family: Inter, 'SF Pro Display', 'PingFang SC', 'Microsoft YaHei', sans-serif;
  -webkit-font-smoothing: antialiased;
}

a {
  color: inherit;
  text-decoration: none;
}

button,
input,
textarea {
  font: inherit;
}

::selection {
  color: #fff;
  background: var(--color-brand);
}
</style>
