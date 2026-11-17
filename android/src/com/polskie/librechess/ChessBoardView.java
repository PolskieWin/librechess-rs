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
    private boolean flipped = false;
    private int boardTheme = 0; // 0: Emerald, 1: Slate, 2: Wood, 3: Ocean

    // Board themes: {lightColor, darkColor}
    private static final int[][] THEMES = {
        {0xFFEEEED2, 0xFF769656}, // Emerald
        {0xFFCBD5E1, 0xFF475569}, // Slate
        {0xFFF0D9B5, 0xFFB58863}, // Wood
        {0xFFDBEAFE, 0xFF2563EB}, // Ocean
        {0xFF334155, 0xFF0F172A}  // Midnight Dark (OLED)
    };

    private Paint lightSqPaint;
    private Paint darkSqPaint;
    private Paint selectPaint;
    private Paint lastMovePaint;
    private Paint checkPaint;
    private Paint legalDotPaint;
    private Paint legalRingPaint;
    private Paint coordPaint;

    // Piece vector paints
    private Paint whitePieceFill;
    private Paint whitePieceStroke;
    private Paint blackPieceFill;
    private Paint blackPieceStroke;
    private Paint pieceDetailStroke;

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

        lightSqPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        darkSqPaint = new Paint(Paint.ANTI_ALIAS_FLAG);

        selectPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        selectPaint.setColor(0x77F59E0B); // Amber translucent
        selectPaint.setStyle(Paint.Style.FILL);

        lastMovePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        lastMovePaint.setColor(0x5538BDF8); // Sky blue translucent
        lastMovePaint.setStyle(Paint.Style.FILL);

        checkPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        checkPaint.setColor(0x88EF4444); // Red translucent
        checkPaint.setStyle(Paint.Style.FILL);

        legalDotPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        legalDotPaint.setColor(0x6610B981); // Emerald translucent dot
        legalDotPaint.setStyle(Paint.Style.FILL);

        float density = getResources().getDisplayMetrics().density;
        legalRingPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        legalRingPaint.setColor(0x99EF4444); // Crimson ring
        legalRingPaint.setStyle(Paint.Style.STROKE);
        legalRingPaint.setStrokeWidth(3.5f * density);

        coordPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        coordPaint.setTextSize(11f * density);
        coordPaint.setFakeBoldText(true);

        whitePieceFill = new Paint(Paint.ANTI_ALIAS_FLAG);
        whitePieceFill.setColor(0xFFF8FAFC);
        whitePieceFill.setStyle(Paint.Style.FILL);

        whitePieceStroke = new Paint(Paint.ANTI_ALIAS_FLAG);
        whitePieceStroke.setColor(0xFF1E293B);
        whitePieceStroke.setStyle(Paint.Style.STROKE);
        whitePieceStroke.setStrokeWidth(2.2f * density);

        blackPieceFill = new Paint(Paint.ANTI_ALIAS_FLAG);
        blackPieceFill.setColor(0xFF1E293B);
        blackPieceFill.setStyle(Paint.Style.FILL);

        blackPieceStroke = new Paint(Paint.ANTI_ALIAS_FLAG);
        blackPieceStroke.setColor(0xFFE2E8F0);
        blackPieceStroke.setStyle(Paint.Style.STROKE);
        blackPieceStroke.setStrokeWidth(2.2f * density);

        pieceDetailStroke = new Paint(Paint.ANTI_ALIAS_FLAG);
        pieceDetailStroke.setStyle(Paint.Style.STROKE);
        pieceDetailStroke.setStrokeWidth(1.5f * density);

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
                    canvas.drawText(String.valueOf(rankNum), left + 4, top + 14, coordPaint);
                }
                if (r == 7) {
                    char fileChar = (char) ((!flipped) ? ('a' + f) : ('h' - f));
                    coordPaint.setColor(isLight ? darkSqPaint.getColor() : lightSqPaint.getColor());
                    canvas.drawText(String.valueOf(fileChar), right - 12, bottom - 4, coordPaint);
                }

                // Draw piece
                int p = engine.board[sqIdx];
                if (p != ChessEngine.EMPTY) {
                    drawPiece(canvas, p, left, top, sq);
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
            }
        }
    }

    private void drawPiece(Canvas canvas, int piece, float x, float y, float size) {
        int color = ChessEngine.colorOf(piece);
        int type = ChessEngine.typeOf(piece);

        boolean isWhite = (color == ChessEngine.WHITE);
        Paint fill = isWhite ? whitePieceFill : blackPieceFill;
        Paint stroke = isWhite ? whitePieceStroke : blackPieceStroke;

        pieceDetailStroke.setColor(isWhite ? 0xFF1E293B : 0xFFCBD5E1);

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
                // Body
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
                break;
            }
            case ChessEngine.KNIGHT: {
                // Horse head silhouette
                path.moveTo(cx - s * 0.32f, botY);
                path.lineTo(cx + s * 0.32f, botY);
                path.lineTo(cx + s * 0.26f, botY - s * 0.12f);
                // Arched back mane
                path.quadTo(cx + s * 0.34f, cy - s * 0.1f, cx + s * 0.12f, topY + s * 0.04f);
                // Ear
                path.lineTo(cx + s * 0.08f, topY - s * 0.04f);
                path.lineTo(cx, topY + s * 0.08f);
                // Snout / Muzzle
                path.lineTo(cx - s * 0.32f, cy - s * 0.08f);
                path.lineTo(cx - s * 0.30f, cy + s * 0.04f);
                // Lower jaw to chest
                path.lineTo(cx - s * 0.14f, cy + s * 0.02f);
                path.quadTo(cx - s * 0.18f, cy + s * 0.24f, cx - s * 0.26f, botY - s * 0.12f);
                path.close();
                canvas.drawPath(path, fill);
                canvas.drawPath(path, stroke);

                // Eye dot
                canvas.drawCircle(cx - s * 0.12f, cy - s * 0.10f, s * 0.035f, pieceDetailStroke);
                // Mane lines
                canvas.drawLine(cx + s * 0.08f, cy - s * 0.12f, cx + s * 0.22f, cy - s * 0.06f, pieceDetailStroke);
                canvas.drawLine(cx + s * 0.12f, cy, cx + s * 0.24f, cy + s * 0.08f, pieceDetailStroke);
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

                // Diagonal mitre cleft
                canvas.drawLine(cx - s * 0.06f, cy - s * 0.22f, cx + s * 0.10f, cy - s * 0.08f, pieceDetailStroke);
                break;
            }
            case ChessEngine.ROOK: {
                // Base
                path.moveTo(cx - s * 0.34f, botY);
                path.lineTo(cx + s * 0.34f, botY);
                path.lineTo(cx + s * 0.26f, botY - s * 0.12f);
                // Tower body
                path.lineTo(cx + s * 0.20f, cy - s * 0.15f);
                // Battlements shelf
                path.lineTo(cx + s * 0.32f, cy - s * 0.22f);
                // Right crenel
                path.lineTo(cx + s * 0.32f, topY);
                path.lineTo(cx + s * 0.18f, topY);
                path.lineTo(cx + s * 0.18f, topY + s * 0.10f);
                // Center gap
                path.lineTo(cx + s * 0.07f, topY + s * 0.10f);
                path.lineTo(cx + s * 0.07f, topY);
                path.lineTo(cx - s * 0.07f, topY);
                path.lineTo(cx - s * 0.07f, topY + s * 0.10f);
                // Left crenel
                path.lineTo(cx - s * 0.18f, topY + s * 0.10f);
                path.lineTo(cx - s * 0.18f, topY);
                path.lineTo(cx - s * 0.32f, topY);
                path.lineTo(cx - s * 0.32f, cy - s * 0.22f);
                path.lineTo(cx - s * 0.20f, cy - s * 0.15f);
                path.lineTo(cx - s * 0.26f, botY - s * 0.12f);
                path.close();
                canvas.drawPath(path, fill);
                canvas.drawPath(path, stroke);
                break;
            }
            case ChessEngine.QUEEN: {
                // Base
                path.moveTo(cx - s * 0.36f, botY);
                path.lineTo(cx + s * 0.36f, botY);
                path.lineTo(cx + s * 0.28f, botY - s * 0.12f);
                // Gown
                path.quadTo(cx + s * 0.14f, cy + s * 0.08f, cx + s * 0.20f, cy - s * 0.08f);
                // 5 Coronet spikes
                path.lineTo(cx + s * 0.36f, topY + s * 0.04f);
                path.lineTo(cx + s * 0.18f, cy - s * 0.18f);
                path.lineTo(cx + s * 0.18f, topY);
                path.lineTo(cx + s * 0.07f, cy - s * 0.18f);
                path.lineTo(cx, topY - s * 0.03f);
                path.lineTo(cx - s * 0.07f, cy - s * 0.18f);
                path.lineTo(cx - s * 0.18f, topY);
                path.lineTo(cx - s * 0.18f, cy - s * 0.18f);
                path.lineTo(cx - s * 0.36f, topY + s * 0.04f);
                path.lineTo(cx - s * 0.20f, cy - s * 0.08f);
                path.quadTo(cx - s * 0.14f, cy + s * 0.08f, cx - s * 0.28f, botY - s * 0.12f);
                path.close();
                canvas.drawPath(path, fill);
                canvas.drawPath(path, stroke);

                // Pearl pearls on the 5 tips
                float pr = s * 0.032f;
                canvas.drawCircle(cx - s * 0.36f, topY + s * 0.04f, pr, fill);
                canvas.drawCircle(cx - s * 0.18f, topY, pr, fill);
                canvas.drawCircle(cx, topY - s * 0.03f, pr, fill);
                canvas.drawCircle(cx + s * 0.18f, topY, pr, fill);
                canvas.drawCircle(cx + s * 0.36f, topY + s * 0.04f, pr, fill);
                break;
            }
            case ChessEngine.KING: {
                // Base
                path.moveTo(cx - s * 0.36f, botY);
                path.lineTo(cx + s * 0.36f, botY);
                path.lineTo(cx + s * 0.28f, botY - s * 0.12f);
                // Regal mantle
                path.quadTo(cx + s * 0.12f, cy + s * 0.10f, cx + s * 0.24f, cy - s * 0.04f);
                path.quadTo(cx + s * 0.30f, cy - s * 0.22f, cx, cy - s * 0.28f);
                path.quadTo(cx - s * 0.30f, cy - s * 0.22f, cx - s * 0.24f, cy - s * 0.04f);
                path.quadTo(cx - s * 0.12f, cy + s * 0.10f, cx - s * 0.28f, botY - s * 0.12f);
                path.close();
                canvas.drawPath(path, fill);
                canvas.drawPath(path, stroke);

                // Finial Cross (+) on crown
                float crossTop = topY - s * 0.02f;
                float crossBot = cy - s * 0.28f;
                float crossW = s * 0.09f;
                float crossH = (crossBot - crossTop);
                float crossY = crossTop + crossH * 0.4f;

                // Vertical stem
                canvas.drawLine(cx, crossTop, cx, crossBot, stroke);
                // Horizontal arm
                canvas.drawLine(cx - crossW, crossY, cx + crossW, crossY, stroke);
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
