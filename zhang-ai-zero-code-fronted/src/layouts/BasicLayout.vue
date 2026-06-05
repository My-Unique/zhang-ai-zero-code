<template>
  <router-view v-if="route.meta.hideLayout" />
  <a-layout v-else class="basic-layout">
    <GlobalHeader />
    <a-layout-content class="main-content">
      <router-view />
    </a-layout-content>
    <GlobalFooter />
  </a-layout>
</template>

<script setup lang="ts">
import { useRoute } from 'vue-router'
import GlobalHeader from '@/components/GlobalHeader.vue'
import GlobalFooter from '@/components/GlobalFooter.vue'

const route = useRoute()
</script>

<style scoped>
.basic-layout {
  min-height: 100vh;
  background: #f6f8fb;
}

.main-content {
  flex: 1;
  width: 100%;
}
</style>
