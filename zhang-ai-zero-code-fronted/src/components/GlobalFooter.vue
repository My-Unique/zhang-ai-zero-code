<template>
  <a-layout-footer class="site-footer">
    <div class="footer-inner">
      <div class="footer-brand">
        <img src="@/assets/logo.jpg" alt="" />
        <div>
          <strong>小张 AI 零代码</strong>
          <p>让每一个想法，都能快速成为可访问的产品。</p>
        </div>
      </div>
      <a
        href="https://github.com/My-Unique/zhang-ai-zero-code"
        target="_blank"
        rel="noopener noreferrer"
      >
        GitHub 项目
      </a>
    </div>
  </a-layout-footer>
</template>

<style scoped>
.site-footer {
  padding: 0;
  background: #101828;
}

.footer-inner {
  display: flex;
  align-items: center;
  justify-content: space-between;
  width: min(var(--page-width), calc(100% - 48px));
  min-height: 112px;
  margin: 0 auto;
}

.footer-brand {
  display: flex;
  align-items: center;
  gap: 12px;
}

.footer-brand img {
  width: 38px;
  height: 38px;
  border-radius: 11px;
}

.footer-brand strong {
  color: #fff;
  font-size: 14px;
}

.footer-brand p {
  margin: 3px 0 0;
  color: #98a2b3;
  font-size: 12px;
}

a {
  color: #d0d5dd;
  font-size: 13px;
}

a:hover {
  color: #fff;
}
</style>
