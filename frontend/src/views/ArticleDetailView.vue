<template>
  <div class="page-container">
    <el-empty v-if="!loading && !article" description="文章不存在或已被删除" />
    <div v-else v-loading="loading" class="detail-wrap">
      <!-- 正文：杂志通栏窄栏 -->
      <article class="article-page">
        <header class="article-head">
          <div class="article-kicker">
            <span v-if="article.categoryName" class="kicker-cat">
              {{ article.categoryName }}
            </span>
            <span class="kicker-dot">◆</span>
            <span>{{ formatDate(article.publishedAt || article.createdAt) }}</span>
            <el-tag v-if="article.status === 'draft'" size="small" type="warning">草稿</el-tag>
          </div>
          <h1 class="detail-title">{{ article.title }}</h1>
          <div class="detail-meta">
            <el-avatar :size="26" :src="article.authorAvatar || undefined">
              {{ (article.authorNickname || '佚名').slice(0, 1) }}
            </el-avatar>
            <span class="meta-author">{{ article.authorNickname || '佚名' }}</span>
          </div>
        </header>

        <div class="head-rule"><span class="rule-mark">◆</span></div>

        <!-- 正文为后端白名单清洗后的 HTML，可安全渲染 -->
        <div class="article-content" v-html="article.contentHtml"></div>
      </article>

      <!-- 评论区 -->
      <section class="comments">
        <header class="comments-head">
          <span class="comments-title">评论</span>
          <span class="comments-count">{{ comments.length }} 则</span>
        </header>

        <div v-if="auth.isLoggedIn" class="comment-editor">
          <el-input v-model="commentContent" type="textarea" :rows="3" maxlength="2000"
                    show-word-limit placeholder="发表你的看法…" />
          <div class="editor-actions">
            <el-button type="primary" :loading="submitting" @click="submitComment">
              发表评论
            </el-button>
          </div>
        </div>
        <el-alert v-else type="info" :closable="false" title="登录后即可发表评论">
          <router-link :to="`/login?redirect=/article/${articleId}`">去登录</router-link>
        </el-alert>

        <el-empty v-if="comments.length === 0" description="还没有评论" :image-size="60" />
        <div v-for="c in comments" :key="c.id" class="comment-item">
          <el-avatar :size="32" :src="c.authorAvatar || undefined">
            {{ (c.authorNickname || '佚名').slice(0, 1) }}
          </el-avatar>
          <div class="comment-body">
            <div class="comment-meta">
              <span class="comment-author">{{ c.authorNickname || '佚名' }}</span>
              <span class="comment-time">{{ formatDate(c.createdAt) }}</span>
              <el-button v-if="canDelete(c)" link type="danger" size="small"
                         @click="deleteComment(c)">删除</el-button>
            </div>
            <div class="comment-text">{{ c.content }}</div>
          </div>
        </div>
      </section>
    </div>
  </div>
</template>

<script setup>
import { computed, onMounted, ref } from 'vue'
import { useRoute } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { articleApi, commentApi } from '../api'
import { useAuthStore } from '../stores/auth'

const route = useRoute()
const auth = useAuthStore()
const articleId = computed(() => route.params.id)

const loading = ref(false)
const article = ref({})
const comments = ref([])
const commentContent = ref('')
const submitting = ref(false)

const canDelete = (c) =>
  auth.isLoggedIn && (auth.user?.id === c.authorId || auth.isAdmin)

async function load() {
  loading.value = true
  try {
    article.value = await articleApi.detail(articleId.value)
    comments.value = await commentApi.listByArticle(articleId.value)
  } finally {
    loading.value = false
  }
}

async function submitComment() {
  if (!commentContent.value.trim()) {
    ElMessage.warning('评论内容不能为空')
    return
  }
  submitting.value = true
  try {
    await commentApi.create(articleId.value, { content: commentContent.value })
    commentContent.value = ''
    comments.value = await commentApi.listByArticle(articleId.value)
    ElMessage.success('评论成功')
  } finally {
    submitting.value = false
  }
}

async function deleteComment(c) {
  await ElMessageBox.confirm('确定删除这条评论吗？', '提示', { type: 'warning' })
  await commentApi.remove(c.id)
  comments.value = await commentApi.listByArticle(articleId.value)
}

function formatDate(value) {
  return value ? String(value).replace('T', ' ').slice(0, 16) : ''
}

onMounted(load)
</script>

<style scoped>
/* ---------- 正文页：窄栏居中 ---------- */
.article-page {
  max-width: 780px;
  margin: 0 auto;
}

.article-head {
  text-align: center;
}

.article-kicker {
  display: flex;
  align-items: center;
  justify-content: center;
  flex-wrap: wrap;
  gap: 10px;
  font-size: 13px;
  letter-spacing: 0.22em;
  color: var(--ink-faint);
}
.kicker-cat {
  color: var(--cinnabar);
  font-weight: 600;
}
.kicker-dot {
  font-size: 8px;
  color: var(--cinnabar);
}

.detail-title {
  margin: 20px 0 18px;
  font-size: 32px;
  font-weight: 900;
  letter-spacing: 0.04em;
  line-height: 1.45;
}

.detail-meta {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 8px;
  color: var(--ink-soft);
  font-size: 14px;
  letter-spacing: 0.06em;
}

/* 题下装饰线：细线 + 朱砂菱形 */
.head-rule {
  display: flex;
  align-items: center;
  gap: 12px;
  margin: 30px 0 34px;
}
.head-rule::before,
.head-rule::after {
  content: '';
  flex: 1;
  border-top: 1px solid var(--hairline-dark);
}
.rule-mark {
  color: var(--cinnabar);
  font-size: 9px;
}

/* ---------- 评论区 ---------- */
.comments {
  max-width: 780px;
  margin: 60px auto 0;
  border-top: 1px solid var(--ink);
  padding-top: 24px;
}

.comments-head {
  display: flex;
  align-items: baseline;
  gap: 12px;
  margin-bottom: 20px;
}
.comments-title {
  font-size: 20px;
  font-weight: 900;
  letter-spacing: 0.18em;
}
.comments-count {
  color: var(--ink-faint);
  font-size: 13px;
  letter-spacing: 0.1em;
}

.comment-editor {
  margin-bottom: 24px;
}
.editor-actions {
  margin-top: 12px;
  text-align: right;
}

.comment-item {
  display: flex;
  gap: 12px;
  padding: 16px 0;
  border-bottom: 1px solid var(--hairline);
}
.comment-item:last-child {
  border-bottom: none;
}
.comment-meta {
  display: flex;
  align-items: center;
  gap: 10px;
  color: var(--ink-faint);
  font-size: 13px;
}
.comment-author {
  color: var(--ink);
  font-weight: 600;
  letter-spacing: 0.04em;
}
.comment-text {
  margin-top: 6px;
  line-height: 1.9;
  white-space: pre-wrap;
  color: var(--ink-soft);
}

@media (max-width: 720px) {
  .detail-title {
    font-size: 25px;
  }
}
</style>
