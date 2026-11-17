use crate::models::{
    GameRecord, User, UserPublicProfile, DEFAULT_STARTING_ELO,
};
use rusqlite::{params, Connection, Result};
use std::sync::{Arc, Mutex};
use uuid::Uuid;

#[derive(Clone)]
pub struct Database {
    conn: Arc<Mutex<Connection>>,
}

impl Database {
    pub fn new(path: &str) -> Result<Self> {
        let conn = Connection::open(path)?;
        let db = Database {
            conn: Arc::new(Mutex::new(conn)),
        };
        db.init_schema()?;
        Ok(db)
    }

    fn init_schema(&self) -> Result<()> {
        let conn = self.conn.lock().unwrap();

        conn.execute(
            "CREATE TABLE IF NOT EXISTS users (
                id TEXT PRIMARY KEY,
                username TEXT UNIQUE NOT NULL COLLATE NOCASE,
                password_hash TEXT NOT NULL,
                salt TEXT NOT NULL,
                created_at TEXT NOT NULL,
                elo_rapid INTEGER NOT NULL DEFAULT 100,
                elo_blitz INTEGER NOT NULL DEFAULT 100,
                elo_bullet INTEGER NOT NULL DEFAULT 100,
                games_played INTEGER NOT NULL DEFAULT 0,
                wins INTEGER NOT NULL DEFAULT 0,
                losses INTEGER NOT NULL DEFAULT 0,
                draws INTEGER NOT NULL DEFAULT 0,
                brilliant_moves INTEGER NOT NULL DEFAULT 0,
                great_moves INTEGER NOT NULL DEFAULT 0
            );",
            [],
        )?;

        conn.execute(
            "CREATE TABLE IF NOT EXISTS sessions (
                token TEXT PRIMARY KEY,
                user_id TEXT NOT NULL,
                created_at TEXT NOT NULL,
                FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE
            );",
            [],
        )?;

        conn.execute(
            "CREATE TABLE IF NOT EXISTS games (
                id TEXT PRIMARY KEY,
                white_user_id TEXT NOT NULL,
                white_username TEXT NOT NULL,
                black_user_id TEXT NOT NULL,
                black_username TEXT NOT NULL,
                time_control_type TEXT NOT NULL,
                time_control_sec INTEGER NOT NULL,
                time_control_inc INTEGER NOT NULL,
                fen TEXT NOT NULL,
                pgn TEXT NOT NULL,
                moves_san_json TEXT NOT NULL,
                result TEXT NOT NULL,
                reason TEXT NOT NULL,
                white_elo_before INTEGER NOT NULL,
                white_elo_after INTEGER NOT NULL,
                black_elo_before INTEGER NOT NULL,
                black_elo_after INTEGER NOT NULL,
                accuracy_white REAL,
                accuracy_black REAL,
                created_at TEXT NOT NULL
            );",
            [],
        )?;

        conn.execute(
            "CREATE TABLE IF NOT EXISTS analysis_cache (
                game_id TEXT PRIMARY KEY,
                analysis_json TEXT NOT NULL,
                created_at TEXT NOT NULL,
                FOREIGN KEY (game_id) REFERENCES games (id) ON DELETE CASCADE
            );",
            [],
        )?;

        // Ensure default bot user exists
        let bot_exists: bool = conn
            .query_row(
                "SELECT COUNT(*) FROM users WHERE username = 'Stockfish'",
                [],
                |row| row.get::<_, i64>(0),
            )
            .map(|c| c > 0)
            .unwrap_or(false);

        if !bot_exists {
            let now = chrono::Utc::now().to_rfc3339();
            let _ = conn.execute(
                "INSERT INTO users (
                    id, username, password_hash, salt, created_at,
                    elo_rapid, elo_blitz, elo_bullet, games_played, wins, losses, draws, brilliant_moves, great_moves
                ) VALUES (
                    'bot-engine', 'Engine Bot', 'none', 'none', ?1,
                    2500, 2500, 2500, 0, 0, 0, 0, 0, 0
                )",
                params![now],
            );
        }

        Ok(())
    }

    pub fn get_or_create_user_by_name(&self, username: &str) -> Result<User> {
        let conn = self.conn.lock().unwrap();
        let mut stmt = conn.prepare(
            "SELECT id, username, created_at,
                    elo_rapid, elo_blitz, elo_bullet, games_played, wins, losses, draws, brilliant_moves, great_moves
             FROM users WHERE username = ?1 COLLATE NOCASE",
        )?;

        let mut rows = stmt.query(params![username])?;
        if let Some(row) = rows.next()? {
            return Ok(User {
                id: row.get(0)?,
                username: row.get(1)?,
                created_at: row.get(2)?,
                elo_rapid: row.get(3)?,
                elo_blitz: row.get(4)?,
                elo_bullet: row.get(5)?,
                games_played: row.get(6)?,
                wins: row.get(7)?,
                losses: row.get(8)?,
                draws: row.get(9)?,
                brilliant_moves: row.get(10)?,
                great_moves: row.get(11)?,
            });
        }
        drop(rows);
        drop(stmt);

        let id = Uuid::new_v4().to_string();
        let now = chrono::Utc::now().to_rfc3339();

        conn.execute(
            "INSERT INTO users (
                id, username, password_hash, salt, created_at,
                elo_rapid, elo_blitz, elo_bullet, games_played, wins, losses, draws, brilliant_moves, great_moves
            ) VALUES (?1, ?2, 'none', 'none', ?3, ?4, ?4, ?4, 0, 0, 0, 0, 0, 0)",
            params![id, username, now, DEFAULT_STARTING_ELO],
        )?;

        Ok(User {
            id,
            username: username.to_string(),
            created_at: now,
            elo_rapid: DEFAULT_STARTING_ELO,
            elo_blitz: DEFAULT_STARTING_ELO,
            elo_bullet: DEFAULT_STARTING_ELO,
            games_played: 0,
            wins: 0,
            losses: 0,
            draws: 0,
            brilliant_moves: 0,
            great_moves: 0,
        })
    }

    #[allow(dead_code)]
    pub fn create_user(&self, username: &str, password_hash: &str, salt: &str) -> Result<User> {
        let conn = self.conn.lock().unwrap();
        let id = Uuid::new_v4().to_string();
        let now = chrono::Utc::now().to_rfc3339();

        conn.execute(
            "INSERT INTO users (
                id, username, password_hash, salt, created_at,
                elo_rapid, elo_blitz, elo_bullet, games_played, wins, losses, draws, brilliant_moves, great_moves
            ) VALUES (?1, ?2, ?3, ?4, ?5, ?6, ?6, ?6, 0, 0, 0, 0, 0, 0)",
            params![id, username, password_hash, salt, now, DEFAULT_STARTING_ELO],
        )?;

        Ok(User {
            id,
            username: username.to_string(),
            created_at: now,
            elo_rapid: DEFAULT_STARTING_ELO,
            elo_blitz: DEFAULT_STARTING_ELO,
            elo_bullet: DEFAULT_STARTING_ELO,
            games_played: 0,
            wins: 0,
            losses: 0,
            draws: 0,
            brilliant_moves: 0,
            great_moves: 0,
        })
    }

    pub fn get_user_by_username(&self, username: &str) -> Result<Option<(User, String, String)>> {
        let conn = self.conn.lock().unwrap();
        let mut stmt = conn.prepare(
            "SELECT id, username, password_hash, salt, created_at,
                    elo_rapid, elo_blitz, elo_bullet, games_played, wins, losses, draws, brilliant_moves, great_moves
             FROM users WHERE username = ?1 COLLATE NOCASE",
        )?;

        let mut rows = stmt.query(params![username])?;
        if let Some(row) = rows.next()? {
            let id: String = row.get(0)?;
            let username: String = row.get(1)?;
            let hash: String = row.get(2)?;
            let salt: String = row.get(3)?;
            let created_at: String = row.get(4)?;
            let elo_rapid: i32 = row.get(5)?;
            let elo_blitz: i32 = row.get(6)?;
            let elo_bullet: i32 = row.get(7)?;
            let games_played: i32 = row.get(8)?;
            let wins: i32 = row.get(9)?;
            let losses: i32 = row.get(10)?;
            let draws: i32 = row.get(11)?;
            let brilliant_moves: i32 = row.get(12)?;
            let great_moves: i32 = row.get(13)?;

            let user = User {
                id,
                username,
                created_at,
                elo_rapid,
                elo_blitz,
                elo_bullet,
                games_played,
                wins,
                losses,
                draws,
                brilliant_moves,
                great_moves,
            };
            Ok(Some((user, hash, salt)))
        } else {
            Ok(None)
        }
    }

    #[allow(dead_code)]
    pub fn get_user_by_id(&self, user_id: &str) -> Result<Option<User>> {
        let conn = self.conn.lock().unwrap();
        let mut stmt = conn.prepare(
            "SELECT id, username, created_at,
                    elo_rapid, elo_blitz, elo_bullet, games_played, wins, losses, draws, brilliant_moves, great_moves
             FROM users WHERE id = ?1",
        )?;

        let mut rows = stmt.query(params![user_id])?;
        if let Some(row) = rows.next()? {
            Ok(Some(User {
                id: row.get(0)?,
                username: row.get(1)?,
                created_at: row.get(2)?,
                elo_rapid: row.get(3)?,
                elo_blitz: row.get(4)?,
                elo_bullet: row.get(5)?,
                games_played: row.get(6)?,
                wins: row.get(7)?,
                losses: row.get(8)?,
                draws: row.get(9)?,
                brilliant_moves: row.get(10)?,
                great_moves: row.get(11)?,
            }))
        } else {
            Ok(None)
        }
    }

    pub fn create_session(&self, token: &str, user_id: &str) -> Result<()> {
        let conn = self.conn.lock().unwrap();
        let now = chrono::Utc::now().to_rfc3339();
        conn.execute(
            "INSERT INTO sessions (token, user_id, created_at) VALUES (?1, ?2, ?3)",
            params![token, user_id, now],
        )?;
        Ok(())
    }

    pub fn get_user_by_session(&self, token: &str) -> Result<Option<User>> {
        let conn = self.conn.lock().unwrap();
        let mut stmt = conn.prepare(
            "SELECT u.id, u.username, u.created_at,
                    u.elo_rapid, u.elo_blitz, u.elo_bullet,
                    u.games_played, u.wins, u.losses, u.draws,
                    u.brilliant_moves, u.great_moves
             FROM sessions s
             JOIN users u ON s.user_id = u.id
             WHERE s.token = ?1",
        )?;

        let mut rows = stmt.query(params![token])?;
        if let Some(row) = rows.next()? {
            Ok(Some(User {
                id: row.get(0)?,
                username: row.get(1)?,
                created_at: row.get(2)?,
                elo_rapid: row.get(3)?,
                elo_blitz: row.get(4)?,
                elo_bullet: row.get(5)?,
                games_played: row.get(6)?,
                wins: row.get(7)?,
                losses: row.get(8)?,
                draws: row.get(9)?,
                brilliant_moves: row.get(10)?,
                great_moves: row.get(11)?,
            }))
        } else {
            Ok(None)
        }
    }

    pub fn delete_session(&self, token: &str) -> Result<()> {
        let conn = self.conn.lock().unwrap();
        conn.execute("DELETE FROM sessions WHERE token = ?1", params![token])?;
        Ok(())
    }

    pub fn update_user_rating_and_stats(
        &self,
        user_id: &str,
        time_control_type: &str,
        new_elo: i32,
        is_win: bool,
        is_loss: bool,
        is_draw: bool,
    ) -> Result<()> {
        let conn = self.conn.lock().unwrap();
        let elo_column = match time_control_type {
            "bullet" => "elo_bullet",
            "blitz" => "elo_blitz",
            _ => "elo_rapid",
        };

        let win_inc = if is_win { 1 } else { 0 };
        let loss_inc = if is_loss { 1 } else { 0 };
        let draw_inc = if is_draw { 1 } else { 0 };

        let query = format!(
            "UPDATE users SET
                {} = ?1,
                games_played = games_played + 1,
                wins = wins + ?2,
                losses = losses + ?3,
                draws = draws + ?4
             WHERE id = ?5",
            elo_column
        );

        conn.execute(
            &query,
            params![new_elo, win_inc, loss_inc, draw_inc, user_id],
        )?;

        Ok(())
    }

    pub fn increment_user_brilliants(
        &self,
        user_id: &str,
        brilliant_count: u32,
        great_count: u32,
    ) -> Result<()> {
        let conn = self.conn.lock().unwrap();
        conn.execute(
            "UPDATE users SET
                brilliant_moves = brilliant_moves + ?1,
                great_moves = great_moves + ?2
             WHERE id = ?3",
            params![brilliant_count, great_count, user_id],
        )?;
        Ok(())
    }

    pub fn save_game(&self, game: &GameRecord) -> Result<()> {
        let conn = self.conn.lock().unwrap();
        let moves_json = serde_json::to_string(&game.moves_san).unwrap_or_else(|_| "[]".into());

        conn.execute(
            "INSERT INTO games (
                id, white_user_id, white_username, black_user_id, black_username,
                time_control_type, time_control_sec, time_control_inc, fen, pgn,
                moves_san_json, result, reason,
                white_elo_before, white_elo_after, black_elo_before, black_elo_after,
                accuracy_white, accuracy_black, created_at
            ) VALUES (
                ?1, ?2, ?3, ?4, ?5, ?6, ?7, ?8, ?9, ?10, ?11, ?12, ?13, ?14, ?15, ?16, ?17, ?18, ?19, ?20
            )",
            params![
                game.id,
                game.white_user_id,
                game.white_username,
                game.black_user_id,
                game.black_username,
                game.time_control_type,
                game.time_control_sec,
                game.time_control_inc,
                game.fen,
                game.pgn,
                moves_json,
                game.result,
                game.reason,
                game.white_elo_before,
                game.white_elo_after,
                game.black_elo_before,
                game.black_elo_after,
                game.accuracy_white,
                game.accuracy_black,
                game.created_at,
            ],
        )?;

        Ok(())
    }

    pub fn get_game_by_id(&self, game_id: &str) -> Result<Option<GameRecord>> {
        let conn = self.conn.lock().unwrap();
        let mut stmt = conn.prepare(
            "SELECT id, white_user_id, white_username, black_user_id, black_username,
                    time_control_type, time_control_sec, time_control_inc, fen, pgn,
                    moves_san_json, result, reason,
                    white_elo_before, white_elo_after, black_elo_before, black_elo_after,
                    accuracy_white, accuracy_black, created_at
             FROM games WHERE id = ?1",
        )?;

        let mut rows = stmt.query(params![game_id])?;
        if let Some(row) = rows.next()? {
            let moves_json: String = row.get(10)?;
            let moves_san: Vec<String> =
                serde_json::from_str(&moves_json).unwrap_or_else(|_| vec![]);

            Ok(Some(GameRecord {
                id: row.get(0)?,
                white_user_id: row.get(1)?,
                white_username: row.get(2)?,
                black_user_id: row.get(3)?,
                black_username: row.get(4)?,
                time_control_type: row.get(5)?,
                time_control_sec: row.get(6)?,
                time_control_inc: row.get(7)?,
                fen: row.get(8)?,
                pgn: row.get(9)?,
                moves_san,
                result: row.get(11)?,
                reason: row.get(12)?,
                white_elo_before: row.get(13)?,
                white_elo_after: row.get(14)?,
                black_elo_before: row.get(15)?,
                black_elo_after: row.get(16)?,
                accuracy_white: row.get(17)?,
                accuracy_black: row.get(18)?,
                created_at: row.get(19)?,
            }))
        } else {
            Ok(None)
        }
    }

    pub fn get_user_recent_games(&self, user_id: &str, limit: i32) -> Result<Vec<GameRecord>> {
        let conn = self.conn.lock().unwrap();
        let mut stmt = conn.prepare(
            "SELECT id, white_user_id, white_username, black_user_id, black_username,
                    time_control_type, time_control_sec, time_control_inc, fen, pgn,
                    moves_san_json, result, reason,
                    white_elo_before, white_elo_after, black_elo_before, black_elo_after,
                    accuracy_white, accuracy_black, created_at
             FROM games
             WHERE white_user_id = ?1 OR black_user_id = ?1
             ORDER BY created_at DESC
             LIMIT ?2",
        )?;

        let rows = stmt.query_map(params![user_id, limit], |row| {
            let moves_json: String = row.get(10)?;
            let moves_san: Vec<String> =
                serde_json::from_str(&moves_json).unwrap_or_else(|_| vec![]);

            Ok(GameRecord {
                id: row.get(0)?,
                white_user_id: row.get(1)?,
                white_username: row.get(2)?,
                black_user_id: row.get(3)?,
                black_username: row.get(4)?,
                time_control_type: row.get(5)?,
                time_control_sec: row.get(6)?,
                time_control_inc: row.get(7)?,
                fen: row.get(8)?,
                pgn: row.get(9)?,
                moves_san,
                result: row.get(11)?,
                reason: row.get(12)?,
                white_elo_before: row.get(13)?,
                white_elo_after: row.get(14)?,
                black_elo_before: row.get(15)?,
                black_elo_after: row.get(16)?,
                accuracy_white: row.get(17)?,
                accuracy_black: row.get(18)?,
                created_at: row.get(19)?,
            })
        })?;

        let mut games = Vec::new();
        for g in rows {
            games.push(g?);
        }
        Ok(games)
    }

    pub fn get_leaderboard(&self, mode: &str, limit: i32) -> Result<Vec<UserPublicProfile>> {
        let conn = self.conn.lock().unwrap();
        let order_col = match mode {
            "blitz" => "elo_blitz",
            "bullet" => "elo_bullet",
            _ => "elo_rapid",
        };

        let query = format!(
            "SELECT id, username, created_at,
                    elo_rapid, elo_blitz, elo_bullet,
                    games_played, wins, losses, draws,
                    brilliant_moves, great_moves
             FROM users
             WHERE id != 'bot-stockfish'
             ORDER BY {} DESC
             LIMIT ?1",
            order_col
        );

        let mut stmt = conn.prepare(&query)?;
        let rows = stmt.query_map(params![limit], |row| {
            let games_played: i32 = row.get(6)?;
            let wins: i32 = row.get(7)?;
            let win_rate = if games_played > 0 {
                (wins as f32 / games_played as f32) * 100.0
            } else {
                0.0
            };

            Ok(UserPublicProfile {
                id: row.get(0)?,
                username: row.get(1)?,
                created_at: row.get(2)?,
                elo_rapid: row.get(3)?,
                elo_blitz: row.get(4)?,
                elo_bullet: row.get(5)?,
                games_played,
                wins,
                losses: row.get(8)?,
                draws: row.get(9)?,
                win_rate,
                brilliant_moves: row.get(10)?,
                great_moves: row.get(11)?,
            })
        })?;

        let mut users = Vec::new();
        for u in rows {
            users.push(u?);
        }
        Ok(users)
    }

    pub fn save_analysis(&self, game_id: &str, analysis_json: &str) -> Result<()> {
        let conn = self.conn.lock().unwrap();
        let now = chrono::Utc::now().to_rfc3339();
        conn.execute(
            "INSERT OR REPLACE INTO analysis_cache (game_id, analysis_json, created_at) VALUES (?1, ?2, ?3)",
            params![game_id, analysis_json, now],
        )?;
        Ok(())
    }

    pub fn get_analysis(&self, game_id: &str) -> Result<Option<String>> {
        let conn = self.conn.lock().unwrap();
        let mut stmt =
            conn.prepare("SELECT analysis_json FROM analysis_cache WHERE game_id = ?1")?;
        let mut rows = stmt.query(params![game_id])?;
        if let Some(row) = rows.next()? {
            Ok(Some(row.get(0)?))
        } else {
            Ok(None)
        }
    }
}
