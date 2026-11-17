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
        void onMove(int from, int to);
    }

    private ChessEngine engine;
    private OnMoveListener moveListener;
    private Vibrator vibrator;
    private boolean hapticsEnabled = true;

    private int selectedSquare = -1;
    private List<Integer> legalDestinations = new ArrayList<>();
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

    // Official Lichess Board themes: {lightColor, darkColor}
    public static final String[] THEME_NAMES = {
        "Lichess Green",
        "Lichess Wood",
        "Lichess Blue",
        "Lichess Canvas",
        "Lichess Charcoal",
        "Midnight OLED"
    };

    private static final int[][] THEMES = {
        {0xFFEEEED2, 0xFF769656}, // Lichess Green (Classic)
        {0xFFF0D9B5, 0xFFB58863}, // Lichess Wood (Brown)
        {0xFFDEE3E6, 0xFF8CA2AD}, // Lichess Blue
        {0xFFE3C16F, 0xFFB88B4A}, // Lichess Canvas (Sand)
        {0xFF8A939E, 0xFF4A545E}, // Lichess Charcoal
        {0xFF334155, 0xFF050810}  // Midnight OLED (Pure Dark)
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

        Path path = new Path();

        switch (type) {
            case ChessEngine.PAWN: {
                // Base
                path.moveTo(cx - s * 0.28f, botY);
                path.lineTo(cx + s * 0.28f, botY);
                path.lineTo(cx + s * 0.22f, botY - s * 0.12f);
                // Waist
                path.quadTo(cx + s * 0.08f, cy, cx + s * 0.12f, cy - s * 0.08f);
                // Collar
                path.lineTo(cx - s * 0.12f, cy - s * 0.08f);
                path.quadTo(cx - s * 0.08f, cy, cx - s * 0.22f, botY - s * 0.12f);
                path.close();
                canvas.drawPath(path, fill);
                canvas.drawPath(path, stroke);

                // Head sphere
                float hr = s * 0.17f;
                float hy = topY + hr + s * 0.04f;
                canvas.drawCircle(cx, hy, hr, fill);
                canvas.drawCircle(cx, hy, hr, stroke);

                // Collar trim
                canvas.drawLine(cx - s * 0.12f, cy - s * 0.08f, cx + s * 0.12f, cy - s * 0.08f, pieceDetailStroke);
                break;
            }
            case ChessEngine.KNIGHT: {
                // Authentic Lichess cburnett horse
                path.moveTo(cx - s * 0.30f, botY);
                path.lineTo(cx + s * 0.30f, botY);
                path.lineTo(cx + s * 0.24f, botY - s * 0.12f);

                // 3 scalloped notches of the mane
                path.quadTo(cx + s * 0.32f, cy + s * 0.14f, cx + s * 0.28f, cy + s * 0.04f);
                path.quadTo(cx + s * 0.34f, cy - s * 0.06f, cx + s * 0.23f, cy - s * 0.16f);
                path.quadTo(cx + s * 0.26f, cy - s * 0.28f, cx + s * 0.14f, topY + s * 0.06f);

                // Forward perked ear
                path.lineTo(cx + s * 0.08f, topY - s * 0.05f);
                path.lineTo(cx + s * 0.01f, topY + s * 0.06f);

                // Forehead down to snout / muzzle
                path.lineTo(cx - s * 0.16f, cy - s * 0.14f);
                path.quadTo(cx - s * 0.32f, cy - s * 0.08f, cx - s * 0.34f, cy - s * 0.02f);
                path.lineTo(cx - s * 0.34f, cy + s * 0.03f); // nostril
                path.lineTo(cx - s * 0.27f, cy + s * 0.07f); // mouth

                // Lower jaw and throat to chest
                path.lineTo(cx - s * 0.16f, cy + s * 0.08f);
                path.quadTo(cx - s * 0.11f, cy + s * 0.17f, cx - s * 0.18f, cy + s * 0.24f);
                path.lineTo(cx - s * 0.24f, botY - s * 0.12f);
                path.close();

                canvas.drawPath(path, fill);
                canvas.drawPath(path, stroke);

                // Eye (circle with inner pupil dot)
                canvas.drawCircle(cx - s * 0.13f, cy - s * 0.08f, s * 0.04f, pieceDetailStroke);
                // Mane scallop accent lines
                canvas.drawLine(cx + s * 0.16f, cy - s * 0.16f, cx + s * 0.04f, cy - s * 0.10f, pieceDetailStroke);
                canvas.drawLine(cx + s * 0.19f, cy - s * 0.04f, cx + s * 0.07f, cy + s * 0.02f, pieceDetailStroke);
                canvas.drawLine(cx + s * 0.21f, cy + s * 0.08f, cx + s * 0.09f, cy + s * 0.14f, pieceDetailStroke);
                // Nostril accent
                canvas.drawCircle(cx - s * 0.29f, cy, s * 0.022f, pieceDetailStroke);
                break;
            }
            case ChessEngine.BISHOP: {
                // Base
                path.moveTo(cx - s * 0.32f, botY);
                path.lineTo(cx + s * 0.32f, botY);
                path.lineTo(cx + s * 0.24f, botY - s * 0.12f);
                // Torso
                path.quadTo(cx + s * 0.10f, cy + s * 0.10f, cx + s * 0.18f, cy);
                // Mitre body
                path.quadTo(cx + s * 0.24f, cy - s * 0.24f, cx, topY + s * 0.06f);
                path.quadTo(cx - s * 0.24f, cy - s * 0.24f, cx - s * 0.18f, cy);
                path.quadTo(cx - s * 0.10f, cy + s * 0.10f, cx - s * 0.24f, botY - s * 0.12f);
                path.close();
                canvas.drawPath(path, fill);
                canvas.drawPath(path, stroke);

                // Finial sphere on top
                canvas.drawCircle(cx, topY + s * 0.03f, s * 0.045f, fill);
                canvas.drawCircle(cx, topY + s * 0.03f, s * 0.045f, stroke);

                // Diagonal mitre cleft slit
                canvas.drawLine(cx - s * 0.04f, cy - s * 0.20f, cx + s * 0.14f, cy - s * 0.06f, pieceDetailStroke);
                canvas.drawLine(cx + s * 0.02f, cy - s * 0.16f, cx + s * 0.08f, cy - s * 0.10f, pieceDetailStroke);
                // Collar band
                canvas.drawLine(cx - s * 0.18f, cy + s * 0.12f, cx + s * 0.18f, cy + s * 0.12f, pieceDetailStroke);
                break;
            }
            case ChessEngine.ROOK: {
                // Base
                path.moveTo(cx - s * 0.34f, botY);
                path.lineTo(cx + s * 0.34f, botY);
                path.lineTo(cx + s * 0.26f, botY - s * 0.12f);
                // Tower trunk
                path.lineTo(cx + s * 0.20f, cy - s * 0.15f);
                // Battlements cornice
                path.lineTo(cx + s * 0.32f, cy - s * 0.20f);
                // 3 Crenellations & 2 embrasure gaps (cburnett)
                path.lineTo(cx + s * 0.32f, topY);
                path.lineTo(cx + s * 0.18f, topY);
                path.lineTo(cx + s * 0.18f, topY + s * 0.10f); // gap 2
                path.lineTo(cx + s * 0.07f, topY + s * 0.10f);
                path.lineTo(cx + s * 0.07f, topY);            // center crenel
                path.lineTo(cx - s * 0.07f, topY);
                path.lineTo(cx - s * 0.07f, topY + s * 0.10f); // gap 1
                path.lineTo(cx - s * 0.18f, topY + s * 0.10f);
                path.lineTo(cx - s * 0.18f, topY);
                path.lineTo(cx - s * 0.32f, topY);
                path.lineTo(cx - s * 0.32f, cy - s * 0.20f);
                path.lineTo(cx - s * 0.20f, cy - s * 0.15f);
                path.lineTo(cx - s * 0.26f, botY - s * 0.12f);
                path.close();
                canvas.drawPath(path, fill);
                canvas.drawPath(path, stroke);

                // Horizontal embrasure trim line
                canvas.drawLine(cx - s * 0.24f, cy - s * 0.17f, cx + s * 0.24f, cy - s * 0.17f, pieceDetailStroke);
                break;
            }
            case ChessEngine.QUEEN: {
                // Base
                path.moveTo(cx - s * 0.36f, botY);
                path.lineTo(cx + s * 0.36f, botY);
                path.lineTo(cx + s * 0.28f, botY - s * 0.12f);
                // Bodice
                path.quadTo(cx + s * 0.14f, cy + s * 0.10f, cx + s * 0.22f, cy - s * 0.06f);

                // 5 Coronet Spikes (cburnett)
                path.lineTo(cx + s * 0.36f, topY + s * 0.04f); // spike 5
                path.lineTo(cx + s * 0.20f, cy - s * 0.16f);
                path.lineTo(cx + s * 0.18f, topY);            // spike 4
                path.lineTo(cx + s * 0.08f, cy - s * 0.18f);
                path.lineTo(cx, topY - s * 0.04f);            // spike 3 (center apex)
                path.lineTo(cx - s * 0.08f, cy - s * 0.18f);
                path.lineTo(cx - s * 0.18f, topY);            // spike 2
                path.lineTo(cx - s * 0.20f, cy - s * 0.16f);
                path.lineTo(cx - s * 0.36f, topY + s * 0.04f); // spike 1

                path.lineTo(cx - s * 0.22f, cy - s * 0.06f);
                path.quadTo(cx - s * 0.14f, cy + s * 0.10f, cx - s * 0.28f, botY - s * 0.12f);
                path.close();

                canvas.drawPath(path, fill);
                canvas.drawPath(path, stroke);

                // 5 Pearl jewels on the tips
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

                // Decorative horizontal waist band
                canvas.drawLine(cx - s * 0.20f, cy + s * 0.12f, cx + s * 0.20f, cy + s * 0.12f, pieceDetailStroke);
                break;
            }
            case ChessEngine.KING: {
                // Base
                path.moveTo(cx - s * 0.36f, botY);
                path.lineTo(cx + s * 0.36f, botY);
                path.lineTo(cx + s * 0.28f, botY - s * 0.12f);
                // Regal crown mantle
                path.quadTo(cx + s * 0.12f, cy + s * 0.10f, cx + s * 0.24f, cy - s * 0.04f);
                path.quadTo(cx + s * 0.30f, cy - s * 0.22f, cx, cy - s * 0.26f);
                path.quadTo(cx - s * 0.30f, cy - s * 0.22f, cx - s * 0.24f, cy - s * 0.04f);
                path.quadTo(cx - s * 0.12f, cy + s * 0.10f, cx - s * 0.28f, botY - s * 0.12f);
                path.close();

                canvas.drawPath(path, fill);
                canvas.drawPath(path, stroke);

                // Cross Pattée finial (Lichess King)
                float crossTop = topY - s * 0.05f;
                float crossBot = cy - s * 0.26f;
                float crossMidY = crossTop + (crossBot - crossTop) * 0.38f;
                float cw = s * 0.09f;

                // Vertical stem
                canvas.drawLine(cx, crossTop, cx, crossBot, stroke);
                // Horizontal arm
                canvas.drawLine(cx - cw, crossMidY, cx + cw, crossMidY, stroke);
                // Flared endpoints on the cross
                canvas.drawLine(cx - cw, crossMidY - s * 0.025f, cx - cw, crossMidY + s * 0.025f, stroke);
                canvas.drawLine(cx + cw, crossMidY - s * 0.025f, cx + cw, crossMidY + s * 0.025f, stroke);
                canvas.drawLine(cx - s * 0.025f, crossTop, cx + s * 0.025f, crossTop, stroke);

                // Decorative mantle band
                canvas.drawLine(cx - s * 0.20f, cy + s * 0.12f, cx + s * 0.20f, cy + s * 0.12f, pieceDetailStroke);
                break;
            }
        }
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (engine == null || engine.isGameOver) return super.onTouchEvent(event);

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
                    invalidate();
                    if (moveListener != null) {
                        moveListener.onMove(from, sqIdx);
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
