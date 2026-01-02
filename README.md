<div align="center">

# ORA

Countdown timers for the events you care about, with a clean UI and a home screen widget. Built for Android in Kotlin.

<p>
  <a href="https://github.com/Yass5002/Ora/releases/latest">
    <img src="https://img.shields.io/github/v/release/Yass5002/Ora?style=for-the-badge&logo=github&label=Latest%20Release&color=A435F0&cacheSeconds=300" alt="Latest Release"/>
  </a>
  <a href="https://github.com/Yass5002/Ora/releases">
    <img src="https://img.shields.io/github/downloads/Yass5002/Ora/total?style=for-the-badge&logo=github&label=Downloads&color=3DDC84&cacheSeconds=300" alt="Downloads"/>
  </a>
  <a href="https://github.com/Yass5002/Ora/blob/main/LICENSE">
    <img src="https://img.shields.io/github/license/Yass5002/Ora?style=for-the-badge&label=License&color=1572B6&logo=mit&cacheSeconds=300" alt="License"/>
  </a>
</p>

<br>

![ORA Demo](https://github.com/Yass5002/Ora/blob/main/ora_screenshots/demo.gif)

</div>

## Overview

ORA is an open-source countdown app for Android. It focuses on fast event creation, clear countdown displays, and a widget that stays up to date without relying on third-party services.

No ads, no trackers, and all data stays on-device.

## Features

- Countdown events for a specific date/time, plus duration-based timers.
- Quick timers via preset chips (e.g. 5m, 15m, 30m, 1h, 1d).
- Per-event colors (presets + hex picker).
- Theme-aware visuals for light and dark mode.
- Swipe actions, animations, and haptics.

### Widget

- Material You-style widget that shows the most important event (pinned items are prioritized).
- Updates every minute, and can switch to per-second updates when an event is near.
- Uses `AlarmManager` with fallbacks, and restores updates after reboot.

### Notifications and reminders

- Optional per-event reminders, automatically rescheduled when events change.
- Optional persistent “live countdown” notification via a foreground service.
- Handles `POST_NOTIFICATIONS` on Android 13+ and guides users for exact alarms on Android 12+ when needed for reliability.

### Data and backup

- Import/export countdowns as JSON.
- Android backup rules included (`backup_rules.xml`).
- Optional cleanup: auto-delete expired, non-pinned events.

## Screenshots

<div align="center">

| Dark mode | Light mode |
| :------: | :--------: |
| <img src="https://raw.githubusercontent.com/Yass5002/Ora/main/ora_screenshots/main_dark.png" width="280px" /> | <img src="https://raw.githubusercontent.com/Yass5002/Ora/main/ora_screenshots/main_light.png" width="280px" /> |
| <img src="https://raw.githubusercontent.com/Yass5002/Ora/main/ora_screenshots/event_detail_dark.png" width="280px" /> | <img src="https://raw.githubusercontent.com/Yass5002/Ora/main/ora_screenshots/event_detail_light.png" width="280px" /> |

<br>

<h4>Home screen widget</h4>

| Dark mode | Light mode |
| :------: | :--------: |
| <img src="https://raw.githubusercontent.com/Yass5002/Ora/main/ora_screenshots/widget_dark.png" width="280px" /> | <img src="https://raw.githubusercontent.com/Yass5002/Ora/main/ora_screenshots/widget_light.png" width="280px" /> |


</div>

## Install

Download the latest APK from GitHub Releases:

<div align="center">
  <a href="https://github.com/Yass5002/Ora/releases/latest">
    <img src="https://img.shields.io/badge/Download%20APK-333?style=for-the-badge&logo=github" alt="Download from GitHub"/>
  </a>
</div>

Planned: F-Droid (submission not live yet).

## Build from source

- Open the project in Android Studio.
- Let Gradle sync.
- Build and run on a device or emulator.

If you run into build issues, please include your Android Studio version, Gradle output, and device/SDK level in the issue.

## Contributing

Issues and pull requests are welcome.

- For bugs: include steps to reproduce, expected behavior, and screenshots/logs if relevant.
- For features: describe the use case first, then the proposed change.
- Keep changes focused (one fix/feature per PR is ideal).

## License

MIT. See `LICENSE`.
