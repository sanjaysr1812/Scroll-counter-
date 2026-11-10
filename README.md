# ScrollMeter — Native Android Short-Video Swipe Tracker

**Tagline:** *Know Every Swipe. Take Back Your Time.*

ScrollMeter is a native Android application built with **Kotlin**, **Jetpack Compose (Material 3)**, **Android AccessibilityService API**, **Room Database**, **Kotlin Coroutines & StateFlow**, and **Jetpack DataStore Preferences**.

It monitors vertical swipe/scroll navigation events strictly inside:
- **YouTube Shorts** (`com.google.android.youtube`)
- **Instagram Reels** (`com.instagram.android`)

---

## 1. Architecture & Project Structure

```text
ScrollMeter/
├── settings.gradle.kts
├── build.gradle.kts
├── gradle.properties
├── gradle/
│   ├── libs.versions.toml
│   └── wrapper/gradle-wrapper.properties
└── app/
    ├── build.gradle.kts
    ├── proguard-rules.pro
    └── src/
        ├── main/
        │   ├── AndroidManifest.xml
        │   ├── res/
        │   │   ├── xml/accessibility_service_config.xml
        │   │   ├── xml/data_extraction_rules.xml
        │   │   └── values/{strings.xml, themes.xml}
        │   └── java/com/scrollmeter/app/
        │       ├── MainActivity.kt
        │       ├── ScrollMeterApplication.kt
        │       ├── accessibility/
        │       │   ├── ScrollAccessibilityService.kt
        │       │   ├── AccessibilityEventProcessor.kt
        │       │   ├── FeedContextDetector.kt
        │       │   ├── SwipeDetectionEngine.kt
        │       │   ├── DetectionConfidence.kt
        │       │   └── BootCompletedReceiver.kt
        │       ├── data/
        │       │   ├── database/
        │       │   │   ├── ScrollMeterDatabase.kt
        │       │   │   ├── dao/{SwipeEventDao.kt, UsageSessionDao.kt, DailyStatisticsDao.kt, UserSettingsDao.kt}
        │       │   │   └── entities/{SwipeEventEntity.kt, UsageSessionEntity.kt, DailyStatisticsEntity.kt, UserSettingsEntity.kt}
        │       │   └── repository/{ScrollMeterRepository.kt, UserPreferencesDataStore.kt, DiagnosticsStateHolder.kt}
        │       ├── domain/
        │       │   ├── models/Platform.kt
        │       │   └── usecases/{EvaluateLimitThresholdsUseCase.kt, ExportStatisticsCsvUseCase.kt}
        │       ├── notifications/NotificationManager.kt
        │       ├── ui/
        │       │   ├── theme/{Color.kt, Type.kt, Theme.kt}
        │       │   ├── components/{ComposeCharts.kt, TrackingStatusBanner.kt}
        │       │   ├── dashboard/{DashboardScreen.kt, DashboardViewModel.kt}
        │       │   ├── analytics/{AnalyticsScreen.kt, AnalyticsViewModel.kt}
        │       │   ├── history/{HistoryScreen.kt, HistoryViewModel.kt}
        │       │   ├── settings/{SettingsScreen.kt, SettingsViewModel.kt}
        │       │   ├── onboarding/OnboardingScreen.kt
        │       │   ├── diagnostics/{DiagnosticsScreen.kt, DiagnosticsViewModel.kt}
        │       │   └── navigation/ScrollMeterNavGraph.kt
        │       └── utils/{DateTimeUtils.kt, PermissionUtils.kt}
        ├── test/java/com/scrollmeter/app/
        │   ├── accessibility/SwipeDetectionEngineTest.kt
        │   └── utils/DateTimeUtilsTest.kt
        └── androidTest/java/com/scrollmeter/app/
            └── data/ScrollMeterDatabaseTest.kt
```

---

## 2. Technology Stack & Documented Dependency Versions

Defined in `gradle/libs.versions.toml`:
- **Minimum Android SDK:** API 28 (Android 9.0 Pie)
- **Target / Compile Android SDK:** API 35 (Android 15)
- **Kotlin:** `2.0.21` (with KSP `2.0.21-1.0.28`)
- **Android Gradle Plugin (AGP):** `8.7.3`
- **Jetpack Compose BOM:** `2024.11.00` (Material 3, UI, Graphics, Tooling)
- **Navigation Compose:** `2.8.4`
- **Room Database:** `2.6.1` (with explicit `MIGRATION_1_2`)
- **DataStore Preferences:** `1.1.1`
- **Kotlin Coroutines & Flow:** `1.9.0`
- **Vico Charts (Compose M3):** `2.0.0-beta.2` + native Compose Canvas charts

---

## 3. Core Swipe Detection Engine & Honest Limitations

### What Counts as a Confirmed Swipe
One accepted vertical feed-navigation event inside the full-screen YouTube Shorts or Instagram Reels player equals **one confirmed swipe**.

### What Is Explicitly Excluded
- Opening YouTube or Instagram (initial app launch)
- Opening the first Short or Reel without swiping (initial feed entry baseline)
- Horizontal navigation (Instagram Stories, carousels, tab swipes)
- Scrolling comments drawers or engagement sheets
- Scrolling video descriptions or share sheets
- Scrolling search results
- Scrolling profile grids
- Scrolling standard 16:9 YouTube watch pages

### Transparent Detection Limitations
- **Swipe Count vs. Unique Videos Watched:** ScrollMeter counts vertical navigation swipes (both forward and backward vertical feed swipes), not unique video IDs.
- **App Version Changes:** YouTube and Instagram frequently update their internal view hierarchies and obfuscate resource IDs. `FeedContextDetector` combines resource ID token hints with structural full-screen vertical pager geometry (`containerHeightRatio >= 0.80f`) and overlay exclusion checks.
- **Uncertain Events:** When an event inside a full-screen container cannot be reliably distinguished from ordinary scrolling, ScrollMeter classifies it as `UNCERTAIN`. Uncertain events are **never silently added** to your confirmed swipe count; they are reported transparently on the Dashboard and in the **Tracking Diagnostics** screen.
- **OEM Battery Optimization:** Some Android OEMs (Xiaomi MIUI/HyperOS, Samsung OneUI, Oppo ColorOS, Vivo) may suspend background AccessibilityServices unless battery optimization is set to "Unrestricted" for ScrollMeter.
