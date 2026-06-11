import { readFileSync } from 'node:fs';

const shell = readFileSync(new URL('../src/components/admin/AdminShell.vue', import.meta.url), 'utf8');
const reviews = readFileSync(new URL('../src/views/admin/AdminReviewDashboardView.vue', import.meta.url), 'utf8');
const usersView = readFileSync(new URL('../src/views/admin/AdminUsersView.vue', import.meta.url), 'utf8');
const usersPanel = readFileSync(new URL('../src/components/admin/AdminUsersPanel.vue', import.meta.url), 'utf8');
const usersComposable = readFileSync(new URL('../src/composables/useAdminUsers.ts', import.meta.url), 'utf8');
const globalStyle = [
  readFileSync(new URL('../src/style.css', import.meta.url), 'utf8'),
  readFileSync(new URL('../src/styles/reset.css', import.meta.url), 'utf8'),
].join('\n');

const requiredMenuMarkers = [
  "key: 'users'",
  "key: 'config'",
  "key: 'tasks'",
  "key: 'criteria'",
];
const requiredReviewTabs = [
  'name="config"',
  'name="tasks"',
  'name="criteria"',
];

const missing = [];
for (const marker of requiredMenuMarkers) {
  if (!shell.includes(marker)) missing.push(`AdminShell missing menu marker: ${marker}`);
}
for (const tab of requiredReviewTabs) {
  if (!reviews.includes(tab)) missing.push(`AdminReviewDashboardView missing tab marker: ${tab}`);
}
if (shell.includes("key: 'archive'") || reviews.includes('name="archive"')) {
  missing.push('Admin review archive/result-view entry must not be present');
}
if (!shell.includes('admin-layout') || !shell.includes('admin-sidebar')) {
  missing.push('AdminShell missing conventional sidebar layout classes');
}
if (!reviews.includes('route.query.tab') || !reviews.includes('router.replace')) {
  missing.push('AdminReviewDashboardView missing query-driven tab synchronization');
}
if (usersPanel.includes('<el-drawer') || usersPanel.includes('admin-users-drawer') || usersView.includes('v-model="panelVisible"')) {
  missing.push('User management must render inline, not as a drawer controlled by v-model');
}
if (usersPanel.includes('user-avatar') || usersPanel.includes('avatarUrl') || usersPanel.includes('<img')) {
  missing.push('User management table must not render user avatars');
}
if (!usersPanel.includes('users-panel') || !usersPanel.includes('用户列表')) {
  missing.push('User management panel missing inline management layout markers');
}
if (!/html\s*\{[^}]*scrollbar-gutter:\s*stable[^}]*overflow-y:\s*auto/s.test(globalStyle)) {
  missing.push('Global layout must set html scrollbar-gutter: stable together with overflow-y: auto to prevent admin navigation shift');
}
if (!shell.includes('overflow-x: hidden') || !shell.includes('overflow-x: auto')) {
  missing.push('AdminShell must prevent page-level horizontal scrolling while allowing content-level horizontal scrolling');
}
if (usersView.includes(':loading="loading"') || usersView.includes('v-loading="loading"')) {
  missing.push('User management must not show a full admin shell loading overlay on every navigation');
}
if (!usersComposable.includes('adminUsersStore') || !usersComposable.includes('loaded') || !usersPanel.includes('!admin.loaded.value')) {
  missing.push('User management must cache its list state and skip automatic reloads after the first visit');
}

if (missing.length) {
  console.error(missing.join('\n'));
  process.exit(1);
}

console.log('Admin sidebar static checks passed.');
