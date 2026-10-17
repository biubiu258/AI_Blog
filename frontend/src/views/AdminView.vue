<template>
  <div class="page-container">
    <header class="page-head">
      <div class="page-title">管理后台</div>
      <div class="page-sub">ADMIN CONSOLE</div>
    </header>

    <el-tabs v-model="activeTab">
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

      <el-tab-pane label="用户管理" name="users">
        <div class="toolbar">
          <el-input v-model="userKeyword" placeholder="按用户名/昵称搜索" clearable style="width: 240px"
                    @keyup.enter="loadUsers(1)">
            <template #append>
              <el-button @click="loadUsers(1)"><el-icon><Search /></el-icon></el-button>
            </template>
          </el-input>
        </div>
        <div class="sheet">
          <el-table :data="users" v-loading="loadingUsers">
            <el-table-column prop="id" label="ID" width="70" />
            <el-table-column prop="username" label="用户名" min-width="140" />
            <el-table-column prop="nickname" label="昵称" min-width="140" />
            <el-table-column prop="email" label="邮箱" min-width="180" show-overflow-tooltip />
            <el-table-column label="角色" width="100">
              <template #default="{ row }">
                <el-tag :type="row.role === 'ADMIN' ? 'danger' : 'info'" size="small" effect="plain">{{ row.role }}</el-tag>
              </template>
            </el-table-column>
            <el-table-column label="状态" width="100">
              <template #default="{ row }">
                <el-tag :type="row.status === 1 ? 'danger' : 'success'" size="small" effect="plain">
                  {{ row.status === 1 ? '已封禁' : '正常' }}
                </el-tag>
              </template>
            </el-table-column>
            <el-table-column label="操作" width="120">
              <template #default="{ row }">
                <el-button v-if="row.role !== 'ADMIN'" link :type="row.status === 1 ? 'success' : 'danger'"
                           @click="toggleBan(row)">
                  {{ row.status === 1 ? '解封' : '封禁' }}
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
    </el-tabs>

    <el-dialog v-model="categoryDialogVisible" :title="editingCategory ? '重命名分类' : '新建分类'"
               width="420px">
      <el-form :model="categoryForm" label-width="70px">
        <el-form-item label="名称" required>
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
  </div>
</template>

<script setup>
import { onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { adminApi, categoryApi } from '../api'

const activeTab = ref('categories')

const categories = ref([])
const loadingCategories = ref(false)
const categoryDialogVisible = ref(false)
const editingCategory = ref(null)
const savingCategory = ref(false)
const categoryForm = reactive({ name: '', description: '' })

const users = ref([])
const userTotal = ref(0)
const userKeyword = ref('')
const loadingUsers = ref(false)
const userQuery = reactive({ page: 1, size: 10 })

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

async function loadUsers(page = userQuery.page) {
  userQuery.page = page
  loadingUsers.value = true
  try {
    const data = await adminApi.listUsers({
      page: userQuery.page,
      size: userQuery.size,
      keyword: userKeyword.value || undefined
    })
    users.value = data.items
    userTotal.value = data.total
  } finally {
    loadingUsers.value = false
  }
}

async function toggleBan(row) {
  const target = row.status === 1 ? 0 : 1
  const action = target === 1 ? '封禁' : '解封'
  await ElMessageBox.confirm(`确定${action}用户「${row.username}」吗？`, '提示', { type: 'warning' })
  await adminApi.setUserStatus(row.id, target)
  ElMessage.success(`已${action}`)
  loadUsers()
}

onMounted(() => {
  loadCategories()
  loadUsers(1)
})
</script>

<style scoped>
/* 页签底线用深色发丝线 */
:deep(.el-tabs__nav-wrap::after) {
  height: 1px;
  background: var(--hairline-dark);
}

.toolbar {
  margin: 14px 0 16px;
}
.pager {
  margin-top: 18px;
  display: flex;
  justify-content: center;
}
</style>
