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
            <span class="kicker-faint">阅读 {{ article.viewCount || 0 }}</span>
            <el-tag v-if="article.status === 'draft'" size="small" type="warning">草稿</el-tag>
            <el-tag v-else-if="article.status === 'scheduled'" size="small" type="info">定时</el-tag>
          </div>
          <h1 class="detail-title">{{ article.title }}</h1>
          <div class="detail-meta">
            <el-avatar :size="26" :src="article.authorAvatar || undefined">
              {{ (article.authorNickname || '佚名').slice(0, 1) }}
            </el-avatar>
            <router-link :to="`/user/${article.authorId}`" class="meta-author">
              {{ article.authorNickname || '佚名' }}
            </router-link>
            <el-button
              v-if="auth.isLoggedIn && auth.user?.id !== article.authorId"
              size="small"
              :type="article.following ? 'info' : 'primary'"
              plain
              @click="toggleFollow"
            >
              {{ article.following ? '已关注' : '关注' }} {{ article.followerCount || 0 }}
            </el-button>
          </div>
          <div v-if="article.tags?.length" class="detail-tags">
            <el-tag v-for="t in article.tags" :key="t" size="small" effect="plain">{{ t }}</el-tag>
          </div>
        </header>

        <div class="head-rule"><span class="rule-mark">◆</span></div>

        <!-- 正文为后端白名单清洗后的 HTML，可安全渲染 -->
        <div class="article-content" v-html="article.contentHtml"></div>

        <footer class="article-actions">
          <el-button :type="article.liked ? 'primary' : 'default'" @click="toggleLike">
            <span class="action-mark">赞</span> {{ article.likeCount || 0 }}
          </el-button>
          <el-button :type="article.favorited ? 'warning' : 'default'" @click="toggleFavorite">
            <span class="action-mark">藏</span> {{ article.favoriteCount || 0 }}
          </el-button>
          <el-button link type="danger" @click="reportArticle">举报</el-button>
        </footer>
      </article>

      <!-- 评论区 -->
      <section class="comments">
        <header class="comments-head">
          <span class="comments-title">评论</span>
          <span class="comments-count">{{ comments.length }} 则</span>
        </header>

        <div v-if="auth.isLoggedIn" class="comment-editor">
          <div v-if="replyTo" class="reply-hint">
            回复 {{ replyTo.authorNickname || '佚名' }}
            <el-button link size="small" @click="replyTo = null">取消</el-button>
          </div>
          <el-input v-model="commentContent" type="textarea" :rows="3" maxlength="2000"
                    show-word-limit :placeholder="replyTo ? '写下你的回复…' : '发表你的看法…'" />
          <div class="editor-actions">
            <el-button type="primary" :loading="submitting" @click="submitComment">
              {{ replyTo ? '发表回复' : '发表评论' }}
            </el-button>
          </div>
        </div>
        <el-alert v-else type="info" :closable="false" title="登录后即可发表评论">
          <router-link :to="`/login?redirect=/article/${articleId}`">去登录</router-link>
        </el-alert>

        <el-empty v-if="comments.length === 0" description="还没有评论" :image-size="60" />
        <div v-for="c in rootComments" :key="c.id" class="comment-thread">
          <div class="comment-item">
            <el-avatar :size="32" :src="c.authorAvatar || undefined">
              {{ (c.authorNickname || '佚名').slice(0, 1) }}
            </el-avatar>
            <div class="comment-body">
              <div class="comment-meta">
                <span class="comment-author">{{ c.authorNickname || '佚名' }}</span>
                <span class="comment-time">{{ formatDate(c.createdAt) }}</span>
                <el-button v-if="auth.isLoggedIn" link size="small"
                           @click="startReply(c)">回复</el-button>
                <el-button v-if="canDelete(c)" link type="danger" size="small"
                           @click="deleteComment(c)">删除</el-button>
                <el-button v-if="auth.isLoggedIn" link size="small"
                           @click="reportComment(c)">举报</el-button>
              </div>
              <div class="comment-text">{{ c.content }}</div>
            </div>
          </div>
          <div v-for="r in repliesOf(c.id)" :key="r.id" class="comment-item comment-reply">
            <el-avatar :size="26" :src="r.authorAvatar || undefined">
              {{ (r.authorNickname || '佚名').slice(0, 1) }}
            </el-avatar>
            <div class="comment-body">
              <div class="comment-meta">
                <span class="comment-author">{{ r.authorNickname || '佚名' }}</span>
                <span v-if="r.replyToNickname" class="reply-target">
                  回复 {{ r.replyToNickname }}
                </span>
                <span class="comment-time">{{ formatDate(r.createdAt) }}</span>
                <el-button v-if="auth.isLoggedIn" link size="small"
                           @click="startReply(r)">回复</el-button>
                <el-button v-if="canDelete(r)" link type="danger" size="small"
                           @click="deleteComment(r)">删除</el-button>
              </div>
              <div class="comment-text">{{ r.content }}</div>
            </div>
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
import { articleApi, commentApi, reportApi, userApi } from '../api'
import { useAuthStore } from '../stores/auth'

const route = useRoute()
const auth = useAuthStore()
const articleId = computed(() => route.params.id)

const loading = ref(false)
const article = ref({})
const comments = ref([])
const commentContent = ref('')
const submitting = ref(false)
const replyTo = ref(null)

const rootComments = computed(() => comments.value.filter((c) => !c.parentId))
const repliesOf = (id) => comments.value.filter((c) => c.parentId === id)

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
  if (article.value.status === 'published') {
    articleApi
      .view(articleId.value)
      .then((res) => {
        article.value.viewCount = res.viewCount
      })
      .catch(() => {})
  }
}

function requireLogin() {
  if (!auth.isLoggedIn) {
    ElMessage.warning('请先登录')
    return false
  }
  return true
}

async function toggleLike() {
  if (!requireLogin()) return
  const res = await articleApi.like(articleId.value)
  article.value.liked = res.liked
  article.value.likeCount = res.likeCount
}

async function toggleFavorite() {
  if (!requireLogin()) return
  const res = await articleApi.favorite(articleId.value)
  article.value.favorited = res.favorited
  article.value.favoriteCount = res.favoriteCount
}

async function toggleFollow() {
  if (!requireLogin()) return
  const res = await userApi.follow(article.value.authorId)
  article.value.following = res.following
  article.value.followerCount = res.followerCount
}

function startReply(c) {
  if (!requireLogin()) return
  replyTo.value = c
  commentContent.value = ''
}

async function submitComment() {
  if (!commentContent.value.trim()) {
    ElMessage.warning('评论内容不能为空')
    return
  }
  submitting.value = true
  try {
    await commentApi.create(articleId.value, {
      content: commentContent.value,
      parentId: replyTo.value?.id
    })
    commentContent.value = ''
    replyTo.value = null
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

async function reportArticle() {
  await reportTarget('article', articleId.value, '文章')
}

async function reportComment(c) {
  await reportTarget('comment', c.id, '评论')
}

async function reportTarget(targetType, targetId, label) {
  if (!requireLogin()) return
  const { value } = await ElMessageBox.prompt(`请说明举报该${label}的原因`, '举报', {
    inputPlaceholder: '如：广告、侵权、不当内容',
    inputValidator: (v) => (v && v.trim() ? true : '请填写举报原因')
  })
  await reportApi.create({ targetType, targetId, reason: value.trim() })
  ElMessage.success('举报已提交，我们会尽快处理')
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
.kicker-faint {
  color: var(--ink-faint);
  letter-spacing: 0.1em;
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
.detail-meta .meta-author {
  color: var(--ink-soft);
  text-decoration: none;
  transition: color 0.2s ease;
}
.detail-meta .meta-author:hover {
  color: var(--cinnabar);
}

.detail-tags {
  display: flex;
  justify-content: center;
  gap: 8px;
  margin-top: 14px;
  flex-wrap: wrap;
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

.article-actions {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 14px;
  margin: 48px 0 10px;
  padding-top: 22px;
  border-top: 1px solid var(--hairline);
}
.action-mark {
  font-weight: 700;
  letter-spacing: 0.1em;
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
.reply-hint {
  margin-bottom: 8px;
  font-size: 13px;
  color: var(--ink-faint);
}
.editor-actions {
  margin-top: 12px;
  text-align: right;
}

.comment-thread {
  border-bottom: 1px solid var(--hairline);
}
.comment-thread:last-child {
  border-bottom: none;
}

.comment-item {
  display: flex;
  gap: 12px;
  padding: 16px 0;
}
.comment-reply {
  margin-left: 44px;
  padding: 10px 0 16px;
}
.comment-meta {
  display: flex;
  align-items: center;
  gap: 10px;
  color: var(--ink-faint);
  font-size: 13px;
  flex-wrap: wrap;
}
.comment-author {
  color: var(--ink);
  font-weight: 600;
  letter-spacing: 0.04em;
}
.reply-target {
  color: var(--cinnabar);
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
  .comment-reply {
    margin-left: 20px;
  }
}
</style>
