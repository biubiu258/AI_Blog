<template>
  <div class="crisis-page">
    <div class="crisis-inner">
      <p class="crisis-eyebrow">IN THIS MOMENT · YOU ARE NOT ALONE</p>
      <h1 class="crisis-title">现在，请先照顾好自己</h1>
      <p class="crisis-lead">「一切都会过去，但先让此刻过去。」</p>

      <!-- 私密发布安抚条：URL 带 article 参数时出现 -->
      <div v-if="articleId" class="comfort-bar">
        <p class="comfort-text">
          你的文章已发布，目前<strong>只有你自己可见</strong>。先照顾好自己，文章随时可以继续修改。
        </p>
        <button class="comfort-btn" @click="$router.push(`/write/${articleId}`)">
          返回编辑
        </button>
      </div>

      <!-- 热线：第一视觉层级，特大字，一键拨号 -->
      <div class="hotline-grid">
        <a v-for="h in hotlines" :key="h.display" class="hotline-card" :href="`tel:${h.tel}`">
          <span class="hotline-name">{{ h.name }}</span>
          <span class="hotline-number">{{ h.display }}</span>
          <span class="hotline-note">{{ h.note }} · 点击拨打</span>
        </a>
      </div>

      <div class="todo-block">
        <p class="todo-head">此刻，可以做的事</p>
        <ul class="todo-list">
          <li>告诉一个信任的人，哪怕只说一句「我现在不太好」。</li>
          <li>尽量不要独自待着。</li>
          <li>
            把感受写下来——
            <router-link to="/prose" class="todo-link">写作有时也是一种出口</router-link>。
          </li>
        </ul>
      </div>

      <p class="crisis-foot">
        「留一盏灯给自己，也留一个电话在手上。」
      </p>
      <router-link to="/" class="back-link">我想回去继续写作</router-link>
    </div>
  </div>
</template>

<script setup>
// 危机页：独立全屏、不套 Layout、未登录可开、秒开纯排版
import { computed } from 'vue'
import { useRoute } from 'vue-router'

const route = useRoute()
const articleId = computed(() => route.query.article || '')

const hotlines = [
  { name: '全国统一心理援助热线', display: '12356', tel: '12356', note: '24 小时' },
  { name: '北京心理危机研究与干预中心', display: '010-82951332', tel: '01082951332', note: '24 小时' },
  { name: '希望 24 热线', display: '400-161-9995', tel: '4001619995', note: '24 小时' },
  { name: '共青团青少年服务热线', display: '12355', tel: '12355', note: '青少年专线' }
]
</script>

<style scoped>
.crisis-page {
  min-height: 100vh;
  background: var(--paper, #f7f3e9);
  font-family: var(--font-serif, serif);
  color: var(--ink, #26241e);
  display: flex;
  justify-content: center;
  padding: 64px 20px 48px;
}
.crisis-inner {
  width: 100%;
  max-width: 880px;
}

.crisis-eyebrow {
  margin: 0 0 10px;
  font-size: 11px;
  letter-spacing: 0.42em;
  color: var(--ink-faint, #9a937f);
}
.crisis-title {
  margin: 0 0 14px;
  font-size: 40px;
  font-weight: 900;
  letter-spacing: 0.08em;
  line-height: 1.3;
  border-top: 3px solid var(--ink, #26241e);
  padding-top: 28px;
}
.crisis-lead {
  margin: 0 0 30px;
  font-size: 16px;
  letter-spacing: 0.1em;
  color: var(--ink-soft, #54503f);
}

/* 私密发布安抚条 */
.comfort-bar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  border: 1px solid var(--hairline-dark, #d8d2c2);
  border-left: 3px solid var(--cinnabar, #a63b2a);
  background: var(--paper-card, #fdfbf4);
  padding: 14px 18px;
  margin-bottom: 30px;
}
.comfort-text {
  margin: 0;
  font-size: 14px;
  line-height: 1.8;
  color: var(--ink-soft, #54503f);
}
.comfort-text strong {
  color: var(--ink, #26241e);
}
.comfort-btn {
  flex-shrink: 0;
  border: 1px solid var(--ink, #26241e);
  background: transparent;
  color: var(--ink, #26241e);
  font-family: inherit;
  font-size: 13px;
  letter-spacing: 0.14em;
  padding: 8px 16px;
  cursor: pointer;
  transition: all 0.2s ease;
}
.comfort-btn:hover {
  background: var(--ink, #26241e);
  color: var(--paper, #f7f3e9);
}

/* 热线卡片 */
.hotline-grid {
  display: grid;
  grid-template-columns: repeat(2, 1fr);
  gap: 14px;
  margin-bottom: 36px;
}
.hotline-card {
  display: flex;
  flex-direction: column;
  gap: 6px;
  border: 1px solid var(--ink, #26241e);
  border-top: 3px solid var(--cinnabar, #a63b2a);
  background: var(--paper-card, #fdfbf4);
  padding: 20px 22px;
  text-decoration: none;
  transition: transform 0.2s ease, box-shadow 0.2s ease;
}
.hotline-card:hover {
  transform: translateY(-2px);
  box-shadow: 4px 4px 0 rgba(38, 36, 30, 0.12);
}
.hotline-name {
  font-size: 13px;
  letter-spacing: 0.16em;
  color: var(--ink-soft, #54503f);
}
.hotline-number {
  font-size: 34px;
  font-weight: 900;
  letter-spacing: 0.06em;
  color: var(--cinnabar, #a63b2a);
  line-height: 1.2;
}
.hotline-note {
  font-size: 11px;
  letter-spacing: 0.2em;
  color: var(--ink-faint, #9a937f);
}

/* 此刻可以做的事 */
.todo-block {
  border-top: 1px solid var(--hairline-dark, #d8d2c2);
  padding-top: 22px;
  margin-bottom: 32px;
}
.todo-head {
  margin: 0 0 12px;
  font-size: 12px;
  letter-spacing: 0.3em;
  color: var(--ink-faint, #9a937f);
}
.todo-list {
  margin: 0;
  padding: 0;
  list-style: none;
}
.todo-list li {
  position: relative;
  padding-left: 18px;
  font-size: 14.5px;
  line-height: 2.1;
  color: var(--ink-soft, #54503f);
}
.todo-list li::before {
  content: '·';
  position: absolute;
  left: 2px;
  color: var(--cinnabar, #a63b2a);
  font-weight: 900;
}
.todo-link {
  color: var(--cinnabar, #a63b2a);
  text-decoration: none;
  border-bottom: 1px solid currentColor;
}

.crisis-foot {
  margin: 0 0 26px;
  font-size: 13px;
  letter-spacing: 0.12em;
  color: var(--ink-faint, #9a937f);
}
.back-link {
  font-size: 13px;
  letter-spacing: 0.18em;
  color: var(--ink-faint, #9a937f);
  text-decoration: none;
  border-bottom: 1px solid var(--hairline-dark, #d8d2c2);
  padding-bottom: 2px;
  transition: color 0.2s ease;
}
.back-link:hover {
  color: var(--ink, #26241e);
}

@media (max-width: 640px) {
  .crisis-title { font-size: 30px; }
  .hotline-grid { grid-template-columns: 1fr; }
  .hotline-number { font-size: 28px; }
  .comfort-bar { flex-direction: column; align-items: flex-start; }
}
</style>
