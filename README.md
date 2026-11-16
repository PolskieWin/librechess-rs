# LibreChess RS

An open-source, lightweight chess app and server with a 100% native Android Canvas GUI, real-time matchmaking, classic board themes, post-game move review, and a base 100 Elo rating system.

Made by Polskie and Gemini.

---

## Features

- 100% Native Android GUI: Hardware-accelerated Canvas chessboard renderer with sharp vector piece graphics. Zero WebView and zero web wrapper.
- Edge-to-Edge Experience: Fully transparent system status and navigation bars on modern Android (including Android 14, 15, and 16).
- Haptic Feedback: Tactile vibration pulses on moves, captures, castling, and check.
- Move Quality Analyzer: Post-game accuracy calculations, evaluation advantage graphs, and move classifications (Brilliant !!, Best, Good, Inaccuracy, Mistake, Blunder).
- Offline Bot AI: Five difficulty levels (Bot 100, Bot 300, Bot 600, Bot 1000, Bot 1500) using piece-square evaluation tables.
- Base 100 Elo System: All players start at 100 Elo rating. Ratings update live using standard Elo formulas with a floor at 100.
- Simple Player Identification: Enter your player name directly to play and save statistics without passwords.
- Real-Time Matchmaking Server: Fast asynchronous server built with Axum, Tokio, and Shakmaty chess engine validation.
- Minimal Memory Footprint: Server starts in milliseconds and uses under 15MB RAM.
- Board Themes: Emerald Green, Minimal Slate, Warm Wood, and Royal Ocean.
- Appearance: Dark Mode (default) and White Mode toggle in settings.
- Android APK: Ready-to-install signed 49KB native Android package included (`librechess-rs.apk`).

---

## Quick Start on Termux

### Run the Server
From your Termux home directory, execute:
```bash
./server
```

Or from the project directory:
```bash
cd ~/Projects/librechess-rs
./server
```

The server binds to `http://0.0.0.0:8080`.
Open `http://localhost:8080` in your browser.

---

## Termux Permissions Guide

To give full permissions to Termux so the server runs smoothly in the background:

1. Storage Permission:
```bash
termux-setup-storage
```
Tap Allow when prompted on your Android screen.

2. Keep Running in Background (Disable Battery Optimization):
```bash
termux-wake-lock
```
This acquires a wake lock so your matchmaking server stays online even when the screen is turned off.

3. Make Server Executable:
```bash
chmod +x ~/server
chmod +x ~/Projects/librechess-rs/server
```

---

## Android APK

The compiled Android application package is located at:
- `~/librechess-rs.apk`
- `~/Projects/librechess-rs/librechess-rs.apk`

To install directly in Termux:
```bash
termux-open ~/librechess-rs.apk
```

---

## Git Repository Setup

To push this repository to your GitHub account:

```bash
cd ~/Projects/librechess-rs
git remote add origin https://github.com/YOUR_USERNAME/librechess-rs.git
git branch -M main
git push -u origin main
```

---

## License

Open source under the MIT License.
