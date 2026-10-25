<template>
  <div class="page-container">
    <header class="page-head">
      <div>
        <div class="page-title">通知</div>
        <div class="page-sub">NOTIFICATIONS · {{ total }} 条</div>
      </div>
      <div class="head-actions">
        <el-radio-group v-model="unreadOnly" size="small" @change="load(1)">
          <el-radio-button :value="false">全部</el-radio-button>
          <el-radio-button :value="true">未读{{ unread ? ` ${unread}` : '' }}</el-radio-button>
        </el-radio-group>
        <el-select v-model="type" size="small" class="type-select" @change="load(1)">
          <el-option label="全部类型" value="" />
          <el-option label="评论" value="comment" />
          <el-option label="回复" value="reply" />
          <el-option label="点赞" value="like" />
          <el-option label="收藏" value="favorite" />
          <el-option label="关注" value="follow" />
        </el-select>
        <el-button size="small" :disabled="!hasUnread" @click="markAllRead">全部已读</el-button>
      </div>
    </header>

    <el-empty v-if="!loading && items.length === 0" description="暂无通知" />
    <div v-loading="loading" class="notif-list">
      <div v-for="n in items" :key="n.id" class="notif-item" :class="{ unread: !n.read }"
           @click="open(n)">
        <el-avatar :size="34" :src="n.actorAvatar || undefined">
          {{ (n.actorNickname || '系统').slice(0, 1) }}
        </el-avatar>
        <div class="notif-body">
          <div class="notif-line">
            <span class="notif-actor">{{ n.actorNickname || '系统' }}</span>
            <span class="notif-text">{{ n.content }}</span>
          </div>
          <div class="notif-meta">
            <span>{{ typeLabel(n.type) }}</span>
            <span>{{ formatDate(n.createdAt) }}</span>
          </div>
        </div>
        <span v-if="!n.read" class="unread-dot">◆</span>
      </div>
    </div>

    <div class="pager">
      <el-pagination background layout="prev, pager, next, total" :total="total"
                     :page-size="size" :current-page="page" @current-change="load" />
    </div>
  </div>
</template>

<script setup>
import { computed, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { notificationApi } from '../api'

const router = useRouter()
const loading = ref(false)
const items = ref([])
const total = ref(0)
const unread = ref(0)
const page = ref(1)
const size = 20
const unreadOnly = ref(false)
const type = ref('')

const hasUnread = computed(() => unread.value > 0)

async function load(p = page.value) {
  page.value = p
  loading.value = true
  try {
    const data = await notificationApi.list({
      page: page.value,
      size,
      unreadOnly: unreadOnly.value,
      type: type.value || undefined
    })
    items.value = data.items || []
    total.value = data.total || 0
    unread.value = data.unread || 0
  } finally {
    loading.value = false
  }
  if (!unreadOnly.value) {
    notifyBadge()
  }
}

async function open(n) {
  if (!n.read) {
    await notificationApi.markRead([n.id])
    n.read = true
  }
  if (n.articleId) {
    router.push(`/article/${n.articleId}`)
  }
  notifyBadge()
}

async function markAllRead() {
  await notificationApi.markRead(null)
  await load(page.value)
  notifyBadge()
}

function notifyBadge() {
  window.dispatchEvent(new Event('notifications:read'))
}

function typeLabel(type) {
  return {
    like: '点赞',
    favorite: '收藏',
    comment: '评论',
    reply: '回复',
    follow: '关注'
  }[type] || type
}

function formatDate(value) {
  return value ? String(value).replace('T', ' ').slice(0, 16) : ''
}

onMounted(() => load(1))
</script>

<style scoped>
.page-head {
  flex-wrap: wrap;
  row-gap: 12px;
}
.head-actions {
  display: flex;
  align-items: center;
  gap: 12px;
}
.type-select {
  width: 120px;
}

.notif-list {
  min-height: 120px;
}

.notif-item {
  display: flex;
  align-items: flex-start;
  gap: 14px;
  padding: 18px 10px;
  border-bottom: 1px solid var(--hairline);
  cursor: pointer;
  transition: background 0.2s ease;
}
.notif-item:hover {
  background: rgba(253, 251, 244, 0.65);
}
.notif-item.unread {
  background: rgba(253, 235, 229, 0.45);
}

.notif-body {
  flex: 1;
  min-width: 0;
}
.notif-line {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
  font-size: 14px;
  line-height: 1.8;
}
.notif-actor {
  font-weight: 700;
  color: var(--ink);
}
.notif-text {
  color: var(--ink-soft);
  word-break: break-word;
}
.notif-meta {
  display: flex;
  gap: 14px;
  margin-top: 6px;
  font-size: 12.5px;
  color: var(--ink-faint);
  letter-spacing: 0.06em;
}
.unread-dot {
  color: var(--cinnabar);
  font-size: 9px;
  margin-top: 6px;
}

.pager {
  margin-top: 26px;
  display: flex;
  justify-content: center;
}
</style>
