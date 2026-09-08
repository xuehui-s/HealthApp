<template>
  <el-container class="layout">
    <el-aside :width="collapsed ? '64px' : '220px'" class="sidebar">
      <div class="logo">
        <el-icon :size="28" color="#fff"><FirstAidKit /></el-icon>
        <span v-show="!collapsed" class="logo-text">医生工作台</span>
      </div>
      <el-menu
        :default-active="activeMenu"
        :collapse="collapsed"
        background-color="#001529"
        text-color="#bfcbd9"
        active-text-color="#13c2c2"
        router
      >
        <el-menu-item index="/doctor/dashboard">
          <el-icon><Odometer /></el-icon><template #title>工作台</template>
        </el-menu-item>
        <el-menu-item index="/doctor/appointments">
          <el-icon><Calendar /></el-icon><template #title>预约管理</template>
        </el-menu-item>
        <el-menu-item index="/doctor/prescription">
          <el-icon><Document /></el-icon><template #title>处方管理</template>
        </el-menu-item>
        <el-menu-item index="/doctor/leave">
          <el-icon><TimeFilled /></el-icon><template #title>请假管理</template>
        </el-menu-item>
        <el-menu-item index="/doctor/agent">
          <el-icon><ChatDotRound /></el-icon><template #title>AI助手</template>
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
          <el-tag type="success" size="small">在线</el-tag>
          <el-dropdown @command="handleCommand">
            <span class="user-info">
              <el-avatar :size="32" style="background:#13c2c2">{{ userStore.userInfo?.name?.charAt(0) }}</el-avatar>
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
import { FirstAidKit, Odometer, Calendar, Document, TimeFilled, ChatDotRound, Fold, Expand, ArrowDown } from '@element-plus/icons-vue'

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
