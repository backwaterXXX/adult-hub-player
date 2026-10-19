# Adult Hub TV and Mobile

> **A modern, privacy-focused Android streaming client for TV and mobile, tailored for the [Adult Hub](https://github.com/backwaterXXX/adult-hub) ecosystem.**

Adult Hub provides separate Android TV and Android mobile applications. Both connect to the same Adult Hub backend and provide library browsing, search, synchronized video channels, playback, and settings. The TV app is designed for a remote and large screen; the mobile app uses touch controls and adapts to portrait and landscape layouts.

If your Adult Hub server is located in a different network, behind a firewall, or on a remote host, you can easily connect to it using a secure reverse proxy (or the included container setup in [deploy/local-proxy/](deploy/local-proxy/)).

---

## 📺 Highlights & Features

### 📱 Two Android Apps
- **Adult Hub TV**: A large-screen interface designed for Android TV and remote control.
- **Adult Hub Mobile**: A touch-friendly interface with bottom navigation, portrait and landscape layouts, and a mobile video player.
- **Shared capabilities**: Sign-in, library browsing, search, channels, playback, settings, and privacy controls are available in both apps.

### 🛋️ Built for the Couch (10-Foot UI)
- **Native Android TV Experience**: Powered by Jetpack Compose for TV with high-contrast visuals, smooth focus transitions, and clear typography.
- **100% Remote-Friendly**: Navigate effortlessly using any standard TV remote (D-pad: Up, Down, Left, Right, OK, Back). No virtual mouse or touchscreen required.

### 🎬 Your Media Collection, Organized
- **Full Library Browsing**: Browse by Movies, Categories, Performers (Actors), and Studios.
- **Detailed Information**: View posters, cast lists, performer biographies, genres, durations, and filmographies.
- **Watch Progress & History**: Pick up right where you left off with "Continue Watching" and full local playback history.

### 📡 Video Channels & Feeds
- **Channel Streams**: Browse synchronized channel playlists and video streams on TV and mobile via the companion Resolver proxy.
- **Direct Playback**: Stream videos instantly without manual downloading or external media players.

### ⚡ Powerful, Reliable Video Player
- **ExoPlayer Engine**: Powered by AndroidX Media3 for hardware-accelerated, buttery-smooth playback.
- **Smart Decoder Fallback**: Automatically switches to software decoding if an Android device encounters an unsupported hardware audio/video profile.
- **TV Live Playback HUD**: View real-time technical stream details (codecs, resolution, bitrate, buffer health, dropped frames) in an unobtrusive corner overlay without pausing your media.
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
- **TV Automatic Update Checks**: The TV app automatically checks for new releases on GitHub.
- **Direct TV Installation**: Download and install updates directly on your TV with a single click—no need to connect a computer or re-sideload.

---

## 🚀 Installation Guide

Adult Hub TV runs on **Android TV 9.0 (API 28) or newer** (e.g., Chromecast with Google TV, Xiaomi TV Box, Nvidia Shield, TCL, Sony, Philips, and other Android/Google TV sets). Adult Hub Mobile runs on **Android 9 (API 28) or newer** phones and tablets.

Each GitHub release contains separate APKs for TV (`AdultHubTV-vX.apk`) and mobile (`AdultHubMobile-vX.apk`). Install the APK that matches your device. Mobile uses a separate package and can be installed alongside the TV app.

### Method 1: Using the "Downloader" App (Easiest)
1. Install the free **Downloader by AFTVnews** app from the Google Play Store on your TV.
2. In your TV's *Settings → Apps → Special app access → Install unknown apps*, allow **Downloader** to install apps.
3. Open Downloader, enter the direct URL to the latest APK from the [Releases](https://github.com/backwaterXXX/adult-hub-player/releases/latest) page, and select **Go**.
4. Once downloaded, click **Install**.

On a phone or tablet, download `AdultHubMobile-vX.apk` from the [Releases](https://github.com/backwaterXXX/adult-hub-player/releases/latest) page, open it from your Downloads app, and allow your file manager or browser to install unknown apps if Android prompts you.

### Method 2: Sideloading via USB Drive or File Transfer
1. Download the latest `AdultHubTV-vX.apk` from the [Releases](https://github.com/backwaterXXX/adult-hub-player/releases/latest) section on your computer or phone.
2. Transfer the APK file to your TV using a USB flash drive or an app like **Send Files to TV** (SFTV).
3. Open a file manager on your TV (such as *FX File Explorer* or *AnExplorer*) and open the APK to install.

### Method 3: Using ADB on TV (For Advanced Users)
Connect your computer to your TV over Wi-Fi or USB debugging:
```sh
adb connect <your-tv-ip>:5555
adb install -r AdultHubTV-vX.apk
```

---

## 🛠️ Quick Start & Setup

### 1. Connect to Jellyfin
When you first launch the app, choose how to connect to your Jellyfin server:
- **Quick Connect (Recommended)**: Select *Quick Connect*, open Jellyfin on your phone or computer, go to *User Settings → Quick Connect*, and enter the 6-digit code shown in the app.
- **Manual Login**: Enter your Jellyfin server address (e.g., `https://jellyfin.example.com` or local `http://192.168.1.50:8096`), your username, and your password.
  > [!NOTE]
  > Unencrypted HTTP is permitted for local network IP addresses (`192.168.x.x`, `10.x.x.x`) with your explicit approval in the app dialog. For servers accessed over the internet, HTTPS is required.

### 2. Choose Your Library
Select the media library you want to browse (e.g., Movies). You can switch libraries anytime from the Settings menu.

### 3. Adult Hub stream and channel service
By default, the Resolver origin follows the Jellyfin server address for `.strm` playback, provider channels, and video matching. If your deployment hosts the Resolver separately, set its address under **Settings → Resolver base URL**.

### 4. (Optional) Connecting Across Different Networks via Proxy
If your **Adult Hub** server runs on a different local network, on a remote server, or behind a firewall:
- You can route traffic securely over HTTPS using any reverse proxy (e.g. Caddy, Nginx, or Cloudflare Tunnels).
- A lightweight, ready-to-run Caddy proxy setup is included in [deploy/local-proxy/](deploy/local-proxy/) to bridge both your Jellyfin and Resolver endpoints to your Android device.

---

## 🎮 TV Remote Control Cheatsheet

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
No. Adult Hub communicates only with the server addresses you explicitly enter (your Jellyfin instance and your optional Resolver proxy). There are no third-party trackers, no analytics SDKs, and no telemetry. Playback history, search history, and credentials are stored on your device and credentials are protected by the Android KeyStore.
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
- **Android SDK Platform 35** and **Build Tools 36.0.0**
- Git

### Build APKs
Clone the repository and run Gradle:
```sh
# Build both debug APKs
./gradlew assembleTvDebug assembleMobileDebug

# Build both signed, optimized release APKs (requires keystore.properties)
./gradlew assembleTvRelease assembleMobileRelease
```

### APK Signing
Debug APKs use the Android debug key and install under separate `.debug` application IDs, so they can coexist with production installs. Release APK packaging requires `keystore.properties` and fails instead of falling back to a debug certificate.

To replace an older sideloaded debug-signed production package with the GitHub release, first back up any local app data, uninstall that package, and then install the release APK. Android does not allow an APK signed by a different certificate to update an existing installation.

Generate a long-lived signing key and copy the example properties file:
```sh
keytool -genkeypair -v -keystore release.jks -alias adult-hub-release -keyalg RSA -keysize 4096 -validity 10000
cp keystore.properties.example keystore.properties
```

Set the keystore and key passwords in `keystore.properties`:
```properties
storeFile=release.jks
storePassword=your_keystore_password
keyAlias=adult-hub-release
keyPassword=your_key_password
```
Keep a secure backup of `release.jks`; losing it prevents future APK updates under the same signing identity. Never commit the keystore or `keystore.properties` to version control.

### GitHub Release Signing
The GitHub Actions release workflow reads these repository Actions secrets and variable:
- `ANDROID_KEYSTORE_BASE64`: Base64-encoded `release.jks` (on Linux, run `base64 -w0 release.jks`)
- `ANDROID_KEYSTORE_PASSWORD`
- `ANDROID_KEY_ALIAS` (use `adult-hub-release`)
- `ANDROID_KEY_PASSWORD`
- Repository variable `ANDROID_SIGNING_CERT_SHA256`: SHA-256 fingerprint of the release signing certificate, without colons.

Push a version tag matching `v<versionName>-tv` (for example, `v0.1.69-tv`) to run release signing and publish both APKs. The workflow checks the tag and requires the version code to increase.

### Running Tests & Linting
```sh
# Run unit tests
./gradlew testTvDebugUnitTest testMobileDebugUnitTest

# Run Android Lint static analysis
./gradlew lintTvDebug lintMobileDebug lintTvRelease lintMobileRelease
```

### Architecture & Tech Stack
- **UI**: Jetpack Compose for TV and Material 3 for mobile
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
