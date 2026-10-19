<div align="center">
  <img src="docs/icon-512.png" width="128" height="128" alt="HyperBoost Icon" />
  <h1>HyperBoost</h1>
  <p><b>Усилитель громкости и эквалайзер для Android в стиле Liquid Glass с поддержкой Xiaomi Hyper Island</b></p>
  <p><i>Liquid Glass Audio Booster & Multi-Band Equalizer for Android with Xiaomi Hyper Island support</i></p>

  <p>
    <a href="#-русский">🇷🇺 Русский</a> •
    <a href="#-english">🇬🇧 English</a> •
    <a href="https://github.com/MrQuster/HyperBoost/releases/latest">📥 Скачать APK (Releases)</a>
  </p>
</div>

---

## 🇷🇺 Русский

**HyperBoost** (`io.github.mrquster.hyperboost`) — современный усилитель звука и DSP-эквалайзер для Android 10–17+, написанный на **Kotlin** и **Jetpack Compose**. Приложение вообще не запрашивает доступ в интернет (`0 сетевых разрешений`), автоматически подстраивается под оболочку смартфона и поддерживает управление прямо из **Суперострова Xiaomi (Hyper Island)** с шагом `−10%` / `+10%`.

### ✨ Основные возможности
- **Два аудиодвижка:**
  1. *Мягкий режим (Session 0):* системный каскад `LoudnessEnhancer + DynamicsProcessing` с нулевой задержкой.
  2. *Захват звука (HyperOS Fix):* перехват PCM-потока через `AudioPlaybackCapture` с 64-битным умножением и защитой от щелчков.
- **Управление из Суперострова (Hyper Island) и Live Updates:** компактная индикация у выреза камеры и кнопки `−10%`, `+10%` и `Выкл`.
- **Многополосный эквалайзер и DSP:** встроенные и пользовательские пресеты, `BassBoost`, `Virtualizer`, баланс каналов, Моно и опциональный мягкий лимитер (`tanh`).
- **Индивидуальные профили устройств:** автосохранение уровня усиления отдельно для динамиков, Bluetooth-наушников и USB-ЦАП.
- **Дизайн Liquid Glass:** интерактивная стеклянная линза на базе AGSL `RuntimeShader`, 3D-параллакс от акселерометра, темы Dark / Light / AMOLED / Material You.

### 📲 Как установить из GitHub Releases
1. Перейдите в раздел [**Releases**](https://github.com/MrQuster/HyperBoost/releases/latest) и скачайте файл `HyperBoost-vX.Y.Z.apk`.
2. Откройте скачанный APK на телефоне и при запросе разрешите установку из вашего браузера или проводника.
3. Если Google Play Protect выведет окно *«Неизвестный разработчик»*, нажмите **«Подробнее» → «Всё равно установить»**.
4. *(Опционально)* Проверьте контрольную сумму файла на ПК:
   ```bash
   sha256sum -c HyperBoost-v1.0.0.apk.sha256
