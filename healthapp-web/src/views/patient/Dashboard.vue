<template>
  <div class="dashboard">
    <!-- 欢迎横幅 -->
    <div class="welcome-banner">
      <div class="welcome-text">
        <h2>您好，{{ userStore.userInfo?.name }} 👋</h2>
        <p>欢迎使用智慧医疗系统，祝您身体健康</p>
      </div>
      <div class="welcome-actions">
        <el-button type="primary" size="large" @click="$router.push('/patient/appointment')">
          <el-icon><Calendar /></el-icon> 立即预约
        </el-button>
        <el-button size="large" @click="$router.push('/agent')">
          <el-icon><ChatDotRound /></el-icon> AI咨询
        </el-button>
      </div>
    </div>

    <!-- 统计卡片 -->
    <el-row :gutter="20" class="stat-row">
      <el-col :span="6">
        <div class="stat-card blue">
          <div class="stat-label">待就诊预约</div>
          <div class="stat-value">{{ stats.pendingAppointments }}</div>
          <el-icon class="stat-icon"><Calendar /></el-icon>
        </div>
      </el-col>
      <el-col :span="6">
        <div class="stat-card orange">
          <div class="stat-label">待缴费订单</div>
          <div class="stat-value">{{ stats.waitPayOrders }}</div>
          <el-icon class="stat-icon"><Wallet /></el-icon>
        </div>
      </el-col>
      <el-col :span="6">
        <div class="stat-card green">
          <div class="stat-label">历史就诊</div>
          <div class="stat-value">{{ stats.totalVisits }}</div>
          <el-icon class="stat-icon"><Document /></el-icon>
        </div>
      </el-col>
      <el-col :span="6">
        <div class="stat-card">
          <div class="stat-label">未读消息</div>
          <div class="stat-value">{{ stats.unreadMessages }}</div>
          <el-icon class="stat-icon"><Bell /></el-icon>
        </div>
      </el-col>
    </el-row>

    <el-row :gutter="20">
      <!-- 最近预约 -->
      <el-col :span="14">
        <div class="card">
          <div class="card-header">
            <h3>最近预约</h3>
            <el-button type="primary" link @click="$router.push('/patient/my-appointments')">查看全部</el-button>
          </div>
          <el-table :data="recentAppointments" style="width: 100%">
            <el-table-column prop="appointDate" label="日期" width="120" />
            <el-table-column prop="timePeriod" label="时段" width="80" />
            <el-table-column prop="doctorName" label="医生" width="100" />
            <el-table-column prop="deptName" label="科室" width="100" />
            <el-table-column label="状态" width="100">
              <template #default="{ row }">
                <el-tag :type="statusType(row.status)">{{ statusName(row.status) }}</el-tag>
              </template>
            </el-table-column>
            <el-table-column label="操作">
              <template #default="{ row }">
                <el-button v-if="row.status === 0" type="danger" link size="small" @click="handleCancel(row.id)">取消</el-button>
              </template>
            </el-table-column>
          </el-table>
        </div>
      </el-col>

      <!-- 快捷功能 -->
      <el-col :span="10">
        <div class="card">
          <div class="card-header"><h3>快捷功能</h3></div>
          <div class="quick-grid">
            <div class="quick-item" @click="$router.push('/patient/appointment')">
              <el-icon :size="32" color="#409eff"><Calendar /></el-icon>
              <span>预约挂号</span>
            </div>
            <div class="quick-item" @click="$router.push('/patient/payment')">
              <el-icon :size="32" color="#e6a23c"><Wallet /></el-icon>
              <span>缴费查询</span>
            </div>
            <div class="quick-item" @click="$router.push('/patient/medical-records')">
              <el-icon :size="32" color="#67c23a"><Document /></el-icon>
              <span>电子病历</span>
            </div>
            <div class="quick-item" @click="$router.push('/agent')">
              <el-icon :size="32" color="#9b59b6"><ChatDotRound /></el-icon>
              <span>AI咨询</span>
            </div>
            <div class="quick-item" @click="$router.push('/patient/messages')">
              <el-icon :size="32" color="#f56c6c"><Bell /></el-icon>
              <span>消息通知</span>
            </div>
            <div class="quick-item" @click="$router.push('/patient/profile')">
              <el-icon :size="32" color="#909399"><User /></el-icon>
              <span>个人中心</span>
            </div>
          </div>
        </div>
      </el-col>
    </el-row>
  </div>
</template>

<script setup lang="ts">
import { reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { useUserStore } from '@/stores/user'
import { Calendar, Wallet, Document, Bell, ChatDotRound, User } from '@element-plus/icons-vue'

const userStore = useUserStore()

const stats = reactive({
  pendingAppointments: 2,
  waitPayOrders: 1,
  totalVisits: 15,
  unreadMessages: 3,
})

const recentAppointments = ref([
  { id: 1, appointDate: '2026-09-10', timePeriod: '上午', doctorName: '张医生', deptName: '内科', status: 0 },
  { id: 2, appointDate: '2026-09-08', timePeriod: '下午', doctorName: '李医生', deptName: '外科', status: 1 },
  { id: 3, appointDate: '2026-09-05', timePeriod: '上午', doctorName: '王医生', deptName: '儿科', status: 3 },
])

const statusName = (s: number) => ['待就诊', '已签到', '待缴费', '诊疗中', '已取消', '请假取消', '超时'][s] || '未知'
const statusType = (s: number) => ['primary', 'warning', 'danger', 'success', 'info', 'info', 'info'][s] || 'info'

const handleCancel = async (id: number) => {
  await ElMessageBox.confirm('确定取消该预约吗？', '提示', { type: 'warning' })
  ElMessage.success('取消成功')
}
</script>

<style scoped lang="scss">
.dashboard { display: flex; flex-direction: column; gap: 20px; }
.welcome-banner {
  background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
  border-radius: 12px;
  padding: 30px;
  color: white;
  display: flex;
  justify-content: space-between;
  align-items: center;
  h2 { margin: 0 0 8px; font-size: 24px; }
  p { margin: 0; opacity: 0.9; }
}
.welcome-actions { display: flex; gap: 12px; }
.stat-row { margin: 0 !important; }
.stat-card {
  background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
  color: white; border-radius: 12px; padding: 24px; position: relative; overflow: hidden;
  .stat-label { font-size: 14px; opacity: 0.9; }
  .stat-value { font-size: 36px; font-weight: 700; margin: 8px 0; }
  .stat-icon { position: absolute; right: 20px; top: 50%; transform: translateY(-50%); font-size: 56px; opacity: 0.15; }
  &.blue { background: linear-gradient(135deg, #4facfe 0%, #00f2fe 100%); }
  &.orange { background: linear-gradient(135deg, #f093fb 0%, #f5576c 100%); }
  &.green { background: linear-gradient(135deg, #11998e 0%, #38ef7d 100%); }
}
.card { background: white; border-radius: 12px; padding: 20px; box-shadow: 0 2px 12px rgba(0,0,0,0.06); }
.card-header { display: flex; justify-content: space-between; align-items: center; margin-bottom: 16px; h3 { margin: 0; font-size: 16px; } }
.quick-grid { display: grid; grid-template-columns: repeat(3, 1fr); gap: 16px; }
.quick-item {
  display: flex; flex-direction: column; align-items: center; gap: 8px;
  padding: 20px 10px; border-radius: 8px; cursor: pointer; transition: all 0.3s;
  &:hover { background: #f5f7fa; transform: translateY(-2px); }
  span { font-size: 13px; color: #606266; }
}
</style>
