// Client-side Chess Rules & Vector SVG Piece Renderer
const PIECE_SVGS = {
  wK: `<svg viewBox="0 0 45 45" class="piece"><g fill="none" fill-rule="evenodd" stroke="#000" stroke-width="1.5" stroke-linecap="round" stroke-linejoin="round"><path d="M22.5 11.63V6M20 8h5" stroke-linejoin="miter"/><path d="M22.5 25s4.5-7.5 3-10.5c0 0-1-2.5-3-2.5s-3 2.5-3 2.5c-1.5 3 3 10.5 3 10.5" fill="#fff" stroke-linecap="butt" stroke-linejoin="miter"/><path d="M11.5 37c5.5 3.5 15.5 3.5 21 0v-7s9-4.5 6-10.5c-4-6.5-13.5-3.5-16 4V23v-2c-2.5-7.5-12-10.5-16-4-3 6 6 10.5 6 10.5v7z" fill="#fff"/><path d="M11.5 30c5.5-3 15.5-3 21 0m-21 3.5c5.5-3 15.5-3 21 0m-21 3.5c5.5-3 15.5-3 21 0"/></g></svg>`,
  wQ: `<svg viewBox="0 0 45 45" class="piece"><g fill="#fff" fill-rule="evenodd" stroke="#000" stroke-width="1.5" stroke-linecap="round" stroke-linejoin="round"><path d="M8 12a2 2 0 1 1-4 0 2 2 0 1 1 4 0zm16.5-4.5a2 2 0 1 1-4 0 2 2 0 1 1 4 0zM41 12a2 2 0 1 1-4 0 2 2 0 1 1 4 0zm-28 6a2 2 0 1 1-4 0 2 2 0 1 1 4 0zm18.5 0a2 2 0 1 1-4 0 2 2 0 1 1 4 0z"/><path d="M9 26c8.5-1.5 21-1.5 27 0l2-12-7 11V11l-5.5 13.5-3-15-3 15-5.5-13.5V25l-7-11 2 12z" stroke-linecap="butt"/><path d="M9 26c0 2 1.5 2 2.5 4 1 1.5 1 1 .5 3.5-1.5 1-1.5 2.5-1.5 2.5-1.5 1.5.5 2.5.5 2.5 6.5 1 16.5 1 23 0 0 0 2-1 .5-2.5 0 0 0-1.5-1.5-2.5-.5-2.5-.5-2 .5-3.5 1-2 2.5-2 2.5-4-8.5-1.5-18.5-1.5-27 0z" stroke-linecap="butt"/><path d="M11 38.5a35 35 1 0 0 23 0" fill="none"/><path d="M11 29a35 35 1 0 1 23 0M12.5 31.5h20M11.5 34.5a35 35 1 0 0 22 0M10.5 37.5a35 35 1 0 0 24 0" fill="none"/></g></svg>`,
  wR: `<svg viewBox="0 0 45 45" class="piece"><g fill="#fff" fill-rule="evenodd" stroke="#000" stroke-width="1.5" stroke-linecap="round" stroke-linejoin="round"><path d="M9 39h27v-3H9v3zm3-3v-4.5h21V36H12zm2-4.5h17l1.5-13H12.5L14 31.5zm-2.5-15h22l2.5-6.5h-5v3h-3.5v-3h-5v3h-3.5v-3h-5l-2.5 6.5z" stroke-linecap="butt"/><path d="M14 29.5v-13h17v13H14z" fill="none" stroke-linejoin="miter"/><path d="M14 16.5h17M11 14h23" fill="none"/><path d="M12 35.5h21m-20 3h19" fill="none"/></g></svg>`,
  wB: `<svg viewBox="0 0 45 45" class="piece"><g fill="none" fill-rule="evenodd" stroke="#000" stroke-width="1.5" stroke-linecap="round" stroke-linejoin="round"><g fill="#fff" stroke-linecap="butt"><path d="M9 36c3.39-.97 10.11.43 13.5-2 3.39 2.43 10.11 1.03 13.5 2 0 0 1.65.54 3 2-.68.97-1.65.99-3 .5-3.39-.97-10.11.46-13.5-1-3.39 1.46-10.11.03-13.5 1-1.35.49-2.32.47-3-.5 1.35-1.46 3-2 3-2z"/><path d="M15 32c2.5 2.5 12.5 2.5 15 0 .5-1.5 0-2 0-2 0-2.5-2.5-4-2.5-4 5.5-1.5 6-11.5-5-15.5-11 4-10.5 14-5 15.5 0 0-2.5 1.5-2.5 4 0 0-.5.5 0 2z"/><path d="M25 8a2.5 2.5 0 1 1-5 0 2.5 2.5 0 1 1 5 0z"/></g><path d="M17.5 26h10M15 30h15m-7.5-14.5v5M20 18h5" stroke-linejoin="miter"/></g></svg>`,
  wN: `<svg viewBox="0 0 45 45" class="piece"><g fill="none" fill-rule="evenodd" stroke="#000" stroke-width="1.5" stroke-linecap="round" stroke-linejoin="round"><path d="M22 10c10.5 1 16.5 8 16 29H15c0-9 10-6.5 8-21" fill="#fff"/><path d="M24 18c.38 2.91-5.55 7.37-8 9-3 2-2.82 4.34-5 4-1.042-.94 1.41-3.04 0-3-1 0 .19 1.23-1 2-1 0-4.003 1-4-4 0-2 6-12 6-12s1.89-1.9 2-3.5c-.73-.994-.5-2-.5-3 1-1 3 2.5 3 2.5h2s.78-1.992 2.5-3c1 0 1 3 1 3" fill="#fff"/><circle cx="9.5" cy="25.5" r="1.5" fill="#000"/></g></svg>`,
  wP: `<svg viewBox="0 0 45 45" class="piece"><path d="M22.5 9c-2.21 0-4 1.79-4 4 0 .89.29 1.71.78 2.38C17.33 16.5 16 18.59 16 21c0 2.03.94 3.84 2.41 5.03-3 1.06-7.41 5.55-7.41 13.47h23c0-7.92-4.41-12.41-7.41-13.47 1.47-1.19 2.41-3 2.41-5.03 0-2.41-1.33-4.5-3.28-5.62.49-.67.78-1.49.78-2.38 0-2.21-1.79-4-4-4z" fill="#fff" stroke="#000" stroke-width="1.5" stroke-linecap="round"/></svg>`,

  bK: `<svg viewBox="0 0 45 45" class="piece"><g fill="none" fill-rule="evenodd" stroke="#000" stroke-width="1.5" stroke-linecap="round" stroke-linejoin="round"><path d="M22.5 11.63V6M20 8h5" stroke-linejoin="miter"/><path d="M22.5 25s4.5-7.5 3-10.5c0 0-1-2.5-3-2.5s-3 2.5-3 2.5c-1.5 3 3 10.5 3 10.5" fill="#111" stroke-linecap="butt" stroke-linejoin="miter"/><path d="M11.5 37c5.5 3.5 15.5 3.5 21 0v-7s9-4.5 6-10.5c-4-6.5-13.5-3.5-16 4V23v-2c-2.5-7.5-12-10.5-16-4-3 6 6 10.5 6 10.5v7z" fill="#111"/><path d="M11.5 30c5.5-3 15.5-3 21 0m-21 3.5c5.5-3 15.5-3 21 0m-21 3.5c5.5-3 15.5-3 21 0" stroke="#fff"/></g></svg>`,
  bQ: `<svg viewBox="0 0 45 45" class="piece"><g fill="#111" fill-rule="evenodd" stroke="#000" stroke-width="1.5" stroke-linecap="round" stroke-linejoin="round"><path d="M8 12a2 2 0 1 1-4 0 2 2 0 1 1 4 0zm16.5-4.5a2 2 0 1 1-4 0 2 2 0 1 1 4 0zM41 12a2 2 0 1 1-4 0 2 2 0 1 1 4 0zm-28 6a2 2 0 1 1-4 0 2 2 0 1 1 4 0zm18.5 0a2 2 0 1 1-4 0 2 2 0 1 1 4 0z"/><path d="M9 26c8.5-1.5 21-1.5 27 0l2-12-7 11V11l-5.5 13.5-3-15-3 15-5.5-13.5V25l-7-11 2 12z" stroke-linecap="butt"/><path d="M9 26c0 2 1.5 2 2.5 4 1 1.5 1 1 .5 3.5-1.5 1-1.5 2.5-1.5 2.5-1.5 1.5.5 2.5.5 2.5 6.5 1 16.5 1 23 0 0 0 2-1 .5-2.5 0 0 0-1.5-1.5-2.5-.5-2.5-.5-2 .5-3.5 1-2 2.5-2 2.5-4-8.5-1.5-18.5-1.5-27 0z" stroke-linecap="butt"/><path d="M11 38.5a35 35 1 0 0 23 0" fill="none" stroke="#fff"/><path d="M11 29a35 35 1 0 1 23 0M12.5 31.5h20M11.5 34.5a35 35 1 0 0 22 0M10.5 37.5a35 35 1 0 0 24 0" fill="none" stroke="#fff"/></g></svg>`,
  bR: `<svg viewBox="0 0 45 45" class="piece"><g fill="#111" fill-rule="evenodd" stroke="#000" stroke-width="1.5" stroke-linecap="round" stroke-linejoin="round"><path d="M9 39h27v-3H9v3zm3-3v-4.5h21V36H12zm2-4.5h17l1.5-13H12.5L14 31.5zm-2.5-15h22l2.5-6.5h-5v3h-3.5v-3h-5v3h-3.5v-3h-5l-2.5 6.5z" stroke-linecap="butt"/><path d="M14 29.5v-13h17v13H14z" fill="none" stroke="#fff" stroke-linejoin="miter"/><path d="M14 16.5h17M11 14h23" fill="none" stroke="#fff"/><path d="M12 35.5h21m-20 3h19" fill="none" stroke="#fff"/></g></svg>`,
  bB: `<svg viewBox="0 0 45 45" class="piece"><g fill="none" fill-rule="evenodd" stroke="#000" stroke-width="1.5" stroke-linecap="round" stroke-linejoin="round"><g fill="#111" stroke-linecap="butt"><path d="M9 36c3.39-.97 10.11.43 13.5-2 3.39 2.43 10.11 1.03 13.5 2 0 0 1.65.54 3 2-.68.97-1.65.99-3 .5-3.39-.97-10.11.46-13.5-1-3.39 1.46-10.11.03-13.5 1-1.35.49-2.32.47-3-.5 1.35-1.46 3-2 3-2z"/><path d="M15 32c2.5 2.5 12.5 2.5 15 0 .5-1.5 0-2 0-2 0-2.5-2.5-4-2.5-4 5.5-1.5 6-11.5-5-15.5-11 4-10.5 14-5 15.5 0 0-2.5 1.5-2.5 4 0 0-.5.5 0 2z"/><path d="M25 8a2.5 2.5 0 1 1-5 0 2.5 2.5 0 1 1 5 0z"/></g><path d="M17.5 26h10M15 30h15m-7.5-14.5v5M20 18h5" stroke="#fff" stroke-linejoin="miter"/></g></svg>`,
  bN: `<svg viewBox="0 0 45 45" class="piece"><g fill="none" fill-rule="evenodd" stroke="#000" stroke-width="1.5" stroke-linecap="round" stroke-linejoin="round"><path d="M22 10c10.5 1 16.5 8 16 29H15c0-9 10-6.5 8-21" fill="#111"/><path d="M24 18c.38 2.91-5.55 7.37-8 9-3 2-2.82 4.34-5 4-1.042-.94 1.41-3.04 0-3-1 0 .19 1.23-1 2-1 0-4.003 1-4-4 0-2 6-12 6-12s1.89-1.9 2-3.5c-.73-.994-.5-2-.5-3 1-1 3 2.5 3 2.5h2s.78-1.992 2.5-3c1 0 1 3 1 3" fill="#111"/><circle cx="9.5" cy="25.5" r="1.5" fill="#fff"/></g></svg>`,
  bP: `<svg viewBox="0 0 45 45" class="piece"><path d="M22.5 9c-2.21 0-4 1.79-4 4 0 .89.29 1.71.78 2.38C17.33 16.5 16 18.59 16 21c0 2.03.94 3.84 2.41 5.03-3 1.06-7.41 5.55-7.41 13.47h23c0-7.92-4.41-12.41-7.41-13.47 1.47-1.19 2.41-3 2.41-5.03 0-2.41-1.33-4.5-3.28-5.62.49-.67.78-1.49.78-2.38 0-2.21-1.79-4-4-4z" fill="#111" stroke="#000" stroke-width="1.5" stroke-linecap="round"/></svg>`
};

class ChessEngineClient {
  constructor() {
    this.board = Array(64).fill(null);
    this.turn = 'w';
    this.castling = { K: true, Q: true, k: true, q: true };
    this.enPassant = null;
    this.halfMoves = 0;
    this.fullMoves = 1;
    this.loadFen('rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1');
  }

  loadFen(fen) {
    this.board = Array(64).fill(null);
    const parts = fen.split(' ');
    const rows = parts[0].split('/');

    for (let r = 0; r < 8; r++) {
      let file = 0;
      for (const char of rows[r]) {
        if (!isNaN(char)) {
          file += parseInt(char, 10);
        } else {
          const color = char === char.toUpperCase() ? 'w' : 'b';
          const type = char.toUpperCase();
          this.board[r * 8 + file] = color + type;
          file++;
        }
      }
    }

    this.turn = parts[1] || 'w';
    this.castling = {
      K: (parts[2] || '').includes('K'),
      Q: (parts[2] || '').includes('Q'),
      k: (parts[2] || '').includes('k'),
      q: (parts[2] || '').includes('q')
    };
    this.enPassant = parts[3] !== '-' ? parts[3] : null;
    this.halfMoves = parseInt(parts[4] || '0', 10);
    this.fullMoves = parseInt(parts[5] || '1', 10);
  }

  getPiece(square) {
    const idx = typeof square === 'number' ? square : this.sqToIdx(square);
    return this.board[idx];
  }

  idxToSq(idx) {
    const file = String.fromCharCode('a'.charCodeAt(0) + (idx % 8));
    const rank = 8 - Math.floor(idx / 8);
    return file + rank;
  }

  sqToIdx(sq) {
    const file = sq.charCodeAt(0) - 'a'.charCodeAt(0);
    const rank = 8 - parseInt(sq[1], 10);
    return rank * 8 + file;
  }

  getLegalMoves(sq) {
    const fromIdx = typeof sq === 'number' ? sq : this.sqToIdx(sq);
    const piece = this.board[fromIdx];
    if (!piece || piece[0] !== this.turn) return [];

    const moves = [];
    const color = piece[0];
    const type = piece[1];
    const r = Math.floor(fromIdx / 8);
    const f = fromIdx % 8;

    const addMove = (tr, tf) => {
      if (tr < 0 || tr >= 8 || tf < 0 || tf >= 8) return false;
      const targetIdx = tr * 8 + tf;
      const targetPiece = this.board[targetIdx];
      if (!targetPiece) {
        moves.push(this.idxToSq(targetIdx));
        return true;
      } else if (targetPiece[0] !== color) {
        moves.push(this.idxToSq(targetIdx));
        return false;
      }
      return false;
    };

    if (type === 'P') {
      const dir = color === 'w' ? -1 : 1;
      const startRank = color === 'w' ? 6 : 1;

      // 1 square forward
      const f1 = (r + dir) * 8 + f;
      if (!this.board[f1]) {
        moves.push(this.idxToSq(f1));
        // 2 squares forward
        const f2 = (r + 2 * dir) * 8 + f;
        if (r === startRank && !this.board[f2]) {
          moves.push(this.idxToSq(f2));
        }
      }

      // Captures
      for (const df of [-1, 1]) {
        const tf = f + df;
        const tr = r + dir;
        if (tf >= 0 && tf < 8 && tr >= 0 && tr < 8) {
          const capIdx = tr * 8 + tf;
          const capPiece = this.board[capIdx];
          const targetSq = this.idxToSq(capIdx);
          if (capPiece && capPiece[0] !== color) {
            moves.push(targetSq);
          } else if (this.enPassant && this.enPassant === targetSq) {
            moves.push(targetSq);
          }
        }
      }
    } else if (type === 'N') {
      const deltas = [
        [-2, -1], [-2, 1], [-1, -2], [-1, 2],
        [1, -2], [1, 2], [2, -1], [2, 1]
      ];
      for (const [dr, df] of deltas) addMove(r + dr, f + df);
    } else if (type === 'B' || type === 'R' || type === 'Q') {
      const dirs = [];
      if (type !== 'B') dirs.push([-1, 0], [1, 0], [0, -1], [0, 1]);
      if (type !== 'R') dirs.push([-1, -1], [-1, 1], [1, -1], [1, 1]);

      for (const [dr, df] of dirs) {
        let step = 1;
        while (addMove(r + dr * step, f + df * step)) {
          step++;
        }
      }
    } else if (type === 'K') {
      for (let dr = -1; dr <= 1; dr++) {
        for (let df = -1; df <= 1; df++) {
          if (dr !== 0 || df !== 0) addMove(r + dr, f + df);
        }
      }

      // Castling
      if (color === 'w' && r === 7 && f === 4) {
        if (this.castling.K && !this.board[61] && !this.board[62]) moves.push('g1');
        if (this.castling.Q && !this.board[59] && !this.board[58] && !this.board[57]) moves.push('c1');
      } else if (color === 'b' && r === 0 && f === 4) {
        if (this.castling.k && !this.board[5] && !this.board[6]) moves.push('g8');
        if (this.castling.q && !this.board[3] && !this.board[2] && !this.board[1]) moves.push('c8');
      }
    }

    return moves;
  }

  toFen() {
    let fen = '';
    for (let r = 0; r < 8; r++) {
      let emptyCount = 0;
      for (let f = 0; f < 8; f++) {
        const piece = this.board[r * 8 + f];
        if (!piece) {
          emptyCount++;
        } else {
          if (emptyCount > 0) {
            fen += emptyCount;
            emptyCount = 0;
          }
          const char = piece[0] === 'w' ? piece[1].toUpperCase() : piece[1].toLowerCase();
          fen += char;
        }
      }
      if (emptyCount > 0) fen += emptyCount;
      if (r < 7) fen += '/';
    }

    fen += ` ${this.turn} `;
    let castling = '';
    if (this.castling.K) castling += 'K';
    if (this.castling.Q) castling += 'Q';
    if (this.castling.k) castling += 'k';
    if (this.castling.q) castling += 'q';
    fen += castling || '-';

    fen += ` ${this.enPassant || '-'} ${this.halfMoves} ${this.fullMoves}`;
    return fen;
  }
}

window.ChessClient = ChessEngineClient;
window.PIECE_SVGS = PIECE_SVGS;
