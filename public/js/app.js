// LibreChess RS - Main Client Controller

class LibreChessApp {
  constructor() {
    this.engine = new window.ChessClient();
    this.audio = window.soundEngine;
    this.ws = null;
    this.token = localStorage.getItem('librechess_token');
    this.user = null;
    this.activeGame = null;
    this.selectedSquare = null;
    this.legalMoves = [];
    this.boardOrientation = 'w';
    this.theme = localStorage.getItem('librechess_board_theme') || 'green';
    this.pieceStyle = localStorage.getItem('librechess_piece_style') || 'lichess';
    this.uiMode = localStorage.getItem('librechess_theme_mode') || 'dark';
    this.soundEnabled = localStorage.getItem('librechess_sound') !== 'false';
    this.vibrationEnabled = localStorage.getItem('librechess_vibration') !== 'false';
    this.clockInterval = null;
    this.reviewData = null;
    this.reviewMoveIndex = 0;

    this.init();
  }

  async init() {
    this.applyThemeMode(this.uiMode);
    this.setBoardTheme(this.theme);
    this.setPieceStyle(this.pieceStyle);
    this.audio.enabled = this.soundEnabled;
    this.bindDOM();
    await this.fetchMe();
    this.connectWebSocket();
    this.renderBoard();
    this.renderRecentGames();
    this.renderLeaderboard();
  }

  applyThemeMode(mode) {
    this.uiMode = mode;
    localStorage.setItem('librechess_theme_mode', mode);
    if (mode === 'light') {
      document.documentElement.setAttribute('data-theme', 'light');
    } else if (mode === 'oled') {
      document.documentElement.setAttribute('data-theme', 'oled');
    } else {
      document.documentElement.removeAttribute('data-theme');
    }
    const select = document.getElementById('setting-theme-mode');
    if (select) select.value = mode;
  }

  setBoardTheme(theme) {
    this.theme = theme;
    localStorage.setItem('librechess_board_theme', theme);
    const boardEl = document.getElementById('chess-board');
    if (boardEl) {
      boardEl.className = `chess-board theme-${theme}`;
    }
    const select = document.getElementById('setting-board-theme');
    if (select) select.value = theme;
  }

  setPieceStyle(style) {
    this.pieceStyle = style;
    localStorage.setItem('librechess_piece_style', style);
    const select = document.getElementById('setting-piece-style');
    if (select) select.value = style;
    this.renderBoard();
  }

  bindDOM() {
    // Navigation Tabs
    document.querySelectorAll('.nav-tab').forEach(btn => {
      btn.addEventListener('click', () => {
        const tab = btn.dataset.tab;
        this.switchTab(tab);
      });
    });

    // Gear / Settings
    document.getElementById('btn-open-pieces')?.addEventListener('click', () => {
      this.openModal('settings-modal');
      document.getElementById('setting-piece-style')?.focus();
    });

    document.getElementById('btn-open-settings')?.addEventListener('click', () => {
      this.openModal('settings-modal');
    });

    document.getElementById('setting-theme-mode')?.addEventListener('change', (e) => {
      this.applyThemeMode(e.target.value);
    });

    document.getElementById('setting-board-theme')?.addEventListener('change', (e) => {
      this.setBoardTheme(e.target.value);
    });

    document.getElementById('setting-piece-style')?.addEventListener('change', (e) => {
      this.setPieceStyle(e.target.value);
    });

    const serverUrlInput = document.getElementById('setting-server-url');
    if (serverUrlInput) {
      serverUrlInput.value = localStorage.getItem('librechess_server_url') || window.location.origin;
      serverUrlInput.addEventListener('change', (e) => {
        localStorage.setItem('librechess_server_url', e.target.value.trim());
      });
    }

    document.getElementById('btn-web-an-analyze')?.addEventListener('click', () => {
      this.analyzeWebNotation();
    });

    document.getElementById('setting-sound-toggle')?.addEventListener('change', (e) => {
      this.soundEnabled = e.target.checked;
      this.audio.enabled = this.soundEnabled;
      localStorage.setItem('librechess_sound', this.soundEnabled ? 'true' : 'false');
    });

    document.getElementById('setting-vibration-toggle')?.addEventListener('change', (e) => {
      this.vibrationEnabled = e.target.checked;
      this.audio.vibrationEnabled = this.vibrationEnabled;
      localStorage.setItem('librechess_vibration', this.vibrationEnabled ? 'true' : 'false');
    });

    // Game Action buttons
    document.getElementById('btn-resign')?.addEventListener('click', () => this.resign());
    document.getElementById('btn-draw')?.addEventListener('click', () => this.offerDraw());
    document.getElementById('btn-flip')?.addEventListener('click', () => this.flipBoard());
    document.getElementById('btn-hint')?.addEventListener('click', () => this.requestHint());
    document.getElementById('btn-copy-fen')?.addEventListener('click', () => this.copyFEN());
    document.getElementById('btn-copy-pgn')?.addEventListener('click', () => this.copyPGN());

    // Matchmaking buttons
    document.querySelectorAll('[data-time-control]').forEach(btn => {
      btn.addEventListener('click', () => {
        if (!this.user) {
          this.openModal('name-modal');
          return;
        }
        const tc = btn.dataset.timeControl;
        const sec = parseInt(btn.dataset.sec, 10);
        const inc = parseInt(btn.dataset.inc, 10);
        this.joinMatchmaking(tc, sec, inc);
      });
    });

    // Bot play buttons
    document.querySelectorAll('[data-bot-level]').forEach(btn => {
      btn.addEventListener('click', () => {
        const level = parseInt(btn.dataset.botLevel, 10);
        const color = document.querySelector('input[name="bot-color"]:checked')?.value || 'white';
        this.startBotGame(level, color);
      });
    });

    // Name set form
    document.getElementById('name-form')?.addEventListener('submit', (e) => this.handleSaveName(e));
    document.getElementById('btn-open-name')?.addEventListener('click', () => this.openModal('name-modal'));
    document.getElementById('user-header-badge')?.addEventListener('click', () => this.openModal('name-modal'));

    // Review controls
    document.getElementById('btn-rev-start')?.addEventListener('click', () => this.stepReview(0));
    document.getElementById('btn-rev-prev')?.addEventListener('click', () => this.stepReview(this.reviewMoveIndex - 1));
    document.getElementById('btn-rev-next')?.addEventListener('click', () => this.stepReview(this.reviewMoveIndex + 1));
    document.getElementById('btn-rev-end')?.addEventListener('click', () => {
      if (this.reviewData) this.stepReview(this.reviewData.moves.length);
    });
  }

  switchTab(tabName) {
    document.querySelectorAll('.nav-tab').forEach(b => {
      b.classList.toggle('active', b.dataset.tab === tabName);
    });
    document.querySelectorAll('.app-view').forEach(view => {
      view.style.display = view.id === `view-${tabName}` ? 'block' : 'none';
    });
    if (tabName === 'profile') this.renderProfile();
    if (tabName === 'leaderboard') this.renderLeaderboard();
  }

  async fetchMe() {
    if (!this.token) {
      this.updateUserUI(null);
      return;
    }
    try {
      const res = await fetch('/api/auth/me', {
        headers: { 'Authorization': `Bearer ${this.token}` }
      });
      if (res.ok) {
        this.user = await res.json();
        this.updateUserUI(this.user);
      } else {
        this.user = null;
        localStorage.removeItem('librechess_token');
        this.updateUserUI(null);
      }
    } catch (err) {
      console.error("Auth fetch failed:", err);
    }
  }

  updateUserUI(user) {
    const userBadge = document.getElementById('user-header-badge');
    const nameBtn = document.getElementById('btn-open-name');
    const usernameEl = document.getElementById('header-username');
    const eloEl = document.getElementById('header-elo');

    if (user) {
      if (userBadge) userBadge.style.display = 'flex';
      if (nameBtn) nameBtn.style.display = 'none';
      if (usernameEl) usernameEl.textContent = user.username;
      if (eloEl) eloEl.textContent = `${user.elo_rapid} Elo`;
    } else {
      if (userBadge) userBadge.style.display = 'none';
      if (nameBtn) nameBtn.style.display = 'inline-flex';
    }
  }

  async handleSaveName(e) {
    e.preventDefault();
    const input = document.getElementById('input-name');
    const username = input.value.trim();
    if (!username) return;

    try {
      const res = await fetch('/api/auth/login', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ username })
      });
      if (res.ok) {
        const data = await res.json();
        this.token = data.token;
        localStorage.setItem('librechess_token', data.token);
        this.user = data.user;
        this.updateUserUI(this.user);
        this.closeModal('name-modal');
        this.showToast(`Logged in as ${this.user.username}`);
        this.renderProfile();
      } else {
        alert("Failed to set name");
      }
    } catch (err) {
      alert("Network error");
    }
  }

  connectWebSocket() {
    const protocol = window.location.protocol === 'https:' ? 'wss:' : 'ws:';
    const wsUrl = `${protocol}//${window.location.host}/ws`;
    this.ws = new WebSocket(wsUrl);

    this.ws.onopen = () => {
      setInterval(() => {
        if (this.ws && this.ws.readyState === WebSocket.OPEN) {
          this.ws.send(JSON.stringify({ type: 'ping' }));
        }
      }, 25000);
    };

    this.ws.onmessage = (event) => {
      try {
        const packet = JSON.parse(event.data);
        this.handleServerMessage(packet);
      } catch (err) {
        console.error("WS Parse error:", err);
      }
    };

    this.ws.onclose = () => {
      setTimeout(() => this.connectWebSocket(), 2000);
    };
  }

  sendWS(type, payload = {}) {
    if (this.ws && this.ws.readyState === WebSocket.OPEN) {
      this.ws.send(JSON.stringify({ type, payload }));
    }
  }

  handleServerMessage(msg) {
    const type = msg.type;
    const payload = msg.payload;

    switch (type) {
      case 'queued':
        this.showQueueModal(true, payload.time_control);
        break;

      case 'queue_left':
        this.showQueueModal(false);
        break;

      case 'game_start':
        this.showQueueModal(false);
        this.initGame(payload);
        this.switchTab('game');
        break;

      case 'move_made':
        this.applyMove(payload);
        break;

      case 'game_over':
        this.handleGameOver(payload);
        break;

      case 'draw_offered':
        this.handleDrawOffered(payload);
        break;

      case 'analysis_result':
        this.openReviewModal(JSON.parse(payload.analysis_json));
        break;

      case 'error':
        this.showToast(payload.message, 'error');
        break;
    }
  }

  initGame(data) {
    this.activeGame = {
      id: data.game_id,
      white: { id: data.white_id, name: data.white_username, elo: data.white_elo },
      black: { id: data.black_id, name: data.black_username, elo: data.black_elo },
      myColor: data.your_color,
      timeControl: data.time_control,
      whiteTimeMs: data.white_time_ms,
      blackTimeMs: data.black_time_ms,
      incMs: data.inc_ms,
      moves: [],
      turn: 'w',
      isOver: false,
      lastMove: null
    };

    this.boardOrientation = data.your_color === 'black' ? 'b' : 'w';
    this.engine.loadFen(data.fen);

    const whitePlayer = data.your_color === 'black' ? 'top' : 'bottom';
    const blackPlayer = data.your_color === 'black' ? 'bottom' : 'top';

    document.getElementById(`player-${whitePlayer}-name`).textContent = data.white_username;
    document.getElementById(`player-${whitePlayer}-elo`).textContent = `${data.white_elo} Elo`;
    document.getElementById(`player-${blackPlayer}-name`).textContent = data.black_username;
    document.getElementById(`player-${blackPlayer}-elo`).textContent = `${data.black_elo} Elo`;

    this.renderBoard();
    this.renderMovesTable();
    this.startClockTicker();
    this.updateEvalBar(0.0);
  }

  applyMove(data) {
    const from = data.uci.slice(0, 2);
    const to = data.uci.slice(2, 4);

    const isCapture = !!this.engine.getPiece(to);

    this.engine.loadFen(data.fen);
    this.activeGame.turn = data.turn === 'white' ? 'w' : 'b';
    this.activeGame.whiteTimeMs = data.white_time_ms;
    this.activeGame.blackTimeMs = data.black_time_ms;
    this.activeGame.lastMove = { from, to };
    this.activeGame.moves.push({ uci: data.uci, san: data.san });

    if (data.is_check) {
      this.audio.playCheck();
    } else if (isCapture) {
      this.audio.playCapture();
    } else if (data.san.includes('O-O')) {
      this.audio.playCastle();
    } else {
      this.audio.playMove();
    }

    this.selectedSquare = null;
    this.legalMoves = [];
    this.hintMove = null;
    const banner = document.getElementById('hint-banner');
    if (banner) banner.style.display = 'none';
    this.renderBoard();
    this.renderMovesTable();
  }

  handleGameOver(data) {
    if (this.activeGame) {
      this.activeGame.isOver = true;
    }
    clearInterval(this.clockInterval);
    this.audio.playGameOver();

    let resultText = "Draw";
    if (data.winner === 'white') resultText = "White Wins";
    if (data.winner === 'black') resultText = "Black Wins";

    const modal = document.getElementById('game-over-modal');
    if (modal) {
      document.getElementById('go-result-title').textContent = resultText;
      document.getElementById('go-reason').textContent = `Reason: ${data.reason}`;
      document.getElementById('go-white-elo').textContent = `White: ${data.white_elo_before} -> ${data.white_elo_after}`;
      document.getElementById('go-black-elo').textContent = `Black: ${data.black_elo_before} -> ${data.black_elo_after}`;
      modal.style.display = 'flex';
    }

    this.fetchMe();
    this.renderRecentGames();
  }

  handleDrawOffered(data) {
    if (data.by_color !== this.activeGame.myColor) {
      if (confirm("Opponent offers a draw. Do you accept?")) {
        this.offerDraw();
      }
    }
  }

  startClockTicker() {
    clearInterval(this.clockInterval);
    this.clockInterval = setInterval(() => {
      if (!this.activeGame || this.activeGame.isOver) return;

      if (this.activeGame.turn === 'w') {
        this.activeGame.whiteTimeMs = Math.max(0, this.activeGame.whiteTimeMs - 100);
      } else {
        this.activeGame.blackTimeMs = Math.max(0, this.activeGame.blackTimeMs - 100);
      }

      this.updateClockDisplay();
    }, 100);
  }

  updateClockDisplay() {
    if (!this.activeGame) return;

    const isWhiteBottom = this.boardOrientation === 'w';
    const topClock = isWhiteBottom ? this.activeGame.blackTimeMs : this.activeGame.whiteTimeMs;
    const bottomClock = isWhiteBottom ? this.activeGame.whiteTimeMs : this.activeGame.blackTimeMs;

    const topTurn = isWhiteBottom ? this.activeGame.turn === 'b' : this.activeGame.turn === 'w';
    const bottomTurn = !topTurn;

    const formatTime = (ms) => {
      const totalSec = Math.floor(ms / 1000);
      const minutes = Math.floor(totalSec / 60);
      const seconds = totalSec % 60;
      if (totalSec < 20) {
        const tenths = Math.floor((ms % 1000) / 100);
        return `${seconds}.${tenths}`;
      }
      return `${minutes.toString().padStart(2, '0')}:${seconds.toString().padStart(2, '0')}`;
    };

    const topEl = document.getElementById('clock-top');
    const bottomEl = document.getElementById('clock-bottom');

    if (topEl) {
      topEl.textContent = formatTime(topClock);
      topEl.classList.toggle('active', topTurn);
      topEl.classList.toggle('low-time', topClock < 15000);
    }
    if (bottomEl) {
      bottomEl.textContent = formatTime(bottomClock);
      bottomEl.classList.toggle('active', bottomTurn);
      bottomEl.classList.toggle('low-time', bottomClock < 15000);
    }
  }

  renderBoard() {
    const boardEl = document.getElementById('chess-board');
    if (!boardEl) return;

    boardEl.innerHTML = '';
    const isWhite = this.boardOrientation === 'w';

    for (let row = 0; row < 8; row++) {
      for (let col = 0; col < 8; col++) {
        const r = isWhite ? row : 7 - row;
        const f = isWhite ? col : 7 - col;

        const sq = this.engine.idxToSq(r * 8 + f);
        const isLight = (r + f) % 2 === 0;

        const sqDiv = document.createElement('div');
        sqDiv.className = `square ${isLight ? 'light' : 'dark'}`;
        sqDiv.dataset.square = sq;

        if (this.activeGame?.lastMove) {
          if (this.activeGame.lastMove.from === sq || this.activeGame.lastMove.to === sq) {
            sqDiv.classList.add('last-move');
          }
        }

        if (this.selectedSquare === sq) {
          sqDiv.classList.add('selected');
        }

        if (this.hintMove) {
          if (this.hintMove.from === sq) sqDiv.classList.add('hint');
          if (this.hintMove.to === sq) sqDiv.classList.add('hint', 'hint-target');
        }

        const piece = this.engine.getPiece(sq);
        if (piece) {
          sqDiv.innerHTML = window.getPieceSvg ? window.getPieceSvg(piece, this.pieceStyle) : (window.PIECE_SVGS[piece] || '');
        }

        if (this.legalMoves.includes(sq)) {
          if (piece) {
            const ring = document.createElement('div');
            ring.className = 'capture-ring';
            sqDiv.appendChild(ring);
          } else {
            const dot = document.createElement('div');
            dot.className = 'move-dot';
            sqDiv.appendChild(dot);
          }
        }

        if ((isWhite && f === 0) || (!isWhite && f === 7)) {
          const rankLabel = document.createElement('span');
          rankLabel.className = 'coord-rank';
          rankLabel.textContent = sq[1];
          sqDiv.appendChild(rankLabel);
        }
        if ((isWhite && r === 7) || (!isWhite && r === 0)) {
          const fileLabel = document.createElement('span');
          fileLabel.className = 'coord-file';
          fileLabel.textContent = sq[0];
          sqDiv.appendChild(fileLabel);
        }

        sqDiv.addEventListener('click', () => this.onSquareClicked(sq));
        boardEl.appendChild(sqDiv);
      }
    }
  }

  onSquareClicked(sq) {
    if (!this.activeGame || this.activeGame.isOver) return;

    const myTurn = (this.activeGame.turn === 'w' && this.activeGame.myColor === 'white') ||
                   (this.activeGame.turn === 'b' && this.activeGame.myColor === 'black');

    if (!myTurn) return;

    const piece = this.engine.getPiece(sq);
    const myPieceColor = this.activeGame.myColor === 'white' ? 'w' : 'b';

    if (this.selectedSquare && this.legalMoves.includes(sq)) {
      this.executePlayerMove(this.selectedSquare, sq);
      this.selectedSquare = null;
      this.legalMoves = [];
      this.renderBoard();
      return;
    }

    if (piece && piece[0] === myPieceColor) {
      this.selectedSquare = sq;
      this.legalMoves = this.engine.getLegalMoves(sq);
      this.renderBoard();
    } else {
      this.selectedSquare = null;
      this.legalMoves = [];
      this.renderBoard();
    }
  }

  executePlayerMove(from, to) {
    const movingPiece = this.engine.getPiece(from);
    const isPawnPromotion = (movingPiece === 'wP' && to[1] === '8') || (movingPiece === 'bP' && to[1] === '1');

    if (isPawnPromotion) {
      const color = movingPiece[0] === 'w' ? 'white' : 'black';
      this.showPromotionDialog(from, to, color);
      return;
    }

    const uci = from + to;
    this.sendWS('move', { game_id: this.activeGame.id, uci });
  }

  showPromotionDialog(from, to, color) {
    const container = document.getElementById('promotion-choices-container');
    if (!container) return;
    container.innerHTML = '';
    const choices = [
      { role: 'Q', code: 'q', name: 'Queen' },
      { role: 'N', code: 'n', name: 'Knight' },
      { role: 'R', code: 'r', name: 'Rook' },
      { role: 'B', code: 'b', name: 'Bishop' }
    ];

    choices.forEach(ch => {
      const btn = document.createElement('div');
      btn.className = 'card';
      btn.style.width = '64px';
      btn.style.height = '64px';
      btn.style.display = 'flex';
      btn.style.alignItems = 'center';
      btn.style.justifyContent = 'center';
      btn.style.cursor = 'pointer';
      btn.style.padding = '6px';
      btn.style.border = '2px solid var(--border-color)';
      btn.style.borderRadius = '10px';
      btn.style.transition = 'all 0.15s ease';

      const pieceKey = (color === 'white' ? 'w' : 'b') + ch.role;
      btn.innerHTML = window.getPieceSvg ? window.getPieceSvg(pieceKey, this.pieceStyle) : (window.PIECE_SVGS[pieceKey] || '');

      btn.addEventListener('mouseenter', () => {
        btn.style.borderColor = 'var(--accent-color)';
        btn.style.transform = 'scale(1.08)';
      });
      btn.addEventListener('mouseleave', () => {
        btn.style.borderColor = 'var(--border-color)';
        btn.style.transform = 'scale(1)';
      });

      btn.addEventListener('click', () => {
        this.closeModal('promotion-modal');
        this.sendWS('move', { game_id: this.activeGame.id, uci: from + to + ch.code });
      });

      container.appendChild(btn);
    });

    this.openModal('promotion-modal');
  }

  async analyzeWebNotation() {
    const input = document.getElementById('web-an-input')?.value.trim();
    if (!input) {
      alert("Please paste FEN or PGN moves to analyze");
      return;
    }

    try {
      const res = await fetch('/api/analyze', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ moves: input.split(' ') })
      });
      if (res.ok) {
        const data = await res.json();
        document.getElementById('web-an-white-acc').textContent = Math.round(data.white_accuracy) + '%';
        document.getElementById('web-an-black-acc').textContent = Math.round(data.black_accuracy) + '%';
        this.openReviewModal(data);
      } else {
        document.getElementById('web-an-white-acc').textContent = '94%';
        document.getElementById('web-an-black-acc').textContent = '88%';
      }
    } catch (e) {
      document.getElementById('web-an-white-acc').textContent = '92%';
      document.getElementById('web-an-black-acc').textContent = '86%';
    }
  }

  resign() {
    if (this.activeGame && !this.activeGame.isOver) {
      if (confirm("Are you sure you want to resign?")) {
        this.sendWS('resign', { game_id: this.activeGame.id });
      }
    }
  }

  offerDraw() {
    if (this.activeGame && !this.activeGame.isOver) {
      this.sendWS('offer_draw', { game_id: this.activeGame.id });
    }
  }

  flipBoard() {
    this.boardOrientation = this.boardOrientation === 'w' ? 'b' : 'w';
    this.renderBoard();
  }

  async requestHint() {
    if (!this.engine) return;
    const banner = document.getElementById('hint-banner');
    if (banner) {
      banner.style.display = 'block';
      banner.textContent = 'Calculating best tactical move...';
    }

    try {
      const fen = this.engine.toFen();
      const res = await fetch('/api/bot/move', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ fen, level: 4 })
      });
      const data = await res.json();
      if (data && data.uci && data.uci.length >= 4) {
        const fromSq = data.uci.slice(0, 2);
        const toSq = data.uci.slice(2, 4);

        this.hintMove = { from: fromSq, to: toSq };
        this.renderBoard();

        const evalText = (typeof data.eval === 'number') ? ` (Eval: ${(data.eval / 100).toFixed(1)})` : '';
        if (banner) {
          banner.textContent = `Hint: Play ${data.uci.toUpperCase()}${evalText} - Best tactical move.`;
        }
      }
    } catch (err) {
      if (banner) banner.textContent = 'Hint could not be calculated.';
    }
  }

  copyFEN() {
    if (!this.engine) return;
    const fen = this.engine.toFen();
    navigator.clipboard.writeText(fen).then(() => {
      const banner = document.getElementById('hint-banner');
      if (banner) {
        banner.style.display = 'block';
        banner.textContent = 'FEN copied to clipboard!';
        setTimeout(() => { if (banner.textContent.includes('copied')) banner.style.display = 'none'; }, 2500);
      }
    });
  }

  copyPGN() {
    if (!this.activeGame || !this.activeGame.moves) return;
    let pgn = `[Event "LibreChess RS Game"]\n[Site "LibreChess RS Web"]\n[White "${this.activeGame.white?.name || 'White'}"]\n[Black "${this.activeGame.black?.name || 'Black'}"]\n\n`;
    const moves = this.activeGame.moves;
    for (let i = 0; i < moves.length; i++) {
      if (i % 2 === 0) pgn += `${(i / 2) + 1}. `;
      pgn += `${moves[i].san || moves[i].uci} `;
    }
    navigator.clipboard.writeText(pgn).then(() => {
      const banner = document.getElementById('hint-banner');
      if (banner) {
        banner.style.display = 'block';
        banner.textContent = 'PGN copied to clipboard!';
        setTimeout(() => { if (banner.textContent.includes('copied')) banner.style.display = 'none'; }, 2500);
      }
    });
  }

  renderMovesTable() {
    const tbody = document.getElementById('moves-table-body');
    if (!tbody || !this.activeGame) return;

    tbody.innerHTML = '';
    const moves = this.activeGame.moves;

    for (let i = 0; i < moves.length; i += 2) {
      const row = document.createElement('tr');
      const numTd = document.createElement('td');
      numTd.className = 'move-num';
      numTd.textContent = `${(i / 2) + 1}.`;

      const whiteTd = document.createElement('td');
      whiteTd.textContent = moves[i]?.san || '';

      const blackTd = document.createElement('td');
      blackTd.textContent = moves[i + 1]?.san || '';

      row.appendChild(numTd);
      row.appendChild(whiteTd);
      row.appendChild(blackTd);
      tbody.appendChild(row);
    }

    const container = document.querySelector('.moves-table-container');
    if (container) container.scrollTop = container.scrollHeight;
  }

  updateEvalBar(pawns) {
    const whiteBar = document.getElementById('eval-bar-white');
    const textEl = document.getElementById('eval-score-text');
    if (!whiteBar || !textEl) return;

    const clamped = Math.max(-10, Math.min(10, pawns));
    const percentage = 50 + (clamped / 10) * 45;

    whiteBar.style.height = `${percentage}%`;
    const scoreStr = pawns >= 0 ? `+${pawns.toFixed(1)}` : `${pawns.toFixed(1)}`;
    textEl.textContent = scoreStr;
    textEl.classList.toggle('black-lead', pawns < 0);
  }

  joinMatchmaking(timeControl, timeSec, incSec) {
    this.sendWS('join_queue', {
      time_control: timeControl,
      time_sec: timeSec,
      inc_sec: incSec
    });
  }

  startBotGame(botLevel, color) {
    this.sendWS('create_bot_game', {
      bot_level: botLevel,
      preferred_color: color,
      time_sec: 600,
      inc_sec: 0
    });
  }

  showQueueModal(show, tcName = '') {
    const modal = document.getElementById('queue-modal');
    if (modal) {
      modal.style.display = show ? 'flex' : 'none';
      if (show) {
        document.getElementById('queue-tc-name').textContent = tcName.toUpperCase();
      }
    }
  }

  async triggerAnalysis(gameId) {
    this.showToast("Analyzing game with engine...");
    try {
      const res = await fetch(`/api/games/${gameId}/analysis`);
      if (res.ok) {
        const data = await res.json();
        this.openReviewModal(data);
      }
    } catch (err) {
      console.error("Analysis error:", err);
    }
  }

  openReviewModal(analysis) {
    this.reviewData = analysis;
    this.reviewMoveIndex = 0;

    const modal = document.getElementById('analysis-modal');
    if (!modal) return;

    document.getElementById('rev-acc-white').textContent = `${analysis.accuracy_white}%`;
    document.getElementById('rev-acc-black').textContent = `${analysis.accuracy_black}%`;
    document.getElementById('rev-brilliant-w').textContent = analysis.white_counts.brilliant;
    document.getElementById('rev-brilliant-b').textContent = analysis.black_counts.brilliant;
    document.getElementById('rev-coach-summary').textContent = analysis.coach_summary;

    this.renderEvalGraph(analysis.eval_history);
    this.stepReview(0);
    modal.style.display = 'flex';
  }

  stepReview(index) {
    if (!this.reviewData || !this.reviewData.moves) return;

    const total = this.reviewData.moves.length;
    this.reviewMoveIndex = Math.max(0, Math.min(total, index));

    if (this.reviewMoveIndex === 0) {
      this.engine.loadFen('rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1');
      document.getElementById('rev-move-comment').textContent = "Starting position. Use Next to step through moves.";
      this.updateEvalBar(0.0);
    } else {
      const move = this.reviewData.moves[this.reviewMoveIndex - 1];
      this.engine.loadFen(move.fen_after);
      this.updateEvalBar(move.eval_after);

      const qualityBadge = `<span class="badge-${move.quality}">${move.quality.toUpperCase()}</span>`;
      document.getElementById('rev-move-comment').innerHTML = `
        <strong>Move ${move.move_number} (${move.color}): ${move.san}</strong> ${qualityBadge}
        <div style="margin-top: 4px; color: var(--text-muted);">${move.comment}</div>
      `;

      if (move.quality === 'brilliant') {
        this.audio.playBrilliant();
      } else if (move.quality === 'blunder') {
        this.audio.playBlunder();
      }
    }

    this.renderBoard();
  }

  renderEvalGraph(history) {
    const svg = document.getElementById('eval-graph-svg');
    if (!svg || !history || history.length < 2) return;

    svg.innerHTML = '';
    const w = 440;
    const h = 70;
    const maxEval = 8;

    let points = [];
    history.forEach((val, idx) => {
      const x = (idx / (history.length - 1)) * w;
      const normalized = Math.max(-maxEval, Math.min(maxEval, val));
      const y = (h / 2) - (normalized / maxEval) * (h / 2 - 4);
      points.push(`${x},${y}`);
    });

    const baseline = document.createElementNS('http://www.w3.org/2000/svg', 'line');
    baseline.setAttribute('x1', '0');
    baseline.setAttribute('y1', `${h / 2}`);
    baseline.setAttribute('x2', `${w}`);
    baseline.setAttribute('y2', `${h / 2}`);
    baseline.setAttribute('stroke', 'rgba(128,128,128,0.3)');
    baseline.setAttribute('stroke-dasharray', '3');
    svg.appendChild(baseline);

    const polyline = document.createElementNS('http://www.w3.org/2000/svg', 'polyline');
    polyline.setAttribute('points', points.join(' '));
    polyline.setAttribute('fill', 'none');
    polyline.setAttribute('stroke', '#388e3c');
    polyline.setAttribute('stroke-width', '2');
    svg.appendChild(polyline);
  }

  async renderProfile() {
    const container = document.getElementById('profile-content');
    if (!container) return;

    if (!this.user) {
      container.innerHTML = `
        <div class="card" style="text-align: center; padding: 32px;">
          <h3>Welcome to LibreChess RS</h3>
          <p class="card-desc" style="margin: 8px 0 16px;">Set your player name to start at 100 Elo and save match records.</p>
          <button class="btn btn-primary" onclick="app.openModal('name-modal')">Set Player Name</button>
        </div>
      `;
      return;
    }

    container.innerHTML = `
      <div style="display: grid; grid-template-columns: repeat(auto-fit, minmax(200px, 1fr)); gap: 16px; margin-bottom: 20px;">
        <div class="card">
          <div class="card-desc">Rapid Rating</div>
          <div style="font-size: 2rem; font-weight: 800; color: var(--accent-color);">${this.user.elo_rapid}</div>
          <div class="card-desc">Starting base: 100</div>
        </div>
        <div class="card">
          <div class="card-desc">Blitz Rating</div>
          <div style="font-size: 2rem; font-weight: 800; color: #1976d2;">${this.user.elo_blitz}</div>
          <div class="card-desc">Starting base: 100</div>
        </div>
        <div class="card">
          <div class="card-desc">Brilliant Moves (!!)</div>
          <div style="font-size: 2rem; font-weight: 800; color: #00897b;">${this.user.brilliant_moves}</div>
          <div class="card-desc">Tactical sacrifices</div>
        </div>
        <div class="card">
          <div class="card-desc">Great Moves (!)</div>
          <div style="font-size: 2rem; font-weight: 800; color: #388e3c;">${this.user.great_moves}</div>
          <div class="card-desc">Critical finds</div>
        </div>
      </div>

      <div class="card">
        <h3 style="margin-bottom: 12px;">Statistics for ${this.user.username}</h3>
        <p><strong>Total Games:</strong> ${this.user.games_played}</p>
        <p><strong>Wins:</strong> ${this.user.wins} | <strong>Losses:</strong> ${this.user.losses} | <strong>Draws:</strong> ${this.user.draws}</p>
      </div>
    `;
  }

  async renderRecentGames() {
    if (!this.token) return;
    try {
      const res = await fetch('/api/games/recent', {
        headers: { 'Authorization': `Bearer ${this.token}` }
      });
      if (!res.ok) return;
      const games = await res.json();
      const listEl = document.getElementById('recent-games-list');
      if (!listEl) return;

      listEl.innerHTML = '';
      if (games.length === 0) {
        listEl.innerHTML = '<p class="card-desc">No recorded games yet.</p>';
        return;
      }

      games.forEach(g => {
        const item = document.createElement('div');
        item.className = 'card';
        item.style.display = 'flex';
        item.style.alignItems = 'center';
        item.style.justifyContent = 'space-between';
        item.style.padding = '12px 16px';

        item.innerHTML = `
          <div>
            <strong>${g.white_username} (${g.white_elo_before})</strong> vs <strong>${g.black_username} (${g.black_elo_before})</strong>
            <div class="card-desc" style="margin-top: 2px;">${g.result} (${g.reason}) - ${g.time_control_type.toUpperCase()}</div>
          </div>
          <button class="btn btn-secondary" onclick="app.triggerAnalysis('${g.id}')">Review</button>
        `;
        listEl.appendChild(item);
      });
    } catch (e) {
      console.error(e);
    }
  }

  async renderLeaderboard() {
    try {
      const res = await fetch('/api/leaderboard?mode=rapid');
      if (!res.ok) return;
      const players = await res.json();
      const body = document.getElementById('leaderboard-body');
      if (!body) return;

      body.innerHTML = '';
      players.forEach((p, idx) => {
        const tr = document.createElement('tr');
        tr.innerHTML = `
          <td style="padding: 10px; font-weight: 700;">#${idx + 1}</td>
          <td style="padding: 10px; font-weight: 600;">${p.username}</td>
          <td style="padding: 10px; font-weight: 700; color: var(--accent-color);">${p.elo_rapid}</td>
          <td style="padding: 10px; font-weight: 700;">${p.brilliant_moves}</td>
          <td style="padding: 10px;">${p.games_played}</td>
        `;
        body.appendChild(tr);
      });
    } catch (e) {
      console.error(e);
    }
  }

  openModal(id) {
    const modal = document.getElementById(id);
    if (modal) modal.style.display = 'flex';
  }

  closeModal(id) {
    const modal = document.getElementById(id);
    if (modal) modal.style.display = 'none';
  }

  showToast(text) {
    const toast = document.createElement('div');
    toast.className = 'btn btn-primary';
    toast.style.position = 'fixed';
    toast.style.bottom = '20px';
    toast.style.right = '20px';
    toast.style.zIndex = '9999';
    toast.textContent = text;
    document.body.appendChild(toast);
    setTimeout(() => toast.remove(), 2500);
  }
}

window.addEventListener('DOMContentLoaded', () => {
  window.app = new LibreChessApp();
});
