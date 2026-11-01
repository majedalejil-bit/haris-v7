# حارس الهاتف — Haris Phone

المصمم والمنفذ: ماجد أبو سيدرا

- `app/` كود تطبيق أندرويد، كل ميزة في ملف مستقل (Kotlin).
- `web/` صفحة عرض الصور من المتصفح (HTML / CSS / JS منفصلة).
- `email/Code.gs` خدمة تنبيه البريد (Google Apps Script).

أي تعديل يُرفع إلى `main` يُبنى تلقائيًا، ويُنشر في الرابط الدائم:
https://github.com/majedalejil-bit/haris-v7/releases/latest/download/haris.apk

لإصدار تحديث للأصدقاء: ارفع `versionCode` في `app/build.gradle.kts`.
