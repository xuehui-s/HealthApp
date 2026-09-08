<template>
  <el-container class="layout">
    <el-aside :width="collapsed ? '64px' : '220px'" class="sidebar">
      <div class="logo">
        <el-icon :size="28" color="#fff"><FirstAidKit /></el-icon>
        <span v-show="!collapsed" class="logo-text">智慧医疗</span>
      </div>
      <el-menu
        :default-active="activeMenu"
        :collapse="collapsed"
        background-color="#1f2d3d"
        text-color="#bfcbd9"
        active-text-color="#409eff"
        router
      >
        <el-menu-item index="/patient/dashboard">
          <el-icon><Odometer /></el-icon>
          <template #title>首页</template>
        </el-menu-item>
        <el-menu-item index="/patient/appointment">
          <el-icon><Calendar /></el-icon>
          <template #title>预约挂号</template>
        </el-menu-item>
        <el-menu-item index="/patient/my-appointments">
          <el-icon><Tickets /></el-icon>
          <template #title>我的预约</template>
        </el-menu-item>
        <el-menu-item index="/patient/payment">
          <el-icon><Wallet /></el-icon>
          <template #title>缴费管理</template>
        </el-menu-item>
        <el-menu-item index="/patient/medical-records">
          <el-icon><Document /></el-icon>
          <template #title>电子病历</template>
        </el-menu-item>
        <el-menu-item index="/patient/messages">
          <el-icon><Bell /></el-icon>
          <template #title>消息通知</template>
        </el-menu-item>
        <el-menu-item index="/agent">
          <el-icon><ChatDotRound /></el-icon>
          <template #title>AI助手</template>
        </el-menu-item>
        <el-menu-item index="/patient/profile">
          <el-icon><User /></el-icon>
          <template #title>个人中心</template>
        </el-menu-item>
      </el-menu>
    </el-aside>

    <el-container>
      <el-header class="header">
        <div class="header-left">
          <el-icon class="collapse-btn" @click="collapsed = !collapsed"><Fold v-if="!collapsed" /><Expand v-else /></el-icon>
          <el-breadcrumb separator="/">
            <el-breadcrumb-item :to="{ path: '/patient/dashboard' }">首页</el-breadcrumb-item>
            <el-breadcrumb-item>{{ $route.meta.title }}</el-breadcrumb-item>
          </el-breadcrumb>
        </div>
        <div class="header-right">
          <el-badge :value="3" class="badge-item">
            <el-icon :size="20" @click="$router.push('/patient/messages')"><Bell /></el-icon>
          </el-badge>
          <el-dropdown @command="handleCommand">
            <span class="user-info">
              <el-avatar :size="32" style="background:#409eff">{{ userStore.userInfo?.name?.charAt(0) }}</el-avatar>
              <span class="username">{{ userStore.userInfo?.name }}</span>
              <el-icon><ArrowDown /></el-icon>
            </span>
            <template #dropdown>
              <el-dropdown-menu>
                <el-dropdown-item command="profile">个人中心</el-dropdown-item>
                <el-dropdown-item command="logout" divided>退出登录</el-dropdown-item>
              </el-dropdown-menu>
            </template>
          </el-dropdown>
        </div>
      </el-header>

      <el-main class="main-content">
        <router-view v-slot="{ Component }">
          <transition name="fade" mode="out-in">
            <component :is="Component" />
          </transition>
        </router-view>
      </el-main>
    </el-container>
  </el-container>
</template>

<script setup lang="ts">
import { ref, computed } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useUserStore } from '@/stores/user'
import { FirstAidKit, Odometer, Calendar, Tickets, Wallet, Document, Bell, User, Fold, Expand, ArrowDown, ChatDotRound } from '@element-plus/icons-vue'

const route = useRoute()
const router = useRouter()
const userStore = useUserStore()
const collapsed = ref(false)

const activeMenu = computed(() => route.path)

const handleCommand = (cmd: string) => {
  if (cmd === 'logout') {
    userStore.logout()
    router.push('/login')
  } else if (cmd === 'profile') {
    router.push('/patient/profile')
  }
}
</script>

<style scoped lang="scss">
.layout { height: 100vh; }
.sidebar {
  background: #1f2d3d;
  transition: width 0.3s;
  overflow: hidden;
  :deep(.el-menu) { border-right: none; }
}
.logo {
  height: 60px;
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 10px;
  color: white;
  .logo-text { font-size: 18px; font-weight: 600; white-space: nowrap; }
}
.header {
  background: white;
  display: flex;
  align-items: center;
  justify-content: space-between;
  box-shadow: 0 1px 4px rgba(0,0,0,0.08);
  padding: 0 20px;
}
.header-left { display: flex; align-items: center; gap: 16px; }
.collapse-btn { font-size: 20px; cursor: pointer; color: #606266; }
.header-right { display: flex; align-items: center; gap: 20px; }
.badge-item { cursor: pointer; }
.user-info {
  display: flex; align-items: center; gap: 8px; cursor: pointer;
  .username { font-size: 14px; color: #606266; }
}
.main-content {
  background: #f5f7fa;
  padding: 20px;
  overflow-y: auto;
}
.fade-enter-active, .fade-leave-active { transition: opacity 0.2s; }
.fade-enter-from, .fade-leave-to { opacity: 0; }
</style>
