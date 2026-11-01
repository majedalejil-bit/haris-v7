(() => {
  const API_KEY = 'AIzaSyCTKSlAnYAqdW-_c9Qufeb-vM1gu1cgNhQ';
  const DOCS = 'https://firestore.googleapis.com/v1/projects/hars-983d4/databases/(default)/documents';
  const STORE = 'haris_session';

  const $ = (id) => document.getElementById(id);
  const icons = {
    pin: '<svg viewBox="0 0 24 24" aria-hidden="true"><path d="M12 21s7-6.2 7-12a7 7 0 1 0-14 0c0 5.8 7 12 7 12Z"/><circle cx="12" cy="9" r="2.5"/></svg>',
    down: '<svg viewBox="0 0 24 24" aria-hidden="true"><path d="M12 4v11m0 0-4-4m4 4 4-4M5 20h14"/></svg>'
  };

  let session = load();

  function load() {
    try { return JSON.parse(sessionStorage.getItem(STORE)) || null; } catch { return null; }
  }
  function save(s) {
    session = s;
    try { s ? sessionStorage.setItem(STORE, JSON.stringify(s)) : sessionStorage.removeItem(STORE); } catch {}
  }

  function errText(m) {
    if (!m) return 'تعذّر الاتصال، تأكد من الإنترنت';
    if (m.startsWith('EMAIL_NOT_FOUND')) return 'هذا البريد غير مسجّل في التطبيق';
    if (m.startsWith('INVALID_PASSWORD') || m.startsWith('INVALID_LOGIN_CREDENTIALS')) return 'البريد أو كلمة المرور غير صحيحة';
    if (m.startsWith('INVALID_EMAIL')) return 'صيغة البريد غير صحيحة';
    if (m.startsWith('TOO_MANY_ATTEMPTS')) return 'محاولات كثيرة، انتظر قليلًا ثم أعد المحاولة';
    if (m.startsWith('USER_DISABLED')) return 'هذا الحساب موقوف';
    return 'خطأ: ' + m;
  }

  async function signIn(email, password) {
    const r = await fetch('https://identitytoolkit.googleapis.com/v1/accounts:signInWithPassword?key=' + API_KEY, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ email, password, returnSecureToken: true })
    });
    const j = await r.json().catch(() => ({}));
    if (!r.ok) throw new Error(errText(j.error && j.error.message));
    return {
      email,
      uid: j.localId,
      id: j.idToken,
      refresh: j.refreshToken,
      exp: Date.now() + Number(j.expiresIn) * 1000
    };
  }

  async function token() {
    if (!session) throw new Error('سجّل الدخول من جديد');
    if (Date.now() < session.exp - 60000) return session.id;
    const r = await fetch('https://securetoken.googleapis.com/v1/token?key=' + API_KEY, {
      method: 'POST',
      headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
      body: 'grant_type=refresh_token&refresh_token=' + encodeURIComponent(session.refresh)
    });
    const j = await r.json().catch(() => ({}));
    if (!r.ok) { save(null); throw new Error('انتهت الجلسة، سجّل الدخول من جديد'); }
    save({ ...session, id: j.id_token, refresh: j.refresh_token, exp: Date.now() + Number(j.expires_in) * 1000 });
    return session.id;
  }

  async function photos() {
    const t = await token();
    const body = {
      structuredQuery: {
        from: [{ collectionId: 'photos' }],
        orderBy: [{ field: { fieldPath: 'time' }, direction: 'DESCENDING' }],
        limit: 60
      }
    };
    const r = await fetch(DOCS + '/users/' + session.uid + ':runQuery', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json', Authorization: 'Bearer ' + t },
      body: JSON.stringify(body)
    });
    if (r.status === 401 || r.status === 403) throw new Error('لا توجد صلاحية لقراءة الصور، سجّل الدخول من جديد');
    if (!r.ok) throw new Error('تعذّر تحميل الصور (' + r.status + ')');
    const rows = await r.json();
    return rows
      .filter((x) => x.document)
      .map((x) => {
        const f = x.document.fields || {};
        const num = (k) => (f[k] && (f[k].doubleValue ?? f[k].integerValue)) != null ? Number(f[k].doubleValue ?? f[k].integerValue) : null;
        return {
          time: Number(f.time && f.time.integerValue) || 0,
          img: f.img && f.img.stringValue,
          lat: num('lat'),
          lng: num('lng'),
          acc: num('acc')
        };
      })
      .filter((p) => p.img);
  }

  function fmt(ms) {
    const d = new Date(ms);
    const date = d.toLocaleDateString('ar', { weekday: 'long', day: 'numeric', month: 'long', year: 'numeric' });
    const time = d.toLocaleTimeString('ar', { hour: '2-digit', minute: '2-digit', second: '2-digit' });
    return { date, time };
  }

  function card(p) {
    const el = document.createElement('article');
    el.className = 'shot';
    const src = 'data:image/jpeg;base64,' + p.img;
    const { date, time } = fmt(p.time);

    const img = document.createElement('img');
    img.src = src;
    img.alt = 'صورة ' + time;
    img.loading = 'lazy';
    img.addEventListener('click', () => openViewer(src));

    const body = document.createElement('div');
    body.className = 'shot-body';
    const when = document.createElement('div');
    when.className = 'when';
    when.textContent = time;
    const sm = document.createElement('small');
    sm.textContent = date;
    when.appendChild(sm);

    const actions = document.createElement('div');
    actions.className = 'shot-actions';
    if (p.lat != null && p.lng != null) {
      const a = document.createElement('a');
      a.className = 'chip map';
      a.href = 'https://maps.google.com/?q=' + p.lat + ',' + p.lng;
      a.target = '_blank';
      a.rel = 'noopener';
      a.innerHTML = icons.pin + '<span></span>';
      a.querySelector('span').textContent = 'الموقع' + (p.acc ? ' (دقة ' + Math.round(p.acc) + ' م)' : '');
      actions.appendChild(a);
    } else {
      const n = document.createElement('span');
      n.className = 'nomap';
      n.textContent = 'بدون موقع';
      actions.appendChild(n);
    }
    const dl = document.createElement('a');
    dl.className = 'chip';
    dl.href = src;
    dl.download = 'haris-' + p.time + '.jpg';
    dl.innerHTML = icons.down + '<span>تحميل</span>';
    actions.appendChild(dl);

    body.append(when, actions);
    el.append(img, body);
    return el;
  }

  function openViewer(src) {
    $('viewerImg').src = src;
    $('viewer').hidden = false;
  }
  $('viewerClose').addEventListener('click', () => { $('viewer').hidden = true; });
  $('viewer').addEventListener('click', (e) => { if (e.target.id === 'viewer') $('viewer').hidden = true; });

  async function showGallery() {
    $('login').hidden = true;
    $('gallery').hidden = false;
    $('who').textContent = session.email;
    const grid = $('grid');
    const msg = $('galleryMsg');
    msg.className = 'msg';
    msg.textContent = '';
    grid.innerHTML = '<div class="skeleton"></div><div class="skeleton"></div><div class="skeleton"></div>';
    try {
      const list = await photos();
      grid.innerHTML = '';
      if (!list.length) {
        grid.innerHTML = '<div class="empty">لا توجد صور بعد. عندما يحاول أحد فتح هاتفك برمز خاطئ ستظهر صورته هنا.</div>';
        return;
      }
      msg.textContent = 'عدد الصور: ' + list.length;
      list.forEach((p) => grid.appendChild(card(p)));
    } catch (e) {
      grid.innerHTML = '';
      msg.className = 'msg err';
      msg.textContent = e.message;
      if (!session) showLogin();
    }
  }

  function showLogin() {
    $('gallery').hidden = true;
    $('login').hidden = false;
  }

  $('loginForm').addEventListener('submit', async (e) => {
    e.preventDefault();
    const btn = $('loginBtn');
    const msg = $('loginMsg');
    btn.disabled = true;
    msg.className = 'msg';
    msg.textContent = 'جارٍ الدخول...';
    try {
      save(await signIn($('email').value.trim(), $('pass').value));
      $('pass').value = '';
      msg.textContent = '';
      showGallery();
    } catch (err) {
      msg.className = 'msg err';
      msg.textContent = err.message;
    } finally {
      btn.disabled = false;
    }
  });

  $('reloadBtn').addEventListener('click', showGallery);
  $('logoutBtn').addEventListener('click', () => { save(null); showLogin(); });

  if (session) showGallery(); else showLogin();
})();
