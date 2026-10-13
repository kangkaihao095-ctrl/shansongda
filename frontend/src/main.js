import { createApp } from 'vue'
import App from './App.vue'
import router from './router'
import { reveal } from './directives'
import './styles/index.css'

createApp(App).use(router).directive('reveal', reveal).mount('#app')
