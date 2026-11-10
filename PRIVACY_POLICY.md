# ScrollMeter Privacy Policy

**Effective Date:** November 10, 2026  
**Application:** ScrollMeter (Android)

## 1. Core Privacy Principle: 100% Offline & Zero Network Access

ScrollMeter is engineered from the ground up so that your usage habits remain strictly on your personal Android device:
- **No Internet Permission:** ScrollMeter's `AndroidManifest.xml` does **not** request `android.permission.INTERNET`. The Android operating system sandbox prevents ScrollMeter from opening network connections or transmitting any data to external servers.
- **No Account Required:** ScrollMeter never asks for an email address, phone number, or cloud login.
- **No Analytics or Advertising SDKs:** ScrollMeter contains zero third-party telemetry, crash-reporting clouds, or advertising trackers.

## 2. Why ScrollMeter Uses the Android AccessibilityService API

Android does not provide a standard system API to count vertical swipe gestures inside third-party short-video feeds. ScrollMeter uses an Android `AccessibilityService` (`ScrollAccessibilityService`) solely after you explicitly enable it in Android System Settings to:
1. Verify whether the active foreground package is **YouTube** (`com.google.android.youtube`) or **Instagram** (`com.instagram.android`).
2. Identify whether the active screen is the **YouTube Shorts** or **Instagram Reels** vertical feed rather than comments, search, profile grids, or messages.
3. Count vertical swipe navigation events and estimate time spent in those short-video feeds.

## 3. What ScrollMeter NEVER Collects or Reads

ScrollMeter **never** collects, reads, logs, or stores:
- Private messages, direct messages (DMs), or chat conversations
- Passwords, credentials, or autofill inputs
- Typed keyboard text (`TYPE_VIEW_TEXT_CHANGED` is not subscribed)
- Comments, captions, usernames, or video titles
- Screenshots or screen recordings
- Contacts, location data, photos, or microphone audio
- Activity from any app other than YouTube and Instagram

## 4. What Data Is Stored Locally on Your Device

ScrollMeter stores only the following structured records inside an offline SQLite (Room) database on your device:
- **Swipe Events:** Platform (`YOUTUBE_SHORTS` or `INSTAGRAM_REELS`), timestamp, local calendar date, hour of day, event type, confidence score, and a non-reversible deduplication hash.
- **Usage Sessions:** Start timestamp, end timestamp, duration in seconds, and platform.
- **Daily Statistics:** Aggregated daily swipe counts and estimated durations per platform.
- **User Settings:** Your configured daily swipe limits, warning thresholds, and theme preference.

## 5. User Control, Export, and Data Deletion

You remain in complete control of your data at all times:
- **Pause or Disable Tracking:** Toggle tracking off inside ScrollMeter Settings, or turn off ScrollMeter in **Android Settings → Accessibility**.
- **Export Data:** Export your daily statistics as a standard `.csv` file to your device storage via the **History** screen.
- **Delete Data:** Delete today's data, delete a selected date range, or permanently wipe all recorded data with a single tap in the **History** screen. Uninstalling ScrollMeter also permanently removes its local database.
