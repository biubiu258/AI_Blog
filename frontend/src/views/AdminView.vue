<template>
  <div class="page-container">
    <header class="page-head">
      <div class="page-title">管理后台</div>
      <div class="page-sub">ADMIN CONSOLE</div>
    </header>

    <el-tabs v-model="activeTab">
      <!-- 分类管理：超级管理员 / 内容管理员 -->
      <el-tab-pane label="分类管理" name="categories">
        <div class="toolbar">
          <el-button type="primary" @click="openCategoryDialog()">新建分类</el-button>
        </div>
        <div class="sheet">
          <el-table :data="categories" v-loading="loadingCategories">
            <el-table-column prop="name" label="名称" min-width="160" />
            <el-table-column prop="description" label="描述" min-width="240" show-overflow-tooltip />
            <el-table-column prop="articleCount" label="文章数" width="100" />
            <el-table-column label="操作" width="150">
              <template #default="{ row }">
                <el-button link type="primary" @click="openCategoryDialog(row)">重命名</el-button>
                <el-button link type="danger" @click="removeCategory(row)">删除</el-button>
              </template>
            </el-table-column>
          </el-table>
        </div>
      </el-tab-pane>

      <!-- 用户管理：仅超级管理员 -->
      <el-tab-pane v-if="isSuper" label="用户管理" name="users">
        <div class="toolbar">
          <div class="toolbar-left">
            <el-input v-model="userKeyword" placeholder="按用户名/昵称搜索" clearable style="width: 220px"
                      @keyup.enter="loadUsers(1)">
              <template #append>
                <el-button @click="loadUsers(1)"><el-icon><Search /></el-icon></el-button>
              </template>
            </el-input>
            <el-select v-model="userStatusFilter" style="width: 120px" @change="loadUsers(1)">
              <el-option label="全部状态" value="" />
              <el-option label="正常" :value="0" />
              <el-option label="已封禁" :value="1" />
            </el-select>
          </div>
        </div>
        <div class="sheet">
          <el-table :data="users" v-loading="loadingUsers">
            <el-table-column prop="id" label="ID" width="64" />
            <el-table-column prop="username" label="用户名" min-width="120" />
            <el-table-column prop="nickname" label="昵称" min-width="110" />
            <el-table-column prop="email" label="邮箱" min-width="150" show-overflow-tooltip />
            <el-table-column label="角色" width="116">
              <template #default="{ row }">
                <el-tag :type="row.role === 'ADMIN' ? 'danger' : row.role === 'CONTENT_ADMIN' ? 'warning' : 'info'"
                        size="small" effect="plain">{{ roleLabel(row.role) }}</el-tag>
              </template>
            </el-table-column>
            <el-table-column label="状态" width="90">
              <template #default="{ row }">
                <el-tag v-if="row.status === 1" type="danger" size="small" effect="plain">已封禁</el-tag>
                <el-tag v-else type="success" size="small" effect="plain">正常</el-tag>
              </template>
            </el-table-column>
            <el-table-column label="封禁原因 / 到期" min-width="170" show-overflow-tooltip>
              <template #default="{ row }">
                <template v-if="row.status === 1">
                  {{ row.banReason || '-' }}
                  <span class="ban-until">（{{ row.bannedUntil ? '至 ' + formatDate(row.bannedUntil) : '永久' }}）</span>
                </template>
                <span v-else class="ban-until">-</span>
              </template>
            </el-table-column>
            <el-table-column label="操作" width="200" fixed="right">
              <template #default="{ row }">
                <el-button v-if="row.role !== 'ADMIN'" link
                           :type="row.status === 1 ? 'success' : 'danger'"
                           @click="openBanDialog(row)">
                  {{ row.status === 1 ? '解封' : '封禁' }}
                </el-button>
                <el-button v-if="row.role !== 'ADMIN' && row.id !== auth.user?.id" link type="primary"
                           @click="toggleRole(row)">
                  {{ row.role === 'CONTENT_ADMIN' ? '收回内容权限' : '设为内容管理员' }}
                </el-button>
              </template>
            </el-table-column>
          </el-table>
        </div>
        <div class="pager">
          <el-pagination background layout="prev, pager, next, total" :total="userTotal"
                         :page-size="userQuery.size" :current-page="userQuery.page"
                         @current-change="loadUsers" />
        </div>
      </el-tab-pane>

      <!-- 内容管理：超级管理员 / 内容管理员 -->
      <el-tab-pane label="内容管理" name="content">
        <div class="toolbar">
          <el-radio-group v-model="contentKind" @change="loadContent(1)">
            <el-radio-button value="articles">文章</el-radio-button>
            <el-radio-button value="comments">评论</el-radio-button>
          </el-radio-group>
          <div class="toolbar-left">
            <el-input v-model="contentKeyword"
                      :placeholder="contentKind === 'articles' ? '搜索标题/正文' : '搜索评论内容'"
                      clearable style="width: 220px" @keyup.enter="loadContent(1)">
              <template #append>
                <el-button @click="loadContent(1)"><el-icon><Search /></el-icon></el-button>
              </template>
            </el-input>
            <el-select v-if="contentKind === 'articles'" v-model="contentStatus"
                       style="width: 120px" @change="loadContent(1)">
              <el-option label="全部状态" value="" />
              <el-option label="草稿" value="draft" />
              <el-option label="已发布" value="published" />
              <el-option label="仅自己可见" value="private" />
            </el-select>
            <el-button v-if="contentKind === 'articles'" type="warning"
                       :disabled="!selectedRows.length" @click="batchUnpublish">
              批量下架
            </el-button>
            <el-button type="danger" :disabled="!selectedRows.length" @click="batchDeleteContent">
              批量删除
            </el-button>
          </div>
        </div>
        <div class="sheet">
          <el-table v-if="contentKind === 'articles'" :data="contentItems" v-loading="loadingContent"
                    @selection-change="onSelectionChange">
            <el-table-column type="selection" width="44" />
            <el-table-column prop="id" label="ID" width="64" />
            <el-table-column label="标题" min-width="200" show-overflow-tooltip>
              <template #default="{ row }">
                <el-link type="primary" @click="$router.push(`/article/${row.id}`)">{{ row.title }}</el-link>
              </template>
            </el-table-column>
            <el-table-column label="作者" width="120">
              <template #default="{ row }">{{ row.authorNickname || '-' }}</template>
            </el-table-column>
            <el-table-column label="分类" width="100">
              <template #default="{ row }">{{ row.categoryName || '-' }}</template>
            </el-table-column>
            <el-table-column label="状态" width="110">
              <template #default="{ row }">
                <el-tag :type="statusTagType(row.status)" size="small" effect="plain">
                  {{ statusLabel(row.status) }}
                </el-tag>
              </template>
            </el-table-column>
            <el-table-column label="更新时间" width="160">
              <template #default="{ row }">{{ formatDate(row.updatedAt) }}</template>
            </el-table-column>
          </el-table>

          <el-table v-else :data="contentItems" v-loading="loadingContent"
                    @selection-change="onSelectionChange">
            <el-table-column type="selection" width="44" />
            <el-table-column prop="id" label="ID" width="64" />
            <el-table-column label="评论内容" min-width="240" show-overflow-tooltip>
              <template #default="{ row }">{{ row.content }}</template>
            </el-table-column>
            <el-table-column label="评论者" width="120">
              <template #default="{ row }">{{ row.authorNickname || '-' }}</template>
            </el-table-column>
            <el-table-column label="所属文章" min-width="180" show-overflow-tooltip>
              <template #default="{ row }">
                <el-link type="primary" @click="$router.push(`/article/${row.articleId}`)">
                  {{ row.articleTitle || row.articleId }}
                </el-link>
              </template>
            </el-table-column>
            <el-table-column label="时间" width="160">
              <template #default="{ row }">{{ formatDate(row.createdAt) }}</template>
            </el-table-column>
          </el-table>
        </div>
        <div class="pager">
          <el-pagination background layout="prev, pager, next, total" :total="contentTotal"
                         :page-size="contentQuery.size" :current-page="contentQuery.page"
                         @current-change="loadContent" />
        </div>
      </el-tab-pane>

      <!-- 举报处理：超级管理员 / 内容管理员 -->
      <el-tab-pane label="举报处理" name="reports">
        <div class="toolbar">
          <el-radio-group v-model="reportStatus" @change="loadReports(1)">
            <el-radio-button value="">全部</el-radio-button>
            <el-radio-button value="pending">待处理</el-radio-button>
            <el-radio-button value="handled">已处理</el-radio-button>
            <el-radio-button value="dismissed">已忽略</el-radio-button>
          </el-radio-group>
          <span v-if="pendingReportCount > 0" class="pending-tip">
            待处理 {{ pendingReportCount }} 条
          </span>
        </div>
        <div class="sheet">
          <el-table :data="reports" v-loading="loadingReports">
            <el-table-column prop="id" label="ID" width="64" />
            <el-table-column label="类型" width="90">
              <template #default="{ row }">
                <el-tag size="small" effect="plain">
                  {{ row.targetType === 'article' ? '文章' : '评论' }}
                </el-tag>
              </template>
            </el-table-column>
            <el-table-column label="被举报内容" min-width="220" show-overflow-tooltip>
              <template #default="{ row }">
                <el-link v-if="row.targetType === 'article'" type="primary"
                         @click="$router.push(`/article/${row.targetId}`)">
                  {{ row.targetTitle || row.targetId }}
                </el-link>
                <span v-else>{{ row.targetTitle || `评论 #${row.targetId}` }}</span>
              </template>
            </el-table-column>
            <el-table-column prop="reason" label="举报理由" min-width="200" show-overflow-tooltip />
            <el-table-column label="举报人" width="120">
              <template #default="{ row }">{{ row.reporterNickname || '-' }}</template>
            </el-table-column>
            <el-table-column label="状态" width="100">
              <template #default="{ row }">
                <el-tag v-if="row.status === 'pending'" type="warning" size="small" effect="plain">待处理</el-tag>
                <el-tag v-else-if="row.status === 'handled'" type="danger" size="small" effect="plain">已处理</el-tag>
                <el-tag v-else type="info" size="small" effect="plain">已忽略</el-tag>
              </template>
            </el-table-column>
            <el-table-column label="时间" width="160">
              <template #default="{ row }">{{ formatDate(row.createdAt) }}</template>
            </el-table-column>
            <el-table-column label="操作" width="180" fixed="right">
              <template #default="{ row }">
                <template v-if="row.status === 'pending'">
                  <el-button link type="info" @click="handleReport(row, 'dismiss')">忽略</el-button>
                  <el-button link type="danger" @click="handleReport(row, 'delete')">删除内容</el-button>
                </template>
                <span v-else class="ban-until">-</span>
              </template>
            </el-table-column>
          </el-table>
        </div>
        <div class="pager">
          <el-pagination background layout="prev, pager, next, total" :total="reportTotal"
                         :page-size="reportQuery.size" :current-page="reportQuery.page"
                         @current-change="loadReports" />
        </div>
      </el-tab-pane>

      <!-- AI 治理：仅超级管理员 -->
      <el-tab-pane v-if="isSuper" label="AI 治理" name="ai">
        <div class="toolbar">
          <el-select v-model="aiDays" style="width: 140px" @change="loadAiUsage">
            <el-option label="近 7 天" :value="7" />
            <el-option label="近 14 天" :value="14" />
            <el-option label="近 30 天" :value="30" />
          </el-select>
          <div class="toolbar-left">
            <el-tag v-for="(count, action) in aiUsage.byAction || {}" :key="action" effect="plain">
              {{ aiActionLabel(action) }} {{ count }}
            </el-tag>
          </div>
        </div>
        <el-row :gutter="16" class="ai-stats">
          <el-col :span="8">
            <div class="stat-card">
              <div class="stat-value">{{ aiUsage.total || 0 }}</div>
              <div class="stat-label">总调用次数</div>
            </div>
          </el-col>
          <el-col :span="8">
            <div class="stat-card">
              <div class="stat-value">{{ aiUsage.adopted || 0 }}</div>
              <div class="stat-label">建议被采纳</div>
            </div>
          </el-col>
          <el-col :span="8">
            <div class="stat-card">
              <div class="stat-value">{{ adoptionRateText }}</div>
              <div class="stat-label">采纳率</div>
            </div>
          </el-col>
        </el-row>
        <div class="sheet">
          <el-table :data="aiUsage.daily || []" v-loading="loadingAiUsage">
            <el-table-column prop="date" label="日期" width="140" />
            <el-table-column prop="count" label="调用次数" width="120" />
            <el-table-column prop="adopted" label="采纳次数" width="120" />
            <el-table-column label="采纳率" min-width="120">
              <template #default="{ row }">{{ rateText(row.adopted, row.count) }}</template>
            </el-table-column>
          </el-table>
        </div>
      </el-tab-pane>

      <!-- AI 输出审计：仅超级管理员 -->
      <el-tab-pane v-if="isSuper" label="AI 输出审计" name="aiAudit">
        <div class="toolbar">
          <div class="toolbar-left">
            <el-select v-model="auditQuery.action" clearable placeholder="全部动作"
                       style="width: 128px" @change="loadAiOutputs(1)">
              <el-option label="生成" value="generate" />
              <el-option label="审阅" value="review" />
              <el-option label="搜索" value="search" />
              <el-option label="关怀检测" value="care" />
            </el-select>
            <el-select v-model="auditQuery.status" clearable placeholder="全部终态"
                       style="width: 120px" @change="loadAiOutputs(1)">
              <el-option label="成功" value="success" />
              <el-option label="失败" value="failed" />
              <el-option label="已取消" value="canceled" />
              <el-option label="进行中" value="streaming" />
            </el-select>
            <el-select v-model="auditQuery.reviewStatus" clearable placeholder="全部审核态"
                       style="width: 132px" @change="loadAiOutputs(1)">
              <el-option label="待审核" value="pending" />
              <el-option label="已通过" value="passed" />
              <el-option label="已标记" value="flagged" />
            </el-select>
            <el-input v-model="auditQuery.keyword" placeholder="追踪号 / 返回内容 / 错误"
                      clearable style="width: 220px" @keyup.enter="loadAiOutputs(1)"
                      @clear="loadAiOutputs(1)" />
            <el-button @click="loadAiOutputs(1)">查询</el-button>
            <span v-if="auditPending" class="pending-tip">待审核 {{ auditPending }}</span>
          </div>
        </div>
        <div class="sheet">
          <el-table :data="auditItems" v-loading="loadingAudit">
            <el-table-column label="时间" width="150">
              <template #default="{ row }">{{ formatDate(row.createdAt) }}</template>
            </el-table-column>
            <el-table-column label="追踪号" width="140">
              <template #default="{ row }">
                <span class="mono">{{ shortTrace(row.traceId) }}</span>
              </template>
            </el-table-column>
            <el-table-column label="动作" width="104">
              <template #default="{ row }">
                <el-tag size="small" effect="plain">{{ auditActionLabel(row.action) }}</el-tag>
              </template>
            </el-table-column>
            <el-table-column prop="task" label="任务" width="96" />
            <el-table-column label="返回内容预览" min-width="240" show-overflow-tooltip>
              <template #default="{ row }">
                {{ row.outputPreview || '（无返回内容）' }}
              </template>
            </el-table-column>
            <el-table-column label="终态" width="96">
              <template #default="{ row }">
                <el-tag size="small" effect="plain" :type="auditStatusType(row.status)">
                  {{ auditStatusLabel(row.status) }}
                </el-tag>
              </template>
            </el-table-column>
            <el-table-column label="审核" width="96">
              <template #default="{ row }">
                <el-tag size="small" effect="plain" :type="auditReviewType(row.reviewStatus)">
                  {{ auditReviewLabel(row.reviewStatus) }}
                </el-tag>
              </template>
            </el-table-column>
            <el-table-column label="字数" width="80">
              <template #default="{ row }">{{ row.outputLength || 0 }}</template>
            </el-table-column>
            <el-table-column label="耗时" width="90">
              <template #default="{ row }">{{ row.durationMs || 0 }} ms</template>
            </el-table-column>
            <el-table-column label="操作" width="110" fixed="right">
              <template #default="{ row }">
                <el-button link type="primary" @click="openAudit(row)">详情 / 审核</el-button>
              </template>
            </el-table-column>
          </el-table>
        </div>
        <div class="pager">
          <el-pagination background layout="prev, pager, next, total" :total="auditTotal"
                         :page-size="auditQuery.size" :current-page="auditQuery.page"
                         @current-change="loadAiOutputs" />
        </div>
      </el-tab-pane>

      <!-- AI 内容标识台账：《标识办法》传播侧合规核对 -->
      <el-tab-pane label="内容标识" name="labels">
        <div class="toolbar">
          <div class="toolbar-left">
            <el-select v-model="labelQuery.level" clearable placeholder="全部等级"
                       style="width: 132px" @change="loadLabels(1)">
              <el-option label="未使用 AI" value="none" />
              <el-option label="AI 辅助" value="assisted" />
              <el-option label="主要生成" value="generated" />
            </el-select>
            <el-input v-model="labelQuery.keyword" placeholder="文章标题" clearable
                      style="width: 220px" @keyup.enter="loadLabels(1)" @clear="loadLabels(1)" />
            <el-button @click="loadLabels(1)">查询</el-button>
          </div>
        </div>
        <div class="sheet">
          <el-table :data="labelItems" v-loading="loadingLabels">
            <el-table-column prop="title" label="标题" min-width="220" show-overflow-tooltip />
            <el-table-column label="状态" width="100">
              <template #default="{ row }">
                <el-tag size="small" effect="plain"
                        :type="row.status === 'published' ? 'success' : 'info'">
                  {{ articleStatusLabel(row.status) }}
                </el-tag>
              </template>
            </el-table-column>
            <el-table-column label="AI 参与" width="122">
              <template #default="{ row }">
                <el-tag size="small" effect="dark" :type="labelLevelType(row.aiUsageLevel)">
                  {{ labelLevelText(row.aiUsageLevel) }}
                </el-tag>
              </template>
            </el-table-column>
            <el-table-column label="内容编号" width="160">
              <template #default="{ row }">
                <span class="mono">{{ row.contentId }}</span>
              </template>
            </el-table-column>
            <el-table-column label="声明时间" width="150">
              <template #default="{ row }">
                {{ row.aiDeclaredAt ? formatDate(row.aiDeclaredAt) : '—' }}
              </template>
            </el-table-column>
            <el-table-column label="操作" width="110" fixed="right">
              <template #default="{ row }">
                <el-button link type="primary" @click="openLabel(row)">修正标识</el-button>
              </template>
            </el-table-column>
          </el-table>
        </div>
        <div class="pager">
          <el-pagination background layout="prev, pager, next, total" :total="labelTotal"
                         :page-size="labelQuery.size" :current-page="labelQuery.page"
                         @current-change="loadLabels" />
        </div>
      </el-tab-pane>

      <!-- 徽章审核：人类创作徽章人工复核 -->
      <el-tab-pane label="徽章审核" name="badges">
        <div class="toolbar">
          <el-radio-group v-model="badgeStatus" @change="loadBadges(1)">
            <el-radio-button value="pending">待审核</el-radio-button>
            <el-radio-button value="passed">已通过</el-radio-button>
            <el-radio-button value="rejected">已拒绝</el-radio-button>
          </el-radio-group>
        </div>
        <div class="sheet">
          <el-table :data="badgeItems" v-loading="loadingBadges">
            <el-table-column prop="id" label="ID" width="64" />
            <el-table-column prop="userNickname" label="申请人" min-width="130" />
            <el-table-column label="文章" width="110">
              <template #default="{ row }">
                <el-tooltip :content="(row.articleIds || []).join('、')" placement="top"
                            :disabled="!(row.articleIds || []).length">
                  <span>共 {{ (row.articleIds || []).length }} 篇</span>
                </el-tooltip>
              </template>
            </el-table-column>
            <el-table-column label="状态" width="100">
              <template #default="{ row }">
                <el-tag :type="badgeStatusType(row.status)" size="small" effect="plain">
                  {{ badgeStatusText(row.status) }}
                </el-tag>
              </template>
            </el-table-column>
            <el-table-column label="申请时间" width="160">
              <template #default="{ row }">{{ formatDate(row.createdAt) }}</template>
            </el-table-column>
            <el-table-column label="审核时间" width="160">
              <template #default="{ row }">{{ formatDate(row.reviewedAt) || '—' }}</template>
            </el-table-column>
            <el-table-column label="备注" min-width="160" show-overflow-tooltip>
              <template #default="{ row }">{{ row.note || '—' }}</template>
            </el-table-column>
            <el-table-column label="操作" width="140" fixed="right">
              <template #default="{ row }">
                <template v-if="row.status === 'pending'">
                  <el-button link type="success" @click="approveBadge(row)">通过</el-button>
                  <el-button link type="danger" @click="openRejectBadge(row)">拒绝</el-button>
                </template>
                <span v-else class="ban-until">-</span>
              </template>
            </el-table-column>
          </el-table>
        </div>
        <div class="pager">
          <el-pagination background layout="prev, pager, next, total" :total="badgeTotal"
                         :page-size="badgeQuery.size" :current-page="badgeQuery.page"
                         @current-change="loadBadges" />
        </div>
      </el-tab-pane>

      <!-- 伯乐管理 -->
      <el-tab-pane label="伯乐管理" name="curators">
        <div class="toolbar">
          <el-button type="primary" @click="openCuratorDialog">新增伯乐</el-button>
          <el-button @click="openCandidatesDrawer">查看候选池</el-button>
        </div>
        <div class="sheet">
          <el-table :data="curators" v-loading="loadingCurators" empty-text="暂无伯乐">
            <el-table-column prop="id" label="ID" width="64" />
            <el-table-column label="伯乐" min-width="160">
              <template #default="{ row }">
                <div class="curator-user">
                  <el-avatar :size="26" :src="row.avatar || undefined">
                    {{ (row.nickname || 'U').slice(0, 1) }}
                  </el-avatar>
                  <span>{{ row.nickname || '未设置昵称' }}</span>
                  <span class="mono">#{{ row.userId }}</span>
                </div>
              </template>
            </el-table-column>
            <el-table-column label="状态" width="96">
              <template #default="{ row }">
                <el-tag size="small" effect="plain" :type="row.status === 'active' ? 'success' : 'info'">
                  {{ row.status === 'active' ? '在任' : '已停用' }}
                </el-tag>
              </template>
            </el-table-column>
            <el-table-column label="当前生效" width="96">
              <template #default="{ row }">
                <el-tag v-if="row.active" type="success" size="small" effect="dark">生效中</el-tag>
                <span v-else class="ban-until">—</span>
              </template>
            </el-table-column>
            <el-table-column label="任期开始" width="120">
              <template #default="{ row }">{{ formatDate(row.termStart) || '—' }}</template>
            </el-table-column>
            <el-table-column label="任期结束" width="120">
              <template #default="{ row }">{{ formatDate(row.termEnd) || '不限' }}</template>
            </el-table-column>
            <el-table-column label="创建时间" width="160">
              <template #default="{ row }">{{ formatDate(row.createdAt) }}</template>
            </el-table-column>
            <el-table-column label="操作" width="90" fixed="right">
              <template #default="{ row }">
                <el-button link type="danger" @click="removeCurator(row)">停用</el-button>
              </template>
            </el-table-column>
          </el-table>
        </div>
      </el-tab-pane>

      <!-- 创作活动 -->
      <el-tab-pane label="创作活动" name="campaigns">
        <div class="toolbar">
          <el-button type="primary" @click="openCampaignDialog()">新建活动</el-button>
          <div class="toolbar-left">
            <el-select v-model="campaignQuery.status" clearable placeholder="全部状态"
                       style="width: 130px" @change="loadCampaigns(1)">
              <el-option label="草稿" value="draft" />
              <el-option label="进行中" value="active" />
              <el-option label="已结束" value="ended" />
            </el-select>
          </div>
        </div>
        <div class="sheet">
          <el-table :data="campaigns" v-loading="loadingCampaigns" empty-text="暂无创作活动">
            <el-table-column prop="id" label="ID" width="64" />
            <el-table-column prop="title" label="标题" min-width="190" show-overflow-tooltip />
            <el-table-column label="奖池" width="130" show-overflow-tooltip>
              <template #default="{ row }">{{ row.prizePool || '—' }}</template>
            </el-table-column>
            <el-table-column label="状态" width="96">
              <template #default="{ row }">
                <el-tag size="small" effect="plain" :type="campaignStatusType(row.status)">
                  {{ campaignStatusText(row.status) }}
                </el-tag>
              </template>
            </el-table-column>
            <el-table-column label="开始时间" width="160">
              <template #default="{ row }">{{ formatDate(row.startAt) }}</template>
            </el-table-column>
            <el-table-column label="结束时间" width="160">
              <template #default="{ row }">{{ formatDate(row.endAt) }}</template>
            </el-table-column>
            <el-table-column label="操作" width="200" fixed="right">
              <template #default="{ row }">
                <el-button link type="primary" @click="openCampaignDialog(row)">编辑</el-button>
                <el-button v-if="row.status !== 'active'" link type="warning"
                           @click="publishCampaign(row)">上线</el-button>
                <el-button link type="danger" @click="removeCampaign(row)">删除</el-button>
              </template>
            </el-table-column>
          </el-table>
        </div>
        <div class="pager">
          <el-pagination background layout="prev, pager, next, total" :total="campaignTotal"
                         :page-size="campaignQuery.size" :current-page="campaignQuery.page"
                         @current-change="loadCampaigns" />
        </div>
      </el-tab-pane>

      <!-- 技术语料：文档入库与术语表 -->
      <el-tab-pane label="技术语料" name="tech">
        <el-row :gutter="16" class="ai-stats tech-stats">
          <el-col :span="6">
            <div class="stat-card">
              <div class="stat-value">{{ techStats.docs }}</div>
              <div class="stat-label">语料文档</div>
            </div>
          </el-col>
          <el-col :span="6">
            <div class="stat-card">
              <div class="stat-value">{{ techStats.ready }}</div>
              <div class="stat-label">已就绪</div>
            </div>
          </el-col>
          <el-col :span="6">
            <div class="stat-card">
              <div class="stat-value">{{ techStats.failed }}</div>
              <div class="stat-label">解析失败</div>
            </div>
          </el-col>
          <el-col :span="6">
            <div class="stat-card">
              <div class="stat-value">{{ techStats.chunks }}</div>
              <div class="stat-label">语料块</div>
            </div>
          </el-col>
          <el-col :span="6">
            <div class="stat-card">
              <div class="stat-value">{{ techStats.assistCalls7d }}</div>
              <div class="stat-label">近 7 天建议调用</div>
            </div>
          </el-col>
          <el-col :span="6">
            <div class="stat-card">
              <div class="stat-value">{{ techStats.adopted7d }}</div>
              <div class="stat-label">近 7 天采纳</div>
            </div>
          </el-col>
          <el-col :span="6">
            <div class="stat-card">
              <div class="stat-value">{{ techStats.failureRate }}%</div>
              <div class="stat-label">解析失败率</div>
            </div>
          </el-col>
        </el-row>

        <el-tabs v-model="techTab" type="card" class="tech-subtabs">
          <el-tab-pane label="语料文档" name="docs">
            <div class="toolbar">
              <div class="toolbar-left">
                <el-input v-model="techDocKeyword" placeholder="搜索标题/来源" clearable
                          style="width: 220px" @keyup.enter="loadTechDocs(1)">
                  <template #append>
                    <el-button @click="loadTechDocs(1)"><el-icon><Search /></el-icon></el-button>
                  </template>
                </el-input>
                <el-select v-model="techDocStatus" style="width: 130px"
                           @change="loadTechDocs(1)">
                  <el-option label="全部状态" value="" />
                  <el-option label="解析中" value="parsing" />
                  <el-option label="已就绪" value="ready" />
                  <el-option label="失败" value="failed" />
                </el-select>
              </div>
              <div class="toolbar-left">
                <el-button @click="openPasteDialog">粘贴入库</el-button>
                <el-button type="primary" @click="openUploadDialog">上传文件</el-button>
              </div>
            </div>
            <div class="sheet">
              <el-table :data="techDocs" v-loading="loadingTechDocs">
                <el-table-column prop="id" label="ID" width="64" />
                <el-table-column label="标题" min-width="200" show-overflow-tooltip>
                  <template #default="{ row }">{{ row.title }}</template>
                </el-table-column>
                <el-table-column label="类型" width="120">
                  <template #default="{ row }">
                    <span class="mono">{{ row.fileType || row.docType || '-' }}</span>
                  </template>
                </el-table-column>
                <el-table-column label="来源 / 版本" min-width="150" show-overflow-tooltip>
                  <template #default="{ row }">
                    {{ row.sourceName || '-' }}<span v-if="row.version"> · {{ row.version }}</span>
                  </template>
                </el-table-column>
                <el-table-column prop="chunkCount" label="块数" width="80" />
                <el-table-column label="状态" width="100">
                  <template #default="{ row }">
                    <el-tooltip :disabled="!row.errorMessage" :content="row.errorMessage || ''">
                      <el-tag :type="techDocStatusType(row.status)" size="small" effect="plain">
                        {{ techDocStatusLabel(row.status) }}
                      </el-tag>
                    </el-tooltip>
                  </template>
                </el-table-column>
                <el-table-column label="更新时间" width="150">
                  <template #default="{ row }">{{ formatDate(row.updatedAt || row.createdAt) }}</template>
                </el-table-column>
                <el-table-column label="操作" width="210">
                  <template #default="{ row }">
                    <el-button link type="primary" @click="viewTechChunks(row)">查看块</el-button>
                    <el-button link type="primary" :disabled="row.status !== 'ready'"
                               @click="rebuildTechIndex(row)">重建索引</el-button>
                    <el-button link type="danger" @click="removeTechDoc(row)">删除</el-button>
                  </template>
                </el-table-column>
              </el-table>
            </div>
            <div class="pager">
              <el-pagination background layout="prev, pager, next, total" :total="techDocTotal"
                             :page-size="techDocQuery.size" :current-page="techDocQuery.page"
                             @current-change="loadTechDocs" />
            </div>
          </el-tab-pane>

          <el-tab-pane label="术语表" name="terms">
            <div class="toolbar">
              <div class="toolbar-left">
                <el-input v-model="techTermKeyword" placeholder="搜索规范写法/释义/变体" clearable
                          style="width: 260px" @keyup.enter="loadTechTerms">
                  <template #append>
                    <el-button @click="loadTechTerms"><el-icon><Search /></el-icon></el-button>
                  </template>
                </el-input>
              </div>
              <div class="toolbar-left">
                <el-button @click="openTermImport">JSON 导入</el-button>
                <el-button type="primary" @click="openTermDialog()">新增术语</el-button>
              </div>
            </div>
            <div class="sheet">
              <el-table :data="techTerms" v-loading="loadingTechTerms">
                <el-table-column prop="canonical" label="规范写法" min-width="150" />
                <el-table-column prop="category" label="分类" width="120" />
                <el-table-column prop="variants" label="变体写法" min-width="220"
                                 show-overflow-tooltip />
                <el-table-column label="大小写" width="90">
                  <template #default="{ row }">
                    {{ row.caseSensitive ? '区分' : '不区分' }}
                  </template>
                </el-table-column>
                <el-table-column label="状态" width="90">
                  <template #default="{ row }">
                    <el-tag :type="row.status === 'active' ? 'success' : 'info'"
                            size="small" effect="plain">
                      {{ row.status === 'active' ? '启用' : '停用' }}
                    </el-tag>
                  </template>
                </el-table-column>
                <el-table-column label="操作" width="140">
                  <template #default="{ row }">
                    <el-button link type="primary" @click="openTermDialog(row)">编辑</el-button>
                    <el-button link type="danger" @click="removeTechTerm(row)">删除</el-button>
                  </template>
                </el-table-column>
              </el-table>
            </div>
          </el-tab-pane>
        </el-tabs>
      </el-tab-pane>

      <!-- 操作日志：仅超级管理员 -->
      <el-tab-pane v-if="isSuper" label="操作日志" name="logs">
        <div class="toolbar">
          <div class="toolbar-left">
            <el-select v-model="logAction" style="width: 160px" placeholder="全部动作" clearable
                       @change="loadLogs(1)">
              <el-option v-for="a in ACTIONS" :key="a.value" :label="a.label" :value="a.value" />
            </el-select>
          </div>
        </div>
        <div class="sheet">
          <el-table :data="logs" v-loading="loadingLogs">
            <el-table-column label="时间" width="170">
              <template #default="{ row }">{{ formatDate(row.createdAt) }}</template>
            </el-table-column>
            <el-table-column prop="adminUsername" label="管理员" width="120" />
            <el-table-column label="动作" width="120">
              <template #default="{ row }">
                <el-tag size="small" effect="plain">{{ actionLabel(row.action) }}</el-tag>
              </template>
            </el-table-column>
            <el-table-column prop="target" label="目标" min-width="150" />
            <el-table-column prop="detail" label="详情" min-width="260" show-overflow-tooltip />
          </el-table>
        </div>
        <div class="pager">
          <el-pagination background layout="prev, pager, next, total" :total="logTotal"
                         :page-size="logQuery.size" :current-page="logQuery.page"
                         @current-change="loadLogs" />
        </div>
      </el-tab-pane>
    </el-tabs>

    <!-- 标识修正弹窗 -->
    <el-dialog v-model="labelDialogVisible" title="修正 AI 内容标识" width="480px">
      <el-form label-width="92px">
        <el-form-item label="文章">
          <span>{{ labelForm.title }}</span>
        </el-form-item>
        <el-form-item label="参与等级">
          <el-radio-group v-model="labelForm.aiUsageLevel">
            <el-radio value="none">未使用 AI</el-radio>
            <el-radio value="assisted">AI 辅助</el-radio>
            <el-radio value="generated">主要生成</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="修正原因">
          <el-input v-model="labelForm.reason" type="textarea" :rows="3"
                    maxlength="200" show-word-limit placeholder="必填，将写入标识变更日志" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="labelDialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="labelSaving" @click="submitLabel">保存</el-button>
      </template>
    </el-dialog>

    <!-- 分类弹窗 -->
    <el-dialog v-model="categoryDialogVisible" :title="editingCategory ? '重命名分类' : '新建分类'" width="420px">
      <el-form label-width="60px">
        <el-form-item label="名称">
          <el-input v-model="categoryForm.name" maxlength="64" placeholder="分类名" />
        </el-form-item>
        <el-form-item label="描述">
          <el-input v-model="categoryForm.description" maxlength="255" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="categoryDialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="savingCategory" @click="saveCategory">保存</el-button>
      </template>
    </el-dialog>

    <!-- 新增伯乐弹窗 -->
    <el-dialog v-model="curatorDialog.visible" title="新增伯乐" width="460px">
      <el-form label-width="86px">
        <el-form-item label="用户 ID" required>
          <el-input-number v-model="curatorDialog.userId" :min="1" controls-position="right"
                           style="width: 100%" />
        </el-form-item>
        <el-form-item label="任期开始">
          <el-date-picker v-model="curatorDialog.termStart" type="date" value-format="YYYY-MM-DD"
                          placeholder="yyyy-MM-dd" style="width: 100%" />
        </el-form-item>
        <el-form-item label="任期结束">
          <el-date-picker v-model="curatorDialog.termEnd" type="date" value-format="YYYY-MM-DD"
                          placeholder="留空表示不限" style="width: 100%" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="curatorDialog.visible = false">取消</el-button>
        <el-button type="primary" :loading="curatorDialog.saving" @click="saveCurator">确定</el-button>
      </template>
    </el-dialog>

    <!-- 运营候选池导出 -->
    <el-drawer v-model="candidatesDrawer.visible" title="运营候选池" size="720px">
      <el-alert type="warning" :closable="false" show-icon
                title="AI 仅提供排序，需人工确认" class="candidates-note" />
      <el-table :data="candidatesDrawer.items" v-loading="candidatesDrawer.loading">
        <el-table-column label="标题" min-width="180" show-overflow-tooltip>
          <template #default="{ row }">
            <el-link type="primary" @click="$router.push(`/article/${row.articleId}`)">
              {{ row.title }}
            </el-link>
          </template>
        </el-table-column>
        <el-table-column label="作者" width="104">
          <template #default="{ row }">{{ row.authorNickname || '佚名' }}</template>
        </el-table-column>
        <el-table-column prop="viewCount" label="阅读量" width="82" />
        <el-table-column label="完读率" width="88">
          <template #default="{ row }">{{ row.completionRate || 0 }}%</template>
        </el-table-column>
        <el-table-column label="AI 参与" width="104">
          <template #default="{ row }">{{ aiUsageLabel(row.aiUsageLevel) }}</template>
        </el-table-column>
        <el-table-column prop="assistScore" label="辅助度" width="82" />
        <el-table-column label="人类徽章" width="92">
          <template #default="{ row }">
            <el-tag v-if="row.humanBadge" type="success" size="small" effect="plain">有</el-tag>
            <span v-else class="ban-until">—</span>
          </template>
        </el-table-column>
      </el-table>
    </el-drawer>

    <!-- 创作活动表单 -->
    <el-dialog v-model="campaignDialog.visible" :title="campaignDialog.id ? '编辑创作活动' : '新建创作活动'"
               width="560px">
      <el-form label-width="86px">
        <el-form-item label="标题" required>
          <el-input v-model="campaignDialog.form.title" maxlength="120" />
        </el-form-item>
        <el-form-item label="活动说明">
          <el-input v-model="campaignDialog.form.description" type="textarea" :rows="3"
                    maxlength="500" show-word-limit />
        </el-form-item>
        <el-form-item label="奖池">
          <el-input v-model="campaignDialog.form.prizePool" maxlength="120"
                    placeholder="如：现金 5000 元 + 站内推荐位" />
        </el-form-item>
        <el-form-item label="参与条件">
          <el-input v-model="campaignDialog.form.joinRule" type="textarea" :rows="2"
                    maxlength="500" show-word-limit />
        </el-form-item>
        <el-form-item label="评选方式">
          <el-input v-model="campaignDialog.form.judgeRule" type="textarea" :rows="2"
                    maxlength="500" show-word-limit />
        </el-form-item>
        <el-form-item label="开始时间" required>
          <el-date-picker v-model="campaignDialog.form.startAt" type="datetime"
                          value-format="YYYY-MM-DDTHH:mm:ss" format="YYYY-MM-DD HH:mm:ss"
                          placeholder="选择开始时间" style="width: 100%" />
        </el-form-item>
        <el-form-item label="结束时间" required>
          <el-date-picker v-model="campaignDialog.form.endAt" type="datetime"
                          value-format="YYYY-MM-DDTHH:mm:ss" format="YYYY-MM-DD HH:mm:ss"
                          placeholder="选择结束时间" style="width: 100%" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="campaignDialog.visible = false">取消</el-button>
        <el-button type="primary" :loading="campaignDialog.saving" @click="saveCampaign">保存</el-button>
      </template>
    </el-dialog>

    <!-- 封禁弹窗：原因必填，可选期限 -->
    <el-dialog v-model="banDialog.visible" width="460px"
               :title="banDialog.row?.status === 1 ? '解封用户' : '封禁用户'">
      <template v-if="banDialog.row?.status === 1">
        <p class="ban-confirm">
          确定解封用户「<b>{{ banDialog.row?.username }}</b>」吗？解封后可正常登录与发文。
        </p>
      </template>
      <template v-else>
        <el-form label-width="80px">
          <el-form-item label="用户">
            <span>{{ banDialog.row?.username }}（{{ banDialog.row?.nickname || '未设置昵称' }}）</span>
          </el-form-item>
          <el-form-item label="封禁原因" required>
            <el-input v-model="banDialog.reason" type="textarea" :rows="2" maxlength="200"
                      show-word-limit placeholder="封禁原因将展示给被封用户" />
          </el-form-item>
          <el-form-item label="期限">
            <el-radio-group v-model="banDialog.untilType">
              <el-radio value="permanent">永久</el-radio>
              <el-radio value="custom">至指定时间</el-radio>
            </el-radio-group>
          </el-form-item>
          <el-form-item v-if="banDialog.untilType === 'custom'" label="到期时间">
            <el-date-picker v-model="banDialog.until" type="datetime" format="YYYY-MM-DD HH:mm"
                            value-format="YYYY-MM-DD HH:mm" placeholder="选择封禁截止时间" />
          </el-form-item>
        </el-form>
      </template>
      <template #footer>
        <el-button @click="banDialog.visible = false">取消</el-button>
        <el-button type="primary" :loading="banDialog.saving" @click="confirmBan">确定</el-button>
      </template>
    </el-dialog>

    <!-- 徽章拒绝弹窗：理由必填 -->
    <el-dialog v-model="badgeReject.visible" title="拒绝徽章申请" width="440px">
      <el-form label-width="80px">
        <el-form-item label="申请人">
          <span>{{ badgeReject.row?.userNickname || badgeReject.row?.userId || '-' }}</span>
        </el-form-item>
        <el-form-item label="拒绝理由" required>
          <el-input v-model="badgeReject.note" type="textarea" :rows="3" maxlength="200"
                    show-word-limit placeholder="理由将展示给申请人" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="badgeReject.visible = false">取消</el-button>
        <el-button type="danger" :loading="badgeReject.saving" @click="confirmRejectBadge">确定拒绝</el-button>
      </template>
    </el-dialog>

    <!-- AI 输出详情与人工审核 -->
    <el-drawer v-model="auditDrawer.visible" title="AI 输出详情" size="640px">
      <div v-loading="auditDrawer.loading" class="audit-body">
        <el-descriptions :column="2" border size="small">
          <el-descriptions-item label="追踪号">{{ auditDrawer.detail.traceId || '-' }}</el-descriptions-item>
          <el-descriptions-item label="动作">
            {{ auditActionLabel(auditDrawer.detail.action) }}
          </el-descriptions-item>
          <el-descriptions-item label="任务">{{ auditDrawer.detail.task || '-' }}</el-descriptions-item>
          <el-descriptions-item label="来源">{{ auditDrawer.detail.source || '-' }}</el-descriptions-item>
          <el-descriptions-item label="用户 ID">{{ auditDrawer.detail.userId || '-' }}</el-descriptions-item>
          <el-descriptions-item label="文章 ID">{{ auditDrawer.detail.articleId || '-' }}</el-descriptions-item>
          <el-descriptions-item label="模型">{{ auditDrawer.detail.model || '-' }}</el-descriptions-item>
          <el-descriptions-item label="提示词版本">
            {{ auditDrawer.detail.promptVersion || '-' }}
          </el-descriptions-item>
          <el-descriptions-item label="终态">
            <el-tag size="small" effect="plain" :type="auditStatusType(auditDrawer.detail.status)">
              {{ auditStatusLabel(auditDrawer.detail.status) }}
            </el-tag>
          </el-descriptions-item>
          <el-descriptions-item label="审核态">
            <el-tag size="small" effect="plain" :type="auditReviewType(auditDrawer.detail.reviewStatus)">
              {{ auditReviewLabel(auditDrawer.detail.reviewStatus) }}
            </el-tag>
          </el-descriptions-item>
          <el-descriptions-item label="Token">
            {{ auditDrawer.detail.tokensIn || 0 }} / {{ auditDrawer.detail.tokensOut || 0 }}
          </el-descriptions-item>
          <el-descriptions-item label="耗时">
            {{ auditDrawer.detail.durationMs || 0 }} ms
          </el-descriptions-item>
          <el-descriptions-item label="是否采纳">
            {{ auditDrawer.detail.adopted ? '已采纳' : '未采纳' }}
          </el-descriptions-item>
          <el-descriptions-item label="结束时间">
            {{ formatDate(auditDrawer.detail.finishedAt) || '-' }}
          </el-descriptions-item>
          <el-descriptions-item v-if="auditDrawer.detail.errorCode" label="错误码">
            {{ auditDrawer.detail.errorCode }}
          </el-descriptions-item>
          <el-descriptions-item v-if="auditDrawer.detail.errorMessage" label="错误信息" :span="2">
            {{ auditDrawer.detail.errorMessage }}
          </el-descriptions-item>
        </el-descriptions>

        <div class="audit-section">
          <div class="audit-section-title">AI 返回内容（完整保存）</div>
          <pre class="audit-pre">{{ auditDrawer.detail.output || '（无返回内容）' }}</pre>
        </div>

        <div class="audit-section">
          <div class="audit-section-title">
            请求快照
            <span v-if="auditDrawer.detail.requestDigest" class="mono audit-digest">
              SHA-256 {{ auditDrawer.detail.requestDigest.slice(0, 16) }}…
            </span>
          </div>
          <pre class="audit-pre">{{ prettySnapshot }}</pre>
        </div>

        <div class="audit-section">
          <div class="audit-section-title">人工审核</div>
          <el-input v-model="auditDrawer.note" type="textarea" :rows="2" maxlength="500"
                    show-word-limit placeholder="审核备注（可选）" />
          <div class="audit-review-actions">
            <el-button type="success" :loading="auditDrawer.saving"
                       @click="submitAuditReview('passed')">通过</el-button>
            <el-button type="danger" :loading="auditDrawer.saving"
                       @click="submitAuditReview('flagged')">标记问题</el-button>
            <el-button :loading="auditDrawer.saving"
                       @click="submitAuditReview('pending')">重置待审</el-button>
          </div>
          <div v-if="auditDrawer.detail.reviewedAt" class="audit-reviewed">
            最近审核：{{ formatDate(auditDrawer.detail.reviewedAt) }}
            <span v-if="auditDrawer.detail.reviewNote"> · {{ auditDrawer.detail.reviewNote }}</span>
          </div>
        </div>
      </div>
    </el-drawer>

    <!-- 粘贴入库 -->
    <el-dialog v-model="techPaste.visible" title="粘贴入库" width="640px">
      <el-form label-width="86px">
        <el-form-item label="标题" required>
          <el-input v-model="techPaste.form.title" maxlength="200" />
        </el-form-item>
        <el-form-item label="文档类型">
          <el-select v-model="techPaste.form.docType" style="width: 180px">
            <el-option label="指南" value="guide" />
            <el-option label="文章" value="article" />
            <el-option label="FAQ" value="faq" />
            <el-option label="API" value="api" />
            <el-option label="规范" value="spec" />
          </el-select>
        </el-form-item>
        <el-form-item label="来源名称">
          <el-input v-model="techPaste.form.sourceName" maxlength="200"
                    placeholder="如：官方文档 / 团队规范" />
        </el-form-item>
        <el-form-item label="版本号">
          <el-input v-model="techPaste.form.version" maxlength="64" placeholder="如 v2.1" />
        </el-form-item>
        <el-form-item label="授权方式">
          <el-input v-model="techPaste.form.license" maxlength="120" />
        </el-form-item>
        <el-form-item label="正文格式">
          <el-radio-group v-model="techPaste.form.fileType">
            <el-radio value="markdown">Markdown</el-radio>
            <el-radio value="text">纯文本</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="正文" required>
          <el-input v-model="techPaste.form.content" type="textarea" :rows="10"
                    maxlength="500000" show-word-limit
                    placeholder="粘贴技术文档正文，解析成功后自动切块入库" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="techPaste.visible = false">取消</el-button>
        <el-button type="primary" :loading="techPaste.saving" @click="saveTechPaste">入库</el-button>
      </template>
    </el-dialog>

    <!-- 上传文件 -->
    <el-dialog v-model="techUpload.visible" title="上传技术资料" width="580px">
      <el-form label-width="86px">
        <el-form-item label="文件">
          <el-upload v-model:file-list="techUpload.files" :auto-upload="false" :limit="1"
                     accept=".md,.txt,.pdf" :on-change="onTechFileChange" drag style="width: 100%">
            <el-icon><UploadFilled /></el-icon>
            <div class="el-upload__text">拖拽文件到此处，或<em>点击选择</em></div>
            <template #tip>
              <div class="upload-tip">
                支持 Markdown / 纯文本 / PDF（需有文本层），单文件 ≤30MB、PDF ≤500 页；
                加密或扫描件 PDF 会标记为失败并给出原因。
              </div>
            </template>
          </el-upload>
        </el-form-item>
        <el-form-item label="标题">
          <el-input v-model="techUpload.form.title" maxlength="200" placeholder="留空则使用文件名" />
        </el-form-item>
        <el-form-item label="文档类型">
          <el-select v-model="techUpload.form.docType" style="width: 180px">
            <el-option label="指南" value="guide" />
            <el-option label="文章" value="article" />
            <el-option label="FAQ" value="faq" />
            <el-option label="API" value="api" />
            <el-option label="规范" value="spec" />
          </el-select>
        </el-form-item>
        <el-form-item label="来源名称">
          <el-input v-model="techUpload.form.sourceName" maxlength="200" />
        </el-form-item>
        <el-form-item label="版本号">
          <el-input v-model="techUpload.form.version" maxlength="64" />
        </el-form-item>
        <el-form-item label="授权方式">
          <el-input v-model="techUpload.form.license" maxlength="120" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="techUpload.visible = false">取消</el-button>
        <el-button type="primary" :loading="techUpload.saving" @click="submitTechUpload">上传</el-button>
      </template>
    </el-dialog>

    <!-- 语料块 -->
    <el-drawer v-model="techChunks.visible" :title="`语料块 · ${techChunks.title}`" size="640px">
      <div v-loading="techChunks.loading">
        <el-empty v-if="!techChunks.items.length" description="暂无切块" />
        <div v-for="chunk in techChunks.items" :key="chunk.id" class="chunk-card">
          <div class="chunk-meta">
            #{{ chunk.chunkIndex }}
            <span v-if="chunk.pageNo"> · 第 {{ chunk.pageNo }} 页</span>
            <span v-if="chunk.sectionPath"> · {{ chunk.sectionPath }}</span>
            <span v-if="chunk.tokenCount"> · {{ chunk.tokenCount }} tokens</span>
          </div>
          <pre class="chunk-text">{{ chunk.content }}</pre>
        </div>
      </div>
    </el-drawer>

    <!-- 术语编辑 -->
    <el-dialog v-model="techTermDialog.visible"
               :title="techTermDialog.id ? '编辑术语' : '新增术语'" width="520px">
      <el-form label-width="90px">
        <el-form-item label="规范写法" required>
          <el-input v-model="techTermDialog.form.canonical" maxlength="120" />
        </el-form-item>
        <el-form-item label="分类">
          <el-input v-model="techTermDialog.form.category" maxlength="64" placeholder="如：数据库" />
        </el-form-item>
        <el-form-item label="变体写法">
          <el-input v-model="techTermDialog.form.variants" type="textarea" :rows="2"
                    maxlength="1000" placeholder="禁用/异体写法，逗号、顿号或换行分隔" />
        </el-form-item>
        <el-form-item label="释义">
          <el-input v-model="techTermDialog.form.definition" type="textarea" :rows="3"
                    maxlength="1000" show-word-limit />
        </el-form-item>
        <el-form-item label="区分大小写">
          <el-switch v-model="techTermDialog.form.caseSensitive" />
        </el-form-item>
        <el-form-item label="状态">
          <el-radio-group v-model="techTermDialog.form.status">
            <el-radio value="active">启用</el-radio>
            <el-radio value="disabled">停用</el-radio>
          </el-radio-group>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="techTermDialog.visible = false">取消</el-button>
        <el-button type="primary" :loading="techTermDialog.saving" @click="saveTechTerm">保存</el-button>
      </template>
    </el-dialog>

    <!-- 术语 JSON 导入 -->
    <el-dialog v-model="techTermImport.visible" title="JSON 导入术语" width="560px">
      <el-alert type="info" :closable="false" show-icon class="import-tip"
                title="提交数组；canonical 必填，同规范写法+分类将覆盖更新" />
      <el-input v-model="techTermImport.json" type="textarea" :rows="10"
                placeholder='[{"canonical":"校验和","category":"存储","variants":"checksum","status":"active"}]' />
      <template #footer>
        <el-button @click="techTermImport.visible = false">取消</el-button>
        <el-button type="primary" :loading="techTermImport.saving" @click="submitTermImport">导入</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { computed, onMounted, reactive, ref, watch } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { adminApi, categoryApi, techAdminApi } from '../api'
import { useAuthStore } from '../stores/auth'

const auth = useAuthStore()
const isSuper = computed(() => auth.user?.role === 'ADMIN')
const isContent = computed(() => auth.user?.role === 'CONTENT_ADMIN')

const activeTab = ref('categories')

/* ---------- 分类管理 ---------- */
const categories = ref([])
const loadingCategories = ref(false)
const categoryDialogVisible = ref(false)
const editingCategory = ref(null)
const savingCategory = ref(false)
const categoryForm = reactive({ name: '', description: '' })

async function loadCategories() {
  loadingCategories.value = true
  try {
    categories.value = await categoryApi.list()
  } finally {
    loadingCategories.value = false
  }
}

function openCategoryDialog(row = null) {
  editingCategory.value = row
  categoryForm.name = row?.name || ''
  categoryForm.description = row?.description || ''
  categoryDialogVisible.value = true
}

async function saveCategory() {
  if (!categoryForm.name.trim()) {
    ElMessage.warning('分类名不能为空')
    return
  }
  savingCategory.value = true
  try {
    if (editingCategory.value) {
      await categoryApi.rename(editingCategory.value.id, { ...categoryForm })
    } else {
      await categoryApi.create({ ...categoryForm })
    }
    categoryDialogVisible.value = false
    ElMessage.success('已保存')
    loadCategories()
  } finally {
    savingCategory.value = false
  }
}

async function removeCategory(row) {
  await ElMessageBox.confirm(`确定删除分类「${row.name}」吗？分类下仍有文章时不可删除。`, '提示',
    { type: 'warning' })
  await categoryApi.remove(row.id)
  ElMessage.success('已删除')
  loadCategories()
}

/* ---------- 用户管理（仅超级管理员） ---------- */
const users = ref([])
const userTotal = ref(0)
const userKeyword = ref('')
const userStatusFilter = ref('')
const loadingUsers = ref(false)
const userQuery = reactive({ page: 1, size: 10 })

async function loadUsers(page = userQuery.page) {
  userQuery.page = page
  loadingUsers.value = true
  try {
    const data = await adminApi.listUsers({
      page: userQuery.page,
      size: userQuery.size,
      keyword: userKeyword.value || undefined,
      status: userStatusFilter.value === '' ? undefined : userStatusFilter.value
    })
    users.value = data.items
    userTotal.value = data.total
  } finally {
    loadingUsers.value = false
  }
}

const banDialog = reactive({
  visible: false, saving: false, row: null,
  reason: '', untilType: 'permanent', until: ''
})

function openBanDialog(row) {
  banDialog.row = row
  banDialog.reason = ''
  banDialog.untilType = 'permanent'
  banDialog.until = ''
  banDialog.visible = true
}

async function confirmBan() {
  const row = banDialog.row
  if (row.status !== 1) {
    if (!banDialog.reason.trim()) {
      ElMessage.warning('请填写封禁原因')
      return
    }
    if (banDialog.untilType === 'custom' && !banDialog.until) {
      ElMessage.warning('请选择到期时间')
      return
    }
  }
  banDialog.saving = true
  try {
    if (row.status === 1) {
      await adminApi.setUserStatus(row.id, { status: 0 })
      ElMessage.success('已解封')
    } else {
      await adminApi.setUserStatus(row.id, {
        status: 1,
        reason: banDialog.reason.trim(),
        until: banDialog.untilType === 'custom' ? banDialog.until : undefined
      })
      ElMessage.success('已封禁')
    }
    banDialog.visible = false
    loadUsers()
  } finally {
    banDialog.saving = false
  }
}

async function toggleRole(row) {
  const to = row.role === 'CONTENT_ADMIN' ? 'USER' : 'CONTENT_ADMIN'
  const label = to === 'CONTENT_ADMIN' ? '设为内容管理员' : '收回内容管理权限'
  await ElMessageBox.confirm(`确定将「${row.username}」${label}吗？`, '提示', { type: 'warning' })
  await adminApi.setUserRole(row.id, to)
  ElMessage.success('角色已更新')
  loadUsers()
}

/* ---------- 内容批量管理 ---------- */
const contentKind = ref('articles')
const contentItems = ref([])
const contentTotal = ref(0)
const contentKeyword = ref('')
const contentStatus = ref('')
const loadingContent = ref(false)
const contentQuery = reactive({ page: 1, size: 10 })
const selectedRows = ref([])

function onSelectionChange(rows) {
  selectedRows.value = rows
}

async function loadContent(page = contentQuery.page) {
  contentQuery.page = page
  loadingContent.value = true
  selectedRows.value = []
  try {
    if (contentKind.value === 'articles') {
      const data = await adminApi.listArticles({
        page: contentQuery.page,
        size: contentQuery.size,
        status: contentStatus.value || undefined,
        keyword: contentKeyword.value || undefined
      })
      contentItems.value = data.items
      contentTotal.value = data.total
    } else {
      const data = await adminApi.listComments({
        page: contentQuery.page,
        size: contentQuery.size,
        keyword: contentKeyword.value || undefined
      })
      contentItems.value = data.items
      contentTotal.value = data.total
    }
  } finally {
    loadingContent.value = false
  }
}

async function batchUnpublish() {
  const ids = selectedRows.value.map(r => r.id)
  await ElMessageBox.confirm(`确定将选中的 ${ids.length} 篇文章下架为草稿吗？`, '批量下架',
    { type: 'warning' })
  const { count } = await adminApi.batchUnpublishArticles(ids)
  ElMessage.success(`已下架 ${count} 篇`)
  loadContent()
}

async function batchDeleteContent() {
  const ids = selectedRows.value.map(r => r.id)
  const unit = contentKind.value === 'articles' ? '篇文章' : '条评论'
  await ElMessageBox.confirm(
    `确定删除选中的 ${ids.length} ${unit}吗？删除后不可恢复。`, '批量删除', { type: 'warning' })
  const { count } = contentKind.value === 'articles'
    ? await adminApi.batchDeleteArticles(ids)
    : await adminApi.batchDeleteComments(ids)
  ElMessage.success(`已删除 ${count} ${unit}`)
  loadContent()
}

/* ---------- 举报处理 ---------- */
const reports = ref([])
const reportTotal = ref(0)
const reportStatus = ref('pending')
const loadingReports = ref(false)
const pendingReportCount = ref(0)
const reportQuery = reactive({ page: 1, size: 10 })

async function loadReports(page = reportQuery.page) {
  reportQuery.page = page
  loadingReports.value = true
  try {
    const data = await adminApi.listReports({
      page: reportQuery.page,
      size: reportQuery.size,
      status: reportStatus.value || undefined
    })
    reports.value = data.items || []
    reportTotal.value = data.total || 0
  } finally {
    loadingReports.value = false
  }
  loadPendingReports()
}

async function loadPendingReports() {
  try {
    const data = await adminApi.pendingReports()
    pendingReportCount.value = data?.count || 0
  } catch {
    pendingReportCount.value = 0
  }
}

async function handleReport(row, action) {
  const verb = action === 'delete' ? '删除被举报内容' : '忽略该举报'
  await ElMessageBox.confirm(`确定${verb}吗？`, '处理举报', { type: 'warning' })
  await adminApi.handleReport(row.id, action)
  ElMessage.success('已处理')
  loadReports()
}

/* ---------- AI 治理（仅超级管理员） ---------- */
const aiDays = ref(14)
const loadingAiUsage = ref(false)
const aiUsage = ref({})
const adoptionRateText = computed(() => rateText(aiUsage.value.adopted, aiUsage.value.total))

function rateText(adopted, total) {
  if (!total) return '0%'
  return `${Math.round((adopted || 0) * 100 / total)}%`
}

function aiActionLabel(action) {
  return action === 'generate' ? '生成' : action === 'review' ? '审阅' : action
}

async function loadAiUsage() {
  loadingAiUsage.value = true
  try {
    aiUsage.value = await adminApi.aiUsage({ days: aiDays.value })
  } catch {
    aiUsage.value = {}
  } finally {
    loadingAiUsage.value = false
  }
}

/* ---------- AI 输出审计（仅超级管理员） ---------- */
const auditItems = ref([])
const auditTotal = ref(0)
const auditPending = ref(0)
const loadingAudit = ref(false)
const auditQuery = reactive({
  page: 1, size: 10, action: '', status: '', reviewStatus: '', keyword: ''
})
const auditDrawer = reactive({
  visible: false, loading: false, saving: false, detail: {}, note: ''
})

const prettySnapshot = computed(() => {
  const raw = auditDrawer.detail.requestSnapshot
  if (!raw) return '（无请求快照）'
  try {
    return JSON.stringify(JSON.parse(raw), null, 2)
  } catch {
    return raw
  }
})

async function loadAiOutputs(page = auditQuery.page) {
  auditQuery.page = page
  loadingAudit.value = true
  try {
    const data = await adminApi.aiOutputs({
      page: auditQuery.page,
      size: auditQuery.size,
      action: auditQuery.action || undefined,
      status: auditQuery.status || undefined,
      reviewStatus: auditQuery.reviewStatus || undefined,
      keyword: auditQuery.keyword || undefined
    })
    auditItems.value = data.items || []
    auditTotal.value = data.total || 0
  } finally {
    loadingAudit.value = false
  }
  loadAuditPending()
}

async function loadAuditPending() {
  try {
    const data = await adminApi.pendingAiOutputs()
    auditPending.value = data?.count || 0
  } catch {
    auditPending.value = 0
  }
}

async function openAudit(row) {
  auditDrawer.visible = true
  auditDrawer.loading = true
  auditDrawer.note = ''
  auditDrawer.detail = { ...row }
  try {
    auditDrawer.detail = await adminApi.aiOutput(row.id)
    auditDrawer.note = auditDrawer.detail.reviewNote || ''
  } finally {
    auditDrawer.loading = false
  }
}

async function submitAuditReview(status) {
  const id = auditDrawer.detail?.id
  if (!id) return
  auditDrawer.saving = true
  try {
    auditDrawer.detail = await adminApi.reviewAiOutput(id, {
      status,
      note: auditDrawer.note || ''
    })
    ElMessage.success(
      status === 'passed' ? '已通过'
        : status === 'flagged' ? '已标记问题'
          : '已重置为待审')
    loadAiOutputs()
  } finally {
    auditDrawer.saving = false
  }
}

function auditActionLabel(action) {
  return { generate: '生成', review: '审阅', search: '搜索', care: '关怀检测' }[action]
    || action || '-'
}

function auditStatusType(status) {
  if (status === 'success') return 'success'
  if (status === 'failed') return 'danger'
  if (status === 'streaming') return 'warning'
  return 'info'
}

function auditStatusLabel(status) {
  return { success: '成功', failed: '失败', canceled: '已取消', streaming: '进行中' }[status]
    || status || '-'
}

function auditReviewType(status) {
  if (status === 'passed') return 'success'
  if (status === 'flagged') return 'danger'
  return 'warning'
}

function auditReviewLabel(status) {
  return { pending: '待审核', passed: '已通过', flagged: '已标记' }[status]
    || status || '-'
}

function shortTrace(traceId) {
  if (!traceId) return '-'
  return traceId.length > 12 ? `${traceId.slice(0, 8)}…${traceId.slice(-4)}` : traceId
}

/* ---------- AI 内容标识台账 ---------- */
const labelQuery = reactive({ page: 1, size: 10, level: '', keyword: '' })
const labelItems = ref([])
const labelTotal = ref(0)
const loadingLabels = ref(false)
const labelDialogVisible = ref(false)
const labelSaving = ref(false)
const labelForm = reactive({ id: null, title: '', aiUsageLevel: 'none', reason: '' })

function labelLevelText(level) {
  return { none: '未使用 AI', assisted: 'AI 辅助', generated: '主要生成' }[level] || '未使用 AI'
}

function labelLevelType(level) {
  if (level === 'generated') return 'danger'
  if (level === 'assisted') return 'warning'
  return 'info'
}

function articleStatusLabel(status) {
  return { published: '已发布', private: '仅自己可见', draft: '草稿',
    scheduled: '定时发布', pending: '待审核' }[status] || status || '-'
}

async function loadLabels(page = labelQuery.page) {
  labelQuery.page = page
  loadingLabels.value = true
  try {
    const data = await adminApi.contentLabels(labelQuery)
    labelItems.value = data.items || []
    labelTotal.value = Number(data.total || 0)
  } catch {
    labelItems.value = []
    labelTotal.value = 0
  } finally {
    loadingLabels.value = false
  }
}

function openLabel(row) {
  labelForm.id = row.id
  labelForm.title = row.title
  labelForm.aiUsageLevel = row.aiUsageLevel || 'none'
  labelForm.reason = ''
  labelDialogVisible.value = true
}

async function submitLabel() {
  if (!labelForm.reason.trim()) {
    ElMessage.warning('修正标识需填写原因')
    return
  }
  labelSaving.value = true
  try {
    await adminApi.updateContentLabel(labelForm.id, {
      aiUsageLevel: labelForm.aiUsageLevel,
      reason: labelForm.reason.trim()
    })
    ElMessage.success('标识已更新并记录变更日志')
    labelDialogVisible.value = false
    loadLabels(labelQuery.page)
  } catch {
    // 失败提示已由请求层统一处理
  } finally {
    labelSaving.value = false
  }
}

/* ---------- 徽章审核 ---------- */
const badgeItems = ref([])
const badgeTotal = ref(0)
const badgeStatus = ref('pending')
const loadingBadges = ref(false)
const badgeQuery = reactive({ page: 1, size: 10 })
const badgeReject = reactive({ visible: false, saving: false, row: null, note: '' })

async function loadBadges(page = badgeQuery.page) {
  badgeQuery.page = page
  loadingBadges.value = true
  try {
    const data = await adminApi.badges({
      page: badgeQuery.page,
      size: badgeQuery.size,
      status: badgeStatus.value || undefined
    })
    badgeItems.value = data.items || []
    badgeTotal.value = Number(data.total || 0)
  } catch {
    badgeItems.value = []
    badgeTotal.value = 0
  } finally {
    loadingBadges.value = false
  }
}

async function approveBadge(row) {
  await ElMessageBox.confirm(
    `确定通过「${row.userNickname || row.userId}」的人类创作徽章申请吗？`,
    '通过申请', { type: 'warning', confirmButtonText: '通过' }
  )
  const res = await adminApi.reviewBadge(row.id, { status: 'passed' })
  ElMessage.success(`已通过（生效文章 ${res?.badgedArticles ?? 0} 篇）`)
  loadBadges()
}

function openRejectBadge(row) {
  badgeReject.row = row
  badgeReject.note = ''
  badgeReject.visible = true
}

async function confirmRejectBadge() {
  if (!badgeReject.note.trim()) {
    ElMessage.warning('请填写拒绝理由')
    return
  }
  badgeReject.saving = true
  try {
    await adminApi.reviewBadge(badgeReject.row.id, {
      status: 'rejected',
      note: badgeReject.note.trim()
    })
    ElMessage.success('已拒绝')
    badgeReject.visible = false
    loadBadges()
  } catch {
    // 失败提示已由请求层统一处理
  } finally {
    badgeReject.saving = false
  }
}

function badgeStatusType(status) {
  if (status === 'passed') return 'success'
  if (status === 'rejected') return 'danger'
  return 'warning'
}

function badgeStatusText(status) {
  return { pending: '待审核', passed: '已通过', rejected: '已拒绝' }[status] || status || '-'
}

/* ---------- 操作日志（仅超级管理员） ---------- */
const ACTIONS = [
  { value: 'BAN', label: '封禁' },
  { value: 'UNBAN', label: '解封' },
  { value: 'SET_ROLE', label: '角色调整' },
  { value: 'ARTICLE_UNPUBLISH', label: '文章下架' },
  { value: 'ARTICLE_DELETE', label: '文章删除' },
  { value: 'COMMENT_DELETE', label: '评论删除' },
  { value: 'REPORT_HANDLE', label: '举报处理' },
  { value: 'CONTENT_LABEL_UPDATE', label: '标识修正' }
]
const logs = ref([])
const logTotal = ref(0)
const logAction = ref('')
const loadingLogs = ref(false)
const logQuery = reactive({ page: 1, size: 10 })

async function loadLogs(page = logQuery.page) {
  logQuery.page = page
  loadingLogs.value = true
  try {
    const data = await adminApi.listLogs({
      page: logQuery.page,
      size: logQuery.size,
      action: logAction.value || undefined
    })
    logs.value = data.items
    logTotal.value = data.total
  } finally {
    loadingLogs.value = false
  }
}

/* ---------- 伯乐管理 ---------- */
const curators = ref([])
const loadingCurators = ref(false)
const curatorDialog = reactive({
  visible: false, saving: false, userId: null, termStart: '', termEnd: ''
})
const candidatesDrawer = reactive({ visible: false, loading: false, items: [] })

async function loadCurators() {
  loadingCurators.value = true
  try {
    curators.value = (await adminApi.curators()) || []
  } finally {
    loadingCurators.value = false
  }
}

function openCuratorDialog() {
  curatorDialog.userId = null
  curatorDialog.termStart = ''
  curatorDialog.termEnd = ''
  curatorDialog.visible = true
}

async function saveCurator() {
  if (!curatorDialog.userId) {
    ElMessage.warning('请填写用户 ID')
    return
  }
  curatorDialog.saving = true
  try {
    await adminApi.addCurator({
      userId: curatorDialog.userId,
      termStart: curatorDialog.termStart || null,
      termEnd: curatorDialog.termEnd || null
    })
    curatorDialog.visible = false
    ElMessage.success('伯乐已设置')
    await loadCurators()
  } finally {
    curatorDialog.saving = false
  }
}

async function removeCurator(row) {
  await ElMessageBox.confirm(
    `确定停用「${row.nickname || row.userId}」的伯乐身份吗？停用后本周推荐位保留但不再可进入工作台。`,
    '停用伯乐', { type: 'warning' }
  )
  await adminApi.removeCurator(row.id)
  ElMessage.success('已停用')
  await loadCurators()
}

async function openCandidatesDrawer() {
  candidatesDrawer.visible = true
  candidatesDrawer.loading = true
  try {
    candidatesDrawer.items = (await adminApi.curationCandidates()) || []
  } catch {
    candidatesDrawer.items = []
  } finally {
    candidatesDrawer.loading = false
  }
}

function aiUsageLabel(level) {
  return { none: '未使用 AI', assisted: 'AI 辅助', generated: '主要生成' }[level] || '未声明'
}

/* ---------- 创作活动 ---------- */
const campaigns = ref([])
const campaignTotal = ref(0)
const loadingCampaigns = ref(false)
const campaignQuery = reactive({ page: 1, size: 10, status: '' })
const campaignDialog = reactive({
  visible: false,
  saving: false,
  id: null,
  form: {
    title: '', description: '', prizePool: '', joinRule: '', judgeRule: '', startAt: '', endAt: ''
  }
})

function campaignStatusText(status) {
  return { draft: '草稿', active: '进行中', ended: '已结束' }[status] || status || '—'
}

function campaignStatusType(status) {
  if (status === 'active') return 'success'
  if (status === 'ended') return 'info'
  return 'warning'
}

async function loadCampaigns(page = campaignQuery.page) {
  campaignQuery.page = page
  loadingCampaigns.value = true
  try {
    const data = await adminApi.campaigns({
      page: campaignQuery.page,
      size: campaignQuery.size,
      status: campaignQuery.status || undefined
    })
    campaigns.value = data.items || []
    campaignTotal.value = data.total || 0
  } finally {
    loadingCampaigns.value = false
  }
}

function openCampaignDialog(row = null) {
  campaignDialog.id = row?.id || null
  Object.assign(campaignDialog.form, {
    title: row?.title || '',
    description: row?.description || '',
    prizePool: row?.prizePool || '',
    joinRule: row?.joinRule || '',
    judgeRule: row?.judgeRule || '',
    startAt: row?.startAt || '',
    endAt: row?.endAt || ''
  })
  campaignDialog.visible = true
}

async function saveCampaign() {
  const form = campaignDialog.form
  if (!form.title.trim()) {
    ElMessage.warning('活动标题不能为空')
    return
  }
  if (!form.startAt || !form.endAt) {
    ElMessage.warning('请填写活动起止时间')
    return
  }
  campaignDialog.saving = true
  try {
    const payload = {
      title: form.title.trim(),
      description: form.description,
      prizePool: form.prizePool,
      joinRule: form.joinRule,
      judgeRule: form.judgeRule,
      startAt: form.startAt,
      endAt: form.endAt
    }
    if (campaignDialog.id) {
      await adminApi.updateCampaign(campaignDialog.id, payload)
    } else {
      await adminApi.createCampaign(payload)
    }
    campaignDialog.visible = false
    ElMessage.success('已保存')
    await loadCampaigns(1)
  } finally {
    campaignDialog.saving = false
  }
}

async function removeCampaign(row) {
  await ElMessageBox.confirm(`确定删除创作活动「${row.title}」吗？`, '删除活动', { type: 'warning' })
  await adminApi.deleteCampaign(row.id)
  ElMessage.success('已删除')
  await loadCampaigns()
}

async function publishCampaign(row) {
  await ElMessageBox.confirm(
    `上线「${row.title}」将立即向全站作者发送系统通知，且读者首页会展示该活动。确认上线？`,
    '上线活动', { type: 'warning', confirmButtonText: '确认上线' }
  )
  await adminApi.publishCampaign(row.id)
  ElMessage.success('已上线并通知全站')
  await loadCampaigns()
}

/* ---------- 工具 ---------- */
function roleLabel(role) {
  return role === 'ADMIN' ? '超级管理员' : role === 'CONTENT_ADMIN' ? '内容管理员' : '用户'
}
function actionLabel(value) {
  return ACTIONS.find(a => a.value === value)?.label || value
}
function formatDate(value) {
  return value ? String(value).replace('T', ' ').slice(0, 16) : ''
}

// 文章状态展示：private 为风控私密发布，仅作者可见
function statusTagType(status) {
  if (status === 'published') return 'success'
  if (status === 'private') return 'info'
  return 'warning'
}
function statusLabel(status) {
  if (status === 'published') return '已发布'
  if (status === 'private') return '仅自己可见'
  return '草稿'
}

/* ---------- 技术语料 ---------- */
const techTab = ref('docs')
const techStats = reactive({
  docs: 0, ready: 0, failed: 0, chunks: 0, assistCalls7d: 0, adopted7d: 0, failureRate: 0
})
const techDocs = ref([])
const techDocTotal = ref(0)
const techDocKeyword = ref('')
const techDocStatus = ref('')
const loadingTechDocs = ref(false)
const techDocQuery = reactive({ page: 1, size: 10 })

async function loadTechStats() {
  const data = await techAdminApi.stats()
  Object.assign(techStats, {
    docs: data.docs || 0,
    ready: data.ready || 0,
    failed: data.failed || 0,
    chunks: data.chunks || 0,
    assistCalls7d: data.assistCalls7d || 0,
    adopted7d: data.adopted7d || 0,
    failureRate: data.failureRate || 0
  })
}

async function loadTechDocs(page = techDocQuery.page) {
  techDocQuery.page = page
  loadingTechDocs.value = true
  try {
    const data = await techAdminApi.docs({
      page: techDocQuery.page,
      size: techDocQuery.size,
      status: techDocStatus.value || undefined,
      keyword: techDocKeyword.value.trim() || undefined
    })
    techDocs.value = data.items || []
    techDocTotal.value = data.total || 0
  } finally {
    loadingTechDocs.value = false
  }
}

function techDocStatusType(status) {
  if (status === 'ready') return 'success'
  if (status === 'failed') return 'danger'
  return 'warning'
}
function techDocStatusLabel(status) {
  if (status === 'ready') return '已就绪'
  if (status === 'failed') return '失败'
  if (status === 'parsing') return '解析中'
  return '草稿'
}

function techResultMessage(doc) {
  if (doc.status === 'ready') return `已入库，共 ${doc.chunkCount || 0} 块`
  if (doc.status === 'failed') return `解析失败：${doc.errorMessage || '未知原因'}`
  return '已提交解析'
}

async function rebuildTechIndex(row) {
  const result = await techAdminApi.indexDoc(row.id)
  if (result.indexed) {
    ElMessage.success(`已重建向量索引，共 ${result.chunks || 0} 块`)
  } else {
    ElMessage.warning(result.message || '向量索引未更新')
  }
  await loadTechStats()
}

async function removeTechDoc(row) {
  await ElMessageBox.confirm(
    `确定删除语料「${row.title}」及其全部切块吗？删除后检索不再命中。`,
    '删除语料', { type: 'warning' }
  )
  await techAdminApi.removeDoc(row.id)
  ElMessage.success('已删除')
  await Promise.all([loadTechDocs(), loadTechStats()])
}

const techChunks = reactive({ visible: false, loading: false, title: '', items: [] })

async function viewTechChunks(row) {
  techChunks.title = row.title
  techChunks.items = []
  techChunks.visible = true
  techChunks.loading = true
  try {
    techChunks.items = await techAdminApi.chunks(row.id)
  } finally {
    techChunks.loading = false
  }
}

const techPaste = reactive({
  visible: false,
  saving: false,
  form: {
    title: '', docType: 'guide', sourceName: '', version: '',
    license: '', fileType: 'markdown', content: ''
  }
})

function openPasteDialog() {
  Object.assign(techPaste.form, {
    title: '', docType: 'guide', sourceName: '', version: '',
    license: '', fileType: 'markdown', content: ''
  })
  techPaste.visible = true
}

async function saveTechPaste() {
  if (!techPaste.form.title.trim()) {
    ElMessage.warning('标题不能为空')
    return
  }
  if (!techPaste.form.content.trim()) {
    ElMessage.warning('正文不能为空')
    return
  }
  techPaste.saving = true
  try {
    const doc = await techAdminApi.createDoc({ ...techPaste.form })
    techPaste.visible = false
    ElMessage.success(techResultMessage(doc))
    await Promise.all([loadTechDocs(1), loadTechStats()])
  } finally {
    techPaste.saving = false
  }
}

const techUpload = reactive({
  visible: false,
  saving: false,
  file: null,
  files: [],
  form: { title: '', docType: 'guide', sourceName: '', version: '', license: '' }
})

function openUploadDialog() {
  techUpload.file = null
  techUpload.files = []
  Object.assign(techUpload.form, {
    title: '', docType: 'guide', sourceName: '', version: '', license: ''
  })
  techUpload.visible = true
}

function onTechFileChange(uploadFile) {
  techUpload.file = uploadFile.raw || null
  if (!techUpload.form.title && uploadFile.name) {
    techUpload.form.title = uploadFile.name.replace(/\.[^.]+$/, '')
  }
}

async function submitTechUpload() {
  if (!techUpload.file) {
    ElMessage.warning('请选择要上传的文件')
    return
  }
  const data = new FormData()
  data.append('file', techUpload.file)
  Object.entries(techUpload.form).forEach(([key, value]) => {
    if (value) data.append(key, value)
  })
  techUpload.saving = true
  try {
    const doc = await techAdminApi.uploadDoc(data)
    techUpload.visible = false
    if (doc.status === 'failed') ElMessage.warning(techResultMessage(doc))
    else ElMessage.success(techResultMessage(doc))
    await Promise.all([loadTechDocs(1), loadTechStats()])
  } finally {
    techUpload.saving = false
  }
}

const techTerms = ref([])
const techTermKeyword = ref('')
const loadingTechTerms = ref(false)

async function loadTechTerms() {
  loadingTechTerms.value = true
  try {
    techTerms.value = await techAdminApi.terms({
      keyword: techTermKeyword.value.trim() || undefined
    })
  } finally {
    loadingTechTerms.value = false
  }
}

const techTermDialog = reactive({
  visible: false,
  saving: false,
  id: null,
  form: { canonical: '', category: '', definition: '', variants: '', caseSensitive: false, status: 'active' }
})

function openTermDialog(row = null) {
  techTermDialog.id = row?.id || null
  Object.assign(techTermDialog.form, {
    canonical: row?.canonical || '',
    category: row?.category || '',
    definition: row?.definition || '',
    variants: row?.variants || '',
    caseSensitive: Boolean(row?.caseSensitive),
    status: row?.status || 'active'
  })
  techTermDialog.visible = true
}

async function saveTechTerm() {
  if (!techTermDialog.form.canonical.trim()) {
    ElMessage.warning('规范写法不能为空')
    return
  }
  techTermDialog.saving = true
  try {
    const payload = { ...techTermDialog.form, caseSensitive: techTermDialog.form.caseSensitive }
    if (techTermDialog.id) await techAdminApi.updateTerm(techTermDialog.id, payload)
    else await techAdminApi.createTerm(payload)
    techTermDialog.visible = false
    ElMessage.success('已保存')
    await loadTechTerms()
  } finally {
    techTermDialog.saving = false
  }
}

async function removeTechTerm(row) {
  await ElMessageBox.confirm(`确定删除术语「${row.canonical}」吗？`, '删除术语', { type: 'warning' })
  await techAdminApi.removeTerm(row.id)
  ElMessage.success('已删除')
  await loadTechTerms()
}

const techTermImport = reactive({ visible: false, saving: false, json: '' })

function openTermImport() {
  techTermImport.json = ''
  techTermImport.visible = true
}

async function submitTermImport() {
  let items
  try {
    items = JSON.parse(techTermImport.json)
  } catch (e) {
    ElMessage.error('JSON 格式不正确')
    return
  }
  if (!Array.isArray(items) || !items.length) {
    ElMessage.warning('请提交非空的术语数组')
    return
  }
  techTermImport.saving = true
  try {
    const result = await techAdminApi.importTerms(items)
    techTermImport.visible = false
    ElMessage.success(`导入完成：新增 ${result.inserted}，更新 ${result.updated}，跳过 ${result.skipped}`)
    await loadTechTerms()
  } finally {
    techTermImport.saving = false
  }
}

watch(activeTab, (tab) => {
  if (tab === 'users') loadUsers(1)
  else if (tab === 'content') loadContent(1)
  else if (tab === 'reports') loadReports(1)
  else if (tab === 'ai') loadAiUsage()
  else if (tab === 'aiAudit') loadAiOutputs(1)
  else if (tab === 'labels') loadLabels(1)
  else if (tab === 'badges') loadBadges(1)
  else if (tab === 'curators') loadCurators()
  else if (tab === 'campaigns') loadCampaigns(1)
  else if (tab === 'tech') {
    loadTechStats()
    loadTechDocs(1)
    loadTechTerms()
  }
  else if (tab === 'logs') loadLogs(1)
})

watch(techTab, (tab) => {
  if (tab === 'docs') loadTechDocs(1)
  else loadTechTerms()
})

onMounted(() => loadCategories())
</script>

<style scoped>
/* 页签底线用深色发丝线 */
:deep(.el-tabs__nav-wrap::after) {
  height: 1px;
  background: var(--hairline-dark);
}

.toolbar {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 16px;
  flex-wrap: wrap;
  margin: 14px 0 16px;
}
.toolbar-left {
  display: flex;
  align-items: center;
  gap: 10px;
  flex-wrap: wrap;
}
.ban-until {
  color: var(--ink-faint);
  font-size: 12px;
}
.ban-confirm {
  margin: 6px 0;
  line-height: 1.8;
}
.pending-tip {
  color: var(--cinnabar);
  font-size: 13px;
  letter-spacing: 0.04em;
}
.curator-user {
  display: flex;
  align-items: center;
  gap: 8px;
}
.candidates-note {
  margin-bottom: 14px;
}
.ai-stats {
  margin-bottom: 18px;
}
.stat-card {
  background: var(--paper-card);
  border: 1px solid var(--hairline-dark);
  border-top: 3px solid var(--ink);
  padding: 18px 20px;
}
.stat-value {
  font-size: 28px;
  font-weight: 900;
  color: var(--ink);
  letter-spacing: 0.02em;
}
.stat-label {
  margin-top: 6px;
  font-size: 12.5px;
  color: var(--ink-faint);
  letter-spacing: 0.08em;
}
.pager {
  margin-top: 18px;
  display: flex;
  justify-content: center;
}
.mono {
  font-family: var(--font-mono, ui-monospace, SFMono-Regular, Menlo, monospace);
  font-size: 12px;
  letter-spacing: 0.02em;
}
.audit-body {
  padding-bottom: 24px;
}
.audit-section {
  margin-top: 20px;
}
.audit-section-title {
  display: flex;
  align-items: baseline;
  gap: 10px;
  margin-bottom: 8px;
  font-size: 13px;
  font-weight: 700;
  color: var(--ink);
  letter-spacing: 0.06em;
}
.audit-digest {
  font-weight: 400;
  color: var(--ink-faint);
}
.audit-pre {
  margin: 0;
  max-height: 260px;
  overflow: auto;
  padding: 12px 14px;
  background: var(--paper);
  border: 1px solid var(--hairline-dark);
  color: var(--ink-soft);
  font-size: 12.5px;
  line-height: 1.7;
  white-space: pre-wrap;
  word-break: break-word;
}
.audit-review-actions {
  display: flex;
  gap: 10px;
  margin-top: 10px;
}
.audit-reviewed {
  margin-top: 10px;
  font-size: 12.5px;
  color: var(--ink-faint);
}
.tech-stats {
  row-gap: 16px;
}
.tech-subtabs {
  margin-top: 4px;
}
.upload-tip {
  margin-top: 8px;
  font-size: 12px;
  line-height: 1.7;
  color: var(--ink-faint);
}
.import-tip {
  margin-bottom: 12px;
}
.chunk-card {
  margin-bottom: 14px;
  padding: 14px 16px;
  background: var(--paper-card);
  border: 1px solid var(--hairline-dark);
}
.chunk-meta {
  margin-bottom: 8px;
  font-family: var(--font-mono, ui-monospace, SFMono-Regular, Menlo, monospace);
  font-size: 12px;
  color: var(--ink-faint);
  letter-spacing: 0.02em;
}
.chunk-text {
  margin: 0;
  max-height: 300px;
  overflow: auto;
  font-size: 13px;
  line-height: 1.75;
  color: var(--ink-soft);
  white-space: pre-wrap;
  word-break: break-word;
}
</style>
