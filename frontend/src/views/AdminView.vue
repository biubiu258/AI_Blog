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
  </div>
</template>

<script setup>
import { computed, onMounted, reactive, ref, watch } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { adminApi, categoryApi } from '../api'
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

/* ---------- 操作日志（仅超级管理员） ---------- */
const ACTIONS = [
  { value: 'BAN', label: '封禁' },
  { value: 'UNBAN', label: '解封' },
  { value: 'SET_ROLE', label: '角色调整' },
  { value: 'ARTICLE_UNPUBLISH', label: '文章下架' },
  { value: 'ARTICLE_DELETE', label: '文章删除' },
  { value: 'COMMENT_DELETE', label: '评论删除' },
  { value: 'REPORT_HANDLE', label: '举报处理' }
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

watch(activeTab, (tab) => {
  if (tab === 'users') loadUsers(1)
  else if (tab === 'content') loadContent(1)
  else if (tab === 'reports') loadReports(1)
  else if (tab === 'ai') loadAiUsage()
  else if (tab === 'aiAudit') loadAiOutputs(1)
  else if (tab === 'logs') loadLogs(1)
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
</style>
