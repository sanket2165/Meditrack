# 💊 Meditrack - Smart Medication Management Android App

[![Android](https://img.shields.io/badge/Platform-Android_API_24+-brightgreen.svg)](https://developer.android.com)
[![Language](https://img.shields.io/badge/Language-Java_%7C_Kotlin-blue.svg)](https://developer.android.com)
[![Database](https://img.shields.io/badge/Database-Room_%7C_Firebase_Firestore-orange.svg)](https://firebase.google.com)
[![License](https://img.shields.io/badge/License-MIT-green.svg)](LICENSE)

**Meditrack** is a feature-rich, intelligent Android application designed to help patients manage their daily medication schedules and allow doctors to remotely monitor patient adherence in real time.

---

## ✨ Key Features

### 👨‍⚕️ Dual User Modes (Patient & Doctor)
- **Patient Dashboard:** Displays today's scheduled medicines, dosage details, adherence progress percentage, stock levels, and prescription notes from doctors.
- **Doctor Portal:** Real-time monitoring of patients' medication progress, SOS emergency notifications, and direct prescription note updates.

### 🔐 Multi-Method Authentication & Security
- **Biometric Login:** Quick and secure login via Fingerprint or Face Authentication using the Android Biometric API.
- **Social & Phone Auth:** Supports Google Sign-In, Firebase Phone OTP authentication, and traditional Email/Password login.

### ⏰ Voice Reminders & Smart Notifications
- **TTS Voice Assistant:** Uses Text-to-Speech to speak aloud medicine names and dosages when reminder alarms trigger.
- **Exact Alarms & Background Services:** Reliable notifications using Android `AlarmManager` and foreground service compatibility.

### 📊 Adherence Tracking & History
- **Interactive Progress:** Circular progress indicator with real-time percentage breakdown (Taken, Pending, Missed).
- **History Logs:** Comprehensive record of past medication compliance stored both locally and in the cloud.
- **Stock Level Alerts:** Automatic stock deduction with low-stock warning banners.

---

## 🛠️ Tech Stack & Architecture

- **Language:** Java & Kotlin
- **UI Toolkit:** Material Design 3, XML Layouts, Jetpack Compose
- **Local Storage:** Room Database (SQLite) with thread-safe Singleton architecture
- **Cloud Backend:** Firebase Cloud Firestore & Firebase Authentication
- **Security:** AndroidX Biometric Library
- **Background Tasks:** AlarmManager, BroadcastReceiver, Foreground Services (TextToSpeech)

---

## 🚀 Getting Started

### Prerequisites
- Android Studio Ladybug (2024.2+) or newer
- Android SDK 35
- JDK 17
- An Android Device or Emulator running Android 7.0 (API 24) or higher

### Setup & Installation
1. **Clone the repository:**
   ```bash
   git clone https://github.com/sanket2165/Meditrack.git
   ```
2. **Open in Android Studio:**
   - Open Android Studio, select **File > Open**, and navigate to the `Meditrack` folder.
3. **Build & Run:**
   - Connect a device/emulator and click the green **Run** (`▶`) button.

---

## 👨‍💻 Developer
Developed with ❤️ by **[Sanket](https://github.com/sanket2165)**
