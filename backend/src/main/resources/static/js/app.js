// ============================================================================
// DocQuery frontend — vanilla JS, no build step required.
// Talks to the Spring Boot API under /api/*.
// ============================================================================

const API_BASE = '/api';

const state = {
  token: localStorage.getItem('docquery_token') || null,
  user: JSON.parse(localStorage.getItem('docquery_user') || 'null'),
  documents: [],
  activeDocumentId: null,
  pollingTimer: null,
};

// ---------------------------------------------------------------------------
// DOM refs
// ---------------------------------------------------------------------------
const el = {
  authScreen: document.getElementById('auth-screen'),
  appScreen: document.getElementById('app-screen'),
  tabLogin: document.getElementById('tab-login'),
  tabRegister: document.getElementById('tab-register'),
  loginForm: document.getElementById('login-form'),
  registerForm: document.getElementById('register-form'),
  authError: document.getElementById('auth-error'),
  loginSubmit: document.getElementById('login-submit'),
  registerSubmit: document.getElementById('register-submit'),

  userAvatar: document.getElementById('user-avatar'),
  userName: document.getElementById('user-name'),
  logoutBtn: document.getElementById('logout-btn'),

  uploadZone: document.getElementById('upload-zone'),
  fileInput: document.getElementById('file-input'),
  docList: document.getElementById('doc-list'),

  activeDocTitle: document.getElementById('active-doc-title'),
  activeDocSub: document.getElementById('active-doc-sub'),
  chatScroll: document.getElementById('chat-scroll'),
  noDocState: document.getElementById('no-doc-state'),
  composer: document.getElementById('composer'),
  questionInput: document.getElementById('question-input'),
  sendBtn: document.getElementById('send-btn'),

  toastStack: document.getElementById('toast-stack'),
};

// ---------------------------------------------------------------------------
// Toasts
// ---------------------------------------------------------------------------
function toast(message, type = 'info') {
  const t = document.createElement('div');
  t.className = 'toast' + (type === 'error' ? ' error' : '');
  t.textContent = message;
  el.toastStack.appendChild(t);
  setTimeout(() => t.remove(), 4500);
}

// ---------------------------------------------------------------------------
// API helper
// ---------------------------------------------------------------------------
async function api(path, options = {}) {
  const headers = options.headers || {};
  if (state.token) headers['Authorization'] = 'Bearer ' + state.token;
  if (!(options.body instanceof FormData) && options.body) {
    headers['Content-Type'] = 'application/json';
  }

  const res = await fetch(API_BASE + path, { ...options, headers });
  const isJson = res.headers.get('content-type')?.includes('application/json');
  const data = isJson ? await res.json() : null;

  if (!res.ok) {
    throw new Error(data?.message || `Request failed (${res.status})`);
  }
  return data;
}

// ---------------------------------------------------------------------------
// Auth
// ---------------------------------------------------------------------------
el.tabLogin.addEventListener('click', () => switchTab('login'));
el.tabRegister.addEventListener('click', () => switchTab('register'));

function switchTab(which) {
  const isLogin = which === 'login';
  el.tabLogin.classList.toggle('active', isLogin);
  el.tabRegister.classList.toggle('active', !isLogin);
  el.loginForm.classList.toggle('hidden', !isLogin);
  el.registerForm.classList.toggle('hidden', isLogin);
  hideAuthError();
}

function showAuthError(message) {
  el.authError.textContent = message;
  el.authError.classList.remove('hidden');
}
function hideAuthError() {
  el.authError.classList.add('hidden');
}

el.loginForm.addEventListener('submit', async (e) => {
  e.preventDefault();
  hideAuthError();
  el.loginSubmit.disabled = true;
  el.loginSubmit.textContent = 'Logging in…';
  try {
    const email = document.getElementById('login-email').value.trim();
    const password = document.getElementById('login-password').value;
    const data = await api('/auth/login', { method: 'POST', body: JSON.stringify({ email, password }) });
    onAuthSuccess(data);
  } catch (err) {
    showAuthError(err.message);
  } finally {
    el.loginSubmit.disabled = false;
    el.loginSubmit.textContent = 'Log in';
  }
});

el.registerForm.addEventListener('submit', async (e) => {
  e.preventDefault();
  hideAuthError();
  el.registerSubmit.disabled = true;
  el.registerSubmit.textContent = 'Creating account…';
  try {
    const name = document.getElementById('register-name').value.trim();
    const email = document.getElementById('register-email').value.trim();
    const password = document.getElementById('register-password').value;
    const passwordConfirm = document.getElementById('register-password-confirm').value;

    if (password !== passwordConfirm) {
      throw new Error("Passwords don't match — please re-type them.");
    }

    const data = await api('/auth/register', { method: 'POST', body: JSON.stringify({ name, email, password }) });
    onAuthSuccess(data);
  } catch (err) {
    showAuthError(err.message);
  } finally {
    el.registerSubmit.disabled = false;
    el.registerSubmit.textContent = 'Create account';
  }
});

function onAuthSuccess(data) {
  state.token = data.token;
  state.user = { name: data.name, email: data.email };
  localStorage.setItem('docquery_token', state.token);
  localStorage.setItem('docquery_user', JSON.stringify(state.user));
  enterApp();
}

el.logoutBtn.addEventListener('click', () => {
  state.token = null;
  state.user = null;
  state.documents = [];
  state.activeDocumentId = null;
  clearInterval(state.pollingTimer);
  localStorage.removeItem('docquery_token');
  localStorage.removeItem('docquery_user');
  el.appScreen.classList.add('hidden');
  el.authScreen.classList.remove('hidden');
});

// ---------------------------------------------------------------------------
// App entry
// ---------------------------------------------------------------------------
function enterApp() {
  el.authScreen.classList.add('hidden');
  el.appScreen.classList.remove('hidden');
  el.userName.textContent = state.user.name;
  el.userAvatar.textContent = state.user.name.charAt(0).toUpperCase();
  loadDocuments();
  state.pollingTimer = setInterval(loadDocuments, 5000);
}

// ---------------------------------------------------------------------------
// Documents
// ---------------------------------------------------------------------------
async function loadDocuments() {
  try {
    const docs = await api('/documents');
    state.documents = docs;
    renderDocList();
  } catch (err) {
    // Silent on background polling failures.
  }
}

function renderDocList() {
  el.docList.innerHTML = '';

  if (state.documents.length === 0) {
    el.docList.innerHTML = '<li class="empty-hint">No documents yet.<br/>Upload a PDF to get started.</li>';
    return;
  }

  for (const doc of state.documents) {
    const li = document.createElement('li');
    li.className = 'doc-item' + (doc.id === state.activeDocumentId ? ' active' : '');
    const statusClass = doc.status === 'READY' ? 'status-ready' : doc.status === 'FAILED' ? 'status-failed' : 'status-processing';
    const statusLabel = doc.status === 'READY' ? `Ready · ${doc.chunkCount} chunks`
                        : doc.status === 'FAILED' ? 'Processing failed'
                        : 'Processing…';
    li.innerHTML = `
      <span class="doc-icon">📄</span>
      <div class="doc-meta">
        <div class="doc-name">${escapeHtml(doc.fileName)}</div>
        <div class="doc-status ${statusClass}">${statusLabel}</div>
      </div>
      <button class="doc-delete-btn" title="Delete document" data-doc-id="${doc.id}">×</button>
    `;
    li.addEventListener('click', (e) => {
      if (e.target.classList.contains('doc-delete-btn')) return;
      selectDocument(doc.id);
    });
    li.querySelector('.doc-delete-btn').addEventListener('click', (e) => {
      e.stopPropagation();
      deleteDocument(doc.id, doc.fileName);
    });
    el.docList.appendChild(li);
  }
}

async function deleteDocument(id, fileName) {
  const confirmed = confirm(`Delete "${fileName}"? This can't be undone.`);
  if (!confirmed) return;

  try {
    await api(`/documents/${id}`, { method: 'DELETE' });
    toast(`Deleted "${fileName}".`);

    if (state.activeDocumentId === id) {
      state.activeDocumentId = null;
      el.activeDocTitle.textContent = 'No document selected';
      el.activeDocSub.textContent = 'Choose a document from the left, or upload a new one.';
      el.chatScroll.innerHTML = '';
      el.noDocState.classList.remove('hidden');
      el.composer.classList.add('hidden');
    }

    await loadDocuments();
  } catch (err) {
    toast(err.message, 'error');
  }
}

function selectDocument(id) {
  const doc = state.documents.find(d => d.id === id);
  if (!doc) return;

  state.activeDocumentId = id;
  renderDocList();

  el.activeDocTitle.textContent = doc.fileName;
  el.activeDocSub.textContent = doc.status === 'READY'
    ? `${doc.pageCount} pages · ${doc.chunkCount} chunks indexed`
    : doc.status === 'FAILED'
    ? 'This document failed to process — try re-uploading it.'
    : 'Still processing… this usually takes under a minute.';

  el.noDocState.classList.add('hidden');
  el.chatScroll.innerHTML = '';
  el.composer.classList.toggle('hidden', doc.status !== 'READY');

  appendAssistantBubble(
    doc.status === 'READY'
      ? `I've read through "${doc.fileName}". Ask me anything about it.`
      : `Still indexing "${doc.fileName}" — hang tight, this updates automatically.`
  );
}

// ---------------------------------------------------------------------------
// Upload
// ---------------------------------------------------------------------------
el.uploadZone.addEventListener('click', () => el.fileInput.click());

el.uploadZone.addEventListener('dragover', (e) => {
  e.preventDefault();
  el.uploadZone.classList.add('dragover');
});
el.uploadZone.addEventListener('dragleave', () => el.uploadZone.classList.remove('dragover'));
el.uploadZone.addEventListener('drop', (e) => {
  e.preventDefault();
  el.uploadZone.classList.remove('dragover');
  if (e.dataTransfer.files.length) handleUpload(e.dataTransfer.files[0]);
});

el.fileInput.addEventListener('change', () => {
  if (el.fileInput.files.length) handleUpload(el.fileInput.files[0]);
  el.fileInput.value = '';
});

async function handleUpload(file) {
  if (file.type !== 'application/pdf') {
    toast('Only PDF files are supported.', 'error');
    return;
  }
  const formData = new FormData();
  formData.append('file', file);

  toast(`Uploading "${file.name}"…`);
  try {
    const doc = await api('/documents/upload', { method: 'POST', body: formData });
    toast(`"${doc.fileName}" is processing.`);
    await loadDocuments();
    selectDocument(doc.id);
  } catch (err) {
    toast(err.message, 'error');
  }
}

// ---------------------------------------------------------------------------
// Chat / Ask
// ---------------------------------------------------------------------------
el.sendBtn.addEventListener('click', sendQuestion);
el.questionInput.addEventListener('keydown', (e) => {
  if (e.key === 'Enter') sendQuestion();
});

async function sendQuestion() {
  const question = el.questionInput.value.trim();
  if (!question || !state.activeDocumentId) return;

  appendUserBubble(question);
  el.questionInput.value = '';
  el.sendBtn.disabled = true;

  const typingEl = appendTypingIndicator();

  try {
    const res = await api(`/documents/${state.activeDocumentId}/ask`, {
      method: 'POST',
      body: JSON.stringify({ question }),
    });
    typingEl.remove();
    appendAssistantBubble(res.answer, res.sourceSnippet, res.fromCache);
  } catch (err) {
    typingEl.remove();
    appendAssistantBubble(`Something went wrong: ${err.message}`);
  } finally {
    el.sendBtn.disabled = false;
  }
}

function appendUserBubble(text) {
  const row = document.createElement('div');
  row.className = 'msg-row user';
  row.innerHTML = `<div class="bubble">${escapeHtml(text)}</div>`;
  el.chatScroll.appendChild(row);
  scrollToBottom();
}

function appendAssistantBubble(text, sourceSnippet, fromCache) {
  const row = document.createElement('div');
  row.className = 'msg-row assistant';

  let inner = `<div class="bubble">${formatAnswerText(text)}`;
  if (sourceSnippet) {
    inner += `
      <div class="source-card">
        <span class="source-label">Source passage</span>
        ${escapeHtml(sourceSnippet)}
      </div>`;
  }
  if (fromCache) {
    inner += `<div><span class="cache-badge">⚡ Cached answer</span></div>`;
  }
  inner += `</div>`;

  row.innerHTML = inner;
  el.chatScroll.appendChild(row);
  scrollToBottom();
}

function appendTypingIndicator() {
  const row = document.createElement('div');
  row.className = 'msg-row assistant';
  row.innerHTML = `<div class="bubble typing-dots"><span></span><span></span><span></span></div>`;
  el.chatScroll.appendChild(row);
  scrollToBottom();
  return row;
}

function scrollToBottom() {
  el.chatScroll.scrollTop = el.chatScroll.scrollHeight;
}

// Turns the AI's plain-text answer (which may contain "- item" lines and
// occasional **bold**) into readable HTML: paragraphs and bullet lists.
function formatAnswerText(rawText) {
  if (!rawText) return '';

  const lines = rawText.split(/\r?\n/).map(l => l.trim()).filter(l => l.length > 0);

  const htmlParts = [];
  let currentList = [];

  const flushList = () => {
    if (currentList.length > 0) {
      htmlParts.push('<ul class="answer-list">' + currentList.join('') + '</ul>');
      currentList = [];
    }
  };

  for (const line of lines) {
    const isListItem = /^-\s+/.test(line);
    if (isListItem) {
      const content = line.replace(/^-\s+/, '');
      currentList.push(`<li>${inlineFormat(content)}</li>`);
    } else {
      flushList();
      htmlParts.push(`<p>${inlineFormat(line)}</p>`);
    }
  }
  flushList();

  return htmlParts.join('');
}

// Escapes HTML first (safety), then re-applies **bold** as <strong>.
function inlineFormat(text) {
  let escaped = escapeHtml(text);
  escaped = escaped.replace(/\*\*(.+?)\*\*/g, '<strong>$1</strong>');
  return escaped;
}

function escapeHtml(str) {
  const div = document.createElement('div');
  div.textContent = str;
  return div.innerHTML;
}

// ---------------------------------------------------------------------------
// Boot
// ---------------------------------------------------------------------------
if (state.token && state.user) {
  enterApp();
}
