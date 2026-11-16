use crate::db::Database;
use crate::game::GameRoom;
use crate::models::GameRecord;
use crate::stockfish::EngineManager;
use serde::{Deserialize, Serialize};
use shakmaty::Position;
use std::collections::HashMap;
use std::sync::Arc;
use std::time::Instant;
use tokio::sync::{mpsc, RwLock};
use uuid::Uuid;

#[derive(Debug, Clone, Serialize, Deserialize)]
#[serde(tag = "type", content = "payload")]
pub enum ClientMessage {
    #[serde(rename = "join_queue")]
    JoinQueue {
        time_control: String, // "bullet", "blitz", "rapid", "custom"
        time_sec: i64,
        inc_sec: i64,
    },
    #[serde(rename = "leave_queue")]
    LeaveQueue,
    #[serde(rename = "create_bot_game")]
    CreateBotGame {
        bot_level: u32,
        preferred_color: String, // "white", "black", "random"
        time_sec: i64,
        inc_sec: i64,
    },
    #[serde(rename = "move")]
    MakeMove {
        game_id: String,
        uci: String,
    },
    #[serde(rename = "resign")]
    Resign {
        game_id: String,
    },
    #[serde(rename = "offer_draw")]
    OfferDraw {
        game_id: String,
    },
    #[serde(rename = "chat")]
    Chat {
        game_id: String,
        text: String,
    },
    #[serde(rename = "request_analysis")]
    RequestAnalysis {
        game_id: String,
    },
    #[serde(rename = "ping")]
    Ping,
}

#[derive(Debug, Clone, Serialize, Deserialize)]
#[serde(tag = "type", content = "payload")]
pub enum ServerMessage {
    #[serde(rename = "queued")]
    Queued {
        time_control: String,
    },
    #[serde(rename = "queue_left")]
    QueueLeft,
    #[serde(rename = "game_start")]
    GameStart {
        game_id: String,
        white_id: String,
        white_username: String,
        white_elo: i32,
        black_id: String,
        black_username: String,
        black_elo: i32,
        your_color: String,
        time_control: String,
        white_time_ms: i64,
        black_time_ms: i64,
        inc_ms: i64,
        fen: String,
    },
    #[serde(rename = "move_made")]
    MoveMade {
        game_id: String,
        uci: String,
        san: String,
        fen: String,
        turn: String,
        white_time_ms: i64,
        black_time_ms: i64,
        is_check: bool,
    },
    #[serde(rename = "game_over")]
    GameOver {
        game_id: String,
        winner: String,
        reason: String,
        white_elo_before: i32,
        white_elo_after: i32,
        black_elo_before: i32,
        black_elo_after: i32,
    },
    #[serde(rename = "draw_offered")]
    DrawOffered {
        game_id: String,
        by_color: String,
    },
    #[serde(rename = "chat")]
    ChatReceived {
        game_id: String,
        sender: String,
        text: String,
    },
    #[serde(rename = "analysis_result")]
    AnalysisResult {
        game_id: String,
        analysis_json: String,
    },
    #[serde(rename = "pong")]
    Pong,
    #[serde(rename = "error")]
    Error {
        message: String,
    },
}

#[derive(Clone)]
pub struct QueuedPlayer {
    pub user_id: String,
    pub username: String,
    pub elo: i32,
    pub time_control: String,
    pub time_sec: i64,
    pub inc_sec: i64,
    pub joined_at: Instant,
    pub tx: mpsc::UnboundedSender<ServerMessage>,
}

#[derive(Clone)]
pub struct Matchmaker {
    pub queues: Arc<RwLock<HashMap<String, Vec<QueuedPlayer>>>>,
    pub active_games: Arc<RwLock<HashMap<String, GameRoom>>>,
    pub player_connections: Arc<RwLock<HashMap<String, mpsc::UnboundedSender<ServerMessage>>>>,
    pub db: Database,
    pub engine: Arc<EngineManager>,
}

impl Matchmaker {
    pub fn new(db: Database) -> Self {
        Matchmaker {
            queues: Arc::new(RwLock::new(HashMap::new())),
            active_games: Arc::new(RwLock::new(HashMap::new())),
            player_connections: Arc::new(RwLock::new(HashMap::new())),
            db,
            engine: Arc::new(EngineManager::new()),
        }
    }

    pub async fn register_connection(
        &self,
        user_id: &str,
        tx: mpsc::UnboundedSender<ServerMessage>,
    ) {
        let mut conns = self.player_connections.write().await;
        conns.insert(user_id.to_string(), tx);
    }

    pub async fn remove_connection(&self, user_id: &str) {
        let mut conns = self.player_connections.write().await;
        conns.remove(user_id);
        self.leave_queue(user_id).await;
    }

    pub async fn send_to_user(&self, user_id: &str, msg: ServerMessage) {
        let conns = self.player_connections.read().await;
        if let Some(tx) = conns.get(user_id) {
            let _ = tx.send(msg);
        }
    }

    pub async fn join_queue(
        &self,
        user_id: String,
        username: String,
        elo: i32,
        time_control: String,
        time_sec: i64,
        inc_sec: i64,
        tx: mpsc::UnboundedSender<ServerMessage>,
    ) {
        let mut queues = self.queues.write().await;
        let queue = queues.entry(time_control.clone()).or_insert_with(Vec::new);

        // Remove if already in queue
        queue.retain(|p| p.user_id != user_id);

        // Try to match with an existing player in queue
        if let Some(opponent_idx) = queue.iter().position(|p| p.user_id != user_id) {
            let opponent = queue.remove(opponent_idx);
            drop(queues); // Release lock before game creation

            // Randomize White / Black
            let (white, black) = if rand::random::<bool>() {
                (
                    (user_id, username, elo, tx),
                    (opponent.user_id, opponent.username, opponent.elo, opponent.tx),
                )
            } else {
                (
                    (opponent.user_id, opponent.username, opponent.elo, opponent.tx),
                    (user_id, username, elo, tx),
                )
            };

            let game_id = Uuid::new_v4().to_string();
            let game = GameRoom::new(
                game_id.clone(),
                white.0.clone(),
                white.1.clone(),
                white.2,
                black.0.clone(),
                black.1.clone(),
                black.2,
                time_control.clone(),
                time_sec,
                inc_sec,
                false,
                0,
            );

            let fen = game.current_fen();

            // Save active game
            {
                let mut games = self.active_games.write().await;
                games.insert(game_id.clone(), game);
            }

            // Send game start to White
            let _ = white.3.send(ServerMessage::GameStart {
                game_id: game_id.clone(),
                white_id: white.0.clone(),
                white_username: white.1.clone(),
                white_elo: white.2,
                black_id: black.0.clone(),
                black_username: black.1.clone(),
                black_elo: black.2,
                your_color: "white".to_string(),
                time_control: time_control.clone(),
                white_time_ms: time_sec * 1000,
                black_time_ms: time_sec * 1000,
                inc_ms: inc_sec * 1000,
                fen: fen.clone(),
            });

            // Send game start to Black
            let _ = black.3.send(ServerMessage::GameStart {
                game_id,
                white_id: white.0,
                white_username: white.1,
                white_elo: white.2,
                black_id: black.0,
                black_username: black.1,
                black_elo: black.2,
                your_color: "black".to_string(),
                time_control,
                white_time_ms: time_sec * 1000,
                black_time_ms: time_sec * 1000,
                inc_ms: inc_sec * 1000,
                fen,
            });
        } else {
            let _ = tx.send(ServerMessage::Queued {
                time_control: time_control.clone(),
            });

            queue.push(QueuedPlayer {
                user_id,
                username,
                elo,
                time_control,
                time_sec,
                inc_sec,
                joined_at: Instant::now(),
                tx,
            });
        }
    }

    pub async fn leave_queue(&self, user_id: &str) {
        let mut queues = self.queues.write().await;
        for queue in queues.values_mut() {
            queue.retain(|p| p.user_id != user_id);
        }
    }

    pub async fn create_bot_game(
        &self,
        user_id: &str,
        username: &str,
        user_elo: i32,
        bot_level: u32,
        preferred_color: &str,
        time_sec: i64,
        inc_sec: i64,
        tx: &mpsc::UnboundedSender<ServerMessage>,
    ) {
        let bot_elo = match bot_level {
            1 => 100,
            2 => 500,
            3 => 1000,
            4 => 1500,
            _ => 2500,
        };

        let user_is_white = match preferred_color {
            "white" => true,
            "black" => false,
            _ => rand::random::<bool>(),
        };

        let bot_name = match bot_level {
            1 => "Toby Bot (100)",
            2 => "Martin Bot (500)",
            3 => "Elena Bot (1000)",
            4 => "Magnus Bot (1500)",
            _ => "Stockfish GM (2500)",
        };

        let game_id = Uuid::new_v4().to_string();

        let (white_id, white_username, white_elo, black_id, black_username, black_elo) =
            if user_is_white {
                (
                    user_id.to_string(),
                    username.to_string(),
                    user_elo,
                    "bot".to_string(),
                    bot_name.to_string(),
                    bot_elo,
                )
            } else {
                (
                    "bot".to_string(),
                    bot_name.to_string(),
                    bot_elo,
                    user_id.to_string(),
                    username.to_string(),
                    user_elo,
                )
            };

        let game = GameRoom::new(
            game_id.clone(),
            white_id.clone(),
            white_username.clone(),
            white_elo,
            black_id.clone(),
            black_username.clone(),
            black_elo,
            "bot".to_string(),
            time_sec,
            inc_sec,
            true,
            bot_level,
        );

        let fen = game.current_fen();

        // Save active game
        {
            let mut games = self.active_games.write().await;
            games.insert(game_id.clone(), game);
        }

        let your_color = if user_is_white { "white" } else { "black" };

        let _ = tx.send(ServerMessage::GameStart {
            game_id: game_id.clone(),
            white_id,
            white_username,
            white_elo,
            black_id,
            black_username,
            black_elo,
            your_color: your_color.to_string(),
            time_control: "bot".to_string(),
            white_time_ms: time_sec * 1000,
            black_time_ms: time_sec * 1000,
            inc_ms: inc_sec * 1000,
            fen,
        });

        // If bot is White, schedule bot's first move!
        if !user_is_white {
            let self_clone = self.clone();
            let gid = game_id.clone();
            tokio::spawn(async move {
                tokio::time::sleep(tokio::time::Duration::from_millis(500)).await;
                self_clone.make_bot_move(&gid).await;
            });
        }
    }

    pub async fn handle_move(&self, user_id: &str, game_id: &str, uci: &str) {
        let (is_bot_game, is_over) = match self.apply_move_and_broadcast(user_id, game_id, uci).await {
            Some(res) => res,
            None => return,
        };

        // If playing bot and game not over, schedule bot's move
        if is_bot_game && !is_over && user_id != "bot" {
            let self_clone = self.clone();
            let gid = game_id.to_string();
            tokio::spawn(async move {
                tokio::time::sleep(tokio::time::Duration::from_millis(600)).await;
                self_clone.make_bot_move(&gid).await;
            });
        }
    }

    pub async fn make_bot_move(&self, game_id: &str) {
        let (fen, bot_level, is_over) = {
            let games = self.active_games.read().await;
            match games.get(game_id) {
                Some(g) => (g.current_fen(), g.bot_level, g.is_over),
                None => return,
            }
        };

        if is_over {
            return;
        }

        let bot_uci = match self.engine.get_bot_move(&fen, bot_level) {
            Ok(m) => m,
            Err(e) => {
                eprintln!("Bot move error: {}", e);
                return;
            }
        };

        // Apply bot move directly without triggering another bot move
        let _ = self.apply_move_and_broadcast("bot", game_id, &bot_uci).await;
    }

    async fn apply_move_and_broadcast(
        &self,
        user_id: &str,
        game_id: &str,
        uci: &str,
    ) -> Option<(bool, bool)> {
        let (white_id, black_id, is_bot_game, result_san, game_over_info, is_over) = {
            let mut games = self.active_games.write().await;
            let game = match games.get_mut(game_id) {
                Some(g) => g,
                None => {
                    self.send_to_user(
                        user_id,
                        ServerMessage::Error {
                            message: "Game not found".to_string(),
                        },
                    )
                    .await;
                    return None;
                }
            };

            let san = match game.make_uci_move(uci, user_id) {
                Ok(s) => s,
                Err(err) => {
                    self.send_to_user(
                        user_id,
                        ServerMessage::Error {
                            message: err,
                        },
                    )
                    .await;
                    return None;
                }
            };

            let game_over = if game.is_over {
                let rec = game.to_game_record(chrono::Utc::now().to_rfc3339());
                Some((
                    game.winner.clone().unwrap_or_else(|| "draw".to_string()),
                    game.end_reason.clone().unwrap_or_else(|| "normal".to_string()),
                    rec,
                ))
            } else {
                None
            };

            (
                game.white_id.clone(),
                game.black_id.clone(),
                game.is_bot_game,
                (san, game.current_fen(), game.turn_color().to_string(), game.white_time_ms, game.black_time_ms, game.chess.is_check()),
                game_over,
                game.is_over,
            )
        };

        // Broadcast move to players
        let move_msg = ServerMessage::MoveMade {
            game_id: game_id.to_string(),
            uci: uci.to_string(),
            san: result_san.0,
            fen: result_san.1,
            turn: result_san.2,
            white_time_ms: result_san.3,
            black_time_ms: result_san.4,
            is_check: result_san.5,
        };

        self.send_to_user(&white_id, move_msg.clone()).await;
        if !is_bot_game {
            self.send_to_user(&black_id, move_msg).await;
        }

        // Handle game over if finished
        if let Some((winner, reason, rec)) = game_over_info {
            self.finalize_game(&rec, &winner, &reason).await;
        }

        Some((is_bot_game, is_over))
    }

    pub async fn handle_resign(&self, user_id: &str, game_id: &str) {
        let game_over_info = {
            let mut games = self.active_games.write().await;
            if let Some(game) = games.get_mut(game_id) {
                if game.resign(user_id).is_ok() {
                    let rec = game.to_game_record(chrono::Utc::now().to_rfc3339());
                    Some((
                        game.winner.clone().unwrap_or_else(|| "draw".to_string()),
                        game.end_reason.clone().unwrap_or_else(|| "resignation".to_string()),
                        rec,
                    ))
                } else {
                    None
                }
            } else {
                None
            }
        };

        if let Some((winner, reason, rec)) = game_over_info {
            self.finalize_game(&rec, &winner, &reason).await;
        }
    }

    pub async fn handle_draw_offer(&self, user_id: &str, game_id: &str) {
        let (action, white_id, black_id, game_over_info) = {
            let mut games = self.active_games.write().await;
            if let Some(game) = games.get_mut(game_id) {
                let white_id = game.white_id.clone();
                let black_id = game.black_id.clone();

                match game.offer_draw(user_id) {
                    Ok(action) if action == "accepted" => {
                        let rec = game.to_game_record(chrono::Utc::now().to_rfc3339());
                        (
                            "accepted",
                            white_id,
                            black_id,
                            Some(("draw".to_string(), "agreement".to_string(), rec)),
                        )
                    }
                    Ok(_) => ("offered", white_id, black_id, None),
                    Err(_) => ("error", white_id, black_id, None),
                }
            } else {
                ("error", String::new(), String::new(), None)
            }
        };

        if action == "offered" {
            let by_color = if user_id == white_id { "white" } else { "black" };
            let msg = ServerMessage::DrawOffered {
                game_id: game_id.to_string(),
                by_color: by_color.to_string(),
            };
            self.send_to_user(&white_id, msg.clone()).await;
            self.send_to_user(&black_id, msg).await;
        } else if let Some((winner, reason, rec)) = game_over_info {
            self.finalize_game(&rec, &winner, &reason).await;
        }
    }

    pub async fn finalize_game(&self, rec: &GameRecord, winner: &str, reason: &str) {
        // Save to database
        let _ = self.db.save_game(rec);

        // Update ratings and stats for both players if not a guest or bot
        if rec.white_user_id != "bot" && !rec.white_user_id.starts_with("guest-") {
            let is_win = winner == "white";
            let is_loss = winner == "black";
            let is_draw = winner == "draw";
            let _ = self.db.update_user_rating_and_stats(
                &rec.white_user_id,
                &rec.time_control_type,
                rec.white_elo_after,
                is_win,
                is_loss,
                is_draw,
            );
        }

        if rec.black_user_id != "bot" && !rec.black_user_id.starts_with("guest-") {
            let is_win = winner == "black";
            let is_loss = winner == "white";
            let is_draw = winner == "draw";
            let _ = self.db.update_user_rating_and_stats(
                &rec.black_user_id,
                &rec.time_control_type,
                rec.black_elo_after,
                is_win,
                is_loss,
                is_draw,
            );
        }

        // Notify players
        let over_msg = ServerMessage::GameOver {
            game_id: rec.id.clone(),
            winner: winner.to_string(),
            reason: reason.to_string(),
            white_elo_before: rec.white_elo_before,
            white_elo_after: rec.white_elo_after,
            black_elo_before: rec.black_elo_before,
            black_elo_after: rec.black_elo_after,
        };

        self.send_to_user(&rec.white_user_id, over_msg.clone()).await;
        self.send_to_user(&rec.black_user_id, over_msg).await;

        // Automatically trigger background analysis for Game Review!
        let db_clone = self.db.clone();
        let engine = EngineManager::new();
        let gid = rec.id.clone();
        let moves = rec.moves_san.clone();
        let white_uid = rec.white_user_id.clone();
        let black_uid = rec.black_user_id.clone();

        tokio::spawn(async move {
            let analysis = engine.analyze_game(&gid, &moves);
            if let Ok(json) = serde_json::to_string(&analysis) {
                let _ = db_clone.save_analysis(&gid, &json);
                // Update brilliant and great moves counts on user profiles!
                if white_uid != "bot" && !white_uid.starts_with("guest-") {
                    let _ = db_clone.increment_user_brilliants(
                        &white_uid,
                        analysis.white_counts.brilliant,
                        analysis.white_counts.great,
                    );
                }
                if black_uid != "bot" && !black_uid.starts_with("guest-") {
                    let _ = db_clone.increment_user_brilliants(
                        &black_uid,
                        analysis.black_counts.brilliant,
                        analysis.black_counts.great,
                    );
                }
            }
        });
    }
}
