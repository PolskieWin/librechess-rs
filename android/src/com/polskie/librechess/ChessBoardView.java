package com.polskie.librechess;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.os.Build;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;

import java.util.ArrayList;
import java.util.List;

public class ChessBoardView extends View {
    public interface OnMoveListener {
        void onMove(int from, int to, int promoType);
    }

    private ChessEngine engine;
    private OnMoveListener moveListener;
    private Vibrator vibrator;
    private boolean hapticsEnabled = true;

    private int selectedSquare = -1;
    private List<Integer> legalDestinations = new ArrayList<>();
    private int pendingPromotionFrom = -1;
    private int pendingPromotionTo = -1;
    private int pendingPromotionColor = ChessEngine.WHITE;
    private int lastFrom = -1;
    private int lastTo = -1;
    private int hintFrom = -1;
    private int hintTo = -1;
    private boolean showThreats = true;
    private boolean flipped = false;
    private int boardTheme = 0;
    private int pieceStyle = 0; // 0: Lichess (cburnett), 1: Modern Minimal

    public static final int PIECE_STYLE_LICHESS = 0;
    public static final int PIECE_STYLE_MODERN = 1;
    public static final int PIECE_STYLE_TOURNAMENT = 2;

    public static final String[] PIECE_STYLE_NAMES = {
        "Lichess (cburnett)",
        "Modern Minimal (Neo)",
        "Classic Tournament (Staunton)"
    };

    // Official Lichess Board themes: {lightColor, darkColor}
    public static final String[] THEME_NAMES = {
        "Lichess Green",
        "Lichess Wood",
        "Lichess Blue",
        "Lichess Canvas",
        "Lichess Charcoal",
        "Midnight OLED (Pure Black)"
    };

    private static final int[][] THEMES = {
        {0xFFEEEED2, 0xFF769656}, // Lichess Green (Classic)
        {0xFFF0D9B5, 0xFFB58863}, // Lichess Wood (Brown)
        {0xFFDEE3E6, 0xFF8CA2AD}, // Lichess Blue
        {0xFFE3C16F, 0xFFB88B4A}, // Lichess Canvas (Sand)
        {0xFF8A939E, 0xFF4A545E}, // Lichess Charcoal
        {0xFF334155, 0xFF000000}  // Midnight OLED (True Pure Black)
    };

    private Paint lightSqPaint;
    private Paint darkSqPaint;
    private Paint selectPaint;
    private Paint lastMovePaint;
    private Paint hintPaint;
    private Paint hintRingPaint;
    private Paint checkPaint;
    private Paint threatDotPaint;
    private Paint legalDotPaint;
    private Paint legalRingPaint;
    private Paint coordPaint;

    // Piece vector paints
    private Paint whitePieceFill;
    private Paint whitePieceStroke;
    private Paint blackPieceFill;
    private Paint blackPieceStroke;
    private Paint pieceDetailStroke;
    private float density = 1.0f;

    public ChessBoardView(Context context) {
        super(context);
        init(context);
    }

    public ChessBoardView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init(context);
    }

    public ChessBoardView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init(context);
    }

    private void init(Context context) {
        vibrator = (Vibrator) context.getSystemService(Context.VIBRATOR_SERVICE);
        density = getResources().getDisplayMetrics().density;

        lightSqPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        darkSqPaint = new Paint(Paint.ANTI_ALIAS_FLAG);

        selectPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        selectPaint.setColor(0x77F59E0B); // Amber translucent
        selectPaint.setStyle(Paint.Style.FILL);

        lastMovePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        lastMovePaint.setColor(0x5538BDF8); // Sky blue translucent
        lastMovePaint.setStyle(Paint.Style.FILL);

        hintPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        hintPaint.setColor(0x6606B6D4); // Cyan translucent
        hintPaint.setStyle(Paint.Style.FILL);

        hintRingPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        hintRingPaint.setColor(0xEE06B6D4); // Cyan solid ring
        hintRingPaint.setStyle(Paint.Style.STROKE);
        hintRingPaint.setStrokeWidth(3.5f * density);

        threatDotPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        threatDotPaint.setColor(0xBBEF4444); // Crimson warning
        threatDotPaint.setStyle(Paint.Style.FILL);

        checkPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        checkPaint.setColor(0x88EF4444); // Red translucent
        checkPaint.setStyle(Paint.Style.FILL);

        legalDotPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        legalDotPaint.setColor(0x6610B981); // Emerald translucent dot
        legalDotPaint.setStyle(Paint.Style.FILL);

        legalRingPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        legalRingPaint.setColor(0x99EF4444); // Crimson ring
        legalRingPaint.setStyle(Paint.Style.STROKE);
        legalRingPaint.setStrokeWidth(3.5f * density);

        coordPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        coordPaint.setTextSize(11f * density);
        coordPaint.setFakeBoldText(true);

        whitePieceFill = new Paint(Paint.ANTI_ALIAS_FLAG);
        whitePieceFill.setColor(0xFFFFFFFF);
        whitePieceFill.setStyle(Paint.Style.FILL);

        whitePieceStroke = new Paint(Paint.ANTI_ALIAS_FLAG);
        whitePieceStroke.setColor(0xFF1E293B);
        whitePieceStroke.setStyle(Paint.Style.STROKE);
        whitePieceStroke.setStrokeWidth(2.2f * density);

        blackPieceFill = new Paint(Paint.ANTI_ALIAS_FLAG);
        blackPieceFill.setColor(0xFF18181B);
        blackPieceFill.setStyle(Paint.Style.FILL);

        blackPieceStroke = new Paint(Paint.ANTI_ALIAS_FLAG);
        blackPieceStroke.setColor(0xFFE2E8F0);
        blackPieceStroke.setStyle(Paint.Style.STROKE);
        blackPieceStroke.setStrokeWidth(2.2f * density);

        pieceDetailStroke = new Paint(Paint.ANTI_ALIAS_FLAG);
        pieceDetailStroke.setStyle(Paint.Style.STROKE);
        pieceDetailStroke.setStrokeWidth(1.6f * density);

        updateThemePaints();
    }

    private void updateThemePaints() {
        int[] theme = THEMES[boardTheme % THEMES.length];
        lightSqPaint.setColor(theme[0]);
        darkSqPaint.setColor(theme[1]);
    }

    public void setEngine(ChessEngine engine) {
        this.engine = engine;
        clearHighlights();
        clearHint();
        invalidate();
    }

    public void setOnMoveListener(OnMoveListener listener) {
        this.moveListener = listener;
    }

    public void setBoardTheme(int themeIdx) {
        this.boardTheme = themeIdx;
        updateThemePaints();
        invalidate();
    }

    public int getBoardTheme() {
        return this.boardTheme;
    }

    public void setPieceStyle(int style) {
        this.pieceStyle = style;
        invalidate();
    }

    public int getPieceStyle() {
        return this.pieceStyle;
    }

    public void setShowThreats(boolean show) {
        this.showThreats = show;
        invalidate();
    }

    public boolean isShowThreats() {
        return this.showThreats;
    }

    public void setHint(int from, int to) {
        this.hintFrom = from;
        this.hintTo = to;
        invalidate();
    }

    public void clearHint() {
        this.hintFrom = -1;
        this.hintTo = -1;
        invalidate();
    }

    public void setHapticsEnabled(boolean enabled) {
        this.hapticsEnabled = enabled;
    }

    public void setFlipped(boolean flipped) {
        this.flipped = flipped;
        invalidate();
    }

    public boolean isFlipped() {
        return flipped;
    }

    public void setLastMove(int from, int to) {
        this.lastFrom = from;
        this.lastTo = to;
        clearHint();
        invalidate();
    }

    public void clearHighlights() {
        selectedSquare = -1;
        legalDestinations.clear();
        pendingPromotionFrom = -1;
        pendingPromotionTo = -1;
        invalidate();
    }

    public void vibrate(int durationMs) {
        if (!hapticsEnabled || vibrator == null) return;
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vibrator.vibrate(VibrationEffect.createOneShot(durationMs, VibrationEffect.DEFAULT_AMPLITUDE));
            } else {
                vibrator.vibrate(durationMs);
            }
        } catch (Exception ignored) {}
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int w = MeasureSpec.getSize(widthMeasureSpec);
        int h = MeasureSpec.getSize(heightMeasureSpec);
        int size = Math.min(w, h);
        if (size <= 0) size = w > 0 ? w : 300;
        setMeasuredDimension(size, size);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        if (engine == null) return;

        float sq = getWidth() / 8.0f;
        int checkKingSq = -1;
        if (engine.isKingInCheck(ChessEngine.WHITE)) {
            for (int i = 0; i < 64; i++) {
                if (engine.board[i] == (ChessEngine.WHITE | ChessEngine.KING)) { checkKingSq = i; break; }
            }
        } else if (engine.isKingInCheck(ChessEngine.BLACK)) {
            for (int i = 0; i < 64; i++) {
                if (engine.board[i] == (ChessEngine.BLACK | ChessEngine.KING)) { checkKingSq = i; break; }
            }
        }

        int activeColor = engine.whiteToMove ? ChessEngine.WHITE : ChessEngine.BLACK;
        int oppColor = engine.whiteToMove ? ChessEngine.BLACK : ChessEngine.WHITE;

        // Draw 64 squares
        for (int r = 0; r < 8; r++) {
            for (int f = 0; f < 8; f++) {
                int sqIdx = (!flipped) ? (r * 8 + f) : ((7 - r) * 8 + (7 - f));
                float left = f * sq;
                float top = r * sq;
                float right = left + sq;
                float bottom = top + sq;

                boolean isLight = ((r + f) % 2 == 0);
                canvas.drawRect(left, top, right, bottom, isLight ? lightSqPaint : darkSqPaint);

                // Last move highlight
                if (sqIdx == lastFrom || sqIdx == lastTo) {
                    canvas.drawRect(left, top, right, bottom, lastMovePaint);
                }

                // Tactical Hint highlight
                if (sqIdx == hintFrom || sqIdx == hintTo) {
                    canvas.drawRect(left, top, right, bottom, hintPaint);
                }

                // Selected square highlight
                if (sqIdx == selectedSquare) {
                    canvas.drawRect(left, top, right, bottom, selectPaint);
                }

                // Check highlight
                if (sqIdx == checkKingSq) {
                    canvas.drawRect(left, top, right, bottom, checkPaint);
                }

                // Coordinates text
                if (f == 0) {
                    int rankNum = (!flipped) ? (8 - r) : (r + 1);
                    coordPaint.setColor(isLight ? darkSqPaint.getColor() : lightSqPaint.getColor());
                    canvas.drawText(String.valueOf(rankNum), left + 4, top + 14 * density, coordPaint);
                }
                if (r == 7) {
                    char fileChar = (char) ((!flipped) ? ('a' + f) : ('h' - f));
                    coordPaint.setColor(isLight ? darkSqPaint.getColor() : lightSqPaint.getColor());
                    canvas.drawText(String.valueOf(fileChar), right - 12 * density, bottom - 4, coordPaint);
                }

                // Draw piece
                int p = engine.board[sqIdx];
                if (p != ChessEngine.EMPTY) {
                    drawPiece(canvas, p, left, top, sq);

                    // Threat highlight: friendly hanging piece warning
                    if (showThreats && ChessEngine.colorOf(p) == activeColor) {
                        if (engine.isSquareAttacked(sqIdx, oppColor) && !engine.isSquareAttacked(sqIdx, activeColor)) {
                            canvas.drawCircle(right - 8 * density, top + 8 * density, 4.0f * density, threatDotPaint);
                        }
                    }
                }

                // Legal move indicators
                if (legalDestinations.contains(sqIdx)) {
                    float cx = left + sq * 0.5f;
                    float cy = top + sq * 0.5f;
                    if (p == ChessEngine.EMPTY && sqIdx != engine.enPassantSq) {
                        canvas.drawCircle(cx, cy, sq * 0.16f, legalDotPaint);
                    } else {
                        canvas.drawCircle(cx, cy, sq * 0.42f, legalRingPaint);
                    }
                }

                // Hint destination ring
                if (sqIdx == hintTo) {
                    float cx = left + sq * 0.5f;
                    float cy = top + sq * 0.5f;
                    canvas.drawCircle(cx, cy, sq * 0.42f, hintRingPaint);
                }
            }
        }

        // Lichess-style in-board promotion popup overlay
        if (pendingPromotionFrom != -1 && pendingPromotionTo != -1) {
            drawPromotionOverlay(canvas, sq);
        }
    }

    private void drawPiece(Canvas canvas, int piece, float x, float y, float size) {
        int color = ChessEngine.colorOf(piece);
        int type = ChessEngine.typeOf(piece);

        boolean isWhite = (color == ChessEngine.WHITE);
        Paint fill = isWhite ? whitePieceFill : blackPieceFill;
        Paint stroke = isWhite ? whitePieceStroke : blackPieceStroke;

        pieceDetailStroke.setColor(isWhite ? 0xFF1E293B : 0xFFFFFFFF);

        float cx = x + size * 0.5f;
        float cy = y + size * 0.5f;
        float s = size * 0.82f;
        float topY = cy - s * 0.45f;
        float botY = cy + s * 0.45f;

        if (pieceStyle == PIECE_STYLE_MODERN) {
            drawModernPiece(canvas, type, isWhite, cx, cy, s, topY, botY, fill, stroke, pieceDetailStroke);
        } else if (pieceStyle == PIECE_STYLE_TOURNAMENT) {
            drawTournamentPiece(canvas, type, isWhite, cx, cy, s, topY, botY, fill, stroke, pieceDetailStroke);
        } else {
            drawLichessPiece(canvas, type, isWhite, cx, cy, s, topY, botY, fill, stroke, pieceDetailStroke);
        }
    }

    private void drawLichessPiece(Canvas canvas, int type, boolean isWhite, float cx, float cy, float s, float topY, float botY, Paint fill, Paint stroke, Paint detail) {
        Path path = new Path();
        switch (type) {
            case ChessEngine.PAWN: {
                path.moveTo(cx - s * 0.28f, botY);
                path.lineTo(cx + s * 0.28f, botY);
                path.lineTo(cx + s * 0.22f, botY - s * 0.12f);
                path.quadTo(cx + s * 0.08f, cy, cx + s * 0.12f, cy - s * 0.08f);
                path.lineTo(cx - s * 0.12f, cy - s * 0.08f);
                path.quadTo(cx - s * 0.08f, cy, cx - s * 0.22f, botY - s * 0.12f);
                path.close();
                canvas.drawPath(path, fill);
                canvas.drawPath(path, stroke);

                float hr = s * 0.17f;
                float hy = topY + hr + s * 0.04f;
                canvas.drawCircle(cx, hy, hr, fill);
                canvas.drawCircle(cx, hy, hr, stroke);
                canvas.drawLine(cx - s * 0.12f, cy - s * 0.08f, cx + s * 0.12f, cy - s * 0.08f, detail);
                break;
            }
            case ChessEngine.KNIGHT: {
                path.moveTo(cx - s * 0.30f, botY);
                path.lineTo(cx + s * 0.30f, botY);
                path.lineTo(cx + s * 0.24f, botY - s * 0.12f);
                path.quadTo(cx + s * 0.32f, cy + s * 0.14f, cx + s * 0.28f, cy + s * 0.04f);
                path.quadTo(cx + s * 0.34f, cy - s * 0.06f, cx + s * 0.23f, cy - s * 0.16f);
                path.quadTo(cx + s * 0.26f, cy - s * 0.28f, cx + s * 0.14f, topY + s * 0.06f);
                path.lineTo(cx + s * 0.08f, topY - s * 0.05f);
                path.lineTo(cx + s * 0.01f, topY + s * 0.06f);
                path.lineTo(cx - s * 0.16f, cy - s * 0.14f);
                path.quadTo(cx - s * 0.32f, cy - s * 0.08f, cx - s * 0.34f, cy - s * 0.02f);
                path.lineTo(cx - s * 0.34f, cy + s * 0.03f);
                path.lineTo(cx - s * 0.27f, cy + s * 0.07f);
                path.lineTo(cx - s * 0.16f, cy + s * 0.08f);
                path.quadTo(cx - s * 0.11f, cy + s * 0.17f, cx - s * 0.18f, cy + s * 0.24f);
                path.lineTo(cx - s * 0.24f, botY - s * 0.12f);
                path.close();
                canvas.drawPath(path, fill);
                canvas.drawPath(path, stroke);

                canvas.drawCircle(cx - s * 0.13f, cy - s * 0.08f, s * 0.04f, detail);
                canvas.drawLine(cx + s * 0.16f, cy - s * 0.16f, cx + s * 0.04f, cy - s * 0.10f, detail);
                canvas.drawLine(cx + s * 0.19f, cy - s * 0.04f, cx + s * 0.07f, cy + s * 0.02f, detail);
                canvas.drawLine(cx + s * 0.21f, cy + s * 0.08f, cx + s * 0.09f, cy + s * 0.14f, detail);
                canvas.drawCircle(cx - s * 0.29f, cy, s * 0.022f, detail);
                break;
            }
            case ChessEngine.BISHOP: {
                path.moveTo(cx - s * 0.32f, botY);
                path.lineTo(cx + s * 0.32f, botY);
                path.lineTo(cx + s * 0.24f, botY - s * 0.12f);
                path.quadTo(cx + s * 0.10f, cy + s * 0.10f, cx + s * 0.18f, cy);
                path.quadTo(cx + s * 0.24f, cy - s * 0.24f, cx, topY + s * 0.06f);
                path.quadTo(cx - s * 0.24f, cy - s * 0.24f, cx - s * 0.18f, cy);
                path.quadTo(cx - s * 0.10f, cy + s * 0.10f, cx - s * 0.24f, botY - s * 0.12f);
                path.close();
                canvas.drawPath(path, fill);
                canvas.drawPath(path, stroke);

                canvas.drawCircle(cx, topY + s * 0.03f, s * 0.045f, fill);
                canvas.drawCircle(cx, topY + s * 0.03f, s * 0.045f, stroke);
                canvas.drawLine(cx - s * 0.04f, cy - s * 0.20f, cx + s * 0.14f, cy - s * 0.06f, detail);
                canvas.drawLine(cx + s * 0.02f, cy - s * 0.16f, cx + s * 0.08f, cy - s * 0.10f, detail);
                canvas.drawLine(cx - s * 0.18f, cy + s * 0.12f, cx + s * 0.18f, cy + s * 0.12f, detail);
                break;
            }
            case ChessEngine.ROOK: {
                path.moveTo(cx - s * 0.34f, botY);
                path.lineTo(cx + s * 0.34f, botY);
                path.lineTo(cx + s * 0.26f, botY - s * 0.12f);
                path.lineTo(cx + s * 0.20f, cy - s * 0.15f);
                path.lineTo(cx + s * 0.32f, cy - s * 0.20f);
                path.lineTo(cx + s * 0.32f, topY);
                path.lineTo(cx + s * 0.18f, topY);
                path.lineTo(cx + s * 0.18f, topY + s * 0.10f);
                path.lineTo(cx + s * 0.07f, topY + s * 0.10f);
                path.lineTo(cx + s * 0.07f, topY);
                path.lineTo(cx - s * 0.07f, topY);
                path.lineTo(cx - s * 0.07f, topY + s * 0.10f);
                path.lineTo(cx - s * 0.18f, topY + s * 0.10f);
                path.lineTo(cx - s * 0.18f, topY);
                path.lineTo(cx - s * 0.32f, topY);
                path.lineTo(cx - s * 0.32f, cy - s * 0.20f);
                path.lineTo(cx - s * 0.20f, cy - s * 0.15f);
                path.lineTo(cx - s * 0.26f, botY - s * 0.12f);
                path.close();
                canvas.drawPath(path, fill);
                canvas.drawPath(path, stroke);
                canvas.drawLine(cx - s * 0.24f, cy - s * 0.17f, cx + s * 0.24f, cy - s * 0.17f, detail);
                break;
            }
            case ChessEngine.QUEEN: {
                path.moveTo(cx - s * 0.36f, botY);
                path.lineTo(cx + s * 0.36f, botY);
                path.lineTo(cx + s * 0.28f, botY - s * 0.12f);
                path.quadTo(cx + s * 0.14f, cy + s * 0.10f, cx + s * 0.22f, cy - s * 0.06f);
                path.lineTo(cx + s * 0.36f, topY + s * 0.04f);
                path.lineTo(cx + s * 0.20f, cy - s * 0.16f);
                path.lineTo(cx + s * 0.18f, topY);
                path.lineTo(cx + s * 0.08f, cy - s * 0.18f);
                path.lineTo(cx, topY - s * 0.04f);
                path.lineTo(cx - s * 0.08f, cy - s * 0.18f);
                path.lineTo(cx - s * 0.18f, topY);
                path.lineTo(cx - s * 0.20f, cy - s * 0.16f);
                path.lineTo(cx - s * 0.36f, topY + s * 0.04f);
                path.lineTo(cx - s * 0.22f, cy - s * 0.06f);
                path.quadTo(cx - s * 0.14f, cy + s * 0.10f, cx - s * 0.28f, botY - s * 0.12f);
                path.close();
                canvas.drawPath(path, fill);
                canvas.drawPath(path, stroke);

                float pr = s * 0.035f;
                canvas.drawCircle(cx - s * 0.36f, topY + s * 0.04f, pr, fill);
                canvas.drawCircle(cx - s * 0.36f, topY + s * 0.04f, pr, stroke);
                canvas.drawCircle(cx - s * 0.18f, topY, pr, fill);
                canvas.drawCircle(cx - s * 0.18f, topY, pr, stroke);
                canvas.drawCircle(cx, topY - s * 0.04f, pr, fill);
                canvas.drawCircle(cx, topY - s * 0.04f, pr, stroke);
                canvas.drawCircle(cx + s * 0.18f, topY, pr, fill);
                canvas.drawCircle(cx + s * 0.18f, topY, pr, stroke);
                canvas.drawCircle(cx + s * 0.36f, topY + s * 0.04f, pr, fill);
                canvas.drawCircle(cx + s * 0.36f, topY + s * 0.04f, pr, stroke);
                canvas.drawLine(cx - s * 0.20f, cy + s * 0.12f, cx + s * 0.20f, cy + s * 0.12f, detail);
                break;
            }
            case ChessEngine.KING: {
                path.moveTo(cx - s * 0.36f, botY);
                path.lineTo(cx + s * 0.36f, botY);
                path.lineTo(cx + s * 0.28f, botY - s * 0.12f);
                path.quadTo(cx + s * 0.12f, cy + s * 0.10f, cx + s * 0.24f, cy - s * 0.04f);
                path.quadTo(cx + s * 0.30f, cy - s * 0.22f, cx, cy - s * 0.26f);
                path.quadTo(cx - s * 0.30f, cy - s * 0.22f, cx - s * 0.24f, cy - s * 0.04f);
                path.quadTo(cx - s * 0.12f, cy + s * 0.10f, cx - s * 0.28f, botY - s * 0.12f);
                path.close();
                canvas.drawPath(path, fill);
                canvas.drawPath(path, stroke);

                float crossTop = topY - s * 0.05f;
                float crossBot = cy - s * 0.26f;
                float crossMidY = crossTop + (crossBot - crossTop) * 0.38f;
                float cw = s * 0.09f;

                canvas.drawLine(cx, crossTop, cx, crossBot, stroke);
                canvas.drawLine(cx - cw, crossMidY, cx + cw, crossMidY, stroke);
                canvas.drawLine(cx - cw, crossMidY - s * 0.025f, cx - cw, crossMidY + s * 0.025f, stroke);
                canvas.drawLine(cx + cw, crossMidY - s * 0.025f, cx + cw, crossMidY + s * 0.025f, stroke);
                canvas.drawLine(cx - s * 0.025f, crossTop, cx + s * 0.025f, crossTop, stroke);
                canvas.drawLine(cx - s * 0.20f, cy + s * 0.12f, cx + s * 0.20f, cy + s * 0.12f, detail);
                break;
            }
        }
    }

    private void drawModernPiece(Canvas canvas, int type, boolean isWhite, float cx, float cy, float s, float topY, float botY, Paint fill, Paint stroke, Paint detail) {
        Path path = new Path();
        switch (type) {
            case ChessEngine.PAWN: {
                path.moveTo(cx - s * 0.24f, botY);
                path.lineTo(cx + s * 0.24f, botY);
                path.lineTo(cx + s * 0.14f, cy - s * 0.04f);
                path.lineTo(cx - s * 0.14f, cy - s * 0.04f);
                path.close();
                canvas.drawPath(path, fill);
                canvas.drawPath(path, stroke);
                float hr = s * 0.16f;
                canvas.drawCircle(cx, topY + hr + s * 0.05f, hr, fill);
                canvas.drawCircle(cx, topY + hr + s * 0.05f, hr, stroke);
                break;
            }
            case ChessEngine.KNIGHT: {
                // Geometric sharp modern steed
                path.moveTo(cx - s * 0.26f, botY);
                path.lineTo(cx + s * 0.26f, botY);
                path.lineTo(cx + s * 0.22f, cy + s * 0.10f);
                path.lineTo(cx + s * 0.18f, cy - s * 0.15f);
                path.lineTo(cx + s * 0.08f, topY - s * 0.03f); // sharp ear
                path.lineTo(cx - s * 0.02f, topY + s * 0.08f);
                path.lineTo(cx - s * 0.32f, cy - s * 0.02f); // sharp muzzle
                path.lineTo(cx - s * 0.26f, cy + s * 0.12f);
                path.lineTo(cx - s * 0.14f, cy + s * 0.12f);
                path.lineTo(cx - s * 0.20f, botY - s * 0.08f);
                path.close();
                canvas.drawPath(path, fill);
                canvas.drawPath(path, stroke);
                canvas.drawLine(cx - s * 0.15f, cy - s * 0.08f, cx - s * 0.07f, cy - s * 0.08f, detail);
                break;
            }
            case ChessEngine.BISHOP: {
                path.moveTo(cx - s * 0.28f, botY);
                path.lineTo(cx + s * 0.28f, botY);
                path.lineTo(cx + s * 0.16f, cy + s * 0.12f);
                path.lineTo(cx + s * 0.18f, cy - s * 0.12f);
                path.lineTo(cx, topY + s * 0.02f); // spearhead
                path.lineTo(cx - s * 0.18f, cy - s * 0.12f);
                path.lineTo(cx - s * 0.16f, cy + s * 0.12f);
                path.close();
                canvas.drawPath(path, fill);
                canvas.drawPath(path, stroke);
                canvas.drawLine(cx, cy - s * 0.25f, cx, cy - s * 0.05f, detail);
                break;
            }
            case ChessEngine.ROOK: {
                path.moveTo(cx - s * 0.30f, botY);
                path.lineTo(cx + s * 0.30f, botY);
                path.lineTo(cx + s * 0.22f, topY + s * 0.18f);
                path.lineTo(cx + s * 0.30f, topY);
                path.lineTo(cx + s * 0.10f, topY);
                path.lineTo(cx + s * 0.10f, topY + s * 0.12f);
                path.lineTo(cx - s * 0.10f, topY + s * 0.12f);
                path.lineTo(cx - s * 0.10f, topY);
                path.lineTo(cx - s * 0.30f, topY);
                path.lineTo(cx - s * 0.22f, topY + s * 0.18f);
                path.close();
                canvas.drawPath(path, fill);
                canvas.drawPath(path, stroke);
                break;
            }
            case ChessEngine.QUEEN: {
                path.moveTo(cx - s * 0.34f, botY);
                path.lineTo(cx + s * 0.34f, botY);
                path.lineTo(cx + s * 0.24f, cy + s * 0.05f);
                path.lineTo(cx + s * 0.35f, topY + s * 0.02f);
                path.lineTo(cx + s * 0.14f, cy - s * 0.12f);
                path.lineTo(cx, topY - s * 0.03f);
                path.lineTo(cx - s * 0.14f, cy - s * 0.12f);
                path.lineTo(cx - s * 0.35f, topY + s * 0.02f);
                path.lineTo(cx - s * 0.24f, cy + s * 0.05f);
                path.close();
                canvas.drawPath(path, fill);
                canvas.drawPath(path, stroke);
                break;
            }
            case ChessEngine.KING: {
                path.moveTo(cx - s * 0.34f, botY);
                path.lineTo(cx + s * 0.34f, botY);
                path.lineTo(cx + s * 0.22f, cy + s * 0.08f);
                path.lineTo(cx + s * 0.24f, cy - s * 0.18f);
                path.lineTo(cx, cy - s * 0.24f);
                path.lineTo(cx - s * 0.24f, cy - s * 0.18f);
                path.lineTo(cx - s * 0.22f, cy + s * 0.08f);
                path.close();
                canvas.drawPath(path, fill);
                canvas.drawPath(path, stroke);

                float crossTop = topY - s * 0.04f;
                float crossBot = cy - s * 0.24f;
                float crossMid = crossTop + (crossBot - crossTop) * 0.45f;
                float arm = s * 0.08f;
                canvas.drawLine(cx, crossTop, cx, crossBot, stroke);
                canvas.drawLine(cx - arm, crossMid, cx + arm, crossMid, stroke);
                break;
            }
        }
    }

    private void drawTournamentPiece(Canvas canvas, int type, boolean isWhite, float cx, float cy, float s, float topY, float botY, Paint fill, Paint stroke, Paint detail) {
        Path path = new Path();
        switch (type) {
            case ChessEngine.PAWN: {
                // Wide weighted base
                path.moveTo(cx - s * 0.32f, botY);
                path.lineTo(cx + s * 0.32f, botY);
                path.lineTo(cx + s * 0.26f, botY - s * 0.10f);
                path.lineTo(cx - s * 0.26f, botY - s * 0.10f);
                path.close();
                canvas.drawPath(path, fill);
                canvas.drawPath(path, stroke);

                Path body = new Path();
                body.moveTo(cx - s * 0.22f, botY - s * 0.10f);
                body.lineTo(cx + s * 0.22f, botY - s * 0.10f);
                body.quadTo(cx + s * 0.06f, cy, cx + s * 0.14f, cy - s * 0.12f);
                body.lineTo(cx - s * 0.14f, cy - s * 0.12f);
                body.quadTo(cx - s * 0.06f, cy, cx - s * 0.22f, botY - s * 0.10f);
                body.close();
                canvas.drawPath(body, fill);
                canvas.drawPath(body, stroke);

                float hr = s * 0.18f;
                float hy = topY + hr + s * 0.02f;
                canvas.drawCircle(cx, hy, hr, fill);
                canvas.drawCircle(cx, hy, hr, stroke);
                break;
            }
            case ChessEngine.KNIGHT: {
                // Classical tournament sculpted horse
                path.moveTo(cx - s * 0.32f, botY);
                path.lineTo(cx + s * 0.32f, botY);
                path.lineTo(cx + s * 0.24f, botY - s * 0.12f);
                path.quadTo(cx + s * 0.35f, cy + s * 0.08f, cx + s * 0.25f, cy - s * 0.10f);
                path.quadTo(cx + s * 0.28f, cy - s * 0.26f, cx + s * 0.10f, topY + s * 0.04f);
                path.lineTo(cx + s * 0.06f, topY - s * 0.06f); // ear
                path.lineTo(cx - s * 0.02f, topY + s * 0.06f);
                path.lineTo(cx - s * 0.22f, cy - s * 0.12f);
                path.quadTo(cx - s * 0.35f, cy - s * 0.04f, cx - s * 0.32f, cy + s * 0.06f);
                path.lineTo(cx - s * 0.20f, cy + s * 0.08f);
                path.quadTo(cx - s * 0.12f, cy + s * 0.18f, cx - s * 0.20f, cy + s * 0.26f);
                path.lineTo(cx - s * 0.24f, botY - s * 0.12f);
                path.close();
                canvas.drawPath(path, fill);
                canvas.drawPath(path, stroke);

                canvas.drawCircle(cx - s * 0.15f, cy - s * 0.07f, s * 0.035f, detail);
                canvas.drawLine(cx + s * 0.12f, cy - s * 0.12f, cx + s * 0.22f, cy - s * 0.06f, detail);
                canvas.drawLine(cx + s * 0.15f, cy + s * 0.02f, cx + s * 0.24f, cy + s * 0.08f, detail);
                break;
            }
            case ChessEngine.BISHOP: {
                path.moveTo(cx - s * 0.34f, botY);
                path.lineTo(cx + s * 0.34f, botY);
                path.lineTo(cx + s * 0.24f, botY - s * 0.12f);
                path.quadTo(cx + s * 0.12f, cy + s * 0.08f, cx + s * 0.20f, cy - s * 0.02f);
                path.quadTo(cx + s * 0.22f, cy - s * 0.26f, cx, topY + s * 0.06f);
                path.quadTo(cx - s * 0.22f, cy - s * 0.26f, cx - s * 0.20f, cy - s * 0.02f);
                path.quadTo(cx - s * 0.12f, cy + s * 0.08f, cx - s * 0.24f, botY - s * 0.12f);
                path.close();
                canvas.drawPath(path, fill);
                canvas.drawPath(path, stroke);

                canvas.drawCircle(cx, topY + s * 0.03f, s * 0.045f, fill);
                canvas.drawCircle(cx, topY + s * 0.03f, s * 0.045f, stroke);
                canvas.drawLine(cx - s * 0.08f, cy - s * 0.20f, cx + s * 0.10f, cy - s * 0.08f, detail);
                break;
            }
            case ChessEngine.ROOK: {
                path.moveTo(cx - s * 0.36f, botY);
                path.lineTo(cx + s * 0.36f, botY);
                path.lineTo(cx + s * 0.28f, botY - s * 0.12f);
                path.lineTo(cx + s * 0.22f, cy - s * 0.14f);
                path.lineTo(cx + s * 0.34f, cy - s * 0.20f);
                path.lineTo(cx + s * 0.34f, topY);
                path.lineTo(cx + s * 0.22f, topY);
                path.lineTo(cx + s * 0.22f, topY + s * 0.10f);
                path.lineTo(cx + s * 0.10f, topY + s * 0.10f);
                path.lineTo(cx + s * 0.10f, topY);
                path.lineTo(cx - s * 0.10f, topY);
                path.lineTo(cx - s * 0.10f, topY + s * 0.10f);
                path.lineTo(cx - s * 0.22f, topY + s * 0.10f);
                path.lineTo(cx - s * 0.22f, topY);
                path.lineTo(cx - s * 0.34f, topY);
                path.lineTo(cx - s * 0.34f, cy - s * 0.20f);
                path.lineTo(cx - s * 0.22f, cy - s * 0.14f);
                path.lineTo(cx - s * 0.28f, botY - s * 0.12f);
                path.close();
                canvas.drawPath(path, fill);
                canvas.drawPath(path, stroke);
                canvas.drawLine(cx - s * 0.26f, cy - s * 0.16f, cx + s * 0.26f, cy - s * 0.16f, detail);
                break;
            }
            case ChessEngine.QUEEN: {
                path.moveTo(cx - s * 0.36f, botY);
                path.lineTo(cx + s * 0.36f, botY);
                path.lineTo(cx + s * 0.28f, botY - s * 0.12f);
                path.quadTo(cx + s * 0.12f, cy + s * 0.10f, cx + s * 0.24f, cy - s * 0.08f);
                path.lineTo(cx + s * 0.36f, topY + s * 0.04f);
                path.lineTo(cx + s * 0.18f, cy - s * 0.18f);
                path.lineTo(cx + s * 0.18f, topY);
                path.lineTo(cx + s * 0.08f, cy - s * 0.18f);
                path.lineTo(cx, topY - s * 0.04f);
                path.lineTo(cx - s * 0.08f, cy - s * 0.18f);
                path.lineTo(cx - s * 0.18f, topY);
                path.lineTo(cx - s * 0.18f, cy - s * 0.18f);
                path.lineTo(cx - s * 0.36f, topY + s * 0.04f);
                path.lineTo(cx - s * 0.24f, cy - s * 0.08f);
                path.quadTo(cx - s * 0.12f, cy + s * 0.10f, cx - s * 0.28f, botY - s * 0.12f);
                path.close();
                canvas.drawPath(path, fill);
                canvas.drawPath(path, stroke);

                float pr = s * 0.032f;
                canvas.drawCircle(cx - s * 0.36f, topY + s * 0.04f, pr, fill);
                canvas.drawCircle(cx - s * 0.18f, topY, pr, fill);
                canvas.drawCircle(cx, topY - s * 0.04f, pr, fill);
                canvas.drawCircle(cx + s * 0.18f, topY, pr, fill);
                canvas.drawCircle(cx + s * 0.36f, topY + s * 0.04f, pr, fill);
                break;
            }
            case ChessEngine.KING: {
                path.moveTo(cx - s * 0.38f, botY);
                path.lineTo(cx + s * 0.38f, botY);
                path.lineTo(cx + s * 0.30f, botY - s * 0.12f);
                path.quadTo(cx + s * 0.14f, cy + s * 0.10f, cx + s * 0.26f, cy - s * 0.04f);
                path.quadTo(cx + s * 0.30f, cy - s * 0.22f, cx, cy - s * 0.28f);
                path.quadTo(cx - s * 0.30f, cy - s * 0.22f, cx - s * 0.26f, cy - s * 0.04f);
                path.quadTo(cx - s * 0.14f, cy + s * 0.10f, cx - s * 0.30f, botY - s * 0.12f);
                path.close();
                canvas.drawPath(path, fill);
                canvas.drawPath(path, stroke);

                float crossTop = topY - s * 0.04f;
                float crossBot = cy - s * 0.28f;
                float crossMid = crossTop + (crossBot - crossTop) * 0.4f;
                float arm = s * 0.09f;
                canvas.drawLine(cx, crossTop, cx, crossBot, stroke);
                canvas.drawLine(cx - arm, crossMid, cx + arm, crossMid, stroke);
                break;
            }
        }
    }

    private int getPromotionPieceAt(float touchX, float touchY) {
        if (pendingPromotionFrom == -1 || pendingPromotionTo == -1) return -1;
        float sq = getWidth() / 8.0f;
        int targetF = pendingPromotionTo % 8;
        int targetR = pendingPromotionTo / 8;
        int col = (!flipped) ? targetF : (7 - targetF);

        int touchedCol = (int) (touchX / sq);
        if (touchedCol != col) return -1;

        int touchedRow = (int) (touchY / sq);
        int[] choices = {ChessEngine.QUEEN, ChessEngine.KNIGHT, ChessEngine.ROOK, ChessEngine.BISHOP};

        int displayRow = (!flipped) ? targetR : (7 - targetR);
        if (displayRow == 0) {
            if (touchedRow >= 0 && touchedRow < 4) {
                return choices[touchedRow];
            }
        } else {
            if (touchedRow <= 7 && touchedRow >= 4) {
                return choices[7 - touchedRow];
            }
        }
        return -1;
    }

    private void drawPromotionOverlay(Canvas canvas, float sq) {
        if (pendingPromotionFrom == -1 || pendingPromotionTo == -1) return;

        // Dim background scrim
        Paint dimPaint = new Paint();
        dimPaint.setColor(0x88000000);
        canvas.drawRect(0, 0, getWidth(), getHeight(), dimPaint);

        int targetF = pendingPromotionTo % 8;
        int targetR = pendingPromotionTo / 8;
        int col = (!flipped) ? targetF : (7 - targetF);
        int displayRow = (!flipped) ? targetR : (7 - targetR);

        float left = col * sq;
        float right = left + sq;
        float top, bottom;

        if (displayRow == 0) {
            top = 0;
            bottom = 4 * sq;
        } else {
            top = 4 * sq;
            bottom = 8 * sq;
        }

        // Material You Card Container
        Paint cardPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        cardPaint.setStyle(Paint.Style.FILL);
        cardPaint.setColor(0xFF1E293B);

        Paint borderPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        borderPaint.setStyle(Paint.Style.STROKE);
        borderPaint.setStrokeWidth(2.5f * density);
        borderPaint.setColor(0xFF06B6D4);

        RectF cardRect = new RectF(left + 2, top + 2, right - 2, bottom - 2);
        canvas.drawRoundRect(cardRect, 10 * density, 10 * density, cardPaint);
        canvas.drawRoundRect(cardRect, 10 * density, 10 * density, borderPaint);

        int[] choices = {ChessEngine.QUEEN, ChessEngine.KNIGHT, ChessEngine.ROOK, ChessEngine.BISHOP};
        Paint dividerPaint = new Paint();
        dividerPaint.setColor(0x33FFFFFF);
        dividerPaint.setStrokeWidth(1.0f * density);

        for (int i = 0; i < 4; i++) {
            float slotTop = (displayRow == 0) ? (i * sq) : ((7 - i) * sq);
            if (i > 0) {
                float lineY = (displayRow == 0) ? slotTop : (slotTop + sq);
                canvas.drawLine(left + 6, lineY, right - 6, lineY, dividerPaint);
            }
            int piece = pendingPromotionColor | choices[i];
            drawPiece(canvas, piece, left, slotTop, sq);
        }
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (engine == null || engine.isGameOver) return super.onTouchEvent(event);

        if (pendingPromotionFrom != -1) {
            if (event.getAction() == MotionEvent.ACTION_UP) {
                int promoPiece = getPromotionPieceAt(event.getX(), event.getY());
                if (promoPiece != -1) {
                    int from = pendingPromotionFrom;
                    int to = pendingPromotionTo;
                    pendingPromotionFrom = -1;
                    pendingPromotionTo = -1;
                    vibrate(30);
                    invalidate();
                    if (moveListener != null) {
                        moveListener.onMove(from, to, promoPiece);
                    }
                } else {
                    pendingPromotionFrom = -1;
                    pendingPromotionTo = -1;
                    invalidate();
                }
            }
            return true;
        }

        if (event.getAction() == MotionEvent.ACTION_UP) {
            float sq = getWidth() / 8.0f;
            int f = (int) (event.getX() / sq);
            int r = (int) (event.getY() / sq);

            if (f < 0 || f > 7 || r < 0 || r > 7) return true;

            int sqIdx = (!flipped) ? (r * 8 + f) : ((7 - r) * 8 + (7 - f));

            if (selectedSquare == -1) {
                // Selecting a piece
                int p = engine.board[sqIdx];
                int currentTurn = engine.whiteToMove ? ChessEngine.WHITE : ChessEngine.BLACK;
                if (p != ChessEngine.EMPTY && ChessEngine.colorOf(p) == currentTurn) {
                    selectedSquare = sqIdx;
                    legalDestinations = engine.getLegalMoves(sqIdx);
                    vibrate(15);
                    invalidate();
                }
            } else {
                // Clicking destination or changing selection
                if (legalDestinations.contains(sqIdx)) {
                    int from = selectedSquare;
                    selectedSquare = -1;
                    legalDestinations.clear();
                    if (engine.isPromotionMove(from, sqIdx)) {
                        pendingPromotionFrom = from;
                        pendingPromotionTo = sqIdx;
                        pendingPromotionColor = ChessEngine.colorOf(engine.board[from]);
                        vibrate(25);
                        invalidate();
                    } else {
                        invalidate();
                        if (moveListener != null) {
                            moveListener.onMove(from, sqIdx, ChessEngine.QUEEN);
                        }
                    }
                } else {
                    int p = engine.board[sqIdx];
                    int currentTurn = engine.whiteToMove ? ChessEngine.WHITE : ChessEngine.BLACK;
                    if (p != ChessEngine.EMPTY && ChessEngine.colorOf(p) == currentTurn) {
                        selectedSquare = sqIdx;
                        legalDestinations = engine.getLegalMoves(sqIdx);
                        vibrate(15);
                        invalidate();
                    } else {
                        selectedSquare = -1;
                        legalDestinations.clear();
                        invalidate();
                    }
                }
            }
        }
        return true;
    }
}
