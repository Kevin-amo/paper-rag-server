<script setup lang="ts">
import { onUnmounted, reactive, ref } from 'vue';
import { useRoute, useRouter } from 'vue-router';
import { ElMessage } from 'element-plus';
import AuthLayout from '../layouts/AuthLayout.vue';
import { getErrorMessage } from '../api/http';
import { useAuth } from '../composables/useAuth';

const router = useRouter();
const route = useRoute();
const auth = useAuth();
const activeMode = ref<'login' | 'register'>('login');
const loginLoading = ref(false);
const registerLoading = ref(false);
const codeLoading = ref(false);
const codeCountdown = ref(0);
let codeTimer: number | undefined;

const loginForm = reactive({
  username: '',
  password: '',
});

const registerForm = reactive({
  username: '',
  email: '',
  emailCode: '',
  password: '',
  confirmPassword: '',
});

const emailPattern = /^[^\s@]+@[^\s@]+\.[^\s@]+$/;

function getRedirectPath() {
  return typeof route.query.redirect === 'string' ? route.query.redirect : '/';
}

function startCodeCountdown() {
  codeCountdown.value = 60;
  if (codeTimer !== undefined) {
    window.clearInterval(codeTimer);
  }
  codeTimer = window.setInterval(() => {
    codeCountdown.value -= 1;
    if (codeCountdown.value <= 0 && codeTimer !== undefined) {
      window.clearInterval(codeTimer);
      codeTimer = undefined;
    }
  }, 1000);
}

function validateRegisterForm() {
  const username = registerForm.username.trim();
  const email = registerForm.email.trim();
  const emailCode = registerForm.emailCode.trim();

  if (username.length < 3) {
    ElMessage.warning('用户名至少需要 3 个字符');
    return false;
  }
  if (!emailPattern.test(email)) {
    ElMessage.warning('请输入有效邮箱');
    return false;
  }
  if (registerForm.password.length < 6) {
    ElMessage.warning('密码至少需要 6 位');
    return false;
  }
  if (registerForm.password !== registerForm.confirmPassword) {
    ElMessage.warning('两次输入的密码不一致');
    return false;
  }
  if (!/^\d{6}$/.test(emailCode)) {
    ElMessage.warning('请输入 6 位邮箱验证码');
    return false;
  }
  return true;
}

async function handleLogin() {
  if (!loginForm.username.trim() || !loginForm.password) {
    ElMessage.warning('请输入用户名和密码');
    return;
  }
  loginLoading.value = true;
  try {
    await auth.login(loginForm.username.trim(), loginForm.password);
    ElMessage.success('登录成功');
    await router.replace(getRedirectPath());
  } catch (error) {
    ElMessage.error(getErrorMessage(error));
  } finally {
    loginLoading.value = false;
  }
}

async function handleSendRegisterCode() {
  const email = registerForm.email.trim();
  if (!emailPattern.test(email)) {
    ElMessage.warning('请输入有效邮箱');
    return;
  }
  codeLoading.value = true;
  try {
    await auth.requestRegisterEmailCode(email);
    ElMessage.success('验证码已发送，请查收邮箱');
    startCodeCountdown();
  } catch (error) {
    ElMessage.error(getErrorMessage(error));
  } finally {
    codeLoading.value = false;
  }
}

async function handleRegister() {
  if (!validateRegisterForm()) {
    return;
  }
  registerLoading.value = true;
  try {
    await auth.register(
      registerForm.username.trim(),
      registerForm.password,
      registerForm.email.trim(),
      registerForm.emailCode.trim(),
    );
    ElMessage.success('注册成功，已自动登录');
    await router.replace(getRedirectPath());
  } catch (error) {
    ElMessage.error(getErrorMessage(error));
  } finally {
    registerLoading.value = false;
  }
}

onUnmounted(() => {
  if (codeTimer !== undefined) {
    window.clearInterval(codeTimer);
  }
});
</script>

<template>
  <AuthLayout>
    <section class="login-shell">
      <aside class="brand-panel">
        <div>
          <p class="eyebrow">Paper RAG</p>
          <h1>论文知识库与 RAG 问答系统</h1>
          <p class="brand-desc">
            面向论文资料管理、文档解析、分块检索与引用可追溯问答的一体化工作台。
          </p>
        </div>

        <div class="feature-grid">
          <div class="feature-card">
            <strong>文档管理</strong>
            <span>批量上传、解析状态、详情与分块查看</span>
          </div>
          <div class="feature-card">
            <strong>RAG 问答</strong>
            <span>会话持久化、上下文追踪、引用来源展示</span>
          </div>
          <div class="feature-card">
            <strong>权限控制</strong>
            <span>普通用户工作台与管理员用户管理分离</span>
          </div>
        </div>
      </aside>

      <el-card class="form-card" shadow="never">
        <div class="form-heading">
          <p>{{ activeMode === 'login' ? '账号登录' : '邮箱注册' }}</p>
          <h2>{{ activeMode === 'login' ? '欢迎回来' : '创建普通用户账号' }}</h2>
          <span>{{ activeMode === 'login' ? '登录后将根据角色进入对应工作台。' : '验证码将发送到你的邮箱，用于完成注册。' }}</span>
        </div>

        <el-tabs v-model="activeMode" stretch class="auth-tabs">
          <el-tab-pane label="登录" name="login">
            <el-form label-position="top" @submit.prevent>
              <el-form-item label="用户名" required>
                <el-input
                  v-model="loginForm.username"
                  size="large"
                  autocomplete="username"
                  placeholder="请输入用户名"
                  @keyup.enter="handleLogin"
                />
              </el-form-item>
              <el-form-item label="密码" required>
                <el-input
                  v-model="loginForm.password"
                  size="large"
                  type="password"
                  show-password
                  autocomplete="current-password"
                  placeholder="请输入密码"
                  @keyup.enter="handleLogin"
                />
              </el-form-item>
              <el-button class="primary-button" type="primary" size="large" :loading="loginLoading" @click="handleLogin">
                登录
              </el-button>
            </el-form>
          </el-tab-pane>

          <el-tab-pane label="注册" name="register">
            <el-form label-position="top" @submit.prevent>
              <el-form-item label="用户名" required>
                <el-input
                  v-model="registerForm.username"
                  size="large"
                  autocomplete="username"
                  placeholder="至少 3 个字符"
                  @keyup.enter="handleRegister"
                />
              </el-form-item>
              <el-form-item label="邮箱" required>
                <el-input v-model="registerForm.email" size="large" autocomplete="email" placeholder="name@example.com">
                  <template #append>
                    <el-button :loading="codeLoading" :disabled="codeCountdown > 0" @click="handleSendRegisterCode">
                      {{ codeCountdown > 0 ? `${codeCountdown}s` : '发送验证码' }}
                    </el-button>
                  </template>
                </el-input>
              </el-form-item>
              <el-form-item label="邮箱验证码" required>
                <el-input
                  v-model="registerForm.emailCode"
                  size="large"
                  maxlength="6"
                  autocomplete="one-time-code"
                  placeholder="6 位数字验证码"
                  @keyup.enter="handleRegister"
                />
              </el-form-item>
              <el-form-item label="密码" required>
                <el-input
                  v-model="registerForm.password"
                  size="large"
                  type="password"
                  show-password
                  autocomplete="new-password"
                  placeholder="至少 6 位"
                  @keyup.enter="handleRegister"
                />
              </el-form-item>
              <el-form-item label="确认密码" required>
                <el-input
                  v-model="registerForm.confirmPassword"
                  size="large"
                  type="password"
                  show-password
                  autocomplete="new-password"
                  placeholder="再次输入密码"
                  @keyup.enter="handleRegister"
                />
              </el-form-item>
              <el-button class="primary-button" type="primary" size="large" :loading="registerLoading" @click="handleRegister">
                注册并登录
              </el-button>
            </el-form>
          </el-tab-pane>
        </el-tabs>
      </el-card>
    </section>
  </AuthLayout>
</template>

<style scoped>
.login-shell {
  width: min(1120px, 100%);
  display: grid;
  grid-template-columns: minmax(0, 1.05fr) minmax(380px, 0.95fr);
  overflow: hidden;
  border: 1px solid rgba(226, 232, 240, 0.75);
  border-radius: 30px;
  background: #fff;
  box-shadow: 0 28px 90px rgba(15, 23, 42, 0.16);
}

.brand-panel {
  min-height: 620px;
  display: flex;
  flex-direction: column;
  justify-content: space-between;
  gap: 36px;
  padding: clamp(36px, 5vw, 58px);
  color: #fff;
  background:
    radial-gradient(circle at 20% 20%, rgba(255, 255, 255, 0.22), transparent 18rem),
    linear-gradient(135deg, #1e3a8a 0%, #2563eb 54%, #4f46e5 100%);
}

.eyebrow {
  margin: 0 0 12px;
  color: rgba(255, 255, 255, 0.76);
  font-size: 13px;
  font-weight: 800;
  letter-spacing: 0.14em;
  text-transform: uppercase;
}

.brand-panel h1 {
  max-width: 520px;
  margin: 0;
  font-size: clamp(34px, 5vw, 52px);
  line-height: 1.12;
}

.brand-desc {
  max-width: 520px;
  margin: 20px 0 0;
  color: rgba(255, 255, 255, 0.9);
  font-size: 16px;
  line-height: 1.8;
}

.feature-grid {
  display: grid;
  gap: 12px;
}

.feature-card {
  padding: 16px;
  border: 1px solid rgba(255, 255, 255, 0.18);
  border-radius: 18px;
  background: rgba(255, 255, 255, 0.12);
  backdrop-filter: blur(12px);
}

.feature-card strong {
  display: block;
  margin-bottom: 6px;
}

.feature-card span {
  color: rgba(255, 255, 255, 0.82);
  font-size: 13px;
  line-height: 1.6;
}

.form-card {
  display: flex;
  align-items: center;
  padding: clamp(30px, 4vw, 46px);
  border: none;
}

.form-card :deep(.el-card__body) {
  width: 100%;
}

.form-heading {
  margin-bottom: 18px;
}

.form-heading p {
  margin: 0 0 8px;
  color: var(--app-primary);
  font-size: 12px;
  font-weight: 800;
  letter-spacing: 0.12em;
  text-transform: uppercase;
}

.form-heading h2 {
  margin: 0;
  color: var(--app-text);
  font-size: 28px;
}

.form-heading span {
  display: block;
  margin-top: 8px;
  color: var(--app-text-muted);
  line-height: 1.6;
}

.auth-tabs {
  margin-top: 10px;
}

.primary-button {
  width: 100%;
  margin-top: 8px;
}

@media (max-width: 900px) {
  .login-shell {
    grid-template-columns: 1fr;
  }

  .brand-panel {
    min-height: auto;
  }
}

@media (max-width: 520px) {
  .login-shell {
    border-radius: 22px;
  }

  .brand-panel,
  .form-card {
    padding: 26px;
  }
}
</style>