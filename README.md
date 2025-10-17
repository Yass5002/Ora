<div align="center">

# ORA - Countdown to What Matters

**A beautifully crafted, open-source countdown timer that brings your most important events to life with a stunning, pulsing neon glow.**

<p>
  <a href="https://github.com/Yass5002/Ora/releases/latest">
    <img src="https://img.shields.io/github/v/release/Yass5002/Ora?style=for-the-badge&logo=github&label=Latest%20Release&color=A435F0" alt="Latest Release"/>
  </a>
  <a href="https://github.com/Yass5002/Ora/releases">
    <img src="https://img.shields.io/github/downloads/Yass5002/Ora/total?style=for-the-badge&logo=github&label=Downloads&color=3DDC84" alt="Downloads"/>
  </a>
  <a href="https://github.com/Yass5002/Ora/blob/main/LICENSE">
    <img src="https://img.shields.io/github/license/Yass5002/Ora?style=for-the-badge&label=License&color=1572B6" alt="License"/>
  </a>
</p>

</div>

---

ORA isn't just a timer; it's a visual experience. Designed for Android with Kotlin, it focuses on a clean, intuitive UI and powerful features, all while being completely free and ad-free.

### ✨ Features

ORA is packed with thoughtful features designed for a premium user experience:

#### **🎨 Stunning & Customizable UI**
- **Theme-Aware Visuals:** A mesmerizing pulsing neon glow in dark mode and an elegant shimmer in light mode.
- **Custom Colors:** Personalize each countdown with preset colors or a custom hex color picker to match your vibe.
- **Responsive Design:** Enjoy a clean UI with swipe actions, animations, and haptics for a polished feel.

#### **⏱️ Powerful & Flexible Countdowns**
- **Multiple Modes:** Create events for a specific date & time or use duration-based timers (days/hours/minutes).
- **Quick Timers:** Use duration chips (5m, 15m, 30m, 1h, 1d) to start short-term countdowns in a single tap.
- **Smart Defaults:** Auto-generated titles ensure you can create events quickly, even without a name.

#### **🏠 A Widget That Works for You (Material You)**
- **Adaptive & Smart:** The modern widget shows your most important event (pinned are prioritized), with layouts that adapt to show full details, an expired state, or an empty state.
- **Live Updates:** The widget refreshes every minute and automatically speeds up to every second when an event is urgent.
- **Reliable:** Built with `AlarmManager` and fallbacks to work across all Android versions, and automatically refreshes after a device reboot.

#### **🔔 Robust Notifications & Reminders**
- **Per-Event Reminders:** Schedule optional reminders that are automatically managed as you add, edit, or delete events.
- **Persistent Live Countdown:** A configurable foreground service keeps a live countdown in your notification area so you never lose track.
- **Modern Android Ready:** Properly handles runtime `POST_NOTIFICATIONS` permission on Android 13+ and guides users to enable exact alarm permissions on Android 12+ for maximum reliability.

#### **💾 Data & Backup**
- **Import/Export:** Easily back up and restore your countdowns by exporting and importing them as a JSON file.
- **Cloud Backup Ready:** Includes Android backup rules (`backup_rules.xml`) to allow app data to be saved during cloud device backups.
- **Smart Housekeeping:** An optional setting automatically deletes non-pinned expired events to keep your list clean.

#### **❤️ Privacy-First & Open Source**
- **100% Free & Private:** No ads, no trackers, no nonsense. Your data stays on your device.
- **Open Source:** Licensed under the MIT License. Feel free to inspect the code, learn from it, or contribute back to the project.

### 📸 Screenshots

| Dark Mode 🌙 | Light Mode ☀️ |
| :----------: | :-----------: |
| <img src="https://raw.githubusercontent.com/Yass5002/Ora/main/ora_screenshots/main_dark.png" width="280px" /> | <img src="https://raw.githubusercontent.com/Yass5002/Ora/main/ora_screenshots/main_light.png" width="280px" /> |
| <img src="https://raw.githubusercontent.com/Yass5002/Ora/main/ora_screenshots/event_detail_dark.png" width="280px" /> | <img src="https://raw.githubusercontent.com/Yass5002/Ora/main/ora_screenshots/event_detail_light.png" width="280px" /> |

<div align="center">
  <h4>Home Screen Widget</h4>
  <img src="https://raw.githubusercontent.com/Yass5002/Ora/main/ora_screenshots/widget_dark.png" width="280px" />
  <img src="https://raw.githubusercontent.com/Yass5002/Ora/main/ora_screenshots/widget_light.png" width="280px" />
</div>

---

### 🚀 Get ORA

The easiest way to install ORA is by downloading the latest APK from our releases page.

<div align="center">
  <a href="https://github.com/Yass5002/Ora/releases/latest">
    <img src="https://img.shields.io/badge/Download%20Latest%20APK-333?style=for-the-badge&logo=github" alt="Download from GitHub"/>
  </a>
  <br><br>
  <!-- Uncomment this section once ORA is available on F-Droid -->
  <!-- 
  <a href="LINK_TO_F-DROID_ONCE_AVAILABLE">
    <img src="https://img.shields.io/f-droid/v/com.dev.ora?style=for-the-badge&logo=f-droid" alt="Get it on F-Droid"/>
  </a>
  -->
</div>

> **Note:** We plan to submit ORA to F-Droid soon!

---

### 🤝 Contributing

Contributions are what make the open-source community such an amazing place to learn, inspire, and create. Any contributions you make are **greatly appreciated**.

If you have a suggestion that would make this better, please fork the repo and create a pull request. You can also simply open an issue with the tag "enhancement". Don't forget to give the project a star! ⭐

1.  Fork the Project
2.  Create your Feature Branch (`git checkout -b feature/AmazingFeature`)
3.  Commit your Changes (`git commit -m 'Add some AmazingFeature'`)
4.  Push to the Branch (`git push origin feature/AmazingFeature`)
5.  Open a Pull Request

### 📜 License

Distributed under the MIT License. See `LICENSE` for more information.
