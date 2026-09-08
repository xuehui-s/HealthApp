import { get, post } from '@/utils/request'

// ==================== 认证 ====================
export const login = (data: { username: string; password: string; role: string }) =>
  post('/auth/login', data)

export const register = (data: any) => post('/auth/register', data)

// ==================== 患者端 ====================
export const getDepartments = () => get('/appointment/dept/list')
export const getDayStatus = (deptId: number) => get('/appointment/day/status', { deptId })
export const getPeriodStatus = (deptId: number, date: string, period: string) =>
  get('/appointment/period/status', { deptId, date, period })
export const getDoctors = (deptId: number) => get('/appointment/doctor/list', { deptId })
export const getDoctorsWithLeave = (deptId: number, date: string, period: string) =>
  get('/appointment/doctor/list/with-leave', { deptId, date, period })
export const submitAppointment = (data: any) => post('/appointment/submit', data)
export const getMyAppointments = () => get('/appointment/my')
export const cancelAppointment = (id: number) => post('/appointment/cancel', null, { params: { id } })

// ==================== 缴费 ====================
export const getWaitPayOrders = (patientId: number) => get('/pay/wait-pay', { patientId })
export const payOrder = (data: any) => post('/pay/pay', data)
export const getMyOrders = (page: number, size: number) => get('/pay/my-list', { page, size })
export const getOrderDetail = (orderNo: string) => get('/pay/detail', { orderNo })
export const applyRefund = (data: any) => post('/pay/refund/apply', data)

// ==================== 医生端 ====================
export const getDoctorAppointments = () => get('/doctor/appointment/my')
export const createPayOrder = (data: any) => post('/pay/create', data)
export const applyLeave = (data: any, type: 'normal' | 'emergency') =>
  post(`/doctor/appointment/leave/${type}`, data)
export const cancelLeave = (id: number) => post('/doctor/appointment/leave/cancel', null, { params: { id } })
export const getMyLeaves = () => get('/doctor/appointment/leave/my')

// ==================== 消息 ====================
export const getMessages = (type: string = 'all') => get('/message/list', { type })
export const readMessage = (id: number) => post('/message/read', null, { params: { id } })
export const getUnreadCount = () => get('/message/unread-count')

// ==================== AI Agent ====================
export const agentChat = (data: { question: string; session_id?: string }) =>
  post('/agent/chat', data)
export const agentGetResult = (taskId: string) => get(`/agent/result/${taskId}`)

// ==================== 管理端 ====================
export const getDashboardStats = () => get('/admin/stats/dashboard')
export const getAppointmentTrend = (days: number = 7) => get('/admin/stats/appointment-trend', { days })
export const getRevenueTrend = (days: number = 7) => get('/admin/stats/revenue-trend', { days })
export const getDepartmentRanking = () => get('/admin/stats/department-ranking')
export const getDoctorWorkload = () => get('/admin/stats/doctor-workload')
export const getPayMethodDistribution = () => get('/admin/stats/pay-method-distribution')
