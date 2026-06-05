import { createApp } from 'vue'
import { createPinia } from 'pinia'

import App from './App.vue'
import router from './router'
import './access'
//与 import '@/access' 等价 因为
// "paths": {
//   "@/*": ["./src/*"] 所以 @/access 就是 src/access.ts
// }

import Antd from 'ant-design-vue'
import 'ant-design-vue/dist/reset.css'

const app = createApp(App)

app.use(createPinia())
app.use(router)
app.use(Antd)
app.mount('#app')
