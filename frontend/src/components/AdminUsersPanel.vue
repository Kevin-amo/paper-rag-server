<script setup lang="ts">
import { computed, watch } from 'vue';
import StatusTag from './common/StatusTag.vue';
import RoleTag from './common/RoleTag.vue';
import ConfirmDeleteButton from './common/ConfirmDeleteButton.vue';
import { useAdminUsers } from '../composables/useAdminUsers';
import type { AdminUser, UserRole, UserStatus } from '../types';

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

const admin = useAdminUsers();

watch(
  () => props.modelValue,
  (open) => {
    if (open) {
      void admin.loadUsers(0);
    }
  },
  { immediate: props.modelValue },
);

function formatDate(value: string | null) {
  return value ? new Date(value).toLocaleString() : '-';
}

function userInitial(user: AdminUser) {
  return (user.displayName || user.username).slice(0, 1).toUpperCase();
}
</script>

<template>
  <el-drawer v-model="visible" size="min(1120px, 94vw)" destroy-on-close class="admin-users-drawer">
    <template #header>
      <div class="drawer-title">
        <span>Admin Console</span>
        <strong>用户管理</strong>
      </div>
    </template>

    <section class="admin-summary">
      <div class="summary-card">
        <span>当前页用户</span>
        <strong>{{ admin.users.value.length }}</strong>
      </div>
      <div class="summary-card">
        <span>启用账号</span>
        <strong>{{ admin.activeCount.value }}</strong>
      </div>
      <div class="summary-card">
        <span>管理员</span>
        <strong>{{ admin.adminCount.value }}</strong>
      </div>
      <div class="summary-card muted">
        <span>禁用账号</span>
        <strong>{{ admin.disabledCount.value }}</strong>
      </div>
    </section>

    <div class="toolbar">
      <el-input
        v-model="admin.keyword.value"
        clearable
        size="large"
        placeholder="搜索用户名 / 昵称 / 邮箱"
        @keyup.enter="admin.loadUsers(0)"
      />
      <el-select v-model="admin.statusFilter.value" clearable size="large" placeholder="状态" class="status-filter">
        <el-option label="启用" value="ACTIVE" />
        <el-option label="禁用" value="DISABLED" />
      </el-select>
      <el-button size="large" @click="admin.loadUsers(0)">搜索</el-button>
      <el-button size="large" type="primary" @click="admin.openCreateDialog">新建用户</el-button>
    </div>

    <el-table :data="admin.users.value" :loading="admin.loading.value" height="560" class="users-table">
      <el-table-column label="用户" min-width="220">
        <template #default="{ row }">
          <div class="user-cell">
            <div class="user-avatar">
              <img v-if="row.avatarUrl" :src="row.avatarUrl" :alt="`${row.username} 头像`">
              <span v-else>{{ userInitial(row) }}</span>
            </div>
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
      <el-table-column label="角色" min-width="230">
        <template #default="{ row }">
          <el-select
            :model-value="row.roles"
            multiple
            collapse-tags
            collapse-tags-tooltip
            @change="(roles: UserRole[]) => admin.changeRoles(row, roles)"
          >
            <el-option label="管理员" value="ADMIN" />
            <el-option label="普通用户" value="USER" />
          </el-select>
          <div class="role-preview">
            <RoleTag v-for="role in row.roles" :key="role" :role="role" />
          </div>
        </template>
      </el-table-column>
      <el-table-column label="状态" width="150">
        <template #default="{ row }">
          <el-select :model-value="row.status" @change="(status: UserStatus) => admin.changeStatus(row, status)">
            <el-option label="启用" value="ACTIVE" />
            <el-option label="禁用" value="DISABLED" />
          </el-select>
          <div class="status-preview">
            <StatusTag :status="row.status" />
          </div>
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
            <el-button text type="primary" @click="admin.openEditDialog(row)">编辑</el-button>
            <el-button text type="primary" @click="admin.openPasswordDialog(row)">重置密码</el-button>
            <ConfirmDeleteButton
              title="确认删除这个用户吗？"
              :loading="admin.deletingUserId.value === row.id"
              @confirm="admin.removeUser(row)"
            />
          </div>
        </template>
      </el-table-column>
    </el-table>

    <div class="pagination-wrap">
      <el-pagination
        background
        layout="total, prev, pager, next"
        :total="admin.pagination.total"
        :page-size="admin.pagination.size"
        :current-page="admin.pagination.page + 1"
        @current-change="(page: number) => admin.loadUsers(page - 1)"
      />
    </div>

    <el-dialog v-model="admin.formDialogVisible.value" :title="admin.dialogTitle.value" width="480px" class="user-form-dialog">
      <el-form label-position="top">
        <el-form-item label="用户名" required>
          <el-input v-model="admin.userForm.username" :disabled="admin.formMode.value === 'edit'" placeholder="例如 alice" />
        </el-form-item>
        <el-form-item v-if="admin.formMode.value === 'create'" label="初始密码" required>
          <el-input v-model="admin.userForm.password" type="password" show-password placeholder="请输入初始密码" />
        </el-form-item>
        <el-form-item label="昵称">
          <el-input v-model="admin.userForm.displayName" placeholder="展示名称" />
        </el-form-item>
        <el-form-item label="邮箱">
          <el-input v-model="admin.userForm.email" placeholder="name@example.com" />
        </el-form-item>
        <el-form-item label="角色" required>
          <el-select v-model="admin.userForm.roles" multiple class="full-select">
            <el-option label="管理员" value="ADMIN" />
            <el-option label="普通用户" value="USER" />
          </el-select>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="admin.formDialogVisible.value = false">取消</el-button>
        <el-button type="primary" :loading="admin.saving.value" @click="admin.saveUser">保存</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="admin.passwordDialogVisible.value" title="重置密码" width="400px">
      <el-form label-position="top">
        <el-form-item :label="`新密码${admin.selectedUser.value ? ` · ${admin.selectedUser.value.username}` : ''}`" required>
          <el-input v-model="admin.passwordForm.password" type="password" show-password placeholder="请输入新密码" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="admin.passwordDialogVisible.value = false">取消</el-button>
        <el-button type="primary" :loading="admin.saving.value" @click="admin.resetPassword">确认重置</el-button>
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

.drawer-title span {
  color: var(--app-primary);
  font-size: 12px;
  font-weight: 800;
  letter-spacing: 0.12em;
  text-transform: uppercase;
}

.drawer-title strong {
  color: var(--app-text);
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
  background: linear-gradient(135deg, #eff6ff, #fff);
}

.summary-card.muted {
  background: linear-gradient(135deg, #f8fafc, #fff);
}

.summary-card span {
  display: block;
  color: var(--app-text-muted);
  font-size: 13px;
}

.summary-card strong {
  display: block;
  margin-top: 8px;
  color: var(--app-text);
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
  border: 1px solid var(--app-border);
  border-radius: 20px;
}

.user-cell {
  display: flex;
  align-items: center;
  gap: 12px;
}

.user-avatar {
  display: grid;
  place-items: center;
  width: 38px;
  height: 38px;
  overflow: hidden;
  border-radius: 12px;
  color: #fff;
  background: linear-gradient(135deg, var(--app-primary), #60a5fa);
  font-weight: 800;
}

.user-avatar img {
  width: 100%;
  height: 100%;
  object-fit: cover;
}

.user-cell div:last-child {
  display: flex;
  flex-direction: column;
  gap: 3px;
}

.user-cell span {
  color: var(--app-text-muted);
  font-size: 12px;
}

.role-preview,
.status-preview {
  display: flex;
  gap: 6px;
  margin-top: 8px;
}

.table-actions {
  display: flex;
  flex-wrap: wrap;
  gap: 4px;
}

.pagination-wrap {
  display: flex;
  justify-content: flex-end;
  margin-top: 16px;
}

.full-select {
  width: 100%;
}

@media (max-width: 820px) {
  .admin-summary,
  .toolbar {
    grid-template-columns: 1fr;
  }

  .status-filter {
    width: 100%;
  }
}
</style>