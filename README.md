# LibreChess RS

An open-source, lightweight chess app and server with a 100% native Android Canvas GUI, real-time matchmaking, classic and OLED board themes, post-game move review, calibrated real chess bots, and a base 100 Elo rating system.

Made by Polskie and Gemini.

---

## Features

- **100% Native Android GUI**: Hardware-accelerated Canvas chessboard renderer with sharp vector piece graphics. Zero WebView and zero web wrapper.
- **Full Dark Mode**: Comprehensive dark theme with Deep Slate and Midnight OLED (pure black) modes across both the web interface and Android native views.
- **Revamped Real Chess Bots**:
  - Calibrated Elo bots: Toby (100), Martin (500), Elena (1000), Magnus (1500), and Grandmaster (2500+).
  - Tactical Minimax with Alpha-Beta pruning, MVV-LVA move ordering, and Quiescence search.
  - Immune to Scholar's Mate and cheap opening traps: Bot 1500 actively defends against 4. Qxf7# threats and punishes premature attacks.
  - Persistent Stockfish 19 UCI integration with automatic fallback to high-speed native pure-Rust Shakmaty search.
- **REST Bot Move API**: Query moves directly via `POST /api/bot/move` with FEN and bot level.
- **Edge-to-Edge Experience**: Fully transparent system status and navigation bars on modern Android (including Android 14, 15, and 16).
- **Haptic Feedback**: Tactile vibration pulses on moves, captures, castling, and check.
- **Move Quality Analyzer**: Post-game accuracy calculations, evaluation advantage graphs, and move classifications (Brilliant !!, Best, Good, Inaccuracy, Mistake, Blunder).
- **Offline Bot AI in Android**: Native Java Minimax Alpha-Beta engine for 100% offline play on your phone.
- **Base 100 Elo System**: All players start at 100 Elo rating. Ratings update live using standard Elo formulas with a floor at 100.
- **Simple Player Identification**: Enter your player name directly to play and save statistics without passwords.
- **Real-Time Matchmaking Server**: Fast asynchronous server built with Axum, Tokio, and Shakmaty chess engine validation.
- **Board Themes**: Emerald Green, Minimal Slate, Warm Wood, Royal Ocean, and Midnight Dark (OLED).
- **Android APK**: Ready-to-install signed native Android package included (`librechess-rs.apk`).

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
