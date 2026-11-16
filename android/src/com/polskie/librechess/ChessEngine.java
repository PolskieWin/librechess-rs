package com.polskie.librechess;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

public class ChessEngine {
    public static final int EMPTY = 0;
    public static final int PAWN = 1;
    public static final int KNIGHT = 2;
    public static final int BISHOP = 3;
    public static final int ROOK = 4;
    public static final int QUEEN = 5;
    public static final int KING = 6;

    public static final int WHITE = 8;
    public static final int BLACK = 16;

    public int[] board = new int[64];
    public boolean whiteToMove = true;
    public boolean castlingWK = true;
    public boolean castlingWQ = true;
    public boolean castlingBK = true;
    public boolean castlingBQ = true;
    public int enPassantSq = -1; // 0..63
    public int halfMoves = 0;
    public int fullMoves = 1;

    public List<MoveRecord> moveHistory = new ArrayList<>();
    public boolean isGameOver = false;
    public String gameResult = "";
    public String gameOverReason = "";

    public static class MoveRecord {
        public int from;
        public int to;
        public int piece;
        public int captured;
        public boolean isCastling;
        public boolean isEnPassant;
        public int promotion;
        public String san;
        public float evalAfter;
        public String classification; // Best, Good, Inaccuracy, Mistake, Blunder, Brilliant

        public MoveRecord(int from, int to, int piece, int captured, boolean isCastling, boolean isEnPassant, int promotion, String san) {
            this.from = from;
            this.to = to;
            this.piece = piece;
            this.captured = captured;
            this.isCastling = isCastling;
            this.isEnPassant = isEnPassant;
            this.promotion = promotion;
            this.san = san;
            this.evalAfter = 0;
            this.classification = "Good";
        }
    }

    public ChessEngine() {
        reset();
    }

    public void reset() {
        for (int i = 0; i < 64; i++) board[i] = EMPTY;

        // Black pieces (ranks 7 and 8, index 0..15)
        board[0] = BLACK | ROOK;
        board[1] = BLACK | KNIGHT;
        board[2] = BLACK | BISHOP;
        board[3] = BLACK | QUEEN;
        board[4] = BLACK | KING;
        board[5] = BLACK | BISHOP;
        board[6] = BLACK | KNIGHT;
        board[7] = BLACK | ROOK;
        for (int i = 0; i < 8; i++) board[8 + i] = BLACK | PAWN;

        // White pieces (ranks 1 and 2, index 48..63)
        for (int i = 0; i < 8; i++) board[48 + i] = WHITE | PAWN;
        board[56] = WHITE | ROOK;
        board[57] = WHITE | KNIGHT;
        board[58] = WHITE | BISHOP;
        board[59] = WHITE | QUEEN;
        board[60] = WHITE | KING;
        board[61] = WHITE | BISHOP;
        board[62] = WHITE | KNIGHT;
        board[63] = WHITE | ROOK;

        whiteToMove = true;
        castlingWK = true;
        castlingWQ = true;
        castlingBK = true;
        castlingBQ = true;
        enPassantSq = -1;
        halfMoves = 0;
        fullMoves = 1;
        moveHistory.clear();
        isGameOver = false;
        gameResult = "";
        gameOverReason = "";
    }

    public static int colorOf(int piece) {
        return piece & (WHITE | BLACK);
    }

    public static int typeOf(int piece) {
        return piece & 7;
    }

    public boolean isSquareAttacked(int sq, int byColor) {
        int r = sq / 8;
        int f = sq % 8;

        // Pawns
        int pDir = byColor == WHITE ? 1 : -1;
        int pr = r + pDir;
        if (pr >= 0 && pr < 8) {
            if (f > 0 && board[pr * 8 + (f - 1)] == (byColor | PAWN)) return true;
            if (f < 7 && board[pr * 8 + (f + 1)] == (byColor | PAWN)) return true;
        }

        // Knights
        int[][] nDeltas = {{-2, -1}, {-2, 1}, {-1, -2}, {-1, 2}, {1, -2}, {1, 2}, {2, -1}, {2, 1}};
        for (int[] d : nDeltas) {
            int tr = r + d[0], tf = f + d[1];
            if (tr >= 0 && tr < 8 && tf >= 0 && tf < 8) {
                if (board[tr * 8 + tf] == (byColor | KNIGHT)) return true;
            }
        }

        // Kings
        for (int dr = -1; dr <= 1; dr++) {
            for (int df = -1; df <= 1; df++) {
                if (dr == 0 && df == 0) continue;
                int tr = r + dr, tf = f + df;
                if (tr >= 0 && tr < 8 && tf >= 0 && tf < 8) {
                    if (board[tr * 8 + tf] == (byColor | KING)) return true;
                }
            }
        }

        // Straight lines (Rook / Queen)
        int[][] straightDirs = {{-1, 0}, {1, 0}, {0, -1}, {0, 1}};
        for (int[] d : straightDirs) {
            int tr = r + d[0], tf = f + d[1];
            while (tr >= 0 && tr < 8 && tf >= 0 && tf < 8) {
                int p = board[tr * 8 + tf];
                if (p != EMPTY) {
                    if (colorOf(p) == byColor && (typeOf(p) == ROOK || typeOf(p) == QUEEN)) return true;
                    break;
                }
                tr += d[0];
                tf += d[1];
            }
        }

        // Diagonals (Bishop / Queen)
        int[][] diagDirs = {{-1, -1}, {-1, 1}, {1, -1}, {1, 1}};
        for (int[] d : diagDirs) {
            int tr = r + d[0], tf = f + d[1];
            while (tr >= 0 && tr < 8 && tf >= 0 && tf < 8) {
                int p = board[tr * 8 + tf];
                if (p != EMPTY) {
                    if (colorOf(p) == byColor && (typeOf(p) == BISHOP || typeOf(p) == QUEEN)) return true;
                    break;
                }
                tr += d[0];
                tf += d[1];
            }
        }

        return false;
    }

    public boolean isKingInCheck(int color) {
        int kingSq = -1;
        for (int i = 0; i < 64; i++) {
            if (board[i] == (color | KING)) {
                kingSq = i;
                break;
            }
        }
        if (kingSq == -1) return false;
        int oppColor = color == WHITE ? BLACK : WHITE;
        return isSquareAttacked(kingSq, oppColor);
    }

    public List<Integer> getPseudoLegalMoves(int from) {
        List<Integer> list = new ArrayList<>();
        int p = board[from];
        if (p == EMPTY) return list;
        int color = colorOf(p);
        int type = typeOf(p);
        int r = from / 8;
        int f = from % 8;

        if (type == PAWN) {
            int dir = color == WHITE ? -1 : 1;
            int startRank = color == WHITE ? 6 : 1;
            int f1 = (r + dir) * 8 + f;
            if (f1 >= 0 && f1 < 64 && board[f1] == EMPTY) {
                list.add(f1);
                int f2 = (r + 2 * dir) * 8 + f;
                if (r == startRank && f2 >= 0 && f2 < 64 && board[f2] == EMPTY) {
                    list.add(f2);
                }
            }

            for (int df : new int[]{-1, 1}) {
                int tf = f + df;
                int tr = r + dir;
                if (tf >= 0 && tf < 8 && tr >= 0 && tr < 8) {
                    int target = tr * 8 + tf;
                    int targetPiece = board[target];
                    if (targetPiece != EMPTY && colorOf(targetPiece) != color) {
                        list.add(target);
                    } else if (target == enPassantSq) {
                        list.add(target);
                    }
                }
            }
        } else if (type == KNIGHT) {
            int[][] deltas = {{-2, -1}, {-2, 1}, {-1, -2}, {-1, 2}, {1, -2}, {1, 2}, {2, -1}, {2, 1}};
            for (int[] d : deltas) {
                int tr = r + d[0], tf = f + d[1];
                if (tr >= 0 && tr < 8 && tf >= 0 && tf < 8) {
                    int target = tr * 8 + tf;
                    if (board[target] == EMPTY || colorOf(board[target]) != color) {
                        list.add(target);
                    }
                }
            }
        } else if (type == BISHOP || type == ROOK || type == QUEEN) {
            int[][] dirs;
            if (type == BISHOP) dirs = new int[][]{{-1, -1}, {-1, 1}, {1, -1}, {1, 1}};
            else if (type == ROOK) dirs = new int[][]{{-1, 0}, {1, 0}, {0, -1}, {0, 1}};
            else dirs = new int[][]{{-1, 0}, {1, 0}, {0, -1}, {0, 1}, {-1, -1}, {-1, 1}, {1, -1}, {1, 1}};

            for (int[] d : dirs) {
                int tr = r + d[0], tf = f + d[1];
                while (tr >= 0 && tr < 8 && tf >= 0 && tf < 8) {
                    int target = tr * 8 + tf;
                    if (board[target] == EMPTY) {
                        list.add(target);
                    } else {
                        if (colorOf(board[target]) != color) list.add(target);
                        break;
                    }
                    tr += d[0];
                    tf += d[1];
                }
            }
        } else if (type == KING) {
            for (int dr = -1; dr <= 1; dr++) {
                for (int df = -1; df <= 1; df++) {
                    if (dr == 0 && df == 0) continue;
                    int tr = r + dr, tf = f + df;
                    if (tr >= 0 && tr < 8 && tf >= 0 && tf < 8) {
                        int target = tr * 8 + tf;
                        if (board[target] == EMPTY || colorOf(board[target]) != color) {
                            list.add(target);
                        }
                    }
                }
            }

            // Castling checks
            int oppColor = color == WHITE ? BLACK : WHITE;
            if (color == WHITE && from == 60) {
                if (castlingWK && board[61] == EMPTY && board[62] == EMPTY && board[63] == (WHITE | ROOK)) {
                    if (!isKingInCheck(WHITE) && !isSquareAttacked(61, oppColor) && !isSquareAttacked(62, oppColor)) {
                        list.add(62);
                    }
                }
                if (castlingWQ && board[59] == EMPTY && board[58] == EMPTY && board[57] == EMPTY && board[56] == (WHITE | ROOK)) {
                    if (!isKingInCheck(WHITE) && !isSquareAttacked(59, oppColor) && !isSquareAttacked(58, oppColor)) {
                        list.add(58);
                    }
                }
            } else if (color == BLACK && from == 4) {
                if (castlingBK && board[5] == EMPTY && board[6] == EMPTY && board[7] == (BLACK | ROOK)) {
                    if (!isKingInCheck(BLACK) && !isSquareAttacked(5, oppColor) && !isSquareAttacked(6, oppColor)) {
                        list.add(6);
                    }
                }
                if (castlingBQ && board[3] == EMPTY && board[2] == EMPTY && board[1] == EMPTY && board[0] == (BLACK | ROOK)) {
                    if (!isKingInCheck(BLACK) && !isSquareAttacked(3, oppColor) && !isSquareAttacked(2, oppColor)) {
                        list.add(2);
                    }
                }
            }
        }

        return list;
    }

    public List<Integer> getLegalMoves(int from) {
        List<Integer> legal = new ArrayList<>();
        int p = board[from];
        if (p == EMPTY) return legal;
        int color = colorOf(p);
        if ((whiteToMove && color != WHITE) || (!whiteToMove && color != BLACK)) return legal;

        List<Integer> pseudo = getPseudoLegalMoves(from);
        for (int to : pseudo) {
            // Test move
            int origFrom = board[from];
            int origTo = board[to];
            int origEp = enPassantSq;
            int capSq = -1;
            int capPiece = EMPTY;

            // En passant test capture
            if (typeOf(origFrom) == PAWN && to == origEp) {
                capSq = color == WHITE ? to + 8 : to - 8;
                capPiece = board[capSq];
                board[capSq] = EMPTY;
            }

            board[to] = origFrom;
            board[from] = EMPTY;

            boolean inCheck = isKingInCheck(color);

            // Revert
            board[from] = origFrom;
            board[to] = origTo;
            if (capSq != -1) board[capSq] = capPiece;

            if (!inCheck) {
                legal.add(to);
            }
        }
        return legal;
    }

    public boolean makeMove(int from, int to) {
        if (isGameOver) return false;
        List<Integer> legal = getLegalMoves(from);
        if (!legal.contains(to)) return false;

        float evalBefore = evaluate();
        int p = board[from];
        int color = colorOf(p);
        int type = typeOf(p);
        int captured = board[to];

        boolean isCastling = false;
        boolean isEnPassant = false;
        int promotion = EMPTY;

        // En passant execution
        if (type == PAWN && to == enPassantSq && captured == EMPTY) {
            isEnPassant = true;
            int capSq = color == WHITE ? to + 8 : to - 8;
            captured = board[capSq];
            board[capSq] = EMPTY;
        }

        // Castling execution
        if (type == KING) {
            if (from == 60 && to == 62) {
                isCastling = true;
                board[63] = EMPTY;
                board[61] = WHITE | ROOK;
            } else if (from == 60 && to == 58) {
                isCastling = true;
                board[56] = EMPTY;
                board[59] = WHITE | ROOK;
            } else if (from == 4 && to == 6) {
                isCastling = true;
                board[7] = EMPTY;
                board[5] = BLACK | ROOK;
            } else if (from == 4 && to == 2) {
                isCastling = true;
                board[0] = EMPTY;
                board[3] = BLACK | ROOK;
            }

            if (color == WHITE) { castlingWK = false; castlingWQ = false; }
            else { castlingBK = false; castlingBQ = false; }
        }

        // Invalidate rook castling rights
        if (from == 56 || to == 56) castlingWQ = false;
        if (from == 63 || to == 63) castlingWK = false;
        if (from == 0 || to == 0) castlingBQ = false;
        if (from == 7 || to == 7) castlingBK = false;

        // Move piece
        board[to] = p;
        board[from] = EMPTY;

        // Auto Queen promotion
        if (type == PAWN) {
            if (color == WHITE && to / 8 == 0) {
                promotion = WHITE | QUEEN;
                board[to] = promotion;
            } else if (color == BLACK && to / 8 == 7) {
                promotion = BLACK | QUEEN;
                board[to] = promotion;
            }
        }

        // Update en passant square
        if (type == PAWN && Math.abs(to - from) == 16) {
            enPassantSq = color == WHITE ? from - 8 : from + 8;
        } else {
            enPassantSq = -1;
        }

        // Halfmove clock
        if (type == PAWN || captured != EMPTY) halfMoves = 0;
        else halfMoves++;

        whiteToMove = !whiteToMove;
        if (whiteToMove) fullMoves++;

        // Compute SAN
        String san = toSAN(from, to, type, captured != EMPTY, isCastling, promotion);

        checkGameEnd();
        if (isGameOver && gameResult.startsWith("1-0") || gameResult.startsWith("0-1")) {
            san += "#";
        } else if (isKingInCheck(whiteToMove ? WHITE : BLACK)) {
            san += "+";
        }

        MoveRecord rec = new MoveRecord(from, to, p, captured, isCastling, isEnPassant, promotion, san);
        float evalAfter = evaluate();
        rec.evalAfter = evalAfter;

        // Move classification
        float delta = color == WHITE ? (evalAfter - evalBefore) : (evalBefore - evalAfter);
        if (captured != EMPTY && (typeOf(p) == QUEEN || typeOf(p) == ROOK) && delta > 1.5f) {
            rec.classification = "Brilliant";
        } else if (delta >= -0.2f) {
            rec.classification = "Best";
        } else if (delta >= -0.7f) {
            rec.classification = "Good";
        } else if (delta >= -1.8f) {
            rec.classification = "Inaccuracy";
        } else if (delta >= -3.5f) {
            rec.classification = "Mistake";
        } else {
            rec.classification = "Blunder";
        }

        moveHistory.add(rec);
        return true;
    }

    private void checkGameEnd() {
        boolean hasAnyLegal = false;
        int checkColor = whiteToMove ? WHITE : BLACK;
        for (int i = 0; i < 64; i++) {
            if (board[i] != EMPTY && colorOf(board[i]) == checkColor) {
                if (!getLegalMoves(i).isEmpty()) {
                    hasAnyLegal = true;
                    break;
                }
            }
        }

        if (!hasAnyLegal) {
            isGameOver = true;
            if (isKingInCheck(checkColor)) {
                gameResult = whiteToMove ? "0-1" : "1-0";
                gameOverReason = "Checkmate";
            } else {
                gameResult = "1/2-1/2";
                gameOverReason = "Stalemate";
            }
        } else if (halfMoves >= 100) {
            isGameOver = true;
            gameResult = "1/2-1/2";
            gameOverReason = "50-move rule";
        }
    }

    public void resign(boolean whiteResigned) {
        isGameOver = true;
        gameResult = whiteResigned ? "0-1" : "1-0";
        gameOverReason = (whiteResigned ? "White" : "Black") + " Resigned";
    }

    public void agreeDraw() {
        isGameOver = true;
        gameResult = "1/2-1/2";
        gameOverReason = "Draw by agreement";
    }

    private String toSAN(int from, int to, int type, boolean isCapture, boolean isCastling, int promotion) {
        if (isCastling) {
            return (to % 8 > from % 8) ? "O-O" : "O-O-O";
        }
        String toSq = squareName(to);
        if (type == PAWN) {
            String s = isCapture ? squareName(from).substring(0, 1) + "x" + toSq : toSq;
            if (promotion != EMPTY) s += "=Q";
            return s;
        }

        String pLetter = "";
        switch (type) {
            case KNIGHT: pLetter = "N"; break;
            case BISHOP: pLetter = "B"; break;
            case ROOK: pLetter = "R"; break;
            case QUEEN: pLetter = "Q"; break;
            case KING: pLetter = "K"; break;
        }
        return pLetter + (isCapture ? "x" : "") + toSq;
    }

    public static String squareName(int idx) {
        char file = (char) ('a' + (idx % 8));
        int rank = 8 - (idx / 8);
        return "" + file + rank;
    }

    // Positional piece-square tables for intelligent play
    private static final int[] PAWN_PST = {
         0,  0,  0,  0,  0,  0,  0,  0,
        50, 50, 50, 50, 50, 50, 50, 50,
        10, 10, 20, 30, 30, 20, 10, 10,
         5,  5, 10, 25, 25, 10,  5,  5,
         0,  0,  0, 20, 20,  0,  0,  0,
         5, -5,-10,  0,  0,-10, -5,  5,
         5, 10, 10,-20,-20, 10, 10,  5,
         0,  0,  0,  0,  0,  0,  0,  0
    };

    private static final int[] KNIGHT_PST = {
        -50,-40,-30,-30,-30,-30,-40,-50,
        -40,-20,  0,  0,  0,  0,-20,-40,
        -30,  0, 10, 15, 15, 10,  0,-30,
        -30,  5, 15, 20, 20, 15,  5,-30,
        -30,  0, 15, 20, 20, 15,  0,-30,
        -30,  5, 10, 15, 15, 10,  5,-30,
        -40,-20,  0,  5,  5,  0,-20,-40,
        -50,-40,-30,-30,-30,-30,-40,-50
    };

    public float evaluate() {
        float score = 0;
        for (int i = 0; i < 64; i++) {
            int p = board[i];
            if (p == EMPTY) continue;
            int type = typeOf(p);
            int color = colorOf(p);

            float val = pieceValue(type);
            int pstIndex = color == WHITE ? i : (63 - i);
            float bonus = 0;
            if (type == PAWN) bonus = PAWN_PST[pstIndex] * 0.01f;
            else if (type == KNIGHT) bonus = KNIGHT_PST[pstIndex] * 0.01f;

            float totalVal = val + bonus;
            if (color == WHITE) score += totalVal;
            else score -= totalVal;
        }
        return score;
    }

    private float pieceValue(int type) {
        switch (type) {
            case PAWN: return 1.0f;
            case KNIGHT: return 3.0f;
            case BISHOP: return 3.25f;
            case ROOK: return 5.0f;
            case QUEEN: return 9.0f;
            default: return 0.0f;
        }
    }

    public int[] getBotMove(int eloLevel) {
        List<int[]> moves = new ArrayList<>();
        int botColor = whiteToMove ? WHITE : BLACK;

        for (int i = 0; i < 64; i++) {
            if (board[i] != EMPTY && colorOf(board[i]) == botColor) {
                for (int to : getLegalMoves(i)) {
                    moves.add(new int[]{i, to});
                }
            }
        }

        if (moves.isEmpty()) return null;

        Random rng = new Random();

        // Bot 100: 75% random, 25% greedy
        if (eloLevel <= 100 && rng.nextFloat() < 0.75f) {
            return moves.get(rng.nextInt(moves.size()));
        }
        // Bot 300: 45% random
        if (eloLevel <= 300 && rng.nextFloat() < 0.45f) {
            return moves.get(rng.nextInt(moves.size()));
        }

        // Greedy search with piece-square evaluation
        int[] bestMove = moves.get(0);
        float bestScore = botColor == WHITE ? -9999f : 9999f;

        for (int[] m : moves) {
            ChessEngine clone = cloneEngine();
            clone.makeMove(m[0], m[1]);
            float eval = clone.evaluate();

            if (botColor == WHITE && eval > bestScore) {
                bestScore = eval;
                bestMove = m;
            } else if (botColor == BLACK && eval < bestScore) {
                bestScore = eval;
                bestMove = m;
            }
        }

        return bestMove;
    }

    public float[] getAccuracy() {
        if (moveHistory.isEmpty()) return new float[]{100.0f, 100.0f};
        int whiteMoves = 0, blackMoves = 0;
        float whiteScore = 0, blackScore = 0;

        for (int i = 0; i < moveHistory.size(); i++) {
            MoveRecord m = moveHistory.get(i);
            boolean isWhite = (colorOf(m.piece) == WHITE);
            float pts = 50.0f;
            if ("Brilliant".equals(m.classification)) pts = 100.0f;
            else if ("Best".equals(m.classification)) pts = 98.0f;
            else if ("Good".equals(m.classification)) pts = 85.0f;
            else if ("Inaccuracy".equals(m.classification)) pts = 60.0f;
            else if ("Mistake".equals(m.classification)) pts = 40.0f;
            else if ("Blunder".equals(m.classification)) pts = 15.0f;

            if (isWhite) {
                whiteMoves++;
                whiteScore += pts;
            } else {
                blackMoves++;
                blackScore += pts;
            }
        }

        float whiteAcc = whiteMoves > 0 ? (whiteScore / whiteMoves) : 100.0f;
        float blackAcc = blackMoves > 0 ? (blackScore / blackMoves) : 100.0f;
        return new float[]{Math.round(whiteAcc * 10) / 10.0f, Math.round(blackAcc * 10) / 10.0f};
    }

    public ChessEngine cloneEngine() {
        ChessEngine c = new ChessEngine();
        System.arraycopy(this.board, 0, c.board, 0, 64);
        c.whiteToMove = this.whiteToMove;
        c.castlingWK = this.castlingWK;
        c.castlingWQ = this.castlingWQ;
        c.castlingBK = this.castlingBK;
        c.castlingBQ = this.castlingBQ;
        c.enPassantSq = this.enPassantSq;
        c.halfMoves = this.halfMoves;
        c.fullMoves = this.fullMoves;
        c.isGameOver = this.isGameOver;
        c.gameResult = this.gameResult;
        c.gameOverReason = this.gameOverReason;
        return c;
    }
}
