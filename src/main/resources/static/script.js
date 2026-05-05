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
async function setupLanPanel() {
  const ipText = document.getElementById("lan-ip-text");
  const urlEl = document.getElementById("lan-url");
  const labelEl = document.getElementById("network-label");

  if (!ipText || !urlEl) return;

  const host = window.location.hostname;
  const port = window.location.port ? `:${window.location.port}` : '';
  const lanUrl = `${window.location.protocol}//${host}${port}`;
  
  urlEl.innerText = lanUrl;

  let mode = "INTERNET";
  if (host === "localhost" || host === "127.0.0.1" || host === "::1") {
    mode = "LOCAL";
  } else if (/^192\.168\./.test(host) || /^10\./.test(host) || /^172\.(1[6-9]|2\d|3[0-1])\./.test(host)) {
    mode = "LAN";
  }

  if (labelEl) labelEl.innerText = mode;

  if (mode === "LOCAL") {
    ipText.innerText = "LOCAL - " + host;
  } else if (mode === "LAN") {
    ipText.innerText = "On LAN - " + host;
  } else {
    ipText.innerText = "INTERNET - " + host;
  }

  document.getElementById("copyLanUrl").onclick = () => {
    navigator.clipboard.writeText(lanUrl);
  };
}

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
        if (data.email) localStorage.setItem('sc_email', data.email);
        if (data.publicKey) localStorage.setItem('sc_server_public_key', data.publicKey);
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
        if (data.email) localStorage.setItem('sc_email', data.email);
        if (data.publicKey) localStorage.setItem('sc_server_public_key', data.publicKey);
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
  const typingIndicator = document.getElementById('typingIndicator');
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
  let e2eIdentity    = null;
  let stompSocket    = null;
  let stompConnected = false;
  let stompReconnectTimer = null;
  let typingTimer    = null;
  let typingSentAt   = 0;
  const profileCache = new Map();

  // ── Init ───────────────────────────────────────────────────
  loggedInUserEl.textContent = ME;
  avatarEl.textContent       = initials(ME);
  setupLanPanel();
  initializeChat();

  async function initializeChat() {
    allDmPreviews = mergeKnownUsers([]);
    renderDmList(allDmPreviews);

    try {
      await ensureE2EIdentity();
    } catch (e) {
      showToast(e.message || 'Could not prepare E2E keys', 'error');
    }

    await loadSidebar();
    await restoreActiveDm();
    connectRealtime();
  }

  // ── Sidebar bootstrap ──────────────────────────────────────
  // E2E helpers: private keys stay in this browser; the backend only receives public keys and ciphertext.
  function utf8ToBytes(text) {
    return new TextEncoder().encode(text);
  }

  function bytesToUtf8(bytes) {
    return new TextDecoder().decode(bytes);
  }

  function bytesToBase64(bytes) {
    let binary = '';
    const chunk = 0x8000;
    for (let i = 0; i < bytes.length; i += chunk) {
      binary += String.fromCharCode(...bytes.subarray(i, i + chunk));
    }
    return btoa(binary);
  }

  function base64ToBytes(base64) {
    const binary = atob(base64);
    const bytes = new Uint8Array(binary.length);
    for (let i = 0; i < binary.length; i++) bytes[i] = binary.charCodeAt(i);
    return bytes;
  }

  function base64Json(obj) {
    return bytesToBase64(utf8ToBytes(JSON.stringify(obj)));
  }

  function jsonFromBase64(value) {
    return JSON.parse(bytesToUtf8(base64ToBytes(value)));
  }

  function randomNonce() {
    const nonce = new Uint8Array(12);
    crypto.getRandomValues(nonce);
    return nonce;
  }

  async function ensureE2EIdentity() {
    if (e2eIdentity) return e2eIdentity;
    if (!crypto.subtle) throw new Error('WebCrypto is not available in this browser');

    const privateKeyName = `sc_e2e_private_${ME}`;
    const publicKeyName = `sc_e2e_public_${ME}`;
    let privateJwk = JSON.parse(localStorage.getItem(privateKeyName) || 'null');
    let publicKeyString = localStorage.getItem(publicKeyName);

    if (!privateJwk || !publicKeyString) {
      const pair = await crypto.subtle.generateKey(
        { name: 'ECDH', namedCurve: 'P-256' },
        true,
        ['deriveKey']
      );
      privateJwk = await crypto.subtle.exportKey('jwk', pair.privateKey);
      const publicJwk = await crypto.subtle.exportKey('jwk', pair.publicKey);
      publicKeyString = base64Json(publicJwk);
      localStorage.setItem(privateKeyName, JSON.stringify(privateJwk));
      localStorage.setItem(publicKeyName, publicKeyString);
    }

    const privateKey = await crypto.subtle.importKey(
      'jwk',
      privateJwk,
      { name: 'ECDH', namedCurve: 'P-256' },
      false,
      ['deriveKey']
    );

    e2eIdentity = { privateKey, publicKeyString };
    await publishMyPublicKey(publicKeyString);
    return e2eIdentity;
  }

  async function publishMyPublicKey(publicKey) {
    const res = await fetch(`${BASE_URL}/users/me/public-key`, {
      method: 'POST',
      headers: authHeaders(),
      body: JSON.stringify({ publicKey })
    });
    if (res.status === 401) { handleUnauthorized(); return; }
    if (!res.ok) throw new Error('Could not publish E2E public key');
    profileCache.delete(ME);
  }

  async function fetchUserProfile(username) {
    if (profileCache.has(username)) return profileCache.get(username);
    const res = await fetch(`${BASE_URL}/users/${encodeURIComponent(username)}`, { headers: authHeaders() });
    if (res.status === 401) { handleUnauthorized(); return null; }
    if (!res.ok) throw new Error('User profile not found');
    const profile = await res.json();
    profileCache.set(username, profile);
    return profile;
  }

  async function importPublicKey(publicKeyString) {
    if (!publicKeyString) throw new Error('Missing public key');
    try {
      const jwk = jsonFromBase64(publicKeyString);
      return crypto.subtle.importKey(
        'jwk',
        jwk,
        { name: 'ECDH', namedCurve: 'P-256' },
        false,
        []
      );
    } catch {
      throw new Error('E2E public key is not ready for this user');
    }
  }

  async function deriveSharedKey(peerPublicKeyString) {
    await ensureE2EIdentity();
    const publicKey = await importPublicKey(peerPublicKeyString);
    return crypto.subtle.deriveKey(
      { name: 'ECDH', public: publicKey },
      e2eIdentity.privateKey,
      { name: 'AES-GCM', length: 256 },
      false,
      ['encrypt', 'decrypt']
    );
  }

  async function encryptBytesWithKey(bytes, key) {
    const nonce = randomNonce();
    const encrypted = await crypto.subtle.encrypt({ name: 'AES-GCM', iv: nonce }, key, bytes);
    return {
      encryptedContent: bytesToBase64(new Uint8Array(encrypted)),
      nonce: bytesToBase64(nonce)
    };
  }

  async function decryptBytesWithKey(encryptedContent, nonce, key) {
    const decrypted = await crypto.subtle.decrypt(
      { name: 'AES-GCM', iv: base64ToBytes(nonce) },
      key,
      base64ToBytes(encryptedContent)
    );
    return new Uint8Array(decrypted);
  }

  async function encryptTextWithKey(text, key) {
    return encryptBytesWithKey(utf8ToBytes(text), key);
  }

  async function decryptTextWithKey(encryptedContent, nonce, key) {
    return bytesToUtf8(await decryptBytesWithKey(encryptedContent, nonce, key));
  }

  async function encryptDmPayload(text, receiver) {
    const profile = await fetchUserProfile(receiver);
    const key = await deriveSharedKey(profile.publicKey);
    return {
      ...(await encryptTextWithKey(text, key)),
      senderPublicKey: e2eIdentity.publicKeyString
    };
  }

  async function decryptDmMessage(msg) {
    if (msg.messageType === 'IMAGE') return msg;
    const encryptedContent = msg.encryptedContent || msg.content;
    if (!encryptedContent || !msg.nonce || (!msg.senderPublicKey && msg.sender !== ME)) {
      return { ...msg, content: '[Encrypted message unavailable on this device]' };
    }

    try {
      const peer = msg.sender === ME ? msg.receiver : msg.sender;
      const peerPublicKey = msg.sender === ME
        ? (await fetchUserProfile(peer)).publicKey
        : msg.senderPublicKey;
      const key = await deriveSharedKey(peerPublicKey);
      return { ...msg, content: await decryptTextWithKey(encryptedContent, msg.nonce, key) };
    } catch {
      return { ...msg, content: '[Encrypted message unavailable on this device]' };
    }
  }

  async function encryptGroupPayload(text, group) {
    const contentKey = await crypto.subtle.generateKey(
      { name: 'AES-GCM', length: 256 },
      true,
      ['encrypt', 'decrypt']
    );
    const encrypted = await encryptTextWithKey(text, contentKey);
    const rawKey = new Uint8Array(await crypto.subtle.exportKey('raw', contentKey));
    const members = [...new Set([...(group.members || []), ME])];
    const keys = {};

    await Promise.all(members.map(async member => {
      const profile = await fetchUserProfile(member);
      const sharedKey = await deriveSharedKey(profile.publicKey);
      keys[member] = {
        ...(await encryptBytesWithKey(rawKey, sharedKey)),
        senderPublicKey: e2eIdentity.publicKeyString
      };
    }));

    return JSON.stringify({
      v: 1,
      kind: 'group-text',
      encryptedContent: encrypted.encryptedContent,
      nonce: encrypted.nonce,
      keys
    });
  }

  async function decryptGroupMessage(msg) {
    if (msg.messageType === 'IMAGE') return msg;
    try {
      const envelope = JSON.parse(msg.encryptedContent || msg.content);
      const wrappedKey = envelope.keys && envelope.keys[ME];
      if (!wrappedKey) throw new Error('No group key for this user');
      const sharedKey = await deriveSharedKey(wrappedKey.senderPublicKey);
      const rawKey = await decryptBytesWithKey(wrappedKey.encryptedContent, wrappedKey.nonce, sharedKey);
      const contentKey = await crypto.subtle.importKey('raw', rawKey, 'AES-GCM', false, ['decrypt']);
      return {
        ...msg,
        content: await decryptTextWithKey(envelope.encryptedContent, envelope.nonce, contentKey)
      };
    } catch {
      return { ...msg, content: '[Encrypted message unavailable on this device]' };
    }
  }

  async function decryptTextMessages(messages, isGroup) {
    return Promise.all((messages || []).map(msg => isGroup ? decryptGroupMessage(msg) : decryptDmMessage(msg)));
  }

  async function encryptImageEnvelope(file, isGroup) {
    const bytes = new Uint8Array(await file.arrayBuffer());
    if (isGroup) {
      const contentKey = await crypto.subtle.generateKey(
        { name: 'AES-GCM', length: 256 },
        true,
        ['encrypt', 'decrypt']
      );
      const encrypted = await encryptBytesWithKey(bytes, contentKey);
      const rawKey = new Uint8Array(await crypto.subtle.exportKey('raw', contentKey));
      const members = [...new Set([...(activeGroupObj?.members || []), ME])];
      const keys = {};
      await Promise.all(members.map(async member => {
        const profile = await fetchUserProfile(member);
        const sharedKey = await deriveSharedKey(profile.publicKey);
        keys[member] = {
          ...(await encryptBytesWithKey(rawKey, sharedKey)),
          senderPublicKey: e2eIdentity.publicKeyString
        };
      }));
      return JSON.stringify({ v: 1, kind: 'group-image', ...encrypted, keys });
    }

    const profile = await fetchUserProfile(activeTarget);
    const key = await deriveSharedKey(profile.publicKey);
    return JSON.stringify({
      v: 1,
      kind: 'dm-image',
      ...(await encryptBytesWithKey(bytes, key)),
      senderPublicKey: e2eIdentity.publicKeyString
    });
  }

  async function decryptImageMessage(msg, isGroup) {
    if (msg.decryptedDataUrl || !msg.encryptedData) return msg;
    try {
      const envelope = JSON.parse(bytesToUtf8(base64ToBytes(msg.encryptedData)));
      let imageBytes;

      if (isGroup) {
        const wrappedKey = envelope.keys && envelope.keys[ME];
        if (!wrappedKey) throw new Error('No group image key for this user');
        const sharedKey = await deriveSharedKey(wrappedKey.senderPublicKey);
        const rawKey = await decryptBytesWithKey(wrappedKey.encryptedContent, wrappedKey.nonce, sharedKey);
        const contentKey = await crypto.subtle.importKey('raw', rawKey, 'AES-GCM', false, ['decrypt']);
        imageBytes = await decryptBytesWithKey(envelope.encryptedContent, envelope.nonce, contentKey);
      } else {
        const peerPublicKey = msg.sender === ME
          ? (await fetchUserProfile(msg.receiver)).publicKey
          : envelope.senderPublicKey;
        const key = await deriveSharedKey(peerPublicKey);
        imageBytes = await decryptBytesWithKey(envelope.encryptedContent, envelope.nonce, key);
      }

      return {
        ...msg,
        decryptedDataUrl: `data:${msg.imageType};base64,${bytesToBase64(imageBytes)}`
      };
    } catch (err) {
      console.error('[decryptImageMessage] Failed to decrypt image:', err);
      return { ...msg, decryptedDataUrl: null };
    }
  }

  async function decryptImageMessages(messages, isGroup) {
    return Promise.all((messages || []).map(msg => decryptImageMessage(msg, isGroup)));
  }

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
    chatHeaderName.classList.add('clickable-name');
    chatHeaderName.title = 'View profile';
    chatHeaderSub.textContent  = 'Secure channel - E2E AES-GCM';
    chatHeaderActs.innerHTML = `<span class="enc-badge">&#128274; DM</span>`;
    hideTypingIndicator();

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
    chatHeaderName.classList.remove('clickable-name');
    chatHeaderName.removeAttribute('title');
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
      const textMsgs = await decryptTextMessages(await textRes.json(), false);
      const imgMsgs  = imgRes.ok ? await decryptImageMessages(await imgRes.json(), false) : [];
      const merged   = mergeAndSort(textMsgs, imgMsgs);
      conversations[user] = merged;
      if (activeType !== 'dm' || activeTarget !== user) return;
      if (silent && merged.length === lastMsgCount) return;
      lastMsgCount = merged.length;
      renderMessages(merged, false);
      markIncomingVisibleMessages(merged);
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
      const textMsgs = await decryptTextMessages(await textRes.json(), true);
      const imgMsgs  = imgRes.ok ? await decryptImageMessages(await imgRes.json(), true) : [];
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

    const validMessages = (messages || []).filter(msg => {
      if (msg.messageType === 'IMAGE') return !!msg.decryptedDataUrl;
      return msg.content !== '[Encrypted message unavailable on this device]';
    });

    if (validMessages.length === 0) {
      messagesArea.innerHTML = '<div class="msg-status">No messages yet — say hello!</div>';
      return;
    }

    let lastDate = null;
    validMessages.forEach(msg => {
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

      const meta = document.createElement('span');
      meta.className = 'msg-meta';

      const time = document.createElement('span');
      time.className = 'msg-time';
      time.textContent = formatTime(msg.timestamp);
      meta.appendChild(time);

      if (isSent && !isGroup) {
        meta.appendChild(buildStatusIndicator(msg));
      }

      grp.appendChild(meta);

      messagesArea.appendChild(grp);
    });

    if (atBottom) scrollToBottom();
  }

  function buildStatusIndicator(msg) {
    const status = document.createElement('span');
    status.className = 'msg-status-icon';
    const retryCount = Number(msg.retryCount || 0);
    const state = (msg.status || '').toUpperCase();

    if (state === 'FAILED' || (state === 'PENDING' && retryCount > 5)) {
      status.classList.add('failed');
      status.textContent = 'Failed to send';
    } else if (state === 'READ') {
      status.classList.add('read');
      status.innerHTML = '&#10003;&#10003;';
    } else if (state === 'DELIVERED') {
      status.innerHTML = '&#10003;&#10003;';
    } else if (retryCount > 0) {
      status.textContent = 'Retrying...';
    } else if (state === 'PENDING') {
      status.textContent = 'Sending...';
    } else {
      status.innerHTML = '&#10003;';
    }

    return status;
  }

  async function acknowledgeDelivered(messages) {
    const incoming = (messages || []).filter(msg =>
      msg && msg.id && msg.sender !== ME && msg.status !== 'DELIVERED' && msg.status !== 'READ'
    );
    await Promise.all(incoming.map(msg =>
      fetch(`${BASE_URL}/${msg.messageType === 'IMAGE' ? 'images/dm' : 'messages'}/ack?messageId=${encodeURIComponent(msg.id)}`, {
        method: 'POST',
        headers: authHeaders()
      }).catch(() => null)
    ));
  }

  async function markIncomingVisibleMessages(messages) {
    if (activeType !== 'dm' || !activeTarget) return;
    const incoming = (messages || []).filter(msg =>
      msg && msg.id && msg.sender === activeTarget && msg.status !== 'READ'
    );
    if (incoming.length === 0) return;

    const ids = incoming.map(msg => msg.id);
    const textIds = incoming.filter(m => m.messageType !== 'IMAGE').map(m => m.id);
    const imgIds = incoming.filter(m => m.messageType === 'IMAGE').map(m => m.id);

    try {
      if (textIds.length > 0) {
        await fetch(`${BASE_URL}/messages/read`, {
          method: 'POST',
          headers: authHeaders(),
          body: JSON.stringify({ messageIds: textIds })
        });
      }
      if (imgIds.length > 0) {
        await fetch(`${BASE_URL}/images/dm/read`, {
          method: 'POST',
          headers: authHeaders(),
          body: JSON.stringify({ messageIds: imgIds })
        });
      }
      incoming.forEach(msg => { msg.status = 'READ'; });
    } catch {
      acknowledgeDelivered(incoming);
    }
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
      wrap.innerHTML = `<div style="padding:10px;font-size:0.82rem;color:var(--danger)">Image unavailable on this device</div>`;
    }
    return wrap;
  }

  // ── Send text message ──────────────────────────────────────
  sendBtn.addEventListener('click', sendMessage);
  messageInput.addEventListener('keydown', e => {
    if (e.key === 'Enter' && !e.shiftKey) { e.preventDefault(); sendMessage(); }
  });
  messageInput.addEventListener('input', notifyTyping);

  async function sendMessage() {
    const content = messageInput.value.trim();
    if (!content || !activeTarget) return;
    messageInput.value = '';
    sendBtn.disabled = true;
    try {
      let res;
      if (activeType === 'dm') {
        const encrypted = await encryptDmPayload(content, activeTarget);
        res = await fetch(`${BASE_URL}/messages/send`, {
          method: 'POST', headers: authHeaders(),
          body: JSON.stringify({ receiver: activeTarget, ...encrypted })
        });
      } else {
        const encryptedContent = await encryptGroupPayload(content, activeGroupObj);
        res = await fetch(`${BASE_URL}/groups/${activeTarget}/messages`, {
          method: 'POST', headers: authHeaders(),
          body: JSON.stringify({
            content: encryptedContent,
            nonce: crypto.randomUUID ? crypto.randomUUID() : bytesToBase64(randomNonce())
          })
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
    } catch (e) {
      showToast(e.message || 'Network error - message not sent', 'error');
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

    try {
      const encryptedEnvelope = await encryptImageEnvelope(pendingImageFile, activeType === 'group');
      const encryptedFile = new File([encryptedEnvelope], pendingImageFile.name, { type: pendingImageFile.type });
      const formData = new FormData();
      formData.append('file', encryptedFile);

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
    } catch (e) {
      showToast(e.message || 'Network error - image not sent', 'error');
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
  document.querySelector('.topbar-user')?.addEventListener('click', () => openUserProfile(ME));
  chatHeaderName.addEventListener('click', () => {
    if (activeType === 'dm' && activeTarget) openUserProfile(activeTarget);
  });

  async function openUserProfile(username) {
    const avatar = document.getElementById('profileAvatar');
    const nameEl = document.getElementById('profileUsername');
    const emailEl = document.getElementById('profileEmail');
    const keyEl = document.getElementById('profilePublicKey');

    avatar.textContent = initials(username);
    nameEl.textContent = username;
    emailEl.textContent = 'Loading...';
    keyEl.textContent = '';
    openModal('modalProfile');

    try {
      const profile = await fetchUserProfile(username);
      nameEl.textContent = profile.username || username;
      emailEl.textContent = profile.email || 'Email unavailable';
      keyEl.textContent = profile.publicKey || 'No public key published yet';
    } catch {
      emailEl.textContent = 'Profile unavailable';
      keyEl.textContent = '';
    }
  }

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
  function connectRealtime() {
    if (!window.WebSocket || stompSocket) return;
    const proto = window.location.protocol === 'https:' ? 'wss:' : 'ws:';
    stompSocket = new WebSocket(`${proto}//${window.location.host}/ws-native`);

    stompSocket.onopen = () => {
      sendStompFrame('CONNECT', {
        'accept-version': '1.2',
        'heart-beat': '10000,10000'
      });
    };

    stompSocket.onmessage = event => {
      String(event.data).split('\0').filter(Boolean).forEach(handleStompFrame);
    };

    stompSocket.onclose = () => {
      stompConnected = false;
      stompSocket = null;
      if (!stompReconnectTimer) {
        stompReconnectTimer = setTimeout(() => {
          stompReconnectTimer = null;
          connectRealtime();
        }, 4000);
      }
    };
  }

  function disconnectRealtime() {
    if (stompReconnectTimer) clearTimeout(stompReconnectTimer);
    stompReconnectTimer = null;
    stompConnected = false;
    if (stompSocket) {
      try { sendStompFrame('DISCONNECT', {}); } catch {}
      stompSocket.close();
      stompSocket = null;
    }
  }

  function sendStompFrame(command, headers = {}, body = '') {
    if (!stompSocket || stompSocket.readyState !== WebSocket.OPEN) return;
    const headerText = Object.entries(headers)
      .map(([k, v]) => `${k}:${v}`)
      .join('\n');
    stompSocket.send(`${command}\n${headerText}\n\n${body}\0`);
  }

  function subscribe(destination, id) {
    sendStompFrame('SUBSCRIBE', { id, destination, ack: 'auto' });
  }

  function handleStompFrame(rawFrame) {
    const frame = rawFrame.trimStart();
    if (!frame) return;
    const splitAt = frame.indexOf('\n\n');
    const head = splitAt >= 0 ? frame.slice(0, splitAt) : frame;
    const body = splitAt >= 0 ? frame.slice(splitAt + 2) : '';
    const [command, ...headerLines] = head.split('\n');
    const headers = Object.fromEntries(headerLines.map(line => {
      const idx = line.indexOf(':');
      return idx > -1 ? [line.slice(0, idx), line.slice(idx + 1)] : [line, ''];
    }));

    if (command === 'CONNECTED') {
      stompConnected = true;
      subscribe(`/topic/messages/${ME}`, 'messages');
      subscribe(`/topic/message-status/${ME}`, 'message-status');
      subscribe(`/topic/typing/${ME}`, 'typing');
      return;
    }

    if (command !== 'MESSAGE') return;
    const destination = headers.destination || '';
    const payload = body ? JSON.parse(body) : {};
    if (destination.includes('/topic/messages/')) handleRealtimeMessage(payload);
    if (destination.includes('/topic/message-status/')) handleStatusEvent(payload);
    if (destination.includes('/topic/typing/')) handleTypingEvent(payload);
  }

  async function handleRealtimeMessage(msg) {
    if (!msg || msg.sender === ME) return;
    await acknowledgeDelivered([msg]);
    if (activeType === 'dm' && activeTarget === msg.sender) {
      await fetchAndRenderDm(msg.sender);
    } else {
      await loadDmPreviews();
    }
  }

  function handleStatusEvent(event) {
    if (!event || !event.messageIds) return;
    const ids = new Set(event.messageIds);
    Object.values(conversations).forEach(list => {
      (list || []).forEach(msg => {
        if (ids.has(msg.id)) {
          msg.status = event.status || msg.status;
          if (typeof event.retryCount === 'number' && event.retryCount > 0) msg.retryCount = event.retryCount;
        }
      });
    });

    if (activeType === 'dm' && activeTarget && conversations[activeTarget]) {
      renderMessages(conversations[activeTarget], false);
    }
  }

  function notifyTyping() {
    if (activeType !== 'dm' || !activeTarget || !stompConnected) return;
    const now = Date.now();
    if (now - typingSentAt < 700) return;
    typingSentAt = now;
    sendStompFrame('SEND', {
      destination: '/app/typing',
      'content-type': 'application/json'
    }, JSON.stringify({ sender: ME, receiver: activeTarget, typing: true }));
  }

  function handleTypingEvent(event) {
    if (!event || !event.typing || event.sender === ME) return;
    if (activeType !== 'dm' || activeTarget !== event.sender) return;
    showTypingIndicator(event.sender);
  }

  function showTypingIndicator(username) {
    if (!typingIndicator) return;
    typingIndicator.textContent = `${username} is typing...`;
    typingIndicator.classList.remove('sc-hidden');
    if (typingTimer) clearTimeout(typingTimer);
    typingTimer = setTimeout(hideTypingIndicator, 2000);
  }

  function hideTypingIndicator() {
    if (!typingIndicator) return;
    typingIndicator.classList.add('sc-hidden');
    typingIndicator.textContent = '';
  }

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
    disconnectRealtime();
    localStorage.removeItem('sc_token');
    localStorage.removeItem('sc_user');
    localStorage.removeItem('sc_email');
    localStorage.removeItem('sc_server_public_key');
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
    disconnectRealtime();
    localStorage.removeItem('sc_token');
    localStorage.removeItem('sc_user');
    localStorage.removeItem('sc_email');
    localStorage.removeItem('sc_server_public_key');
    window.location.href = 'index.html';
  });

  window.addEventListener('beforeunload', () => {
    stopPolling();
    disconnectRealtime();
  });
}
const username = localStorage.getItem("sc_user");

if (username) {
    document.getElementById("current-user").innerText = username;
}

window.logout = function() {
  document.getElementById('logoutBtn').click();
};
