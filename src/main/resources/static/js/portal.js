/* ============================================================
   智慧医疗门户 · 前端逻辑
   - 零依赖（原生 fetch），同源请求，无硬编码地址
   - 兼容两代后端响应格式：legacy {success,errorMsg,data,total}
     与企业版 {code,message,data}
   - AI 助手通过 fetch 流式读取 SSE，携带鉴权头
   ============================================================ */
'use strict';

/* ==================== 状态 ==================== */
const store = {
  role: localStorage.getItem('userRole') || '',          // patient | doctor
  token: localStorage.getItem('token') || '',
  userId: localStorage.getItem('userId') || '',          // patientId / doctorId
  username: localStorage.getItem('username') || '',
  name: localStorage.getItem('name') || '',
  aiSession: localStorage.getItem('aiSession') || '',
};

/* ==================== 常量映射 ==================== */
const APPT_STATUS = { 0: ['待就诊', 'blue'], 1: ['已签到', 'orange'], 2: ['待缴费', 'orange'], 3: ['已缴费', 'green'], 4: ['已取消', 'gray'], 5: ['医生取消', 'gray'], 6: ['超时终止', 'red'] };
const PAY_STATUS = { 0: ['待缴费', 'orange'], 1: ['已缴费', 'green'], 2: ['已作废', 'gray'], 3: ['超时作废', 'gray'], 4: ['已退款', 'blue'], 5: ['部分退款', 'blue'] };
const REFUND_STATUS = { 0: ['待审核', 'orange'], 1: ['已退款', 'green'], 2: ['已拒绝', 'red'] };
const PAY_METHOD = { CASH: '现金', WECHAT: '微信', ALIPAY: '支付宝', BANK_CARD: '银行卡', MEDICARE: '医保' };
const BILL_CAT = { DRUG: '药品', EXAM: '检查', CONSULT: '诊查', MATERIAL: '材料', TREAT: '治疗', REGISTRATION: '挂号', OTHER: '其他' };

/* ==================== 基础工具 ==================== */
const $ = (id) => document.getElementById(id);

function esc(s) {
  return String(s ?? '').replace(/[&<>"']/g, c => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' }[c]));
}
function money(v) {
  const n = Number(v);
  return isNaN(n) ? '0.00' : n.toFixed(2);
}
function tag(map, key) {
  const item = map[key];
  if (!item) return `<span class="tag">${esc(key)}</span>`;
  return `<span class="tag ${item[1]}">${item[0]}</span>`;
}
function catName(k) { return BILL_CAT[k] || k; }
function methodName(k) { return PAY_METHOD[k] || k; }
function fmtDate(d) { return d ? String(d).slice(0, 10) : '-'; }
function fmtTime(t) { return t ? String(t).replace('T', ' ').slice(0, 16) : '-'; }

function toast(msg, type = '') {
  const box = $('toast-box');
  const el = document.createElement('div');
  el.className = 'toast ' + type;
  el.textContent = msg;
  box.appendChild(el);
  setTimeout(() => { el.style.opacity = '0'; el.style.transition = 'opacity .3s'; setTimeout(() => el.remove(), 300); }, 2600);
}

function openModal(title, bodyHtml, footHtml = '') {
  $('modalTitle').textContent = title;
  $('modalBody').innerHTML = bodyHtml;
  $('modalFoot').innerHTML = footHtml;
  $('modalMask').classList.add('show');
}
function closeModal() { $('modalMask').classList.remove('show'); }
$('modalMask').addEventListener('click', e => { if (e.target === $('modalMask')) closeModal(); });

/* ==================== HTTP ==================== */
async function http(method, path, body = null, opts = {}) {
  const headers = {};
  if (store.token) headers['Authorization'] = 'Bearer ' + store.token;
  if (store.role === 'patient' && store.userId) headers['patientId'] = store.userId;
  if (store.role === 'doctor' && store.userId) headers['doctorId'] = store.userId;
  if (body !== null) headers['Content-Type'] = 'application/json';

  const resp = await fetch(path, {
    method, headers,
    body: body !== null ? JSON.stringify(body) : undefined,
    signal: opts.signal,
  });

  if (resp.status === 401) { forceLogout('登录已过期，请重新登录'); throw new Error('401'); }
  const res = await resp.json().catch(() => ({}));

  // legacy 格式
  if (typeof res.success === 'boolean') {
    if (!res.success) throw new Error(res.errorMsg || '操作失败');
    return res;
  }
  // 企业版格式
  if (typeof res.code === 'number') {
    if (res.code === 10002 || res.code === 20005 || res.code === 20006) {
      forceLogout('登录已过期，请重新登录'); throw new Error('401');
    }
    if (res.code !== 200) throw new Error(res.message || '操作失败');
    return res;
  }
  return res;
}
const GET = (p) => http('GET', p);
const POST = (p, b) => http('POST', p, b ?? {});
const PUT = (p, b) => http('PUT', p, b ?? {});

function forceLogout(msg) {
  if (msg) toast(msg, 'err');
  localStorage.clear();
  setTimeout(() => location.reload(), 800);
}

/* ==================== 登录 / 注册 ==================== */
let loginRole = 'patient';

function switchRole(role) {
  loginRole = role;
  $('rt-patient').classList.toggle('on', role === 'patient');
  $('rt-doctor').classList.toggle('on', role === 'doctor');
  const isPatient = role === 'patient';
  $('accountLabel').textContent = isPatient ? '手机号' : '医生工号';
  $('loginAccount').placeholder = isPatient ? '请输入手机号' : '请输入医生工号（身份证后4位）';
  $('loginSub').textContent = isPatient ? '请使用预留手机号登录预约系统' : '请使用工号登录医生工作站';
}

function showRegister() {
  $('view-login').style.display = 'none';
  $('view-register').style.display = 'flex';
}
function backToLogin() {
  $('view-register').style.display = 'none';
  $('view-login').style.display = 'flex';
}

let codeCooldown = 0;
function sendCode() {
  const account = $('loginAccount').value.trim();
  if (!account) return toast('请先输入账号', 'err');
  if (codeCooldown > 0) return;
  const url = loginRole === 'patient'
    ? `/patient/getCode?username=${encodeURIComponent(account)}`
    : `/doctor/sendCode?username=${encodeURIComponent(account)}`;
  GET(url).then(() => {
    toast('验证码已发送（见后端控制台）', 'ok');
    codeCooldown = 60;
    const btn = $('btnSendCode');
    const timer = setInterval(() => {
      btn.textContent = `${codeCooldown}s 后重发`;
      btn.disabled = true;
      if (--codeCooldown <= 0) { clearInterval(timer); btn.textContent = '获取验证码'; btn.disabled = false; }
    }, 1000);
  }).catch(e => toast(e.message, 'err'));
}

async function doLogin(e) {
  e.preventDefault();
  const account = $('loginAccount').value.trim();
  const password = $('loginPassword').value;
  const code = $('loginCode').value.trim();
  if (!account || !password || !code) return toast('请填写完整登录信息', 'err');
  $('btnLogin').disabled = true;
  try {
    const res = loginRole === 'patient'
      ? await POST('/patient/login', { username: account, password, code })
      : await POST('/doctor/login', { username: account, password, code });
    const d = res.data || {};
    store.role = loginRole;
    store.token = d.token;
    store.userId = String(d.patientId ?? d.doctorId ?? '');
    store.username = d.username || account;
    store.name = d.name || d.username || account;
    saveSession();
    toast('登录成功', 'ok');
    enterApp();
  } catch (err) { toast(err.message, 'err'); }
  $('btnLogin').disabled = false;
}

async function doRegister(e) {
  e.preventDefault();
  const phone = $('regPhone').value.trim();
  const p1 = $('regPwd').value, p2 = $('regPwd2').value;
  if (!/^1[3-9]\d{9}$/.test(phone)) return toast('请输入正确的手机号', 'err');
  if (p1 !== p2) return toast('两次输入的密码不一致', 'err');
  try {
    await POST('/patient/register', { username: phone, password: p1 });
    toast('注册成功，请登录', 'ok');
    backToLogin();
    switchRole('patient');
    $('loginAccount').value = phone;
  } catch (err) { toast(err.message, 'err'); }
}

function saveSession() {
  localStorage.setItem('token', store.token);
  localStorage.setItem('userRole', store.role);
  localStorage.setItem('userId', store.userId);
  localStorage.setItem('username', store.username);
  localStorage.setItem('name', store.name);
}
function doLogout() {
  const url = store.role === 'patient' ? '/patient/logout' : '/doctor/logout';
  http('POST', url, {}).catch(() => {}).finally(() => forceLogout(''));
}

/* ==================== 应用骨架 ==================== */
const NAV_ITEMS = {
  patient: [
    ['page-book', '预约挂号', 'i-calendar'],
    ['page-my-appt', '我的预约', 'i-list'],
    ['page-pay', '缴费中心', 'i-wallet'],
    ['page-msg', '消息中心', 'i-bell'],
    ['page-ai', 'AI 助手', 'i-robot'],
  ],
  doctor: [
    ['page-work', '门诊工作台', 'i-stethoscope'],
    ['page-billing', '收费开单', 'i-receipt'],
    ['page-settle', '日结营收', 'i-chart'],
    ['page-leave', '请假管理', 'i-plane'],
    ['page-msg', '消息中心', 'i-bell'],
    ['page-ai', 'AI 助手', 'i-robot'],
  ],
};

function enterApp() {
  $('view-login').style.display = 'none';
  $('view-register').style.display = 'none';
  $('view-app').style.display = 'flex';
  $('whoName').textContent = store.name || store.username;
  $('whoRole').textContent = store.role === 'patient' ? '患者' : '医生';
  $('userAvatar').textContent = (store.name || store.username || '用').slice(0, 1);
  buildNav();
  switchPage(store.role === 'patient' ? 'page-book' : 'page-work');
  refreshUnread();
  setInterval(refreshUnread, 30000);
}

function buildNav() {
  const nav = $('mainNav');
  nav.innerHTML = NAV_ITEMS[store.role].map(([page, label, icon]) => `
    <button data-page="${page}" onclick="switchPage('${page}')">
      <svg class="icon"><use href="#${icon}"/></svg>${label}
      ${page === 'page-msg' ? '<span class="badge" id="unreadBadge"></span>' : ''}
    </button>`).join('');
}

let currentPage = '';
function switchPage(page) {
  currentPage = page;
  document.querySelectorAll('.page').forEach(p => p.classList.remove('on'));
  $(page).classList.add('on');
  document.querySelectorAll('#mainNav button').forEach(b => b.classList.toggle('on', b.dataset.page === page));

  if (page === 'page-book' && !$('deptGrid').dataset.loaded) loadDepts();
  if (page === 'page-my-appt') loadMyAppointments();
  if (page === 'page-pay') switchPayTab(currentPayTab);
  if (page === 'page-work') loadWorkbench();
  if (page === 'page-leave') loadLeaves();
  if (page === 'page-msg') loadMessages();
  if (page === 'page-ai') initAiChat();
}

/* ==================== 预约挂号 ==================== */
const book = { deptId: null, deptName: '', date: null, period: null, doctorId: null, doctorName: '' };

async function loadDepts() {
  try {
    const res = await GET('/appointment/dept/list');
    const list = res.data || [];
    $('deptGrid').dataset.loaded = '1';
    $('deptGrid').innerHTML = list.map(d => `
      <div class="dept-item" data-id="${d.id}" onclick="pickDept(${d.id}, '${esc(d.name)}')">
        ${esc(d.name)}<small>${esc((d.description || '').slice(0, 10))}</small>
      </div>`).join('') || '<div class="empty">暂无科室</div>';
  } catch (e) { toast(e.message, 'err'); }
}

async function pickDept(id, name) {
  book.deptId = id; book.deptName = name;
  book.date = null; book.period = null; book.doctorId = null;
  document.querySelectorAll('.dept-item').forEach(el => el.classList.toggle('on', +el.dataset.id === id));
  renderSummary();
  $('dayStrip').innerHTML = '<div class="empty">加载中...</div>';
  $('doctorGrid').innerHTML = '<div class="empty">请选择日期与时段</div>';
  $('rem-am').textContent = $('rem-pm').textContent = '选择日期后查看余量';
  try {
    const res = await GET(`/appointment/day/status?deptId=${id}`);
    const days = res.data || [];
    $('dayStrip').innerHTML = days.map(d => {
      const dt = new Date(d.date + 'T00:00:00');
      const week = ['日', '一', '二', '三', '四', '五', '六'][dt.getDay()];
      const isToday = fmtDate(d.date) === fmtDate(new Date());
      return `
      <div class="day-item ${d.isFull ? 'full' : ''}" data-date="${d.date}" onclick="${d.isFull ? '' : `pickDay('${d.date}')`}">
        <b>${isToday ? '今天' : dt.getMonth() + 1 + '/' + dt.getDate()}</b>
        <span>周${week} · 余 ${d.remaining}</span>
      </div>`;
    }).join('') || '<div class="empty">近 7 天暂无号源</div>';
  } catch (e) { toast(e.message, 'err'); $('dayStrip').innerHTML = '<div class="empty">加载失败</div>'; }
}

async function pickDay(date) {
  book.date = date; book.period = null; book.doctorId = null;
  document.querySelectorAll('.day-item').forEach(el => el.classList.toggle('on', el.dataset.date === date));
  document.querySelectorAll('.period-item').forEach(el => el.classList.remove('on'));
  $('doctorGrid').innerHTML = '<div class="empty">请选择时段</div>';
  renderSummary();
  // 两个时段余量
  for (const p of ['上午', '下午']) {
    try {
      const res = await GET(`/appointment/period/status?deptId=${book.deptId}&date=${date}&period=${encodeURIComponent(p)}`);
      const d = res.data || {};
      $(p === '上午' ? 'rem-am' : 'rem-pm').textContent = d.canAppoint ? `余 ${d.remaining} 个号源` : '已不可预约';
      if (!d.canAppoint) $(p === '上午' ? 'rem-am' : 'rem-pm').parentElement.classList.add('full');
      else $(p === '上午' ? 'rem-am' : 'rem-pm').parentElement.classList.remove('full');
    } catch { $(p === '上午' ? 'rem-am' : 'rem-pm').textContent = '-'; }
  }
}

async function pickPeriod(period) {
  if (!book.date) return toast('请先选择日期', 'err');
  const el = document.querySelector(`.period-item[data-period="${period}"]`);
  if (el.classList.contains('full')) return toast('该时段已停止挂号', 'err');
  book.period = period; book.doctorId = null;
  document.querySelectorAll('.period-item').forEach(x => x.classList.toggle('on', x === el));
  renderSummary();
  $('doctorGrid').innerHTML = '<div class="empty">加载中...</div>';
  try {
    const res = await GET(`/appointment/doctor/list/with-leave?deptId=${book.deptId}&date=${book.date}&period=${encodeURIComponent(period)}`);
    const docs = res.data || [];
    $('doctorGrid').innerHTML = docs.map(d => `
      <div class="doctor-item ${d.onLeave ? 'off' : ''}" data-id="${d.id}"
           onclick="${d.onLeave ? `toast('该医生本时段请假', 'err')` : `pickDoctor(${d.id}, '${esc(d.name)}')`}">
        <div class="avatar">${esc((d.name || '医').slice(0, 1))}</div>
        <div><b>${esc(d.name)}</b><span>${d.onLeave ? '本时段请假' : '正常出诊'}</span></div>
      </div>`).join('') || '<div class="empty">该时段暂无出诊医生</div>';
  } catch (e) { toast(e.message, 'err'); }
}

function pickDoctor(id, name) {
  book.doctorId = id; book.doctorName = name;
  document.querySelectorAll('.doctor-item').forEach(el => el.classList.toggle('on', +el.dataset.id === id));
  renderSummary();
}

function renderSummary() {
  $('sumDept').textContent = book.deptName || '-';
  $('sumDoctor').textContent = book.doctorName || '-';
  $('sumDate').textContent = book.date || '-';
  $('sumPeriod').textContent = book.period || '-';
}

async function submitAppointment() {
  if (!book.deptId || !book.date || !book.period || !book.doctorId) {
    return toast('请完成科室 / 日期 / 时段 / 医生的选择', 'err');
  }
  $('btnSubmitBook').disabled = true;
  try {
    const res = await POST('/appointment/submit', {
      deptId: book.deptId, doctorId: book.doctorId,
      appointDate: book.date, timePeriod: book.period,
    });
    const d = res.data || {};
    openModal('挂号成功', `
      <div style="text-align:center;padding:8px 0 4px">
        <svg class="icon" style="width:46px;height:46px;color:var(--success)"><use href="#i-check"/></svg>
        <p style="font-size:16px;font-weight:600;margin:8px 0 2px">预约成功，排队号 <span style="color:var(--primary)">${esc(d.queueNum ?? '-')}</span> 号</p>
        <p style="color:var(--text-muted);font-size:13px">前方等待 ${esc(d.frontCount ?? 0)} 人 · ${esc(book.deptName)} ${esc(book.doctorName)} ${esc(book.date)} ${esc(book.period)}</p>
      </div>`,
      `<button class="btn" onclick="closeModal();switchPage('page-my-appt')">查看我的预约</button>`);
    book.date = null; book.period = null; book.doctorId = null;
    document.querySelectorAll('.day-item.on,.period-item.on,.doctor-item.on').forEach(el => el.classList.remove('on'));
    renderSummary();
  } catch (e) { toast(e.message, 'err'); }
  $('btnSubmitBook').disabled = false;
}

/* ==================== 我的预约 / 门诊工作台 ==================== */
async function loadMyAppointments() {
  const body = $('myApptBody');
  body.innerHTML = '<tr><td colspan="6" class="empty">加载中...</td></tr>';
  try {
    const res = await GET('/appointment/my');
    const list = res.data || [];
    $('myApptHead').innerHTML = '<th>科室</th><th>医生</th><th>就诊日期</th><th>时段</th><th>排队号</th><th>状态 / 操作</th>';
    body.innerHTML = list.map(a => `
      <tr>
        <td>${esc(a.deptName ?? a.dept_id ?? a.deptId ?? '-')}</td>
        <td>${esc(a.doctorName ?? a.doctor_id ?? a.doctorId ?? '-')}</td>
        <td>${fmtDate(a.appointDate)}</td>
        <td>${esc(a.timePeriod || '-')}</td>
        <td>${esc(a.queueNum ?? '-')}</td>
        <td>${tag(APPT_STATUS, a.status)}
          ${a.status === 0 ? `<button class="btn sm danger-ghost" style="margin-left:8px" onclick="cancelAppt(${a.id})">取消</button>` : ''}
        </td>
      </tr>`).join('') || '<tr><td colspan="6" class="empty"><svg class="icon"><use href="#i-empty"/></svg><br>暂无预约记录</td></tr>';
  } catch (e) { body.innerHTML = `<tr><td colspan="6" class="empty">${esc(e.message)}</td></tr>`; }
}

async function cancelAppt(id) {
  openModal('取消预约', '<p>确定要取消该预约吗？取消后号源将释放，当日取消次数有限（3 次）。</p>',
    `<button class="btn ghost" onclick="closeModal()">再想想</button>
     <button class="btn danger" onclick="confirmCancel(${id})">确认取消</button>`);
}
async function confirmCancel(id) {
  try {
    await POST(`/appointment/cancel?id=${id}`);
    closeModal(); toast('预约已取消', 'ok'); loadMyAppointments();
  } catch (e) { toast(e.message, 'err'); }
}

async function loadWorkbench() {
  const body = $('workBody');
  body.innerHTML = '<tr><td colspan="6" class="empty">加载中...</td></tr>';
  try {
    const res = await GET('/doctor/appointment/my');
    const list = res.data || [];
    body.innerHTML = list.map(a => `
      <tr>
        <td>${esc(a.id ?? '-')}</td>
        <td>${esc(a.patientName ?? a.patientId ?? '-')}</td>
        <td>${fmtDate(a.appointDate)}</td>
        <td>${esc(a.timePeriod || '-')}</td>
        <td>${esc(a.queueNum ?? '-')}</td>
        <td>${tag(APPT_STATUS, a.status)}</td>
      </tr>`).join('') || '<tr><td colspan="6" class="empty"><svg class="icon"><use href="#i-empty"/></svg><br>暂无待就诊患者</td></tr>';
  } catch (e) { body.innerHTML = `<tr><td colspan="6" class="empty">${esc(e.message)}</td></tr>`; }
}

/* ==================== 缴费中心（患者） ==================== */
let currentPayTab = 'waitpay';
function switchPayTab(tab) {
  currentPayTab = tab;
  document.querySelectorAll('#page-pay .subtabs button').forEach(b => b.classList.toggle('on', b.dataset.tab === tab));
  if (tab === 'waitpay') loadWaitPayPatient();
  if (tab === 'records') loadPayRecords(1);
  if (tab === 'refunds') loadRefundRecords();
}

async function loadWaitPayPatient() {
  const panel = $('payPanel');
  panel.innerHTML = '<div class="empty">加载中...</div>';
  try {
    const res = await GET(`/payOrder/waitPay/${store.userId}`);
    const list = res.data || [];
    if (!list.length) { panel.innerHTML = '<div class="empty"><svg class="icon"><use href="#i-empty"/></svg><br>暂无待缴费订单</div>'; return; }
    panel.innerHTML = `<table class="tbl"><thead><tr><th>单号</th><th>金额(元)</th><th>开单时间</th><th>状态</th><th>操作</th></tr></thead><tbody>` +
      list.map(o => `
        <tr>
          <td style="font-family:monospace">${esc(o.orderNo)}</td>
          <td><b>¥${money(o.totalAmount)}</b></td>
          <td>${fmtTime(o.createTime)}</td>
          <td>${tag(PAY_STATUS, o.status)}</td>
          <td><button class="btn sm ghost" onclick="openDetail('${esc(o.orderNo)}')">明细</button>
              <button class="btn sm" onclick="openRefund('${esc(o.orderNo)}', ${money(o.totalAmount)})">申请退款</button></td>
        </tr>`).join('') + '</tbody></table>';
  } catch (e) { panel.innerHTML = `<div class="empty">${esc(e.message)}</div>`; }
}

let payRecordPage = 1;
async function loadPayRecords(page) {
  const panel = $('payPanel');
  panel.innerHTML = '<div class="empty">加载中...</div>';
  try {
    const res = await GET(`/payOrder/myList?patientId=${store.userId}&page=${page}&size=8`);
    const list = res.data || [];
    const total = Number(res.total || 0);
    const pages = Math.max(1, Math.ceil(total / 8));
    payRecordPage = page;
    panel.innerHTML = `<table class="tbl"><thead><tr><th>单号</th><th>金额(元)</th><th>支付方式</th><th>缴费时间</th><th>状态</th><th>操作</th></tr></thead><tbody>` +
      list.map(o => `
        <tr>
          <td style="font-family:monospace">${esc(o.orderNo)}</td>
          <td><b>¥${money(o.totalAmount)}</b></td>
          <td>${methodName(o.payMethod) || '-'}</td>
          <td>${fmtTime(o.payTime)}</td>
          <td>${tag(PAY_STATUS, o.status)}</td>
          <td><button class="btn sm ghost" onclick="openDetail('${esc(o.orderNo)}')">明细</button></td>
        </tr>`).join('') + '</tbody></table>' +
      (pages > 1 ? `<div class="pager">共 ${total} 条
        <button ${page <= 1 ? 'disabled' : ''} onclick="loadPayRecords(${page - 1})">上一页</button>
        <span>${page} / ${pages}</span>
        <button ${page >= pages ? 'disabled' : ''} onclick="loadPayRecords(${page + 1})">下一页</button></div>` : '');
  } catch (e) { panel.innerHTML = `<div class="empty">${esc(e.message)}</div>`; }
}

async function loadRefundRecords() {
  const panel = $('payPanel');
  panel.innerHTML = '<div class="empty">加载中...</div>';
  try {
    const res = await GET(`/payOrder/refund/list/${store.userId}`);
    const list = res.data || [];
    if (!list.length) { panel.innerHTML = '<div class="empty"><svg class="icon"><use href="#i-empty"/></svg><br>暂无退款记录</div>'; return; }
    panel.innerHTML = `<table class="tbl"><thead><tr><th>退款单号</th><th>原订单</th><th>退款金额(元)</th><th>原因</th><th>状态</th><th>申请时间</th></tr></thead><tbody>` +
      list.map(r => `
        <tr>
          <td style="font-family:monospace">${esc(r.refundNo)}</td>
          <td style="font-family:monospace">${esc(r.orderNo)}</td>
          <td><b>¥${money(r.refundAmount)}</b></td>
          <td>${esc(r.refundReason || '-')}</td>
          <td>${tag(REFUND_STATUS, r.status)}</td>
          <td>${fmtTime(r.createTime)}</td>
        </tr>`).join('') + '</tbody></table>';
  } catch (e) { panel.innerHTML = `<div class="empty">${esc(e.message)}</div>`; }
}

/* ==================== 缴费单详情 / 退款（患者） ==================== */
async function openDetail(orderNo) {
  try {
    const res = await GET(`/payOrder/detail/${orderNo}`);
    const d = res.data || {};
    const order = d.order || d;
    const items = d.items || order.items || [];
    const rows = (items || []).map(it => `
      <tr><td>${catName(it.category)}</td><td>${esc(it.itemName)}</td>
      <td>¥${money(it.unitPrice)}</td><td>×${esc(it.quantity)}</td>
      <td style="text-align:right">¥${money(it.subtotal)}</td></tr>`).join('');
    openModal('缴费凭证', `
      <p style="font-family:monospace;color:var(--text-muted);font-size:13px">单号：${esc(order.orderNo || orderNo)}</p>
      ${order.transactionNo ? `<p style="font-family:monospace;color:var(--text-muted);font-size:13px">流水号：${esc(order.transactionNo)}</p>` : ''}
      ${order.payTime ? `<p style="color:var(--text-muted);font-size:13px">支付时间：${fmtTime(order.payTime)} · ${methodName(order.payMethod)}</p>` : ''}
      ${rows ? `<table class="tbl" style="margin-top:10px"><thead><tr><th>类别</th><th>项目</th><th>单价</th><th>数量</th><th style="text-align:right">小计</th></tr></thead><tbody>${rows}</tbody></table>` : ''}
      <p style="text-align:right;margin-top:12px;font-size:15px">合计：<b style="color:var(--primary);font-size:20px">¥${money(order.totalAmount)}</b></p>`,
      `<button class="btn ghost" onclick="printReceipt('${esc(orderNo)}')">打印凭证</button>
       <button class="btn" onclick="closeModal()">关闭</button>`);
    if (order.status === 1 && !order.receiptPrinted) {
      POST(`/payOrder/receiptPrinted?orderNo=${encodeURIComponent(order.orderNo || orderNo)}`).catch(() => {});
    }
  } catch (e) { toast(e.message, 'err'); }
}

function printReceipt(orderNo) {
  const w = window.open('', '_blank', 'width=520,height=680');
  const body = $('modalBody').innerHTML;
  w.document.write(`<!DOCTYPE html><html><head><meta charset="utf-8"><title>缴费凭证 ${esc(orderNo)}</title>
    <style>body{font-family:"Microsoft YaHei",sans-serif;padding:28px;color:#1F2A37}
    h2{text-align:center;letter-spacing:4px}table{width:100%;border-collapse:collapse;font-size:13px;margin-top:10px}
    td,th{border-bottom:1px solid #ccc;padding:6px 4px;text-align:left}
    .tail{margin-top:24px;text-align:center;color:#666;font-size:12px}</style></head>
    <body><h2>缴费凭证</h2>${body}<p class="tail">感谢您的就诊 · 本凭证盖章有效</p></body></html>`);
  w.document.close();
  w.focus();
  w.print();
}

function openRefund(orderNo, amount) {
  openModal('申请退款', `
    <div class="field"><label>原订单</label><input class="input" value="${esc(orderNo)}" readonly style="font-family:monospace"></div>
    <div class="field"><label>退款金额（可退 ¥${amount}）</label><input class="input" id="refundAmt" type="number" step="0.01" value="${amount}"></div>
    <div class="field"><label>退款方式</label>
      <select class="input" id="refundMethod">
        <option value="WECHAT">微信</option><option value="ALIPAY">支付宝</option>
        <option value="BANK_CARD">银行卡</option><option value="CASH">现金</option><option value="MEDICARE">医保</option>
      </select></div>
    <div class="field"><label>退款原因</label><textarea class="input" id="refundReason" rows="2" placeholder="请填写退款原因"></textarea></div>`,
    `<button class="btn ghost" onclick="closeModal()">取消</button>
     <button class="btn" onclick="submitRefund('${esc(orderNo)}')">提交申请</button>`);
}
async function submitRefund(orderNo) {
  const refundAmount = $('refundAmt').value;
  const refundReason = $('refundReason').value.trim();
  if (!refundAmount || Number(refundAmount) <= 0) return toast('请填写退款金额', 'err');
  if (!refundReason) return toast('请填写退款原因', 'err');
  try {
    await POST('/payOrder/refund/apply', {
      orderNo, refundAmount, refundReason,
      refundMethod: $('refundMethod').value, operatorId: store.userId,
    });
    closeModal(); toast('退款申请已提交，等待审核', 'ok'); switchPayTab(currentPayTab);
  } catch (e) { toast(e.message, 'err'); }
}

/* ==================== 收费开单（医生） ==================== */
function addBillRow() {
  const row = document.createElement('div');
  row.className = 'bill-row';
  row.innerHTML = `
    <select class="input cat">
      ${Object.keys(BILL_CAT).map(k => `<option value="${k}">${BILL_CAT[k]}</option>`).join('')}
    </select>
    <input class="input name" placeholder="项目名称">
    <input class="input price" type="number" step="0.01" min="0" placeholder="0.00" oninput="calcTotal()">
    <input class="input qty" type="number" min="1" value="1" oninput="calcTotal()">
    <span class="sub">¥0.00</span>
    <button class="del" onclick="removeBillRow(this)">×</button>`;
  $('billItems').appendChild(row);
}
function removeBillRow(btn) { btn.parentElement.remove(); calcTotal(); }
function calcTotal() {
  let total = 0;
  document.querySelectorAll('#billItems .bill-row:not(.head)').forEach(row => {
    const price = Number(row.querySelector('.price').value) || 0;
    const qty = Number(row.querySelector('.qty').value) || 0;
    const sub = price * qty;
    row.querySelector('.sub').textContent = '¥' + money(sub);
    total += sub;
  });
  $('billTotal').textContent = money(total);
}
addBillRow(); addBillRow();

async function createPayOrder() {
  const appointmentId = $('billApptId').value.trim();
  const items = [];
  document.querySelectorAll('#billItems .bill-row:not(.head)').forEach(row => {
    const name = row.querySelector('.name').value.trim();
    const price = row.querySelector('.price').value;
    const qty = row.querySelector('.qty').value;
    if (name && price) items.push({ category: row.querySelector('.cat').value, itemName: name, unitPrice: price, quantity: qty });
  });
  if (!appointmentId) return toast('请填写关联预约ID', 'err');
  if (!items.length) return toast('请至少添加一条费用明细', 'err');
  try {
    await POST('/payOrder/create', { appointmentId, items, remark: $('billRemark').value.trim() });
    toast('缴费单已开立，已通知患者缴费', 'ok');
    $('billApptId').value = ''; $('billRemark').value = '';
    document.querySelectorAll('#billItems .bill-row:not(.head)').forEach((r, i) => { if (i > 1) r.remove(); });
    document.querySelectorAll('#billItems .bill-row:not(.head)').forEach(r => {
      r.querySelector('.name').value = ''; r.querySelector('.price').value = ''; r.querySelector('.qty').value = 1;
    });
    calcTotal();
  } catch (e) { toast(e.message, 'err'); }
}

async function loadWaitPay() {
  const pid = $('qPatientId').value.trim();
  const box = $('waitPayBox');
  if (!pid) return toast('请输入患者ID', 'err');
  box.innerHTML = '<div class="empty">查询中...</div>';
  try {
    const res = await GET(`/payOrder/waitPay/${pid}`);
    const list = res.data || [];
    if (!list.length) { box.innerHTML = '<div class="empty"><svg class="icon"><use href="#i-empty"/></svg><br>该患者暂无待缴费单</div>'; return; }
    box.innerHTML = list.map(o => `
      <div style="border:1px solid var(--border);border-radius:8px;padding:12px;margin-bottom:10px">
        <div style="display:flex;justify-content:space-between;align-items:center">
          <div><span style="font-family:monospace;font-size:12.5px">${esc(o.orderNo)}</span><br>
          <b style="font-size:16px">¥${money(o.totalAmount)}</b></div>
          <div style="display:flex;gap:8px">
            <select class="input" id="pm-${esc(o.orderNo)}" style="width:96px;padding:6px 8px">
              ${Object.keys(PAY_METHOD).map(k => `<option value="${k}">${PAY_METHOD[k]}</option>`).join('')}
            </select>
            <button class="btn sm success" onclick="payOrder('${esc(o.orderNo)}')">收款</button>
            <button class="btn sm danger-ghost" onclick="invalidOrder('${esc(o.orderNo)}')">作废</button>
          </div>
        </div>
      </div>`).join('');
  } catch (e) { box.innerHTML = `<div class="empty">${esc(e.message)}</div>`; }
}

async function payOrder(orderNo) {
  const payMethod = $('pm-' + orderNo)?.value || 'CASH';
  openModal('确认收款', `<p>订单 <b style="font-family:monospace">${esc(orderNo)}</b></p>
    <p style="margin-top:6px">支付方式：<b>${methodName(payMethod)}</b>，确认收款后订单状态将变更为已缴费。</p>`,
    `<button class="btn ghost" onclick="closeModal()">取消</button>
     <button class="btn success" onclick="confirmPay('${esc(orderNo)}','${payMethod}')">确认收款</button>`);
}
async function confirmPay(orderNo, payMethod) {
  try {
    const res = await POST('/payOrder/pay', { orderNo, payMethod, payerId: store.userId });
    closeModal(); toast('收款成功', 'ok');
    openDetail(orderNo);
    loadWaitPay();
  } catch (e) { toast(e.message, 'err'); }
}
async function invalidOrder(orderNo) {
  openModal('作废缴费单', `<p>确定作废订单 <b style="font-family:monospace">${esc(orderNo)}</b> 吗？作废后不可恢复。</p>`,
    `<button class="btn ghost" onclick="closeModal()">取消</button>
     <button class="btn danger" onclick="confirmInvalid('${esc(orderNo)}')">确认作废</button>`);
}
async function confirmInvalid(orderNo) {
  try {
    await POST(`/payOrder/invalid?orderNo=${encodeURIComponent(orderNo)}`);
    closeModal(); toast('订单已作废', 'ok'); loadWaitPay();
  } catch (e) { toast(e.message, 'err'); }
}

/* ==================== 日结与营收 ==================== */
function renderStatsCards(map) {
  const defs = [
    ['总单量', map.totalCount ?? map.total ?? '-', '笔'],
    ['总营收', map.totalRevenue ?? map.revenue ?? '-', '元'],
    ['退款笔数', map.refundCount ?? '-', '笔'],
    ['退款金额', map.refundAmount ?? '-', '元'],
    ['净营收', map.netRevenue ?? map.net ?? '-', '元'],
  ];
  $('settleStats').innerHTML = defs.map(([label, value, unit]) => `
    <div class="stat-card"><div class="label">${label}</div>
    <div class="value">${esc(value)}<small>${unit}</small></div></div>`).join('');
}

async function dailySettlement() {
  try {
    const res = await POST(`/payOrder/settlement?cashierId=${store.userId}`);
    renderStatsCards(res.data || {});
    const rows = res.data?.byPayMethod || res.data?.payMethodStats || [];
    if (Array.isArray(rows) && rows.length) {
      $('revenuePanel').innerHTML = `<table class="tbl"><thead><tr><th>支付方式</th><th>笔数</th><th>金额(元)</th></tr></thead><tbody>` +
        rows.map(r => `<tr><td>${methodName(r.payMethod)}</td><td>${esc(r.count ?? '-')}</td><td>¥${money(r.amount ?? r.totalAmount)}</td></tr>`).join('') + '</tbody></table>';
    }
    toast('日终结算完成', 'ok');
  } catch (e) { toast(e.message, 'err'); }
}

async function queryRevenue() {
  const start = $('statStart').value, end = $('statEnd').value;
  if (!start || !end) return toast('请选择查询日期区间', 'err');
  const panel = $('revenuePanel');
  panel.innerHTML = '<div class="empty">查询中...</div>';
  try {
    const res = await POST('/payOrder/revenueStats', {
      startDate: start, endDate: end, payMethod: $('statPayMethod').value || null,
    });
    const d = res.data;
    const list = Array.isArray(d) ? d : (d?.rows || d?.list || []);
    if (!list.length) { panel.innerHTML = '<div class="empty"><svg class="icon"><use href="#i-empty"/></svg><br>该区间暂无营收数据</div>'; renderStatsCards({}); return; }
    const cols = Object.keys(list[0]).slice(0, 6);
    panel.innerHTML = `<table class="tbl"><thead><tr>${cols.map(c => `<th>${esc(c)}</th>`).join('')}</tr></thead><tbody>` +
      list.map(r => `<tr>${cols.map(c => `<td>${esc(r[c] ?? '-')}</td>`).join('')}</tr>`).join('') + '</tbody></table>';
    renderStatsCards(list[0]);
  } catch (e) { panel.innerHTML = `<div class="empty">${esc(e.message)}</div>`; }
}

/* ==================== 请假管理 ==================== */
async function applyLeave() {
  const type = $('leaveType').value;
  const leaveDate = $('leaveStart').value, endDate = $('leaveEnd').value;
  if (!leaveDate || !endDate) return toast('请选择请假日期区间', 'err');
  const url = type === 'normal' ? '/doctor/appointment/leave/normal' : '/doctor/appointment/leave/emergency';
  try {
    await POST(url, { leaveDate, endDate, timePeriod: $('leavePeriod').value });
    toast(type === 'normal' ? '请假申请成功' : '紧急请假成功，受影响预约已自动取消', 'ok');
    loadLeaves();
  } catch (e) { toast(e.message, 'err'); }
}

async function loadLeaves() {
  const body = $('leaveBody');
  body.innerHTML = '<tr><td colspan="5" class="empty">加载中...</td></tr>';
  try {
    const res = await GET('/doctor/appointment/leave/my');
    const list = res.data || [];
    body.innerHTML = list.map(l => `
      <tr>
        <td>${l.type === 2 ? '<span class="tag red">紧急</span>' : '<span class="tag blue">常规</span>'}</td>
        <td>${fmtDate(l.leaveDate)} ~ ${fmtDate(l.endDate)}</td>
        <td>${esc(l.timePeriod)}</td>
        <td>${l.status === 1 ? '<span class="tag green">生效中</span>' : '<span class="tag gray">已取消</span>'}</td>
        <td>${l.status === 1 ? `<button class="btn sm danger-ghost" onclick="cancelLeave(${l.id})">取消</button>` : '-'}</td>
      </tr>`).join('') || '<tr><td colspan="5" class="empty"><svg class="icon"><use href="#i-empty"/></svg><br>暂无请假记录</td></tr>';
  } catch (e) { body.innerHTML = `<tr><td colspan="5" class="empty">${esc(e.message)}</td></tr>`; }
}
async function cancelLeave(id) {
  try {
    await POST(`/doctor/appointment/leave/cancel?id=${id}`);
    toast('请假已取消', 'ok'); loadLeaves();
  } catch (e) { toast(e.message, 'err'); }
}

/* ==================== 消息中心 ==================== */
const MSG_TYPE = { 1: '预约成功', 2: '签到提醒', 3: '缴费通知', 4: '退款通知', 5: '请假取消', 6: '排队提醒', 7: '系统通知', 8: '订单超时' };
async function loadMessages() {
  const body = $('msgBody');
  body.innerHTML = '<tr><td colspan="5" class="empty">加载中...</td></tr>';
  try {
    const userType = store.role === 'patient' ? 1 : 2;
    const res = await GET(`/message/my?userId=${store.userId}&userType=${userType}`);
    const list = res.data || [];
    body.innerHTML = list.map(m => `
      <tr style="${m.isRead ? '' : 'background:#F7FAFF'}" onclick="markRead(${m.id})" title="点击标记已读">
        <td><span class="tag blue">${esc(MSG_TYPE[m.msgType] || '通知')}</span></td>
        <td><b>${esc(m.title || '-')}</b></td>
        <td style="color:var(--text-muted)">${esc((m.content || '').slice(0, 60))}</td>
        <td>${fmtTime(m.createTime)}</td>
        <td>${m.isRead ? '<span class="tag gray">已读</span>' : '<span class="tag orange">未读</span>'}</td>
      </tr>`).join('') || '<tr><td colspan="5" class="empty"><svg class="icon"><use href="#i-empty"/></svg><br>暂无消息</td></tr>';
  } catch (e) { body.innerHTML = `<tr><td colspan="5" class="empty">${esc(e.message)}</td></tr>`; }
}
async function markRead(id) {
  try { await POST(`/message/read/${id}`); loadMessages(); refreshUnread(); } catch {}
}
async function refreshUnread() {
  if (!store.token) return;
  try {
    const userType = store.role === 'patient' ? 1 : 2;
    const res = await GET(`/message/unread?userId=${store.userId}&userType=${userType}`);
    const badge = $('unreadBadge');
    const n = Number(res.data || 0);
    if (badge) { badge.style.display = n > 0 ? 'flex' : 'none'; badge.textContent = n > 99 ? '99+' : n; }
  } catch {}
}

/* ==================== AI 助手（fetch 流式 SSE） ==================== */
let aiBusy = false, aiInited = false;

function aiMsgEl(role) {
  const wrap = document.createElement('div');
  wrap.className = 'msg ' + (role === 'user' ? 'user' : 'ai');
  wrap.innerHTML = `
    <div class="avatar">${role === 'user' ? '我' : 'AI'}</div>
    <div class="bubble"><span class="ai-typing"><i></i><i></i><i></i></span></div>`;
  $('aiMsgs').appendChild(wrap);
  $('aiMsgs').scrollTop = $('aiMsgs').scrollHeight;
  return wrap.querySelector('.bubble');
}
function aiScroll() { $('aiMsgs').scrollTop = $('aiMsgs').scrollHeight; }

function renderMdLite(el, text) {
  // 轻量渲染：**加粗**、换行、列表，不做完整 Markdown
  const html = esc(text)
    .replace(/\*\*(.+?)\*\*/g, '<b>$1</b>')
    .replace(/^### (.+)$/gm, '<b>$1</b>')
    .replace(/\n/g, '<br>');
  el.innerHTML = html;
}

async function initAiChat() {
  if (aiInited) return;
  aiInited = true;
  if (!store.aiSession) {
    store.aiSession = 'web-' + store.role + '-' + store.userId + '-' + Date.now().toString(36);
    localStorage.setItem('aiSession', store.aiSession);
  }
  const bubble = aiMsgEl('ai');
  bubble.innerHTML = '您好，我是<b>医智助手</b>。可以咨询疾病知识、用药疑问、报告解读，也可以查询您的预约与缴费记录。<br><span style="color:var(--text-muted);font-size:12px">急症情况请立即拨打 120 或前往急诊。</span>';
}

async function sendAi() {
  if (aiBusy) return;
  const input = $('aiInput');
  const question = input.value.trim();
  if (!question) return;
  input.value = '';
  aiBusy = true; $('aiSend').disabled = true;

  // 用户消息
  const userBubble = aiMsgEl('user');
  userBubble.textContent = question;

  // AI 气泡 + 工具事件容器
  const bubble = aiMsgEl('ai');
  bubble.innerHTML = '<span class="ai-typing"><i></i><i></i><i></i></span>';
  let answer = '';
  const toolChips = [];

  try {
    const headers = {
      'Content-Type': 'application/json',
      'Authorization': 'Bearer ' + store.token,
      'Accept': 'text/event-stream',
    };
    if (store.role === 'patient' && store.userId) headers['patientId'] = store.userId;
    if (store.role === 'doctor' && store.userId) headers['doctorId'] = store.userId;

    const resp = await fetch(`/api/v1/agent/stream?question=${encodeURIComponent(question)}&sessionId=${encodeURIComponent(store.aiSession)}`, {
      method: 'GET', headers,
    });
    if (!resp.ok || !resp.body) throw new Error('AI 服务连接失败 (' + resp.status + ')');

    const reader = resp.body.getReader();
    const decoder = new TextDecoder();
    let buf = '';

    while (true) {
      const { done, value } = await reader.read();
      if (done) break;
      buf += decoder.decode(value, { stream: true });
      const parts = buf.split('\n\n');
      buf = parts.pop();
      for (const part of parts) {
        const line = part.split('\n').find(l => l.startsWith('data:'));
        if (!line) continue;
        const payload = line.slice(5).trim();
        if (payload === '[DONE]') continue;
        let event;
        try { event = JSON.parse(payload); } catch { continue; }

        if (event.type === 'content') {
          answer += event.delta || '';
          renderMdLite(bubble, answer);
          aiScroll();
        } else if (event.type === 'tool_call') {
          toolChips.push(event.tool);
          const chip = document.createElement('div');
          chip.className = 'tool-chip';
          chip.innerHTML = `<svg class="icon"><use href="#i-tool"/></svg>${esc(event.tool)}`;
          bubble.parentElement.insertBefore(chip, bubble);
          aiScroll();
        } else if (event.type === 'error') {
          answer += '\n' + (event.message || 'AI 服务异常');
          renderMdLite(bubble, answer);
        }
      }
    }
    if (!answer) { bubble.innerHTML = '<span style="color:var(--text-muted)">（AI 未返回内容，请重试）</span>'; }
  } catch (e) {
    bubble.innerHTML = `<span style="color:var(--danger)">${esc(e.message || 'AI 服务暂时不可用')}</span>`;
  }
  aiBusy = false; $('aiSend').disabled = false;
  aiScroll();
}

async function clearAiHistory() {
  try {
    await http('DELETE', `/api/v1/agent/history?sessionId=${encodeURIComponent(store.aiSession)}`);
    $('aiMsgs').innerHTML = '';
    initAiChat();
    toast('会话已清空', 'ok');
  } catch (e) { toast(e.message, 'err'); }
}

/* ==================== 启动 ==================== */
document.addEventListener('keydown', e => {
  if (e.ctrlKey && e.key === 'Enter' && currentPage === 'page-ai') sendAi();
});
(function boot() {
  if (store.token && store.role && store.userId) {
    enterApp();
  } else {
    $('view-login').style.display = 'flex';
  }
})();
