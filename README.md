# LibreChess RS

An open-source, lightweight chess server, web application, and Android package with real-time matchmaking, classic board themes, post-game move analysis, and a 100 base Elo rating system.

Made by Polskie and Gemini.

---

## Features

- Lightweight Backend: Fast asynchronous server built with Axum, Tokio, and Shakmaty chess engine validation.
- Minimal Memory Footprint: Starts in milliseconds, uses under 15MB RAM.
- Base 100 Elo System: All players start at 100 Elo rating. Ratings update live using standard Elo formulas with a floor at 100.
- Simple Player Identification: Enter your player name directly to play and save statistics without passwords.
- Real-Time Matchmaking: Live queue for Bullet (1|0), Blitz (3|0), Blitz (5|3), and Rapid (10|0).
- Play vs Bot: Five engine difficulty levels (100 Elo, 500 Elo, 1000 Elo, 1500 Elo, 2500+ Master).
- Move Quality Analyzer: Post-game accuracy calculations, evaluation advantage graphs, and move classifications (Brilliant, Great, Best, Inaccuracy, Mistake, Blunder).
- Board Customization: Classic Green, Warm Wood, Slate Grey, and Ocean Blue board themes.
- Appearance: Dark Mode (default) and White Mode toggle in settings.
- Sound Effects: Synthesized Web Audio API sound effects for moves, captures, checks, castling, and game results.
- Android APK: Ready-to-install Android application package included (`librechess-rs.apk`).

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
