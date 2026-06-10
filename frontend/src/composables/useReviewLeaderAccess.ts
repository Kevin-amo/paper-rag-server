import { ref } from 'vue';
import { listLeaderGroups } from '../api/reviewLeader';

const canAccessLeaderWorkspace = ref(false);
const leaderWorkspaceAccessLoading = ref(false);

export function useReviewLeaderAccess() {
  async function refreshLeaderWorkspaceAccess() {
    leaderWorkspaceAccessLoading.value = true;
    try {
      const groups = await listLeaderGroups();
      canAccessLeaderWorkspace.value = groups.length > 0;
    } catch {
      canAccessLeaderWorkspace.value = false;
    } finally {
      leaderWorkspaceAccessLoading.value = false;
    }
  }

  return {
    canAccessLeaderWorkspace,
    leaderWorkspaceAccessLoading,
    refreshLeaderWorkspaceAccess,
  };
}