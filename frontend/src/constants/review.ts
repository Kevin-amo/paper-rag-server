export const statusLabelMap: Record<string, string> = {
  PENDING: '待评审',
  REVIEWING: '评审中',
  COMPLETED: '已完成',
  PENDING_ASSIGNMENT: '待分配',
  ASSIGNED: '待评审',
  IN_REVIEW: '评审中',
  SUBMITTED: '已提交',
  RETURNED: '已退回',
  CANCELLED: '已取消',
  CONSENSUS_CONFIRMED: '最终已确认',
  NEEDS_REVIEW: '需复核',
};

export const riskTypeMap: Record<string, 'success' | 'warning' | 'danger' | 'info'> = {
  LOW: 'success',
  MEDIUM: 'warning',
  HIGH: 'danger',
  CRITICAL: 'danger',
};

export const riskStatusMap: Record<string, string> = {
  OPEN: '待处理',
  CONFIRMED: '已确认',
  IGNORED: '已忽略',
  RESOLVED: '已解决',
};

export function statusLabel(status: string) {
  return statusLabelMap[status] ?? status;
}

export function riskStatusLabel(status: string) {
  return riskStatusMap[status] ?? status;
}
