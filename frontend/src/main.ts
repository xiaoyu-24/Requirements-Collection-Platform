import { createApp } from 'vue'
import Antd from 'ant-design-vue'
import dayjs from 'dayjs'
import 'dayjs/locale/zh-cn'
import 'ant-design-vue/dist/reset.css'
import './style.css'
import App from './App.vue'
import router from './router'

dayjs.locale('zh-cn')

const mountTarget = document.getElementById('app')
if (mountTarget) {
  createApp(App).use(router).use(Antd).mount(mountTarget)
}
