<script lang="ts">
export default {
  name: 'AdminUsersPanel',
};
</script>

<script setup lang="ts">
import { onBeforeUnmount, onMounted, ref } from 'vue';
import { ElMessage, ElMessageBox } from 'element-plus';
import { EditPen, MoreFilled } from '@element-plus/icons-vue';
import StatusTag from '../common/StatusTag.vue';
import RoleTag from '../common/RoleTag.vue';
import { useAdminUsers } from '../../composables/useAdminUsers';
import type { AdminUser, UserRole, UserStatus } from '../../types';

type UserActionCommand = 'edit' | 'reset-password' | 'delete';

const admin = useAdminUsers();
const roleOptions: Array<{ label: string; value: UserRole }> = [
  { label: '管理员', value: 'ADMIN' },
  { label: '评审员', value: 'REVIEWER' },
  { label: '普通用户', value: 'USER' },
];
const statusOptions: Array<{ label: string; value: UserStatus }> = [
  { label: '启用', value: 'ACTIVE' },
  { label: '禁用', value: 'DISABLED' },
];
const roleEditorUserId = ref<string | null>(null);
const statusEditorUserId = ref<string | null>(null);
const roleDraft = ref<UserRole[]>([]);
const statusDraft = ref<UserStatus>('ACTIVE');
const inlineSavingUserId = ref<string | null>(null);

onMounted(() => {
  if (!admin.loaded.value) {
    void admin.loadUsers(0);
  }
  window.addEventListener('click', closeInlineEditors);
});

onBeforeUnmount(() => {
  window.removeEventListener('click', closeInlineEditors);
});

function formatDate(value: string | null) {
  return value ? new Date(value).toLocaleString() : '-';
}

function closeInlineEditors() {
  closeRoleEditor();
  closeStatusEditor();
}

function openRoleEditor(user: AdminUser) {
  statusEditorUserId.value = null;
  roleDraft.value = [...user.roles];
  roleEditorUserId.value = user.id;
}

function closeRoleEditor() {
  roleEditorUserId.value = null;
  roleDraft.value = [];
}

async function saveRoleEditor(user: AdminUser) {
  if (!roleDraft.value.length) {
    ElMessage.warning('请至少保留一个角色');
    return;
  }
  inlineSavingUserId.value = user.id;
  try {
    await admin.changeRoles(user, [...roleDraft.value]);
    closeRoleEditor();
  } finally {
    inlineSavingUserId.value = null;
  }
}

function openStatusEditor(user: AdminUser) {
  roleEditorUserId.value = null;
  statusDraft.value = user.status;
  statusEditorUserId.value = user.id;
}

function closeStatusEditor() {
  statusEditorUserId.value = null;
}

async function saveStatusEditor(user: AdminUser) {
  inlineSavingUserId.value = user.id;
  try {
    await admin.changeStatus(user, statusDraft.value);
    closeStatusEditor();
  } finally {
    inlineSavingUserId.value = null;
  }
}

async function handleUserAction(command: UserActionCommand, user: AdminUser) {
  closeInlineEditors();
  if (command === 'edit') {
    admin.openEditDialog(user);
    return;
  }
  if (command === 'reset-password') {
    admin.openPasswordDialog(user);
    return;
  }

  try {
    await ElMessageBox.confirm('确认删除这个用户吗？此操作不可恢复。', '删除用户', {
      confirmButtonText: '删除',
      cancelButtonText: '取消',
      confirmButtonClass: 'danger-confirm-button',
      type: 'warning',
    });
    await admin.removeUser(user);
  } catch {
    // User cancelled the confirmation dialog.
  }
}
</script>

<template>
  <section class="users-panel">
    <div class="panel-heading">
      <div>
        <h2>用户列表</h2>
        <p>管理账号、角色、状态和密码重置。</p>
      </div>
      <el-button type="primary" @click="admin.openCreateDialog">新建用户</el-button>
    </div>

    <section class="admin-summary">
      <div class="summary-card accent-blue">
        <span class="metric-icon">U</span>
        <div>
          <span>当前页用户</span>
          <strong>{{ admin.users.value.length }}</strong>
        </div>
      </div>
      <div class="summary-card accent-green">
        <span class="metric-icon">A</span>
        <div>
          <span>启用账号</span>
          <strong>{{ admin.activeCount.value }}</strong>
        </div>
      </div>
      <div class="summary-card accent-indigo">
        <span class="metric-icon">M</span>
        <div>
          <span>管理员</span>
          <strong>{{ admin.adminCount.value }}</strong>
        </div>
      </div>
      <div class="summary-card accent-red">
        <span class="metric-icon">D</span>
        <div>
          <span>禁用账号</span>
          <strong>{{ admin.disabledCount.value }}</strong>
        </div>
      </div>
    </section>

    <div class="toolbar">
      <el-input
        v-model="admin.keyword.value"
        clearable
        placeholder="搜索用户名 / 昵称 / 邮箱"
        @keyup.enter="admin.loadUsers(0)"
      />
      <el-select v-model="admin.statusFilter.value" clearable placeholder="状态" class="status-filter">
        <el-option label="启用" value="ACTIVE" />
        <el-option label="禁用" value="DISABLED" />
      </el-select>
      <el-button @click="admin.loadUsers(0)">搜索</el-button>
      <el-button type="primary" @click="admin.loadUsers(admin.pagination.page)">刷新</el-button>
    </div>

    <el-table :data="admin.users.value" :loading="admin.loading.value" class="users-table">
      <el-table-column label="用户" min-width="220">
        <template #default="{ row }">
          <div class="user-cell">
            <strong>{{ row.displayName || row.username }}</strong>
            <span>{{ row.username }}</span>
          </div>
        </template>
      </el-table-column>
      <el-table-column prop="email" label="邮箱" min-width="190" show-overflow-tooltip>
        <template #default="{ row }">{{ row.email || '-' }}</template>
      </el-table-column>
      <el-table-column label="角色" min-width="240">
        <template #default="{ row }">
          <div class="inline-edit-cell">
            <div class="tag-list">
              <RoleTag v-for="role in row.roles" :key="role" :role="role" />
            </div>
            <el-popover
              :visible="roleEditorUserId === row.id"
              placement="bottom-start"
              trigger="manual"
              width="280"
              popper-class="admin-inline-popover"
            >
              <template #reference>
                <el-button
                  circle
                  text
                  class="cell-edit-button"
                  :icon="EditPen"
                  aria-label="编辑角色"
                  title="编辑角色"
                  @click.stop="openRoleEditor(row)"
                />
              </template>
              <div class="inline-editor" @click.stop @keydown.esc.stop="closeRoleEditor">
                <div class="inline-editor-title">调整角色</div>
                <el-checkbox-group v-model="roleDraft" class="role-choice-list">
                  <el-checkbox-button v-for="option in roleOptions" :key="option.value" :value="option.value">
                    {{ option.label }}
                  </el-checkbox-button>
                </el-checkbox-group>
                <div class="inline-editor-actions">
                  <el-button size="small" @click="closeRoleEditor">取消</el-button>
                  <el-button
                    size="small"
                    type="primary"
                    :loading="inlineSavingUserId === row.id"
                    @click="saveRoleEditor(row)"
                  >
                    保存
                  </el-button>
                </div>
              </div>
            </el-popover>
          </div>
        </template>
      </el-table-column>
      <el-table-column label="状态" width="150">
        <template #default="{ row }">
          <div class="inline-edit-cell compact">
            <StatusTag :status="row.status" />
            <el-popover
              :visible="statusEditorUserId === row.id"
              placement="bottom-start"
              trigger="manual"
              width="220"
              popper-class="admin-inline-popover"
            >
              <template #reference>
                <el-button
                  circle
                  text
                  class="cell-edit-button"
                  :icon="EditPen"
                  aria-label="编辑状态"
                  title="编辑状态"
                  @click.stop="openStatusEditor(row)"
                />
              </template>
              <div class="inline-editor" @click.stop @keydown.esc.stop="closeStatusEditor">
                <div class="inline-editor-title">调整状态</div>
                <el-radio-group v-model="statusDraft" class="status-choice-list">
                  <el-radio-button v-for="option in statusOptions" :key="option.value" :value="option.value">
                    {{ option.label }}
                  </el-radio-button>
                </el-radio-group>
                <div class="inline-editor-actions">
                  <el-button size="small" @click="closeStatusEditor">取消</el-button>
                  <el-button
                    size="small"
                    type="primary"
                    :loading="inlineSavingUserId === row.id"
                    @click="saveStatusEditor(row)"
                  >
                    保存
                  </el-button>
                </div>
              </div>
            </el-popover>
          </div>
        </template>
      </el-table-column>
      <el-table-column label="最近登录" min-width="170">
        <template #default="{ row }">{{ formatDate(row.lastLoginAt) }}</template>
      </el-table-column>
      <el-table-column label="创建时间" min-width="170">
        <template #default="{ row }">{{ formatDate(row.createdAt) }}</template>
      </el-table-column>
      <el-table-column label="操作" width="110" fixed="right" align="center">
        <template #default="{ row }">
          <el-dropdown
            trigger="click"
            placement="bottom-end"
            popper-class="user-actions-menu"
            @command="(command: UserActionCommand) => handleUserAction(command, row)"
          >
            <button class="action-menu-trigger" type="button" aria-label="更多操作">
              <el-icon><MoreFilled /></el-icon>
            </button>
            <template #dropdown>
              <el-dropdown-menu>
                <el-dropdown-item command="edit">编辑</el-dropdown-item>
                <el-dropdown-item command="reset-password">重置密码</el-dropdown-item>
                <el-dropdown-item
                  command="delete"
                  divided
                  class="danger-menu-item"
                  :disabled="admin.deletingUserId.value === row.id"
                >
                  删除
                </el-dropdown-item>
              </el-dropdown-menu>
            </template>
          </el-dropdown>
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
            <el-option label="评审员" value="REVIEWER" />
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
  </section>
</template>

<style scoped>
.users-panel {
  display: grid;
  gap: 16px;
  border: 1px solid #dde3ee;
  border-radius: 10px;
  padding: 18px;
  background: #fff;
  box-shadow: 0 12px 30px rgba(16, 24, 40, 0.05);
}

.panel-heading {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  border-bottom: 1px solid #edf1f7;
  padding-bottom: 14px;
}

.panel-heading h2 {
  margin: 0;
  color: #101828;
  font-size: 18px;
}

.panel-heading p {
  margin: 6px 0 0;
  color: #667085;
  font-size: 13px;
}

.admin-summary {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 12px;
}

.summary-card {
  display: flex;
  align-items: center;
  gap: 14px;
  border: 1px solid #dde3ee;
  border-radius: 10px;
  padding: 14px;
  background: #fff;
  box-shadow: 0 10px 26px rgba(16, 24, 40, 0.04);
}

.metric-icon {
  width: 40px;
  height: 40px;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  border-radius: 999px;
  font-size: 13px;
  font-weight: 850;
}

.summary-card span {
  display: block;
  color: #667085;
  font-size: 13px;
}

.summary-card strong {
  display: block;
  margin-top: 8px;
  color: #101828;
  font-size: 24px;
  line-height: 1;
}

.accent-blue .metric-icon {
  background: #eaf2ff;
  color: #155eef;
}

.accent-green .metric-icon {
  background: #e8f8ef;
  color: #099250;
}

.accent-indigo .metric-icon {
  background: #eef2ff;
  color: #4f46e5;
}

.accent-red .metric-icon {
  background: #feecec;
  color: #d92d20;
}

.toolbar {
  display: grid;
  grid-template-columns: minmax(260px, 1fr) 150px auto auto;
  gap: 10px;
}

.status-filter {
  width: 150px;
}

.toolbar :deep([class~="el-input__wrapper"]),
.toolbar :deep([class~="el-select__wrapper"]) {
  min-height: 38px;
  border-radius: 9px;
  box-shadow: 0 0 0 1px #d0d7e2 inset;
}

.users-table {
  overflow: hidden;
  border: 1px solid #dde3ee;
  border-radius: 10px;
}

.user-cell {
  display: flex;
  flex-direction: column;
  gap: 3px;
}

.user-cell strong {
  color: #101828;
  font-weight: 700;
}

.user-cell span {
  color: #667085;
  font-size: 12px;
}

.inline-edit-cell {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 6px;
  min-height: 32px;
}

.inline-edit-cell.compact {
  justify-content: flex-start;
}

.tag-list {
  display: flex;
  min-width: 0;
  flex-wrap: wrap;
  gap: 6px;
}

.cell-edit-button {
  width: 28px;
  height: 28px;
  min-width: 28px;
  color: #667085;
  opacity: 0;
  transition:
    opacity 0.18s ease,
    background-color 0.18s ease,
    color 0.18s ease;
}

.inline-edit-cell:hover .cell-edit-button,
.cell-edit-button:focus {
  opacity: 1;
}

.cell-edit-button:hover,
.cell-edit-button:focus {
  color: var(--app-primary);
  background: var(--app-primary-soft);
}

.inline-editor {
  display: grid;
  gap: 12px;
}

.inline-editor-title {
  color: #101828;
  font-size: 13px;
  font-weight: 800;
}

.role-choice-list,
.status-choice-list {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}

.role-choice-list :deep([class~="el-checkbox-button__inner"]),
.status-choice-list :deep([class~="el-radio-button__inner"]) {
  border: 1px solid #d0d7e2;
  border-radius: 999px;
  box-shadow: none;
  font-weight: 700;
}

.role-choice-list :deep([class~="el-checkbox-button"]:first-child [class~="el-checkbox-button__inner"]),
.status-choice-list :deep([class~="el-radio-button"]:first-child [class~="el-radio-button__inner"]) {
  border-left: 1px solid #d0d7e2;
  border-radius: 999px;
}

.role-choice-list :deep([class~="el-checkbox-button"][class~="is-checked"] [class~="el-checkbox-button__inner"]),
.status-choice-list :deep([class~="el-radio-button"][class~="is-active"] [class~="el-radio-button__inner"]) {
  border-color: var(--app-primary);
  background: var(--app-primary);
  box-shadow: none;
}

.inline-editor-actions {
  display: flex;
  justify-content: flex-end;
  gap: 8px;
}

.action-menu-trigger {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  height: 32px;
  min-width: 72px;
  border: 0;
  padding: 0 8px;
  background: transparent;
  color: var(--app-primary);
  cursor: pointer;
  justify-content: center;
  box-shadow: none;
  font-weight: 800;
  font: inherit;
  line-height: 1;
  transition: color 0.16s ease;
}

.action-menu-trigger:hover,
.action-menu-trigger:focus {
  background: transparent;
  color: var(--app-primary-dark);
  outline: none;
  box-shadow: none;
}

.action-menu-trigger:focus-visible {
  outline: 2px solid rgba(0, 122, 255, 0.3);
  outline-offset: 2px;
}

.pagination-wrap {
  display: flex;
  justify-content: flex-end;
}

.full-select {
  width: 100%;
}

:global([class~="admin-inline-popover"]) {
  border: 1px solid rgba(209, 209, 214, 0.78);
  border-radius: 14px;
  box-shadow: 0 18px 45px rgba(15, 23, 42, 0.14);
}

:global([class~="user-actions-menu"]) {
  min-width: 132px;
}

:global([class~="user-actions-menu"] [class~="danger-menu-item"]) {
  color: var(--app-danger);
  font-weight: 800;
}

:global([class~="user-actions-menu"] [class~="danger-menu-item"]:not([class~="is-disabled"]):hover) {
  background: rgba(255, 59, 48, 0.1);
  color: #d70015;
}

:global([class~="danger-confirm-button"]) {
  --el-button-bg-color: var(--app-danger);
  --el-button-border-color: var(--app-danger);
  --el-button-hover-bg-color: #ff6259;
  --el-button-hover-border-color: #ff6259;
}

@media (max-width: 980px) {
  .admin-summary,
  .toolbar {
    grid-template-columns: 1fr;
  }

  .status-filter {
    width: 100%;
  }

  .panel-heading {
    align-items: flex-start;
    flex-direction: column;
  }
}
</style>
