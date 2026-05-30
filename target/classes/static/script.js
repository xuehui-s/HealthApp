// 全局配置
axios.defaults.baseURL = 'http://localhost:8080';
axios.defaults.timeout = 10000;
let currentRole = '';

// 请求拦截器：自动带token和用户ID，解决token无效问题
axios.interceptors.request.use(config => {
    const token = localStorage.getItem('token');
    const userId = localStorage.getItem('userId');
    if (token) {
        config.headers.Authorization = `Bearer ${token}`;
    }
    if (currentRole === 'patient') {
        config.headers.patientId = userId;
    } else if (currentRole === 'doctor') {
        config.headers.doctorId = userId;
    }
    return config;
});

// 响应拦截器：统一处理错误
axios.interceptors.response.use(res => {
    if (res.data.code !== 200) {
        showToast(res.data.msg || '操作失败');
        return Promise.reject(res.data);
    }
    return res.data;
}, err => {
    if (err.response?.status === 401) {
        showToast('登录已过期，请重新登录');
        logout();
    } else {
        showToast('请求失败，请检查网络');
    }
    return Promise.reject(err);
});

// 工具函数
function showToast(msg) {
    const toast = document.getElementById('toast');
    toast.textContent = msg;
    toast.style.display = 'block';
    setTimeout(() => toast.style.display = 'none', 2000);
}

// 页面切换
function hideAllPages() {
    document.querySelectorAll('.page').forEach(p => p.style.display = 'none');
}

function goToRoleSelect() {
    hideAllPages();
    document.getElementById('roleSelectPage').style.display = 'flex';
}

function goToLogin(role) {
    currentRole = role;
    hideAllPages();
    document.getElementById('loginPage').style.display = 'flex';
    document.getElementById('loginTitle').textContent = role === 'patient' ? '患者登录' : '医生登录';
    // 医生隐藏注册按钮
    document.querySelector('.link-btn').style.display = role === 'patient' ? 'block' : 'none';
}

function goToRegister() {
    hideAllPages();
    document.getElementById('registerPage').style.display = 'flex';
    document.getElementById('registerTitle').textContent = '患者注册';
}

function goToLoginFromRegister() {
    goToLogin('patient');
}

// 验证码发送
function sendCode() {
    const username = document.getElementById('loginUsername').value.trim();
    if (!username) return showToast('请先输入手机号/工号');
    const url = currentRole === 'patient' ? '/patient/getCode' : '/doctor/sendCode';
    axios.get(url, { params: { username } }).then(() => {
        showToast('验证码已发送');
    });
}

// 登录逻辑（关键：绝不删除token）
async function handleLogin(e) {
    e.preventDefault();
    const username = document.getElementById('loginUsername').value.trim();
    const password = document.getElementById('loginPassword').value.trim();
    const code = document.getElementById('loginCode').value.trim();
    if (!username || !password || !code) return showToast('请填写完整信息');

    const url = currentRole === 'patient' ? '/patient/login' : '/doctor/login';
    const res = await axios.post(url, { username, password, code });

    // ✅ 登录成功后，保存token和用户信息，不删除！
    localStorage.setItem('token', res.data);
    localStorage.setItem('userRole', currentRole);
    localStorage.setItem('userId', username);
    enterMainPage();
}

// 注册逻辑
async function handleRegister(e) {
    e.preventDefault();
    const phone = document.getElementById('regPhone').value.trim();
    const password = document.getElementById('regPassword').value.trim();
    await axios.post('/patient/register', { phone, password });
    showToast('注册成功，请登录');
    goToLoginFromRegister();
}

// 进入主页面
function enterMainPage() {
    hideAllPages();
    document.getElementById('mainPage').style.display = 'block';
    document.getElementById('userName').textContent = localStorage.getItem('userId');

    // 角色适配显示
    if (currentRole === 'patient') {
        document.querySelector('.doctor-only').style.display = 'none';
        document.getElementById('patientPayment').style.display = 'block';
        document.getElementById('doctorPayment').style.display = 'none';
        // 加载患者数据
        loadDepartments();
        loadMyAppointments();
        loadWaitPayOrders();
        loadMessages('all');
    } else {
        document.querySelector('.doctor-only').style.display = 'inline-block';
        document.getElementById('patientPayment').style.display = 'none';
        document.getElementById('doctorPayment').style.display = 'block';
    }
}

// 退出登录
function logout() {
    localStorage.clear();
    location.reload();
}

// 模块切换
function switchModule(module) {
    document.querySelectorAll('.module').forEach(m => m.classList.remove('active'));
    document.getElementById(`${module}Module`).classList.add('active');
    document.querySelectorAll('.nav-btn').forEach(n => n.classList.remove('active'));
    event.target.classList.add('active');
}

// 医生子标签切换
function switchDoctorTab(tab) {
    document.querySelectorAll('.doctor-tab').forEach(t => t.classList.remove('active'));
    document.getElementById(`${tab}Tab`).classList.add('active');
    document.querySelectorAll('.tab-btn').forEach(b => b.classList.remove('active'));
    event.target.classList.add('active');
}

// 预约功能
async function loadDepartments() {
    const res = await axios.get('/appointment/dept/list');
    const list = res.data || [];
    const sel = document.getElementById('deptSelect');
    sel.innerHTML = '<option value="">请选择科室</option>';
    list.forEach(d => {
        sel.innerHTML += `<option value="${d.id}">${d.name}</option>`;
    });
}

async function loadDoctors() {
    const deptId = document.getElementById('deptSelect').value;
    if (!deptId) return;
    const res = await axios.get('/appointment/doctor/list', { params: { deptId } });
    const list = res.data || [];
    const sel = document.getElementById('doctorSelect');
    sel.innerHTML = '<option value="">请选择医生</option>';
    list.forEach(d => {
        sel.innerHTML += `<option value="${d.id}">${d.name}</option>`;
    });
}

function loadPeriods() {
    const box = document.getElementById('periodOptions');
    box.innerHTML = `
        <button class="period-btn" onclick="selectPeriod(this, 'AM')">上午</button>
        <button class="period-btn" onclick="selectPeriod(this, 'PM')">下午</button>
    `;
}

let selectedPeriod = '';
function selectPeriod(btn, period) {
    selectedPeriod = period;
    document.querySelectorAll('.period-btn').forEach(b => b.style.background = '#eee');
    btn.style.background = '#409eff';
    btn.style.color = '#fff';
}

async function submitAppointment() {
    const deptId = document.getElementById('deptSelect').value;
    const doctorId = document.getElementById('doctorSelect').value;
    const date = document.getElementById('appointDate').value;
    if (!deptId || !doctorId || !date || !selectedPeriod) return showToast('请选择完整信息');

    await axios.post('/appointment/submit', {
        deptId, doctorId, appointmentDate: date, period: selectedPeriod
    });
    showToast('预约成功');
    loadMyAppointments();
}

async function loadMyAppointments() {
    const res = await axios.get('/appointment/my');
    const list = res.data || [];
    document.getElementById('myAppointments').innerHTML = list.map(item => `
        <div class="list-item">
            科室：${item.deptName} | 医生：${item.doctorName} | 日期：${item.appointmentDate} | 时段：${item.period}
        </div>
    `).join('');
}

// 缴费功能
async function loadWaitPayOrders() {
    const patientId = localStorage.getItem('userId');
    const res = await axios.get(`/payOrder/waitPay/${patientId}`);
    const list = res.data || [];
    document.getElementById('waitPayList').innerHTML = list.map(item => `
        <div class="list-item">
            订单号：${item.orderNo} | 金额：${item.totalAmount}元 | 状态：待缴费
        </div>
    `).join('');
}

async function createBill() {
    const aid = document.getElementById('billAppointmentId').value;
    const items = document.getElementById('billItems').value;
    const amount = document.getElementById('billAmount').value;
    await axios.post('/payOrder/create', {
        appointmentId: aid, items, totalAmount: amount
    });
    showToast('开单成功');
}

async function docCreateBill() {
    const aid = document.getElementById('docBillAppId').value;
    const items = document.getElementById('docBillItems').value;
    const amount = document.getElementById('docBillAmount').value;
    await axios.post('/payOrder/create', {
        appointmentId: aid, items, totalAmount: amount
    });
    showToast('开单成功');
}

// 消息功能
async function loadMessages(type) {
    const userId = localStorage.getItem('userId');
    const userType = currentRole === 'patient' ? 1 : 2;
    const res = await axios.get('/message/my', { params: { userId, userType } });
    let list = res.data || [];
    if (type === 'unread') {
        list = list.filter(m => !m.isRead);
    }
    document.getElementById('messageList').innerHTML = list.map(m => `
        <div class="list-item">${m.content}</div>
    `).join('');
}

// 医生请假功能
async function applyLeave() {
    const type = document.getElementById('leaveType').value;
    const start = document.getElementById('leaveStart').value;
    const end = document.getElementById('leaveEnd').value;
    const url = type === 'normal' ? '/doctor/appointment/leave/normal' : '/doctor/appointment/leave/emergency';
    await axios.post(url, { startDate: start, endDate: end });
    showToast('请假申请成功');
}

// 初始化
document.addEventListener('DOMContentLoaded', () => {
    const today = new Date().toISOString().split('T')[0];
    document.getElementById('appointDate').value = today;
    document.getElementById('leaveStart').min = today;
    document.getElementById('leaveEnd').min = today;
});
// 初始化：默认显示角色选择页面
goToRoleSelect();