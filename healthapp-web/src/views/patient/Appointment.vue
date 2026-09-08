<template>
  <div class="appointment-page">
    <div class="page-header">
      <h2>预约挂号</h2>
      <p>选择科室、医生和时间，完成在线预约</p>
    </div>

    <!-- 步骤条 -->
    <el-steps :active="step" finish-status="success" align-center class="steps">
      <el-step title="选择科室" />
      <el-step title="选择医生" />
      <el-step title="选择时间" />
      <el-step title="确认预约" />
    </el-steps>

    <div class="card">
      <!-- 步骤1：选择科室 -->
      <div v-if="step === 0" class="step-content">
        <h3>请选择科室</h3>
        <div class="dept-grid">
          <div
            v-for="dept in departments"
            :key="dept.id"
            class="dept-card"
            :class="{ active: selectedDept?.id === dept.id }"
            @click="selectDept(dept)"
          >
            <div class="dept-icon" :style="{ background: dept.color }">
              <el-icon :size="28"><OfficeBuilding /></el-icon>
            </div>
            <div class="dept-name">{{ dept.name }}</div>
            <div class="dept-desc">{{ dept.description }}</div>
          </div>
        </div>
      </div>

      <!-- 步骤2：选择医生 -->
      <div v-if="step === 1" class="step-content">
        <h3>请选择医生 - {{ selectedDept?.name }}</h3>
        <div class="doctor-list">
          <div
            v-for="doc in doctors"
            :key="doc.id"
            class="doctor-card"
            :class="{ active: selectedDoctor?.id === doc.id, disabled: doc.onLeave }"
            @click="!doc.onLeave && selectDoctor(doc)"
          >
            <el-avatar :size="56" :style="{ background: doc.onLeave ? '#909399' : '#409eff' }">
              {{ doc.name?.charAt(0) }}
            </el-avatar>
            <div class="doctor-info">
              <div class="doctor-name">
                {{ doc.name }}
                <el-tag size="small" type="info">{{ doc.title || '主治医师' }}</el-tag>
                <el-tag v-if="doc.onLeave" size="small" type="danger">请假中</el-tag>
              </div>
              <div class="doctor-dept">{{ selectedDept?.name }}</div>
            </div>
            <el-button v-if="!doc.onLeave" type="primary" size="small">选择</el-button>
            <span v-else class="leave-text">不可预约</span>
          </div>
        </div>
      </div>

      <!-- 步骤3：选择时间 -->
      <div v-if="step === 2" class="step-content">
        <h3>请选择预约时间</h3>
        <div class="date-selector">
          <div
            v-for="day in next7Days"
            :key="day.date"
            class="date-item"
            :class="{ active: selectedDate === day.date, disabled: day.full }"
            @click="!day.full && selectDate(day.date)"
          >
            <div class="date-weekday">{{ day.weekday }}</div>
            <div class="date-day">{{ day.day }}</div>
            <div class="date-remaining" :class="{ full: day.full }">
              {{ day.full ? '已满' : `余${day.remaining}` }}
            </div>
          </div>
        </div>
        <div v-if="selectedDate" class="period-selector">
          <h4>选择时段</h4>
          <div class="period-buttons">
            <button
              class="period-btn"
              :class="{ active: selectedPeriod === '上午' }"
              @click="selectedPeriod = '上午'"
            >
              <span class="period-name">上午</span>
              <span class="period-time">08:00 - 12:00</span>
            </button>
            <button
              class="period-btn"
              :class="{ active: selectedPeriod === '下午' }"
              @click="selectedPeriod = '下午'"
            >
              <span class="period-name">下午</span>
              <span class="period-time">14:00 - 17:30</span>
            </button>
          </div>
        </div>
      </div>

      <!-- 步骤4：确认预约 -->
      <div v-if="step === 3" class="step-content">
        <h3>确认预约信息</h3>
        <el-descriptions :column="2" border class="confirm-table">
          <el-descriptions-item label="患者">{{ userStore.userInfo?.name }}</el-descriptions-item>
          <el-descriptions-item label="科室">{{ selectedDept?.name }}</el-descriptions-item>
          <el-descriptions-item label="医生">{{ selectedDoctor?.name }}（{{ selectedDoctor?.title }}）</el-descriptions-item>
          <el-descriptions-item label="日期">{{ selectedDate }}</el-descriptions-item>
          <el-descriptions-item label="时段">{{ selectedPeriod }}</el-descriptions-item>
          <el-descriptions-item label="挂号费">¥10.00</el-descriptions-item>
        </el-descriptions>
        <el-alert
          title="预约成功后请按时就诊，如需取消请提前操作"
          type="info"
          :closable="false"
          style="margin-top: 20px"
        />
      </div>

      <!-- 操作按钮 -->
      <div class="step-actions">
        <el-button v-if="step > 0" @click="step--">上一步</el-button>
        <el-button v-if="step < 3" type="primary" :disabled="!canNext" @click="step++">下一步</el-button>
        <el-button v-if="step === 3" type="primary" :loading="submitting" @click="confirmAppointment">
          确认预约
        </el-button>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, computed } from 'vue'
import { ElMessage } from 'element-plus'
import { useUserStore } from '@/stores/user'
import { OfficeBuilding } from '@element-plus/icons-vue'

const userStore = useUserStore()
const step = ref(0)
const submitting = ref(false)
const selectedDept = ref<any>(null)
const selectedDoctor = ref<any>(null)
const selectedDate = ref('')
const selectedPeriod = ref('')

const colors = ['#409eff', '#67c23a', '#e6a23c', '#f56c6c', '#9b59b6', '#13c2c2', '#eb2f96', '#fa8c16']
const departments = ref([
  { id: 1, name: '内科', description: '呼吸、消化、心血管', color: colors[0] },
  { id: 2, name: '外科', description: '普外、骨科、泌尿', color: colors[1] },
  { id: 3, name: '儿科', description: '儿童疾病诊治', color: colors[2] },
  { id: 4, name: '妇产科', description: '妇科与产科保健', color: colors[3] },
  { id: 5, name: '眼科', description: '眼部疾病诊治', color: colors[4] },
  { id: 6, name: '口腔科', description: '口腔疾病诊治', color: colors[5] },
  { id: 7, name: '皮肤科', description: '皮肤疾病诊治', color: colors[6] },
  { id: 8, name: '中医科', description: '中医诊疗服务', color: colors[7] },
])

const doctors = ref([
  { id: 1, name: '张明华', title: '主任医师', onLeave: false },
  { id: 2, name: '李建国', title: '副主任医师', onLeave: false },
  { id: 3, name: '王小芳', title: '主治医师', onLeave: true },
  { id: 4, name: '赵伟强', title: '住院医师', onLeave: false },
])

const weekdays = ['周日', '周一', '周二', '周三', '周四', '周五', '周六']
const next7Days = computed(() => {
  const days = []
  const today = new Date()
  for (let i = 0; i < 7; i++) {
    const d = new Date(today)
    d.setDate(today.getDate() + i)
    days.push({
      date: d.toISOString().split('T')[0],
      weekday: i === 0 ? '今天' : i === 1 ? '明天' : weekdays[d.getDay()],
      day: `${d.getMonth() + 1}/${d.getDate()}`,
      remaining: Math.floor(Math.random() * 15),
      full: Math.random() > 0.8,
    })
  }
  return days
})

const canNext = computed(() => {
  if (step.value === 0) return !!selectedDept.value
  if (step.value === 1) return !!selectedDoctor.value
  if (step.value === 2) return !!selectedDate.value && !!selectedPeriod.value
  return true
})

const selectDept = (dept: any) => { selectedDept.value = dept }
const selectDoctor = (doc: any) => { selectedDoctor.value = doc }
const selectDate = (date: string) => { selectedDate.value = date }

const confirmAppointment = async () => {
  submitting.value = true
  setTimeout(() => {
    submitting.value = false
    ElMessage.success('预约成功！请按时就诊')
    step.value = 0
    selectedDept.value = null
    selectedDoctor.value = null
    selectedDate.value = ''
    selectedPeriod.value = ''
  }, 1000)
}
</script>

<style scoped lang="scss">
.appointment-page { max-width: 1000px; margin: 0 auto; }
.page-header { margin-bottom: 24px; h2 { margin: 0 0 4px; } p { color: #909399; margin: 0; } }
.steps { margin-bottom: 32px; }
.card { background: white; border-radius: 12px; padding: 30px; box-shadow: 0 2px 12px rgba(0,0,0,0.06); }
.step-content { min-height: 300px; h3 { margin: 0 0 20px; color: #303133; } }
.dept-grid { display: grid; grid-template-columns: repeat(4, 1fr); gap: 16px; }
.dept-card {
  border: 2px solid #ebeef5; border-radius: 10px; padding: 20px; text-align: center;
  cursor: pointer; transition: all 0.3s;
  &:hover { border-color: #409eff; transform: translateY(-2px); }
  &.active { border-color: #409eff; background: #ecf5ff; }
}
.dept-icon { width: 56px; height: 56px; border-radius: 12px; display: flex; align-items: center; justify-content: center; margin: 0 auto 12px; color: white; }
.dept-name { font-size: 16px; font-weight: 600; margin-bottom: 4px; }
.dept-desc { font-size: 12px; color: #909399; }
.doctor-list { display: flex; flex-direction: column; gap: 12px; }
.doctor-card {
  display: flex; align-items: center; gap: 16px; padding: 16px 20px;
  border: 2px solid #ebeef5; border-radius: 10px; cursor: pointer; transition: all 0.3s;
  &:hover:not(.disabled) { border-color: #409eff; }
  &.active { border-color: #409eff; background: #ecf5ff; }
  &.disabled { opacity: 0.5; cursor: not-allowed; }
}
.doctor-info { flex: 1; }
.doctor-name { font-size: 16px; font-weight: 600; display: flex; align-items: center; gap: 8px; margin-bottom: 4px; }
.doctor-dept { font-size: 13px; color: #909399; }
.leave-text { color: #f56c6c; font-size: 13px; }
.date-selector { display: flex; gap: 12px; margin-bottom: 24px; }
.date-item {
  flex: 1; text-align: center; padding: 16px 8px; border: 2px solid #ebeef5;
  border-radius: 10px; cursor: pointer; transition: all 0.3s;
  &:hover:not(.disabled) { border-color: #409eff; }
  &.active { border-color: #409eff; background: #ecf5ff; }
  &.disabled { opacity: 0.4; cursor: not-allowed; }
}
.date-weekday { font-size: 13px; color: #909399; }
.date-day { font-size: 18px; font-weight: 600; margin: 4px 0; }
.date-remaining { font-size: 12px; color: #67c23a; &.full { color: #f56c6c; } }
.period-buttons { display: flex; gap: 16px; }
.period-btn {
  flex: 1; padding: 20px; border: 2px solid #ebeef5; border-radius: 10px;
  background: white; cursor: pointer; transition: all 0.3s;
  &:hover { border-color: #409eff; }
  &.active { border-color: #409eff; background: #ecf5ff; }
}
.period-name { display: block; font-size: 18px; font-weight: 600; margin-bottom: 4px; }
.period-time { font-size: 13px; color: #909399; }
.confirm-table { margin-top: 10px; }
.step-actions { display: flex; justify-content: flex-end; gap: 12px; margin-top: 30px; padding-top: 20px; border-top: 1px solid #ebeef5; }
</style>
