<script setup lang="ts">
import { computed, reactive, ref, watch } from 'vue';
import { ElMessage } from 'element-plus';
import {
  createAdminUser,
  listAdminUsers,
  resetAdminUserPassword,
  updateAdminUserRoles,
  updateAdminUserStatus,
} from '../api/adminUsers';
import { getErrorMessage } from '../api/http';
import type { AdminUser } from '../types';

const props = defineProps<{
  modelValue: boolean;
}>();

const emit = defineEmits<{
  'update:modelValue': [value: boolean];
}>();

const visible = computed({
  get: () => props.modelValue,
  set: (value: boolean) => emit('update:modelValue', value),
});

const loading = ref(false);
const saving = ref(false);
const users = ref<AdminUser[]>([]);
const keyword = ref('');
const pagination = reactive({ page: 0, size: 20, total: 0 });
const createDialogVisible = ref(false);
const passwordDialogVisible = ref(false);
const selectedUser = ref<AdminUser | null>(null);

const createForm = reactive({
  username: '',
  password: '',
  displayName: '',
  email: '',
  roles: ['USER'],
});
const passwordForm = reactive({ password: '' });

watch(
  () => props.modelValue,
  (open) => {
    if (open) {
      void loadUsers(0);
    }
  },
);

async function loadUsers(page = pagination.page) {
  loading.value = true;
  try {
    const result = await listAdminUsers({ page, size: pagination.size, keyword: keyword.value.trim() || undefined });
    users.value = result.items;
    pagination.page = result.page;
    pagination.size = result.size;
    pagination.total = result.total;
  } catch (error) {
    ElMessage.error(getErrorMessage(error));
  } finally {
    loading.value = false;
  }
}

async function handleCreateUser() {
  saving.value = true;
  try {
    await createAdminUser({
      username: createForm.username.trim(),
      password: createForm.password,
      displayName: createForm.displayName.trim() || undefined,
      email: createForm.email.trim() || undefined,
      roles: createForm.roles,
    });
    ElMessage.success('用户已创建');
    createDialogVisible.value = false;
    Object.assign(createForm, { username: '', password: '', displayName: '', email: '', roles: ['USER'] });
    await loadUsers(0);
  } catch (error) {
    ElMessage.error(getErrorMessage(error));
  } finally {
    saving.value = false;
  }
}

async function handleRoleChange(user: AdminUser, roles: string[]) {
  try {
    await updateAdminUserRoles(user.id, roles);
    ElMessage.success('角色已更新');
    await loadUsers();
  } catch (error) {
    ElMessage.error(getErrorMessage(error));
    await loadUsers();
  }
}

async function handleStatusChange(user: AdminUser, status: string) {
  try {
    await updateAdminUserStatus(user.id, status);
    ElMessage.success('状态已更新');
    await loadUsers();
  } catch (error) {
    ElMessage.error(getErrorMessage(error));
    await loadUsers();
  }
}

function openPasswordDialog(user: AdminUser) {
  selectedUser.value = user;
  passwordForm.password = '';
  passwordDialogVisible.value = true;
}

async function handleResetPassword() {
  if (!selectedUser.value) {
    return;
  }
  saving.value = true;
  try {
    await resetAdminUserPassword(selectedUser.value.id, { password: passwordForm.password });
    ElMessage.success('密码已重置');
    passwordDialogVisible.value = false;
  } catch (error) {
    ElMessage.error(getErrorMessage(error));
  } finally {
    saving.value = false;
  }
}

function formatDate(value: string | null) {
  return value ? new Date(value).toLocaleString() : '-';
}
</script>

<template>
  <el-drawer v-model="visible" title="用户管理" size="720px" destroy-on-close>
    <div class="toolbar">
      <el-input v-model="keyword" clearable placeholder="搜索用户名 / 昵称 / 邮箱" @keyup.enter="loadUsers(0)" />
      <el-button @click="loadUsers(0)">搜索</el-button>
      <el-button type="primary" @click="createDialogVisible = true">新建用户</el-button>
    </div>

    <el-table :data="users" :loading="loading" border stripe height="560">
      <el-table-column prop="username" label="用户名" min-width="130" />
      <el-table-column prop="displayName" label="昵称" min-width="130" />
      <el-table-column label="角色" min-width="180">
        <template #default="{ row }">
          <el-select :model-value="row.roles" multiple @change="(roles: string[]) => handleRoleChange(row, roles)">
            <el-option label="管理员" value="ADMIN" />
            <el-option label="普通用户" value="USER" />
          </el-select>
        </template>
      </el-table-column>
      <el-table-column label="状态" width="130">
        <template #default="{ row }">
          <el-select :model-value="row.status" @change="(status: string) => handleStatusChange(row, status)">
            <el-option label="启用" value="ACTIVE" />
            <el-option label="禁用" value="DISABLED" />
          </el-select>
        </template>
      </el-table-column>
      <el-table-column label="最近登录" width="170">
        <template #default="{ row }">{{ formatDate(row.lastLoginAt) }}</template>
      </el-table-column>
      <el-table-column label="操作" width="110" fixed="right">
        <template #default="{ row }">
          <el-button text type="primary" @click="openPasswordDialog(row)">重置密码</el-button>
        </template>
      </el-table-column>
    </el-table>

    <div class="pagination-wrap">
      <el-pagination
        background
        layout="total, prev, pager, next"
        :total="pagination.total"
        :page-size="pagination.size"
        :current-page="pagination.page + 1"
        @current-change="(page: number) => loadUsers(page - 1)"
      />
    </div>

    <el-dialog v-model="createDialogVisible" title="新建用户" width="420px">
      <el-form label-position="top">
        <el-form-item label="用户名"><el-input v-model="createForm.username" /></el-form-item>
        <el-form-item label="初始密码"><el-input v-model="createForm.password" type="password" show-password /></el-form-item>
        <el-form-item label="昵称"><el-input v-model="createForm.displayName" /></el-form-item>
        <el-form-item label="邮箱"><el-input v-model="createForm.email" /></el-form-item>
        <el-form-item label="角色">
          <el-select v-model="createForm.roles" multiple>
            <el-option label="管理员" value="ADMIN" />
            <el-option label="普通用户" value="USER" />
          </el-select>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="createDialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="handleCreateUser">创建</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="passwordDialogVisible" title="重置密码" width="380px">
      <el-form label-position="top">
        <el-form-item label="新密码"><el-input v-model="passwordForm.password" type="password" show-password /></el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="passwordDialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="handleResetPassword">确认重置</el-button>
      </template>
    </el-dialog>
  </el-drawer>
</template>

<style scoped>
.toolbar {
  display: flex;
  gap: 10px;
  margin-bottom: 14px;
}

.pagination-wrap {
  display: flex;
  justify-content: flex-end;
  margin-top: 16px;
}
</style>