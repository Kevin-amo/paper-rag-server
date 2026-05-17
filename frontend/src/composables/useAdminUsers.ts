import { computed, reactive, ref } from 'vue';
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
import type { AdminUser, UserRole, UserStatus } from '../types';

export type AdminUserFormMode = 'create' | 'edit';

export function useAdminUsers() {
  const loading = ref(false);
  const saving = ref(false);
  const deletingUserId = ref<string | null>(null);
  const users = ref<AdminUser[]>([]);
  const keyword = ref('');
  const statusFilter = ref<UserStatus | ''>('');
  const formDialogVisible = ref(false);
  const passwordDialogVisible = ref(false);
  const selectedUser = ref<AdminUser | null>(null);
  const formMode = ref<AdminUserFormMode>('create');
  const pagination = reactive({ page: 0, size: 20, total: 0 });

  const userForm = reactive({
    username: '',
    password: '',
    displayName: '',
    email: '',
    roles: ['USER'] as UserRole[],
  });
  const passwordForm = reactive({ password: '' });

  const activeCount = computed(() => users.value.filter((user) => user.status === 'ACTIVE').length);
  const adminCount = computed(() => users.value.filter((user) => user.roles.includes('ADMIN')).length);
  const disabledCount = computed(() => users.value.filter((user) => user.status === 'DISABLED').length);
  const dialogTitle = computed(() => (formMode.value === 'create' ? '新建用户' : '编辑用户'));

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
    Object.assign(userForm, { username: '', password: '', displayName: '', email: '', roles: ['USER'] as UserRole[] });
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

  async function saveUser() {
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

  async function changeRoles(user: AdminUser, roles: UserRole[]) {
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

  async function changeStatus(user: AdminUser, status: UserStatus) {
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

  async function resetPassword() {
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

  async function removeUser(user: AdminUser) {
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

  return {
    loading,
    saving,
    deletingUserId,
    users,
    keyword,
    statusFilter,
    formDialogVisible,
    passwordDialogVisible,
    selectedUser,
    formMode,
    pagination,
    userForm,
    passwordForm,
    activeCount,
    adminCount,
    disabledCount,
    dialogTitle,
    loadUsers,
    resetUserForm,
    openCreateDialog,
    openEditDialog,
    saveUser,
    changeRoles,
    changeStatus,
    openPasswordDialog,
    resetPassword,
    removeUser,
  };
}