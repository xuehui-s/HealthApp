/* ============================================================
   运营管理控制台 · 前端逻辑
   - 全部对接企业版 /api/v1/admin/*（统一响应 {code,message,data}）
   - JWT(Bearer) + Redis 会话鉴权，401 自动退回登录
   - 图表为手写 SVG/DOM，无外部图表库依赖
   ============================================================ */
'use strict';

const $ = (id) => document.getElementById(id);
const store = {
  token: localStorage.getItem('adminToken') || '',
  name: localStorage.getItem('adminName') || '',
};

/* ==================== 常量 ==================== */
const APPT_STATUS = { 0: ['待就诊', 'blue'], 1: ['已签到', 'orange'], 2: ['待缴费', 'orange'], 3: ['已缴费', 'green'], 4: ['已取消', 'gray'], 5: ['医生取消', 'gray'], 6: ['超时终止', 'red'] };
const PAY_STATUS = { 0: ['待缴费', 'orange'], 1: ['已缴费', 'green'], 2: ['已作废', 'gray'], 3: ['超时作废', 'gray'], 4: ['已退款', 'blue'], 5: ['部分退款', 'blue'] };
const REFUND_STATUS = { 0: ['待审核', 'orange'], 1: ['已退款', 'green'], 2: ['已拒绝', 'red'] };
const PAY_METHOD = { CASH: '现金', WECHAT: '微信', ALIPAY: '支付宝', BANK_CARD: '银行卡', MEDICARE: '医保' };

/* ==================== 工具 ==================== */
function esc(s) {
  return String(s ?? '').replace(/[&<>"']/g, c => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' }[c]));
}
function money(v) { const n = Number(v); return isNaN(n) ? '0.00' : n.toFixed(2); }
function tag(map, key) {
  const item = map[key];
  return item ? `<span class="tag ${item[1]}">${item[0]}</span>` : `<span class="tag">${esc(key)}</span>`;
}
function methodName(k) { return PAY_METHOD[k] || k || '-'; }
function fmtTime(t) { return t ? String(t).replace('T', ' ').slice(0, 19) : '-'; }

function toast(msg, type = '') {
  const el = document.createElement('div');
  el.className = 'toast ' + type;
  el.textContent = msg;
  $('toast-box').appendChild(el);
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
async function api(method, path, body = null) {
  const headers = {};
  if (store.token) headers['Authorization'] = 'Bearer ' + store.token;
  if (body !== null) headers['Content-Type'] = 'application/json';
  const resp = await fetch(path, { method, headers, body: body !== null ? JSON.stringify(body) : undefined });
  const res = await resp.json().catch(() => ({}));
  if ([10002, 20005, 20006].includes(res.code)) { backToLogin('登录已过期'); throw new Error('401'); }
  if (res.code !== 200) throw new Error(res.message || `请求失败(${resp.status})`);
  return res.data;
}
const GET = (p) => api('GET', p);
const POST = (p, b) => api('POST', p, b ?? {});
const PUT = (p, b) => api('PUT', p, b ?? {});

function backToLogin(msg) {
  localStorage.removeItem('adminToken');
  localStorage.removeItem('adminName');
  store.token = '';
  if (msg) toast(msg, 'err');
  $('view-app').style.display = 'none';
  $('view-login').style.display = 'flex';
}

/* ==================== 登录 ==================== */
async function doLogin(e) {
  e.preventDefault();
  const username = $('loginUser').value.trim();
  const password = $('loginPwd').value;
  if (!username || !password) return toast('请输入账号与密码', 'err');
  $('btnLogin').disabled = true;
  try {
    const res = await fetch('/api/v1/admin/auth/login', {
      method: 'POST', headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ username, password }),
    });
    const r = await res.json();
    if (r.code !== 200) throw new Error(r.message || '登录失败');
    store.token = r.data.token;
    store.name = r.data.name || username;
    localStorage.setItem('adminToken', store.token);
    localStorage.setItem('adminName', store.name);
    enterApp();
    toast('欢迎回来，' + store.name, 'ok');
  } catch (err) { toast(err.message, 'err'); }
  $('btnLogin').disabled = false;
}
function doLogout() {
  POST('/api/v1/admin/auth/logout').catch(() => {}).finally(() => backToLogin(''));
}

/* ==================== 导航 ==================== */
const PAGE_LOADERS = {
  dashboard: loadDashboard,
  appointments: () => { loadAppointments(1); loadDeptOptions('apptDept'); },
  orders: loadOrders,
  refunds: loadRefunds,
  departments: loadDepartments,
  doctors: () => { loadDoctors(1); loadDeptOptions('docDept'); },
  patients: loadPatients,
  aichat: loadAiChats,
  logs: loadLogs,
};
function switchPage(page) {
  document.querySelectorAll('.page').forEach(p => p.classList.remove('on'));
  $('page-' + page).classList.add('on');
  document.querySelectorAll('#sideNav a').forEach(a => a.classList.toggle('on', a.dataset.page === page));
  PAGE_LOADERS[page] && PAGE_LOADERS[page]();
}
document.querySelectorAll('#sideNav a').forEach(a => a.addEventListener('click', () => switchPage(a.dataset.page)));

function pagerHtml(page, totalPages, fn) {
  if (totalPages <= 1) return '';
  return `<div class="pager">
    <button ${page <= 1 ? 'disabled' : ''} onclick="${fn}(${page - 1})">上一页</button>
    <span>${page} / ${totalPages}</span>
    <button ${page >= totalPages ? 'disabled' : ''} onclick="${fn}(${page + 1})">下一页</button></div>`;
}
function emptyBox(text) {
  return `<div class="empty"><svg class="icon"><use href="#i-empty"/></svg><br>${esc(text)}</div>`;
}

/* ==================== 数据看板 ==================== */
async function loadDashboard() {
  $('dashDate').textContent = new Date().toLocaleDateString('zh-CN', { year: 'numeric', month: 'long', day: 'numeric', weekday: 'long' });
  try {
    const d = await GET('/api/v1/admin/stats/dashboard');
    const cards = [
      ['今日预约', d.todayAppointments, '人次'],
      ['今日营收', '¥' + money(d.todayRevenue), ''],
      ['注册患者', d.totalPatients, '人'],
      ['在职医生', d.totalDoctors, '人'],
      ['待缴费订单', d.waitPayOrders, '单'],
      ['今日 AI 对话', d.todayAiChats, '次'],
    ];
    $('dashCards').innerHTML = cards.map(([label, value, unit]) => `
      <div class="stat-card"><div class="label">${label}</div>
      <div class="value">${esc(value ?? 0)}<small>${unit}</small></div></div>`).join('');
  } catch (e) { $('dashCards').innerHTML = emptyBox(e.message); }

  const [trend, revenue, deptRank, workload, payDist] = await Promise.allSettled([
    GET('/api/v1/admin/stats/appointment-trend?days=7'),
    GET('/api/v1/admin/stats/revenue-trend?days=7'),
    GET('/api/v1/admin/stats/department-ranking'),
    GET('/api/v1/admin/stats/doctor-workload'),
    GET('/api/v1/admin/stats/pay-method-distribution'),
  ]);

  if (trend.status === 'fulfilled') lineChart($('chartAppt'), (trend.value || []).map(x => ({ label: String(x.date).slice(5), value: Number(x.count) })), '人次');
  else $('chartAppt').innerHTML = emptyBox('加载失败');
  if (revenue.status === 'fulfilled') barChart($('chartRevenue'), (revenue.value || []).map(x => ({ label: String(x.date).slice(5), value: Number(x.revenue) })), '元');
  else $('chartRevenue').innerHTML = emptyBox('加载失败');
  if (deptRank.status === 'fulfilled') hBarChart($('chartDept'), (deptRank.value || []).map(x => ({ label: x.department || '-', value: Number(x.count) })));
  else $('chartDept').innerHTML = emptyBox('加载失败');
  if (payDist.status === 'fulfilled') hBarChart($('chartPay'), (payDist.value || []).map(x => ({ label: methodName(x.pay_method), value: Number(x.amount) })), true);
  else $('chartPay').innerHTML = emptyBox('加载失败');

  if (workload.status === 'fulfilled') {
    const list = workload.value || [];
    $('workloadBox').innerHTML = `<table class="tbl"><thead><tr><th>医生</th><th>科室</th><th>接诊量</th></tr></thead><tbody>` +
      list.map((w, i) => `<tr><td><b>${i + 1}</b>　${esc(w.doctor)}</td><td>${esc(w.department || '-')}</td><td>${esc(w.patient_count)}</td></tr>`).join('') +
      '</tbody></table>' || emptyBox('暂无数据');
  } else $('workloadBox').innerHTML = emptyBox('加载失败');
}

/* ==================== 手写图表 ==================== */
const PALETTE = ['#17649F', '#14808A', '#2F9E6E', '#C08A1E', '#CE4B4B', '#5E8FB8', '#7A6FB8', '#B8746F', '#3E8FB0', '#6E9E4F'];

function lineChart(el, data, unit) {
  if (!data.length) { el.innerHTML = emptyBox('暂无数据'); return; }
  const W = 480, H = 210, P = { l: 42, r: 12, t: 14, b: 26 };
  const max = Math.max(...data.map(d => d.value), 1);
  const iw = W - P.l - P.r, ih = H - P.t - P.b;
  const xs = i => P.l + (data.length === 1 ? iw / 2 : i * iw / (data.length - 1));
  const ys = v => P.t + ih - v / max * ih;
  const pts = data.map((d, i) => `${xs(i)},${ys(d.value)}`).join(' ');
  const grid = [0, .25, .5, .75, 1].map(r =>
    `<line x1="${P.l}" y1="${P.t + ih * r}" x2="${W - P.r}" y2="${P.t + ih * r}" stroke="#E4E8EE"/>
     <text x="${P.l - 6}" y="${P.t + ih * r + 4}" font-size="10" fill="#9AA6B6" text-anchor="end">${Math.round(max * (1 - r))}</text>`).join('');
  const labels = data.map((d, i) => `<text x="${xs(i)}" y="${H - 8}" font-size="10" fill="#67748A" text-anchor="middle">${esc(d.label)}</text>`).join('');
  const dots = data.map((d, i) => `<circle cx="${xs(i)}" cy="${ys(d.value)}" r="3.2" fill="#17649F"><title>${esc(d.label)}：${d.value}${unit}</title></circle>`).join('');
  el.innerHTML = `<svg viewBox="0 0 ${W} ${H}">${grid}
    <polyline points="${pts}" fill="none" stroke="#17649F" stroke-width="2.2" stroke-linejoin="round"/>
    <polygon points="${P.l},${P.t + ih} ${pts} ${W - P.r},${P.t + ih}" fill="rgba(23,100,159,.08)"/>
    ${dots}${labels}</svg>`;
}

function barChart(el, data, unit) {
  if (!data.length) { el.innerHTML = emptyBox('暂无数据'); return; }
  const W = 480, H = 210, P = { l: 46, r: 12, t: 14, b: 26 };
  const max = Math.max(...data.map(d => d.value), 1);
  const iw = W - P.l - P.r, ih = H - P.t - P.b;
  const step = iw / data.length;
  const grid = [0, .5, 1].map(r =>
    `<line x1="${P.l}" y1="${P.t + ih * (1 - r)}" x2="${W - P.r}" y2="${P.t + ih * (1 - r)}" stroke="#E4E8EE"/>
     <text x="${P.l - 6}" y="${P.t + ih * (1 - r) + 4}" font-size="10" fill="#9AA6B6" text-anchor="end">${Math.round(max * r)}</text>`).join('');
  const bars = data.map((d, i) => {
    const h = d.value / max * ih;
    const x = P.l + i * step + step * 0.18, w = step * 0.64;
    return `<rect x="${x}" y="${P.t + ih - h}" width="${w}" height="${Math.max(h, 1)}" rx="3" fill="#2F9E6E"><title>${esc(d.label)}：¥${money(d.value)}</title></rect>
      <text x="${x + w / 2}" y="${H - 8}" font-size="10" fill="#67748A" text-anchor="middle">${esc(d.label)}</text>`;
  }).join('');
  el.innerHTML = `<svg viewBox="0 0 ${W} ${H}">${grid}${bars}</svg>`;
}

function hBarChart(el, data, isMoney) {
  if (!data.length) { el.innerHTML = emptyBox('暂无数据'); return; }
  const max = Math.max(...data.map(d => d.value), 1);
  el.innerHTML = data.map((d, i) => `
    <div style="display:flex;align-items:center;gap:10px;margin:9px 0">
      <span style="width:92px;font-size:12.5px;color:var(--text-muted);text-align:right;flex:none">${esc(d.label)}</span>
      <div style="flex:1;background:#F0F3F7;border-radius:4px;height:16px;overflow:hidden">
        <div style="width:${Math.max(d.value / max * 100, 1.5)}%;height:100%;background:${PALETTE[i % PALETTE.length]};border-radius:4px"></div>
      </div>
      <b style="width:86px;font-size:12.5px;flex:none">${isMoney ? '¥' + money(d.value) : d.value}</b>
    </div>`).join('');
}

/* ==================== 预约管理 ==================== */
let deptOptionsLoaded = false;
async function loadDeptOptions(selectId) {
  try {
    const list = await GET('/api/v1/admin/departments');
    const el = $(selectId);
    const cur = el.value;
    el.innerHTML = '<option value="">全部科室</option>' +
      list.map(d => `<option value="${d.id}">${esc(d.name)}</option>`).join('');
    if (cur) el.value = cur;
    deptOptionsLoaded = true;
  } catch {}
}

async function loadAppointments(page) {
  const box = $('apptTable');
  box.innerHTML = '<div class="empty">加载中...</div>';
  try {
    const status = $('apptStatus').value, date = $('apptDate').value, deptId = $('apptDept').value;
    const qs = new URLSearchParams({ page, size: 10 });
    if (status) qs.set('status', status);
    if (date) qs.set('date', date);
    if (deptId) qs.set('deptId', deptId);
    const d = await GET('/api/v1/admin/appointments?' + qs);
    const records = d.records || [];
    box.innerHTML = records.length ? `<table class="tbl">
      <thead><tr><th>ID</th><th>患者</th><th>医生</th><th>科室</th><th>日期</th><th>时段</th><th>排队号</th><th>状态</th><th>操作</th></tr></thead><tbody>` +
      records.map(a => `<tr>
        <td>${esc(a.id)}</td><td>${esc(a.patientName || a.patientId || '-')}</td>
        <td>${esc(a.doctorName || a.doctorId || '-')}</td><td>${esc(a.deptName || '-')}</td>
        <td>${esc(a.appointDate)}</td><td>${esc(a.timePeriod)}</td>
        <td>${esc(a.queueNum ?? '-')}</td><td>${tag(APPT_STATUS, a.status)}</td>
        <td>${a.status === 0 ? `<button class="btn sm danger-ghost" onclick="cancelAppointment(${a.id})">取消</button>` : '-'}</td>
      </tr>`).join('') + '</tbody></table>' + pagerHtml(page, d.totalPages, 'loadAppointments')
      : emptyBox('暂无预约记录');
  } catch (e) { box.innerHTML = emptyBox(e.message); }
}

function cancelAppointment(id) {
  openModal('取消预约', `<p>确定取消预约 <b>#${id}</b> 吗？该操作会释放号源并通知数据统计。</p>`,
    `<button class="btn ghost" onclick="closeModal()">取消</button>
     <button class="btn danger" onclick="doCancelAppointment(${id})">确认取消</button>`);
}
async function doCancelAppointment(id) {
  try {
    await POST(`/api/v1/admin/appointments/${id}/cancel`);
    closeModal(); toast('预约已取消', 'ok');
    loadAppointments(currentApptPage());
  } catch (e) { toast(e.message, 'err'); }
}
function currentApptPage() { return parseInt(($('apptTable').querySelector('.pager span')?.textContent || '1/1').split('/')[0]) || 1; }

/* ==================== 缴费订单 ==================== */
async function loadOrders(page) {
  const box = $('orderTable');
  box.innerHTML = '<div class="empty">加载中...</div>';
  try {
    const status = $('orderStatus').value, orderNo = $('orderNo').value.trim();
    const qs = new URLSearchParams({ page, size: 10 });
    if (status !== '') qs.set('status', status);
    if (orderNo) qs.set('orderNo', orderNo);
    const d = await GET('/api/v1/admin/pay-orders?' + qs);
    const records = d.records || [];
    box.innerHTML = records.length ? `<table class="tbl">
      <thead><tr><th>订单号</th><th>患者ID</th><th>医生ID</th><th>金额(元)</th><th>支付方式</th><th>支付时间</th><th>状态</th><th>操作</th></tr></thead><tbody>` +
      records.map(o => `<tr>
        <td class="mono">${esc(o.orderNo)}</td><td>${esc(o.patientId ?? '-')}</td><td>${esc(o.doctorId ?? '-')}</td>
        <td><b>¥${money(o.totalAmount)}</b></td><td>${methodName(o.payMethod)}</td>
        <td>${fmtTime(o.payTime)}</td><td>${tag(PAY_STATUS, o.status)}</td>
        <td><button class="btn sm ghost" onclick="openOrderDetail('${esc(o.orderNo)}')">明细</button></td>
      </tr>`).join('') + '</tbody></table>' + pagerHtml(page, d.totalPages, 'loadOrders')
      : emptyBox('暂无订单');
  } catch (e) { box.innerHTML = emptyBox(e.message); }
}

async function openOrderDetail(orderNo) {
  try {
    const d = await GET(`/api/v1/admin/pay-orders/${encodeURIComponent(orderNo)}/detail`);
    const order = d.order || d;
    const items = d.items || order.items || [];
    const rows = items.map(it => `<tr>
      <td>${esc(it.category)}</td><td>${esc(it.itemName)}</td>
      <td>¥${money(it.unitPrice)}</td><td>×${esc(it.quantity)}</td>
      <td style="text-align:right">¥${money(it.subtotal)}</td></tr>`).join('');
    openModal('订单明细', `
      <p class="mono">订单号：${esc(order.orderNo || orderNo)}</p>
      ${order.transactionNo ? `<p class="mono">流水号：${esc(order.transactionNo)}</p>` : ''}
      <p style="color:var(--text-muted);font-size:13px">状态：${tag(PAY_STATUS, order.status)}</p>
      ${rows ? `<table class="tbl" style="margin-top:10px"><thead><tr><th>类别</th><th>项目</th><th>单价</th><th>数量</th><th style="text-align:right">小计</th></tr></thead><tbody>${rows}</tbody></table>` : ''}
      <p style="text-align:right;margin-top:12px">合计：<b style="color:var(--primary);font-size:20px">¥${money(order.totalAmount)}</b></p>`,
      `<button class="btn" onclick="closeModal()">关闭</button>`);
  } catch (e) { toast(e.message, 'err'); }
}

/* ==================== 退款审核 ==================== */
async function loadRefunds(page) {
  const box = $('refundTable');
  box.innerHTML = '<div class="empty">加载中...</div>';
  try {
    const status = $('refundStatus').value;
    const qs = new URLSearchParams({ page, size: 10 });
    if (status !== '') qs.set('status', status);
    const d = await GET('/api/v1/admin/refunds?' + qs);
    const records = d.records || [];
    // 侧边栏待审核角标
    const pending = records.filter(r => r.status === 0).length;
    $('refundDot').style.display = (status === '0' && pending > 0) ? 'block' : 'none';
    box.innerHTML = records.length ? `<table class="tbl">
      <thead><tr><th>退款单号</th><th>原订单</th><th>患者ID</th><th>原金额</th><th>退款金额</th><th>方式</th><th>原因</th><th>状态</th><th>操作</th></tr></thead><tbody>` +
      records.map(r => `<tr>
        <td class="mono">${esc(r.refundNo)}</td><td class="mono">${esc(r.orderNo)}</td>
        <td>${esc(r.patientId ?? '-')}</td><td>¥${money(r.originalAmount)}</td>
        <td><b>¥${money(r.refundAmount)}</b></td><td>${methodName(r.refundMethod)}</td>
        <td><span class="clip" title="${esc(r.refundReason)}">${esc(r.refundReason || '-')}</span></td>
        <td>${tag(REFUND_STATUS, r.status)}</td>
        <td>${r.status === 0 ? `
          <button class="btn sm success" onclick="auditRefund(${r.id}, 1)">通过</button>
          <button class="btn sm danger-ghost" onclick="auditRefund(${r.id}, 2)">拒绝</button>` : '-'}</td>
      </tr>`).join('') + '</tbody></table>' + pagerHtml(page, d.totalPages, 'loadRefunds')
      : emptyBox('暂无退款单');
  } catch (e) { box.innerHTML = emptyBox(e.message); }
}

function auditRefund(id, result) {
  openModal(result === 1 ? '通过退款' : '拒绝退款', `
    <p>退款单 <b>#${id}</b>，${result === 1 ? '通过后退款金额将原路退回。' : '拒绝后原订单恢复为已缴费。'}</p>
    <div class="field" style="margin-top:12px"><label>审核备注</label>
      <textarea class="input" id="auditRemark" rows="2" placeholder="选填"></textarea></div>`,
    `<button class="btn ghost" onclick="closeModal()">取消</button>
     <button class="btn ${result === 1 ? 'success' : 'danger'}" onclick="doAudit(${id}, ${result})">确认${result === 1 ? '通过' : '拒绝'}</button>`);
}
async function doAudit(id, result) {
  try {
    await POST(`/api/v1/admin/refunds/${id}/audit?auditResult=${result}&auditRemark=${encodeURIComponent($('auditRemark').value.trim())}`);
    closeModal(); toast('审核完成', 'ok'); loadRefunds(currentRefundPage());
  } catch (e) { toast(e.message, 'err'); }
}
function currentRefundPage() { return parseInt(($('refundTable').querySelector('.pager span')?.textContent || '1/1').split('/')[0]) || 1; }

/* ==================== 科室管理 ==================== */
async function loadDepartments() {
  const box = $('deptTable');
  box.innerHTML = '<div class="empty">加载中...</div>';
  try {
    const list = await GET('/api/v1/admin/departments');
    deptOptionsLoaded = false;
    box.innerHTML = `<table class="tbl">
      <thead><tr><th>ID</th><th>科室名称</th><th>简介</th><th>状态</th><th>操作</th></tr></thead><tbody>` +
      list.map(d => `<tr>
        <td>${d.id}</td><td><b>${esc(d.name)}</b></td>
        <td style="color:var(--text-muted)"><span class="clip" style="max-width:340px">${esc(d.description || '-')}</span></td>
        <td>${d.status === 1 ? '<span class="tag green">启用</span>' : '<span class="tag gray">停用</span>'}</td>
        <td>
          <button class="btn sm ghost" onclick="openDeptModal(${d.id}, '${esc(d.name)}', '${esc(d.description || '')}')">编辑</button>
          <button class="btn sm ${d.status === 1 ? 'danger-ghost' : 'ghost'}" onclick="toggleDept(${d.id}, ${d.status === 1 ? 0 : 1})">${d.status === 1 ? '停用' : '启用'}</button>
        </td></tr>`).join('') + '</tbody></table>';
  } catch (e) { box.innerHTML = emptyBox(e.message); }
}

function openDeptModal(id, name, desc) {
  openModal(id ? '编辑科室' : '新增科室', `
    <div class="field"><label>科室名称</label><input class="input" id="deptName" value="${esc(name || '')}" placeholder="如：心血管内科"></div>
    <div class="field"><label>科室简介</label><textarea class="input" id="deptDesc" rows="3" placeholder="选填">${esc(desc || '')}</textarea></div>`,
    `<button class="btn ghost" onclick="closeModal()">取消</button>
     <button class="btn" onclick="saveDept(${id || 'null'})">保存</button>`);
}
async function saveDept(id) {
  const name = $('deptName').value.trim();
  const description = $('deptDesc').value.trim();
  if (!name) return toast('请填写科室名称', 'err');
  try {
    if (id) await PUT(`/api/v1/admin/departments/${id}`, { name, description });
    else await POST('/api/v1/admin/departments', { name, description });
    closeModal(); toast('保存成功', 'ok'); loadDepartments();
  } catch (e) { toast(e.message, 'err'); }
}
async function toggleDept(id, status) {
  try { await PUT(`/api/v1/admin/departments/${id}/status?status=${status}`); toast('已更新', 'ok'); loadDepartments(); }
  catch (e) { toast(e.message, 'err'); }
}

/* ==================== 医生管理 ==================== */
async function loadDoctors(page) {
  const box = $('docTable');
  box.innerHTML = '<div class="empty">加载中...</div>';
  try {
    const deptId = $('docDept').value, keyword = $('docKeyword').value.trim();
    const qs = new URLSearchParams({ page, size: 10 });
    if (deptId) qs.set('deptId', deptId);
    if (keyword) qs.set('keyword', keyword);
    const d = await GET('/api/v1/admin/doctors?' + qs);
    const records = d.records || [];
    box.innerHTML = records.length ? `<table class="tbl">
      <thead><tr><th>ID</th><th>姓名</th><th>工号</th><th>手机号</th><th>科室</th><th>职称</th><th>状态</th><th>操作</th></tr></thead><tbody>` +
      records.map(doc => `<tr>
        <td>${doc.id}</td><td><b>${esc(doc.name)}</b></td><td class="mono">${esc(doc.username)}</td>
        <td>${esc(doc.phone || '-')}</td><td>${esc(doc.departmentId ?? '-')}</td><td>${esc(doc.title || '-')}</td>
        <td>${doc.status === 1 ? '<span class="tag green">正常</span>' : '<span class="tag red">停诊</span>'}</td>
        <td><button class="btn sm ${doc.status === 1 ? 'danger-ghost' : 'ghost'}" onclick="toggleDoctor(${doc.id}, ${doc.status === 1 ? 0 : 1})">${doc.status === 1 ? '停诊' : '恢复'}</button></td>
      </tr>`).join('') + '</tbody></table>' + pagerHtml(page, d.totalPages, 'loadDoctors')
      : emptyBox('暂无医生');
  } catch (e) { box.innerHTML = emptyBox(e.message); }
}
async function toggleDoctor(id, status) {
  try { await PUT(`/api/v1/admin/doctors/${id}/status?status=${status}`); toast('已更新', 'ok'); loadDoctors(currentDocPage()); }
  catch (e) { toast(e.message, 'err'); }
}
function currentDocPage() { return parseInt(($('docTable').querySelector('.pager span')?.textContent || '1/1').split('/')[0]) || 1; }

/* ==================== 患者管理 ==================== */
async function loadPatients(page) {
  const box = $('patTable');
  box.innerHTML = '<div class="empty">加载中...</div>';
  try {
    const keyword = $('patKeyword').value.trim();
    const qs = new URLSearchParams({ page, size: 10 });
    if (keyword) qs.set('keyword', keyword);
    const d = await GET('/api/v1/admin/patients?' + qs);
    const records = d.records || [];
    box.innerHTML = records.length ? `<table class="tbl">
      <thead><tr><th>ID</th><th>姓名</th><th>手机号</th><th>性别</th><th>年龄</th><th>注册时间</th><th>状态</th><th>操作</th></tr></thead><tbody>` +
      records.map(p => `<tr>
        <td>${p.id}</td><td><b>${esc(p.name || '-')}</b></td><td>${esc(p.username)}</td>
        <td>${esc(p.gender || '-')}</td><td>${esc(p.age ?? '-')}</td><td>${fmtTime(p.createTime)}</td>
        <td>${p.status === 1 ? '<span class="tag green">正常</span>' : '<span class="tag red">禁用</span>'}</td>
        <td><button class="btn sm ${p.status === 1 ? 'danger-ghost' : 'ghost'}" onclick="togglePatient(${p.id}, ${p.status === 1 ? 0 : 1})">${p.status === 1 ? '禁用' : '启用'}</button></td>
      </tr>`).join('') + '</tbody></table>' + pagerHtml(page, d.totalPages, 'loadPatients')
      : emptyBox('暂无患者');
  } catch (e) { box.innerHTML = emptyBox(e.message); }
}
async function togglePatient(id, status) {
  try { await PUT(`/api/v1/admin/patients/${id}/status?status=${status}`); toast('已更新', 'ok'); loadPatients(currentPatPage()); }
  catch (e) { toast(e.message, 'err'); }
}
function currentPatPage() { return parseInt(($('patTable').querySelector('.pager span')?.textContent || '1/1').split('/')[0]) || 1; }

/* ==================== AI 对话记录 ==================== */
async function loadAiChats(page) {
  const box = $('aiTable');
  box.innerHTML = '<div class="empty">加载中...</div>';
  try {
    const sessionId = $('aiSession').value.trim(), role = $('aiRole').value;
    const qs = new URLSearchParams({ page, size: 12 });
    if (sessionId) qs.set('sessionId', sessionId);
    if (role) qs.set('role', role);
    const d = await GET('/api/v1/admin/ai/conversations?' + qs);
    const records = d.records || [];
    box.innerHTML = records.length ? `<table class="tbl">
      <thead><tr><th>ID</th><th>会话</th><th>用户</th><th>角色</th><th>内容</th><th>工具</th><th>Tokens</th><th>耗时</th><th>时间</th></tr></thead><tbody>` +
      records.map(m => `<tr>
        <td>${m.id}</td>
        <td class="mono"><span class="clip" style="max-width:130px">${esc(m.sessionId || '-')}</span></td>
        <td>${m.userType === 1 ? '患者' : m.userType === 2 ? '医生' : '-'}#${m.userId}</td>
        <td>${m.role === 'user' ? '<span class="tag blue">提问</span>' : '<span class="tag green">回答</span>'}</td>
        <td><span class="clip" style="max-width:300px" title="${esc(m.content)}">${esc(m.content)}</span></td>
        <td style="color:var(--teal,#14808A)">${esc(m.toolsUsed || '-')}</td>
        <td>${m.totalTokens ? esc(m.totalTokens) : '-'}</td>
        <td>${m.latency ? m.latency + 'ms' : '-'}</td>
        <td>${fmtTime(m.createTime)}</td>
      </tr>`).join('') + '</tbody></table>' + pagerHtml(page, d.totalPages, 'loadAiChats')
      : emptyBox('暂无 AI 对话记录（患者/医生在门户中使用 AI 助手后将在此留痕）');
  } catch (e) { box.innerHTML = emptyBox(e.message); }
}

/* ==================== 操作日志 ==================== */
async function loadLogs(page) {
  const box = $('logTable');
  box.innerHTML = '<div class="empty">加载中...</div>';
  try {
    const username = $('logUser').value.trim();
    const qs = new URLSearchParams({ page, size: 12 });
    if (username) qs.set('username', username);
    const d = await GET('/api/v1/admin/logs?' + qs);
    const records = d.records || [];
    box.innerHTML = records.length ? `<table class="tbl">
      <thead><tr><th>ID</th><th>操作人</th><th>模块</th><th>描述</th><th>类型</th><th>IP</th><th>URI</th><th>耗时</th><th>结果</th><th>时间</th></tr></thead><tbody>` +
      records.map(l => `<tr>
        <td>${l.id}</td><td>${esc(l.username || l.userId || '-')}</td>
        <td><span class="tag blue">${esc(l.module || '-')}</span></td>
        <td>${esc(l.description || '-')}</td><td>${esc(l.operationType || '-')}</td>
        <td class="mono">${esc(l.ip || '-')}</td>
        <td class="mono"><span class="clip" style="max-width:170px">${esc(l.uri || '-')}</span></td>
        <td>${l.costTime != null ? l.costTime + 'ms' : '-'}</td>
        <td>${l.status === 0 ? '<span class="tag green">成功</span>' : `<span class="tag red" title="${esc(l.errorMsg || '')}">失败</span>`}</td>
        <td>${fmtTime(l.createTime)}</td>
      </tr>`).join('') + '</tbody></table>' + pagerHtml(page, d.totalPages, 'loadLogs')
      : emptyBox('暂无操作日志（管理端每次写操作都会在此留痕）');
  } catch (e) { box.innerHTML = emptyBox(e.message); }
}

/* ==================== 启动 ==================== */
(function boot() {
  if (store.token && store.name) {
    $('meName').textContent = store.name;
    $('view-app').style.display = 'block';
    switchPage('dashboard');
  } else {
    $('view-login').style.display = 'flex';
  }
})();
