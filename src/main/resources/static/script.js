/* ============================================================
   SecureChat — Frontend Logic v3
   JWT auth · Email OTP · DM · Group Chat · Image Sharing
   ============================================================ */

const BASE_URL = window.location.origin;

// ── Shared helpers ────────────────────────────────────────────
function getToken() { return localStorage.getItem('sc_token'); }
function getUser()  { return localStorage.getItem('sc_user');  }

function authHeaders() {
  return { 'Content-Type': 'application/json', 'Authorization': `Bearer ${getToken()}` };
}

function initials(name) {
  if (!name) return '?';
  const parts = name.trim().split(/\s+/);
  return parts.length > 1
    ? (parts[0][0] + parts[1][0]).toUpperCase()
    : name.slice(0, 2).toUpperCase();
}

function escapeHtml(s) {
  return String(s)
    .replace(/&/g,'&amp;').replace(/</g,'&lt;')
    .replace(/>/g,'&gt;').replace(/"/g,'&quot;');
}

function formatTime(iso) {
  if (!iso) return '';
  return new Date(iso).toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' });
}

function formatDate(iso) {
  if (!iso) return '';
  const d = new Date(iso), today = new Date();
  if (d.toDateString() === today.toDateString()) return 'Today';
  const yest = new Date(today); yest.setDate(today.getDate() - 1);
  if (d.toDateString() === yest.toDateString()) return 'Yesterday';
  return d.toLocaleDateString([], { month: 'short', day: 'numeric' });
}

function formatRelative(iso) {
  if (!iso) return '';
  const d = new Date(iso), now = new Date();
  const diff = now - d;
  if (diff < 60000)   return 'now';
  if (diff < 3600000) return Math.floor(diff/60000) + 'm';
  if (d.toDateString() === now.toDateString()) return formatTime(iso);
  return d.toLocaleDateString([], { month: 'short', day: 'numeric' });
}

// ── Page detection ────────────────────────────────────────────
const path    = window.location.pathname;
const isIndex = path.endsWith('index.html') || path === '/' || path === '';
const isChat  = path.endsWith('chat.html');

// ============================================================
//  AUTH PAGE
// ============================================================
if (isIndex) {
  if (getToken()) { window.location.href = 'chat.html'; }

  // ── State ──────────────────────────────────────────────────
  let pendingEmail = ''; // email waiting for OTP verification

  // ── DOM refs ───────────────────────────────────────────────
  const tabLogin    = document.getElementById('tabLogin');
  const tabRegister = document.getElementById('tabRegister');
  const panelLogin  = document.getElementById('panelLogin');
  const panelReg    = document.getElementById('panelRegister');
  const panelOtp    = document.getElementById('panelOtp');

  // ── Tab switching ──────────────────────────────────────────
  tabLogin.addEventListener('click',    () => switchTab('login'));
  tabRegister.addEventListener('click', () => switchTab('register'));

  function switchTab(tab) {
    const isL = tab === 'login';
    tabLogin.classList.toggle('active', isL);
    tabRegister.classList.toggle('active', !isL);
    panelLogin.classList.toggle('active', isL);
    panelReg.classList.toggle('active', !isL);
    panelOtp.classList.remove('active');
    clearAlerts();
  }

  function showOtpPanel(email) {
    pendingEmail = email;
    tabLogin.classList.remove('active');
    tabRegister.classList.remove('active');
    panelLogin.classList.remove('active');
    panelReg.classList.remove('active');
    panelOtp.classList.add('active');
    document.getElementById('otpSubtitle').textContent =
      `We sent a 6-digit code to ${email}. It expires in 5 minutes.`;
    document.getElementById('otpCode').value = '';
    document.getElementById('otpCode').focus();
    clearAlerts();
  }

  // ── Password visibility toggles ────────────────────────────
  document.querySelectorAll('.toggle-pw').forEach(btn => {
    btn.addEventListener('click', () => {
      const inp = document.getElementById(btn.dataset.target);
      inp.type = inp.type === 'password' ? 'text' : 'password';
    });
  });

  // ── LOGIN ──────────────────────────────────────────────────
  document.getElementById('loginForm').addEventListener('submit', async e => {
    e.preventDefault(); clearAlerts();
    const username = document.getElementById('loginUsername').value.trim();
    const password = document.getElementById('loginPassword').value;
    let ok = true;
    if (!username) { showFieldErr('loginUsernameErr', 'Username is required'); markInvalid('loginUsername'); ok = false; }
    if (!password) { showFieldErr('loginPasswordErr', 'Password is required'); markInvalid('loginPassword'); ok = false; }
    if (!ok) return;
    setLoading('loginBtn', true);
    try {
      const res  = await fetch(`${BASE_URL}/auth/login`, {
        method: 'POST', headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ username, password })
      });
      const data = await res.json();
      if (res.ok) {
        localStorage.setItem('sc_token', data.token);
        localStorage.setItem('sc_user',  data.username);
        showAlert('loginAlert', 'Login successful! Redirecting…', 'success');
        setTimeout(() => window.location.href = 'chat.html', 800);
      } else {
        showAlert('loginAlert', data.message || 'Invalid credentials', 'error');
      }
    } catch { showAlert('loginAlert', 'Cannot reach server. Is it running?', 'error'); }
    finally  { setLoading('loginBtn', false); }
  });

  // ── REGISTER ───────────────────────────────────────────────
  document.getElementById('registerForm').addEventListener('submit', async e => {
    e.preventDefault(); clearAlerts();
    const username = document.getElementById('regUsername').value.trim();
    const email    = document.getElementById('regEmail').value.trim();
    const password = document.getElementById('regPassword').value;
    const confirm  = document.getElementById('regConfirm').value;
    let ok = true;

    if (!username || username.length < 3) {
      showFieldErr('regUsernameErr', 'Min 3 characters'); markInvalid('regUsername'); ok = false;
    } else if (!/^[a-zA-Z0-9_]+$/.test(username)) {
      showFieldErr('regUsernameErr', 'Letters, numbers and _ only'); markInvalid('regUsername'); ok = false;
    }
    if (!email || !/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(email)) {
      showFieldErr('regEmailErr', 'Enter a valid email address'); markInvalid('regEmail'); ok = false;
    }
    if (!password || password.length < 8) {
      showFieldErr('regPasswordErr', 'Min 8 characters'); markInvalid('regPassword'); ok = false;
    }
    if (password !== confirm) {
      showFieldErr('regConfirmErr', 'Passwords do not match'); markInvalid('regConfirm'); ok = false;
    }
    if (!ok) return;

    setLoading('registerBtn', true);
    try {
      const res  = await fetch(`${BASE_URL}/auth/register`, {
        method: 'POST', headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ username, email, password })
      });
      const data = await res.json();
      if (res.ok) {
        showOtpPanel(data.email || email);
      } else {
        const msg = data.message || (data.details ? Object.values(data.details).join(', ') : 'Registration failed');
        showAlert('registerAlert', msg, 'error');
      }
    } catch { showAlert('registerAlert', 'Cannot reach server. Is it running?', 'error'); }
    finally  { setLoading('registerBtn', false); }
  });

  // ── OTP VERIFY ─────────────────────────────────────────────
  document.getElementById('otpForm').addEventListener('submit', async e => {
    e.preventDefault(); clearAlerts();
    const otp = document.getElementById('otpCode').value.trim();
    if (!otp || otp.length !== 6 || !/^\d{6}$/.test(otp)) {
      showFieldErr('otpCodeErr', 'Enter the 6-digit code from your email');
      markInvalid('otpCode'); return;
    }
    setLoading('otpBtn', true);
    try {
      const res  = await fetch(`${BASE_URL}/auth/verify-otp`, {
        method: 'POST', headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ email: pendingEmail, otp })
      });
      const data = await res.json();
      if (res.ok) {
        localStorage.setItem('sc_token', data.token);
        localStorage.setItem('sc_user',  data.username);
        showAlert('otpAlert', 'Email verified! Signing you in…', 'success');
        setTimeout(() => window.location.href = 'chat.html', 900);
      } else {
        showAlert('otpAlert', data.message || 'Verification failed', 'error');
      }
    } catch { showAlert('otpAlert', 'Cannot reach server. Is it running?', 'error'); }
    finally  { setLoading('otpBtn', false); }
  });

  // ── RESEND OTP ─────────────────────────────────────────────
  document.getElementById('resendOtpBtn').addEventListener('click', async () => {
    if (!pendingEmail) return;
    const btn = document.getElementById('resendOtpBtn');
    btn.disabled = true;
    btn.textContent = 'Sending…';
    try {
      const res  = await fetch(`${BASE_URL}/auth/resend-otp`, {
        method: 'POST', headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ email: pendingEmail })
      });
      const data = await res.json();
      if (res.ok) {
        showAlert('otpAlert', 'New code sent! Check your inbox.', 'success');
      } else {
        showAlert('otpAlert', data.message || 'Could not resend code', 'error');
      }
    } catch { showAlert('otpAlert', 'Network error', 'error'); }
    finally {
      // Re-enable after 30 s to prevent spam
      setTimeout(() => { btn.disabled = false; btn.textContent = 'Resend code'; }, 30000);
    }
  });

  // ── OTP digit-only filter ──────────────────────────────────
  document.getElementById('otpCode').addEventListener('input', function() {
    this.value = this.value.replace(/\D/g, '').slice(0, 6);
    this.classList.remove('invalid');
    document.getElementById('otpCodeErr').textContent = '';
  });

  // ── Clear field errors on input ────────────────────────────
  document.querySelectorAll('input').forEach(inp => {
    inp.addEventListener('input', () => {
      inp.classList.remove('invalid');
      const e = document.getElementById(inp.id + 'Err');
      if (e) e.textContent = '';
    });
  });

  // ── Auth helpers ───────────────────────────────────────────
  function showFieldErr(id, msg) { const e = document.getElementById(id); if (e) e.textContent = msg; }
  function markInvalid(id)       { const e = document.getElementById(id); if (e) e.classList.add('invalid'); }
  function showAlert(id, msg, type) {
    const e = document.getElementById(id);
    if (!e) return;
    e.textContent = msg;
    e.className = `alert show ${type}`;
  }
  function clearAlerts() {
    document.querySelectorAll('.alert').forEach(e => { e.className = 'alert'; e.textContent = ''; });
    document.querySelectorAll('.field-error').forEach(e => e.textContent = '');
    document.querySelectorAll('input').forEach(e => e.classList.remove('invalid'));
  }
  function setLoading(btnId, loading) {
    const btn = document.getElementById(btnId);
    if (!btn) return;
    btn.disabled = loading;
    const t = btn.querySelector('.btn-text');
    const s = btn.querySelector('.btn-spinner');
    if (t) t.style.display = loading ? 'none' : '';
    if (s) s.classList.toggle('sc-hidden', !loading);
  }
}

// ============================================================
//  CHAT PAGE
// ============================================================
if (isChat) {
  if (!getToken()) { window.location.href = 'index.html'; }

  const ME = getUser();
  const ACTIVE_DM_KEY = `sc_active_dm_${ME}`;
  const KNOWN_USERS_KEY = 'knownUsers';
  const conversations = {};

  // ── DOM refs ───────────────────────────────────────────────
  const loggedInUserEl = document.getElementById('loggedInUser');
  const avatarEl       = document.getElementById('avatarEl');
  const searchInput    = document.getElementById('searchInput');
  const dmListEl       = document.getElementById('dmList');
  const groupListEl    = document.getElementById('groupList');
  const emptyState     = document.getElementById('emptyState');
  const activeChatEl   = document.getElementById('activeChat');
  const chatAvatarEl   = document.getElementById('chatAvatarEl');
  const chatHeaderName = document.getElementById('chatHeaderName');
  const chatHeaderSub  = document.getElementById('chatHeaderSub');
  const chatHeaderActs = document.getElementById('chatHeaderActions');
  const messagesArea   = document.getElementById('messagesArea');
  const messageInput   = document.getElementById('messageInput');
  const sendBtn        = document.getElementById('sendBtn');
  const imageFileInput = document.getElementById('imageFileInput');

  // ── State ──────────────────────────────────────────────────
  let activeType     = null;   // 'dm' | 'group'
  let activeTarget   = null;   // username (dm) or groupId (group)
  let activeGroupObj = null;
  let pollingTimer   = null;
  let lastMsgCount   = 0;
  let allDmPreviews  = [];
  let allGroups      = [];
  let pendingImageFile = null;

  // ── Init ───────────────────────────────────────────────────
  loggedInUserEl.textContent = ME;
  avatarEl.textContent       = initials(ME);
  allDmPreviews = mergeKnownUsers([]);
  renderDmList(allDmPreviews);
  loadSidebar().then(restoreActiveDm);

  // ── Sidebar bootstrap ──────────────────────────────────────
  async function loadSidebar() {
    await Promise.all([loadDmPreviews(), loadGroups()]);
  }

  async function loadDmPreviews() {
    try {
      const res = await fetch(`${BASE_URL}/messages/previews`, { headers: authHeaders() });
      if (res.status === 401) { handleUnauthorized(); return; }
      if (!res.ok) return;
      allDmPreviews = mergeKnownUsers(normalizeDmPreviews(await res.json()));
      renderDmList(allDmPreviews);
      if (activeType === 'dm' && activeTarget) {
        chatHeaderName.textContent = activeTarget;
      }
    } catch { /* silent */ }
  }

  async function loadGroups() {
    try {
      const res = await fetch(`${BASE_URL}/groups`, { headers: authHeaders() });
      if (res.status === 401) { handleUnauthorized(); return; }
      if (!res.ok) return;
      allGroups = await res.json();
      renderGroupList(allGroups);
    } catch { /* silent */ }
  }

  // ── Sidebar rendering ──────────────────────────────────────
  function normalizeDmPreviews(previews) {
    return (previews || []).filter(p => p && p.name && p.name !== ME);
  }

  function loadKnownUsers() {
    return JSON.parse(localStorage.getItem(KNOWN_USERS_KEY) || '[]')
      .filter(name => name && name !== ME);
  }

  function saveKnownUsers(users) {
    localStorage.setItem(KNOWN_USERS_KEY, JSON.stringify([...new Set(users.filter(name => name && name !== ME))]));
  }

  function rememberKnownUser(username) {
    saveKnownUsers([...loadKnownUsers(), username]);
  }

  function mergeKnownUsers(previews) {
    const byName = new Map();
    normalizeDmPreviews(previews).forEach(p => byName.set(p.name, p));
    loadKnownUsers().forEach(name => {
      if (!byName.has(name)) {
        byName.set(name, { name, lastMessage: '', lastMessageTime: null });
      }
    });
    saveKnownUsers([...byName.keys()]);
    return [...byName.values()];
  }

  function withActiveDmPreview(previews) {
    const items = [...(previews || [])];
    if (activeType === 'dm' && activeTarget && !items.some(p => p.name === activeTarget)) {
      items.unshift({ name: activeTarget, lastMessage: '', lastMessageTime: null });
    }
    return items;
  }

  function renderDmList(previews) {
    const items = withActiveDmPreview(previews);
    if (items.length === 0) {
      dmListEl.innerHTML = '<p class="sidebar-hint">No conversations yet</p>';
      return;
    }
    dmListEl.innerHTML = '';
    items.forEach(p => {
      dmListEl.appendChild(makeSidebarItem({
        avatarText: initials(p.name),
        avatarClass: '',
        name: p.name,
        preview: p.lastMessage || '',
        time: formatRelative(p.lastMessageTime),
        active: activeType === 'dm' && activeTarget === p.name,
        onClick: () => openDm(p.name)
      }));
    });
  }

  function renderGroupList(groups) {
    if (!groups || groups.length === 0) {
      groupListEl.innerHTML = '<p class="sidebar-hint">No groups yet</p>';
      return;
    }
    groupListEl.innerHTML = '';
    groups.forEach(g => {
      groupListEl.appendChild(makeSidebarItem({
        avatarText: initials(g.name),
        avatarClass: 'group-avatar',
        name: g.name,
        preview: g.lastMessage || '',
        time: formatRelative(g.lastMessageTime),
        active: activeType === 'group' && activeTarget === g.id,
        onClick: () => openGroup(g)
      }));
    });
  }

  function makeSidebarItem({ avatarText, avatarClass, name, preview, time, active, onClick }) {
    const item = document.createElement('div');
    item.className = `conv-item${active ? ' active' : ''}`;
    item.innerHTML = `
      <div class="conv-avatar ${avatarClass}">${escapeHtml(avatarText)}</div>
      <div class="conv-info">
        <div class="conv-name">${escapeHtml(name)}</div>
        ${preview ? `<div class="conv-preview">${escapeHtml(preview)}</div>` : ''}
      </div>
      ${time ? `<span class="conv-time">${escapeHtml(time)}</span>` : ''}
    `;
    item.addEventListener('click', onClick);
    return item;
  }

  // ── Search filter ──────────────────────────────────────────
  searchInput.addEventListener('input', () => {
    const q = searchInput.value.trim().toLowerCase();
    if (!q) { renderDmList(allDmPreviews); renderGroupList(allGroups); return; }
    renderDmList(allDmPreviews.filter(p => p.name.toLowerCase().includes(q)));
    renderGroupList(allGroups.filter(g => g.name.toLowerCase().includes(q)));
  });

  // ── Open DM ────────────────────────────────────────────────
  async function openDm(username) {
    if (username === ME) { showToast('You cannot chat with yourself', 'error'); return; }
    stopPolling();
    activeType     = 'dm';
    activeTarget   = username;
    activeGroupObj = null;
    localStorage.setItem(ACTIVE_DM_KEY, username);
    rememberKnownUser(username);
    allDmPreviews = mergeKnownUsers(allDmPreviews);

    chatAvatarEl.className   = 'chat-header-avatar';
    chatAvatarEl.textContent = initials(username);
    chatHeaderName.textContent = username;
    chatHeaderSub.textContent  = 'Secure channel - AES-128 CBC';
    chatHeaderActs.innerHTML = `<span class="enc-badge">&#128274; DM</span>`;

    showActiveChat();
    renderDmList(allDmPreviews);
    renderGroupList(allGroups);

    if (conversations[username]) {
      lastMsgCount = conversations[username].length;
      renderMessages(conversations[username], false);
    } else {
      messagesArea.innerHTML = '<div class="msg-status">Loading…</div>';
      await fetchAndRenderDm(username);
    }
    pollingTimer = setInterval(() => fetchAndRenderDm(username, true), 3000);
    messageInput.focus();
  }

  async function restoreActiveDm() {
    const username = localStorage.getItem(ACTIVE_DM_KEY);
    if (username && username !== ME) {
      await openDm(username);
    }
  }

  // ── Open Group ─────────────────────────────────────────────
  async function openGroup(group) {
    stopPolling();
    activeType     = 'group';
    activeTarget   = group.id;
    activeGroupObj = group;

    updateGroupHeader(group);
    showActiveChat();
    renderDmList(allDmPreviews);
    renderGroupList(allGroups);

    messagesArea.innerHTML = '<div class="msg-status">Loading…</div>';
    await fetchAndRenderGroup();
    pollingTimer = setInterval(() => fetchAndRenderGroup(true), 3000);
    messageInput.focus();
  }

  function updateGroupHeader(group) {
    chatAvatarEl.className   = 'chat-header-avatar group-avatar';
    chatAvatarEl.textContent = initials(group.name);
    chatHeaderName.textContent = group.name;
    const memberCount = group.members ? group.members.length : '?';
    chatHeaderSub.textContent = `${memberCount} members · admin: ${group.admin}`;

    chatHeaderActs.innerHTML = `<span class="enc-badge">&#128274; Group</span>`;
    if (group.admin === ME) {
      const addBtn = document.createElement('button');
      addBtn.className = 'btn-header-action';
      addBtn.innerHTML = `<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><path d="M16 21v-2a4 4 0 0 0-4-4H6a4 4 0 0 0-4 4v2"/><circle cx="9" cy="7" r="4"/><line x1="19" y1="8" x2="19" y2="14"/><line x1="22" y1="11" x2="16" y2="11"/></svg> Add Member`;
      addBtn.addEventListener('click', () => openAddMemberModal(group.id));
      chatHeaderActs.prepend(addBtn);
    } else {
      const leaveBtn = document.createElement('button');
      leaveBtn.className = 'btn-header-action';
      leaveBtn.innerHTML = `<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><path d="M9 21H5a2 2 0 0 1-2-2V5a2 2 0 0 1 2-2h4"/><polyline points="16 17 21 12 16 7"/><line x1="21" y1="12" x2="9" y2="12"/></svg> Leave`;
      leaveBtn.addEventListener('click', () => leaveGroup(group.id));
      chatHeaderActs.prepend(leaveBtn);
    }
  }

  // ── Fetch & render: DM (text + images merged) ──────────────
  async function fetchAndRenderDm(userOrSilent = activeTarget, silent = false) {
    let user = userOrSilent || activeTarget;
    if (typeof userOrSilent === 'boolean') {
      silent = userOrSilent;
      user = activeTarget;
    }
    if (!user) return;

    try {
      const [textRes, imgRes] = await Promise.all([
        fetch(`${BASE_URL}/messages/chat?user2=${encodeURIComponent(user)}`, { headers: authHeaders() }),
        fetch(`${BASE_URL}/images/dm?user2=${encodeURIComponent(user)}`,     { headers: authHeaders() })
      ]);
      if (textRes.status === 401 || imgRes.status === 401) { handleUnauthorized(); return; }
      if (!textRes.ok) { if (!silent) messagesArea.innerHTML = '<div class="msg-status">Failed to load</div>'; return; }
      const textMsgs = await textRes.json();
      const imgMsgs  = imgRes.ok ? await imgRes.json() : [];
      const merged   = mergeAndSort(textMsgs, imgMsgs);
      conversations[user] = merged;
      if (activeType !== 'dm' || activeTarget !== user) return;
      if (silent && merged.length === lastMsgCount) return;
      lastMsgCount = merged.length;
      renderMessages(merged, false);
      loadDmPreviews();
    } catch { if (!silent) messagesArea.innerHTML = '<div class="msg-status">Network error</div>'; }
  }

  // ── Fetch & render: Group (text + images merged) ───────────
  async function fetchAndRenderGroup(silent = false) {
    try {
      const [textRes, imgRes] = await Promise.all([
        fetch(`${BASE_URL}/groups/${activeTarget}/messages`, { headers: authHeaders() }),
        fetch(`${BASE_URL}/images/group/${activeTarget}`,    { headers: authHeaders() })
      ]);
      if (textRes.status === 401 || imgRes.status === 401) { handleUnauthorized(); return; }
      if (!textRes.ok) { if (!silent) messagesArea.innerHTML = '<div class="msg-status">Failed to load</div>'; return; }
      const textMsgs = await textRes.json();
      const imgMsgs  = imgRes.ok ? await imgRes.json() : [];
      const merged   = mergeAndSort(textMsgs, imgMsgs);
      if (silent && merged.length === lastMsgCount) return;
      lastMsgCount = merged.length;
      renderMessages(merged, true);
      loadGroups();
    } catch { if (!silent) messagesArea.innerHTML = '<div class="msg-status">Network error</div>'; }
  }

  function mergeAndSort(textMsgs, imgMsgs) {
    const all = [...textMsgs, ...imgMsgs];
    all.sort((a, b) => new Date(a.timestamp) - new Date(b.timestamp));
    return all;
  }

  // ── Render messages ────────────────────────────────────────
  function renderMessages(messages, isGroup) {
    const atBottom = isScrolledToBottom();
    messagesArea.innerHTML = '';

    if (!messages || messages.length === 0) {
      messagesArea.innerHTML = '<div class="msg-status">No messages yet — say hello!</div>';
      return;
    }

    let lastDate = null;
    messages.forEach(msg => {
      const d = formatDate(msg.timestamp);
      if (d !== lastDate) {
        const div = document.createElement('div');
        div.className = 'date-divider';
        div.textContent = d;
        messagesArea.appendChild(div);
        lastDate = d;
      }

      const isSent = msg.sender === ME;
      const grp    = document.createElement('div');
      grp.className = `msg-group ${isSent ? 'sent' : 'recv'}`;

      if (isGroup && !isSent) {
        const label = document.createElement('span');
        label.className = 'msg-sender-label';
        label.textContent = msg.sender;
        grp.appendChild(label);
      }

      if (msg.messageType === 'IMAGE') {
        grp.appendChild(buildImageBubble(msg));
      } else {
        const bubble = document.createElement('div');
        bubble.className = 'msg-bubble';
        bubble.textContent = msg.content;
        grp.appendChild(bubble);
      }

      const time = document.createElement('span');
      time.className = 'msg-time';
      time.textContent = formatTime(msg.timestamp);
      grp.appendChild(time);

      messagesArea.appendChild(grp);
    });

    if (atBottom) scrollToBottom();
  }

  function buildImageBubble(msg) {
    const wrap = document.createElement('div');
    wrap.className = 'msg-img-bubble';

    if (msg.decryptedDataUrl) {
      const img = document.createElement('img');
      img.className = 'msg-img-thumb';
      img.src = msg.decryptedDataUrl;
      img.alt = msg.imageName || 'Image';
      wrap.appendChild(img);

      const overlay = document.createElement('div');
      overlay.className = 'msg-img-overlay';
      overlay.innerHTML = `<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><circle cx="11" cy="11" r="8"/><line x1="21" y1="21" x2="16.65" y2="16.65"/></svg>`;
      wrap.appendChild(overlay);

      const caption = document.createElement('div');
      caption.className = 'msg-img-caption';
      caption.textContent = msg.imageName || 'Image';
      wrap.appendChild(caption);

      wrap.addEventListener('click', () => openImageViewer(msg));
    } else {
      wrap.innerHTML = `<div style="padding:10px;font-size:0.82rem;color:var(--danger)">&#9888; Image integrity check failed</div>`;
    }
    return wrap;
  }

  // ── Send text message ──────────────────────────────────────
  sendBtn.addEventListener('click', sendMessage);
  messageInput.addEventListener('keydown', e => {
    if (e.key === 'Enter' && !e.shiftKey) { e.preventDefault(); sendMessage(); }
  });

  async function sendMessage() {
    const content = messageInput.value.trim();
    if (!content || !activeTarget) return;
    messageInput.value = '';
    sendBtn.disabled = true;
    try {
      let res;
      if (activeType === 'dm') {
        res = await fetch(`${BASE_URL}/messages/send`, {
          method: 'POST', headers: authHeaders(),
          body: JSON.stringify({ receiver: activeTarget, content })
        });
      } else {
        res = await fetch(`${BASE_URL}/groups/${activeTarget}/messages`, {
          method: 'POST', headers: authHeaders(),
          body: JSON.stringify({ content })
        });
      }
      if (res.status === 401) { handleUnauthorized(); return; }
      if (res.ok) {
        if (activeType === 'dm') await fetchAndRenderDm();
        else                     await fetchAndRenderGroup();
      } else {
        const data = await res.json().catch(() => ({}));
        showToast(data.message || 'Failed to send', 'error');
        messageInput.value = content;
      }
    } catch {
      showToast('Network error — message not sent', 'error');
      messageInput.value = content;
    } finally {
      sendBtn.disabled = false;
      messageInput.focus();
    }
  }

  // ── Image upload ───────────────────────────────────────────
  // The <label id="btnAttach"> wraps the hidden file input, so clicking it
  // naturally opens the file picker. We only intercept to guard against
  // no active conversation.
  document.getElementById('btnAttach').addEventListener('click', e => {
    if (!activeTarget) {
      e.preventDefault();
      showToast('Open a conversation first', 'error');
    }
  });

  imageFileInput.addEventListener('change', () => {
    const file = imageFileInput.files[0];
    if (!file) return;
    imageFileInput.value = '';

    const allowed = ['image/jpeg', 'image/jpg', 'image/png', 'image/webp'];
    if (!allowed.includes(file.type)) {
      showToast('Unsupported format — use JPG, PNG, or WEBP', 'error');
      return;
    }
    if (file.size > 10 * 1024 * 1024) {
      showToast('File too large — maximum 10 MB', 'error');
      return;
    }

    pendingImageFile = file;
    const reader = new FileReader();
    reader.onload = ev => {
      document.getElementById('imgPreviewEl').src = ev.target.result;
      document.getElementById('imgPreviewMeta').textContent =
        `${file.name}  ·  ${(file.size / 1024).toFixed(1)} KB  ·  ${file.type}`;
      openModal('modalImagePreview');
    };
    reader.readAsDataURL(file);
  });

  document.getElementById('btnSendImage').addEventListener('click', async () => {
    if (!pendingImageFile || !activeTarget) return;
    closeModal('modalImagePreview');

    const progressEl = document.createElement('div');
    progressEl.className = 'upload-progress';
    progressEl.innerHTML = `<div class="progress-spinner"></div> Encrypting &amp; sending image…`;
    messagesArea.appendChild(progressEl);
    scrollToBottom();

    const formData = new FormData();
    formData.append('file', pendingImageFile);

    try {
      let res;
      if (activeType === 'dm') {
        formData.append('receiver', activeTarget);
        res = await fetch(`${BASE_URL}/images/dm/send`, {
          method: 'POST',
          headers: { 'Authorization': `Bearer ${getToken()}` },
          body: formData
        });
      } else {
        res = await fetch(`${BASE_URL}/images/group/${activeTarget}/send`, {
          method: 'POST',
          headers: { 'Authorization': `Bearer ${getToken()}` },
          body: formData
        });
      }
      if (res.status === 401) { handleUnauthorized(); return; }
      if (res.ok) {
        pendingImageFile = null;
        if (activeType === 'dm') await fetchAndRenderDm();
        else                     await fetchAndRenderGroup();
      } else {
        const data = await res.json().catch(() => ({}));
        showToast(data.message || 'Failed to send image', 'error');
      }
    } catch {
      showToast('Network error — image not sent', 'error');
    } finally {
      progressEl.remove();
    }
  });

  // ── Image viewer ───────────────────────────────────────────
  function openImageViewer(msg) {
    document.getElementById('imgViewTitle').textContent = msg.imageName || 'Image';
    document.getElementById('imgViewEl').src = msg.decryptedDataUrl;
    document.getElementById('imgViewMeta').textContent =
      `${msg.imageName || ''}  ·  ${(msg.imageSize / 1024).toFixed(1)} KB  ·  ${msg.sender}  ·  ${formatTime(msg.timestamp)}`;
    const dlBtn = document.getElementById('btnDownloadImage');
    dlBtn.href     = msg.decryptedDataUrl;
    dlBtn.download = msg.imageName || 'image';
    openModal('modalImageView');
  }

  // ── New DM modal ───────────────────────────────────────────
  document.getElementById('btnNewDm').addEventListener('click', () => openModal('modalDm'));

  document.getElementById('btnDmOpen').addEventListener('click', async () => {
    const username = document.getElementById('dmUsername').value.trim();
    const errEl    = document.getElementById('dmUsernameErr');
    errEl.textContent = '';
    if (!username) { errEl.textContent = 'Username is required'; return; }
    if (username === ME) { errEl.textContent = 'You cannot chat with yourself'; return; }
    closeModal('modalDm');
    document.getElementById('dmUsername').value = '';
    await openDm(username);
  });

  document.getElementById('dmUsername').addEventListener('keydown', e => {
    if (e.key === 'Enter') document.getElementById('btnDmOpen').click();
  });

  // ── Create group modal ─────────────────────────────────────
  document.getElementById('btnNewGroup').addEventListener('click', () => openModal('modalGroup'));

  document.getElementById('btnGroupCreate').addEventListener('click', async () => {
    const name    = document.getElementById('groupName').value.trim();
    const members = document.getElementById('groupMembers').value
                      .split(',').map(s => s.trim()).filter(Boolean);
    const nameErr = document.getElementById('groupNameErr');
    nameErr.textContent = '';
    if (!name || name.length < 2) { nameErr.textContent = 'Group name must be at least 2 characters'; return; }
    try {
      const res = await fetch(`${BASE_URL}/groups`, {
        method: 'POST', headers: authHeaders(),
        body: JSON.stringify({ name, members })
      });
      if (res.status === 401) { handleUnauthorized(); return; }
      const data = await res.json();
      if (res.ok) {
        closeModal('modalGroup');
        document.getElementById('groupName').value    = '';
        document.getElementById('groupMembers').value = '';
        await loadGroups();
        openGroup(data);
      } else {
        nameErr.textContent = data.message || 'Failed to create group';
      }
    } catch { showToast('Network error', 'error'); }
  });

  // ── Add member modal ───────────────────────────────────────
  function openAddMemberModal(groupId) {
    document.getElementById('addMemberUsername').value = '';
    document.getElementById('addMemberErr').textContent = '';
    openModal('modalAddMember');

    document.getElementById('btnAddMemberOk').onclick = async () => {
      const username = document.getElementById('addMemberUsername').value.trim();
      const errEl    = document.getElementById('addMemberErr');
      errEl.textContent = '';
      if (!username) { errEl.textContent = 'Username is required'; return; }
      try {
        const res = await fetch(`${BASE_URL}/groups/${groupId}/members`, {
          method: 'POST', headers: authHeaders(),
          body: JSON.stringify({ username })
        });
        const data = await res.json();
        if (res.ok) {
          closeModal('modalAddMember');
          activeGroupObj = data;
          updateGroupHeader(data);
          await loadGroups();
          showToast(`${username} added to group`);
        } else {
          errEl.textContent = data.message || 'Failed to add member';
        }
      } catch { showToast('Network error', 'error'); }
    };
  }

  // ── Leave group ────────────────────────────────────────────
  async function leaveGroup(groupId) {
    if (!confirm('Leave this group?')) return;
    try {
      const res = await fetch(`${BASE_URL}/groups/${groupId}/leave`, {
        method: 'DELETE', headers: authHeaders()
      });
      if (res.ok || res.status === 204) {
        showToast('You left the group');
        activeType = null; activeTarget = null; activeGroupObj = null;
        showEmptyState();
        await loadGroups();
      } else {
        const data = await res.json().catch(() => ({}));
        showToast(data.message || 'Could not leave group', 'error');
      }
    } catch { showToast('Network error', 'error'); }
  }

  // ── Modal helpers ──────────────────────────────────────────
  function openModal(id)  { document.getElementById(id).classList.remove('sc-hidden'); }
  function closeModal(id) { document.getElementById(id).classList.add('sc-hidden'); }

  document.querySelectorAll('[data-close]').forEach(btn => {
    btn.addEventListener('click', () => closeModal(btn.dataset.close));
  });

  document.querySelectorAll('.modal-overlay').forEach(overlay => {
    overlay.addEventListener('click', e => {
      if (e.target === overlay) closeModal(overlay.id);
    });
  });

  // ── UI helpers ─────────────────────────────────────────────
  function showActiveChat() {
    emptyState.classList.add('sc-hidden');
    activeChatEl.classList.remove('sc-hidden');
    lastMsgCount = 0;
  }

  function showEmptyState() {
    activeChatEl.classList.add('sc-hidden');
    emptyState.classList.remove('sc-hidden');
  }

  function stopPolling() {
    if (pollingTimer) { clearInterval(pollingTimer); pollingTimer = null; }
  }

  function scrollToBottom() { messagesArea.scrollTop = messagesArea.scrollHeight; }

  function isScrolledToBottom() {
    return messagesArea.scrollHeight - messagesArea.scrollTop - messagesArea.clientHeight < 80;
  }

  function handleUnauthorized() {
    stopPolling();
    localStorage.clear();
    window.location.href = 'index.html';
  }

  function showToast(msg, type = 'success') {
    const old = document.getElementById('sc-toast');
    if (old) old.remove();
    const t = document.createElement('div');
    t.id = 'sc-toast';
    t.textContent = msg;
    Object.assign(t.style, {
      position:'fixed', bottom:'24px', left:'50%', transform:'translateX(-50%)',
      background: type === 'error' ? '#dc2626' : '#1b5e20',
      color:'#fff', padding:'10px 20px', borderRadius:'8px',
      fontSize:'0.88rem', fontWeight:'500', zIndex:'9999',
      boxShadow:'0 4px 16px rgba(0,0,0,.2)'
    });
    document.body.appendChild(t);
    setTimeout(() => t.remove(), 3200);
  }

  // ── Logout ─────────────────────────────────────────────────
  document.getElementById('logoutBtn').addEventListener('click', () => {
    stopPolling();
    localStorage.clear();
    window.location.href = 'index.html';
  });

  window.addEventListener('beforeunload', stopPolling);
}
const username = localStorage.getItem("username");

if (username) {
    document.getElementById("current-user").innerText = username;
}
