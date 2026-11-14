# الروبوت المتكلّم - مشروع أندرويد كامل (قابل للبناء التلقائي عبر GitHub Actions)

## هيكلة المشروع
```
android/
├── app/
│   ├── build.gradle                              - إعدادات تطبيق الأندرويد (اسم الحزمة، الصلاحيات، المكتبات)
│   └── src/main/
│       ├── AndroidManifest.xml                   - الصلاحيات والإعدادات (نسخة نصية قابلة للتعديل - مو binary)
│       ├── assets/index.html                      - ** كامل كود التطبيق ** (الواجهة + المنطق + الذكاء الاصطناعي)
│       ├── java/com/talkingrobot/app/
│       │   ├── MainActivity.java                 - يشغّل WebView ويحمّل index.html
│       │   ├── AndroidBridge.java                 - يطبّق كل دوال AndroidBridge اللي يناديها الجافاسكربت
│       │   └── BgListenService.java               - خدمة بسيطة للاستماع بالخلفية
│       └── res/
│           ├── drawable/robot.png                  - أيقونة التطبيق ووجه الروبوت
│           └── values/ (strings.xml, themes.xml)
├── build.gradle                                   - إعدادات جذر المشروع
├── settings.gradle
└── gradle.properties
.github/workflows/build-apk.yml                    - يبني APK تلقائياً كل ما ترفع تعديل على main
```

## كيف تضيف مميزة جديدة
- **تعديل بالواجهة/السلوك (صوت، شكل، أسئلة...):** عدّل فقط `android/app/src/main/assets/index.html` - هذا ملف HTML/CSS/JS عادي.
- **ميزة تحتاج وصول لأندرويد نفسه** (مثلاً فتح تطبيق جديد، صلاحية جديدة):
  1. أضف دالة جديدة بـ `AndroidBridge.java` معلّمة بـ `@JavascriptInterface`.
  2. ناديها من الجافاسكربت بـ `N("اسم_الدالة", معطيات...)`.
  3. لو تحتاج صلاحية جديدة، أضفها بـ `AndroidManifest.xml`.
- بعد أي تعديل وترفعه (`git push`) على فرع `main`، الـ APK ينبني تلقائياً - تلگاه جاهز بتبويب **Actions → آخر تشغيلة → Artifacts**.

## خطوات الرفع على مستودعك أول مرة
```bash
git clone https://github.com/oo9ee9-cpu/AgentForge_Flat_GitHub_Starter.git
cd AgentForge_Flat_GitHub_Starter
# احذف الملفات القديمة (الـ APK المفكوك السابق) إذا تريد تبدأ نظيف
git rm -r --cached AndroidManifest.xml README.txt classes.dex index.html resources.arsc robot.png 2>/dev/null || true
# انسخ محتويات هذا المشروع فوق المستودع (بنفس الهيكلة - مو flat هالمرة، لازم المجلدات تضل زي ما هي)
cp -r /path/to/extracted/project/* .
git add .
git commit -m "Add full Android project (buildable via GitHub Actions)"
git push origin main
```
بعدها روح لتبويب **Actions** بالمستودع - راح تشوف "Build APK" يشتغل تلقائياً، وبعد ما يخلص (حوالي دقيقتين)، نزّل الـ APK من **Artifacts**.

## ملاحظة مهمة عن الهيكلة
الطلب السابق كان ملفات "flat" (بدون مجلدات) - هذا كان مناسب لأنه كان بس تفكيك APK جاهز للفحص.
لكن مشروع Gradle/Android **لازم** يحافظ على هيكلة المجلدات (`android/app/src/main/...`) حتى تگدر أدوات البناء تلقاه - إذا تفرد الملفات بمجلد وحد ما راح ينبني أبداً.

## التطوير محلياً (اختياري، قبل الرفع)
إذا عندك Android Studio على جهازك:
1. افتح مجلد `android/` كمشروع.
2. خلّيه يسوي Sync تلقائي.
3. `Run ▶` على محاكي أو موبايل متوصل.
