use crate::models::{calculate_elo_change, GameRecord};
use shakmaty::fen::Fen;
use shakmaty::san::San;
use shakmaty::uci::UciMove;
use shakmaty::{Chess, Color, Position};
use std::str::FromStr;
use std::time::Instant;

#[derive(Debug, Clone)]
#[allow(dead_code)]
pub struct GameRoom {
    pub id: String,
    pub white_id: String,
    pub white_username: String,
    pub black_id: String,
    pub black_username: String,
    pub white_elo: i32,
    pub black_elo: i32,
    pub time_control_type: String,
    pub white_time_ms: i64,
    pub black_time_ms: i64,
    pub increment_ms: i64,
    pub last_move_time: Option<Instant>,
    pub chess: Chess,
    pub moves_san: Vec<String>,
    pub moves_uci: Vec<String>,
    pub draw_offered_by: Option<Color>,
    pub is_over: bool,
    pub winner: Option<String>, // "white", "black", "draw"
    pub end_reason: Option<String>,
    pub is_bot_game: bool,
    pub bot_level: u32,
    pub white_connected: bool,
    pub black_connected: bool,
}

impl GameRoom {
    pub fn new(
        id: String,
        white_id: String,
        white_username: String,
        white_elo: i32,
        black_id: String,
        black_username: String,
        black_elo: i32,
        time_control_type: String,
        time_sec: i64,
        inc_sec: i64,
        is_bot_game: bool,
        bot_level: u32,
    ) -> Self {
        GameRoom {
            id,
            white_id,
            white_username,
            black_id,
            black_username,
            white_elo,
            black_elo,
            time_control_type,
            white_time_ms: time_sec * 1000,
            black_time_ms: time_sec * 1000,
            increment_ms: inc_sec * 1000,
            last_move_time: None,
            chess: Chess::default(),
            moves_san: Vec::new(),
            moves_uci: Vec::new(),
            draw_offered_by: None,
            is_over: false,
            winner: None,
            end_reason: None,
            is_bot_game,
            bot_level,
            white_connected: true,
            black_connected: true,
        }
    }

    pub fn turn_color(&self) -> Color {
        self.chess.turn()
    }

    pub fn is_player_turn(&self, user_id: &str) -> bool {
        let is_white = self.turn_color() == Color::White;
        if is_white {
            self.white_id == user_id
        } else {
            self.black_id == user_id
        }
    }

    pub fn current_fen(&self) -> String {
        Fen::from_position(self.chess.clone(), shakmaty::EnPassantMode::Legal).to_string()
    }

    /// Update clocks on move
    #[allow(dead_code)]
    pub fn update_clock_on_move(&mut self) -> Result<(), String> {
        let now = Instant::now();
        if let Some(prev) = self.last_move_time {
            let _elapsed_ms = prev.elapsed().as_millis() as i64;
            // The side that just moved was the opposite of current turn after move
            // But this function should be called right BEFORE turn switches or right AFTER
            // We pass color or calculate appropriately
        }
        self.last_move_time = Some(now);
        Ok(())
    }

    /// Execute move in UCI format (e.g. "e2e4", "e7e8q")
    pub fn make_uci_move(&mut self, uci_str: &str, user_id: &str) -> Result<String, String> {
        if self.is_over {
            return Err("Game is already over".to_string());
        }

        // Verify it is this player's turn
        if !self.is_player_turn(user_id) && user_id != "bot" {
            return Err("Not your turn".to_string());
        }

        let moving_color = self.turn_color();

        // Parse UCI move
        let uci = UciMove::from_str(uci_str).map_err(|e| format!("Invalid UCI move format: {:?}", e))?;
        let legal_move = uci
            .to_move(&self.chess)
            .map_err(|e| format!("Illegal move: {:?}", e))?;

        // Calculate time consumed by moving player
        let now = Instant::now();
        if let Some(prev) = self.last_move_time {
            let elapsed_ms = prev.elapsed().as_millis() as i64;
            if moving_color == Color::White {
                self.white_time_ms -= elapsed_ms;
                self.white_time_ms += self.increment_ms;
                if self.white_time_ms <= 0 {
                    self.white_time_ms = 0;
                    self.is_over = true;
                    self.winner = Some("black".to_string());
                    self.end_reason = Some("timeout".to_string());
                    return Err("White flagged (out of time)".to_string());
                }
            } else {
                self.black_time_ms -= elapsed_ms;
                self.black_time_ms += self.increment_ms;
                if self.black_time_ms <= 0 {
                    self.black_time_ms = 0;
                    self.is_over = true;
                    self.winner = Some("white".to_string());
                    self.end_reason = Some("timeout".to_string());
                    return Err("Black flagged (out of time)".to_string());
                }
            }
        }
        self.last_move_time = Some(now);

        // Compute SAN notation before executing move
        let san = San::from_move(&self.chess, &legal_move).to_string();

        // Play move
        self.chess.play_unchecked(&legal_move);
        self.moves_uci.push(uci_str.to_string());
        self.moves_san.push(san.clone());

        // Reset draw offer
        self.draw_offered_by = None;

        // Check game end conditions
        if self.chess.is_checkmate() {
            self.is_over = true;
            self.winner = Some(if moving_color == Color::White {
                "white".to_string()
            } else {
                "black".to_string()
            });
            self.end_reason = Some("checkmate".to_string());
        } else if self.chess.is_stalemate() {
            self.is_over = true;
            self.winner = Some("draw".to_string());
            self.end_reason = Some("stalemate".to_string());
        } else if self.chess.is_insufficient_material() {
            self.is_over = true;
            self.winner = Some("draw".to_string());
            self.end_reason = Some("insufficient_material".to_string());
        }

        Ok(san)
    }

    pub fn resign(&mut self, user_id: &str) -> Result<(), String> {
        if self.is_over {
            return Err("Game already over".to_string());
        }
        if user_id == self.white_id {
            self.is_over = true;
            self.winner = Some("black".to_string());
            self.end_reason = Some("resignation".to_string());
            Ok(())
        } else if user_id == self.black_id {
            self.is_over = true;
            self.winner = Some("white".to_string());
            self.end_reason = Some("resignation".to_string());
            Ok(())
        } else {
            Err("Player not in this game".to_string())
        }
    }

    pub fn offer_draw(&mut self, user_id: &str) -> Result<String, String> {
        if self.is_over {
            return Err("Game already over".to_string());
        }

        let player_color = if user_id == self.white_id {
            Color::White
        } else if user_id == self.black_id {
            Color::Black
        } else {
            return Err("Player not in this game".to_string());
        };

        if let Some(offered) = self.draw_offered_by {
            if offered != player_color {
                // Other player accepts the existing offer!
                self.is_over = true;
                self.winner = Some("draw".to_string());
                self.end_reason = Some("agreement".to_string());
                return Ok("accepted".to_string());
            }
        }

        self.draw_offered_by = Some(player_color);
        Ok("offered".to_string())
    }

    pub fn to_game_record(&self, created_at: String) -> GameRecord {
        let (white_score, result_str) = match self.winner.as_deref() {
            Some("white") => (1.0, "1-0"),
            Some("black") => (0.0, "0-1"),
            _ => (0.5, "1/2-1/2"),
        };

        let (new_white_elo, new_black_elo) =
            calculate_elo_change(self.white_elo, self.black_elo, white_score);

        // Simple PGN export
        let mut pgn = format!(
            "[Event \"LibreChess Live\"]\n[Site \"LibreChess Server\"]\n[White \"{}\"]\n[Black \"{}\"]\n[Result \"{}\"]\n\n",
            self.white_username, self.black_username, result_str
        );

        for (i, san) in self.moves_san.iter().enumerate() {
            if i % 2 == 0 {
                pgn.push_str(&format!("{}. {} ", (i / 2) + 1, san));
            } else {
                pgn.push_str(&format!("{} ", san));
            }
        }
        pgn.push_str(result_str);

        GameRecord {
            id: self.id.clone(),
            white_user_id: self.white_id.clone(),
            white_username: self.white_username.clone(),
            black_user_id: self.black_id.clone(),
            black_username: self.black_username.clone(),
            time_control_type: self.time_control_type.clone(),
            time_control_sec: (self.white_time_ms / 1000) as i32,
            time_control_inc: (self.increment_ms / 1000) as i32,
            fen: self.current_fen(),
            pgn,
            moves_san: self.moves_san.clone(),
            result: result_str.to_string(),
            reason: self.end_reason.clone().unwrap_or_else(|| "normal".to_string()),
            white_elo_before: self.white_elo,
            white_elo_after: new_white_elo,
            black_elo_before: self.black_elo,
            black_elo_after: new_black_elo,
            accuracy_white: None,
            accuracy_black: None,
            created_at,
        }
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn test_kingside_castling() {
        let mut game = GameRoom::new(
            "test".into(),
            "w".into(), "White".into(), 100,
            "b".into(), "Black".into(), 100,
            "rapid".into(), 600, 0, false, 0
        );

        let moves = ["e2e4", "e7e5", "g1f3", "b8c6", "f1c4", "g8f6", "e1g1"];
        for m in moves {
            let res = game.make_uci_move(m, if game.turn_color() == Color::White { "w" } else { "b" });
            assert!(res.is_ok(), "Move {} failed: {:?}", m, res);
        }

        assert_eq!(game.moves_san.last().unwrap(), "O-O");
        // Verify FEN shows castled position
        let fen = game.current_fen();
        assert!(fen.contains("4rk1") || fen.contains("5rk1") || fen.contains("RNBQ1RK1"));
    }

    #[test]
    fn test_queenside_castling() {
        let mut game = GameRoom::new(
            "test".into(),
            "w".into(), "White".into(), 100,
            "b".into(), "Black".into(), 100,
            "rapid".into(), 600, 0, false, 0
        );

        let moves = ["d2d4", "d7d5", "b1c3", "b8c6", "c1f4", "g8f6", "d1d2", "e7e6", "e1c1"];
        for m in moves {
            let res = game.make_uci_move(m, if game.turn_color() == Color::White { "w" } else { "b" });
            assert!(res.is_ok(), "Move {} failed: {:?}", m, res);
        }

        assert_eq!(game.moves_san.last().unwrap(), "O-O-O");
    }

    #[test]
    fn test_en_passant() {
        let mut game = GameRoom::new(
            "test".into(),
            "w".into(), "White".into(), 100,
            "b".into(), "Black".into(), 100,
            "rapid".into(), 600, 0, false, 0
        );

        let moves = ["e2e4", "g8f6", "e4e5", "d7d5", "e5d6"];
        for m in moves {
            let res = game.make_uci_move(m, if game.turn_color() == Color::White { "w" } else { "b" });
            assert!(res.is_ok(), "Move {} failed: {:?}", m, res);
        }

        assert_eq!(game.moves_san.last().unwrap(), "exd6");
        // Verify pawn on d5 was removed
        let fen = game.current_fen();
        assert!(!fen.contains("3p4"));
    }
}
