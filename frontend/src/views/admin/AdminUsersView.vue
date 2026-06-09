<script setup lang="ts">
import AdminShell from '../../components/admin/AdminShell.vue';
import AdminUsersPanel from '../../components/admin/AdminUsersPanel.vue';
</script>

<template>
  <AdminShell active="users" title="用户管理">
    <AdminUsersPanel />
  </AdminShell>
</template>
