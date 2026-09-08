<template>
  <div class="login-page">
    <div class="login-bg">
      <div class="bg-shape shape-1"></div>
      <div class="bg-shape shape-2"></div>
      <div class="bg-shape shape-3"></div>
    </div>
    <div class="login-container">
      <div class="login-left">
        <div class="brand">
          <div class="brand-icon">
            <el-icon :size="48"><FirstAidKit /></el-icon>
          </div>
          <h1>智慧医疗系统</h1>
          <p class="brand-desc">HealthApp Enterprise</p>
        </div>
        <div class="features">
          <div class="feature-item">
            <el-icon><ChatDotRound /></el-icon>
            <span>AI智能辅助诊断</span>
          </div>
          <div class="feature-item">
            <el-icon><Calendar /></el-icon>
            <span>在线预约挂号</span>
          </div>
          <div class="feature-item">
            <el-icon><Document /></el-icon>
            <span>电子病历管理</span>
          </div>
          <div class="feature-item">
            <el-icon><DataAnalysis /></el-icon>
            <span>数据可视化看板</span>
          </div>
        </div>
      </div>
      <div class="login-right">
        <div class="login-card">
          <h2>欢迎登录</h2>
          <p class="login-subtitle">请选择您的角色并登录</p>

          <el-tabs v-model="activeRole" class="role-tabs">
            <el-tab-pane label="患者" name="patient">
              <el-form @submit.prevent="handleLogin">
                <el-form-item>
                  <el-input v-model="form.username" placeholder="手机号" size="large" :prefix-icon="User" />
                </el-form-item>
                <el-form-item>
                  <el-input v-model="form.password" type="password" placeholder="密码" size="large" :prefix-icon="Lock" show-password />
                </el-form-item>
                <el-button type="primary" size="large" class="login-btn" :loading="loading" @click="handleLogin">
                  登 录
                </el-button>
              </el-form>
              <div class="login-footer">
                <span>还没有账号？<a @click="$message.info('请联系医院注册')">立即注册</a></span>
              </div>
            </el-tab-pane>

            <el-tab-pane label="医生" name="doctor">
              <el-form @submit.prevent="handleLogin">
                <el-form-item>
                  <el-input v-model="form.username" placeholder="工号" size="large" :prefix-icon="User" />
                </el-form-item>
                <el-form-item>
                  <el-input v-model="form.password" type="password" placeholder="密码" size="large" :prefix-icon="Lock" show-password />
                </el-form-item>
                <el-button type="primary" size="large" class="login-btn" :loading="loading" @click="handleLogin">
                  登 录
                </el-button>
              </el-form>
            </el-tab-pane>

            <el-tab-pane label="管理员" name="admin">
              <el-form @submit.prevent="handleLogin">
                <el-form-item>
                  <el-input v-model="form.username" placeholder="管理员账号" size="large" :prefix-icon="User" />
                </el-form-item>
                <el-form-item>
                  <el-input v-model="form.password" type="password" placeholder="密码" size="large" :prefix-icon="Lock" show-password />
                </el-form-item>
                <el-button type="primary" size="large" class="login-btn" :loading="loading" @click="handleLogin">
                  登 录
                </el-button>
              </el-form>
            </el-tab-pane>
          </el-tabs>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { User, Lock, FirstAidKit, ChatDotRound, Calendar, Document, DataAnalysis } from '@element-plus/icons-vue'
import { useUserStore } from '@/stores/user'

const router = useRouter()
const userStore = useUserStore()

const activeRole = ref('patient')
const loading = ref(false)
const form = reactive({ username: '', password: '' })

const handleLogin = async () => {
  if (!form.username || !form.password) {
    ElMessage.warning('请输入账号和密码')
    return
  }
  loading.value = true
  // 模拟登录（实际项目调用后端接口）
  setTimeout(() => {
    const mockUser = {
      id: activeRole.value === 'patient' ? 1 : activeRole.value === 'doctor' ? 1 : 1,
      username: form.username,
      name: activeRole.value === 'patient' ? '张三' : activeRole.value === 'doctor' ? '李医生' : '管理员',
      role: activeRole.value,
      token: 'mock-token-' + Date.now(),
    }
    userStore.setLogin({ token: mockUser.token, userInfo: mockUser as any })
    ElMessage.success('登录成功')
    const redirect = router.currentRoute.value.query.redirect as string
    router.push(redirect || `/${activeRole.value}/dashboard`)
    loading.value = false
  }, 800)
}
</script>

<style scoped lang="scss">
.login-page {
  height: 100vh;
  display: flex;
  align-items: center;
  justify-content: center;
  background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
  position: relative;
  overflow: hidden;
}
.login-bg {
  position: absolute;
  inset: 0;
  .bg-shape {
    position: absolute;
    border-radius: 50%;
    background: rgba(255,255,255,0.1);
  }
  .shape-1 { width: 400px; height: 400px; top: -100px; right: -100px; }
  .shape-2 { width: 300px; height: 300px; bottom: -50px; left: -50px; }
  .shape-3 { width: 200px; height: 200px; top: 40%; left: 30%; }
}
.login-container {
  display: flex;
  width: 900px;
  max-width: 95%;
  background: white;
  border-radius: 20px;
  overflow: hidden;
  box-shadow: 0 20px 60px rgba(0,0,0,0.3);
  position: relative;
  z-index: 1;
}
.login-left {
  flex: 1;
  background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
  color: white;
  padding: 50px 40px;
  display: flex;
  flex-direction: column;
  justify-content: center;
}
.brand {
  text-align: center;
  margin-bottom: 40px;
  .brand-icon {
    width: 80px; height: 80px;
    background: rgba(255,255,255,0.2);
    border-radius: 20px;
    display: flex; align-items: center; justify-content: center;
    margin: 0 auto 16px;
  }
  h1 { font-size: 28px; margin-bottom: 8px; }
  .brand-desc { opacity: 0.8; font-size: 14px; }
}
.features {
  .feature-item {
    display: flex; align-items: center; gap: 12px;
    padding: 12px 0;
    font-size: 15px;
    opacity: 0.9;
    .el-icon { font-size: 20px; }
  }
}
.login-right {
  flex: 1;
  padding: 50px 40px;
  display: flex;
  align-items: center;
}
.login-card {
  width: 100%;
  h2 { font-size: 24px; margin-bottom: 8px; color: #303133; }
  .login-subtitle { color: #909399; margin-bottom: 24px; font-size: 14px; }
}
.role-tabs {
  :deep(.el-tabs__item) { font-size: 16px; }
}
.login-btn {
  width: 100%;
  margin-top: 8px;
  background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
  border: none;
  font-size: 16px;
  letter-spacing: 4px;
}
.login-footer {
  text-align: center;
  margin-top: 16px;
  font-size: 13px;
  color: #909399;
  a { color: #667eea; cursor: pointer; }
}
</style>
