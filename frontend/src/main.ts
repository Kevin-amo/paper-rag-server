import { createApp } from 'vue';
import ElementPlus from 'element-plus';
import 'element-plus/dist/index.css';
import App from './App.vue';
import router from './router';
import { setTokenProvider } from './api/http';
import { setAgentTokenProvider } from './api/agent';
import { setDocumentsTokenProvider } from './api/documents';
import { getAccessToken } from './composables/authState';
import './style.css';

setTokenProvider(getAccessToken);
setAgentTokenProvider(getAccessToken);
setDocumentsTokenProvider(getAccessToken);

createApp(App).use(ElementPlus).use(router).mount('#app');