## LibreChess RS v1.2.0 - Material You & Lichess Vector Overhaul

### What's New in v1.2.0

#### 1. Authentic Lichess Vector Piece Rendering
- Embedded authentic Colin M.L. Burnett (cburnett) vector paths directly into native Android Canvas rendering.
- Complete overhaul of piece styling: white pieces feature crisp dark outlines, and black pieces feature rich charcoal bodies (`#1E1B18`) with white interior detail lines (eyes, crosses, crenellations).
- 3 piece styles supported: **Lichess Classic (cburnett)**, **Neo Modern**, and **Tournament Staunton**.
- Added an instant **Pieces** button in the top bar for 1-tap switching of piece styles and board themes with live preview.

#### 2. Vibrant Material You Bot Screen
- Replaced plain dark cards with distinct, elevated Material You tonal containers:
  - **Toby (100 Elo)**: Emerald green container, avatar, beginner pill, casual traits.
  - **Martin (300 Elo)**: Warm amber container, avatar, casual pill, center pawn traits.
  - **Elena (600 Elo)**: Sky cyan container, avatar, club pill, active diagonal traits.
  - **Tactics Bot (1000 Elo)**: Electric violet container, avatar, tactician pill, forks/pins traits.
  - **Magnus (1500 Elo)**: Fiery orange container, avatar, master pill, refutes Scholar's Mate traps.
  - **Grandmaster (2500+ Elo)**: Royal gold container, avatar, titan pill, Stockfish level engine.
- Added a **Play as Side** selector (White, Random, Black). Playing as Black automatically flips the board and triggers the bot to make the opening move as White.

#### 3. Lichess In-Board Promotion UI
- 4-choice vertical overlay directly in the promotion column (Queen, Knight, Rook, Bishop) rendered with the active piece set.
- Complete under-promotion notation (`=N`, `=R`, `=B`, `=Q`) across move history and PGN exports.

#### 4. Time Controls & Clocks
- Presets for **Bullet** (`1|0`, `2|1`), **Blitz** (`3|0`, `5|3`), and **Rapid** (`10|0`, `15|10`) with active countdowns and increment handling.

#### 5. Stockfish Game Analyzer
- In-app analysis view with accuracy scoring for White and Black, tactical move badges (Brilliant `!!`, Best, Blunder `??`, Inaccuracy), coach commentary, and PGN/FEN notation parser.

#### 6. Custom IP Matchmaking
- In-app endpoint configuration allowing connectivity to local LAN or remote custom servers with ping verification.

#### 7. Ultra-Lightweight Footprint
- Signed release APK size: **100 KB** (well within the 1 MB ceiling).
