package com.polskie.librechess;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.DialogInterface;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class MainActivity extends Activity implements ChessBoardView.OnMoveListener {
    private static final String PREFS_NAME = "LibreChessPrefs";

    private ChessEngine engine;
    private ChessBoardView boardView;

    // UI Widgets
    private LinearLayout rootLayout;
    private TextView txtAppTitle;
    private TextView btnUserBadge;
    private Button btnSettings;
    private TextView txtOpponentName;
    private TextView txtOpponentClock;
    private TextView txtPlayerName;
    private TextView txtPlayerClock;
    private TextView txtStatusBar;
    private TextView txtReviewInfo;

    private LinearLayout layoutGameControls;
    private Button btnNewGame;
    private Button btnBot;
    private Button btnFlip;
    private Button btnHint;
    private Button btnResign;
    private Button btnReview;

    private LinearLayout layoutReviewControls;
    private Button btnRevStart;
    private Button btnRevPrev;
    private Button btnRevNext;
    private Button btnRevEnd;
    private Button btnRevExit;

    // State & Preferences
    public static final int THEME_MODE_DEEP_SLATE = 0;
    public static final int THEME_MODE_OLED = 1;
    public static final int THEME_MODE_LIGHT = 2;
    public static final String[] THEME_MODE_NAMES = {
        "Deep Slate Dark (#0F172A)",
        "Midnight OLED Pure Black (#000000)",
        "Clean Light Mode"
    };

    private SharedPreferences prefs;
    private String playerName = "Polskie";
    private int playerElo = 100;
    private int themeMode = THEME_MODE_DEEP_SLATE;
    private boolean isDarkMode = true;
    private int boardTheme = 0;
    private int pieceStyle = 0; // 0: Lichess, 1: Modern, 2: Tournament
    private boolean showThreats = true;
    private boolean hapticsEnabled = true;
    private int botLevel = 100;
    private String serverUrl = "http://127.0.0.1:8080";

    // Game Clocks
    private Handler timerHandler = new Handler(Looper.getMainLooper());
    private int whiteTimeSeconds = 600; // 10 minutes
    private int blackTimeSeconds = 600;
    private boolean clockRunning = false;

    // Review state
    private boolean inReviewMode = false;
    private int reviewMoveIndex = 0;
    private List<ChessEngine> reviewBoardStates = new ArrayList<>();

    private Runnable clockRunnable = new Runnable() {
        @Override
        public void run() {
            if (!clockRunning || engine.isGameOver) return;

            if (engine.whiteToMove) {
                if (whiteTimeSeconds > 0) whiteTimeSeconds--;
                else {
                    engine.isGameOver = true;
                    engine.gameResult = "0-1";
                    engine.gameOverReason = "White out of time";
                    onGameOver();
                }
            } else {
                if (blackTimeSeconds > 0) blackTimeSeconds--;
                else {
                    engine.isGameOver = true;
                    engine.gameResult = "1-0";
                    engine.gameOverReason = "Black out of time";
                    onGameOver();
                }
            }

            updateClockDisplays();
            if (clockRunning) {
                timerHandler.postDelayed(this, 1000);
            }
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Edge-to-edge transparent system bars
        setupEdgeToEdge();

        setContentView(R.layout.activity_main);

        prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        loadPreferences();

        bindViews();
        applyTheme();

        engine = new ChessEngine();
        boardView.setEngine(engine);
        boardView.setOnMoveListener(this);
        boardView.setBoardTheme(boardTheme);
        boardView.setPieceStyle(pieceStyle);
        boardView.setShowThreats(showThreats);
        boardView.setHapticsEnabled(hapticsEnabled);

        setupClickListeners();
        startNewGame();
    }

    private void setupEdgeToEdge() {
        Window window = getWindow();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            window.clearFlags(WindowManager.LayoutParams.FLAG_TRANSLUCENT_STATUS);
            window.clearFlags(WindowManager.LayoutParams.FLAG_TRANSLUCENT_NAVIGATION);
            window.addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS);
            window.setStatusBarColor(Color.TRANSPARENT);
            window.setNavigationBarColor(Color.TRANSPARENT);
        }

        View decorView = window.getDecorView();
        int flags = View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                  | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                  | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION;

        boolean isLight = (themeMode == THEME_MODE_LIGHT);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && isLight) {
            flags |= View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR;
        }

        decorView.setSystemUiVisibility(flags);
    }

    private void loadPreferences() {
        playerName = prefs.getString("player_name", "Polskie");
        playerElo = prefs.getInt("player_elo", 100);
        themeMode = prefs.getInt("theme_mode", prefs.getBoolean("is_dark_mode", true) ? THEME_MODE_DEEP_SLATE : THEME_MODE_LIGHT);
        isDarkMode = (themeMode != THEME_MODE_LIGHT);
        boardTheme = prefs.getInt("board_theme", 0);
        pieceStyle = prefs.getInt("piece_style", 0);
        showThreats = prefs.getBoolean("show_threats", true);
        hapticsEnabled = prefs.getBoolean("haptics_enabled", true);
        botLevel = prefs.getInt("bot_level", 100);
        serverUrl = prefs.getString("server_url", "http://127.0.0.1:8080");
    }

    private void savePreferences() {
        SharedPreferences.Editor editor = prefs.edit();
        editor.putString("player_name", playerName);
        editor.putInt("player_elo", playerElo);
        editor.putInt("theme_mode", themeMode);
        editor.putBoolean("is_dark_mode", themeMode != THEME_MODE_LIGHT);
        editor.putInt("board_theme", boardTheme);
        editor.putInt("piece_style", pieceStyle);
        editor.putBoolean("show_threats", showThreats);
        editor.putBoolean("haptics_enabled", hapticsEnabled);
        editor.putInt("bot_level", botLevel);
        editor.putString("server_url", serverUrl);
        editor.apply();
    }

    private void bindViews() {
        rootLayout = (LinearLayout) findViewById(R.id.root_layout);
        txtAppTitle = (TextView) findViewById(R.id.txt_app_title);
        btnUserBadge = (TextView) findViewById(R.id.btn_user_badge);
        btnSettings = (Button) findViewById(R.id.btn_settings);
        txtOpponentName = (TextView) findViewById(R.id.txt_opponent_name);
        txtOpponentClock = (TextView) findViewById(R.id.txt_opponent_clock);
        txtPlayerName = (TextView) findViewById(R.id.txt_player_name);
        txtPlayerClock = (TextView) findViewById(R.id.txt_player_clock);
        txtStatusBar = (TextView) findViewById(R.id.txt_status_bar);
        txtReviewInfo = (TextView) findViewById(R.id.txt_review_info);

        boardView = (ChessBoardView) findViewById(R.id.chess_board);

        layoutGameControls = (LinearLayout) findViewById(R.id.layout_game_controls);
        btnNewGame = (Button) findViewById(R.id.btn_new_game);
        btnBot = (Button) findViewById(R.id.btn_bot);
        btnFlip = (Button) findViewById(R.id.btn_flip);
        btnHint = (Button) findViewById(R.id.btn_hint);
        btnResign = (Button) findViewById(R.id.btn_resign);
        btnReview = (Button) findViewById(R.id.btn_review);

        layoutReviewControls = (LinearLayout) findViewById(R.id.layout_review_controls);
        btnRevStart = (Button) findViewById(R.id.btn_rev_start);
        btnRevPrev = (Button) findViewById(R.id.btn_rev_prev);
        btnRevNext = (Button) findViewById(R.id.btn_rev_next);
        btnRevEnd = (Button) findViewById(R.id.btn_rev_end);
        btnRevExit = (Button) findViewById(R.id.btn_rev_exit);

        // WindowInsets padding for edge-to-edge
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT_WATCH) {
            rootLayout.setOnApplyWindowInsetsListener(new View.OnApplyWindowInsetsListener() {
                @Override
                public android.view.WindowInsets onApplyWindowInsets(View v, android.view.WindowInsets insets) {
                    v.setPadding(
                        insets.getSystemWindowInsetLeft() + 24,
                        insets.getSystemWindowInsetTop() + 16,
                        insets.getSystemWindowInsetRight() + 24,
                        insets.getSystemWindowInsetBottom() + 16
                    );
                    return insets;
                }
            });
        }
    }

    private void applyTheme() {
        int bgColor;
        int textTitleColor;
        int textSecondaryColor;
        int clockColor;

        if (themeMode == THEME_MODE_OLED) {
            bgColor = 0xFF000000;
            textTitleColor = 0xFFFFFFFF;
            textSecondaryColor = 0xFF94A3B8;
            clockColor = 0xFF22C55E; // High-contrast green clock for OLED Pure Black
        } else if (themeMode == THEME_MODE_LIGHT) {
            bgColor = 0xFFF8FAFC;
            textTitleColor = 0xFF0F172A;
            textSecondaryColor = 0xFF475569;
            clockColor = 0xFF0F172A;
        } else { // THEME_MODE_DEEP_SLATE
            bgColor = 0xFF0F172A;
            textTitleColor = 0xFFF8FAFC;
            textSecondaryColor = 0xFF94A3B8;
            clockColor = 0xFF38BDF8;
        }

        rootLayout.setBackgroundColor(bgColor);
        txtAppTitle.setTextColor(textTitleColor);
        txtOpponentClock.setTextColor(clockColor);
        txtPlayerClock.setTextColor(clockColor);
        txtOpponentName.setTextColor(textSecondaryColor);

        btnUserBadge.setText(playerName + " (" + playerElo + ")");
        txtPlayerName.setText(playerName + " (" + playerElo + ")");
        txtOpponentName.setText("Bot " + botLevel);
    }

    private void setupClickListeners() {
        btnUserBadge.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                showNameDialog();
            }
        });

        btnSettings.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                showSettingsDialog();
            }
        });

        btnNewGame.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                startNewGame();
            }
        });

        btnBot.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                showBotPickerDialog();
            }
        });

        btnFlip.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                boardView.setFlipped(!boardView.isFlipped());
            }
        });

        btnHint.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (inReviewMode || engine.isGameOver) return;
                ChessEngine.TacticalHint hint = engine.getTacticalHint();
                if (hint != null) {
                    boardView.setHint(hint.from, hint.to);
                    txtStatusBar.setText("💡 " + hint.san + " • " + hint.explanation);
                    txtStatusBar.setTextColor(0xFF06B6D4);
                    boardView.vibrate(25);
                }
            }
        });

        btnResign.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                showResignDialog();
            }
        });

        btnReview.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                enterReviewMode();
            }
        });

        // Review stepper listeners
        btnRevStart.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                reviewMoveIndex = 0;
                applyReviewIndex();
            }
        });

        btnRevPrev.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (reviewMoveIndex > 0) {
                    reviewMoveIndex--;
                    applyReviewIndex();
                }
            }
        });

        btnRevNext.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (reviewMoveIndex < reviewBoardStates.size() - 1) {
                    reviewMoveIndex++;
                    applyReviewIndex();
                }
            }
        });

        btnRevEnd.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                reviewMoveIndex = reviewBoardStates.size() - 1;
                applyReviewIndex();
            }
        });

        btnRevExit.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                exitReviewMode();
            }
        });
    }

    private void startNewGame() {
        clockRunning = false;
        timerHandler.removeCallbacks(clockRunnable);

        inReviewMode = false;
        layoutGameControls.setVisibility(View.VISIBLE);
        layoutReviewControls.setVisibility(View.GONE);
        txtReviewInfo.setVisibility(View.GONE);
        btnReview.setVisibility(View.GONE);

        engine.reset();
        boardView.setEngine(engine);
        boardView.clearHighlights();

        whiteTimeSeconds = 600;
        blackTimeSeconds = 600;
        updateClockDisplays();

        txtStatusBar.setText("White to move");
        txtStatusBar.setTextColor(0xFF38BDF8);

        clockRunning = true;
        timerHandler.postDelayed(clockRunnable, 1000);
    }

    @Override
    public void onMove(int from, int to) {
        if (inReviewMode || engine.isGameOver) return;

        boolean success = engine.makeMove(from, to);
        if (!success) return;

        boardView.setLastMove(from, to);
        boardView.clearHighlights();

        // Check if capture or castling for haptics
        if (engine.moveHistory.size() > 0) {
            ChessEngine.MoveRecord last = engine.moveHistory.get(engine.moveHistory.size() - 1);
            if (last.captured != ChessEngine.EMPTY) {
                boardView.vibrate(40);
            } else if (last.isCastling) {
                boardView.vibrate(30);
            } else {
                boardView.vibrate(20);
            }
        }

        updateStatus();

        if (engine.isGameOver) {
            onGameOver();
            return;
        }

        // Bot turn
        if (!engine.whiteToMove) {
            txtStatusBar.setText("Bot thinking...");
            timerHandler.postDelayed(new Runnable() {
                @Override
                public void run() {
                    makeBotMove();
                }
            }, 350);
        }
    }

    private void makeBotMove() {
        if (engine.isGameOver || engine.whiteToMove) return;

        int[] botMove = engine.getBotMove(botLevel);
        if (botMove != null) {
            engine.makeMove(botMove[0], botMove[1]);
            boardView.setLastMove(botMove[0], botMove[1]);
            boardView.clearHighlights();

            if (engine.moveHistory.size() > 0) {
                ChessEngine.MoveRecord last = engine.moveHistory.get(engine.moveHistory.size() - 1);
                if (last.captured != ChessEngine.EMPTY) {
                    boardView.vibrate(35);
                } else {
                    boardView.vibrate(15);
                }
            }
            updateStatus();

            if (engine.isGameOver) {
                onGameOver();
            }
        }
    }

    private void updateStatus() {
        if (engine.isGameOver) return;

        String turnText = engine.whiteToMove ? "White to move" : "Black to move";
        int checkColor = engine.whiteToMove ? ChessEngine.WHITE : ChessEngine.BLACK;
        if (engine.isKingInCheck(checkColor)) {
            turnText += " (Check!)";
            txtStatusBar.setTextColor(0xFFEF4444);
        } else {
            txtStatusBar.setTextColor(0xFF38BDF8);
        }

        if (engine.moveHistory.size() > 0) {
            ChessEngine.MoveRecord last = engine.moveHistory.get(engine.moveHistory.size() - 1);
            txtStatusBar.setText(turnText + " | " + last.san);
        } else {
            txtStatusBar.setText(turnText);
        }
    }

    private void updateClockDisplays() {
        int wMin = whiteTimeSeconds / 60;
        int wSec = whiteTimeSeconds % 60;
        int bMin = blackTimeSeconds / 60;
        int bSec = blackTimeSeconds % 60;

        txtPlayerClock.setText(String.format(Locale.US, "%02d:%02d", wMin, wSec));
        txtOpponentClock.setText(String.format(Locale.US, "%02d:%02d", bMin, bSec));
    }

    private AlertDialog.Builder createDialogBuilder() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.HONEYCOMB) {
            boolean isDark = (themeMode != THEME_MODE_LIGHT);
            return new AlertDialog.Builder(this, isDark ? AlertDialog.THEME_DEVICE_DEFAULT_DARK : AlertDialog.THEME_DEVICE_DEFAULT_LIGHT);
        }
        return new AlertDialog.Builder(this);
    }

    private void onGameOver() {
        clockRunning = false;
        timerHandler.removeCallbacks(clockRunnable);

        boolean won = "1-0".equals(engine.gameResult);
        boolean draw = "1/2-1/2".equals(engine.gameResult);

        if (won) {
            playerElo += 16;
            txtStatusBar.setText("Victory: " + engine.gameOverReason + " (+16 Elo)");
            txtStatusBar.setTextColor(0xFF10B981);
            boardView.vibrate(100);
        } else if (draw) {
            txtStatusBar.setText("Draw: " + engine.gameOverReason);
            txtStatusBar.setTextColor(0xFFF59E0B);
            boardView.vibrate(40);
        } else {
            if (playerElo > 100) playerElo = Math.max(100, playerElo - 14);
            txtStatusBar.setText("Defeat: " + engine.gameOverReason + " (-14 Elo)");
            txtStatusBar.setTextColor(0xFFEF4444);
            boardView.vibrate(80);
        }

        savePreferences();
        applyTheme();

        btnReview.setVisibility(View.VISIBLE);

        // Build review states cache
        buildReviewStates();

        // Game over dialog
        createDialogBuilder()
            .setTitle("Game Finished")
            .setMessage("Result: " + engine.gameResult + " (" + engine.gameOverReason + ")\n"
                      + "New Rating: " + playerElo + " Elo\n\n"
                      + "Tap Review to inspect accuracies and move evaluations.")
            .setPositiveButton("Review Game", new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialog, int which) {
                    enterReviewMode();
                }
            })
            .setNeutralButton("Copy PGN", new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialog, int which) {
                    copyToClipboard("LibreChess PGN", engine.toPGN(playerName, "Bot " + botLevel));
                    Toast.makeText(MainActivity.this, "PGN copied to clipboard!", Toast.LENGTH_SHORT).show();
                }
            })
            .setNegativeButton("New Game", new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialog, int which) {
                    startNewGame();
                }
            })
            .setCancelable(true)
            .show();
    }

    private void buildReviewStates() {
        reviewBoardStates.clear();
        ChessEngine sim = new ChessEngine();
        reviewBoardStates.add(sim.cloneEngine());

        for (ChessEngine.MoveRecord m : engine.moveHistory) {
            sim.makeMove(m.from, m.to);
            reviewBoardStates.add(sim.cloneEngine());
        }
    }

    private void enterReviewMode() {
        if (reviewBoardStates.isEmpty()) buildReviewStates();
        inReviewMode = true;

        layoutGameControls.setVisibility(View.GONE);
        layoutReviewControls.setVisibility(View.VISIBLE);
        txtReviewInfo.setVisibility(View.VISIBLE);

        float[] acc = engine.getAccuracy();
        txtStatusBar.setText("Accuracy: White " + acc[0] + "% | Black " + acc[1] + "%");

        reviewMoveIndex = reviewBoardStates.size() - 1;
        applyReviewIndex();
    }

    private void applyReviewIndex() {
        if (reviewBoardStates.isEmpty()) return;
        ChessEngine st = reviewBoardStates.get(reviewMoveIndex);
        boardView.setEngine(st);

        if (reviewMoveIndex > 0 && reviewMoveIndex <= engine.moveHistory.size()) {
            ChessEngine.MoveRecord rec = engine.moveHistory.get(reviewMoveIndex - 1);
            boardView.setLastMove(rec.from, rec.to);

            String tag = "[" + rec.classification + "]";
            int tagColor = 0xFF38BDF8;
            if ("Brilliant".equals(rec.classification)) {
                tag = "[!! Brilliant !!]";
                tagColor = 0xFF06B6D4;
            } else if ("Best".equals(rec.classification)) {
                tagColor = 0xFF10B981;
            } else if ("Inaccuracy".equals(rec.classification)) {
                tagColor = 0xFFF59E0B;
            } else if ("Mistake".equals(rec.classification)) {
                tagColor = 0xFFFB923C;
            } else if ("Blunder".equals(rec.classification)) {
                tagColor = 0xFFEF4444;
            }

            txtReviewInfo.setTextColor(tagColor);
            txtReviewInfo.setText("Move " + reviewMoveIndex + ": " + rec.san + " " + tag + " | Eval: " + String.format(Locale.US, "%.1f", rec.evalAfter));
        } else {
            boardView.setLastMove(-1, -1);
            txtReviewInfo.setText("Starting Position");
            txtReviewInfo.setTextColor(0xFF94A3B8);
        }
        boardView.clearHighlights();
    }

    private void exitReviewMode() {
        inReviewMode = false;
        layoutReviewControls.setVisibility(View.GONE);
        layoutGameControls.setVisibility(View.VISIBLE);
        txtReviewInfo.setVisibility(View.GONE);
        boardView.setEngine(engine);
        if (engine.moveHistory.size() > 0) {
            ChessEngine.MoveRecord last = engine.moveHistory.get(engine.moveHistory.size() - 1);
            boardView.setLastMove(last.from, last.to);
        }
        boardView.clearHighlights();
    }

    private void showNameDialog() {
        final EditText input = new EditText(this);
        input.setText(playerName);
        input.setSingleLine(true);
        input.setPadding(30, 20, 30, 20);
        if (themeMode != THEME_MODE_LIGHT) {
            input.setTextColor(0xFFF8FAFC);
            input.setHintTextColor(0xFF64748B);
        }

        createDialogBuilder()
            .setTitle("Player Profile")
            .setMessage("Enter your display name (Rating starts at 100 Elo):")
            .setView(input)
            .setPositiveButton("Save", new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialog, int which) {
                    String name = input.getText().toString().trim();
                    if (!name.isEmpty()) {
                        playerName = name;
                        savePreferences();
                        applyTheme();
                    }
                }
            })
            .setNegativeButton("Cancel", null)
            .show();
    }

    private void showBotPickerDialog() {
        final String[] botOptions = {
            "Bot 100 (Beginner - Toby)",
            "Bot 300 (Casual - Martin)",
            "Bot 600 (Club - Elena)",
            "Bot 1000 (Intermediate - Tactics)",
            "Bot 1500 (Master - Alpha-Beta & Scholar Defense)"
        };
        final int[] botRatings = {100, 300, 600, 1000, 1500};

        int currentIdx = 0;
        for (int i = 0; i < botRatings.length; i++) {
            if (botRatings[i] == botLevel) { currentIdx = i; break; }
        }

        createDialogBuilder()
            .setTitle("Select Opponent")
            .setSingleChoiceItems(botOptions, currentIdx, new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialog, int which) {
                    botLevel = botRatings[which];
                    savePreferences();
                    txtOpponentName.setText("Bot " + botLevel);
                    dialog.dismiss();
                    startNewGame();
                }
            })
            .setNegativeButton("Cancel", null)
            .show();
    }

    private void showResignDialog() {
        if (engine.isGameOver) return;
        createDialogBuilder()
            .setTitle("Resign Game")
            .setMessage("Are you sure you want to resign this match?")
            .setPositiveButton("Resign", new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialog, int which) {
                    engine.resign(true);
                    onGameOver();
                }
            })
            .setNegativeButton("Keep Playing", null)
            .show();
    }

    private void copyToClipboard(String label, String text) {
        try {
            ClipboardManager clipboard = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
            ClipData clip = ClipData.newPlainText(label, text);
            if (clipboard != null) clipboard.setPrimaryClip(clip);
        } catch (Exception ignored) {}
    }

    private void showSettingsDialog() {
        ScrollView scrollView = new ScrollView(this);
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(36, 20, 36, 20);
        scrollView.addView(layout);

        boolean isDark = (themeMode != THEME_MODE_LIGHT);
        int textPrimary = isDark ? 0xFFF8FAFC : 0xFF0F172A;
        int textSecondary = isDark ? 0xFF94A3B8 : 0xFF475569;

        // Display Name input
        TextView lblName = new TextView(this);
        lblName.setText("Display Name");
        lblName.setTextSize(14f);
        lblName.setTextColor(textSecondary);
        lblName.setPadding(0, 8, 0, 4);
        layout.addView(lblName);

        final EditText edtName = new EditText(this);
        edtName.setText(playerName);
        edtName.setTextColor(textPrimary);
        edtName.setSingleLine(true);
        layout.addView(edtName);

        // App Theme Mode (Slate Dark, Midnight OLED Pure Black, Light)
        TextView lblThemeMode = new TextView(this);
        lblThemeMode.setText("App Theme Mode");
        lblThemeMode.setTextSize(14f);
        lblThemeMode.setTextColor(textSecondary);
        lblThemeMode.setPadding(0, 10, 0, 4);
        layout.addView(lblThemeMode);

        final RadioGroup rgThemeMode = new RadioGroup(this);
        for (int i = 0; i < THEME_MODE_NAMES.length; i++) {
            RadioButton rb = new RadioButton(this);
            rb.setId(300 + i);
            rb.setText(THEME_MODE_NAMES[i]);
            rb.setTextColor(textPrimary);
            if (i == themeMode) rb.setChecked(true);
            rgThemeMode.addView(rb);
        }
        layout.addView(rgThemeMode);

        // Haptic feedback toggle
        final CheckBox chkHaptics = new CheckBox(this);
        chkHaptics.setText("Haptic Vibration Feedback");
        chkHaptics.setChecked(hapticsEnabled);
        chkHaptics.setTextColor(textPrimary);
        chkHaptics.setPadding(0, 8, 0, 8);
        layout.addView(chkHaptics);

        // Tactical Threat Highlights toggle
        final CheckBox chkThreats = new CheckBox(this);
        chkThreats.setText("Highlight Tactical Threats & Hanging Pieces");
        chkThreats.setChecked(showThreats);
        chkThreats.setTextColor(textPrimary);
        chkThreats.setPadding(0, 4, 0, 10);
        layout.addView(chkThreats);

        // Piece Look selector (3 distinct custom piece sets)
        TextView lblPiece = new TextView(this);
        lblPiece.setText("Pieces Look");
        lblPiece.setTextSize(14f);
        lblPiece.setTextColor(textSecondary);
        lblPiece.setPadding(0, 10, 0, 4);
        layout.addView(lblPiece);

        final RadioGroup rgPiece = new RadioGroup(this);
        for (int i = 0; i < ChessBoardView.PIECE_STYLE_NAMES.length; i++) {
            RadioButton rb = new RadioButton(this);
            rb.setId(200 + i);
            rb.setText(ChessBoardView.PIECE_STYLE_NAMES[i]);
            rb.setTextColor(textPrimary);
            if (i == pieceStyle) rb.setChecked(true);
            rgPiece.addView(rb);
        }
        layout.addView(rgPiece);

        // Official Lichess Board Theme selector
        TextView lblTheme = new TextView(this);
        lblTheme.setText("Board Theme");
        lblTheme.setTextSize(14f);
        lblTheme.setTextColor(textSecondary);
        lblTheme.setPadding(0, 10, 0, 4);
        layout.addView(lblTheme);

        final RadioGroup rgTheme = new RadioGroup(this);
        for (int i = 0; i < ChessBoardView.THEME_NAMES.length; i++) {
            RadioButton rb = new RadioButton(this);
            rb.setId(100 + i);
            rb.setText(ChessBoardView.THEME_NAMES[i]);
            rb.setTextColor(textPrimary);
            if (i == boardTheme) rb.setChecked(true);
            rgTheme.addView(rb);
        }
        layout.addView(rgTheme);

        // Clipboard Export Tools
        TextView lblExport = new TextView(this);
        lblExport.setText("Export Game Notation");
        lblExport.setTextSize(14f);
        lblExport.setTextColor(textSecondary);
        lblExport.setPadding(0, 12, 0, 4);
        layout.addView(lblExport);

        LinearLayout rowButtons = new LinearLayout(this);
        rowButtons.setOrientation(LinearLayout.HORIZONTAL);

        Button btnCopyFen = new Button(this);
        btnCopyFen.setText("Copy FEN");
        btnCopyFen.setTextSize(12f);
        btnCopyFen.setTextColor(0xFF06B6D4);
        btnCopyFen.setBackgroundResource(R.drawable.btn_flat);
        LinearLayout.LayoutParams lpFen = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1.0f);
        lpFen.setMargins(0, 0, 8, 0);
        btnCopyFen.setLayoutParams(lpFen);
        btnCopyFen.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                copyToClipboard("LibreChess FEN", engine.toFEN());
                Toast.makeText(MainActivity.this, "Position FEN copied!", Toast.LENGTH_SHORT).show();
            }
        });
        rowButtons.addView(btnCopyFen);

        Button btnCopyPgn = new Button(this);
        btnCopyPgn.setText("Copy PGN");
        btnCopyPgn.setTextSize(12f);
        btnCopyPgn.setTextColor(0xFF10B981);
        btnCopyPgn.setBackgroundResource(R.drawable.btn_flat);
        LinearLayout.LayoutParams lpPgn = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1.0f);
        lpPgn.setMargins(8, 0, 0, 0);
        btnCopyPgn.setLayoutParams(lpPgn);
        btnCopyPgn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                copyToClipboard("LibreChess PGN", engine.toPGN(playerName, "Bot " + botLevel));
                Toast.makeText(MainActivity.this, "Match PGN copied!", Toast.LENGTH_SHORT).show();
            }
        });
        rowButtons.addView(btnCopyPgn);
        layout.addView(rowButtons);

        // Server URL for Termux ./server
        TextView lblServer = new TextView(this);
        lblServer.setText("Matchmaking Server URL");
        lblServer.setTextSize(14f);
        lblServer.setTextColor(textSecondary);
        lblServer.setPadding(0, 14, 0, 4);
        layout.addView(lblServer);

        final EditText edtServer = new EditText(this);
        edtServer.setText(serverUrl);
        edtServer.setTextColor(textPrimary);
        edtServer.setSingleLine(true);
        layout.addView(edtServer);

        // Credits Box
        TextView txtCredits = new TextView(this);
        txtCredits.setText("\nLibreChess RS\nVersion 1.2.0 • Ultra-Lightweight (<1MB)\nLichess Pieces & Boards • Full Tactics Bot AI");
        txtCredits.setTextSize(12f);
        txtCredits.setTextColor(textSecondary);
        txtCredits.setPadding(0, 10, 0, 10);
        layout.addView(txtCredits);

        createDialogBuilder()
            .setTitle("Settings")
            .setView(scrollView)
            .setPositiveButton("Save", new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialog, int which) {
                    String newName = edtName.getText().toString().trim();
                    if (!newName.isEmpty()) playerName = newName;

                    int checkedThemeModeId = rgThemeMode.getCheckedRadioButtonId();
                    if (checkedThemeModeId >= 300) {
                        themeMode = checkedThemeModeId - 300;
                        isDarkMode = (themeMode != THEME_MODE_LIGHT);
                    }

                    hapticsEnabled = chkHaptics.isChecked();
                    showThreats = chkThreats.isChecked();
                    boardView.setHapticsEnabled(hapticsEnabled);
                    boardView.setShowThreats(showThreats);

                    int checkedPieceId = rgPiece.getCheckedRadioButtonId();
                    if (checkedPieceId >= 200) {
                        pieceStyle = checkedPieceId - 200;
                        boardView.setPieceStyle(pieceStyle);
                    }

                    int checkedThemeId = rgTheme.getCheckedRadioButtonId();
                    if (checkedThemeId >= 100) {
                        boardTheme = checkedThemeId - 100;
                        boardView.setBoardTheme(boardTheme);
                    }

                    String sUrl = edtServer.getText().toString().trim();
                    if (!sUrl.isEmpty()) serverUrl = sUrl;

                    savePreferences();
                    setupEdgeToEdge();
                    applyTheme();
                }
            })
            .setNegativeButton("Cancel", null)
            .show();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        timerHandler.removeCallbacks(clockRunnable);
    }
}
