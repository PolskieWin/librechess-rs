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

    public boolean isPromotionMove(int from, int to) {
        if (from < 0 || from >= 64 || to < 0 || to >= 64) return false;
        int p = board[from];
        if (p == EMPTY) return false;
        int type = typeOf(p);
        if (type != PAWN) return false;
        int color = colorOf(p);
        return (color == WHITE && to / 8 == 0) || (color == BLACK && to / 8 == 7);
    }

    public boolean makeMove(int from, int to) {
        return makeMove(from, to, QUEEN);
    }

    public boolean makeMove(int from, int to, int promoType) {
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

        // Pawn promotion (Queen, Knight, Rook, Bishop)
        if (type == PAWN) {
            int pType = (promoType == KNIGHT || promoType == ROOK || promoType == BISHOP) ? promoType : QUEEN;
            if (color == WHITE && to / 8 == 0) {
                promotion = WHITE | pType;
                board[to] = promotion;
            } else if (color == BLACK && to / 8 == 7) {
                promotion = BLACK | pType;
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
            if (promotion != EMPTY) {
                int pType = typeOf(promotion);
                if (pType == KNIGHT) s += "=N";
                else if (pType == ROOK) s += "=R";
                else if (pType == BISHOP) s += "=B";
                else s += "=Q";
            }
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
         5, 10, 10,-20,-20, 10, 10,  5,
         5, -5,-10,  0,  0,-10, -5,  5,
         0,  0,  0, 25, 25,  0,  0,  0,
         5,  5, 10, 27, 27, 10,  5,  5,
        10, 10, 20, 30, 30, 20, 10, 10,
        50, 50, 50, 50, 50, 50, 50, 50,
         0,  0,  0,  0,  0,  0,  0,  0
    };

    private static final int[] KNIGHT_PST = {
        -50,-40,-30,-30,-30,-30,-40,-50,
        -40,-20,  0,  5,  5,  0,-20,-40,
        -30,  5, 10, 15, 15, 10,  5,-30,
        -30,  0, 15, 20, 20, 15,  0,-30,
        -30,  5, 15, 20, 20, 15,  5,-30,
        -30,  0, 10, 15, 15, 10,  0,-30,
        -40,-20,  0,  0,  0,  0,-20,-40,
        -50,-40,-30,-30,-30,-30,-40,-50
    };

    private static final int[] BISHOP_PST = {
        -20,-10,-10,-10,-10,-10,-10,-20,
        -10,  5,  0,  0,  0,  0,  5,-10,
        -10, 10, 10, 10, 10, 10, 10,-10,
        -10,  0, 10, 10, 10, 10,  0,-10,
        -10,  5,  5, 10, 10,  5,  5,-10,
        -10,  0,  5, 10, 10,  5,  0,-10,
        -10,  0,  0,  0,  0,  0,  0,-10,
        -20,-10,-10,-10,-10,-10,-10,-20
    };

    private static final int[] ROOK_PST = {
          0,  0,  0,  5,  5,  0,  0,  0,
         -5,  0,  0,  0,  0,  0,  0, -5,
         -5,  0,  0,  0,  0,  0,  0, -5,
         -5,  0,  0,  0,  0,  0,  0, -5,
         -5,  0,  0,  0,  0,  0,  0, -5,
         -5,  0,  0,  0,  0,  0,  0, -5,
          5, 10, 10, 10, 10, 10, 10,  5,
          0,  0,  0,  0,  0,  0,  0,  0
    };

    private static final int[] QUEEN_PST = {
        -20,-10,-10, -5, -5,-10,-10,-20,
        -10,  0,  0,  0,  0,  0,  0,-10,
        -10,  0,  5,  5,  5,  5,  0,-10,
         -5,  0,  5,  5,  5,  5,  0, -5,
          0,  0,  5,  5,  5,  5,  0, -5,
        -10,  5,  5,  5,  5,  5,  0,-10,
        -10,  0,  5,  0,  0,  0,  0,-10,
        -20,-10,-10, -5, -5,-10,-10,-20
    };

    private static final int[] KING_PST = {
         20, 30, 10,  0,  0, 10, 30, 20,
         20, 20,  0,  0,  0,  0, 20, 20,
        -10,-20,-20,-20,-20,-20,-20,-10,
        -20,-30,-30,-40,-40,-30,-30,-20,
        -30,-40,-40,-50,-50,-40,-40,-30,
        -30,-40,-40,-50,-50,-40,-40,-30,
        -30,-40,-40,-50,-50,-40,-40,-30,
        -30,-40,-40,-50,-50,-40,-40,-30
    };

    public boolean isAttackedByPawn(int sq, int byColor) {
        int r = sq / 8;
        int f = sq % 8;
        int pDir = byColor == WHITE ? 1 : -1;
        int pr = r + pDir;
        if (pr >= 0 && pr < 8) {
            if (f > 0 && board[pr * 8 + (f - 1)] == (byColor | PAWN)) return true;
            if (f < 7 && board[pr * 8 + (f + 1)] == (byColor | PAWN)) return true;
        }
        return false;
    }

    public List<Integer> getKnightAttacks(int sq) {
        List<Integer> list = new ArrayList<>();
        int r = sq / 8, f = sq % 8;
        int[][] nDeltas = {{-2, -1}, {-2, 1}, {-1, -2}, {-1, 2}, {1, -2}, {1, 2}, {2, -1}, {2, 1}};
        for (int[] d : nDeltas) {
            int tr = r + d[0], tf = f + d[1];
            if (tr >= 0 && tr < 8 && tf >= 0 && tf < 8) {
                list.add(tr * 8 + tf);
            }
        }
        return list;
    }

    public int getPawnForkCount(int sq, int pawnColor) {
        int r = sq / 8, f = sq % 8;
        int pDir = (pawnColor == WHITE) ? -1 : 1;
        int tr = r + pDir;
        if (tr < 0 || tr >= 8) return 0;
        int oppColor = (pawnColor == WHITE) ? BLACK : WHITE;
        int targetCount = 0;

        for (int df : new int[]{-1, 1}) {
            int tf = f + df;
            if (tf >= 0 && tf < 8) {
                int p = board[tr * 8 + tf];
                if (p != EMPTY && colorOf(p) == oppColor) {
                    int t = typeOf(p);
                    if (t == KNIGHT || t == BISHOP || t == ROOK || t == QUEEN) {
                        targetCount++;
                    }
                }
            }
        }
        return targetCount >= 2 ? targetCount : 0;
    }

    public boolean isPinnedToKing(int sq, int color) {
        int kingSq = -1;
        int kingPiece = color | KING;
        for (int i = 0; i < 64; i++) {
            if (board[i] == kingPiece) { kingSq = i; break; }
        }
        if (kingSq == -1) return false;

        int kr = kingSq / 8, kf = kingSq % 8;
        int pr = sq / 8, pf = sq % 8;
        int dr = pr - kr, df = pf - kf;

        int stepR = 0, stepF = 0;
        if (dr == 0 && df != 0) {
            stepF = df > 0 ? 1 : -1;
        } else if (df == 0 && dr != 0) {
            stepR = dr > 0 ? 1 : -1;
        } else if (Math.abs(dr) == Math.abs(df) && dr != 0) {
            stepR = dr > 0 ? 1 : -1;
            stepF = df > 0 ? 1 : -1;
        } else {
            return false;
        }

        int currR = kr + stepR, currF = kf + stepF;
        while (currR != pr || currF != pf) {
            if (board[currR * 8 + currF] != EMPTY) return false;
            currR += stepR;
            currF += stepF;
        }

        currR = pr + stepR;
        currF = pf + stepF;
        int oppColor = (color == WHITE) ? BLACK : WHITE;
        boolean isDiag = (stepR != 0 && stepF != 0);

        while (currR >= 0 && currR < 8 && currF >= 0 && currF < 8) {
            int p = board[currR * 8 + currF];
            if (p != EMPTY) {
                if (colorOf(p) == oppColor) {
                    int type = typeOf(p);
                    if (isDiag && (type == BISHOP || type == QUEEN)) return true;
                    if (!isDiag && (type == ROOK || type == QUEEN)) return true;
                }
                return false;
            }
            currR += stepR;
            currF += stepF;
        }
        return false;
    }

    public boolean isPinnedToQueen(int sq, int color) {
        int queenSq = -1;
        int queenPiece = color | QUEEN;
        for (int i = 0; i < 64; i++) {
            if (board[i] == queenPiece) { queenSq = i; break; }
        }
        if (queenSq == -1) return false;

        int qr = queenSq / 8, qf = queenSq % 8;
        int pr = sq / 8, pf = sq % 8;
        int dr = pr - qr, df = pf - qf;

        int stepR = 0, stepF = 0;
        if (dr == 0 && df != 0) {
            stepF = df > 0 ? 1 : -1;
        } else if (df == 0 && dr != 0) {
            stepR = dr > 0 ? 1 : -1;
        } else if (Math.abs(dr) == Math.abs(df) && dr != 0) {
            stepR = dr > 0 ? 1 : -1;
            stepF = df > 0 ? 1 : -1;
        } else {
            return false;
        }

        int currR = qr + stepR, currF = qf + stepF;
        while (currR != pr || currF != pf) {
            if (board[currR * 8 + currF] != EMPTY) return false;
            currR += stepR;
            currF += stepF;
        }

        currR = pr + stepR;
        currF = pf + stepF;
        int oppColor = (color == WHITE) ? BLACK : WHITE;
        boolean isDiag = (stepR != 0 && stepF != 0);

        while (currR >= 0 && currR < 8 && currF >= 0 && currF < 8) {
            int p = board[currR * 8 + currF];
            if (p != EMPTY) {
                if (colorOf(p) == oppColor) {
                    int type = typeOf(p);
                    if (isDiag && (type == BISHOP || type == QUEEN)) return true;
                    if (!isDiag && (type == ROOK || type == QUEEN)) return true;
                }
                return false;
            }
            currR += stepR;
            currF += stepF;
        }
        return false;
    }

    public int countAttackedMajors(int sq, int oppColor) {
        int count = 0;
        for (int to : getLegalMoves(sq)) {
            int p = board[to];
            if (p != EMPTY && colorOf(p) == oppColor) {
                int t = typeOf(p);
                if (t == KING || t == QUEEN || t == ROOK) count++;
            }
        }
        return count;
    }

    public boolean isBackRankVulnerable(int kingColor) {
        int r = (kingColor == WHITE) ? 7 : 0;
        int pawnR = (kingColor == WHITE) ? 6 : 1;
        int kingSq = -1;
        for (int f = 0; f < 8; f++) {
            if (board[r * 8 + f] == (kingColor | KING)) { kingSq = r * 8 + f; break; }
        }
        if (kingSq == -1) return false;
        int kf = kingSq % 8;
        if (kf < 5) return false; // Not kingside castled

        // Check if f, g, h pawns are unmoved
        boolean pawnsUnmoved = (board[pawnR * 8 + 5] == (kingColor | PAWN))
                            && (board[pawnR * 8 + 6] == (kingColor | PAWN))
                            && (board[pawnR * 8 + 7] == (kingColor | PAWN));
        if (!pawnsUnmoved) return false;

        // Check if enemy has a rook or queen on the board
        int oppColor = (kingColor == WHITE) ? BLACK : WHITE;
        for (int i = 0; i < 64; i++) {
            int p = board[i];
            if (colorOf(p) == oppColor && (typeOf(p) == ROOK || typeOf(p) == QUEEN)) {
                return true;
            }
        }
        return false;
    }

    public float evaluate() {
        float score = 0;
        int whiteBishops = 0;
        int blackBishops = 0;

        for (int i = 0; i < 64; i++) {
            int p = board[i];
            if (p == EMPTY) continue;
            int type = typeOf(p);
            int color = colorOf(p);
            boolean isWhite = (color == WHITE);
            int oppColor = isWhite ? BLACK : WHITE;

            float val = pieceValue(type);
            int pstIndex = isWhite ? i : (63 - i);
            float bonus = 0;
            switch (type) {
                case PAWN: {
                    bonus = PAWN_PST[pstIndex] * 0.01f;
                    // Tactical Pawn Fork
                    int forkCount = getPawnForkCount(i, color);
                    if (forkCount >= 2) bonus += 1.80f;
                    break;
                }
                case KNIGHT: {
                    bonus = KNIGHT_PST[pstIndex] * 0.01f;
                    // Tactical Knight Fork evaluation
                    int targets = 0;
                    for (int dest : getKnightAttacks(i)) {
                        int destP = board[dest];
                        if (destP != EMPTY && colorOf(destP) == oppColor) {
                            int dt = typeOf(destP);
                            if (dt == KING || dt == QUEEN || dt == ROOK) targets++;
                        }
                    }
                    if (targets >= 2) bonus += 1.50f; // Knight fork tactical bonus
                    break;
                }
                case BISHOP: {
                    bonus = BISHOP_PST[pstIndex] * 0.01f;
                    if (isWhite) whiteBishops++; else blackBishops++;
                    break;
                }
                case ROOK: {
                    bonus = ROOK_PST[pstIndex] * 0.01f;
                    int f = i % 8;
                    int r = i / 8;
                    // 7th rank bonus (White row 1, Black row 6)
                    if ((isWhite && r == 1) || (!isWhite && r == 6)) bonus += 0.40f;
                    // Open file bonus
                    boolean hasFriendlyPawn = false;
                    boolean hasEnemyPawn = false;
                    for (int rank = 0; rank < 8; rank++) {
                        int pieceOnCol = board[rank * 8 + f];
                        if (typeOf(pieceOnCol) == PAWN) {
                            if (colorOf(pieceOnCol) == color) hasFriendlyPawn = true;
                            else hasEnemyPawn = true;
                        }
                    }
                    if (!hasFriendlyPawn && !hasEnemyPawn) bonus += 0.35f; // Open file
                    else if (!hasFriendlyPawn) bonus += 0.20f; // Semi-open file
                    break;
                }
                case QUEEN: {
                    bonus = QUEEN_PST[pstIndex] * 0.01f;
                    // Tactical Queen Double Attack / Fork
                    int qTargets = countAttackedMajors(i, oppColor);
                    if (qTargets >= 2) bonus += 1.20f;
                    break;
                }
                case KING: {
                    bonus = KING_PST[pstIndex] * 0.01f;
                    // Tactical back-rank vulnerability penalty
                    if (isBackRankVulnerable(color)) bonus -= 0.75f;
                    break;
                }
            }

            // Tactical hanging piece & pin penalty
            if (type != KING) {
                // Pin detection
                if (isPinnedToKing(i, color)) {
                    bonus -= 0.65f; // Absolute pin penalty
                } else if (isPinnedToQueen(i, color)) {
                    bonus -= 0.35f; // Relative pin penalty
                }

                // Hanging piece / en prise evaluation
                boolean attacked = isSquareAttacked(i, oppColor);
                boolean defended = isSquareAttacked(i, color);
                if (attacked && !defended) {
                    bonus -= val * 0.50f; // Free hanging piece penalty
                } else if (attacked && defended) {
                    if (type != PAWN && isAttackedByPawn(i, oppColor)) {
                        bonus -= 0.60f; // Attacked by cheaper piece
                    }
                }
            }

            float totalVal = val + bonus;
            if (isWhite) score += totalVal;
            else score -= totalVal;
        }

        // Bishop pair bonus
        if (whiteBishops >= 2) score += 0.45f;
        if (blackBishops >= 2) score -= 0.45f;

        return score;
    }

    private float pieceValue(int type) {
        switch (type) {
            case PAWN: return 1.0f;
            case KNIGHT: return 3.25f;
            case BISHOP: return 3.35f;
            case ROOK: return 5.05f;
            case QUEEN: return 9.5f;
            case KING: return 200.0f;
            default: return 0.0f;
        }
    }

    public List<int[]> getAllLegalMoves() {
        List<int[]> moves = new ArrayList<>();
        int sideColor = whiteToMove ? WHITE : BLACK;
        for (int i = 0; i < 64; i++) {
            if (board[i] != EMPTY && colorOf(board[i]) == sideColor) {
                for (int to : getLegalMoves(i)) {
                    moves.add(new int[]{i, to});
                }
            }
        }
        return moves;
    }

    public List<int[]> getTacticalMoves() {
        List<int[]> tactical = new ArrayList<>();
        int sideColor = whiteToMove ? WHITE : BLACK;
        for (int i = 0; i < 64; i++) {
            if (board[i] != EMPTY && colorOf(board[i]) == sideColor) {
                int pieceType = typeOf(board[i]);
                for (int to : getLegalMoves(i)) {
                    boolean isCapture = (board[to] != EMPTY || to == enPassantSq);
                    boolean isPromotion = (pieceType == PAWN && (to / 8 == 0 || to / 8 == 7));
                    if (isCapture || isPromotion) {
                        tactical.add(new int[]{i, to});
                    }
                }
            }
        }
        return tactical;
    }

    private void orderMoves(List<int[]> moves) {
        // Fast MVV-LVA move ordering
        moves.sort(new java.util.Comparator<int[]>() {
            @Override
            public int compare(int[] m1, int[] m2) {
                return Integer.compare(scoreMove(m2), scoreMove(m1));
            }

            private int scoreMove(int[] m) {
                int target = board[m[1]];
                int victim = target != EMPTY ? typeOf(target) : (m[1] == enPassantSq ? PAWN : 0);
                int attacker = typeOf(board[m[0]]);
                int score = 0;
                if (victim > 0) {
                    score += 10000 + (victim * 10 - attacker);
                }
                if (attacker == PAWN && (m[1] / 8 == 0 || m[1] / 8 == 7)) {
                    score += 9500;
                }
                return score;
            }
        });
    }

    public boolean canOpponentCheckmateInOne() {
        for (int[] m : getAllLegalMoves()) {
            ChessEngine sim = cloneEngine();
            sim.makeMove(m[0], m[1]);
            if (sim.isGameOver && !"1/2-1/2".equals(sim.gameResult)) {
                return true;
            }
        }
        return false;
    }

    public float quiescence(float alpha, float beta, boolean maximizing, int maxDepth) {
        boolean inCheck = isKingInCheck(maximizing ? WHITE : BLACK);
        float standPat = evaluate();

        if (maxDepth <= 0 && !inCheck) return standPat;

        if (maximizing) {
            if (!inCheck) {
                if (standPat >= beta) return beta;
                if (standPat > alpha) alpha = standPat;
            }

            List<int[]> tacticalMoves = inCheck ? getAllLegalMoves() : getTacticalMoves();
            if (tacticalMoves.isEmpty()) {
                return inCheck ? -100000f : standPat;
            }

            orderMoves(tacticalMoves);
            for (int[] m : tacticalMoves) {
                ChessEngine sim = cloneEngine();
                sim.makeMove(m[0], m[1]);
                float score = sim.quiescence(alpha, beta, false, maxDepth - 1);
                if (score >= beta) return beta;
                if (score > alpha) alpha = score;
            }
            return alpha;
        } else {
            if (!inCheck) {
                if (standPat <= alpha) return alpha;
                if (standPat < beta) beta = standPat;
            }

            List<int[]> tacticalMoves = inCheck ? getAllLegalMoves() : getTacticalMoves();
            if (tacticalMoves.isEmpty()) {
                return inCheck ? 100000f : standPat;
            }

            orderMoves(tacticalMoves);
            for (int[] m : tacticalMoves) {
                ChessEngine sim = cloneEngine();
                sim.makeMove(m[0], m[1]);
                float score = sim.quiescence(alpha, beta, true, maxDepth - 1);
                if (score <= alpha) return alpha;
                if (score < beta) beta = score;
            }
            return beta;
        }
    }

    public float minimax(int depth, float alpha, float beta, boolean maximizing) {
        if (isGameOver) {
            int oppColor = maximizing ? WHITE : BLACK;
            if (isKingInCheck(oppColor)) {
                return maximizing ? (-100000f - depth) : (100000f + depth);
            }
            return 0f; // Stalemate
        }

        if (depth <= 0) {
            return quiescence(alpha, beta, maximizing, 2);
        }

        List<int[]> moves = getAllLegalMoves();
        if (moves.isEmpty()) {
            int myColor = maximizing ? WHITE : BLACK;
            if (isKingInCheck(myColor)) {
                return maximizing ? (-100000f - depth) : (100000f + depth);
            }
            return 0f;
        }

        orderMoves(moves);

        if (maximizing) {
            float maxEval = -999999f;
            for (int[] m : moves) {
                ChessEngine sim = cloneEngine();
                sim.makeMove(m[0], m[1]);
                float eval = sim.minimax(depth - 1, alpha, beta, false);
                maxEval = Math.max(maxEval, eval);
                alpha = Math.max(alpha, eval);
                if (beta <= alpha) break;
            }
            return maxEval;
        } else {
            float minEval = 999999f;
            for (int[] m : moves) {
                ChessEngine sim = cloneEngine();
                sim.makeMove(m[0], m[1]);
                float eval = sim.minimax(depth - 1, alpha, beta, true);
                minEval = Math.min(minEval, eval);
                beta = Math.min(beta, eval);
                if (beta <= alpha) break;
            }
            return minEval;
        }
    }

    public int[] getBotMove(int eloLevel) {
        List<int[]> moves = getAllLegalMoves();
        if (moves.isEmpty()) return null;

        boolean isWhite = whiteToMove;
        Random rng = new Random();

        // Bot 100: Safe beginner moves. Filters out any move that blunders immediate mate in 1!
        if (eloLevel <= 100) {
            List<int[]> safeMoves = new ArrayList<>();
            for (int[] m : moves) {
                ChessEngine sim = cloneEngine();
                sim.makeMove(m[0], m[1]);
                if (!sim.canOpponentCheckmateInOne()) {
                    safeMoves.add(m);
                }
            }
            if (!safeMoves.isEmpty() && rng.nextFloat() < 0.40f) {
                return safeMoves.get(rng.nextInt(safeMoves.size()));
            }
            if (!safeMoves.isEmpty()) moves = safeMoves;
        }

        // Calibrated depth:
        // Bot 100: Depth 2
        // Bot 300: Depth 2
        // Bot 600: Depth 3
        // Bot 1000: Depth 3 with Quiescence
        // Bot 1500: Depth 4 with Quiescence & MVV-LVA (Defends Scholar's Mate with certainty!)
        int searchDepth = 2;
        if (eloLevel >= 1500) searchDepth = 4;
        else if (eloLevel >= 600) searchDepth = 3;

        orderMoves(moves);

        int[] bestMove = moves.get(0);
        float bestScore = isWhite ? -999999f : 999999f;
        float alpha = -999999f;
        float beta = 999999f;

        for (int[] m : moves) {
            ChessEngine sim = cloneEngine();
            sim.makeMove(m[0], m[1]);
            float score = sim.minimax(searchDepth - 1, alpha, beta, !isWhite);

            if (isWhite) {
                if (score > bestScore) {
                    bestScore = score;
                    bestMove = m;
                }
                alpha = Math.max(alpha, bestScore);
            } else {
                if (score < bestScore) {
                    bestScore = score;
                    bestMove = m;
                }
                beta = Math.min(beta, bestScore);
            }
            if (beta <= alpha) break;
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

    public static class TacticalHint {
        public int from;
        public int to;
        public String san;
        public String explanation;

        public TacticalHint(int from, int to, String san, String explanation) {
            this.from = from;
            this.to = to;
            this.san = san;
            this.explanation = explanation;
        }
    }

    public TacticalHint getTacticalHint() {
        List<int[]> moves = getAllLegalMoves();
        if (moves.isEmpty()) return null;

        int[] bestMove = getBotMove(1500);
        if (bestMove == null) bestMove = moves.get(0);

        int from = bestMove[0];
        int to = bestMove[1];
        int p = board[from];
        int pType = typeOf(p);
        int targetP = board[to];
        int targetType = typeOf(targetP);
        boolean isWhite = whiteToMove;
        int oppColor = isWhite ? BLACK : WHITE;

        ChessEngine sim = cloneEngine();
        sim.makeMove(from, to);

        boolean isCapture = (targetP != EMPTY || to == enPassantSq);
        String san = toSAN(from, to, pType, isCapture, false, EMPTY);
        if (sim.isGameOver && !"1/2-1/2".equals(sim.gameResult)) san += "#";
        else if (sim.isKingInCheck(oppColor)) san += "+";

        String explanation;
        if (sim.isGameOver && !"1/2-1/2".equals(sim.gameResult)) {
            explanation = "Checkmate! Wins the game immediately.";
        } else if (sim.isKingInCheck(oppColor)) {
            explanation = "Delivers check disrupting opponent king safety.";
        } else if (pType == PAWN && sim.getPawnForkCount(to, isWhite ? WHITE : BLACK) >= 2) {
            explanation = "Pawn Fork! Attacks two major enemy pieces simultaneously.";
        } else if (pType == KNIGHT) {
            int forks = 0;
            for (int dest : getKnightAttacks(to)) {
                int dp = sim.board[dest];
                if (dp != EMPTY && colorOf(dp) == oppColor) {
                    int dt = typeOf(dp);
                    if (dt == KING || dt == QUEEN || dt == ROOK) forks++;
                }
            }
            if (forks >= 2) {
                explanation = "Knight Fork! Attacks multiple high-value targets simultaneously.";
            } else {
                explanation = "Tactical knight leap controlling critical central outposts.";
            }
        } else if (pType == QUEEN && sim.countAttackedMajors(to, oppColor) >= 2) {
            explanation = "Queen Fork! Dual attack forcing opponent material surrender.";
        } else if (sim.isPinnedToKing(to, oppColor)) {
            explanation = "Absolute Pin! Enemy piece is immobilized against their King.";
        } else if (targetP != EMPTY) {
            if (!sim.isSquareAttacked(to, oppColor)) {
                explanation = "Captures hanging piece (" + getPieceName(targetType) + ") on " + squareName(to) + "!";
            } else {
                explanation = "Tactical exchange winning material on " + squareName(to) + ".";
            }
        } else if (isSquareAttacked(from, oppColor) && !sim.isSquareAttacked(to, oppColor)) {
            explanation = "Evacuates attacked piece (" + getPieceName(pType) + ") to a secure square.";
        } else if (pType == PAWN && (to / 8 == 1 || to / 8 == 6)) {
            explanation = "Pushes passed pawn towards impending promotion.";
        } else if (sim.isBackRankVulnerable(oppColor)) {
            explanation = "Back-Rank Infiltration! Infiltrating the baseline with mating threats.";
        } else {
            explanation = "Optimal tactical move controlling key files and center diagonals.";
        }

        return new TacticalHint(from, to, san, explanation);
    }

    public static String getPieceName(int type) {
        switch (type) {
            case PAWN: return "Pawn";
            case KNIGHT: return "Knight";
            case BISHOP: return "Bishop";
            case ROOK: return "Rook";
            case QUEEN: return "Queen";
            case KING: return "King";
            default: return "Piece";
        }
    }

    public String toFEN() {
        StringBuilder sb = new StringBuilder();
        for (int r = 0; r < 8; r++) {
            int emptyCount = 0;
            for (int f = 0; f < 8; f++) {
                int p = board[r * 8 + f];
                if (p == EMPTY) {
                    emptyCount++;
                } else {
                    if (emptyCount > 0) {
                        sb.append(emptyCount);
                        emptyCount = 0;
                    }
                    int type = typeOf(p);
                    char c;
                    switch (type) {
                        case PAWN: c = 'p'; break;
                        case KNIGHT: c = 'n'; break;
                        case BISHOP: c = 'b'; break;
                        case ROOK: c = 'r'; break;
                        case QUEEN: c = 'q'; break;
                        case KING: c = 'k'; break;
                        default: c = '?'; break;
                    }
                    if (colorOf(p) == WHITE) c = Character.toUpperCase(c);
                    sb.append(c);
                }
            }
            if (emptyCount > 0) sb.append(emptyCount);
            if (r < 7) sb.append('/');
        }
        sb.append(whiteToMove ? " w " : " b ");
        StringBuilder castling = new StringBuilder();
        if (castlingWK) castling.append('K');
        if (castlingWQ) castling.append('Q');
        if (castlingBK) castling.append('k');
        if (castlingBQ) castling.append('q');
        if (castling.length() == 0) castling.append('-');
        sb.append(castling).append(' ');

        if (enPassantSq >= 0 && enPassantSq < 64) {
            sb.append(squareName(enPassantSq));
        } else {
            sb.append('-');
        }
        sb.append(' ').append(halfMoves).append(' ').append(fullMoves);
        return sb.toString();
    }

    public boolean loadFEN(String fen) {
        if (fen == null || fen.trim().isEmpty()) return false;
        try {
            String[] parts = fen.trim().split("\\s+");
            if (parts.length < 1) return false;
            String[] rows = parts[0].split("/");
            if (rows.length != 8) return false;

            int[] newBoard = new int[64];
            for (int r = 0; r < 8; r++) {
                String row = rows[r];
                int f = 0;
                for (int i = 0; i < row.length(); i++) {
                    char c = row.charAt(i);
                    if (Character.isDigit(c)) {
                        f += (c - '0');
                    } else {
                        if (f >= 8) return false;
                        int color = Character.isUpperCase(c) ? WHITE : BLACK;
                        int type = EMPTY;
                        switch (Character.toLowerCase(c)) {
                            case 'p': type = PAWN; break;
                            case 'n': type = KNIGHT; break;
                            case 'b': type = BISHOP; break;
                            case 'r': type = ROOK; break;
                            case 'q': type = QUEEN; break;
                            case 'k': type = KING; break;
                        }
                        newBoard[r * 8 + f] = color | type;
                        f++;
                    }
                }
            }
            System.arraycopy(newBoard, 0, board, 0, 64);
            whiteToMove = parts.length < 2 || "w".equalsIgnoreCase(parts[1]);

            castlingWK = false; castlingWQ = false; castlingBK = false; castlingBQ = false;
            if (parts.length >= 3) {
                String cast = parts[2];
                if (cast.contains("K")) castlingWK = true;
                if (cast.contains("Q")) castlingWQ = true;
                if (cast.contains("k")) castlingBK = true;
                if (cast.contains("q")) castlingBQ = true;
            }
            enPassantSq = -1;
            if (parts.length >= 4 && !"-".equals(parts[3])) {
                String ep = parts[3].toLowerCase();
                if (ep.length() >= 2) {
                    int ef = ep.charAt(0) - 'a';
                    int er = 8 - (ep.charAt(1) - '0');
                    if (ef >= 0 && ef < 8 && er >= 0 && er < 8) {
                        enPassantSq = er * 8 + ef;
                    }
                }
            }
            halfMoves = (parts.length >= 5) ? Integer.parseInt(parts[4]) : 0;
            fullMoves = (parts.length >= 6) ? Integer.parseInt(parts[5]) : 1;
            moveHistory.clear();
            isGameOver = false;
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    public String toPGN(String whiteName, String blackName) {
        StringBuilder sb = new StringBuilder();
        sb.append("[Event \"LibreChess RS Match\"]\n");
        sb.append("[Site \"Android / Termux\"]\n");
        java.text.SimpleDateFormat sdf = new java.text.SimpleDateFormat("yyyy.MM.dd", java.util.Locale.US);
        sb.append("[Date \"").append(sdf.format(new java.util.Date())).append("\"]\n");
        sb.append("[White \"").append(whiteName != null && !whiteName.isEmpty() ? whiteName : "White").append("\"]\n");
        sb.append("[Black \"").append(blackName != null && !blackName.isEmpty() ? blackName : "Black").append("\"]\n");
        String res = isGameOver ? (gameResult.isEmpty() ? "*" : gameResult) : "*";
        sb.append("[Result \"").append(res).append("\"]\n\n");

        for (int i = 0; i < moveHistory.size(); i++) {
            if (i % 2 == 0) {
                sb.append((i / 2 + 1)).append(". ");
            }
            sb.append(moveHistory.get(i).san).append(" ");
        }
        sb.append(res);
        return sb.toString();
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
