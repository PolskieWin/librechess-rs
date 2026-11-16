use crate::models::{GameAnalysis, MoveAnalysis, MoveQuality, QualityCounts};
use rand::seq::SliceRandom;
use rand::Rng;
use shakmaty::fen::Fen;
use shakmaty::san::San;
use shakmaty::{CastlingMode, Chess, Color, Position, Role};
use std::io::{BufRead, BufReader, Write};
use std::process::{Command, Stdio};
use std::str::FromStr;
use std::time::Duration;

pub struct EngineManager {
    stockfish_path: Option<String>,
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
        for path in possible_paths {
            if let Ok(output) = Command::new(path).arg("uci").output() {
                let stdout = String::from_utf8_lossy(&output.stdout);
                if stdout.contains("Stockfish") || stdout.contains("uciok") {
                    found = Some(path.to_string());
                    break;
                }
            }
        }

        EngineManager {
            stockfish_path: found,
        }
    }

    pub fn has_stockfish(&self) -> bool {
        self.stockfish_path.is_some()
    }

    /// Ask Stockfish or fallback engine for a bot move
    pub fn get_bot_move(&self, fen: &str, bot_level: u32) -> Result<String, String> {
        let setup: Fen = fen.parse().map_err(|e| format!("Invalid FEN: {:?}", e))?;
        let pos: Chess = setup
            .into_position(CastlingMode::Chess960)
            .map_err(|e| format!("Invalid position: {:?}", e))?;

        let legals = pos.legal_moves();
        if legals.is_empty() {
            return Err("No legal moves available".to_string());
        }

        // For beginner bot level 1 (100-300 Elo):
        // 35% chance to play a random legal move to make it beatable and realistic for beginners
        if bot_level == 1 {
            let mut rng = rand::thread_rng();
            if rng.gen_bool(0.35) {
                let m = legals.choose(&mut rng).unwrap();
                return Ok(m.to_uci(CastlingMode::Standard).to_string());
            }
        }

        let depth = match bot_level {
            1 => 1,
            2 => 3,
            3 => 6,
            4 => 10,
            _ => 14,
        };

        if let Some(ref path) = self.stockfish_path {
            match self.query_stockfish_bestmove(path, fen, depth, bot_level) {
                Ok(m) => return Ok(m),
                Err(err) => {
                    eprintln!("Stockfish query error: {}, falling back to internal", err);
                }
            }
        }

        // Fallback internal move selector
        self.fallback_best_move(&pos)
    }

    fn query_stockfish_bestmove(
        &self,
        path: &str,
        fen: &str,
        depth: u32,
        bot_level: u32,
    ) -> Result<String, String> {
        let mut child = Command::new(path)
            .stdin(Stdio::piped())
            .stdout(Stdio::piped())
            .stderr(Stdio::null())
            .spawn()
            .map_err(|e| e.to_string())?;

        let mut stdin = child.stdin.take().ok_or("Failed to open stdin")?;
        let stdout = child.stdout.take().ok_or("Failed to open stdout")?;
        let mut reader = BufReader::new(stdout);

        // Configure Stockfish Skill Level for realistic bot Elo
        let skill_level = match bot_level {
            1 => 0,
            2 => 3,
            3 => 8,
            4 => 14,
            _ => 20,
        };

        let commands = format!(
            "uci\nsetoption name Skill Level value {}\nposition fen {}\ngo depth {}\n",
            skill_level, fen, depth
        );

        stdin
            .write_all(commands.as_bytes())
            .map_err(|e| e.to_string())?;
        stdin.flush().map_err(|e| e.to_string())?;

        let mut bestmove = String::new();
        let mut line = String::new();

        let start = std::time::Instant::now();
        while start.elapsed() < Duration::from_secs(5) {
            line.clear();
            if reader.read_line(&mut line).is_err() || line.is_empty() {
                break;
            }
            if line.starts_with("bestmove") {
                let parts: Vec<&str> = line.split_whitespace().collect();
                if parts.len() >= 2 {
                    bestmove = parts[1].to_string();
                }
                break;
            }
        }

        let _ = child.kill();
        let _ = child.wait();

        if bestmove.is_empty() || bestmove == "(none)" {
            return Err("No bestmove returned by Stockfish".to_string());
        }

        Ok(bestmove)
    }

    /// Evaluates a single FEN position. Returns score in pawns from White's perspective (+1.0 = +1 pawn for White)
    /// Also returns the engine's recommended best move in UCI format.
    pub fn evaluate_position(&self, fen: &str, depth: u32) -> (f32, Option<String>) {
        if let Some(ref path) = self.stockfish_path {
            if let Ok(res) = self.query_stockfish_eval(path, fen, depth) {
                return res;
            }
        }
        self.fallback_eval_position(fen)
    }

    fn query_stockfish_eval(&self, path: &str, fen: &str, depth: u32) -> Result<(f32, Option<String>), String> {
        let mut child = Command::new(path)
            .stdin(Stdio::piped())
            .stdout(Stdio::piped())
            .stderr(Stdio::null())
            .spawn()
            .map_err(|e| e.to_string())?;

        let mut stdin = child.stdin.take().ok_or("Failed to open stdin")?;
        let stdout = child.stdout.take().ok_or("Failed to open stdout")?;
        let mut reader = BufReader::new(stdout);

        let commands = format!("uci\nposition fen {}\ngo depth {}\n", fen, depth);
        stdin
            .write_all(commands.as_bytes())
            .map_err(|e| e.to_string())?;
        stdin.flush().map_err(|e| e.to_string())?;

        let mut bestmove = None;
        let mut score = 0.0f32;
        let mut line = String::new();

        // Determine whose turn it is from FEN
        let is_white_turn = !fen.contains(" b ");

        let start = std::time::Instant::now();
        while start.elapsed() < Duration::from_secs(4) {
            line.clear();
            if reader.read_line(&mut line).is_err() || line.is_empty() {
                break;
            }

            if line.contains("score cp ") {
                let parts: Vec<&str> = line.split_whitespace().collect();
                if let Some(cp_idx) = parts.iter().position(|&x| x == "cp") {
                    if let Some(val_str) = parts.get(cp_idx + 1) {
                        if let Ok(cp) = val_str.parse::<i32>() {
                            let raw_pawns = cp as f32 / 100.0;
                            // Stockfish UCI returns score relative to side to move!
                            score = if is_white_turn { raw_pawns } else { -raw_pawns };
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

        let _ = child.kill();
        let _ = child.wait();

        Ok((score, bestmove))
    }

    /// Fast fallback evaluator based on piece values and central control
    fn fallback_eval_position(&self, fen: &str) -> (f32, Option<String>) {
        let setup: Fen = match fen.parse() {
            Ok(s) => s,
            Err(_) => return (0.0, None),
        };
        let pos: Chess = match setup.into_position(CastlingMode::Chess960) {
            Ok(p) => p,
            Err(_) => return (0.0, None),
        };

        let score = evaluate_material_and_mobility(&pos);
        let best_move = self.fallback_best_move(&pos).ok();
        (score, best_move)
    }

    fn fallback_best_move(&self, pos: &Chess) -> Result<String, String> {
        let legals = pos.legal_moves();
        if legals.is_empty() {
            return Err("No legal moves".to_string());
        }

        let is_white = pos.turn() == Color::White;
        let mut best_m = &legals[0];
        let mut best_score = if is_white { -9999.0 } else { 9999.0 };

        for m in &legals {
            let mut next_pos = pos.clone();
            next_pos.play_unchecked(m);
            let s = evaluate_material_and_mobility(&next_pos);
            if is_white && s > best_score {
                best_score = s;
                best_m = m;
            } else if !is_white && s < best_score {
                best_score = s;
                best_m = m;
            }
        }

        Ok(best_m.to_uci(CastlingMode::Standard).to_string())
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
        let (mut current_eval, _) = self.evaluate_position(&initial_fen, 8);
        eval_history.push(current_eval);

        for (ply, san_str) in moves_san.iter().enumerate() {
            let color = if ply % 2 == 0 { "white" } else { "black" };
            let move_number = (ply / 2) + 1;
            let is_white = color == "white";

            let fen_before =
                Fen::from_position(pos.clone(), shakmaty::EnPassantMode::Legal).to_string();

            // Find matching legal move for the SAN
            let parsed_san = match San::from_str(san_str) {
                Ok(s) => s,
                Err(_) => continue,
            };

            let legal_move = match parsed_san.to_move(&pos) {
                Ok(m) => m,
                Err(_) => continue,
            };

            let uci_move = legal_move.to_uci(CastlingMode::Standard).to_string();

            // Evaluate best move from position before move
            let (eval_before, best_move_uci) = self.evaluate_position(&fen_before, 8);

            // Execute move
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

            // Centipawn delta from side-to-move's perspective
            // White wants eval to increase, Black wants eval to decrease
            let eval_diff = if is_white {
                eval_before - eval_after
            } else {
                eval_after - eval_before
            };
            let cp_loss = (eval_diff * 100.0).max(0.0);

            let is_best = best_move_uci.as_deref() == Some(&uci_move) || cp_loss < 8.0;

            // Classify move (Chess.com style):
            // Brilliant: sacrifices material (e.g. piece value dropped without equal capture) yet maintains/increases win
            let is_sacrifice = moved_role
                .map(|r| piece_value(r) > captured_role.map(piece_value).unwrap_or(0.0) + 1.5)
                .unwrap_or(false);

            let current_advantage = if is_white { eval_after } else { -eval_after };

            let (quality, comment) = if is_sacrifice && is_best && current_advantage > 0.5 {
                (
                    MoveQuality::Brilliant,
                    "An exceptional sacrifice that crushes the position!!".to_string(),
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

            // Update badge counts
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

            // CAPS Accuracy formula per move
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

            current_eval = eval_after;
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

fn piece_value(role: Role) -> f32 {
    match role {
        Role::Pawn => 1.0,
        Role::Knight => 3.0,
        Role::Bishop => 3.2,
        Role::Rook => 5.0,
        Role::Queen => 9.0,
        Role::King => 0.0,
    }
}

fn evaluate_material_and_mobility(pos: &Chess) -> f32 {
    let mut score = 0.0f32;
    for sq in shakmaty::Square::ALL {
        if let Some(piece) = pos.board().piece_at(sq) {
            let val = piece_value(piece.role);
            if piece.color == Color::White {
                score += val;
            } else {
                score -= val;
            }
        }
    }
    score
}
