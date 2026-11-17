use crate::models::{GameAnalysis, MoveAnalysis, MoveQuality, QualityCounts};
use rand::seq::SliceRandom;
use rand::Rng;
use shakmaty::fen::Fen;
use shakmaty::san::San;
use shakmaty::{CastlingMode, Chess, Color, Move, Position, Role, Square};
use std::io::{BufRead, BufReader, Write};
use std::process::{Child, ChildStdin, ChildStdout, Command, Stdio};
use std::str::FromStr;
use std::sync::Mutex;
use std::time::Duration;

// Positional Piece-Square Tables (oriented from White's perspective, index 0 = a1, 63 = h8)
// For Black, flip vertically (sq.flip_vertical())

#[rustfmt::skip]
const PAWN_PST: [i32; 64] = [
     0,   0,   0,   0,   0,   0,   0,   0,
     5,  10,  10, -20, -20,  10,  10,   5,
     5,  -5, -10,   0,   0, -10,  -5,   5,
     0,   0,   0,  25,  25,   0,   0,   0,
     5,   5,  10,  27,  27,  10,   5,   5,
    10,  10,  20,  30,  30,  20,  10,  10,
    50,  50,  50,  50,  50,  50,  50,  50,
     0,   0,   0,   0,   0,   0,   0,   0,
];

#[rustfmt::skip]
const KNIGHT_PST: [i32; 64] = [
    -50, -40, -30, -30, -30, -30, -40, -50,
    -40, -20,   0,   5,   5,   0, -20, -40,
    -30,   5,  10,  15,  15,  10,   5, -30,
    -30,   0,  15,  20,  20,  15,   0, -30,
    -30,   5,  15,  20,  20,  15,   5, -30,
    -30,   0,  10,  15,  15,  10,   0, -30,
    -40, -20,   0,   0,   0,   0, -20, -40,
    -50, -40, -30, -30, -30, -30, -40, -50,
];

#[rustfmt::skip]
const BISHOP_PST: [i32; 64] = [
    -20, -10, -10, -10, -10, -10, -10, -20,
    -10,   5,   0,   0,   0,   0,   5, -10,
    -10,  10,  10,  10,  10,  10,  10, -10,
    -10,   0,  10,  10,  10,  10,   0, -10,
    -10,   5,   5,  10,  10,   5,   5, -10,
    -10,   0,   5,  10,  10,   5,   0, -10,
    -10,   0,   0,   0,   0,   0,   0, -10,
    -20, -10, -10, -10, -10, -10, -10, -20,
];

#[rustfmt::skip]
const ROOK_PST: [i32; 64] = [
      0,   0,   0,   5,   5,   0,   0,   0,
     -5,   0,   0,   0,   0,   0,   0,  -5,
     -5,   0,   0,   0,   0,   0,   0,  -5,
     -5,   0,   0,   0,   0,   0,   0,  -5,
     -5,   0,   0,   0,   0,   0,   0,  -5,
     -5,   0,   0,   0,   0,   0,   0,  -5,
      5,  10,  10,  10,  10,  10,  10,   5,
      0,   0,   0,   0,   0,   0,   0,   0,
];

#[rustfmt::skip]
const QUEEN_PST: [i32; 64] = [
    -20, -10, -10,  -5,  -5, -10, -10, -20,
    -10,   0,   0,   0,   0,   0,   0, -10,
    -10,   0,   5,   5,   5,   5,   0, -10,
     -5,   0,   5,   5,   5,   5,   0,  -5,
      0,   0,   5,   5,   5,   5,   0,  -5,
    -10,   5,   5,   5,   5,   5,   0, -10,
    -10,   0,   5,   0,   0,   0,   0, -10,
    -20, -10, -10,  -5,  -5, -10, -10, -20,
];

#[rustfmt::skip]
const KING_MIDGAME_PST: [i32; 64] = [
     20,  30,  10,   0,   0,  10,  30,  20,
     20,  20,   0,   0,   0,   0,  20,  20,
    -10, -20, -20, -20, -20, -20, -20, -10,
    -20, -30, -30, -40, -40, -30, -30, -20,
    -30, -40, -40, -50, -50, -40, -40, -30,
    -30, -40, -40, -50, -50, -40, -40, -30,
    -30, -40, -40, -50, -50, -40, -40, -30,
    -30, -40, -40, -50, -50, -40, -40, -30,
];

struct PersistentStockfish {
    child: Child,
    stdin: ChildStdin,
    reader: BufReader<ChildStdout>,
}

impl PersistentStockfish {
    fn spawn(path: &str) -> Result<Self, String> {
        let mut child = Command::new(path)
            .stdin(Stdio::piped())
            .stdout(Stdio::piped())
            .stderr(Stdio::null())
            .spawn()
            .map_err(|e| format!("Failed to spawn Stockfish: {}", e))?;

        let mut stdin = child.stdin.take().ok_or("Failed to open child stdin")?;
        let stdout = child.stdout.take().ok_or("Failed to open child stdout")?;
        let mut reader = BufReader::new(stdout);

        // UCI Handshake
        stdin
            .write_all(b"uci\n")
            .map_err(|e| format!("Failed to write uci: {}", e))?;
        stdin.flush().map_err(|e| e.to_string())?;

        let mut line = String::new();
        let start = std::time::Instant::now();
        while start.elapsed() < Duration::from_secs(4) {
            line.clear();
            if reader.read_line(&mut line).is_err() || line.is_empty() {
                break;
            }
            if line.trim() == "uciok" {
                break;
            }
        }

        // Ready check
        stdin
            .write_all(b"isready\n")
            .map_err(|e| e.to_string())?;
        stdin.flush().map_err(|e| e.to_string())?;

        let start2 = std::time::Instant::now();
        while start2.elapsed() < Duration::from_secs(4) {
            line.clear();
            if reader.read_line(&mut line).is_err() || line.is_empty() {
                break;
            }
            if line.trim() == "readyok" {
                break;
            }
        }

        Ok(PersistentStockfish {
            child,
            stdin,
            reader,
        })
    }

    fn send_command(&mut self, cmd: &str) -> Result<(), String> {
        self.stdin
            .write_all(cmd.as_bytes())
            .map_err(|e| e.to_string())?;
        self.stdin.flush().map_err(|e| e.to_string())
    }

    fn wait_for_ready(&mut self) -> Result<(), String> {
        self.send_command("isready\n")?;
        let mut line = String::new();
        let start = std::time::Instant::now();
        while start.elapsed() < Duration::from_secs(3) {
            line.clear();
            if self.reader.read_line(&mut line).is_err() || line.is_empty() {
                break;
            }
            if line.trim() == "readyok" {
                return Ok(());
            }
        }
        Err("Stockfish readyok timeout".to_string())
    }

    fn get_bestmove(&mut self, fen: &str, bot_level: u32) -> Result<String, String> {
        self.wait_for_ready()?;

        // Configure Stockfish Skill Level & Elo
        let (skill, elo_limit, search_cmd) = match bot_level {
            1 => (0, Some(1320), "go movetime 100\n".to_string()),
            2 => (3, Some(1320), "go movetime 180\n".to_string()),
            3 => (7, Some(1350), "go movetime 250\n".to_string()),
            4 => (12, Some(1500), "go depth 10\n".to_string()), // Bot 1500: Deep tactical calculation
            _ => (20, None, "go depth 14\n".to_string()),
        };

        if let Some(elo) = elo_limit {
            self.send_command("setoption name UCI_LimitStrength value true\n")?;
            self.send_command(&format!("setoption name UCI_Elo value {}\n", elo))?;
        } else {
            self.send_command("setoption name UCI_LimitStrength value false\n")?;
        }
        self.send_command(&format!("setoption name Skill Level value {}\n", skill))?;

        self.send_command(&format!("position fen {}\n", fen))?;
        self.send_command(&search_cmd)?;

        let mut line = String::new();
        let start = std::time::Instant::now();
        while start.elapsed() < Duration::from_secs(6) {
            line.clear();
            if self.reader.read_line(&mut line).is_err() || line.is_empty() {
                break;
            }
            if line.starts_with("bestmove") {
                let parts: Vec<&str> = line.split_whitespace().collect();
                if parts.len() >= 2 && parts[1] != "(none)" {
                    return Ok(parts[1].to_string());
                }
            }
        }

        Err("No bestmove from Stockfish".to_string())
    }

    fn eval_position(&mut self, fen: &str, depth: u32) -> Result<(f32, Option<String>), String> {
        self.wait_for_ready()?;
        self.send_command("setoption name UCI_LimitStrength value false\n")?;
        self.send_command("setoption name Skill Level value 20\n")?;
        self.send_command(&format!("position fen {}\n", fen))?;
        self.send_command(&format!("go depth {}\n", depth))?;

        let mut bestmove = None;
        let mut score = 0.0f32;
        let mut line = String::new();
        let is_white_turn = !fen.contains(" b ");

        let start = std::time::Instant::now();
        while start.elapsed() < Duration::from_secs(5) {
            line.clear();
            if self.reader.read_line(&mut line).is_err() || line.is_empty() {
                break;
            }

            if line.contains("score cp ") {
                let parts: Vec<&str> = line.split_whitespace().collect();
                if let Some(cp_idx) = parts.iter().position(|&x| x == "cp") {
                    if let Some(val_str) = parts.get(cp_idx + 1) {
                        if let Ok(cp) = val_str.parse::<i32>() {
                            let raw = cp as f32 / 100.0;
                            score = if is_white_turn { raw } else { -raw };
                        }
                    }
                }
            } else if line.contains("score mate ") {
                let parts: Vec<&str> = line.split_whitespace().collect();
                if let Some(mate_idx) = parts.iter().position(|&x| x == "mate") {
                    if let Some(val_str) = parts.get(mate_idx + 1) {
                        if let Ok(moves) = val_str.parse::<i32>() {
                            let mate_val = if moves > 0 { 20.0 } else { -20.0 };
                            score = if is_white_turn { mate_val } else { -mate_val };
                        }
                    }
                }
            }

            if line.starts_with("bestmove") {
                let parts: Vec<&str> = line.split_whitespace().collect();
                if parts.len() >= 2 && parts[1] != "(none)" {
                    bestmove = Some(parts[1].to_string());
                }
                break;
            }
        }

        Ok((score, bestmove))
    }
}

impl Drop for PersistentStockfish {
    fn drop(&mut self) {
        let _ = self.stdin.write_all(b"quit\n");
        let _ = self.stdin.flush();
        let _ = self.child.kill();
        let _ = self.child.wait();
    }
}

pub struct EngineManager {
    stockfish_path: Option<String>,
    worker: Mutex<Option<PersistentStockfish>>,
}

impl EngineManager {
    pub fn new() -> Self {
        let possible_paths = [
            "/data/data/com.termux/files/usr/bin/stockfish",
            "stockfish",
            "/usr/games/stockfish",
            "/usr/bin/stockfish",
            "/usr/local/bin/stockfish",
        ];

        let mut found = None;
        let mut worker = None;

        for path in possible_paths {
            if let Ok(w) = PersistentStockfish::spawn(path) {
                found = Some(path.to_string());
                worker = Some(w);
                break;
            }
        }

        EngineManager {
            stockfish_path: found,
            worker: Mutex::new(worker),
        }
    }

    pub fn has_stockfish(&self) -> bool {
        self.stockfish_path.is_some()
    }

    /// Ask Stockfish or high-level Shakmaty Minimax engine for a bot move
    pub fn get_bot_move(&self, fen: &str, bot_level: u32) -> Result<String, String> {
        let setup: Fen = fen.parse().map_err(|e| format!("Invalid FEN: {:?}", e))?;
        let pos: Chess = setup
            .into_position(CastlingMode::Chess960)
            .map_err(|e| format!("Invalid position: {:?}", e))?;

        let legals = pos.legal_moves();
        if legals.is_empty() {
            return Err("No legal moves available".to_string());
        }

        // For beginner bot level 1 (100 Elo):
        // Filter out any move that blunders immediate checkmate-in-1, then 35% chance for friendly casual play
        if bot_level == 1 {
            let mut rng = rand::thread_rng();
            if rng.gen_bool(0.35) {
                let non_suicidal: Vec<&Move> = legals
                    .iter()
                    .filter(|m| {
                        let mut next = pos.clone();
                        next.play_unchecked(m);
                        !has_immediate_mate(&next)
                    })
                    .collect();

                if let Some(m) = non_suicidal.choose(&mut rng) {
                    return Ok(m.to_uci(CastlingMode::Standard).to_string());
                }
            }
        }

        // Try persistent Stockfish first
        {
            let mut lock = self.worker.lock().unwrap();
            if let Some(ref mut sf) = *lock {
                match sf.get_bestmove(fen, bot_level) {
                    Ok(m) => {
                        // Verify that Stockfish returned a valid legal move
                        if legals.iter().any(|lm| lm.to_uci(CastlingMode::Standard).to_string() == m) {
                            return Ok(m);
                        }
                    }
                    Err(e) => {
                        eprintln!("[ENGINE] Stockfish query error: {}, falling back to internal", e);
                    }
                }
            }
        }

        // High-level pure Rust Alpha-Beta Minimax Engine fallback
        self.native_best_move(&pos, bot_level)
    }

    /// Evaluates a single FEN position
    pub fn evaluate_position(&self, fen: &str, depth: u32) -> (f32, Option<String>) {
        {
            let mut lock = self.worker.lock().unwrap();
            if let Some(ref mut sf) = *lock {
                if let Ok(res) = sf.eval_position(fen, depth) {
                    return res;
                }
            }
        }
        self.native_eval_position(fen)
    }

    fn native_best_move(&self, pos: &Chess, bot_level: u32) -> Result<String, String> {
        let depth = match bot_level {
            1 => 2,
            2 => 3,
            3 => 4,
            4 => 5, // Bot 1500: Depth 5 + Quiescence search
            _ => 6,
        };

        let best_move = search_best_move(pos, depth)?;
        Ok(best_move.to_uci(CastlingMode::Standard).to_string())
    }

    fn native_eval_position(&self, fen: &str) -> (f32, Option<String>) {
        let setup: Fen = match fen.parse() {
            Ok(s) => s,
            Err(_) => return (0.0, None),
        };
        let pos: Chess = match setup.into_position(CastlingMode::Chess960) {
            Ok(p) => p,
            Err(_) => return (0.0, None),
        };

        let score = evaluate_board(&pos) as f32 / 100.0;
        let best_m = search_best_move(&pos, 4)
            .ok()
            .map(|m| m.to_uci(CastlingMode::Standard).to_string());

        (score, best_m)
    }

    /// Full post-game analysis of a chess match
    pub fn analyze_game(&self, game_id: &str, moves_san: &[String]) -> GameAnalysis {
        let mut pos = Chess::default();
        let mut moves_analysis = Vec::new();
        let mut eval_history = Vec::new();

        let mut white_accuracies = Vec::new();
        let mut black_accuracies = Vec::new();

        let mut white_counts = QualityCounts::default();
        let mut black_counts = QualityCounts::default();

        let initial_fen = Fen::from_position(pos.clone(), shakmaty::EnPassantMode::Legal).to_string();
        let (initial_eval, _) = self.evaluate_position(&initial_fen, 8);
        eval_history.push(initial_eval);

        for (ply, san_str) in moves_san.iter().enumerate() {
            let color = if ply % 2 == 0 { "white" } else { "black" };
            let move_number = (ply / 2) + 1;
            let is_white = color == "white";

            let fen_before =
                Fen::from_position(pos.clone(), shakmaty::EnPassantMode::Legal).to_string();

            let parsed_san = match San::from_str(san_str) {
                Ok(s) => s,
                Err(_) => continue,
            };

            let legal_move = match parsed_san.to_move(&pos) {
                Ok(m) => m,
                Err(_) => continue,
            };

            let uci_move = legal_move.to_uci(CastlingMode::Standard).to_string();

            let (eval_before, best_move_uci) = self.evaluate_position(&fen_before, 8);

            let captured_role = pos.board().piece_at(legal_move.to()).map(|p| p.role);
            let moved_role = legal_move
                .from()
                .and_then(|sq| pos.board().piece_at(sq))
                .map(|p| p.role);

            pos.play_unchecked(&legal_move);

            let fen_after =
                Fen::from_position(pos.clone(), shakmaty::EnPassantMode::Legal).to_string();
            let (eval_after, _) = self.evaluate_position(&fen_after, 8);
            eval_history.push(eval_after);

            let eval_diff = if is_white {
                eval_before - eval_after
            } else {
                eval_after - eval_before
            };
            let cp_loss = (eval_diff * 100.0).max(0.0);

            let is_best = best_move_uci.as_deref() == Some(&uci_move) || cp_loss < 8.0;

            let is_sacrifice = moved_role
                .map(|r| piece_value_cp(r) > captured_role.map(piece_value_cp).unwrap_or(0) + 150)
                .unwrap_or(false);

            let current_advantage = if is_white { eval_after } else { -eval_after };

            let (quality, comment) = if is_sacrifice && is_best && current_advantage > 0.5 {
                (
                    MoveQuality::Brilliant,
                    "An exceptional tactical sacrifice that crushes the position!!".to_string(),
                )
            } else if is_best && cp_loss < 4.0 {
                (
                    MoveQuality::Best,
                    "The top engine choice in this position.".to_string(),
                )
            } else if cp_loss <= 18.0 {
                (
                    MoveQuality::Excellent,
                    "An excellent and precise continuation.".to_string(),
                )
            } else if cp_loss <= 45.0 {
                (
                    MoveQuality::Good,
                    "A solid and reliable move.".to_string(),
                )
            } else if cp_loss <= 100.0 {
                (
                    MoveQuality::Inaccuracy,
                    "Slight inaccuracy; there was a cleaner line available.".to_string(),
                )
            } else if cp_loss <= 220.0 {
                (
                    MoveQuality::Mistake,
                    "A mistake that concessions advantage to the opponent.".to_string(),
                )
            } else {
                (
                    MoveQuality::Blunder,
                    "A serious blunder that alters the game trajectory!".to_string(),
                )
            };

            let counts = if is_white {
                &mut white_counts
            } else {
                &mut black_counts
            };
            match quality {
                MoveQuality::Brilliant => counts.brilliant += 1,
                MoveQuality::Great => counts.great += 1,
                MoveQuality::Best => counts.best += 1,
                MoveQuality::Excellent => counts.excellent += 1,
                MoveQuality::Good => counts.good += 1,
                MoveQuality::Book => counts.book += 1,
                MoveQuality::Inaccuracy => counts.inaccuracy += 1,
                MoveQuality::Mistake => counts.mistake += 1,
                MoveQuality::Blunder => counts.blunder += 1,
                MoveQuality::Miss => counts.miss += 1,
            }

            let move_accuracy = (100.0 * (-0.0035 * cp_loss).exp()).clamp(0.0, 100.0);
            if is_white {
                white_accuracies.push(move_accuracy);
            } else {
                black_accuracies.push(move_accuracy);
            }

            moves_analysis.push(MoveAnalysis {
                ply,
                move_number,
                color: color.to_string(),
                uci: uci_move,
                san: san_str.clone(),
                fen_before,
                fen_after,
                eval_before,
                eval_after,
                best_move_uci,
                best_move_san: None,
                quality,
                centipawn_loss: cp_loss,
                comment,
            });
        }

        let accuracy_white = if !white_accuracies.is_empty() {
            white_accuracies.iter().sum::<f32>() / white_accuracies.len() as f32
        } else {
            100.0
        };

        let accuracy_black = if !black_accuracies.is_empty() {
            black_accuracies.iter().sum::<f32>() / black_accuracies.len() as f32
        } else {
            100.0
        };

        let coach_summary = format!(
            "Game Review: White finished with {:.1}% accuracy, Black with {:.1}% accuracy. White found {} brilliant move(s) and Black found {} brilliant move(s).",
            accuracy_white, accuracy_black, white_counts.brilliant, black_counts.brilliant
        );

        GameAnalysis {
            game_id: game_id.to_string(),
            accuracy_white: (accuracy_white * 10.0).round() / 10.0,
            accuracy_black: (accuracy_black * 10.0).round() / 10.0,
            white_counts,
            black_counts,
            moves: moves_analysis,
            eval_history,
            coach_summary,
        }
    }
}

// =========================================================================
// Native Shakmaty Minimax Alpha-Beta Search Engine with Full Tactics
// =========================================================================

const MATE_SCORE: i32 = 100_000;

fn piece_value_cp(role: Role) -> i32 {
    match role {
        Role::Pawn => 100,
        Role::Knight => 325,
        Role::Bishop => 335,
        Role::Rook => 505,
        Role::Queen => 950,
        Role::King => 20_000,
    }
}

/// Evaluates static score in centipawns from White's perspective (+100 = White +1 pawn)
/// Includes positional PST, tactical evaluations (hanging pieces, forks, pins, king safety, open files)
fn evaluate_board(pos: &Chess) -> i32 {
    let mut score = 0;

    let board = pos.board();
    let white_bb = board.white();
    let black_bb = board.black();

    let mut white_bishops = 0;
    let mut black_bishops = 0;

    for sq in Square::ALL {
        if let Some(piece) = board.piece_at(sq) {
            let val = piece_value_cp(piece.role);
            let is_white = piece.color == Color::White;
            let pst_idx = if is_white {
                sq as usize
            } else {
                sq.flip_vertical() as usize
            };

            let pst = match piece.role {
                Role::Pawn => PAWN_PST[pst_idx],
                Role::Knight => KNIGHT_PST[pst_idx],
                Role::Bishop => {
                    if is_white { white_bishops += 1; } else { black_bishops += 1; }
                    BISHOP_PST[pst_idx]
                }
                Role::Rook => {
                    let mut rook_bonus = ROOK_PST[pst_idx];
                    let file = sq.file();
                    // Open file bonus: no friendly pawns on file
                    let file_pawns = board.pawns() & shakmaty::Bitboard::from(file);
                    if file_pawns.is_empty() {
                        rook_bonus += 35; // Fully open file
                    } else if (file_pawns & (if is_white { white_bb } else { black_bb })).is_empty() {
                        rook_bonus += 20; // Semi-open file
                    }
                    // 7th rank bonus
                    if (is_white && sq.rank() == shakmaty::Rank::Seventh)
                        || (!is_white && sq.rank() == shakmaty::Rank::Second)
                    {
                        rook_bonus += 40;
                    }
                    rook_bonus
                }
                Role::Queen => QUEEN_PST[pst_idx],
                Role::King => KING_MIDGAME_PST[pst_idx],
            };

            let mut piece_score = val + pst;

            // Tactical evaluation: Hanging pieces and attack/defense balance
            let opp_color = piece.color.other();
            let is_attacked = pos.board().attacks_to(sq, opp_color, board.occupied()).any();
            let is_defended = pos.board().attacks_to(sq, piece.color, board.occupied()).any();

            if is_attacked && !is_defended {
                // Free hanging piece! Huge tactical penalty
                piece_score -= val / 2;
            } else if is_attacked && is_defended {
                // Check if attacked by a lesser value piece (e.g. pawn attacking knight/queen)
                let enemy_pawns = board.pawns() & (if is_white { black_bb } else { white_bb });
                if piece.role != Role::Pawn && (pos.board().attacks_to(sq, opp_color, board.occupied()) & enemy_pawns).any() {
                    piece_score -= 60; // Tactical pin/pawn fork danger
                }
            }

            // Tactical fork potential: Knights attacking multiple high-value targets
            if piece.role == Role::Knight {
                let attacks = pos.board().attacks_from(sq);
                let enemy_majors = (if is_white { black_bb } else { white_bb })
                    & (board.queens() | board.rooks() | board.kings());
                if (attacks & enemy_majors).count() >= 2 {
                    piece_score += 150; // Tactical Knight fork!
                }
            }

            // Tactical fork potential: Pawns attacking multiple high-value targets
            if piece.role == Role::Pawn {
                let attacks = pos.board().attacks_from(sq);
                let enemy_minors_majors = (if is_white { black_bb } else { white_bb }) & !board.pawns();
                if (attacks & enemy_minors_majors).count() >= 2 {
                    piece_score += 180; // Tactical Pawn fork!
                }
            }

            // Tactical Queen double-attack
            if piece.role == Role::Queen {
                let attacks = pos.board().attacks_from(sq);
                let enemy_majors = (if is_white { black_bb } else { white_bb })
                    & (board.queens() | board.rooks() | board.kings());
                if (attacks & enemy_majors).count() >= 2 {
                    piece_score += 120; // Tactical Queen fork/skewer!
                }
            }

            if is_white {
                score += piece_score;
            } else {
                score -= piece_score;
            }
        }
    }

    // Bishop pair tactical bonus
    if white_bishops >= 2 {
        score += 45;
    }
    if black_bishops >= 2 {
        score -= 45;
    }

    score
}

fn has_immediate_mate(pos: &Chess) -> bool {
    for m in pos.legal_moves() {
        let mut next = pos.clone();
        next.play_unchecked(&m);
        if next.is_checkmate() {
            return true;
        }
    }
    false
}

/// Order moves to maximize alpha-beta pruning cutoffs
/// Priority: Checks > Tactical captures (MVV-LVA) > Promotions > Quiet moves
fn score_move(pos: &Chess, m: &Move) -> i32 {
    let mut score = 0;

    let mut next = pos.clone();
    next.play_unchecked(m);
    if next.is_checkmate() {
        return 100_000; // Immediate checkmate wins!
    }
    if next.is_check() {
        score += 8_000; // Check gives high tactical priority
    }

    if m.is_capture() {
        let victim = pos.board().piece_at(m.to()).map(|p| piece_value_cp(p.role)).unwrap_or(100);
        let attacker = m.from().and_then(|sq| pos.board().piece_at(sq)).map(|p| piece_value_cp(p.role)).unwrap_or(100);
        score += 10_000 + (victim * 10 - attacker);
    }

    if m.is_promotion() {
        score += 9_500;
    }

    score
}

fn order_moves(pos: &Chess, moves: &mut [Move]) {
    moves.sort_by_cached_key(|m| -score_move(pos, m));
}

/// Quiescence search evaluates capture sequences AND checks to prevent tactical horizon blunders
fn quiescence(pos: &Chess, mut alpha: i32, beta: i32, is_white: bool, max_depth: i32) -> i32 {
    let in_check = pos.is_check();
    let stand_pat = evaluate_board(pos);

    if max_depth <= 0 && !in_check {
        return stand_pat;
    }

    if is_white {
        if !in_check {
            if stand_pat >= beta {
                return beta;
            }
            if stand_pat > alpha {
                alpha = stand_pat;
            }
        }

        let mut tactical_moves: Vec<Move> = if in_check {
            // Must evaluate all legal evasions when in check
            pos.legal_moves().into_iter().collect()
        } else {
            // Search all captures, promotions, and checking moves
            pos.legal_moves()
                .into_iter()
                .filter(|m| {
                    if m.is_capture() || m.is_promotion() {
                        true
                    } else if max_depth >= 2 {
                        let mut next = pos.clone();
                        next.play_unchecked(m);
                        next.is_check()
                    } else {
                        false
                    }
                })
                .collect()
        };

        if tactical_moves.is_empty() {
            return if in_check { -MATE_SCORE } else { stand_pat };
        }

        order_moves(pos, &mut tactical_moves);

        for m in tactical_moves {
            let mut next = pos.clone();
            next.play_unchecked(&m);
            let score = quiescence(&next, alpha, beta, false, max_depth - 1);
            if score >= beta {
                return beta;
            }
            if score > alpha {
                alpha = score;
            }
        }
        alpha
    } else {
        let mut beta_mut = beta;
        if !in_check {
            if stand_pat <= alpha {
                return alpha;
            }
            if stand_pat < beta_mut {
                beta_mut = stand_pat;
            }
        }

        let mut tactical_moves: Vec<Move> = if in_check {
            pos.legal_moves().into_iter().collect()
        } else {
            pos.legal_moves()
                .into_iter()
                .filter(|m| {
                    if m.is_capture() || m.is_promotion() {
                        true
                    } else if max_depth >= 2 {
                        let mut next = pos.clone();
                        next.play_unchecked(m);
                        next.is_check()
                    } else {
                        false
                    }
                })
                .collect()
        };

        if tactical_moves.is_empty() {
            return if in_check { MATE_SCORE } else { stand_pat };
        }

        order_moves(pos, &mut tactical_moves);

        for m in tactical_moves {
            let mut next = pos.clone();
            next.play_unchecked(&m);
            let score = quiescence(&next, alpha, beta_mut, true, max_depth - 1);
            if score <= alpha {
                return alpha;
            }
            if score < beta_mut {
                beta_mut = score;
            }
        }
        beta_mut
    }
}

/// Alpha-Beta Minimax search
fn alpha_beta(
    pos: &Chess,
    depth: i32,
    mut alpha: i32,
    mut beta: i32,
    ply: i32,
    is_white: bool,
) -> i32 {
    if pos.is_checkmate() {
        return if is_white {
            -MATE_SCORE + ply * 100 // Black wins
        } else {
            MATE_SCORE - ply * 100 // White wins
        };
    }

    if pos.is_stalemate() || pos.is_insufficient_material() {
        return 0;
    }

    if depth <= 0 {
        return quiescence(pos, alpha, beta, is_white, 3);
    }

    let mut legals = pos.legal_moves();
    order_moves(pos, &mut legals);

    if is_white {
        let mut max_eval = -999_999;
        for m in &legals {
            let mut next = pos.clone();
            next.play_unchecked(m);
            let eval = alpha_beta(&next, depth - 1, alpha, beta, ply + 1, false);
            if eval > max_eval {
                max_eval = eval;
            }
            if eval > alpha {
                alpha = eval;
            }
            if beta <= alpha {
                break;
            }
        }
        max_eval
    } else {
        let mut min_eval = 999_999;
        for m in &legals {
            let mut next = pos.clone();
            next.play_unchecked(m);
            let eval = alpha_beta(&next, depth - 1, alpha, beta, ply + 1, true);
            if eval < min_eval {
                min_eval = eval;
            }
            if eval < beta {
                beta = eval;
            }
            if beta <= alpha {
                break;
            }
        }
        min_eval
    }
}

pub fn search_best_move(pos: &Chess, depth: i32) -> Result<Move, String> {
    let mut legals = pos.legal_moves();
    if legals.is_empty() {
        return Err("No legal moves".to_string());
    }

    order_moves(pos, &mut legals);

    let is_white = pos.turn() == Color::White;
    let mut best_move = legals[0].clone();
    let mut alpha = -999_999;
    let mut beta = 999_999;

    if is_white {
        let mut best_score = -999_999;
        for m in &legals {
            let mut next = pos.clone();
            next.play_unchecked(m);
            let score = alpha_beta(&next, depth - 1, alpha, beta, 1, false);
            if score > best_score {
                best_score = score;
                best_move = m.clone();
            }
            if score > alpha {
                alpha = score;
            }
        }
    } else {
        let mut best_score = 999_999;
        for m in &legals {
            let mut next = pos.clone();
            next.play_unchecked(m);
            let score = alpha_beta(&next, depth - 1, alpha, beta, 1, true);
            if score < best_score {
                best_score = score;
                best_move = m.clone();
            }
            if score < beta {
                beta = score;
            }
        }
    }

    Ok(best_move)
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn test_scholars_mate_defense() {
        // Position: 1. e4 e5 2. Qh5 Nc6 3. Bc4
        // White threatens 4. Qxf7# (Scholar's Mate)
        let fen = "r1bqkbnr/pppp1ppp/2n5/4p2Q/2B1P3/8/PPPP1PPP/RNB1K1NR b KQkq - 3 3";
        let engine = EngineManager::new();
        let bot_move = engine.get_bot_move(fen, 4).expect("Should return a bot move");

        // The defending moves are g7g6, d8e7, d8f6, or g8h6
        assert_ne!(bot_move, "g8f6", "Bot 1500 must NOT blunder Scholar's Mate with g8f6!");
        assert_ne!(bot_move, "a7a6", "Bot 1500 must NOT play a7a6 under mate threat!");
        assert_ne!(bot_move, "d7d6", "Bot 1500 must NOT play d7d6 under mate threat!");

        assert!(
            bot_move == "g7g6" || bot_move == "d8e7" || bot_move == "d8f6" || bot_move == "g8h6",
            "Bot 1500 must defend against Scholar's Mate! Got: {}",
            bot_move
        );
    }

    #[test]
    fn test_native_scholars_mate_defense() {
        let fen = "r1bqkbnr/pppp1ppp/2n5/4p2Q/2B1P3/8/PPPP1PPP/RNB1K1NR b KQkq - 3 3";
        let setup: Fen = fen.parse().unwrap();
        let pos: Chess = setup.into_position(CastlingMode::Chess960).unwrap();

        // Native depth 4-5 alpha beta
        let best_move = search_best_move(&pos, 4).unwrap();
        let uci = best_move.to_uci(CastlingMode::Standard).to_string();

        assert_ne!(uci, "g8f6", "Native engine must NOT blunder Scholar's Mate with g8f6!");
        assert_ne!(uci, "a7a6", "Native engine must NOT play a7a6 under mate threat!");
        assert!(
            uci == "g7g6" || uci == "d8e7" || uci == "d8f6" || uci == "g8h6",
            "Native engine must defend against Scholar's Mate! Got: {}",
            uci
        );
    }
}
