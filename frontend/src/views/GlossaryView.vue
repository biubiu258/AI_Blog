<template>
  <div class="page-container">
    <header class="page-head">
      <div>
        <div class="page-title">技术术语表</div>
        <div class="page-sub">GLOSSARY · 统一规范写法，避免异体与误用</div>
      </div>
      <div class="head-actions">
        <el-input v-model="keyword" clearable placeholder="搜索术语、释义或变体"
                  class="search-input" @keyup.enter="load" @clear="load" />
        <el-button @click="load">搜索</el-button>
        <el-button link @click="$router.push('/tech')">返回技术板块</el-button>
      </div>
    </header>

    <el-empty v-if="!loading && !terms.length" :image-size="60"
              description="术语表暂无内容，等待管理员维护" />

    <section v-for="group in groups" :key="group.category" class="term-group">
      <div class="group-head">
        <span class="group-title">{{ group.category }}</span>
        <span class="group-count">{{ group.items.length }} 条</span>
      </div>
      <div class="term-grid">
        <article v-for="t in group.items" :key="t.id" class="term-card">
          <div class="card-head">
            <span class="canonical">{{ t.canonical }}</span>
            <el-tag v-if="t.caseSensitive === 1" size="small" type="warning" effect="plain">
              区分大小写
            </el-tag>
          </div>
          <p class="definition">{{ t.definition || '暂无释义' }}</p>
          <div v-if="t.variants" class="variants">
            <span class="variants-label">避免</span>
            <span v-for="v in splitVariants(t.variants)" :key="v" class="variant-chip">{{ v }}</span>
          </div>
        </article>
      </div>
    </section>
  </div>
</template>

<script setup>
import { computed, onMounted, ref } from 'vue'
import { techApi } from '../api'

const terms = ref([])
const loading = ref(false)
const keyword = ref('')
const activeKeyword = ref('')

function splitVariants(value) {
  return String(value || '')
    .split(/[,，、;；\n]+/)
    .map((item) => item.trim())
    .filter(Boolean)
}

const groups = computed(() => {
  const buckets = new Map()
  for (const term of terms.value) {
    const category = term.category || '通用'
    if (!buckets.has(category)) buckets.set(category, [])
    buckets.get(category).push(term)
  }
  return [...buckets.entries()].map(([category, items]) => ({ category, items }))
})

async function load() {
  loading.value = true
  activeKeyword.value = keyword.value.trim()
  try {
    terms.value = (await techApi.terms({
      keyword: activeKeyword.value || undefined
    })) || []
  } catch {
    terms.value = []
  } finally {
    loading.value = false
  }
}

onMounted(load)
</script>

<style scoped>
.page-head {
  flex-wrap: wrap;
  row-gap: 12px;
}
.head-actions {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-wrap: wrap;
}
.search-input {
  width: 260px;
}

.term-group {
  margin-bottom: 32px;
}
.group-head {
  display: flex;
  align-items: baseline;
  gap: 12px;
  padding-bottom: 10px;
  border-bottom: 1px solid var(--ink);
  margin-bottom: 16px;
}
.group-title {
  font-size: 18px;
  font-weight: 900;
  letter-spacing: 0.16em;
}
.group-count {
  font-size: 12px;
  color: var(--ink-faint);
}

.term-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(250px, 1fr));
  gap: 16px;
}
.term-card {
  padding: 16px;
  background: var(--paper-card);
  border: 1px solid var(--hairline);
  border-left: 3px solid var(--cinnabar);
}
.card-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
}
.canonical {
  font-size: 16px;
  font-weight: 800;
  letter-spacing: 0.04em;
}
.definition {
  margin: 10px 0 0;
  font-size: 13px;
  line-height: 1.75;
  color: var(--ink-soft);
}
.variants {
  margin-top: 12px;
  padding-top: 10px;
  border-top: 1px dashed var(--hairline);
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 6px;
}
.variants-label {
  font-size: 11.5px;
  letter-spacing: 0.12em;
  color: var(--ink-faint);
}
.variant-chip {
  padding: 1px 7px;
  font-size: 12px;
  color: var(--ink-faint);
  background: rgba(120, 110, 90, 0.08);
  border: 1px solid var(--hairline);
  border-radius: 2px;
  font-family: var(--font-mono, monospace);
}

@media (max-width: 720px) {
  .term-grid {
    grid-template-columns: 1fr;
  }
  .search-input {
    width: 100%;
  }
}
</style>
