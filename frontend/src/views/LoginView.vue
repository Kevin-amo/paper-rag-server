<script setup lang="ts">
import { onUnmounted, reactive, ref } from 'vue';
import { useRoute, useRouter } from 'vue-router';
import { ElMessage } from 'element-plus';
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
  if (!username) {
    ElMessage.warning('请输入用户名');
    return false;
  }
  if (!emailPattern.test(email)) {
    ElMessage.warning('请输入有效邮箱');
    return false;
  }
  if (!registerForm.password) {
    ElMessage.warning('请输入密码');
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
  <div class="login-page">
    <section class="login-card">
      <div class="brand-panel">
        <p class="eyebrow">Paper RAG</p>
        <h1>进入知识库</h1>
        <p>使用账号登录或通过邮箱验证码注册，进入论文检索、文档管理和 RAG 问答系统。</p>
      </div>

      <el-card class="form-card" shadow="never">
        <el-tabs v-model="activeMode" stretch>
          <el-tab-pane label="账号登录" name="login">
            <h2>欢迎回来</h2>
            <p class="form-subtitle">输入用户名和密码继续访问系统。</p>
            <el-form label-position="top" @submit.prevent>
              <el-form-item label="用户名">
                <el-input
                  v-model="loginForm.username"
                  size="large"
                  autocomplete="username"
                  @keyup.enter="handleLogin"
                />
              </el-form-item>
              <el-form-item label="密码">
                <el-input
                  v-model="loginForm.password"
                  size="large"
                  type="password"
                  show-password
                  autocomplete="current-password"
                  @keyup.enter="handleLogin"
                />
              </el-form-item>
              <el-button class="primary-button" type="primary" size="large" :loading="loginLoading" @click="handleLogin">
                登录
              </el-button>
            </el-form>
          </el-tab-pane>

          <el-tab-pane label="邮箱注册" name="register">
            <h2>创建账号</h2>
            <p class="form-subtitle">使用邮箱验证码创建普通用户账号。</p>
            <el-form label-position="top" @submit.prevent>
              <el-form-item label="用户名">
                <el-input
                  v-model="registerForm.username"
                  size="large"
                  autocomplete="username"
                  @keyup.enter="handleRegister"
                />
              </el-form-item>
              <el-form-item label="邮箱">
                <el-input v-model="registerForm.email" size="large" autocomplete="email">
                  <template #append>
                    <el-button :loading="codeLoading" :disabled="codeCountdown > 0" @click="handleSendRegisterCode">
                      {{ codeCountdown > 0 ? `${codeCountdown}s` : '发送验证码' }}
                    </el-button>
                  </template>
                </el-input>
              </el-form-item>
              <el-form-item label="验证码">
                <el-input
                  v-model="registerForm.emailCode"
                  size="large"
                  maxlength="6"
                  autocomplete="one-time-code"
                  @keyup.enter="handleRegister"
                />
              </el-form-item>
              <el-form-item label="密码">
                <el-input
                  v-model="registerForm.password"
                  size="large"
                  type="password"
                  show-password
                  autocomplete="new-password"
                  @keyup.enter="handleRegister"
                />
              </el-form-item>
              <el-form-item label="确认密码">
                <el-input
                  v-model="registerForm.confirmPassword"
                  size="large"
                  type="password"
                  show-password
                  autocomplete="new-password"
                  @keyup.enter="handleRegister"
                />
              </el-form-item>
              <el-button
                class="primary-button"
                type="primary"
                size="large"
                :loading="registerLoading"
                @click="handleRegister"
              >
                注册并登录
              </el-button>
            </el-form>
          </el-tab-pane>
        </el-tabs>
      </el-card>
    </section>
  </div>
</template>

<style scoped>
.login-page {
  min-height: 100vh;
  display: grid;
  place-items: center;
  padding: 32px;
  background: radial-gradient(circle at top left, #dbeafe 0, transparent 36%), #f8fafc;
}

.login-card {
  width: min(1040px, 100%);
  display: grid;
  grid-template-columns: 1.05fr 0.95fr;
  overflow: hidden;
  border-radius: 28px;
  background: #fff;
  box-shadow: 0 24px 80px rgba(15, 23, 42, 0.14);
}

.brand-panel {
  padding: 56px;
  color: #fff;
  background: linear-gradient(135deg, #1d4ed8 0%, #2563eb 52%, #60a5fa 100%);
}

.eyebrow {
  margin: 0 0 12px;
  font-size: 13px;
  letter-spacing: 0.12em;
  text-transform: uppercase;
  color: rgba(255, 255, 255, 0.76);
}

.brand-panel h1 {
  margin: 0;
  font-size: 38px;
}

.brand-panel p:last-child {
  margin: 18px 0 0;
  line-height: 1.7;
  color: rgba(255, 255, 255, 0.9);
}

.form-card {
  display: flex;
  align-items: center;
  padding: 42px 40px;
  border: none;
}

.form-card :deep(.el-card__body) {
  width: 100%;
}

.form-card h2 {
  margin: 18px 0 8px;
  font-size: 24px;
  color: #0f172a;
}

.form-subtitle {
  margin: 0 0 24px;
  color: #64748b;
  line-height: 1.6;
}

.primary-button {
  width: 100%;
  margin-top: 8px;
}

@media (max-width: 820px) {
  .login-card {
    grid-template-columns: 1fr;
  }

  .brand-panel,
  .form-card {
    padding: 36px;
  }
}
</style>