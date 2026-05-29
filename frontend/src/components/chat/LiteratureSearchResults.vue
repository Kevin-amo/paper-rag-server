<script setup lang="ts">
import { ref } from 'vue';
import EmptyState from '../common/EmptyState.vue';
import type { LiteratureSearchResult } from '../../types';

const props = defineProps<{
  items: LiteratureSearchResult[];
  loading?: boolean;
  errorMessage?: string;
  lastQuery?: string;
  hasSearched?: boolean;
  summary?: string | null;
  inline?: boolean;
}>();

function metaText(item: LiteratureSearchResult) {
  return [
    item.authors.length ? item.authors.join(', ') : '作者未知',
    item.year ?? '年份未知',
    item.primaryCategory || item.categories[0] || '分类未知',
  ].join(' · ');
}

function visibleTags(item: LiteratureSearchResult) {
  return [item.primaryCategory, ...item.categories]
    .filter((tag): tag is string => !!tag)
    .filter((tag, index, tags) => tags.indexOf(tag) === index)
    .slice(0, 3);
}

function paperUrl(item: LiteratureSearchResult) {
  return item.url || item.doi || item.externalId || '';
}

const activeAbstract = ref<{ title: string; text: string } | null>(null);

function paperKey(item: LiteratureSearchResult, index: number) {
  return `${item.externalId || item.title || 'paper'}-${index}`;
}

function openAbstract(item: LiteratureSearchResult) {
  if (!item.abstractText) {
    return;
  }
  activeAbstract.value = {
    title: item.title || '未命名论文',
    text: item.abstractText,
  };
}

function closeAbstract() {
  activeAbstract.value = null;
}
</script>

<template>
  <section
    v-loading="props.loading"
    class="literature-results"
    :class="{ 'is-empty': !props.items.length, inline: props.inline }"
  >
    <template v-if="props.inline">
      <div v-if="props.errorMessage" class="literature-state error-state inline-state">
        <strong>文献搜索失败</strong>
        <span>{{ props.errorMessage }}</span>
      </div>

      <div v-else-if="!props.items.length" class="literature-state inline-state">
        <strong>未找到相关论文</strong>
        <span v-if="props.summary">{{ props.summary }}</span>
        <span v-else-if="props.lastQuery">关键词：{{ props.lastQuery }}</span>
      </div>

      <div v-else class="literature-inline-panel">
        <article
          v-for="(item, index) in props.items"
          :key="paperKey(item, index)"
          class="paper-card inline-paper-card"
        >
          <div class="paper-index">{{ index + 1 }}</div>
          <div class="paper-body">
            <h3>
              <a
                v-if="paperUrl(item)"
                :href="paperUrl(item)"
                target="_blank"
                rel="noreferrer"
              >
                {{ item.title || '未命名论文' }}
              </a>
              <span v-else>{{ item.title || '未命名论文' }}</span>
            </h3>
            <p class="paper-meta">{{ metaText(item) }}</p>
            <button
              v-if="item.abstractText"
              type="button"
              class="abstract-button"
              @click="openAbstract(item)"
            >
              查看摘要
            </button>
            <div class="paper-tags">
              <span v-for="tag in visibleTags(item)" :key="tag">{{ tag }}</span>
            </div>
          </div>
        </article>
      </div>
    </template>

    <template v-else>
      <EmptyState
        v-if="!props.hasSearched && !props.loading"
        title="搜索外部论文"
        description="输入关键词即可检索论文元数据。"
      />

      <div v-else-if="props.errorMessage" class="literature-state error-state">
        <strong>文献搜索失败</strong>
        <span>{{ props.errorMessage }}</span>
      </div>

      <div v-else-if="props.hasSearched && !props.loading && !props.items.length" class="literature-state">
        <strong>未找到相关论文</strong>
        <span v-if="props.lastQuery">关键词：{{ props.lastQuery }}</span>
      </div>

      <div v-else class="literature-panel">
        <article
          v-for="(item, index) in props.items"
          :key="paperKey(item, index)"
          class="paper-card"
        >
          <div class="paper-index">{{ index + 1 }}</div>
          <div class="paper-body">
            <h3>
              <a
                v-if="paperUrl(item)"
                :href="paperUrl(item)"
                target="_blank"
                rel="noreferrer"
              >
                {{ item.title || '未命名论文' }}
              </a>
              <span v-else>{{ item.title || '未命名论文' }}</span>
            </h3>
            <p class="paper-meta">{{ metaText(item) }}</p>
            <button
              v-if="item.abstractText"
              type="button"
              class="abstract-button"
              @click="openAbstract(item)"
            >
              查看摘要
            </button>
            <div class="paper-tags">
              <span v-for="tag in visibleTags(item)" :key="tag">{{ tag }}</span>
            </div>
          </div>
        </article>
      </div>
    </template>
    <Teleport to="body">
      <Transition name="abstract-modal">
        <div
          v-if="activeAbstract"
          class="abstract-overlay"
          role="presentation"
          @click.self="closeAbstract"
        >
          <section class="abstract-dialog" role="dialog" aria-modal="true" aria-labelledby="abstract-dialog-title">
            <header class="abstract-dialog-header">
              <div>
                <span>文献摘要</span>
                <h2 id="abstract-dialog-title">{{ activeAbstract.title }}</h2>
              </div>
              <button type="button" class="abstract-close" aria-label="关闭摘要弹窗" @click="closeAbstract">
                ×
              </button>
            </header>
            <p class="abstract-dialog-content">{{ activeAbstract.text }}</p>
          </section>
        </div>
      </Transition>
    </Teleport>
  </section>
</template>

<style scoped>
.literature-results {
  min-height: 0;
  overflow-y: auto;
  display: flex;
  flex: 1;
  flex-direction: column;
  padding: clamp(24px, 4vw, 44px) 0 150px;
  background: transparent;
}

.literature-results.inline {
  padding: 0;
  background: transparent;
}

.literature-results.is-empty {
  align-items: center;
  justify-content: center;
  padding: 48px 0 64px;
}

.literature-results.is-empty :deep(.empty-state) {
  width: min(820px, calc(100% - 48px));
  min-height: 220px;
  border-color: rgba(255, 255, 255, 0.76);
  background: rgba(255, 255, 255, 0.62);
  box-shadow: inset 0 1px 0 rgba(255, 255, 255, 0.78);
}

.literature-results.is-empty :deep(.empty-state p) {
  max-width: 560px;
}

.literature-results.is-empty :deep(.empty-icon) {
  background: rgba(0, 122, 255, 0.1);
}

.literature-inline-panel {
  width: 100%;
  display: grid;
  gap: 12px;
  margin-top: 14px;
}

.paper-card {
  display: grid;
  grid-template-columns: 42px minmax(0, 1fr);
  gap: 14px;
  padding: 18px;
  border: 1px solid rgba(255, 255, 255, 0.74);
  border-radius: 24px;
  background: rgba(255, 255, 255, 0.68);
  box-shadow:
    0 10px 28px rgba(15, 23, 42, 0.06),
    inset 0 1px 0 rgba(255, 255, 255, 0.78);
}

.inline-paper-card {
  padding: 14px;
  border-radius: 18px;
  box-shadow: none;
}

.paper-index {
  width: 34px;
  height: 34px;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  border-radius: 12px;
  background: rgba(0, 122, 255, 0.1);
  color: var(--app-primary);
  font-weight: 900;
}

.paper-body {
  min-width: 0;
  display: grid;
  gap: 10px;
}

.paper-body h3 {
  margin: 0;
  color: var(--app-text);
  font-size: 17px;
  line-height: 1.5;
}

.paper-body h3 a {
  color: inherit;
  text-decoration: none;
}

.paper-body h3 a:hover {
  color: var(--app-primary);
  text-decoration: underline;
}

.inline-paper-card .paper-body h3 {
  font-size: 15px;
}

.paper-meta {
  margin: 0;
  color: #64748b;
  font-size: 13px;
  font-weight: 700;
}

.abstract-button {
  justify-self: start;
  padding: 7px 11px;
  border: 1px solid rgba(0, 122, 255, 0.22);
  border-radius: 999px;
  background: rgba(0, 122, 255, 0.09);
  color: var(--app-primary);
  cursor: pointer;
  font-size: 13px;
  font-weight: 900;
  transition: background 0.16s ease, border-color 0.16s ease, transform 0.16s ease;
}

.abstract-button:hover {
  border-color: rgba(0, 122, 255, 0.32);
  background: rgba(0, 122, 255, 0.13);
  transform: translateY(-1px);
}

.paper-tags {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}

.paper-tags span {
  padding: 5px 9px;
  border-radius: 999px;
  background: rgba(242, 242, 247, 0.82);
  color: #515154;
  font-size: 12px;
  font-weight: 800;
}

.literature-state {
  width: min(760px, calc(100% - 48px));
  margin: auto;
  padding: 24px;
  border: 1px solid rgba(255, 255, 255, 0.74);
  border-radius: 24px;
  background: rgba(255, 255, 255, 0.66);
  color: var(--app-text-muted);
  text-align: center;
}

.inline-state {
  width: 100%;
  margin: 0;
  padding: 18px;
  border-radius: 18px;
}

.literature-state strong,
.literature-state span {
  display: block;
}

.literature-state strong {
  margin-bottom: 8px;
  color: var(--app-text);
  font-size: 16px;
}

.abstract-overlay {
  position: fixed;
  inset: 0;
  z-index: 3000;
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 24px;
  background: rgba(15, 23, 42, 0.42);
  backdrop-filter: blur(7px);
}

.abstract-dialog {
  width: min(720px, 100%);
  max-height: min(76vh, 720px);
  overflow: hidden;
  display: grid;
  grid-template-rows: auto minmax(0, 1fr);
  border: 1px solid rgba(255, 255, 255, 0.74);
  border-radius: 26px;
  background: rgba(255, 255, 255, 0.88);
  box-shadow: 0 28px 80px rgba(15, 23, 42, 0.26);
  backdrop-filter: blur(26px) saturate(175%);
  -webkit-backdrop-filter: blur(26px) saturate(175%);
}

.abstract-dialog-header {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 18px;
  padding: 22px 24px 16px;
  border-bottom: 1px solid rgba(209, 209, 214, 0.58);
}

.abstract-dialog-header span {
  display: inline-flex;
  margin-bottom: 6px;
  color: var(--app-primary);
  font-size: 12px;
  font-weight: 900;
}

.abstract-dialog-header h2 {
  margin: 0;
  color: #111827;
  font-size: 18px;
  line-height: 1.55;
}

.abstract-close {
  width: 34px;
  height: 34px;
  flex: 0 0 auto;
  border: 0;
  border-radius: 999px;
  background: #f1f5f9;
  color: #475569;
  cursor: pointer;
  font-size: 24px;
  line-height: 1;
}

.abstract-close:hover {
  background: #e2e8f0;
  color: #0f172a;
}

.abstract-dialog-content {
  overflow-y: auto;
  margin: 0;
  padding: 20px 24px 24px;
  color: #374151;
  line-height: 1.85;
  white-space: pre-wrap;
}

.abstract-modal-enter-active,
.abstract-modal-leave-active {
  transition: opacity 0.2s ease;
}

.abstract-modal-enter-active .abstract-dialog,
.abstract-modal-leave-active .abstract-dialog {
  transition: opacity 0.22s ease, transform 0.22s cubic-bezier(0.22, 1, 0.36, 1);
}

.abstract-modal-enter-from,
.abstract-modal-leave-to {
  opacity: 0;
}

.abstract-modal-enter-from .abstract-dialog,
.abstract-modal-leave-to .abstract-dialog {
  opacity: 0;
  transform: translateY(18px) scale(0.96);
}

.abstract-modal-enter-to .abstract-dialog,
.abstract-modal-leave-from .abstract-dialog {
  opacity: 1;
  transform: translateY(0) scale(1);
}
.error-state {
  border-color: #fecaca;
  background: #fff7f7;
  color: #b91c1c;
}

@media (max-width: 640px) {
  .literature-results {
    padding-bottom: 178px;
  }

  .literature-panel,
  .literature-state {
    width: calc(100% - 24px);
  }

  .paper-card {
    grid-template-columns: 1fr;
  }
}
</style>