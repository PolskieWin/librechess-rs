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
import java.net.HttpURLConnection;
import java.net.URL;

public class MainActivity extends Activity implements ChessBoardView.OnMoveListener {
    private static final String PREFS_NAME = "LibreChessPrefs";

    private ChessEngine engine;
    private ChessBoardView boardView;

    // UI Widgets
    private LinearLayout rootLayout;
    private TextView txtAppTitle;
    private TextView btnUserBadge;
    private Button btnSettings;
    private Button btnStyle;
    private TextView btnColorWhite;
    private TextView btnColorRandom;
    private TextView btnColorBlack;
    private int playerColorPreference = 0; // 0: White, 1: Random, 2: Black
    private boolean isPlayerWhite = true;
    private TextView txtOpponentName;
    private TextView txtOpponentClock;
    private TextView txtOpponentCaptured;
    private TextView txtPlayerName;
    private TextView txtPlayerClock;
    private TextView txtPlayerCaptured;
    private TextView txtStatusBar;
    private TextView txtOpeningName;
    private TextView txtReviewInfo;

    // Navigation Tabs & Views
    private TextView tabBoard;
    private TextView tabPlay;
    private TextView tabBots;
    private TextView tabAnalyzer;

    private View viewBoard;
    private View viewPlay;
    private View viewBots;
    private View viewAnalyzer;
    private int currentTab = 0; // 0: Board, 1: Play, 2: Bots, 3: Analyzer

    // Play & Matchmaking Time Control Cards
    private View cardTcBullet1;
    private View cardTcBullet2;
    private View cardTcBlitz3;
    private View cardTcBlitz5;
    private View cardTcRapid10;
    private View cardTcRapid15;
    private EditText inputCustomIp;
    private Button btnSaveIp;
    private Button btnTestIp;

    // Bot Cards
    private View cardBot100;
    private View cardBot300;
    private View cardBot600;
    private View cardBot1000;
    private View cardBot1500;
    private View cardBot2500;

    // Analyzer UI
    private TextView txtAnWhiteAcc;
    private TextView txtAnBlackAcc;
    private TextView txtAnSummary;
    private Button btnAnReviewCurrent;
    private Button btnAnImportPgn;
    private EditText inputAnNotation;
    private Button btnAnExecute;

    private int selectedTimeSeconds = 600;

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
    public static final int THEME_MODE_MATERIAL_ORANGE = 0;
    public static final int THEME_MODE_OLED = 1;
    public static final int THEME_MODE_LIGHT = 2;
    public static final String[] THEME_MODE_NAMES = {
        "Material You Orange (#18110B)",
        "Midnight OLED Pure Black (#000000)",
        "Material You Warm Light"
    };

    private SharedPreferences prefs;
    private String playerName = "Polskie";
    private int playerElo = 100;
    private int themeMode = THEME_MODE_MATERIAL_ORANGE;
    private boolean isDarkMode = true;
    private int boardTheme = 0;
    private int pieceStyle = 0; // 0: Lichess, 1: Modern, 2: Tournament
    private boolean showThreats = true;
    private boolean showEvalBar = true;
    private boolean soundEnabled = true;
    private boolean hapticsEnabled = true;
    private int botLevel = 100;
    private String serverUrl = "http://127.0.0.1:8080";

    // Native Audio Engine
    private android.media.SoundPool soundPool;
    private int soundMoveId;
    private int soundCaptureId;
    private int soundCheckId;
    private int soundCastleId;
    private int soundPromoteId;
    private int soundGameStartId;
    private int soundGameEndId;
    private int soundBlunderId;

    // Master Openings Repertoire Database
    private java.util.Map<String, String> openingsMap = new java.util.HashMap<>();

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
        boardView.setShowEvalBar(showEvalBar);
        boardView.setHapticsEnabled(hapticsEnabled);

        initSoundPool();
        initOpenings();

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
        themeMode = prefs.getInt("theme_mode", prefs.getBoolean("is_dark_mode", true) ? THEME_MODE_MATERIAL_ORANGE : THEME_MODE_LIGHT);
        isDarkMode = (themeMode != THEME_MODE_LIGHT);
        boardTheme = prefs.getInt("board_theme", 0);
        pieceStyle = prefs.getInt("piece_style", 0);
        showThreats = prefs.getBoolean("show_threats", true);
        showEvalBar = prefs.getBoolean("show_eval_bar", true);
        soundEnabled = prefs.getBoolean("sound_enabled", true);
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
        editor.putBoolean("show_eval_bar", showEvalBar);
        editor.putBoolean("sound_enabled", soundEnabled);
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
        btnStyle = (Button) findViewById(R.id.btn_style);
        txtOpponentName = (TextView) findViewById(R.id.txt_opponent_name);
        txtOpponentClock = (TextView) findViewById(R.id.txt_opponent_clock);
        txtOpponentCaptured = (TextView) findViewById(R.id.txt_opponent_captured);
        txtPlayerName = (TextView) findViewById(R.id.txt_player_name);
        txtPlayerClock = (TextView) findViewById(R.id.txt_player_clock);
        txtPlayerCaptured = (TextView) findViewById(R.id.txt_player_captured);
        txtStatusBar = (TextView) findViewById(R.id.txt_status_bar);
        txtOpeningName = (TextView) findViewById(R.id.txt_opening_name);
        txtReviewInfo = (TextView) findViewById(R.id.txt_review_info);

        // Navigation Tabs & Main Views
        tabBoard = (TextView) findViewById(R.id.tab_board);
        tabPlay = (TextView) findViewById(R.id.tab_play);
        tabBots = (TextView) findViewById(R.id.tab_bots);
        tabAnalyzer = (TextView) findViewById(R.id.tab_analyzer);

        viewBoard = findViewById(R.id.view_board);
        viewPlay = findViewById(R.id.view_play);
        viewBots = findViewById(R.id.view_bots);
        viewAnalyzer = findViewById(R.id.view_analyzer);

        // Time Control Cards
        cardTcBullet1 = findViewById(R.id.card_tc_bullet_1);
        cardTcBullet2 = findViewById(R.id.card_tc_bullet_2);
        cardTcBlitz3 = findViewById(R.id.card_tc_blitz_3);
        cardTcBlitz5 = findViewById(R.id.card_tc_blitz_5);
        cardTcRapid10 = findViewById(R.id.card_tc_rapid_10);
        cardTcRapid15 = findViewById(R.id.card_tc_rapid_15);

        // Custom IP widgets
        inputCustomIp = (EditText) findViewById(R.id.input_custom_ip);
        btnSaveIp = (Button) findViewById(R.id.btn_save_ip);
        btnTestIp = (Button) findViewById(R.id.btn_test_ip);
        if (inputCustomIp != null) inputCustomIp.setText(serverUrl);

        // Bot Cards
        cardBot100 = findViewById(R.id.card_bot_100);
        cardBot300 = findViewById(R.id.card_bot_300);
        cardBot600 = findViewById(R.id.card_bot_600);
        cardBot1000 = findViewById(R.id.card_bot_1000);
        cardBot1500 = findViewById(R.id.card_bot_1500);
        cardBot2500 = findViewById(R.id.card_bot_2500);

        btnColorWhite = (TextView) findViewById(R.id.btn_color_white);
        btnColorRandom = (TextView) findViewById(R.id.btn_color_random);
        btnColorBlack = (TextView) findViewById(R.id.btn_color_black);

        // Analyzer UI
        txtAnWhiteAcc = (TextView) findViewById(R.id.txt_an_white_acc);
        txtAnBlackAcc = (TextView) findViewById(R.id.txt_an_black_acc);
        txtAnSummary = (TextView) findViewById(R.id.txt_an_summary);
        btnAnReviewCurrent = (Button) findViewById(R.id.btn_an_review_current);
        btnAnImportPgn = (Button) findViewById(R.id.btn_an_import_pgn);
        inputAnNotation = (EditText) findViewById(R.id.input_an_notation);
        btnAnExecute = (Button) findViewById(R.id.btn_an_execute);

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
            bgColor = 0xFFFFF8F3;
            textTitleColor = 0xFF2D1600;
            textSecondaryColor = 0xFF7D5230;
            clockColor = 0xFFD94800;
        } else { // THEME_MODE_MATERIAL_ORANGE
            bgColor = 0xFF18110B;
            textTitleColor = 0xFFF8FAFC;
            textSecondaryColor = 0xFFD8B498;
            clockColor = 0xFFFF7A00;
        }

        rootLayout.setBackgroundColor(bgColor);
        txtAppTitle.setTextColor(textTitleColor);
        txtOpponentClock.setTextColor(clockColor);
        txtPlayerClock.setTextColor(clockColor);
        txtOpponentName.setTextColor(textSecondaryColor);

        if (txtOpeningName != null) {
            txtOpeningName.setTextColor((themeMode == THEME_MODE_LIGHT) ? 0xFF8A4600 : 0xFFD8B498);
        }
        if (txtPlayerCaptured != null) {
            txtPlayerCaptured.setTextColor(clockColor);
        }
        if (txtOpponentCaptured != null) {
            txtOpponentCaptured.setTextColor(clockColor);
        }

        btnUserBadge.setText(playerName + " (" + playerElo + ")");
        txtPlayerName.setText(playerName + " (" + playerElo + ")");
        txtOpponentName.setText("Bot " + botLevel);

        if (tabBoard != null && tabPlay != null && tabBots != null && tabAnalyzer != null) {
            int activeText = 0xFFFFFFFF;
            int inactiveText = (themeMode == THEME_MODE_LIGHT) ? 0xFF7D5230 : 0xFFD8B498;
            tabBoard.setTextColor(currentTab == 0 ? activeText : inactiveText);
            tabPlay.setTextColor(currentTab == 1 ? activeText : inactiveText);
            tabBots.setTextColor(currentTab == 2 ? activeText : inactiveText);
            tabAnalyzer.setTextColor(currentTab == 3 ? activeText : inactiveText);
        }
    }

    private void initSoundPool() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            soundPool = new android.media.SoundPool.Builder()
                .setMaxStreams(4)
                .setAudioAttributes(new android.media.AudioAttributes.Builder()
                    .setUsage(android.media.AudioAttributes.USAGE_GAME)
                    .setContentType(android.media.AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build())
                .build();
        } else {
            soundPool = new android.media.SoundPool(4, android.media.AudioManager.STREAM_MUSIC, 0);
        }
        soundMoveId = soundPool.load(this, R.raw.sound_move, 1);
        soundCaptureId = soundPool.load(this, R.raw.sound_capture, 1);
        soundCheckId = soundPool.load(this, R.raw.sound_check, 1);
        soundCastleId = soundPool.load(this, R.raw.sound_castle, 1);
        soundPromoteId = soundPool.load(this, R.raw.sound_promote, 1);
        soundGameStartId = soundPool.load(this, R.raw.sound_game_start, 1);
        soundGameEndId = soundPool.load(this, R.raw.sound_game_end, 1);
        soundBlunderId = soundPool.load(this, R.raw.sound_blunder, 1);
    }

    private void playSound(int soundId) {
        if (!soundEnabled || soundPool == null || soundId == 0) return;
        soundPool.play(soundId, 1.0f, 1.0f, 1, 0, 1.0f);
    }

    private void initOpenings() {
        try {
            java.io.InputStream is = getAssets().open("openings.json");
            int size = is.available();
            byte[] buffer = new byte[size];
            is.read(buffer);
            is.close();
            String json = new String(buffer, "UTF-8");
            org.json.JSONArray array = new org.json.JSONArray(json);
            for (int i = 0; i < array.length(); i++) {
                org.json.JSONObject obj = array.getJSONObject(i);
                String moves = obj.getString("moves").trim();
                String eco = obj.getString("eco");
                String name = obj.getString("name");
                openingsMap.put(moves, eco + " " + name);
            }
        } catch (Exception ignored) {}
    }

    private void updateOpeningDisplay() {
        if (txtOpeningName == null) return;
        String currentMoves = engine.getMovesSANString();
        String match = null;
        if (!currentMoves.isEmpty()) {
            String[] parts = currentMoves.split(" ");
            StringBuilder checkSeq = new StringBuilder();
            for (int i = 0; i < parts.length; i++) {
                if (i > 0) checkSeq.append(" ");
                checkSeq.append(parts[i]);
                String lookup = openingsMap.get(checkSeq.toString());
                if (lookup != null) {
                    match = lookup;
                }
            }
        }
        if (match != null) {
            txtOpeningName.setText(match);
            txtOpeningName.setVisibility(View.VISIBLE);
        } else if (currentMoves.isEmpty()) {
            txtOpeningName.setText("Standard Starting Position");
            txtOpeningName.setVisibility(View.VISIBLE);
        } else {
            txtOpeningName.setVisibility(View.GONE);
        }
    }

    private void updateCapturedPiecesDisplay() {
        if (txtPlayerCaptured == null || txtOpponentCaptured == null) return;
        int playerSide = isPlayerWhite ? ChessEngine.WHITE : ChessEngine.BLACK;
        int opponentSide = isPlayerWhite ? ChessEngine.BLACK : ChessEngine.WHITE;

        List<Integer> playerCaptures = engine.getCapturedPieces(playerSide);
        List<Integer> opponentCaptures = engine.getCapturedPieces(opponentSide);

        int playerDiff = engine.getMaterialDifferential(playerSide);
        int opponentDiff = engine.getMaterialDifferential(opponentSide);

        txtPlayerCaptured.setText(formatCaptured(playerCaptures, playerDiff));
        txtOpponentCaptured.setText(formatCaptured(opponentCaptures, opponentDiff));
    }

    private String formatCaptured(List<Integer> pieces, int diff) {
        StringBuilder sb = new StringBuilder();
        for (int p : pieces) {
            int type = ChessEngine.typeOf(p);
            switch (type) {
                case ChessEngine.QUEEN: sb.append("♛"); break;
                case ChessEngine.ROOK: sb.append("♜"); break;
                case ChessEngine.BISHOP: sb.append("♝"); break;
                case ChessEngine.KNIGHT: sb.append("♞"); break;
                case ChessEngine.PAWN: sb.append("♟"); break;
            }
        }
        if (diff > 0) {
            sb.append(" +").append(diff);
        }
        return sb.toString();
    }

    private void setupClickListeners() {
        // Material You Navigation Tabs
        if (tabBoard != null) {
            tabBoard.setOnClickListener(new View.OnClickListener() {
                @Override public void onClick(View v) { switchTab(0); }
            });
        }
        if (tabPlay != null) {
            tabPlay.setOnClickListener(new View.OnClickListener() {
                @Override public void onClick(View v) { switchTab(1); }
            });
        }
        if (tabBots != null) {
            tabBots.setOnClickListener(new View.OnClickListener() {
                @Override public void onClick(View v) { switchTab(2); }
            });
        }
        if (tabAnalyzer != null) {
            tabAnalyzer.setOnClickListener(new View.OnClickListener() {
                @Override public void onClick(View v) { switchTab(3); }
            });
        }

        // Time Control Selection (Bullet, Blitz, Rapid)
        if (cardTcBullet1 != null) {
            cardTcBullet1.setOnClickListener(new View.OnClickListener() {
                @Override public void onClick(View v) {
                    selectedTimeSeconds = 60;
                    startNewGame();
                    switchTab(0);
                    Toast.makeText(MainActivity.this, "Bullet 1 | 0 started", Toast.LENGTH_SHORT).show();
                }
            });
        }
        if (cardTcBullet2 != null) {
            cardTcBullet2.setOnClickListener(new View.OnClickListener() {
                @Override public void onClick(View v) {
                    selectedTimeSeconds = 120;
                    startNewGame();
                    switchTab(0);
                    Toast.makeText(MainActivity.this, "Bullet 2 | 1 started", Toast.LENGTH_SHORT).show();
                }
            });
        }
        if (cardTcBlitz3 != null) {
            cardTcBlitz3.setOnClickListener(new View.OnClickListener() {
                @Override public void onClick(View v) {
                    selectedTimeSeconds = 180;
                    startNewGame();
                    switchTab(0);
                    Toast.makeText(MainActivity.this, "Blitz 3 | 0 started", Toast.LENGTH_SHORT).show();
                }
            });
        }
        if (cardTcBlitz5 != null) {
            cardTcBlitz5.setOnClickListener(new View.OnClickListener() {
                @Override public void onClick(View v) {
                    selectedTimeSeconds = 300;
                    startNewGame();
                    switchTab(0);
                    Toast.makeText(MainActivity.this, "Blitz 5 | 3 started", Toast.LENGTH_SHORT).show();
                }
            });
        }
        if (cardTcRapid10 != null) {
            cardTcRapid10.setOnClickListener(new View.OnClickListener() {
                @Override public void onClick(View v) {
                    selectedTimeSeconds = 600;
                    startNewGame();
                    switchTab(0);
                    Toast.makeText(MainActivity.this, "Rapid 10 | 0 started", Toast.LENGTH_SHORT).show();
                }
            });
        }
        if (cardTcRapid15 != null) {
            cardTcRapid15.setOnClickListener(new View.OnClickListener() {
                @Override public void onClick(View v) {
                    selectedTimeSeconds = 900;
                    startNewGame();
                    switchTab(0);
                    Toast.makeText(MainActivity.this, "Rapid 15 | 10 started", Toast.LENGTH_SHORT).show();
                }
            });
        }

        // Custom IP Matchmaking controls
        if (btnSaveIp != null) {
            btnSaveIp.setOnClickListener(new View.OnClickListener() {
                @Override public void onClick(View v) {
                    String sUrl = inputCustomIp.getText().toString().trim();
                    if (!sUrl.isEmpty()) {
                        serverUrl = sUrl;
                        savePreferences();
                        Toast.makeText(MainActivity.this, "Server IP saved: " + serverUrl, Toast.LENGTH_SHORT).show();
                    }
                }
            });
        }
        if (btnTestIp != null) {
            btnTestIp.setOnClickListener(new View.OnClickListener() {
                @Override public void onClick(View v) {
                    testServerConnection();
                }
            });
        }

        // Bot selection cards
        setupBotCard(cardBot100, 100);
        setupBotCard(cardBot300, 300);
        setupBotCard(cardBot600, 600);
        setupBotCard(cardBot1000, 1000);
        setupBotCard(cardBot1500, 1500);
        setupBotCard(cardBot2500, 2500);

        // Analyzer controls
        if (btnAnReviewCurrent != null) {
            btnAnReviewCurrent.setOnClickListener(new View.OnClickListener() {
                @Override public void onClick(View v) {
                    enterReviewMode();
                    switchTab(0);
                }
            });
        }
        if (btnAnImportPgn != null) {
            btnAnImportPgn.setOnClickListener(new View.OnClickListener() {
                @Override public void onClick(View v) {
                    if (inputAnNotation != null) {
                        inputAnNotation.requestFocus();
                    }
                    Toast.makeText(MainActivity.this, "Paste PGN moves in the box below and tap Analyze", Toast.LENGTH_SHORT).show();
                }
            });
        }
        if (btnAnExecute != null) {
            btnAnExecute.setOnClickListener(new View.OnClickListener() {
                @Override public void onClick(View v) {
                    analyzePastedNotation();
                }
            });
        }

        btnUserBadge.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                showNameDialog();
            }
        });

        if (btnStyle != null) {
            btnStyle.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    showBoardStyleDialog();
                }
            });
        }

        if (btnColorWhite != null) {
            btnColorWhite.setOnClickListener(new View.OnClickListener() {
                @Override public void onClick(View v) { updateColorPreference(0); }
            });
        }
        if (btnColorRandom != null) {
            btnColorRandom.setOnClickListener(new View.OnClickListener() {
                @Override public void onClick(View v) { updateColorPreference(1); }
            });
        }
        if (btnColorBlack != null) {
            btnColorBlack.setOnClickListener(new View.OnClickListener() {
                @Override public void onClick(View v) { updateColorPreference(2); }
            });
        }

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
                    txtStatusBar.setText("[HINT] " + hint.san + " • " + hint.explanation);
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

    private void setupBotCard(View card, final int rating) {
        if (card != null) {
            card.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    botLevel = rating;
                    savePreferences();
                    if (playerColorPreference == 0) {
                        isPlayerWhite = true;
                    } else if (playerColorPreference == 2) {
                        isPlayerWhite = false;
                    } else {
                        isPlayerWhite = (Math.random() < 0.5);
                    }
                    startNewGame();
                    switchTab(0);
                    String sideName = isPlayerWhite ? "White" : "Black";
                    Toast.makeText(MainActivity.this, "Challenged Bot " + rating + " as " + sideName, Toast.LENGTH_SHORT).show();
                }
            });
        }
    }

    private void switchTab(int tab) {
        currentTab = tab;
        if (viewBoard != null) viewBoard.setVisibility(tab == 0 ? View.VISIBLE : View.GONE);
        if (viewPlay != null) viewPlay.setVisibility(tab == 1 ? View.VISIBLE : View.GONE);
        if (viewBots != null) viewBots.setVisibility(tab == 2 ? View.VISIBLE : View.GONE);
        if (viewAnalyzer != null) viewAnalyzer.setVisibility(tab == 3 ? View.VISIBLE : View.GONE);

        if (tabBoard != null && tabPlay != null && tabBots != null && tabAnalyzer != null) {
            tabBoard.setBackgroundResource(tab == 0 ? R.drawable.nav_tab_active : R.drawable.nav_tab_inactive);
            tabPlay.setBackgroundResource(tab == 1 ? R.drawable.nav_tab_active : R.drawable.nav_tab_inactive);
            tabBots.setBackgroundResource(tab == 2 ? R.drawable.nav_tab_active : R.drawable.nav_tab_inactive);
            tabAnalyzer.setBackgroundResource(tab == 3 ? R.drawable.nav_tab_active : R.drawable.nav_tab_inactive);

            int activeText = 0xFFFFFFFF;
            int inactiveText = (themeMode == THEME_MODE_LIGHT) ? 0xFF64748B : 0xFF94A3B8;
            tabBoard.setTextColor(tab == 0 ? activeText : inactiveText);
            tabPlay.setTextColor(tab == 1 ? activeText : inactiveText);
            tabBots.setTextColor(tab == 2 ? activeText : inactiveText);
            tabAnalyzer.setTextColor(tab == 3 ? activeText : inactiveText);
        }

        if (tab == 3) {
            updateAnalyzerView();
        }
    }

    private void updateAnalyzerView() {
        if (engine == null || txtAnWhiteAcc == null) return;
        float[] acc = engine.getAccuracy();
        txtAnWhiteAcc.setText(String.format(Locale.US, "%.0f%%", acc[0]));
        txtAnBlackAcc.setText(String.format(Locale.US, "%.0f%%", acc[1]));

        int count = engine.moveHistory.size();
        if (count == 0) {
            txtAnSummary.setText("No moves played yet. Play a game or paste PGN notation below to analyze.");
        } else {
            int brilliants = 0;
            int blunders = 0;
            for (ChessEngine.MoveRecord m : engine.moveHistory) {
                if ("Brilliant".equals(m.classification)) brilliants++;
                if ("Blunder".equals(m.classification)) blunders++;
            }
            txtAnSummary.setText(String.format(Locale.US, "Game analysis completed (%d plies). Brilliants: %d !!, Blunders: %d. Tap Review Game to inspect positions.", count, brilliants, blunders));
        }
    }

    private void analyzePastedNotation() {
        if (inputAnNotation == null) return;
        String input = inputAnNotation.getText().toString().trim();
        if (input.isEmpty()) {
            Toast.makeText(this, "Please enter FEN or PGN moves to analyze", Toast.LENGTH_SHORT).show();
            return;
        }

        if (input.contains("/") && input.split(" ").length >= 2) {
            engine.reset();
            engine.loadFEN(input);
            boardView.setEngine(engine);
            boardView.clearHighlights();
            switchTab(0);
            updateStatus();
            Toast.makeText(this, "FEN position loaded into engine", Toast.LENGTH_SHORT).show();
        } else {
            engine.reset();
            String[] tokens = input.replaceAll("\\d+\\.", " ").replaceAll("\\s+", " ").trim().split(" ");
            int loaded = 0;
            for (String tok : tokens) {
                if (tok.isEmpty() || tok.equals("*") || tok.equals("1-0") || tok.equals("0-1") || tok.equals("1/2-1/2")) continue;
                boolean matched = false;
                for (int from = 0; from < 64 && !matched; from++) {
                    int p = engine.board[from];
                    if (p == ChessEngine.EMPTY) continue;
                    if (ChessEngine.colorOf(p) != (engine.whiteToMove ? ChessEngine.WHITE : ChessEngine.BLACK)) continue;
                    List<Integer> legals = engine.getLegalMoves(from);
                    for (int to : legals) {
                        int[] promoChoices = {ChessEngine.QUEEN, ChessEngine.KNIGHT, ChessEngine.ROOK, ChessEngine.BISHOP};
                        for (int promo : promoChoices) {
                            ChessEngine sim = engine.cloneEngine();
                            if (sim.makeMove(from, to, promo)) {
                                String s = sim.moveHistory.get(sim.moveHistory.size() - 1).san;
                                if (s.equalsIgnoreCase(tok) || s.replace("+", "").replace("#", "").equalsIgnoreCase(tok.replace("+", "").replace("#", ""))) {
                                    engine.makeMove(from, to, promo);
                                    matched = true;
                                    loaded++;
                                    break;
                                }
                            }
                        }
                        if (matched) break;
                    }
                }
            }
            buildReviewStates();
            switchTab(0);
            enterReviewMode();
            Toast.makeText(this, "Imported " + loaded + " moves into Game Review", Toast.LENGTH_SHORT).show();
        }
    }

    private void testServerConnection() {
        if (inputCustomIp == null) return;
        final String targetUrl = inputCustomIp.getText().toString().trim();
        if (targetUrl.isEmpty()) {
            Toast.makeText(this, "Enter a valid server URL", Toast.LENGTH_SHORT).show();
            return;
        }

        Toast.makeText(this, "Testing connection to " + targetUrl + "...", Toast.LENGTH_SHORT).show();
        new Thread(new Runnable() {
            @Override
            public void run() {
                boolean success = false;
                try {
                    URL url = new URL(targetUrl);
                    HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                    conn.setConnectTimeout(2500);
                    conn.setReadTimeout(2500);
                    conn.setRequestMethod("GET");
                    int code = conn.getResponseCode();
                    success = (code >= 200 && code < 400);
                    conn.disconnect();
                } catch (Exception ignored) {}

                final boolean ok = success;
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        if (ok) {
                            Toast.makeText(MainActivity.this, "Server online! Connection verified.", Toast.LENGTH_SHORT).show();
                        } else {
                            Toast.makeText(MainActivity.this, "Cannot connect to server. Check IP and port.", Toast.LENGTH_LONG).show();
                        }
                    }
                });
            }
        }).start();
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

        boardView.setFlipped(!isPlayerWhite);
        txtPlayerName.setText(playerName + " (" + playerElo + ")");
        txtOpponentName.setText("Bot " + botLevel);

        whiteTimeSeconds = selectedTimeSeconds;
        blackTimeSeconds = selectedTimeSeconds;
        updateClockDisplays();
        updateOpeningDisplay();
        updateCapturedPiecesDisplay();
        playSound(soundGameStartId);

        boolean botStarts = (!isPlayerWhite && engine.whiteToMove);
        if (botStarts) {
            txtStatusBar.setText("Bot thinking...");
            txtStatusBar.setTextColor(0xFFFF7A00);
        } else {
            txtStatusBar.setText("White to move");
            txtStatusBar.setTextColor(0xFFFF7A00);
        }

        clockRunning = true;
        timerHandler.postDelayed(clockRunnable, 1000);

        if (botStarts) {
            timerHandler.postDelayed(new Runnable() {
                @Override
                public void run() {
                    makeBotMove();
                }
            }, 450);
        }
    }

    @Override
    public void onMove(int from, int to, int promoType) {
        if (inReviewMode || engine.isGameOver) return;
        boolean isBotTurn = (engine.whiteToMove == !isPlayerWhite);
        if (isBotTurn) return;

        boolean success = engine.makeMove(from, to, promoType);
        if (!success) return;

        boardView.setLastMove(from, to);
        boardView.clearHighlights();

        // Audio & Haptic triggers
        if (engine.moveHistory.size() > 0) {
            ChessEngine.MoveRecord last = engine.moveHistory.get(engine.moveHistory.size() - 1);
            if (engine.isGameOver) {
                playSound(soundGameEndId);
            } else if (engine.isKingInCheck(engine.whiteToMove ? ChessEngine.WHITE : ChessEngine.BLACK)) {
                playSound(soundCheckId);
            } else if (last.promotion != ChessEngine.EMPTY) {
                playSound(soundPromoteId);
            } else if (last.isCastling) {
                playSound(soundCastleId);
            } else if (last.captured != ChessEngine.EMPTY) {
                playSound(soundCaptureId);
            } else {
                playSound(soundMoveId);
            }

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
        if (engine.whiteToMove == !isPlayerWhite) {
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
        if (engine.isGameOver) return;
        boolean isBotTurn = (engine.whiteToMove == !isPlayerWhite);
        if (!isBotTurn) return;

        int[] botMove = engine.getBotMove(botLevel);
        if (botMove != null) {
            engine.makeMove(botMove[0], botMove[1]);
            boardView.setLastMove(botMove[0], botMove[1]);
            boardView.clearHighlights();

            if (engine.moveHistory.size() > 0) {
                ChessEngine.MoveRecord last = engine.moveHistory.get(engine.moveHistory.size() - 1);
                if (engine.isGameOver) {
                    playSound(soundGameEndId);
                } else if (engine.isKingInCheck(engine.whiteToMove ? ChessEngine.WHITE : ChessEngine.BLACK)) {
                    playSound(soundCheckId);
                } else if (last.promotion != ChessEngine.EMPTY) {
                    playSound(soundPromoteId);
                } else if (last.isCastling) {
                    playSound(soundCastleId);
                } else if (last.captured != ChessEngine.EMPTY) {
                    playSound(soundCaptureId);
                } else {
                    playSound(soundMoveId);
                }

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
            txtStatusBar.setTextColor(0xFFFF7A00);
        }

        if (engine.moveHistory.size() > 0) {
            ChessEngine.MoveRecord last = engine.moveHistory.get(engine.moveHistory.size() - 1);
            txtStatusBar.setText(turnText + " | " + last.san);
        } else {
            txtStatusBar.setText(turnText);
        }

        updateOpeningDisplay();
        updateCapturedPiecesDisplay();
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

        playSound(soundGameEndId);
        savePreferences();
        applyTheme();
        updateAnalyzerView();

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
            int tagColor = 0xFFFF7A00;
            if ("Brilliant".equals(rec.classification)) {
                tag = "[!! Brilliant !!]";
                tagColor = 0xFFFF9E0B;
            } else if ("Best".equals(rec.classification)) {
                tagColor = 0xFF10B981;
            } else if ("Inaccuracy".equals(rec.classification)) {
                tagColor = 0xFFF59E0B;
            } else if ("Mistake".equals(rec.classification)) {
                tagColor = 0xFFFB923C;
            } else if ("Blunder".equals(rec.classification)) {
                tagColor = 0xFFEF4444;
                playSound(soundBlunderId);
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
        updateOpeningDisplay();
        updateCapturedPiecesDisplay();
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

    private void updateColorPreference(int pref) {
        playerColorPreference = pref;
        if (btnColorWhite != null) {
            btnColorWhite.setBackgroundResource(pref == 0 ? R.drawable.nav_tab_active : R.drawable.nav_tab_inactive);
            btnColorWhite.setTextColor(pref == 0 ? 0xFFFF7A00 : 0xFF94A3B8);
        }
        if (btnColorRandom != null) {
            btnColorRandom.setBackgroundResource(pref == 1 ? R.drawable.nav_tab_active : R.drawable.nav_tab_inactive);
            btnColorRandom.setTextColor(pref == 1 ? 0xFFFF7A00 : 0xFF94A3B8);
        }
        if (btnColorBlack != null) {
            btnColorBlack.setBackgroundResource(pref == 2 ? R.drawable.nav_tab_active : R.drawable.nav_tab_inactive);
            btnColorBlack.setTextColor(pref == 2 ? 0xFFFF7A00 : 0xFF94A3B8);
        }
    }

    private void showBoardStyleDialog() {
        String[] options = {
            "Pieces: Lichess (cburnett)" + (pieceStyle == 0 ? "  [Active]" : ""),
            "Pieces: Neo Modern" + (pieceStyle == 1 ? "  [Active]" : ""),
            "Pieces: Tournament Staunton" + (pieceStyle == 2 ? "  [Active]" : ""),
            "Board: Lichess Green" + (boardTheme == 0 ? "  [Active]" : ""),
            "Board: Lichess Wood" + (boardTheme == 1 ? "  [Active]" : ""),
            "Board: Lichess Blue" + (boardTheme == 2 ? "  [Active]" : ""),
            "Board: Lichess Canvas" + (boardTheme == 3 ? "  [Active]" : ""),
            "Board: Lichess Charcoal" + (boardTheme == 4 ? "  [Active]" : ""),
            "Board: Midnight OLED (Pure Black)" + (boardTheme == 5 ? "  [Active]" : "")
        };

        createDialogBuilder()
            .setTitle("Piece Sets & Board Themes")
            .setItems(options, new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialog, int which) {
                    if (which == 0) {
                        pieceStyle = 0;
                        boardView.setPieceStyle(pieceStyle);
                        savePreferences();
                        Toast.makeText(MainActivity.this, "Pieces: Lichess (cburnett)", Toast.LENGTH_SHORT).show();
                    } else if (which == 1) {
                        pieceStyle = 1;
                        boardView.setPieceStyle(pieceStyle);
                        savePreferences();
                        Toast.makeText(MainActivity.this, "Pieces: Neo Modern", Toast.LENGTH_SHORT).show();
                    } else if (which == 2) {
                        pieceStyle = 2;
                        boardView.setPieceStyle(pieceStyle);
                        savePreferences();
                        Toast.makeText(MainActivity.this, "Pieces: Tournament Staunton", Toast.LENGTH_SHORT).show();
                    } else {
                        boardTheme = which - 3;
                        boardView.setBoardTheme(boardTheme);
                        savePreferences();
                        Toast.makeText(MainActivity.this, "Board: " + ChessBoardView.THEME_NAMES[boardTheme], Toast.LENGTH_SHORT).show();
                    }
                }
            })
            .setNegativeButton("Close", null)
            .show();
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
            rgThemeMode.addView(rb);
        }
        rgThemeMode.check(300 + themeMode);
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
            rgPiece.addView(rb);
        }
        rgPiece.check(200 + pieceStyle);
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
            rgTheme.addView(rb);
        }
        rgTheme.check(100 + boardTheme);
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
        btnCopyFen.setTextColor(0xFFFF7A00);
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
        txtCredits.setText("\nLibreChess RS\nVersion 1.3.0 • Material You Orange • Chess.com Overhaul\nLichess Pieces & Boards • Full Tactics Bot AI");
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
        if (soundPool != null) {
            soundPool.release();
            soundPool = null;
        }
    }
}
