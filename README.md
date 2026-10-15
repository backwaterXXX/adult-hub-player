# Adult Hub TV

> **A modern, privacy-focused Android TV streaming client tailored specifically for the [Adult Hub](https://github.com/backwaterXXX/adult-hub) ecosystem.**

Adult Hub TV is an open-source media player designed from the ground up for the big screen. It is customized specifically as the dedicated TV player for the [Adult Hub](https://github.com/backwaterXXX/adult-hub) backend ecosystem (combining Jellyfin media libraries and synchronized video channel feeds).

If your Adult Hub server is located in a different network, behind a firewall, or on a remote host, you can easily connect to it using a secure reverse proxy (or the included container setup in [deploy/local-proxy/](deploy/local-proxy/)).

---

## 📺 Highlights & Features

### 🛋️ Built for the Couch (10-Foot UI)
- **Native Android TV Experience**: Powered by Jetpack Compose for TV with high-contrast visuals, smooth focus transitions, and clear typography.
- **100% Remote-Friendly**: Navigate effortlessly using any standard TV remote (D-pad: Up, Down, Left, Right, OK, Back). No virtual mouse or touchscreen required.

### 🎬 Your Media Collection, Organized
- **Full Library Browsing**: Browse by Movies, Categories, Performers (Actors), and Studios.
- **Detailed Information**: View posters, cast lists, performer biographies, genres, durations, and filmographies.
- **Watch Progress & History**: Pick up right where you left off with "Continue Watching" and full local playback history.

### 📡 Video Channels & Feeds
- **Channel Streams**: Seamlessly browse synchronized channel playlists and video streams directly on your TV via the companion Resolver proxy.
- **Direct Playback**: Stream videos instantly without manual downloading or external media players.

### ⚡ Powerful, Reliable Video Player
- **ExoPlayer Engine**: Powered by AndroidX Media3 for hardware-accelerated, buttery-smooth playback.
- **Smart Decoder Fallback**: Automatically switches to software decoding if your TV processor (e.g., Amlogic/Mali) encounters an unsupported hardware audio/video profile.
- **Live Playback HUD**: View real-time technical stream details (codecs, resolution, bitrate, buffer health, dropped frames) in an unobtrusive corner overlay without pausing your media.
- **Customizable Buffering**: Choose between *Fast Start* (instant playback), *Standard*, or *Large Buffer* (for slower Wi-Fi networks).
- **Audio & Subtitle Selection**: Easily switch language tracks and subtitle options during playback.

### 🎲 Smart Discovery
- **"Surprise Me" (Instant Shuffle)**: Play a random movie immediately when you cannot decide what to watch.
- **Filtered Shuffles**: Shuffle by performer, category, or unwatched movies only.

### 🔒 Privacy by Design
- **Quick Panic Exit**: Press the Back button twice from the home screen to quickly close the app.
- **On-Device Keystore Encryption**: Server credentials and access tokens are securely encrypted using the Android KeyStore before saving to disk.
- **1-Click Data Clearing**: Easily clear your watch history, search queries, or saved channel progress in Settings.
- **Content Controls**: Hide specific channels or exclude tags from appearing in your recommendations.

### 🔄 In-App Updates
- **Automatic Update Checks**: The app automatically checks for new releases on GitHub.
- **Direct TV Installation**: Download and install updates directly on your TV with a single click—no need to connect a computer or re-sideload.

---

## 🚀 Installation Guide

Adult Hub TV runs on any device running **Android TV 9.0 (API 28) or newer** (e.g., Chromecast with Google TV, Xiaomi TV Box, Nvidia Shield, TCL, Sony, Philips, and other Android/Google TV sets).

### Method 1: Using the "Downloader" App (Easiest)
1. Install the free **Downloader by AFTVnews** app from the Google Play Store on your TV.
2. In your TV's *Settings → Apps → Special app access → Install unknown apps*, allow **Downloader** to install apps.
3. Open Downloader, enter the direct URL to the latest APK from the [Releases](https://github.com/backwaterXXX/adult-hub-tv/releases/latest) page, and select **Go**.
4. Once downloaded, click **Install**.

### Method 2: Sideloading via USB Drive or File Transfer
1. Download the latest `AdultHubTV-vX.X.X.apk` from the [Releases](https://github.com/backwaterXXX/adult-hub-tv/releases/latest) section on your computer or phone.
2. Transfer the APK file to your TV using a USB flash drive or an app like **Send Files to TV** (SFTV).
3. Open a file manager on your TV (such as *FX File Explorer* or *AnExplorer*) and open the APK to install.

### Method 3: Using ADB (For Advanced Users)
Connect your computer to your TV over Wi-Fi or USB debugging:
```sh
adb connect <your-tv-ip>:5555
adb install -r AdultHubTV-vX.X.X.apk
```

---

## 🛠️ Quick Start & Setup

### 1. Connect to Jellyfin
When you first launch the app, choose how to connect to your Jellyfin server:
- **Quick Connect (Recommended)**: Select *Quick Connect*, open Jellyfin on your phone or computer, go to *User Settings → Quick Connect*, and enter the 6-digit code shown on your TV screen.
- **Manual Login**: Enter your Jellyfin server address (e.g., `https://jellyfin.example.com` or local `http://192.168.1.50:8096`), your username, and your password.
  > [!NOTE]
  > Unencrypted HTTP is permitted for local network IP addresses (`192.168.x.x`, `10.x.x.x`) with your explicit approval in the app dialog. For servers accessed over the internet, HTTPS is required.

### 2. Choose Your Library
Select the media library you want to browse (e.g., Movies). You can switch libraries anytime from the Settings menu.

### 3. (Optional) Configure Channels Resolver
If you use the channel streaming integration:
1. Open the **Settings** tab.
2. Enter your Resolver service address under **Resolver URL**.
3. Return to the **Library** screen and navigate to the **Channels** tab to explore your feeds.

### 4. (Optional) Connecting Across Different Networks via Proxy
If your **Adult Hub** server runs on a different local network, on a remote server, or behind a firewall:
- You can route traffic securely over HTTPS using any reverse proxy (e.g. Caddy, Nginx, or Cloudflare Tunnels).
- A lightweight, ready-to-run Caddy proxy setup is included in [deploy/local-proxy/](deploy/local-proxy/) to bridge both your Jellyfin and Resolver endpoints to your Android TV.

---

## 🎮 Remote Control Cheatsheet

| Button | Action in Menus / Browsing | Action During Video Playback |
|---|---|---|
| **D-Pad Center (OK)** | Select / Open item | Toggle Play / Pause; show on-screen controls |
| **D-Pad Left / Right** | Move focus horizontally | Seek backward / forward (configurable 10s or 15s) |
| **D-Pad Up / Down** | Scroll vertical sections and rows | Open Quick Controls (Audio, Subtitles, Stats HUD) |
| **Back Button** | Return to previous screen (Press twice on Home to exit) | Close on-screen controls / Exit player |

---

## ❓ Frequently Asked Questions (FAQ)

<details>
<summary><strong>Does this app send my personal data or viewing history anywhere?</strong></summary>
<br>
No. Adult Hub TV communicates only with the server addresses you explicitly enter (your Jellyfin instance and your optional Resolver proxy). There are no third-party trackers, no analytics SDKs, and no telemetry. All playback history, search history, and credentials are stored strictly locally on your TV and protected by the Android KeyStore.
</details>

<details>
<summary><strong>Can I use unencrypted HTTP on my home network?</strong></summary>
<br>
Yes. While HTTPS is strongly encouraged, you can connect to local network addresses (such as <code>http://192.168.1.100:8096</code>) by confirming the in-app consent dialog. Public internet domains require HTTPS for security.
</details>

<details>
<summary><strong>Videos are stuttering or buffering on my TV. What can I do?</strong></summary>
<br>
Open <strong>Settings</strong> in the app:
1. Switch the <strong>Buffer Profile</strong> to <em>Large Buffer</em> (30s / 120s) to give the player more headroom on unstable Wi-Fi.
2. Enable <strong>Low RAM Mode</strong> if your TV device has limited system memory (e.g., 1.5 GB RAM devices).
3. Check the live stats HUD during playback to see whether the video is Direct Playing or Transcoding.
</details>

---

## 💻 Building from Source (For Developers)

<details>
<summary><strong>Click to expand build, test, and release instructions</strong></summary>

### Prerequisites
- **JDK 17** (Temurin or OpenJDK recommended)
- **Android SDK 35** with build-tools `35.0.0` or newer
- Git

### Build APKs
Clone the repository and run Gradle:
```sh
# Build debug APK
./gradlew assembleDebug

# Build optimized release APK (minified with R8)
./gradlew assembleRelease
```

### Release Signing
Release builds use a local `keystore.properties` file in the project root:
```properties
storeFile=release.jks
storePassword=your_keystore_password
keyAlias=your_key_alias
keyPassword=your_key_password
```
*(See `keystore.properties.example` for details. Never commit keystores or passwords to version control.)*

### Running Tests & Linting
```sh
# Run unit tests
./gradlew testDebugUnitTest

# Run Android Lint static analysis
./gradlew lintDebug
```

### Architecture & Tech Stack
- **UI**: Jetpack Compose for TV (`androidx.tv:tv-material`, `androidx.compose.ui`)
- **Player**: AndroidX Media3 ExoPlayer (`androidx.media3:media3-exoplayer`)
- **Jellyfin Integration**: Jellyfin Kotlin SDK (`org.jellyfin.sdk:jellyfin-core`)
- **Dependency Injection**: Koin (`io.insert-koin:koin-androidx-compose`)
- **Networking**: Retrofit 2 + OkHttp 4 + Kotlinx Serialization
- **Storage**: Jetpack DataStore Preferences + Android KeyStore crypto
- **Image Loading**: Coil Compose

</details>

---

## 📄 License & Acknowledgements

- **License**: This project is licensed under the **Apache License 2.0** — see the [LICENSE](LICENSE) file for details.
- **Third-Party Libraries**: For open-source acknowledgements and third-party license notices (including LGPL-3.0 disclosures for the precompiled Jellyfin Kotlin SDK), see [NOTICE](NOTICE).
