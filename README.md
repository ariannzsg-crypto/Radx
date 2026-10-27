<p align="center">
  <img src="docs/icon-512.png" width="160" alt="Radx" />
</p>

# Radx

کلاینت VPN / پروکسی برای اندروید با Kotlin و Jetpack Compose، ساخته‌شده روی هسته‌ی **sing-box** (کتابخانه‌ی `libbox`) به‌همراه هسته‌ی **Xray** برای ترنسپورت‌هایی که sing-box نمی‌شناسد (مثل XHTTP).

رابط کاربری مینیمال و دارک، با رنگ‌های آبی/فیروزه‌ای برند Radx؛ الهام‌گرفته از v2rayNG، Happ و Hiddify.

| ویژگی | وضعیت نسخه ۱.۰.۰ |
| --- | --- |
| ایمپورت VMess / VLESS / Trojan / Shadowsocks / Hysteria2 / TUIC / SOCKS | ✅ |
| ایمپورت Clash Meta (YAML) و sing-box (JSON) | ✅ |
| لینک سابسکریپشن + به‌روزرسانی | ✅ |
| ایمپورت از فایل (SAF)، پیست متن، دیپ‌لینک `vless://` | ✅ |
| دکمه اتصال/قطع بزرگ، نمایش وضعیت، مدت اتصال | ✅ |
| لیست نودها، جستجو، تست تأخیر، انتخاب سریع | ✅ |
| آمار ترافیک (آپلود/دانلود) | ✅ (تقریبی، بر پایه شمارنده UID) |
| کپی/اشتراک‌گذاری کانفیگ | ✅ |
| تنظیمات: هسته، حالت تونل، LAN، IPv6، DNS، اعلان | ✅ |
| تنظیمات ظاهر: حالت شیشه‌ای + رنگ تأکیدی (فیروزه‌ای/آبی/بنفش/زمردی) | ✅ |
| اتصال خودکار هنگام باز شدن برنامه | ✅ |
| نمایش نسخه‌ی برنامه و وضعیت هر دو هسته در «درباره» | ✅ |
| کنار گذاشتن خودکار نودهایی که هسته‌ی Xray نمی‌تواند بخواند | ✅ |
| حالت VPN (TUN) و حالت پروکسی محلی (SOCKS/HTTP روی 7890) | ✅ |
| نودهای XHTTP/KCP روی هسته‌ی Xray (پل SOCKS به sing-box) | ✅ |
| تب لاگ زنده‌ی هر دو هسته با فیلتر و کپی | ✅ |
| کارت «ساختار تونل» (رابط، مسیر، DNS، MTU، پل Xray) | ✅ |
| ترتیب نودها بر اساس پینگ (سریع‌ترین/کندترین/نام) | ✅ |
| تنظیمات پیشرفته: MTU تونل، عبور شبکه‌ی محلی، مسدود کردن QUIC، سطح لاگ هسته | ✅ |
| نسخه‌ی ویندوز/لینوکس (ابزار خط فرمان `radx` با همان خط لوله‌ی کانفیگ) | ✅ (`Radx-Windows-vX.Y.Z.zip` در Releases) |
| ساخت APK و انتشار در GitHub Releases | ✅ (workflow آماده) |

---

## ۱. ساختار پروژه

```
Radx/
├── settings.gradle.kts, build.gradle.kts, gradle.properties
├── gradle/libs.versions.toml           # نسخه‌های AGP/Kotlin/Compose
├── app/
│   ├── build.gradle.kts                # source set شرطی libbox + امضا
│   ├── libs/libbox.aar                 # هسته (ساخته‌شده با گوموبایل، git-ignore)
│   └── src/
│       ├── main/
│       │   ├── AndroidManifest.xml
│       │   ├── res/                    # تم، رشته‌ها (fa/en)، آیکون‌ها
│       │   └── java/com/radx/app/
│       │       ├── RadxApplication.kt   # Context/Store مشترک
│       │       ├── MainActivity.kt      # Compose، دیپ‌لینک، مجوز اعلان
│       │       ├── data/                # Models + RadxStore (DataStore)
│       │       ├── config/              # همه‌ی خط لوله‌ی کانفیگ
│       │       │   ├── ProxyLinkParser.kt     # vmess/vless/trojan/ss/hy2/tuic/socks
│       │       │   ├── ClashConfigParser.kt   # YAML → sing-box outbounds
│       │       │   ├── SingBoxConfigParser.kt # استخراج outbounds از JSON
│       │       │   ├── ConfigImporter.kt      # تشخیص فرمت، سابسکریپشن، base64
│       │       │   └── SingBoxConfigBuilder.kt# ساخت کانفیگ نهایی sing-box
│       │       ├── core/                # CoreEngine/TunSetup/TunnelStatus/Traffic
│       │       ├── service/             # RadxVpnService (VpnService + Foreground)
│       │       └── ui/                  # Theme، ViewModel، Screens، Components
│       └── libbox/java/com/radx/app/libbox/
│           └── LibboxEngine.kt          # پل Go↔Kotlin (فقط وقتی AAR هست)
├── desktop/                            # نسخه‌ی ویندوز/لینوکس (همان خط لوله‌ی کانفیگ)
│   ├── build.gradle.kts                # source set مشترک: config + data اپ
│   ├── src/main/kotlin/com/radx/desktop/  # Main (CLI) + DesktopStore + Cores
│   └── dist/                           # radx.cmd و راهنمای ویندوز در بسته‌ی نهایی
├── scripts/
│   ├── build-libbox.sh                 # ساخت libbox.aar با gomobile
│   ├── check_sources.js                # بررسی استاتیک منابع/رشته‌ها
│   └── dev/bootstrap_toolchain.sh      # JDK17 + SDK + Gradle برای ساخت محلی
├── .github/workflows/release.yml       # ساخت AAR + APK + انتشار Release
├── CHANGELOG.md
└── README.md
```

لایه‌بندی: **UI (Compose) → ViewModel → Store/Importer → Service → CoreEngine (libbox)**.
هیچ‌جای UI مستقیم با هسته یا VpnService حرف نمی‌زند؛ همه‌چیز از `RadxViewModel` و `TunnelStatus` می‌گذرد.

---

## ۲. طراحی صفحات

### خانه
- هدر: نام Radx + پیل وضعیت (متصل / در حال اتصال / قطع).
- **دکمه‌ی دایره‌ای بزرگ اتصال** (۱۷۲dp) با حلقه‌ی گرادیان چرخان هنگام اتصال/اتصال‌شدن.
- زیر دکمه: وضعیت متنی، پیام محافظت، و مدت اتصال.
- کارت «نود فعال» (تپ → صفحه نودها) با بج پروتکل.
- کارت جمع‌شدنی **«ساختار تونل»**: همان چیزی که هسته واقعاً ساخت — آدرس‌های رابط، MTU، مسیرها، DNS، مسیریابی خودکار/سخت‌گیرانه، برنامه‌های خارج از تونل، پل Xray (تعداد نود و بازه‌ی پورت) و نسخه‌ی هسته. داده از `TunnelOptions` خود libbox گرفته می‌شود، نه از تنظیمات اپ.
- دو کاشی ترافیک: دانلود (فیروزه‌ای) و آپلود (آبی) با نرخ لحظه‌ای و مجموع.
- دکمه «افزودن کانفیگ» + لیست کانفیگ‌ها؛ هر کارت منوی سه‌نقطه دارد: به‌روزرسانی (فقط سابسکریپشن)، کپی کانفیگ، اشتراک‌گذاری، حذف.

### نودها
- جستجو روی نام/آدرس/پروتکل.
- تست تأخیر گروهی (TCP handshake) با رنگ‌بندی: سبز ≤۲۵۰ms، زرد ≤۶۰۰ms، قرمز بیشتر.
- **دکمه‌ی ترتیب پینگ**: پیش‌فرض ← سریع‌ترین ← کندترین ← نام؛ نودهای بی‌نتیجه ته لیست می‌مانند.
- ردیف‌های کارتی با دایره‌ی انتخاب؛ انتخاب نود در حالت متصل، تونل را با کانفیگ جدید ری‌استارت می‌کند.

### افزودن کانفیگ
BottomSheet با سه تب: **پیست متن** (لینک‌ها، YAML یا JSON)، **لینک اشتراک**، **فایل** (SAF، بدون نیاز به مجوز حافظه).
نام اختیاری برای کانفیگ؛ در نبود نام، از نام نود/هاست سابسکریپشن استفاده می‌شود.

### لاگ
- تب سوم: خروجی زنده‌ی **همر دو هسته** (sing-box از کال‌بک پلتفرم، Xray از خروجی پروسه).
- فیلتر: همه / sing-box / Xray / خطاها؛ رنگ خطوط بر اساس سطح.
- کپی کل لاگ (برای گزارش اتصال ناموفق)، پاک کردن و «دنبال کردن خط جدید».
- آخرین ۶۰۰ خط در حافظه نگه داشته می‌شود؛ نسخه‌ی کامل Xray هم در `files/xray/xray.log` هست.

### تنظیمات
- انتخاب هسته: sing-box (فعال) و Clash Meta (در این نسخه همراه نیست).
- حالت تونل: VPN (TUN) یا پروکسی محلی.
- شبکه: اجازه LAN، IPv6.
- پیشرفته: اندازه‌ی MTU تونل (۱۲۸۰/۱۵۰۰/۹۰۰۰)، عبور شبکه‌ی محلی از بیرون تونل، مسدود کردن QUIC و سطح لاگ هسته (خطا/هشدار/اطلاعات/اشکال‌زدایی).
- DNS محلی/راه دور با دکمه‌ی اعمال.
- ظاهر: کلید «حالت شیشه‌ای» و انتخاب رنگ تأکیدی.
- رفتار: اتصال خودکار هنگام باز شدن برنامه.
- اعلان وضعیت، نسخه و کد نسخه، وضعیت هسته‌ی sing-box و Xray و توضیح برنامه.

### تم
`RadxTheme` فقط دارک است: پس‌زمینه `#070C17`، سطح `#121C2D`، فیروزه‌ای `#22D3EE`، آبی `#2F80ED`. رنگ تأکیدی از تنظیمات می‌آید (`RadxAccent` + `LocalRadxAccent`) و کل رابط را می‌چرخاند.

ظاهر شیشه‌ای: `GlassBackdrop` یک لایه‌ی محیطی با سه هاله‌ی رنگی می‌کشد، `GlassPanel` کارت‌های نیمه‌شفاف با لبه‌ی روشن می‌سازد (`RadxCard` روی همین بنا شده) و `GlassNavBar` نوار پایین شناور است. با خاموش کردن «حالت شیشه‌ای» همین کارت‌ها به سطح‌های تخت قبلی برمی‌گردند. رشته‌ها فارسی (پیش‌فرض) و انگلیسی (`values-en`) با پشتیبانی RTL.

---

## ۳. یکپارچه‌سازی هسته (sing-box / libbox)

هسته، کتابخانه‌ی Go پروژه‌ی [sing-box](https://github.com/SagerNet/sing-box) است که با **gomobile** به AAR تبدیل می‌شود (`io.nekohasekai.libbox`).

### ساخت هسته
```bash
./scripts/build-libbox.sh v1.11.15
# خروجی: app/libs/libbox.aar
```
پیش‌نیاز: Go، JDK 17 (gomobile فقط با openjdk 17 کار می‌کند) و Android SDK + NDK.
نسخه‌ی پین‌شده در `scripts/build-libbox.sh` و workflow: **v1.11.15**.

### هسته‌ی دوم: Xray
ترنسپورت‌هایی مثل `xhttp` فقط در Xray وجود دارند. Radx هسته‌ی Xray را به‌صورت **پروسه‌ی جدا** اجرا می‌کند:

- باینری رسمی Xray برای اندروید در `app/src/main/jniLibs/<abi>/libxray.so` می‌نشیند (تنها جایی که اندروید اجازه‌ی اجرا می‌دهد).
- `XrayRunner` آن را با کانفیگ تولیدشده توسط `XrayConfigBuilder` بالا می‌آورد؛ Xray فقط روی `127.0.0.1:23300+` سوکس باز می‌کند.
- هر نود Xray یک inbound و یک قاعده‌ی مسیریابی مخصوص خودش دارد، بنابراین تغییر نود نیازی به ری‌استارت هسته ندارد.
- در `SingBoxConfigBuilder` هر نود Xray به یک outbound `socks` محلی تبدیل می‌شود؛ tun و DNS همچنان در اختیار sing-box است.
- چون UID اپ از VPN مستثنی است، سوکت‌های Xray مستقیم به سرور می‌رسند و به `protect()` نیازی نیست.
- پروسه‌ی جدا لازم است چون هر دو هسته Go هستند و runtime مشترک gomobile در یک پروسه با هم تصادم می‌کند.

نسخه‌ی پین‌شده: `XRAY_VERSION` در workflow (پیش‌فرض `v26.10.21`). این نسخه فیلد `allowInsecure` را نمی‌پذیرد؛ لینک‌هایی که `allowInsecure=1` دارند همچنان کار می‌کنند چون پارسر آن را حذف می‌کند و به‌جایش `pcs`/`vcn` را به `pinnedPeerCertSha256`/`verifyPeerCertByName` نگاشت می‌کند.

### چرا source set شرطی؟
`app/build.gradle.kts` فقط وقتی `app/libs/libbox.aar` وجود داشته باشد، `src/libbox/java` را کامپایل و AAR را اضافه می‌کند:

```kotlin
val libboxAar = file("libs/libbox.aar")
android.sourceSets.getByName("main").apply { if (libboxAar.exists()) java.srcDir("src/libbox/java") }
dependencies { if (libboxAar.exists()) implementation(files(libboxAar)) }
```

بنابراین پروژه بدون هسته هم build و اجرا می‌شود (UI، ایمپورت، ذخیره‌سازی و تنظیمات کار می‌کنند) و در این حالت دکمه اتصال پیام «هسته در این نسخه وجود ندارد» می‌دهد. APK رسمی همیشه با هسته ساخته می‌شود.

### لایه‌ی انتزاعی
```kotlin
interface CoreEngine {
    fun setup(baseDirectory: String, workingDirectory: String, tempDirectory: String)
    fun start(configJson: String, tun: TunSetup?)   // tun = null در حالت پروکسی
    fun stop()
    val version: String
}

interface TunSetup {
    fun protect(fd: Int): Boolean       // جلوگیری از حلقه‌ی مسیریابی
    fun establish(spec: TunSpec): Int   // ساخت VpnService.Builder و بازگرداندن fd
}
```
`CoreProvider` با `Class.forName` پیاده‌سازی `com.radx.app.libbox.LibboxEngine` را می‌سازد.

### `LibboxEngine` (سمت Go)
- `Libbox.setup(SetupOptions(basePath, workingPath, tempPath, fixAndroidStack = true))`
- `Libbox.newService(configJson, platformInterface)` سپس `BoxService.start()` / `.close()`
- پیاده‌سازی `PlatformInterface` (نسخه ۱.۱۱):
  - `openTun(options)` → تبدیل `TunOptions` به `TunSpec` (`inet4Address`، `inet6Address`، `routeRange`، `routeAddress`، dns، include/exclude package) و تحویل به Service.
  - `autoDetectInterfaceControl(fd)` → `VpnService.protect(fd)`
  - `getInterfaces()` / `startDefaultInterfaceMonitor()` → از `ConnectivityManager` (تشخیص اینترفیس پیش‌فرض و مترد بودن)
  - `findConnectionOwner` (API 29+)، `packageNameByUid`، `uidByPackageName` برای قواعد مبتنی بر اپ
  - `writeLog` → `Log.i("libbox", …)`

### جریان اتصال
1. `VpnService.prepare()` (فقط حالت VPN) → مجوز کاربر.
2. `RadxVpnService.startForeground()` با اعلان وضعیت.
3. ساخت کانفیگ نهایی با `SingBoxConfigBuilder`.
4. `CoreEngine.setup()` → `CoreEngine.start(config, tunSetup)`.
5. `TunnelStatus` به `Connected` می‌رود؛ ViewModel ترافیک را نمونه‌برداری می‌کند.
6. قطع: `engine.stop()` + بستن fd تون + `stopSelf()`.

### ارتقای هسته
نسخه‌ی sing-box و امضای `PlatformInterface` به هم گره خورده‌اند. برای ارتقا:
1. نسخه را در `scripts/build-libbox.sh` و `SING_BOX_VERSION` در workflow عوض کنید.
2. `experimental/libbox/platform.go` و `tun.go` را با `LibboxEngine.kt` تطبیق دهید (متدهای اضافه/حذف‌شده).
3. `release.yml` را با تگ جدید اجرا کنید.

---

## ۴. پرمیشن‌ها و AndroidManifest

```xml
<uses-permission android:name="android.permission.INTERNET" />
<uses-permission android:name="android.permission.ACCESS_NETWORK_STATE" />
<uses-permission android:name="android.permission.FOREGROUND_SERVICE" />
<uses-permission android:name="android.permission.FOREGROUND_SERVICE_SYSTEM_EXEMPTED" />
<uses-permission android:name="android.permission.POST_NOTIFICATIONS" />
```

| مورد | چرا |
| --- | --- |
| `INTERNET` | اتصال به سرور پروکسی و دانلود سابسکریپشن |
| `ACCESS_NETWORK_STATE` | تشخیص اینترفیس پیش‌فرض/شبکه‌ی مترد برای هسته |
| `FOREGROUND_SERVICE` + `..._SYSTEM_EXEMPTED` | تونل باید در سرویس Foreground با نوع `systemExempted` اجرا شود (الزام اندروید ۱۴) |
| `POST_NOTIFICATIONS` | اعلان دائمی وضعیت (اندروید ۱۳+، درخواست زمان اجرا در `MainActivity`) |

سرویس VPN:
```xml
<service
    android:name=".service.RadxVpnService"
    android:exported="false"
    android:foregroundServiceType="systemExempted"
    android:permission="android.permission.BIND_VPN_SERVICE">
    <intent-filter><action android:name="android.net.VpnService" /></intent-filter>
    <meta-data android:name="android.net.VpnService.SUPPORTS_ALWAYS_ON" android:value="true" />
</service>
```
نکته‌ها:
- مجوز VPN یک **رضایت زمان اجرا** است، نه پرمیشن مانیفست؛ فقط با `VpnService.prepare()` گرفته می‌شود.
- `addDisallowedApplication(packageName)` در `establish()` تا سوکت‌های خود هسته وارد تونل نشوند.
- دسترسی به فایل با SAF (`OpenDocument`) است، بنابراین `READ_EXTERNAL_STORAGE` لازم نیست.

---

## ۵. راهنمای پیاده‌سازی

### خط لوله‌ی کانفیگ
```
متن/فایل/URL
   └─ ConfigImporter (تشخیص: Clash YAML → sing-box JSON → base64 → لینک‌ها)
        └─ ProxyLinkParser | ClashConfigParser | SingBoxConfigParser
             └─ List<ProxyNode> (outbound آماده‌ی sing-box + server/port برای پینگ)
                  └─ SingBoxConfigBuilder (inbound + selector + dns + route)
                       └─ RadxVpnService → CoreEngine
```

نمونه‌ی خروجی `SingBoxConfigBuilder` (خلاصه):
```json
{
  "log": {"level": "warn", "timestamp": true},
  "dns": {"servers": [
      {"tag": "dns-remote", "address": "https://1.1.1.1/dns-query", "detour": "proxy"},
      {"tag": "dns-local", "address": "223.5.5.5", "detour": "direct"}],
    "final": "dns-remote", "strategy": "prefer_ipv4"},
  "inbounds": [{"type": "tun", "tag": "tun-in", "address": ["172.19.0.1/30", "fdfe:dcba:9876::1/126"],
                "mtu": 9000, "auto_route": true, "stack": "gvisor"}],
  "outbounds": [
    {"type": "selector", "tag": "proxy", "outbounds": ["node-1"], "default": "node-1"},
    {"type": "vless", "tag": "node-1", "...": "..."},
    {"type": "direct", "tag": "direct"}],
  "route": {
    "rules": [{"action": "sniff"}, {"protocol": "dns", "action": "hijack-dns"},
              {"ip_is_private": true, "outbound": "direct"}],
    "final": "proxy", "auto_detect_interface": true}
}
```
در حالت پروکسی محلی، inbound از `tun` به `mixed` روی `127.0.0.1:7890` (یا `0.0.0.0` با LAN) تغییر می‌کند.

### فرمت‌های پشتیبانی‌شده
- `vmess://` (JSON base64)، `vless://` (TLS/Reality/WS/gRPC/HTTP)، `trojan://`، `ss://` (هر دو شکل + plugin)، `hysteria2://`/`hy2://`، `tuic://`، `socks://`.
- Clash Meta YAML: بخش `proxies:` (ss, vmess, vless, trojan, hysteria2, tuic, socks5, http).
- sing-box JSON: آرایه‌ی `outbounds` با تایپ‌های پروکسی.
- سابسکریپشن: بدنه‌ی خام یا base64.

### ذخیره‌سازی
`RadxStore` روی DataStore: کلیدهای `profiles` (JSON)، `selected_profile`، `settings`. هر `Profile` نودهای از پیش تبدیل‌شده را نگه می‌دارد، پس اجرای بعدی نیازی به پارس مجدد ندارد.

### سابسکریپشن
`ConfigImporter.importSubscription` با OkHttp (UA: `Radx/1.0`)، سپس همان خط لوله. به‌روزرسانی، `selectedTag` قبلی را در صورت وجود حفظ می‌کند و اگر تونل فعال باشد، ری‌استارت می‌شود.

### ترافیک
`TrafficMonitor` از `TrafficStats.getUidRxBytes/TxBytes` همین UID (که هسته داخل آن اجرا می‌شود) نمونه می‌گیرد و نرخ را از اختلاف دو نمونه در ۱ ثانیه حساب می‌کند. اعداد تقریبی‌اند و ترافیک تمام دستگاه را دقیقاً برابر نمی‌گیرند.

### تأخیر نود
اتصال TCP به `server:port` با مهلت ۳ ثانیه؛ عدد = زمان handshake (تقریب خوبی از RTT، بدون نیاز به API اضافه).

### انتخاب نود
نودها داخل یک `selector` به نام `proxy` قرار می‌گیرند. انتخاب کاربر در پروفایل ذخیره و در صورت اتصال، سرویس با کانفیگ جدید ری‌استارت می‌شود (بدون Clash API، تگ `default` سلکتور عوض می‌شود).

### اجرای محلی
```bash
# ساخت هسته (یک‌بار)
./scripts/build-libbox.sh v1.11.15
# باینری Xray (نسخه‌ی اندروید) را در jniLibs بگذارید؛ در CI خودکار دانلود می‌شود
# app/src/main/jniLibs/arm64-v8a/libxray.so
# ساخت دیباگ + تست‌های واحد
./gradlew :app:assembleDebug
./gradlew :app:testDebugUnitTest
# نسخه release
./gradlew :app:assembleRelease
```
Gradle Wrapper (8.9) در مخزن هست؛ نیازی به نصب Gradle جداگانه نیست (فقط JDK 17 و Android SDK).
Android Studio: پروژه را باز کنید؛ اگر `libbox.aar` نباشد، فقط UI/ایمپورت کامپایل می‌شود.

وضعیت بررسی‌شده‌ی همین مخزن: `assembleDebug` و `assembleRelease` موفق، و ۲۴ تست واحد خط لوله‌ی کانفیگ (لینک‌ها، YAML، JSON، base64، ساخت کانفیگ نهایی و تنظیمات پیشرفته) پاس می‌شوند.

`scripts/check_sources.js` (در CI هم اجرا می‌شود) همه‌ی `R.string.*`ها را با هر دو فایل رشته و رفرنس‌های مانیفست/XML بررسی می‌کند.

نکته‌ی ویندوز: اگر ساخت با خطای `AAPT2 ... Daemon startup failed` متوقف شد، در `local.properties` مسیر aapt2 داخل SDK را بدهید:
```properties
android.aapt2FromMavenOverride=C:/path/to/android-sdk/build-tools/35.0.0/aapt2.exe
```
روی لینوکس/CI این مشکل معمولاً پیش نمی‌آید.

### نسخه‌ی ویندوز / لینوکس (ابزار `radx`)
ماژول `desktop` همان `ProxyLinkParser`/`ConfigImporter`/`SingBoxConfigBuilder`/`XrayConfigBuilder` اپ را کامپایل می‌کند، پس هر دو نسخه یک کانفیگ می‌سازند.
```bash
./gradlew :desktop:installDist
# خروجی: desktop/build/install/radx/  (به همراه پوشه‌ی cores/ و radx.cmd)
radx import https://example.com/sub --name main
radx nodes --profile 1
radx ping --select-fastest
radx gen --mode proxy --out ./out --cores ./cores --check
radx up  --mode proxy --cores ./cores            # تا Ctrl+C
```
حالت `proxy` روی `127.0.0.1:7890` (SOCKS + HTTP) گوش می‌دهد و نیازی به دسترسی مدیر ندارد؛ حالت `tun` کل سیستم را می‌گیرد و باید در شل Administrator اجرا شود. هسته‌ها با `--cores`، `RADX_CORES`، پوشه‌ی `cores/` کنار ابزار یا `PATH` پیدا می‌شوند و وضعیت در `%USERPROFILE%\.radx` (یا `RADX_HOME`) ذخیره می‌شود. تنظیمات پیشرفته‌ی اپ روی این ابزار با `--mtu`، `--block-quic`، `--no-bypass-lan` و `--log-level` در دسترس‌اند.

---

## ۶. ساخت و انتشار APK در GitHub Releases

### الف) امضا (یک‌بار)
```bash
keytool -genkeypair -v -keystore radx-release.jks -alias radx \
  -keyalg RSA -keysize 2048 -validity 10000
base64 -w0 radx-release.jks > radx-release.jks.b64   # macOS: base64 -i ... -o ...
```
در GitHub → Settings → Secrets and variables → Actions چهار Secret بسازید:

| Secret | مقدار |
| --- | --- |
| `KEYSTORE_BASE64` | خروجی base64 فایل keystore |
| `KEYSTORE_PASSWORD` | رمز keystore |
| `KEY_ALIAS` | `radx` |
| `KEY_PASSWORD` | رمز کلید |

اگر Secretها نباشند، workflow با کلید debug امضا می‌کند (نصب‌شدنی، اما برای انتشار/به‌روزرسانی مناسب نیست).

### ب) تگ و انتشار
```bash
# CHANGELOG.md را به‌روزرسانی کنید، سپس:
git add .
git commit -m "release: v1.0.0"
git tag v1.0.0
git push origin main --tags
```
workflow `Build and Release APK` اجرا می‌شود و:
1. `scripts/check_sources.js` را اجرا می‌کند،
2. `libbox.aar` را با gomobile می‌سازد (با کش بر اساس نسخه sing-box)،
3. `gradle :app:assembleRelease` را با کلید release اجرا می‌کند،
4. APK را با نام `Radx-v1.0.0.apk` به Release تگ می‌چسباند و بخش مربوطه‌ی `CHANGELOG.md` را به‌عنوان متن Release می‌گذارد،
5. در کار `windows` بسته‌ی `Radx-Windows-v1.0.0.zip` (شامل `radx.cmd`، هسته‌های ویندوز و `wintun.dll`) ساخته، با یک تست دودوی واقعی بررسی و به همان Release آپلود می‌شود.

اجرای دستی بدون تگ: از تب Actions → `Build and Release APK` → Run workflow (نسخه اختیاری). در این حالت فقط Artifact ساخته می‌شود و Release ایجاد نمی‌شود.

برای انتشار، تگ باید با الگوی `v*` باشد (`v1.0.0`، `v1.1.0`، …).

---

## محدودیت‌های نسخه ۱.۳.۰

- **قواعد مسیریابی Clash/sing-box ایمپورت نمی‌شوند**؛ همه نودها در یک selector و همه ترافیک از `proxy` می‌رود (LAN مستقیم است). ایمپورت `rules`/`proxy-groups` در نقشه راه است.
- **هسته Clash Meta همراه نیست**؛ فقط sing-box. گزینه‌ی Clash در تنظیمات نمایش داده می‌شود اما غیرفعال است.
- پروتکل‌هایی که هیچ‌کدام از دو هسته ندارند (مثل `ssr://`) پشتیبانی نمی‌شوند و با پیام خطا رد می‌شوند.
- در مسیر Xray، `allowInsecure` پشتیبانی نمی‌شود (خود Xray آن را حذف کرده)؛ اگر گواهی سرور معتبر نباشد نود وصل نمی‌شود مگر لینک `pcs` داشته باشد.
- نودهای `vless`/`trojan` روی ترنسپورت Xray که **بدون TLS** و روی آدرس عمومی باشند، خود هسته‌ی Xray 26.x رد می‌کند؛ Radx آن‌ها را از هر دو هسته کنار می‌گذارد و دلیلش را در تب لاگ می‌نویسد (آدرس‌های محلی/خصوصی مستثنا هستند).
- ترافیک تقریبی است (شمارنده UID، نه شمارنده‌ی هسته).
- تقسیم APK بر اساس ABI انجام نمی‌شود؛ AAR هر چهار ABI را دارد و حجم APK بیشتر است.
- minify خاموش است تا اولین نسخه پایدار بدون ریسک باشد.

## نقشه راه
1. ایمپورت `rules`/`proxy-groups` Clash و route rules سفارشی.
2. Clash API داخلی برای سوییچ زنده‌ی نود بدون ری‌استارت + نمایش اتصال‌های فعال.
3. آمار دقیق از هسته (`connection tracker`)، تست تأخیر واقعی per-proxy.
4. پروفایل جدا برای هر اپ (include/exclude package)، Always-on و Quick Settings tile.
5. هسته‌ی دوم (Clash Meta/mihomo) با همان لایه‌ی `CoreEngine`.
