<script setup lang="ts">
import { computed, reactive, ref, watch } from 'vue';
import { ElMessage } from 'element-plus';
import {
  createAdminUser,
  deleteAdminUser,
  listAdminUsers,
  resetAdminUserPassword,
  updateAdminUser,
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
const deletingUserId = ref<string | null>(null);
const users = ref<AdminUser[]>([]);
const keyword = ref('');
const statusFilter = ref('');
const pagination = reactive({ page: 0, size: 20, total: 0 });
const formDialogVisible = ref(false);
const passwordDialogVisible = ref(false);
const selectedUser = ref<AdminUser | null>(null);
const formMode = ref<'create' | 'edit'>('create');

const userForm = reactive({
  username: '',
  password: '',
  displayName: '',
  email: '',
  roles: ['USER'],
});
const passwordForm = reactive({ password: '' });

const activeCount = computed(() => users.value.filter((user) => user.status === 'ACTIVE').length);
const adminCount = computed(() => users.value.filter((user) => user.roles.includes('ADMIN')).length);
const disabledCount = computed(() => users.value.filter((user) => user.status === 'DISABLED').length);
const dialogTitle = computed(() => (formMode.value === 'create' ? '新建用户' : '编辑用户'));

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
    const result = await listAdminUsers({
      page,
      size: pagination.size,
      keyword: keyword.value.trim() || undefined,
      status: statusFilter.value || undefined,
    });
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

function resetUserForm() {
  Object.assign(userForm, { username: '', password: '', displayName: '', email: '', roles: ['USER'] });
  selectedUser.value = null;
}

function openCreateDialog() {
  formMode.value = 'create';
  resetUserForm();
  formDialogVisible.value = true;
}

function openEditDialog(user: AdminUser) {
  formMode.value = 'edit';
  selectedUser.value = user;
  Object.assign(userForm, {
    username: user.username,
    password: '',
    displayName: user.displayName || '',
    email: user.email || '',
    roles: [...user.roles],
  });
  formDialogVisible.value = true;
}

async function handleSaveUser() {
  if (formMode.value === 'create' && !userForm.username.trim()) {
    ElMessage.warning('请输入用户名');
    return;
  }
  if (formMode.value === 'create' && !userForm.password) {
    ElMessage.warning('请输入初始密码');
    return;
  }
  if (!userForm.roles.length) {
    ElMessage.warning('请至少选择一个角色');
    return;
  }

  saving.value = true;
  try {
    if (formMode.value === 'create') {
      await createAdminUser({
        username: userForm.username.trim(),
        password: userForm.password,
        displayName: userForm.displayName.trim() || undefined,
        email: userForm.email.trim() || undefined,
        roles: userForm.roles,
      });
      ElMessage.success('用户已创建');
      await loadUsers(0);
    } else if (selectedUser.value) {
      await updateAdminUser(selectedUser.value.id, {
        displayName: userForm.displayName.trim() || undefined,
        email: userForm.email.trim() || undefined,
      });
      await updateAdminUserRoles(selectedUser.value.id, userForm.roles);
      ElMessage.success('用户资料已更新');
      await loadUsers();
    }
    formDialogVisible.value = false;
    resetUserForm();
  } catch (error) {
    ElMessage.error(getErrorMessage(error));
  } finally {
    saving.value = false;
  }
}

async function handleRoleChange(user: AdminUser, roles: string[]) {
  if (!roles.length) {
    ElMessage.warning('请至少保留一个角色');
    await loadUsers();
    return;
  }
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
  if (!passwordForm.password) {
    ElMessage.warning('请输入新密码');
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

async function handleDeleteUser(user: AdminUser) {
  deletingUserId.value = user.id;
  try {
    await deleteAdminUser(user.id);
    ElMessage.success('用户已删除');
    const nextPage = users.value.length === 1 && pagination.page > 0 ? pagination.page - 1 : pagination.page;
    await loadUsers(nextPage);
  } catch (error) {
    ElMessage.error(getErrorMessage(error));
  } finally {
    deletingUserId.value = null;
  }
}

function formatDate(value: string | null) {
  return value ? new Date(value).toLocaleString() : '-';
}

function roleTagType(role: string) {
  return role === 'ADMIN' ? 'danger' : 'primary';
}

function statusTagType(status: string) {
  return status === 'ACTIVE' ? 'success' : 'info';
}
</script>

<template>
  <el-drawer v-model="visible" size="min(1080px, 92vw)" destroy-on-close class="admin-users-drawer">
    <template #header>
      <div class="drawer-title">
        <span class="drawer-kicker">Admin Console</span>
        <strong>用户管理</strong>
      </div>
    </template>

    <section class="admin-summary">
      <div class="summary-card">
        <span>当前页用户</span>
        <strong>{{ users.length }}</strong>
      </div>
      <div class="summary-card">
        <span>启用账号</span>
        <strong>{{ activeCount }}</strong>
      </div>
      <div class="summary-card">
        <span>管理员</span>
        <strong>{{ adminCount }}</strong>
      </div>
      <div class="summary-card muted">
        <span>禁用账号</span>
        <strong>{{ disabledCount }}</strong>
      </div>
    </section>

    <div class="toolbar">
      <el-input
        v-model="keyword"
        clearable
        size="large"
        placeholder="搜索用户名 / 昵称 / 邮箱"
        @keyup.enter="loadUsers(0)"
      />
      <el-select v-model="statusFilter" clearable size="large" placeholder="状态" class="status-filter">
        <el-option label="启用" value="ACTIVE" />
        <el-option label="禁用" value="DISABLED" />
      </el-select>
      <el-button size="large" @click="loadUsers(0)">搜索</el-button>
      <el-button size="large" type="primary" @click="openCreateDialog">新建用户</el-button>
    </div>

    <el-table :data="users" :loading="loading" height="560" class="users-table">
      <el-table-column label="用户" min-width="220">
        <template #default="{ row }">
          <div class="user-cell">
            <div class="user-avatar">{{ row.username.slice(0, 1).toUpperCase() }}</div>
            <div>
              <strong>{{ row.displayName || row.username }}</strong>
              <span>{{ row.username }}</span>
            </div>
          </div>
        </template>
      </el-table-column>
      <el-table-column prop="email" label="邮箱" min-width="190" show-overflow-tooltip>
        <template #default="{ row }">{{ row.email || '-' }}</template>
      </el-table-column>
      <el-table-column label="角色" min-width="220">
        <template #default="{ row }">
          <el-select :model-value="row.roles" multiple collapse-tags collapse-tags-tooltip @change="(roles: string[]) => handleRoleChange(row, roles)">
            <el-option label="管理员" value="ADMIN" />
            <el-option label="普通用户" value="USER" />
          </el-select>
        </template>
      </el-table-column>
      <el-table-column label="状态" width="150">
        <template #default="{ row }">
          <el-select :model-value="row.status" @change="(status: string) => handleStatusChange(row, status)">
            <el-option label="启用" value="ACTIVE" />
            <el-option label="禁用" value="DISABLED" />
          </el-select>
        </template>
      </el-table-column>
      <el-table-column label="最近登录" min-width="170">
        <template #default="{ row }">{{ formatDate(row.lastLoginAt) }}</template>
      </el-table-column>
      <el-table-column label="创建时间" min-width="170">
        <template #default="{ row }">{{ formatDate(row.createdAt) }}</template>
      </el-table-column>
      <el-table-column label="操作" width="220" fixed="right">
        <template #default="{ row }">
          <div class="table-actions">
            <el-button text type="primary" @click="openEditDialog(row)">编辑</el-button>
            <el-button text type="primary" @click="openPasswordDialog(row)">密码</el-button>
            <el-popconfirm
              title="确认删除这个用户吗？"
              confirm-button-text="删除"
              cancel-button-text="取消"
              confirm-button-type="danger"
              @confirm="handleDeleteUser(row)"
            >
              <template #reference>
                <el-button text type="danger" :loading="deletingUserId === row.id">删除</el-button>
              </template>
            </el-popconfirm>
          </div>
        </template>
      </el-table-column>
    </el-table>

    <div class="pagination-wrap">
      <div class="status-legend">
        <el-tag :type="statusTagType('ACTIVE')" effect="plain">ACTIVE</el-tag>
        <el-tag :type="statusTagType('DISABLED')" effect="plain">DISABLED</el-tag>
        <el-tag :type="roleTagType('ADMIN')" effect="plain">ADMIN</el-tag>
      </div>
      <el-pagination
        background
        layout="total, prev, pager, next"
        :total="pagination.total"
        :page-size="pagination.size"
        :current-page="pagination.page + 1"
        @current-change="(page: number) => loadUsers(page - 1)"
      />
    </div>

    <el-dialog v-model="formDialogVisible" :title="dialogTitle" width="480px" class="user-form-dialog">
      <el-form label-position="top">
        <el-form-item label="用户名" required>
          <el-input v-model="userForm.username" :disabled="formMode === 'edit'" placeholder="例如 alice" />
        </el-form-item>
        <el-form-item v-if="formMode === 'create'" label="初始密码" required>
          <el-input v-model="userForm.password" type="password" show-password placeholder="请输入初始密码" />
        </el-form-item>
        <el-form-item label="昵称">
          <el-input v-model="userForm.displayName" placeholder="展示名称" />
        </el-form-item>
        <el-form-item label="邮箱">
          <el-input v-model="userForm.email" placeholder="name@example.com" />
        </el-form-item>
        <el-form-item label="角色" required>
          <el-select v-model="userForm.roles" multiple class="full-select">
            <el-option label="管理员" value="ADMIN" />
            <el-option label="普通用户" value="USER" />
          </el-select>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="formDialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="handleSaveUser">保存</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="passwordDialogVisible" title="重置密码" width="400px">
      <el-form label-position="top">
        <el-form-item :label="`新密码${selectedUser ? ` · ${selectedUser.username}` : ''}`" required>
          <el-input v-model="passwordForm.password" type="password" show-password placeholder="请输入新密码" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="passwordDialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="handleResetPassword">确认重置</el-button>
      </template>
    </el-dialog>
  </el-drawer>
</template>

<style scoped>
.drawer-title {
  display: flex;
  flex-direction: column;
  gap: 3px;
}

.drawer-kicker {
  color: #2563eb;
  font-size: 12px;
  font-weight: 800;
  letter-spacing: 0.12em;
  text-transform: uppercase;
}

.drawer-title strong {
  color: #0f172a;
  font-size: 22px;
}

.admin-summary {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 14px;
  margin-bottom: 18px;
}

.summary-card {
  border: 1px solid rgba(37, 99, 235, 0.11);
  border-radius: 20px;
  padding: 16px;
  background: linear-gradient(135deg, #eff6ff, #ffffff);
}

.summary-card.muted {
  background: linear-gradient(135deg, #f8fafc, #ffffff);
}

.summary-card span {
  display: block;
  color: #64748b;
  font-size: 13px;
}

.summary-card strong {
  display: block;
  margin-top: 8px;
  color: #0f172a;
  font-size: 28px;
  line-height: 1;
}

.toolbar {
  display: grid;
  grid-template-columns: minmax(260px, 1fr) 150px auto auto;
  gap: 10px;
  margin-bottom: 14px;
}

.status-filter {
  width: 150px;
}

.users-table {
  overflow: hidden;
  border: 1px solid #e2e8f0;
  border-radius: 20px;
}

.users-table :deep(.el-table__header th) {
  background: #f8fafc;
  color: #475569;
  font-weight: 800;
}

.user-cell {
  display: flex;
  align-items: center;
  gap: 12px;
}

.user-avatar {
  display: grid;
  width: 38px;
  height: 38px;
  place-items: center;
  border-radius: 14px;
  background: linear-gradient(135deg, #2563eb, #7c3aed);
  color: #fff;
  font-weight: 800;
}

.user-cell strong,
.user-cell span {
  display: block;
}

.user-cell span {
  margin-top: 2px;
  color: #64748b;
  font-size: 12px;
}

.table-actions {
  display: flex;
  align-items: center;
  gap: 2px;
}

.pagination-wrap {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  margin-top: 16px;
}

.status-legend {
  display: flex;
  gap: 8px;
}

.full-select {
  width: 100%;
}

@media (max-width: 900px) {
  .admin-summary,
  .toolbar {
    grid-template-columns: 1fr;
  }

  .status-filter {
    width: 100%;
  }

  .pagination-wrap {
    align-items: flex-start;
    flex-direction: column;
  }
}
</style>