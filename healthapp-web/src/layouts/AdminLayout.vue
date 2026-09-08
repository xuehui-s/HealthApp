<template>
  <el-container class="layout">
    <el-aside :width="collapsed ? '64px' : '220px'" class="sidebar">
      <div class="logo">
        <el-icon :size="28" color="#fff"><Setting /></el-icon>
        <span v-show="!collapsed" class="logo-text">管理后台</span>
      </div>
      <el-menu
        :default-active="activeMenu"
        :collapse="collapsed"
        background-color="#001529"
        text-color="#bfcbd9"
        active-text-color="#f56c6c"
        router
      >
        <el-menu-item index="/admin/dashboard">
          <el-icon><DataAnalysis /></el-icon><template #title>数据看板</template>
        </el-menu-item>
        <el-sub-menu index="user">
          <template #title><el-icon><User /></el-icon><span>用户管理</span></template>
          <el-menu-item index="/admin/patients">患者管理</el-menu-item>
          <el-menu-item index="/admin/doctors">医生管理</el-menu-item>
        </el-sub-menu>
        <el-menu-item index="/admin/departments">
          <el-icon><OfficeBuilding /></el-icon><template #title>科室管理</template>
        </el-menu-item>
        <el-menu-item index="/admin/orders">
          <el-icon><ShoppingCart /></el-icon><template #title>订单管理</template>
        </el-menu-item>
        <el-menu-item index="/admin/drugs">
          <el-icon><Box /></el-icon><template #title>药品管理</template>
        </el-menu-item>
        <el-menu-item index="/admin/logs">
          <el-icon><Document /></el-icon><template #title>操作日志</template>
        </el-menu-item>
      </el-menu>
    </el-aside>

    <el-container>
      <el-header class="header">
        <div class="header-left">
          <el-icon class="collapse-btn" @click="collapsed = !collapsed"><Fold v-if="!collapsed" /><Expand v-else /></el-icon>
          <span class="page-title">{{ $route.meta.title }}</span>
        </div>
        <div class="header-right">
          <el-dropdown @command="handleCommand">
            <span class="user-info">
              <el-avatar :size="32" style="background:#f56c6c">{{ userStore.userInfo?.name?.charAt(0) }}</el-avatar>
              <span class="username">{{ userStore.userInfo?.name }}</span>
              <el-icon><ArrowDown /></el-icon>
            </span>
            <template #dropdown>
              <el-dropdown-menu>
                <el-dropdown-item command="logout" divided>退出登录</el-dropdown-item>
              </el-dropdown-menu>
            </template>
          </el-dropdown>
        </div>
      </el-header>
      <el-main class="main-content">
        <router-view />
      </el-main>
    </el-container>
  </el-container>
</template>

<script setup lang="ts">
import { ref, computed } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useUserStore } from '@/stores/user'
import { Setting, DataAnalysis, User, OfficeBuilding, ShoppingCart, Box, Document, Fold, Expand, ArrowDown } from '@element-plus/icons-vue'

const route = useRoute()
const router = useRouter()
const userStore = useUserStore()
const collapsed = ref(false)
const activeMenu = computed(() => route.path)

const handleCommand = (cmd: string) => {
  if (cmd === 'logout') {
    userStore.logout()
    router.push('/login')
  }
}
</script>

<style scoped lang="scss">
.layout { height: 100vh; }
.sidebar { background: #001529; transition: width 0.3s; overflow: hidden; :deep(.el-menu) { border-right: none; } }
.logo { height: 60px; display: flex; align-items: center; justify-content: center; gap: 10px; color: white;
  .logo-text { font-size: 16px; font-weight: 600; white-space: nowrap; } }
.header { background: white; display: flex; align-items: center; justify-content: space-between; box-shadow: 0 1px 4px rgba(0,0,0,0.08); padding: 0 20px; }
.header-left { display: flex; align-items: center; gap: 16px; }
.collapse-btn { font-size: 20px; cursor: pointer; color: #606266; }
.page-title { font-size: 16px; font-weight: 600; color: #303133; }
.header-right { display: flex; align-items: center; gap: 16px; }
.user-info { display: flex; align-items: center; gap: 8px; cursor: pointer; .username { font-size: 14px; color: #606266; } }
.main-content { background: #f0f2f5; padding: 20px; overflow-y: auto; }
</style>
