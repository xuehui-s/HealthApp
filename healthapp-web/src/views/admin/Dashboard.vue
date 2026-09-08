<template>
  <div class="admin-dashboard">
    <!-- 统计卡片 -->
    <el-row :gutter="20">
      <el-col :span="6"><div class="stat-card blue"><div class="stat-label">今日预约</div><div class="stat-value">{{ stats.todayAppointments }}</div><div class="stat-trend up">↑ 12%</div><el-icon class="stat-icon"><Calendar /></el-icon></div></el-col>
      <el-col :span="6"><div class="stat-card green"><div class="stat-label">今日营收</div><div class="stat-value">¥{{ stats.todayRevenue }}</div><div class="stat-trend up">↑ 8%</div><el-icon class="stat-icon"><Money /></el-icon></div></el-col>
      <el-col :span="6"><div class="stat-card orange"><div class="stat-label">总患者数</div><div class="stat-value">{{ stats.totalPatients }}</div><div class="stat-trend up">↑ 5%</div><el-icon class="stat-icon"><User /></el-icon></div></el-col>
      <el-col :span="6"><div class="stat-card"><div class="stat-label">AI对话数</div><div class="stat-value">{{ stats.todayAiChats }}</div><div class="stat-trend up">↑ 23%</div><el-icon class="stat-icon"><ChatDotRound /></el-icon></div></el-col>
    </el-row>

    <el-row :gutter="20" style="margin-top: 20px">
      <!-- 预约趋势图 -->
      <el-col :span="14">
        <div class="card">
          <div class="card-header"><h3>近7天预约趋势</h3><el-radio-group v-model="trendDays" size="small"><el-radio-button :value="7">7天</el-radio-button><el-radio-button :value="30">30天</el-radio-button></el-radio-group></div>
          <div ref="trendChartRef" style="height: 300px"></div>
        </div>
      </el-col>
      <!-- 支付方式分布 -->
      <el-col :span="10">
        <div class="card">
          <div class="card-header"><h3>支付方式分布</h3></div>
          <div ref="pieChartRef" style="height: 300px"></div>
        </div>
      </el-col>
    </el-row>

    <el-row :gutter="20" style="margin-top: 20px">
      <!-- 营收趋势 -->
      <el-col :span="14">
        <div class="card">
          <div class="card-header"><h3>营收趋势</h3></div>
          <div ref="revenueChartRef" style="height: 280px"></div>
        </div>
      </el-col>
      <!-- 科室排行 -->
      <el-col :span="10">
        <div class="card">
          <div class="card-header"><h3>科室预约排行</h3></div>
          <div ref="barChartRef" style="height: 280px"></div>
        </div>
      </el-col>
    </el-row>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted, nextTick, watch } from 'vue'
import * as echarts from 'echarts'
import { Calendar, Money, User, ChatDotRound } from '@element-plus/icons-vue'

const stats = reactive({ todayAppointments: 48, todayRevenue: '12,580', totalPatients: 1256, todayAiChats: 89 })
const trendDays = ref(7)
const trendChartRef = ref<HTMLElement>()
const pieChartRef = ref<HTMLElement>()
const revenueChartRef = ref<HTMLElement>()
const barChartRef = ref<HTMLElement>()

const initTrendChart = () => {
  const chart = echarts.init(trendChartRef.value!)
  const dates = Array.from({ length: trendDays.value }, (_, i) => {
    const d = new Date(); d.setDate(d.getDate() - (trendDays.value - 1 - i)); return `${d.getMonth() + 1}/${d.getDate()}`
  })
  chart.setOption({
    tooltip: { trigger: 'axis' },
    legend: { data: ['预约量', '就诊量'], right: 0 },
    grid: { left: '3%', right: '4%', bottom: '3%', containLabel: true },
    xAxis: { type: 'category', data: dates, boundaryGap: false },
    yAxis: { type: 'value' },
    series: [
      { name: '预约量', type: 'line', smooth: true, data: dates.map(() => Math.floor(Math.random() * 50 + 20)), areaStyle: { opacity: 0.3 }, itemStyle: { color: '#667eea' } },
      { name: '就诊量', type: 'line', smooth: true, data: dates.map(() => Math.floor(Math.random() * 40 + 15)), areaStyle: { opacity: 0.3 }, itemStyle: { color: '#13c2c2' } },
    ],
  })
}

const initPieChart = () => {
  const chart = echarts.init(pieChartRef.value!)
  chart.setOption({
    tooltip: { trigger: 'item' },
    legend: { orient: 'vertical', left: 'left' },
    series: [{
      type: 'pie', radius: ['40%', '70%'], avoidLabelOverlap: false,
      itemStyle: { borderRadius: 8, borderColor: '#fff', borderWidth: 2 },
      label: { show: false }, emphasis: { label: { show: true, fontSize: 16, fontWeight: 'bold' } },
      data: [
        { value: 1048, name: '微信支付', itemStyle: { color: '#07c160' } },
        { value: 735, name: '支付宝', itemStyle: { color: '#1677ff' } },
        { value: 580, name: '现金', itemStyle: { color: '#faad14' } },
        { value: 484, name: '银行卡', itemStyle: { color: '#722ed1' } },
        { value: 300, name: '医保', itemStyle: { color: '#f5222d' } },
      ],
    }],
  })
}

const initRevenueChart = () => {
  const chart = echarts.init(revenueChartRef.value!)
  const dates = Array.from({ length: 7 }, (_, i) => { const d = new Date(); d.setDate(d.getDate() - (6 - i)); return `${d.getMonth() + 1}/${d.getDate()}` })
  chart.setOption({
    tooltip: { trigger: 'axis' },
    grid: { left: '3%', right: '4%', bottom: '3%', containLabel: true },
    xAxis: { type: 'category', data: dates },
    yAxis: { type: 'value', name: '元' },
    series: [{ type: 'bar', data: dates.map(() => Math.floor(Math.random() * 8000 + 3000)), itemStyle: { color: new echarts.graphic.LinearGradient(0, 0, 0, 1, [{ offset: 0, color: '#667eea' }, { offset: 1, color: '#764ba2' }]), borderRadius: [4, 4, 0, 0] } }],
  })
}

const initBarChart = () => {
  const chart = echarts.init(barChartRef.value!)
  chart.setOption({
    tooltip: { trigger: 'axis' },
    grid: { left: '3%', right: '4%', bottom: '3%', containLabel: true },
    xAxis: { type: 'value' },
    yAxis: { type: 'category', data: ['中医科', '皮肤科', '眼科', '妇产科', '儿科', '外科', '内科'] },
    series: [{ type: 'bar', data: [45, 62, 78, 95, 120, 156, 189], itemStyle: { color: new echarts.graphic.LinearGradient(0, 0, 1, 0, [{ offset: 0, color: '#11998e' }, { offset: 1, color: '#38ef7d' }]), borderRadius: [0, 4, 4, 0] }, label: { show: true, position: 'right' } }],
  })
}

watch(trendDays, () => nextTick(initTrendChart))
onMounted(() => { nextTick(() => { initTrendChart(); initPieChart(); initRevenueChart(); initBarChart() }) })
</script>

<style scoped lang="scss">
.stat-card { background: linear-gradient(135deg, #667eea, #764ba2); color: white; border-radius: 12px; padding: 24px; position: relative; overflow: hidden;
  .stat-label { font-size: 14px; opacity: 0.9; } .stat-value { font-size: 28px; font-weight: 700; margin: 8px 0 4px; }
  .stat-trend { font-size: 12px; &.up { color: #52c41a; } }
  .stat-icon { position: absolute; right: 16px; top: 50%; transform: translateY(-50%); font-size: 48px; opacity: 0.15; }
  &.blue { background: linear-gradient(135deg, #4facfe, #00f2fe); }
  &.green { background: linear-gradient(135deg, #11998e, #38ef7d); }
  &.orange { background: linear-gradient(135deg, #f093fb, #f5576c); }
}
.card { background: white; border-radius: 12px; padding: 20px; box-shadow: 0 2px 12px rgba(0,0,0,0.06); }
.card-header { display: flex; justify-content: space-between; align-items: center; margin-bottom: 16px; h3 { margin: 0; font-size: 16px; } }
</style>
