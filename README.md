# Ilova Bloklagich (Android)

Tanlangan ilovani Google Play Market'dan yuklab olishni va ishlatishni cheklaydigan dastur.
Android 8.0 va undan yangi telefonlarda ishlaydi. Tashqi kutubxonalar yo'q, internetga hech narsa yubormaydi.

## Ikki rejim

| | Oddiy rejim (Accessibility) | Device Owner rejimi |
|---|---|---|
| Sozlash | Faqat telefonda, 1 daqiqa | Bir marta kompyuter + adb kerak |
| Play Market'da ilova sahifasi ochilsa | Ustidan "Taqiqlangan" ekrani chiqadi | Xuddi shunday (Accessibility ham yoqilgan bo'lsa) |
| Ilova baribir o'rnatilsa (brauzer, APK fayl) | O'chirish oynasi va bildirishnoma chiqadi | **Bir necha soniyada avtomatik o'chiriladi** |
| Bloklangan ilovani ochish | To'siladi | Ilova muzlatiladi, keyin o'chiriladi |
| Bloklagichni o'chirib qo'yish | Mumkin (Accessibility'ni o'chirsa yoki ilovani o'chirsa) | **Mumkin emas** (faqat PIN bilan, ilova ichidan) |

Ishonchli himoya kerak bo'lsa (bola telefoni, ishxona telefonlari) — Device Owner rejimidan foydalaning.

---

## 1. APK'ni yig'ish

**A) Android Studio bilan (kompyuter):** papkani `File → Open` orqali oching, so'ng `Build → Build App Bundle(s) / APK(s) → Build APK(s)`.

**B) GitHub orqali (Android Studio shart emas):**
1. github.com'da yangi repozitoriy oching va shu papkadagi barcha fayllarni yuklang (`.github` papkasi ham bo'lishi shart).
2. **Actions** bo'limida "APK yig'ish" avtomatik ishga tushadi (3–5 daqiqa).
3. Tugagach, shu ish sahifasining pastidagi **Artifacts → IlovaBloklagich-apk** dan APK'ni yuklab oling (zip ichida).

## 2. O'rnatish va oddiy rejimni yoqish

1. APK'ni telefonga o'rnating ("Noma'lum manbalardan o'rnatish"ga ruxsat bering).
2. Ilovani oching → **"Accessibility sozlamalarini ochish"** → "Ilova Bloklagich" → yoqing.
3. **Android 13 va undan yangi:** "Cheklangan sozlama" degan xabar chiqsa:
   Sozlamalar → Ilovalar → Ilova Bloklagich → **⋮** (yuqori o'ng burchak) → **"Cheklangan sozlamalarga ruxsat berish"**, keyin 2-qadamni qaytaring.
4. Ilovani qo'shing:
   - Play Market'da ilovani toping → **Ulashish** → havolani nusxalang → Bloklagichga qo'ying;
   - **"Play Market'dagi nomi"** maydoniga ilova nomini Play Market'da qanday yozilgan bo'lsa shunday yozing (masalan `Instagram`). Bu nom sahifani aniqlash uchun kerak.
5. **PIN o'rnating** — aks holda telefonni ushlagan odam ro'yxatni o'zgartira oladi.

## 3. Device Owner rejimini yoqish (bir martalik)

Kerak bo'ladi: kompyuter, USB kabel va [Android Platform Tools](https://developer.android.com/tools/releases/platform-tools) (`adb`).

1. Telefonda: **Sozlamalar → Telefon haqida → "Build raqami"ni 7 marta bosing** → Dasturchi rejimi yoqiladi.
   Dasturchi sozlamalarida **"USB orqali tuzatish" (USB debugging)** ni yoqing.
   *Xiaomi/Redmi/POCO'da "USB orqali tuzatish (Xavfsizlik sozlamalari)"ni ham yoqing.*
2. **Barcha hisoblarni vaqtincha o'chiring** (Sozlamalar → Hisoblar: Google, Samsung, Xiaomi va h.k.).
   Android bu buyruqni telefonda hisob bor paytda bajarishga ruxsat bermaydi. Ish profili yoki qo'shimcha foydalanuvchi bo'lsa, ularni ham o'chiring.
3. Ilova Bloklagich telefonga o'rnatilgan bo'lsin, so'ng kompyuterda:
   ```
   adb shell dpm set-device-owner uz.appblocker/.AdminReceiver
   ```
   `Success: Device owner set to package uz.appblocker` chiqishi kerak.
4. Hisoblaringizni qayta qo'shing. Ilovani ochsangiz, "Device Owner rejimi: faol" yoziladi.

*Kompyuter bo'lmasa:* Android 11+ da Dasturchi sozlamalaridagi **"Simsiz tuzatish" (Wireless debugging)** va LADB kabi ilova orqali xuddi shu buyruqni telefonning o'zida bajarish mumkin.

### Device Owner rejimidagi qo'shimcha cheklovlar (ixtiyoriy)
Ilova ichida yoqib/o'chirish mumkin:
- Zavod sozlamalariga qaytarishni taqiqlash
- Xavfsiz rejim (Safe mode)ni taqiqlash
- Sozlamalarda ilovalarni o'chirish, majburan to'xtatish va ma'lumotini tozalashni taqiqlash

### Device Owner'dan chiqish
Ilova → (PIN) → **"Device Owner rejimidan chiqish"**. Shundan so'ng Bloklagichni oddiy usulda o'chirish mumkin.

### PIN unutilsa
Kompyuterda (USB debugging yoqilgan holda):
```
adb shell am broadcast -a uz.appblocker.RESET_PIN -n uz.appblocker/.ResetReceiver
```
Bu buyruqni faqat adb bajara oladi — telefondagi boshqa ilovalar PIN'ni o'chira olmaydi.

---

## Qanday ishlaydi

- **BlockerAccessibilityService** — Play Market ekranidagi matnlarni telefon ichida tekshiradi. Sahifada ilova nomi **va** "O'rnatish / Install / Установить" tugmasi bo'lsa, ustidan bloklash ekranini ochadi. Bloklangan ilova ochilganda ham shu ekran chiqadi.
- **GuardService** — doimiy xizmat (bildirishnomada "Himoya faol"). Har qanday yo'l bilan yangi ilova o'rnatilsa sezadi: Device Owner bo'lsa ilovani muzlatib, jimgina o'chiradi; aks holda o'chirish oynasini ochadi.
- **AdminReceiver** — Device Owner qabul qiluvchisi; Bloklagichning o'zini o'chirishni taqiqlaydi.
- Tizim ilovalari (Play Market, Sozlamalar, bosh ekran, qo'ng'iroqlar) telefon qulflanib qolmasligi uchun bloklanmaydi.

## Cheklovlar
- Play Market boshqa tilda (masalan, nemischa) bo'lsa, "O'rnatish" tugmasi matnini `BlockerAccessibilityService.kt` dagi `INSTALL_WORDS` ro'yxatiga qo'shing.
- Oddiy rejimda bilimli foydalanuvchi Accessibility'ni o'chirib qo'yishi mumkin — shuning uchun Device Owner tavsiya etiladi.
- Bu ilova Google Play'ga joylash uchun mo'ljallanmagan (Accessibility va QUERY_ALL_PACKAGES ruxsatlari Play siyosatida qattiq tekshiriladi) — to'g'ridan-to'g'ri APK sifatida o'rnatiladi.

## Fayllar
```
app/src/main/java/uz/appblocker/
  MainActivity.kt                  asosiy ekran (ro'yxat, PIN, sozlamalar)
  BlockerAccessibilityService.kt   Play Market va ilova ochilishini to'sish
  GuardService.kt                  o'rnatishni kuzatuvchi doimiy xizmat
  Blocker.kt                       umumiy mantiq, Device Owner amallari
  BlockedActivity.kt               "Taqiqlangan" ekrani
  Receivers.kt                     Admin, Boot, Reset, Uninstall qabul qiluvchilari
  Prefs.kt                         ro'yxat va PIN saqlash
```
