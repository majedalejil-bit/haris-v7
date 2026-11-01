// حارس الهاتف — خدمة تنبيه البريد (Google Apps Script)
// المصمم والمنفذ: ماجد أبو سيدرا
//
// يرسل صورة المحاولة الخاطئة وموقع الهاتف إلى بريد صاحب الحساب فقط.
// البريد لا يُرسل إلا بعد التحقق من دخول المستخدم عبر Firebase.

const API_KEY = 'AIzaSyCTKSlAnYAqdW-_c9Qufeb-vM1gu1cgNhQ';
const WEB_PAGE = 'https://majedalejil-bit.github.io/haris-v7/';

function doPost(e) {
  try {
    const d = JSON.parse(e.postData.contents);
    const email = verifiedEmail_(d.idToken);
    if (!email) return out_({ ok: false, error: 'auth' });

    const blob = Utilities.newBlob(Utilities.base64Decode(d.img), 'image/jpeg', 'haris.jpg');
    const hasLoc = typeof d.lat === 'number' && typeof d.lng === 'number';
    const map = hasLoc ? 'https://maps.google.com/?q=' + d.lat + ',' + d.lng : '';
    const when = String(d.when || '');

    const html =
      '<div dir="rtl" style="font-family:Tahoma,Arial,sans-serif;font-size:15px;color:#0f172a">' +
      '<h2 style="margin:0 0 8px;color:#b91c1c">محاولة فتح هاتفك برمز خاطئ</h2>' +
      '<p style="margin:0 0 12px">الوقت: <b>' + esc_(when) + '</b></p>' +
      '<img src="cid:photo" style="max-width:100%;border-radius:12px;display:block;margin:0 0 14px">' +
      (hasLoc
        ? '<p style="margin:0 0 6px"><a href="' + map + '" style="background:#16a34a;color:#fff;padding:10px 16px;border-radius:10px;text-decoration:none;display:inline-block">عرض موقع الهاتف على الخريطة</a></p>' +
          (d.acc ? '<p style="margin:0 0 12px;color:#64748b">دقة الموقع: حوالي ' + Math.round(d.acc) + ' متر</p>' : '')
        : '<p style="margin:0 0 12px;color:#64748b">لم يتوفر موقع الهاتف لحظة الالتقاط.</p>') +
      '<p style="margin:12px 0 0"><a href="' + WEB_PAGE + '">كل صورك على صفحة حارس الهاتف</a></p>' +
      '<p style="margin:18px 0 0;color:#94a3b8;font-size:12px">حارس الهاتف — المصمم والمنفذ: ماجد أبو سيدرا</p>' +
      '</div>';

    MailApp.sendEmail({
      to: email,
      subject: 'تنبيه حارس الهاتف: محاولة فتح هاتفك ' + when,
      htmlBody: html,
      body: 'محاولة فتح هاتفك برمز خاطئ — ' + when + (hasLoc ? '\nالموقع: ' + map : ''),
      inlineImages: { photo: blob },
      attachments: [blob],
      name: 'حارس الهاتف'
    });
    return out_({ ok: true });
  } catch (err) {
    return out_({ ok: false, error: String(err) });
  }
}

function doGet() {
  return out_({ ok: true, service: 'haris-mail' });
}

function verifiedEmail_(idToken) {
  if (!idToken) return '';
  const r = UrlFetchApp.fetch('https://identitytoolkit.googleapis.com/v1/accounts:lookup?key=' + API_KEY, {
    method: 'post',
    contentType: 'application/json',
    payload: JSON.stringify({ idToken: idToken }),
    muteHttpExceptions: true
  });
  if (r.getResponseCode() !== 200) return '';
  const users = JSON.parse(r.getContentText()).users || [];
  return users.length ? (users[0].email || '') : '';
}

function esc_(s) {
  return s.replace(/[&<>"]/g, function (c) { return { '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;' }[c]; });
}

function out_(o) {
  return ContentService.createTextOutput(JSON.stringify(o)).setMimeType(ContentService.MimeType.JSON);
}

// شغّل هذه الدالة مرة واحدة من المحرر لمنح صلاحية الإرسال
function authorize() {
  MailApp.getRemainingDailyQuota();
  UrlFetchApp.fetch('https://www.google.com');
}
