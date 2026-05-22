import { ref } from 'vue';
import { searchLiterature } from '../api/literature';
import { getErrorMessage } from '../api/http';
import type { LiteratureSearchResult } from '../types';

export function useLiteratureSearch() {
  const literatureLoading = ref(false);
  const literatureItems = ref<LiteratureSearchResult[]>([]);
  const literatureErrorMessage = ref('');
  const lastLiteratureQuery = ref('');
  const hasSearchedLiterature = ref(false);

  async function search(query: string) {
    const normalizedQuery = query.trim();
    if (!normalizedQuery || literatureLoading.value) {
      return;
    }

    literatureLoading.value = true;
    literatureErrorMessage.value = '';
    lastLiteratureQuery.value = normalizedQuery;
    hasSearchedLiterature.value = true;

    try {
      const response = await searchLiterature({ query: normalizedQuery });
      literatureItems.value = response.items ?? [];
    } catch (error) {
      literatureItems.value = [];
      literatureErrorMessage.value = getErrorMessage(error);
    } finally {
      literatureLoading.value = false;
    }
  }

  function clear() {
    literatureItems.value = [];
    literatureErrorMessage.value = '';
    lastLiteratureQuery.value = '';
    hasSearchedLiterature.value = false;
  }

  return {
    literatureLoading,
    literatureItems,
    literatureErrorMessage,
    lastLiteratureQuery,
    hasSearchedLiterature,
    search,
    clear,
  };
}