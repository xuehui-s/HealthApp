import { createRouter, createWebHistory, RouteRecordRaw } from 'vue-router'
import NProgress from 'nprogress'
import 'nprogress/nprogress.css'
import { useUserStore } from '@/stores/user'

const routes: RouteRecordRaw[] = [
  {
    path: '/login',
    name: 'Login',
    component: () => import('@/views/login/index.vue'),
    meta: { title: '登录', requiresAuth: false },
  },
  {
    path: '/',
    redirect: '/dashboard',
  },
  // 患者端
  {
    path: '/patient',
    component: () => import('@/layouts/PatientLayout.vue'),
    meta: { requiresAuth: true, role: 'patient' },
    children: [
      { path: 'dashboard', name: 'PatientDashboard', component: () => import('@/views/patient/Dashboard.vue'), meta: { title: '首页' } },
      { path: 'appointment', name: 'PatientAppointment', component: () => import('@/views/patient/Appointment.vue'), meta: { title: '预约挂号' } },
      { path: 'my-appointments', name: 'MyAppointments', component: () => import('@/views/patient/MyAppointments.vue'), meta: { title: '我的预约' } },
      { path: 'payment', name: 'PatientPayment', component: () => import('@/views/patient/Payment.vue'), meta: { title: '缴费管理' } },
      { path: 'medical-records', name: 'MedicalRecords', component: () => import('@/views/patient/MedicalRecords.vue'), meta: { title: '电子病历' } },
      { path: 'messages', name: 'PatientMessages', component: () => import('@/views/patient/Messages.vue'), meta: { title: '消息通知' } },
      { path: 'profile', name: 'PatientProfile', component: () => import('@/views/patient/Profile.vue'), meta: { title: '个人中心' } },
    ],
  },
  // 医生端
  {
    path: '/doctor',
    component: () => import('@/layouts/DoctorLayout.vue'),
    meta: { requiresAuth: true, role: 'doctor' },
    children: [
      { path: 'dashboard', name: 'DoctorDashboard', component: () => import('@/views/doctor/Dashboard.vue'), meta: { title: '工作台' } },
      { path: 'appointments', name: 'DoctorAppointments', component: () => import('@/views/doctor/Appointments.vue'), meta: { title: '预约管理' } },
      { path: 'diagnosis/:id', name: 'DoctorDiagnosis', component: () => import('@/views/doctor/Diagnosis.vue'), meta: { title: '诊疗开单' } },
      { path: 'prescription', name: 'DoctorPrescription', component: () => import('@/views/doctor/Prescription.vue'), meta: { title: '处方管理' } },
      { path: 'leave', name: 'DoctorLeave', component: () => import('@/views/doctor/Leave.vue'), meta: { title: '请假管理' } },
      { path: 'agent', name: 'DoctorAgent', component: () => import('@/views/agent/DoctorAgent.vue'), meta: { title: 'AI助手' } },
    ],
  },
  // 管理端
  {
    path: '/admin',
    component: () => import('@/layouts/AdminLayout.vue'),
    meta: { requiresAuth: true, role: 'admin' },
    children: [
      { path: 'dashboard', name: 'AdminDashboard', component: () => import('@/views/admin/Dashboard.vue'), meta: { title: '数据看板' } },
      { path: 'patients', name: 'AdminPatients', component: () => import('@/views/admin/Patients.vue'), meta: { title: '患者管理' } },
      { path: 'doctors', name: 'AdminDoctors', component: () => import('@/views/admin/Doctors.vue'), meta: { title: '医生管理' } },
      { path: 'departments', name: 'AdminDepartments', component: () => import('@/views/admin/Departments.vue'), meta: { title: '科室管理' } },
      { path: 'orders', name: 'AdminOrders', component: () => import('@/views/admin/Orders.vue'), meta: { title: '订单管理' } },
      { path: 'drugs', name: 'AdminDrugs', component: () => import('@/views/admin/Drugs.vue'), meta: { title: '药品管理' } },
      { path: 'logs', name: 'AdminLogs', component: () => import('@/views/admin/Logs.vue'), meta: { title: '操作日志' } },
    ],
  },
  // AI助手（通用）
  {
    path: '/agent',
    name: 'Agent',
    component: () => import('@/views/agent/Chat.vue'),
    meta: { title: 'AI助手', requiresAuth: true },
  },
]

const router = createRouter({
  history: createWebHistory(),
  routes,
})

NProgress.configure({ showSpinner: false })

router.beforeEach((to, from, next) => {
  NProgress.start()
  document.title = `${to.meta.title || '智慧医疗'} - HealthApp`

  const userStore = useUserStore()
  if (to.meta.requiresAuth && !userStore.token) {
    next({ path: '/login', query: { redirect: to.fullPath } })
  } else {
    next()
  }
})

router.afterEach(() => {
  NProgress.done()
})

export default router
