<script lang="ts">
export default {
  name: 'AccountManagementDialog',
};
</script>

<script setup lang="ts">
import { computed, reactive, ref, watch } from 'vue';
import { ElMessage } from 'element-plus';
import type { AuthUser } from '../../types';

const props = defineProps<{
  modelValue: boolean;
  user: AuthUser | null;
  avatarUrl?: string | null;
  avatarLoading?: boolean;
  displayNameLoading?: boolean;
  passwordLoading?: boolean;
  emailCodeLoading?: boolean;
  emailLoading?: boolean;
}>();

const emit = defineEmits<{
  'update:modelValue': [value: boolean];
  uploadAvatar: [file: File];
  changeDisplayName: [displayName: string];
  changePassword: [payload: { currentPassword: string; newPassword: string }];
  requestEmailCode: [email: string];
  changeEmail: [payload: { email: string; emailCode: string }];
}>();

const fileInputRef = ref<HTMLInputElement | null>(null);
const avatarPreviewUrl = ref('');
const selectedAvatarName = ref('');
const profileForm = reactive({
  displayName: '',
});
const passwordForm = reactive({
  currentPassword: '',
  newPassword: '',
  confirmPassword: '',
});
const emailForm = reactive({
  email: '',
  emailCode: '',
});

const visible = computed({
  get: () => props.modelValue,
  set: (value: boolean) => emit('update:modelValue', value),
});
const displayAvatarUrl = computed(() => avatarPreviewUrl.value || props.avatarUrl || props.user?.avatarUrl || '');
const displayName = computed(() => props.user?.displayName || props.user?.username || '当前用户');
const username = computed(() => props.user?.username || '-');
const currentEmail = computed(() => props.user?.email || '未绑定邮箱');
const busy = computed(() => props.avatarLoading || props.displayNameLoading || props.passwordLoading || props.emailCodeLoading || props.emailLoading);

watch(
  () => props.modelValue,
  (open) => {
    if (!open) {
      resetDialog();
      return;
    }
    profileForm.displayName = props.user?.displayName || props.user?.username || '';
    emailForm.email = props.user?.email || '';
  },
);

function openAvatarPicker() {
  if (props.avatarLoading) {
    return;
  }
  fileInputRef.value?.click();
}

function handleAvatarSelected(event: Event) {
  const input = event.target as HTMLInputElement;
  const file = input.files?.[0];
  input.value = '';
  if (!file) {
    return;
  }
  if (!validateAvatarFile(file)) {
    return;
  }
  replacePreviewUrl(URL.createObjectURL(file));
  selectedAvatarName.value = file.name;
  emit('uploadAvatar', file);
}

function validateAvatarFile(file: File) {
  if (!['image/jpeg', 'image/png', 'image/webp'].includes(file.type)) {
    ElMessage.warning('头像仅支持 JPG、PNG 或 WebP 图片');
    return false;
  }
  if (file.size > 5 * 1024 * 1024) {
    ElMessage.warning('头像文件不能超过 5MB');
    return false;
  }
  return true;
}

function submitDisplayName() {
  const displayName = profileForm.displayName.trim();
  if (!displayName) {
    ElMessage.warning('请输入昵称');
    return;
  }
  emit('changeDisplayName', displayName);
}

function submitPassword() {
  const currentPassword = passwordForm.currentPassword.trim();
  const newPassword = passwordForm.newPassword.trim();
  const confirmPassword = passwordForm.confirmPassword.trim();
  if (!currentPassword || !newPassword || !confirmPassword) {
    ElMessage.warning('请完整填写密码信息');
    return;
  }
  if (newPassword !== confirmPassword) {
    ElMessage.warning('两次输入的新密码不一致');
    return;
  }
  if (currentPassword === newPassword) {
    ElMessage.warning('新密码不能与当前密码相同');
    return;
  }
  if (newPassword.length < 6) {
    ElMessage.warning('新密码至少 6 位');
    return;
  }
  emit('changePassword', { currentPassword, newPassword });
}

function submitEmailCodeRequest() {
  const email = emailForm.email.trim();
  if (!email) {
    ElMessage.warning('请输入新邮箱');
    return;
  }
  emit('requestEmailCode', email);
}

function submitEmailChange() {
  const email = emailForm.email.trim();
  const emailCode = emailForm.emailCode.trim();
  if (!email || !emailCode) {
    ElMessage.warning('请填写新邮箱和验证码');
    return;
  }
  emit('changeEmail', { email, emailCode });
}

function clearPasswordForm() {
  passwordForm.currentPassword = '';
  passwordForm.newPassword = '';
  passwordForm.confirmPassword = '';
}

function resetProfileForm() {
  profileForm.displayName = props.user?.displayName || props.user?.username || '';
}

function clearEmailCode() {
  emailForm.emailCode = '';
}

function resetDialog() {
  resetProfileForm();
  clearPasswordForm();
  clearEmailCode();
  selectedAvatarName.value = '';
  revokePreviewUrl();
}

function replacePreviewUrl(nextPreviewUrl: string) {
  revokePreviewUrl();
  avatarPreviewUrl.value = nextPreviewUrl;
}

function revokePreviewUrl() {
  if (avatarPreviewUrl.value) {
    URL.revokeObjectURL(avatarPreviewUrl.value);
    avatarPreviewUrl.value = '';
  }
}

defineExpose({ clearPasswordForm });
</script>

<template>
  <el-dialog
    v-model="visible"
    title="账号管理"
    width="min(620px, 94vw)"
    class="account-dialog"
    destroy-on-close
    align-center
    :close-on-click-modal="!busy"
  >
    <section class="account-profile-card">
      <button class="account-avatar-button" type="button" title="点击修改头像" @click="openAvatarPicker">
        <img v-if="displayAvatarUrl" :src="displayAvatarUrl" alt="当前头像">
        <span v-else>{{ displayName.slice(0, 1).toUpperCase() }}</span>
      </button>
      <input
        ref="fileInputRef"
        class="avatar-file-input"
        type="file"
        accept="image/jpeg,image/png,image/webp"
        @change="handleAvatarSelected"
      >
      <div class="account-profile-meta">
        <strong>{{ displayName }}</strong>
        <span>用户名：{{ username }}</span>
        <span>邮箱：{{ currentEmail }}</span>
        <small>{{ selectedAvatarName || '点击头像即可选择新头像，支持 JPG / PNG / WebP，最大 5MB' }}</small>
      </div>
    </section>

    <section class="account-section">
      <div class="section-heading">
        <strong>修改昵称</strong>
        <span>昵称会显示在侧边栏和账号信息中。</span>
      </div>
      <el-form label-position="top" class="account-form">
        <el-form-item label="昵称" required>
          <div class="email-code-row">
            <el-input v-model="profileForm.displayName" maxlength="40" placeholder="请输入昵称" />
            <el-button type="primary" :loading="props.displayNameLoading" @click="submitDisplayName">保存昵称</el-button>
          </div>
        </el-form-item>
      </el-form>
    </section>

    <section class="account-section">
      <div class="section-heading">
        <strong>修改密码</strong>
        <span>需要输入当前密码，并二次确认新密码。</span>
      </div>
      <el-form label-position="top" class="account-form">
        <el-form-item label="当前密码" required>
          <el-input v-model="passwordForm.currentPassword" type="password" show-password placeholder="请输入当前密码" />
        </el-form-item>
        <div class="form-grid two-columns">
          <el-form-item label="新密码" required>
            <el-input v-model="passwordForm.newPassword" type="password" show-password placeholder="请输入新密码" />
          </el-form-item>
          <el-form-item label="确认新密码" required>
            <el-input v-model="passwordForm.confirmPassword" type="password" show-password placeholder="请再次输入新密码" />
          </el-form-item>
        </div>
        <div class="form-actions">
          <el-button @click="clearPasswordForm">清空</el-button>
          <el-button type="primary" :loading="props.passwordLoading" @click="submitPassword">保存密码</el-button>
        </div>
      </el-form>
    </section>

    <section class="account-section">
      <div class="section-heading">
        <strong>换绑邮箱</strong>
        <span>验证码会发送到新邮箱，通过后立即更新当前账号邮箱。</span>
      </div>
      <el-form label-position="top" class="account-form">
        <el-form-item label="新邮箱" required>
          <div class="email-code-row">
            <el-input v-model="emailForm.email" placeholder="name@example.com" />
            <el-button :loading="props.emailCodeLoading" @click="submitEmailCodeRequest">发送验证码</el-button>
          </div>
        </el-form-item>
        <el-form-item label="验证码" required>
          <el-input v-model="emailForm.emailCode" maxlength="6" placeholder="请输入 6 位验证码" />
        </el-form-item>
        <div class="form-actions">
          <el-button type="primary" :loading="props.emailLoading" @click="submitEmailChange">确认换绑</el-button>
        </div>
      </el-form>
    </section>
  </el-dialog>
</template>

<style scoped>
.account-profile-card {
  display: grid;
  grid-template-columns: 96px minmax(0, 1fr);
  align-items: center;
  gap: 18px;
  margin-bottom: 18px;
  padding: 18px;
  border: 1px solid rgba(37, 99, 235, 0.12);
  border-radius: 22px;
  background: linear-gradient(135deg, #f8fbff, #ffffff);
}

.account-avatar-button {
  position: relative;
  display: grid;
  place-items: center;
  width: 96px;
  height: 96px;
  overflow: hidden;
  border: 0;
  border-radius: 28px;
  color: var(--app-primary);
  background: var(--app-primary-soft);
  cursor: pointer;
  font-size: 30px;
  font-weight: 900;
  padding: 0;
}

.account-avatar-button img {
  width: 100%;
  height: 100%;
  object-fit: cover;
}

.account-avatar-button:hover {
  box-shadow: var(--app-focus-ring);
}

.avatar-file-input {
  display: none;
}

.account-profile-meta {
  min-width: 0;
  display: grid;
  gap: 6px;
}

.account-profile-meta strong {
  color: var(--app-text);
  font-size: 20px;
}

.account-profile-meta span,
.account-profile-meta small,
.section-heading span {
  color: var(--app-text-muted);
  line-height: 1.5;
}

.account-profile-meta small {
  margin-top: 2px;
}

.account-section {
  display: grid;
  gap: 14px;
  padding: 18px 0;
  border-top: 1px solid rgba(15, 23, 42, 0.08);
}

.section-heading {
  display: grid;
  gap: 4px;
}

.section-heading strong {
  color: var(--app-text);
  font-size: 16px;
}

.account-form :deep([class~="el-form-item"]) {
  margin-bottom: 14px;
}

.form-grid.two-columns {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 12px;
}

.email-code-row {
  width: 100%;
  display: grid;
  grid-template-columns: minmax(0, 1fr) auto;
  gap: 10px;
}

.form-actions {
  display: flex;
  justify-content: flex-end;
  gap: 10px;
}

:global([class~="el-dialog"][class~="account-dialog"]) {
  height: min(760px, 88vh);
  display: flex;
  flex-direction: column;
  overflow: hidden;
  border-radius: 24px;
}

:global([class~="el-dialog"][class~="account-dialog"] [class~="el-dialog__body"]) {
  flex: 1;
  min-height: 0;
  overflow-y: auto;
  padding-top: 10px;
}

:global([class~="account-dialog"] [class~="el-button"]),
:global([class~="account-dialog"] [class~="el-input__wrapper"]) {
  border-radius: 12px;
}

@media (max-width: 640px) {
  .account-profile-card,
  .form-grid.two-columns,
  .email-code-row {
    grid-template-columns: 1fr;
  }

  .account-avatar-button {
    justify-self: center;
  }

  .form-actions {
    flex-direction: column;
  }
}
</style>