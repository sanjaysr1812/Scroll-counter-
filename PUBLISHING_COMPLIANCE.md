# ScrollMeter — App Publishing & Policy Compliance Guide

This guide addresses all requirements to publish **ScrollMeter** without encountering **"No reserved or prohibited terms"** validation errors, trademark rejections, or AccessibilityService policy blocks.

---

## 1. Web & App Publishing Metadata ("No Reserved or Prohibited Terms")

Publishing portals reject app titles and descriptions that include reserved platform trademarks (`Google`, `Android`, `Android Studio`, `YouTube`, `Instagram`, `AI Studio`, `Kotlin`, `Jetpack`).

### Compliant Metadata Configured in `metadata.json` & `index.html`
- **App Name:** `ScrollMeter`
- **Short Description (78 chars):**
  `Know every swipe and take back your time with short-video scroll analytics.`
- **Full Description (Zero Reserved or Prohibited Terms):**
  `Know every swipe and take back your time with real-time short-video vertical scroll tracking, daily limits, usage analytics, and offline data storage.`

---

## 2. Store Listing Copy (Trademark-Safe & Policy-Compliant)

When submitting your store listing, use the following copy to avoid Intellectual Property / Impersonation rejections:

- **Title:** `ScrollMeter: Swipe Counter`
- **Short Description:**
  `Track vertical swipes in short-form video feeds, set daily limits, and reclaim your focus—100% offline.`
- **Full Store Description:**
  ```text
  ScrollMeter helps you understand your short-form video browsing habits by counting vertical navigation swipes in real time and estimating time spent in short-video feeds.

  KEY FEATURES
  • Real-Time Swipe Counter: Counts eligible vertical feed-navigation swipes while excluding comments, search results, profile grids, and horizontal carousels.
  • Daily Limits & Gentle Warnings: Set custom daily swipe and time allowances with optional alerts at 50%, 75%, 90%, and 100%.
  • Offline Historical Analytics: View daily, 7-day, 30-day, and hourly swipe distributions stored locally on your device.
  • Transparent Diagnostics: Includes a built-in calibration test and separates confirmed swipes from uncertain transitions.
  • 100% Offline Privacy: ScrollMeter does not request internet permission. Your usage data never leaves your device.

  ACCESSIBILITY SERVICE DISCLOSURE
  ScrollMeter uses the AccessibilityService API strictly after you grant explicit in-app consent and enable the service in system settings. The service is used solely to detect vertical scroll events inside supported short-form video feeds so your daily swipe counter and session timer can update accurately. ScrollMeter never reads personal messages, passwords, or typed text, and never transmits any data off your device.

  DISCLAIMER
  ScrollMeter is an independent digital wellbeing utility and is not affiliated with, endorsed by, or sponsored by any third-party video or social media platform.
  ```

---

## 3. Technical & Policy Fixes Implemented in the Project

1. **`android:isAccessibilityTool="false"` (`accessibility_service_config.xml`):**
   Explicitly declares that ScrollMeter is a digital wellbeing utility rather than an assistive tool for users with disabilities, satisfying API 31+ AccessibilityService policy rules.
2. **Two-Step Prominent Disclosure Dialog (`AccessibilityProminentDisclosureDialog.kt` & `MainActivity.kt`):**
   Presents a mandatory modal disclosure with explicit **Accept & Open Settings** and **Decline** buttons before opening system Accessibility Settings.
3. **Adaptive Launcher Icons (`@mipmap/ic_launcher` & `@mipmap/ic_launcher_round`):**
   Vector adaptive icons (`ic_launcher_background.xml` and `ic_launcher_foreground.xml`) are configured in `AndroidManifest.xml` for release AAB/APK validation.
4. **Backup & Data Extraction Rules (`backup_rules.xml` & `data_extraction_rules.xml`):**
   Prevents cloud backup of local SQLite/DataStore files across all API levels (API 28–35) without manifest merger warnings.
5. **100% Stable Dependencies & Release Signing (`gradle/libs.versions.toml` & `app/build.gradle.kts`):**
   Removed beta third-party chart dependencies in favor of native Compose `Canvas` charts and added `keystore.properties` release signing support.
6. **Data Safety Section Answers:**
   - *Does your app collect or share any of the required user data types?* → **No** (`android.permission.INTERNET` is omitted from `AndroidManifest.xml`).
