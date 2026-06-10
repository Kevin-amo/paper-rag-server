<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue';
import { ElMessage } from 'element-plus';
import { getErrorMessage } from '../../../api/http';
import { listAdminUsers } from '../../../api/adminUsers';
import {
  createReviewBatch,
  createReviewGroup,
  listReviewBatches,
  listReviewGroupMembers,
  listReviewGroups,
  replaceReviewGroupMembers,
  updateReviewBatch,
  updateReviewGroup,
} from '../../../api/adminReviews';
import type {
  AdminUser,
  ReviewBatch,
  ReviewBatchPayload,
  ReviewGroup,
  ReviewGroupMember,
  ReviewGroupPayload,
} from '../../../types';

type DialogMode = 'create' | 'edit';

const loading = ref(false);
const saving = ref(false);
const batches = ref<ReviewBatch[]>([]);
const groups = ref<ReviewGroup[]>([]);
const members = ref<ReviewGroupMember[]>([]);
const reviewerCandidates = ref<AdminUser[]>([]);
const selectedBatchId = ref<string>('');
const selectedGroup = ref<ReviewGroup | null>(null);
const batchDialogVisible = ref(false);
const groupDialogVisible = ref(false);
const memberDrawerVisible = ref(false);
const batchDialogMode = ref<DialogMode>('create');
const groupDialogMode = ref<DialogMode>('create');
const editingBatch = ref<ReviewBatch | null>(null);
const editingGroup = ref<ReviewGroup | null>(null);

const batchForm = reactive({
  name: '',
  description: '',
  status: 'ACTIVE',
  startsAt: '',
  endsAt: '',
});

const groupForm = reactive({
  name: '',
  leaderUserId: '',
  status: 'ACTIVE',
});

const memberForm = reactive({
  leaderUserId: '',
  memberUserIds: [] as string[],
});

const selectedBatch = computed(() => batches.value.find((batch) => batch.id === selectedBatchId.value) || null);
const activeBatchCount = computed(() => batches.value.filter((batch) => batch.status === 'ACTIVE').length);
const activeGroupCount = computed(() => groups.value.filter((group) => group.status === 'ACTIVE').length);
const activeMemberCount = computed(() => members.value.filter((member) => member.status === 'ACTIVE').length);
const reviewerOptions = computed(() =>
  reviewerCandidates.value.filter((user) => user.status === 'ACTIVE' && user.roles.includes('REVIEWER')),
);
const batchDialogTitle = computed(() => (batchDialogMode.value === 'create' ? '新建评审批次' : '编辑评审批次'));
const groupDialogTitle = computed(() => (groupDialogMode.value === 'create' ? '新建评审小组' : '编辑评审小组'));

onMounted(async () => {
  await Promise.all([loadBatches(), loadReviewerCandidates()]);
});

async function loadBatches() {
  loading.value = true;
  try {
    const result = await listReviewBatches({ page: 0, size: 100 });
    batches.value = result.items;
    if (!selectedBatchId.value && batches.value.length) {
      selectedBatchId.value = batches.value[0].id;
    }
    await loadGroups();
  } catch (error) {
    ElMessage.error(getErrorMessage(error));
  } finally {
    loading.value = false;
  }
}

async function loadGroups() {
  try {
    groups.value = await listReviewGroups(selectedBatchId.value || undefined);
    if (selectedGroup.value && !groups.value.some((group) => group.id === selectedGroup.value?.id)) {
      selectedGroup.value = null;
      members.value = [];
    }
  } catch (error) {
    ElMessage.error(getErrorMessage(error));
  }
}

async function loadReviewerCandidates() {
  try {
    const result = await listAdminUsers({ page: 0, size: 100, status: 'ACTIVE' });
    reviewerCandidates.value = result.items;
  } catch (error) {
    ElMessage.error(getErrorMessage(error));
  }
}

async function handleBatchChange() {
  selectedGroup.value = null;
  members.value = [];
  await loadGroups();
}

async function selectBatch(batch: ReviewBatch) {
  selectedBatchId.value = batch.id;
  await handleBatchChange();
}

function openCreateBatchDialog() {
  batchDialogMode.value = 'create';
  editingBatch.value = null;
  Object.assign(batchForm, { name: '', description: '', status: 'ACTIVE', startsAt: '', endsAt: '' });
  batchDialogVisible.value = true;
}

function openEditBatchDialog(batch: ReviewBatch) {
  batchDialogMode.value = 'edit';
  editingBatch.value = batch;
  Object.assign(batchForm, {
    name: batch.name,
    description: batch.description || '',
    status: batch.status || 'ACTIVE',
    startsAt: toDateTimeInput(batch.startsAt),
    endsAt: toDateTimeInput(batch.endsAt),
  });
  batchDialogVisible.value = true;
}

async function saveBatch() {
  if (!batchForm.name.trim()) {
    ElMessage.warning('请输入批次名称');
    return;
  }
  saving.value = true;
  try {
    const payload: ReviewBatchPayload = {
      name: batchForm.name.trim(),
      description: batchForm.description.trim() || null,
      status: batchForm.status,
      startsAt: toPayloadDateTime(batchForm.startsAt),
      endsAt: toPayloadDateTime(batchForm.endsAt),
    };
    const batch = batchDialogMode.value === 'create'
      ? await createReviewBatch(payload)
      : await updateReviewBatch(editingBatch.value!.id, payload);
    selectedBatchId.value = batch.id;
    batchDialogVisible.value = false;
    ElMessage.success(batchDialogMode.value === 'create' ? '评审批次已创建' : '评审批次已更新');
    await loadBatches();
  } catch (error) {
    ElMessage.error(getErrorMessage(error));
  } finally {
    saving.value = false;
  }
}

function openCreateGroupDialog() {
  if (!selectedBatchId.value) {
    ElMessage.warning('请先选择批次');
    return;
  }
  groupDialogMode.value = 'create';
  editingGroup.value = null;
  Object.assign(groupForm, { name: '', leaderUserId: '', status: 'ACTIVE' });
  groupDialogVisible.value = true;
}

function openEditGroupDialog(group: ReviewGroup) {
  groupDialogMode.value = 'edit';
  editingGroup.value = group;
  Object.assign(groupForm, {
    name: group.name,
    leaderUserId: group.leaderUserId,
    status: group.status || 'ACTIVE',
  });
  groupDialogVisible.value = true;
}

async function saveGroup() {
  if (!selectedBatchId.value) {
    ElMessage.warning('请先选择批次');
    return;
  }
  if (!groupForm.name.trim()) {
    ElMessage.warning('请输入小组名称');
    return;
  }
  if (!groupForm.leaderUserId) {
    ElMessage.warning('请选择组长');
    return;
  }
  saving.value = true;
  try {
    const payload: ReviewGroupPayload = {
      batchId: selectedBatchId.value,
      name: groupForm.name.trim(),
      leaderUserId: groupForm.leaderUserId,
      status: groupForm.status,
    };
    const group = groupDialogMode.value === 'create'
      ? await createReviewGroup(payload)
      : await updateReviewGroup(editingGroup.value!.id, payload);
    groupDialogVisible.value = false;
    selectedGroup.value = group;
    ElMessage.success(groupDialogMode.value === 'create' ? '评审小组已创建' : '评审小组已更新');
    await loadGroups();
    await openMemberDrawer(group);
  } catch (error) {
    ElMessage.error(getErrorMessage(error));
  } finally {
    saving.value = false;
  }
}

async function openMemberDrawer(group: ReviewGroup) {
  selectedGroup.value = group;
  memberDrawerVisible.value = true;
  memberForm.leaderUserId = group.leaderUserId;
  try {
    members.value = await listReviewGroupMembers(group.id);
    memberForm.leaderUserId = group.leaderUserId;
    memberForm.memberUserIds = members.value.map((member) => member.userId);
  } catch (error) {
    ElMessage.error(getErrorMessage(error));
  }
}

async function saveMembers() {
  if (!selectedGroup.value) return;
  if (!memberForm.leaderUserId) {
    ElMessage.warning('请选择组长');
    return;
  }
  const memberIds = new Set(memberForm.memberUserIds);
  memberIds.add(memberForm.leaderUserId);
  saving.value = true;
  try {
    members.value = await replaceReviewGroupMembers(selectedGroup.value.id, {
      leaderUserId: memberForm.leaderUserId,
      memberUserIds: [...memberIds],
    });
    ElMessage.success('小组成员已更新');
    memberDrawerVisible.value = false;
    await loadGroups();
  } catch (error) {
    ElMessage.error(getErrorMessage(error));
  } finally {
    saving.value = false;
  }
}

function displayUser(userId: string | null | undefined) {
  if (!userId) return '-';
  const user = reviewerCandidates.value.find((item) => item.id === userId);
  return user?.displayName || user?.username || userId;
}

function formatDate(value: string | null) {
  return value ? new Date(value).toLocaleString() : '-';
}

function toDateTimeInput(value: string | null) {
  return value ? new Date(value).toISOString().slice(0, 19) : '';
}

function toPayloadDateTime(value: string) {
  return value ? new Date(value).toISOString() : null;
}
</script>

<template>
  <section class="review-config-panel" v-loading="loading">
    <div class="config-summary">
      <div class="config-card">
        <span>批次数量</span>
        <strong>{{ batches.length }}</strong>
      </div>
      <div class="config-card">
        <span>启用批次</span>
        <strong>{{ activeBatchCount }}</strong>
      </div>
      <div class="config-card">
        <span>当前小组</span>
        <strong>{{ groups.length }}</strong>
      </div>
      <div class="config-card">
        <span>启用小组</span>
        <strong>{{ activeGroupCount }}</strong>
      </div>
    </div>

    <div class="config-layout">
      <section class="config-section">
        <div class="section-header">
          <div>
            <h3>评审批次</h3>
            <p>admin 只负责批次、规则、小组与组长配置。</p>
          </div>
          <el-button type="primary" @click="openCreateBatchDialog">新建批次</el-button>
        </div>

        <el-table :data="batches" class="config-table" highlight-current-row @row-click="selectBatch">
          <el-table-column label="批次" min-width="180" show-overflow-tooltip>
            <template #default="{ row }">
              <div class="primary-cell">
                <strong>{{ row.name }}</strong>
                <span>{{ row.description || '无描述' }}</span>
              </div>
            </template>
          </el-table-column>
          <el-table-column label="状态" width="110">
            <template #default="{ row }">
              <el-tag :type="row.status === 'ACTIVE' ? 'success' : row.status === 'DRAFT' ? 'info' : 'warning'" effect="plain">
                {{ row.status }}
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column label="更新时间" width="170">
            <template #default="{ row }">{{ formatDate(row.updatedAt) }}</template>
          </el-table-column>
          <el-table-column label="操作" width="96" align="right">
            <template #default="{ row }">
              <el-button size="small" text type="primary" @click.stop="openEditBatchDialog(row)">编辑</el-button>
            </template>
          </el-table-column>
        </el-table>
      </section>

      <section class="config-section">
        <div class="section-header">
          <div>
            <h3>评审小组</h3>
            <p>{{ selectedBatch ? `当前批次：${selectedBatch.name}` : '请选择批次后配置小组' }}</p>
          </div>
          <el-button type="primary" :disabled="!selectedBatchId" @click="openCreateGroupDialog">新建小组</el-button>
        </div>

        <el-table :data="groups" class="config-table">
          <el-table-column label="小组" min-width="170" show-overflow-tooltip>
            <template #default="{ row }">
              <div class="primary-cell">
                <strong>{{ row.name }}</strong>
                <span>{{ row.taskCount }} 个任务 · {{ row.memberCount }} 名成员</span>
              </div>
            </template>
          </el-table-column>
          <el-table-column label="组长" min-width="160" show-overflow-tooltip>
            <template #default="{ row }">{{ row.leaderDisplayName || row.leaderUsername || displayUser(row.leaderUserId) }}</template>
          </el-table-column>
          <el-table-column label="状态" width="110">
            <template #default="{ row }">
              <el-tag :type="row.status === 'ACTIVE' ? 'success' : 'info'" effect="plain">{{ row.status }}</el-tag>
            </template>
          </el-table-column>
          <el-table-column label="操作" width="190" align="right">
            <template #default="{ row }">
              <el-button size="small" text type="primary" @click="openEditGroupDialog(row)">编辑</el-button>
              <el-button size="small" text type="primary" @click="openMemberDrawer(row)">成员</el-button>
            </template>
          </el-table-column>
        </el-table>
      </section>
    </div>

    <el-dialog v-model="batchDialogVisible" :title="batchDialogTitle" width="520px">
      <el-form label-position="top">
        <el-form-item label="批次名称" required>
          <el-input v-model="batchForm.name" placeholder="例如 2026 春季论文评审" />
        </el-form-item>
        <el-form-item label="说明">
          <el-input v-model="batchForm.description" type="textarea" :rows="3" placeholder="批次说明" />
        </el-form-item>
        <el-form-item label="状态">
          <el-select v-model="batchForm.status" class="full-select">
            <el-option label="草稿" value="DRAFT" />
            <el-option label="启用" value="ACTIVE" />
            <el-option label="关闭" value="CLOSED" />
            <el-option label="归档" value="ARCHIVED" />
          </el-select>
        </el-form-item>
        <div class="form-grid">
          <el-form-item label="开始时间">
            <el-input v-model="batchForm.startsAt" type="datetime-local" />
          </el-form-item>
          <el-form-item label="结束时间">
            <el-input v-model="batchForm.endsAt" type="datetime-local" />
          </el-form-item>
        </div>
      </el-form>
      <template #footer>
        <el-button @click="batchDialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="saveBatch">保存</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="groupDialogVisible" :title="groupDialogTitle" width="520px">
      <el-form label-position="top">
        <el-form-item label="小组名称" required>
          <el-input v-model="groupForm.name" placeholder="例如 第一评审组" />
        </el-form-item>
        <el-form-item label="组长" required>
          <el-select v-model="groupForm.leaderUserId" filterable class="full-select" placeholder="选择具备 REVIEWER 角色的用户">
            <el-option
              v-for="user in reviewerOptions"
              :key="user.id"
              :label="user.displayName || user.username"
              :value="user.id"
            >
              <span>{{ user.displayName || user.username }}</span>
              <small class="option-meta">{{ user.username }}</small>
            </el-option>
          </el-select>
        </el-form-item>
        <el-form-item label="状态">
          <el-select v-model="groupForm.status" class="full-select">
            <el-option label="启用" value="ACTIVE" />
            <el-option label="停用" value="DISABLED" />
          </el-select>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="groupDialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="saveGroup">保存</el-button>
      </template>
    </el-dialog>

    <el-drawer v-model="memberDrawerVisible" title="小组成员" size="480px">
      <template v-if="selectedGroup">
        <div class="drawer-heading">
          <strong>{{ selectedGroup.name }}</strong>
          <span>组长必须保留在有效成员中。</span>
        </div>
        <el-form label-position="top">
          <el-form-item label="组长" required>
            <el-select v-model="memberForm.leaderUserId" filterable class="full-select">
              <el-option
                v-for="user in reviewerOptions"
                :key="user.id"
                :label="user.displayName || user.username"
                :value="user.id"
              />
            </el-select>
          </el-form-item>
          <el-form-item label="组内评审员">
            <el-select v-model="memberForm.memberUserIds" multiple filterable class="full-select" placeholder="选择本组成员">
              <el-option
                v-for="user in reviewerOptions"
                :key="user.id"
                :label="user.displayName || user.username"
                :value="user.id"
              />
            </el-select>
          </el-form-item>
        </el-form>
        <div class="member-list">
          <div class="member-list-title">当前有效成员：{{ activeMemberCount }}</div>
          <el-tag v-for="member in members" :key="member.id" effect="plain" class="member-tag">
            {{ member.displayName || member.username || member.userId }} · {{ member.memberRole }}
          </el-tag>
        </div>
      </template>
      <template #footer>
        <el-button @click="memberDrawerVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="saveMembers">保存成员</el-button>
      </template>
    </el-drawer>
  </section>
</template>

<style scoped>
.review-config-panel {
  display: grid;
  gap: 16px;
}

.config-summary {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 12px;
}

.config-card,
.config-section {
  border: 1px solid #dde3ee;
  border-radius: 10px;
  background: #fff;
  box-shadow: 0 10px 26px rgba(16, 24, 40, 0.04);
}

.config-card {
  padding: 14px 16px;
}

.config-card span,
.primary-cell span,
.section-header p,
.drawer-heading span {
  color: #667085;
  font-size: 13px;
}

.config-card strong {
  display: block;
  margin-top: 8px;
  color: #101828;
  font-size: 24px;
}

.config-layout {
  display: grid;
  grid-template-columns: minmax(0, 1fr) minmax(0, 1.2fr);
  gap: 16px;
}

.config-section {
  overflow: hidden;
}

.section-header {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 16px;
  border-bottom: 1px solid #edf1f7;
  padding: 16px;
}

.section-header h3 {
  margin: 0 0 6px;
  color: #101828;
  font-size: 16px;
}

.section-header p {
  margin: 0;
}

.config-table {
  width: 100%;
}

.primary-cell {
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.primary-cell strong {
  color: #101828;
  font-weight: 750;
}

.full-select {
  width: 100%;
}

.form-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 12px;
}

.option-meta {
  float: right;
  color: #98a2b3;
}

.drawer-heading {
  display: grid;
  gap: 5px;
  margin-bottom: 18px;
}

.member-list {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  border-top: 1px solid #edf1f7;
  margin-top: 16px;
  padding-top: 16px;
}

.member-list-title {
  width: 100%;
  color: #344054;
  font-weight: 700;
}

.member-tag {
  max-width: 100%;
}

@media (max-width: 1080px) {
  .config-summary,
  .config-layout,
  .form-grid {
    grid-template-columns: 1fr;
  }
}
</style>