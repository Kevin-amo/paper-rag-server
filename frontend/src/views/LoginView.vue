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
const authTransitionName = ref('auth-pane-forward');
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

function handleAuthModeChange(mode: string | number) {
  if (mode !== 'login' && mode !== 'register') {
    return;
  }
  if (mode === activeMode.value) {
    return;
  }

  authTransitionName.value = activeMode.value === 'login' ? 'auth-pane-forward' : 'auth-pane-back';
  activeMode.value = mode;
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

        <el-tabs
          :model-value="activeMode"
          stretch
          class="auth-tabs"
          :class="`mode-${activeMode}`"
          @tab-change="handleAuthModeChange"
        >
          <el-tab-pane label="登录" name="login" />
          <el-tab-pane label="注册" name="register" />
        </el-tabs>

        <div class="auth-form-frame" :class="`mode-${activeMode}`">
          <Transition :name="authTransitionName" mode="out-in">
          <el-form v-if="activeMode === 'login'" key="login" label-position="top" @submit.prevent>
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

          <el-form v-else key="register" label-position="top" @submit.prevent>
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
          </Transition>
        </div>
      </el-card>
    </section>
  </AuthLayout>
</template>

<style scoped>
.login-shell {
  --auth-accent: #007aff;
  --auth-accent-strong: #0a84ff;
  --auth-ink: #1d1d1f;
  --auth-muted: #6e6e73;
  width: min(1080px, 100%);
  display: grid;
  grid-template-columns: minmax(0, 1.02fr) minmax(390px, 0.98fr);
  overflow: hidden;
  border: 1px solid rgba(255, 255, 255, 0.72);
  border-radius: 36px;
  background: rgba(255, 255, 255, 0.68);
  box-shadow:
    0 34px 90px rgba(15, 23, 42, 0.14),
    inset 0 1px 0 rgba(255, 255, 255, 0.86);
  backdrop-filter: blur(28px) saturate(180%);
  -webkit-backdrop-filter: blur(28px) saturate(180%);
}

.brand-panel {
  position: relative;
  min-height: 640px;
  display: flex;
  flex-direction: column;
  justify-content: space-between;
  gap: 38px;
  overflow: hidden;
  padding: clamp(38px, 5vw, 62px);
  color: var(--auth-ink);
  background:
    radial-gradient(circle at 22% 16%, rgba(0, 122, 255, 0.2), transparent 18rem),
    radial-gradient(circle at 84% 72%, rgba(175, 82, 222, 0.14), transparent 20rem),
    linear-gradient(145deg, rgba(255, 255, 255, 0.78), rgba(244, 247, 255, 0.62));
}

.brand-panel::before {
  position: absolute;
  top: 22px;
  left: 24px;
  width: 52px;
  height: 12px;
  border-radius: 999px;
  background:
    radial-gradient(circle at 6px 6px, #ff5f57 0, #ff5f57 5px, transparent 5.5px),
    radial-gradient(circle at 26px 6px, #ffbd2e 0, #ffbd2e 5px, transparent 5.5px),
    radial-gradient(circle at 46px 6px, #28c840 0, #28c840 5px, transparent 5.5px);
  content: '';
  opacity: 0.95;
}

.brand-panel::after {
  position: absolute;
  right: -110px;
  bottom: -120px;
  width: 300px;
  height: 300px;
  border-radius: 999px;
  background: rgba(255, 255, 255, 0.5);
  content: '';
}

.brand-panel > * {
  position: relative;
  z-index: 1;
}

.eyebrow {
  margin: 18px 0 12px;
  color: var(--auth-accent);
  font-size: 12px;
  font-weight: 800;
  letter-spacing: 0.16em;
  text-transform: uppercase;
}

.brand-panel h1 {
  max-width: 540px;
  margin: 0;
  color: var(--auth-ink);
  font-size: clamp(36px, 5vw, 56px);
  letter-spacing: -0.055em;
  line-height: 1.05;
}

.brand-desc {
  max-width: 520px;
  margin: 22px 0 0;
  color: #515154;
  font-size: 16px;
  line-height: 1.85;
}

.feature-grid {
  display: grid;
  gap: 14px;
}

.feature-card {
  padding: 17px 18px;
  border: 1px solid rgba(255, 255, 255, 0.76);
  border-radius: 22px;
  background: rgba(255, 255, 255, 0.56);
  box-shadow:
    0 12px 32px rgba(15, 23, 42, 0.08),
    inset 0 1px 0 rgba(255, 255, 255, 0.78);
  backdrop-filter: blur(18px) saturate(160%);
  -webkit-backdrop-filter: blur(18px) saturate(160%);
}

.feature-card strong {
  display: block;
  margin-bottom: 6px;
  color: var(--auth-ink);
  font-size: 15px;
}

.feature-card span {
  color: var(--auth-muted);
  font-size: 13px;
  line-height: 1.65;
}

.form-card {
  display: flex;
  align-items: center;
  padding: clamp(34px, 4.5vw, 52px);
  border: none;
  border-left: 1px solid rgba(255, 255, 255, 0.58);
  border-radius: 0;
  background: rgba(255, 255, 255, 0.72);
  box-shadow: none;
}

.form-card :deep(.el-card__body) {
  width: 100%;
  padding-top: 24px;
}

.form-heading {
  margin-bottom: 22px;
}

.form-heading p {
  margin: 0 0 8px;
  color: var(--auth-accent);
  font-size: 12px;
  font-weight: 800;
  letter-spacing: 0.14em;
  text-transform: uppercase;
}

.form-heading h2 {
  margin: 0;
  color: var(--auth-ink);
  font-size: 31px;
  letter-spacing: -0.035em;
}

.form-heading span {
  display: block;
  margin-top: 9px;
  color: var(--auth-muted);
  line-height: 1.65;
}

.auth-tabs {
  --auth-tab-offset: 0%;
  margin-top: 12px;
}

.auth-tabs.mode-register {
  --auth-tab-offset: 100%;
}

.auth-tabs :deep(.el-tabs__header) {
  margin-bottom: 24px;
}

.auth-tabs :deep(.el-tabs__content) {
  display: none;
}

.auth-tabs :deep(.el-tabs__nav-wrap::after),
.auth-tabs :deep(.el-tabs__active-bar) {
  display: none;
}

.auth-tabs :deep(.el-tabs__nav-scroll) {
  overflow: hidden;
  padding: 4px;
  border: 1px solid rgba(209, 209, 214, 0.72);
  border-radius: 999px;
  background: rgba(242, 242, 247, 0.82);
}

.auth-tabs :deep(.el-tabs__nav) {
  position: relative;
  width: 100%;
}

.auth-tabs :deep(.el-tabs__nav::before) {
  position: absolute;
  inset: 0 auto 0 0;
  z-index: 0;
  width: 50%;
  border-radius: 999px;
  background: rgba(255, 255, 255, 0.96);
  box-shadow: 0 8px 18px rgba(15, 23, 42, 0.1);
  content: '';
  transform: translateX(var(--auth-tab-offset));
  transition: transform 0.34s cubic-bezier(0.22, 1, 0.36, 1), box-shadow 0.2s ease;
}

.auth-tabs :deep(.el-tabs__item) {
  z-index: 1;
  height: 38px;
  border-radius: 999px;
  color: var(--auth-muted);
  font-weight: 800;
  transition: color 0.18s ease, transform 0.18s ease;
}

.auth-tabs :deep(.el-tabs__item.is-active) {
  background: transparent;
  box-shadow: none;
  color: var(--auth-ink);
  transform: translateY(-1px);
}

.auth-form-frame {
  overflow: visible;
  height: 238px;
  transition: height 0.46s cubic-bezier(0.22, 1, 0.36, 1);
  will-change: height;
}

.auth-form-frame.mode-register {
  height: 510px;
}

.auth-form-frame :deep(.el-form) {
  padding-bottom: 28px;
}

.auth-pane-forward-enter-active,
.auth-pane-forward-leave-active,
.auth-pane-back-enter-active,
.auth-pane-back-leave-active {
  transition: opacity 0.2s ease, transform 0.26s cubic-bezier(0.22, 1, 0.36, 1);
}

.auth-pane-forward-enter-from,
.auth-pane-back-leave-to {
  opacity: 0;
  transform: translateX(18px) scale(0.985);
}

.auth-pane-forward-leave-to,
.auth-pane-back-enter-from {
  opacity: 0;
  transform: translateX(-18px) scale(0.985);
}

.form-card :deep(.el-form-item) {
  margin-bottom: 17px;
}

.form-card :deep(.el-form-item__label) {
  padding-bottom: 7px;
  color: var(--auth-ink);
  font-size: 13px;
  font-weight: 800;
}

.form-card :deep(.el-input__wrapper) {
  min-height: 48px;
  border-radius: 16px;
  background: rgba(247, 247, 250, 0.86);
  box-shadow:
    inset 0 0 0 1px rgba(209, 209, 214, 0.72),
    inset 0 1px 0 rgba(255, 255, 255, 0.88);
  transition: background 0.18s ease, box-shadow 0.18s ease, transform 0.18s ease;
}

.form-card :deep(.el-input__wrapper:hover) {
  background: rgba(255, 255, 255, 0.96);
}

.form-card :deep(.el-input__wrapper.is-focus) {
  background: #ffffff;
  box-shadow:
    0 0 0 4px rgba(0, 122, 255, 0.13),
    inset 0 0 0 1px rgba(0, 122, 255, 0.48);
}

.form-card :deep(.el-input__inner) {
  color: var(--auth-ink);
  font-weight: 650;
}

.form-card :deep(.el-input__inner::placeholder) {
  color: #a1a1a6;
  font-weight: 500;
}

.form-card :deep(.el-input-group__append) {
  border-radius: 0 16px 16px 0;
  background: rgba(255, 255, 255, 0.8);
  box-shadow:
    inset 0 0 0 1px rgba(209, 209, 214, 0.72),
    inset 1px 0 0 rgba(209, 209, 214, 0.65);
}

.form-card :deep(.el-input-group__append .el-button) {
  border: none;
  color: var(--auth-accent);
  font-weight: 800;
}

.primary-button {
  width: 100%;
  height: 48px;
  margin-top: 10px;
  border: none;
  border-radius: 16px;
  background: linear-gradient(135deg, var(--auth-accent), var(--auth-accent-strong));
  box-shadow: 0 12px 26px rgba(0, 122, 255, 0.24);
  color: #ffffff;
  font-weight: 850;
  letter-spacing: 0.02em;
}

.primary-button:hover,
.primary-button:focus {
  border: none;
  background: linear-gradient(135deg, #0a84ff, #409cff);
  box-shadow: 0 15px 30px rgba(0, 122, 255, 0.28);
  color: #ffffff;
}

.primary-button.is-disabled,
.primary-button.is-loading {
  box-shadow: none;
}

@media (max-width: 900px) {
  .login-shell {
    grid-template-columns: 1fr;
  }

  .brand-panel {
    min-height: auto;
  }

  .form-card {
    border-top: 1px solid rgba(255, 255, 255, 0.58);
    border-left: none;
  }

  .form-card :deep(.el-card__body) {
    padding-top: 0;
  }
}

@media (max-width: 520px) {
  .login-shell {
    border-radius: 28px;
  }

  .brand-panel,
  .form-card {
    padding: 28px;
  }

  .brand-panel h1 {
    font-size: 34px;
  }
}
</style>