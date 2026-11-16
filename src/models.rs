use serde::{Deserialize, Serialize};

pub const DEFAULT_STARTING_ELO: i32 = 100;

#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct User {
    pub id: String,
    pub username: String,
    pub created_at: String,
    pub elo_rapid: i32,
    pub elo_blitz: i32,
    pub elo_bullet: i32,
    pub games_played: i32,
    pub wins: i32,
    pub losses: i32,
    pub draws: i32,
    pub brilliant_moves: i32,
    pub great_moves: i32,
}

#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct UserPublicProfile {
    pub id: String,
    pub username: String,
    pub created_at: String,
    pub elo_rapid: i32,
    pub elo_blitz: i32,
    pub elo_bullet: i32,
    pub games_played: i32,
    pub wins: i32,
    pub losses: i32,
    pub draws: i32,
    pub win_rate: f32,
    pub brilliant_moves: i32,
    pub great_moves: i32,
}

#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct GameRecord {
    pub id: String,
    pub white_user_id: String,
    pub white_username: String,
    pub black_user_id: String,
    pub black_username: String,
    pub time_control_type: String,
    pub time_control_sec: i32,
    pub time_control_inc: i32,
    pub fen: String,
    pub pgn: String,
    pub moves_san: Vec<String>,
    pub result: String, // "1-0", "0-1", "1/2-1/2", "*"
    pub reason: String, // "checkmate", "resignation", "timeout", "stalemate", "agreement", "repetition"
    pub white_elo_before: i32,
    pub white_elo_after: i32,
    pub black_elo_before: i32,
    pub black_elo_after: i32,
    pub accuracy_white: Option<f32>,
    pub accuracy_black: Option<f32>,
    pub created_at: String,
}

#[derive(Debug, Clone, Serialize, Deserialize, PartialEq)]
#[serde(rename_all = "snake_case")]
pub enum MoveQuality {
    Brilliant,
    Great,
    Best,
    Excellent,
    Good,
    Book,
    Inaccuracy,
    Mistake,
    Blunder,
    Miss,
}

#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct MoveAnalysis {
    pub ply: usize,
    pub move_number: usize,
    pub color: String, // "white" or "black"
    pub uci: String,
    pub san: String,
    pub fen_before: String,
    pub fen_after: String,
    pub eval_before: f32, // In pawns, positive = white advantage
    pub eval_after: f32,
    pub best_move_uci: Option<String>,
    pub best_move_san: Option<String>,
    pub quality: MoveQuality,
    pub centipawn_loss: f32,
    pub comment: String,
}

#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct GameAnalysis {
    pub game_id: String,
    pub accuracy_white: f32,
    pub accuracy_black: f32,
    pub white_counts: QualityCounts,
    pub black_counts: QualityCounts,
    pub moves: Vec<MoveAnalysis>,
    pub eval_history: Vec<f32>,
    pub coach_summary: String,
}

#[derive(Debug, Clone, Default, Serialize, Deserialize)]
pub struct QualityCounts {
    pub brilliant: u32,
    pub great: u32,
    pub best: u32,
    pub excellent: u32,
    pub good: u32,
    pub book: u32,
    pub inaccuracy: u32,
    pub mistake: u32,
    pub blunder: u32,
    pub miss: u32,
}

/// Calculate new Elo ratings for two players given score (1.0 = white win, 0.5 = draw, 0.0 = black win)
pub fn calculate_elo_change(white_elo: i32, black_elo: i32, white_score: f64) -> (i32, i32) {
    let k = 32.0;
    let expected_white = 1.0 / (1.0 + 10.0_f64.powf((black_elo as f64 - white_elo as f64) / 400.0));
    let expected_black = 1.0 - expected_white;

    let black_score = 1.0 - white_score;

    let delta_white = (k * (white_score - expected_white)).round() as i32;
    let delta_black = (k * (black_score - expected_black)).round() as i32;

    let mut new_white = white_elo + delta_white;
    let mut new_black = black_elo + delta_black;

    // Hard floor at 100 Elo as required
    if new_white < 100 {
        new_white = 100;
    }
    if new_black < 100 {
        new_black = 100;
    }

    (new_white, new_black)
}
