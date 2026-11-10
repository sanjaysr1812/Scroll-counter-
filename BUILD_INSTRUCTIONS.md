# ScrollMeter — Build & Release APK / AAB Instructions

## Environment Note

The interactive web preview runs inside a Linux/Node.js environment without the Android SDK/NDK toolchain. Therefore, **an APK or AAB binary is not pre-compiled inside the web container**.

All native project files (`settings.gradle.kts`, `build.gradle.kts`, `gradle/libs.versions.toml`, `app/build.gradle.kts`, `AndroidManifest.xml`, adaptive launcher icons, backup/extraction rules, Kotlin sources, Room schemas/migrations, and JUnit/Espresso tests) are located at the root of this repository and are ready to open and build directly in **Android Studio**.

---

## Step 1: Export the Project

1. **From the ScrollMeter Workbench (Port 3000):** Click **"Download Project Archive"** in the top bar to download `ScrollMeter-Project.tar.gz`.
2. **Or via ZIP / Repository Export:** Download the project ZIP or export to your repository. Because `settings.gradle.kts`, `build.gradle.kts`, and `app/` are placed directly at the project root, Android Studio recognizes the project directory immediately.

---

## Step 2: Open & Sync in Android Studio

1. Install **Android Studio Ladybug (2024.2.1) or newer** with **JDK 17** (`jbr-17`).
2. Select **File → Open...** and choose the extracted `ScrollMeter` root folder.
3. Allow Gradle `8.10.2` to sync.
4. Ensure **Android SDK Platform 35** and **Android SDK Build-Tools 35.0.0** are installed via **Tools → SDK Manager**.

---

## Step 3: Run Unit & Instrumentation Tests

```bash
# Run JVM Unit Tests (SwipeDetectionEngineTest & DateTimeUtilsTest)
./gradlew testDebugUnitTest

# Run Room Database Instrumentation Tests on a connected device or emulator
./gradlew connectedDebugAndroidTest
```

---

## Step 4: Build Debug APK or Signed Release AAB/APK for Publishing

### Debug APK (For Local Device Testing)
```bash
./gradlew assembleDebug
# Output: app/build/outputs/apk/debug/app-debug.apk
```

### Signed Release Bundle (AAB) or APK (For Store Publishing)
1. Generate an upload keystore using Android Studio (**Build → Generate Signed App Bundle / APK**) or `keytool`:
   ```bash
   keytool -genkey -v -keystore release-key.jks -keyalg RSA -keysize 2048 -validity 10000 -alias scrollmeter
   ```
2. Create `keystore.properties` in the project root (never commit your private passwords):
   ```properties
   storeFile=release-key.jks
   storePassword=YOUR_STORE_PASSWORD
   keyAlias=scrollmeter
   keyPassword=YOUR_KEY_PASSWORD
   ```
3. Build the signed Release App Bundle (`.aab`) or Release APK (`.apk`):
   ```bash
   ./gradlew bundleRelease
   # Output: app/build/outputs/bundle/release/app-release.aab

   ./gradlew assembleRelease
   # Output: app/build/outputs/apk/release/app-release.apk
   ```

---

## Step 5: Physical Device Setup & Android 13+ Restricted Settings Note

When sideloading a debug APK via USB on Android 13, 14, or 15 (API 33–35), the OS protects Accessibility Services behind *Restricted Settings*. If the toggle is initially disabled:
1. Open **System Settings → Apps → ScrollMeter → ⋮ (Top-Right Menu) → Allow restricted settings**.
2. Return to **ScrollMeter**, accept the in-app **Prominent Disclosure Dialog**, and enable **ScrollMeter Swipe Counter** in Accessibility Settings.
