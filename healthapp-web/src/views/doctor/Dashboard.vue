<template>
  <div class="doctor-dashboard">
    <!-- 统计卡片 -->
    <el-row :gutter="20">
      <el-col :span="6"><div class="stat-card blue"><div class="stat-label">今日预约</div><div class="stat-value">12</div><el-icon class="stat-icon"><Calendar /></el-icon></div></el-col>
      <el-col :span="6"><div class="stat-card green"><div class="stat-label">已就诊</div><div class="stat-value">8</div><el-icon class="stat-icon"><Check /></el-icon></div></el-col>
      <el-col :span="6"><div class="stat-card orange"><div class="stat-label">待就诊</div><div class="stat-value">4</div><el-icon class="stat-icon"><Clock /></el-icon></div></el-col>
      <el-col :span="6"><div class="stat-card"><div class="stat-label">本月处方</div><div class="stat-value">86</div><el-icon class="stat-icon"><Document /></el-icon></div></el-col>
    </el-row>

    <el-row :gutter="20" style="margin-top: 20px">
      <!-- 今日预约列表 -->
      <el-col :span="16">
        <div class="card">
          <div class="card-header">
            <h3>今日预约</h3>
            <el-tag type="primary">2026-09-05</el-tag>
          </div>
          <el-table :data="todayAppointments" style="width: 100%">
            <el-table-column prop="queueNum" label="序号" width="70" />
            <el-table-column prop="patientName" label="患者" width="100" />
            <el-table-column prop="timePeriod" label="时段" width="80" />
            <el-table-column prop="status" label="状态" width="100">
              <template #default="{ row }">
                <el-tag :type="row.status === '待就诊' ? 'warning' : row.status === '诊疗中' ? 'primary' : 'success'" size="small">{{ row.status }}</el-tag>
              </template>
            </el-table-column>
            <el-table-column label="操作">
              <template #default="{ row }">
                <el-button v-if="row.status === '待就诊'" type="primary" size="small" @click="$router.push(`/doctor/diagnosis/${row.id}`)">开始诊疗</el-button>
                <el-button v-else-if="row.status === '诊疗中'" type="success" size="small">继续</el-button>
                <el-button v-else type="info" size="small">查看</el-button>
              </template>
            </el-table-column>
          </el-table>
        </div>
      </el-col>

      <!-- 快捷操作 -->
      <el-col :span="8">
        <div class="card">
          <div class="card-header"><h3>快捷操作</h3></div>
          <div class="quick-actions">
            <div class="action-item" @click="$router.push('/doctor/appointments')">
              <el-icon :size="24" color="#409eff"><Calendar /></el-icon>
              <span>预约管理</span>
            </div>
            <div class="action-item" @click="$router.push('/doctor/prescription')">
              <el-icon :size="24" color="#67c23a"><Document /></el-icon>
              <span>处方管理</span>
            </div>
            <div class="action-item" @click="$router.push('/doctor/leave')">
              <el-icon :size="24" color="#e6a23c"><TimeFilled /></el-icon>
              <span>请假申请</span>
            </div>
            <div class="action-item" @click="$router.push('/doctor/agent')">
              <el-icon :size="24" color="#9b59b6"><ChatDotRound /></el-icon>
              <span>AI助手</span>
            </div>
          </div>
        </div>

        <div class="card" style="margin-top: 20px">
          <div class="card-header"><h3>AI助手建议</h3><el-tag size="small" type="success">实时</el-tag></div>
          <div class="ai-suggestion">
            <p>今日有4位待就诊患者，建议合理安排时间。</p>
            <p class="ai-tip">💡 使用AI助手可快速查询疾病诊疗指南和用药规范</p>
            <el-button type="primary" size="small" @click="$router.push('/doctor/agent')">立即使用</el-button>
          </div>
        </div>
      </el-col>
    </el-row>
  </div>
</template>

<script setup lang="ts">
import { ref } from 'vue'
import { Calendar, Check, Clock, Document, TimeFilled, ChatDotRound } from '@element-plus/icons-vue'

const todayAppointments = ref([
  { id: 1, queueNum: 1, patientName: '张三', timePeriod: '上午', status: '已完成' },
  { id: 2, queueNum: 2, patientName: '李四', timePeriod: '上午', status: '诊疗中' },
  { id: 3, queueNum: 3, patientName: '王五', timePeriod: '上午', status: '待就诊' },
  { id: 4, queueNum: 4, patientName: '赵六', timePeriod: '下午', status: '待就诊' },
])
</script>

<style scoped lang="scss">
.stat-card { background: linear-gradient(135deg, #667eea, #764ba2); color: white; border-radius: 12px; padding: 24px; position: relative; overflow: hidden;
  .stat-label { font-size: 14px; opacity: 0.9; } .stat-value { font-size: 32px; font-weight: 700; margin: 8px 0; }
  .stat-icon { position: absolute; right: 20px; top: 50%; transform: translateY(-50%); font-size: 48px; opacity: 0.15; }
  &.blue { background: linear-gradient(135deg, #4facfe, #00f2fe); }
  &.green { background: linear-gradient(135deg, #11998e, #38ef7d); }
  &.orange { background: linear-gradient(135deg, #f093fb, #f5576c); }
}
.card { background: white; border-radius: 12px; padding: 20px; box-shadow: 0 2px 12px rgba(0,0,0,0.06); }
.card-header { display: flex; justify-content: space-between; align-items: center; margin-bottom: 16px; h3 { margin: 0; font-size: 16px; } }
.quick-actions { display: grid; grid-template-columns: 1fr 1fr; gap: 12px; }
.action-item { display: flex; flex-direction: column; align-items: center; gap: 8px; padding: 20px 10px; border-radius: 8px; cursor: pointer; transition: all 0.3s; &:hover { background: #f5f7fa; transform: translateY(-2px); } span { font-size: 13px; color: #606266; } }
.ai-suggestion { p { margin: 0 0 12px; color: #606266; line-height: 1.6; } .ai-tip { font-size: 13px; color: #909399; background: #fdf6ec; padding: 8px 12px; border-radius: 6px; } }
</style>
