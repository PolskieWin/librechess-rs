mod crypto_utils;
mod db;
mod game;
mod matchmaking;
mod models;
mod routes;
mod stockfish;

use db::Database;
use matchmaking::Matchmaker;
use routes::{create_router, AppState};
use stockfish::EngineManager;
use std::net::SocketAddr;
use std::sync::Arc;
use std::time::Duration;

#[tokio::main]
async fn main() -> Result<(), Box<dyn std::error::Error>> {
    let port: u16 = std::env::var("PORT")
        .ok()
        .and_then(|p| p.parse().ok())
        .unwrap_or(8080);

    let host = std::env::var("HOST").unwrap_or_else(|_| "0.0.0.0".to_string());
    let db_path = std::env::var("DATABASE_PATH").unwrap_or_else(|_| "chess.db".to_string());

    println!("============================================================");
    println!("  LIBRECHESS RS");
    println!("  Clean Fast Chess Server");
    println!("  Base Elo: 100 | Live Matchmaking | Move Analyzer");
    println!("============================================================");

    let db = Database::new(&db_path)?;
    println!("[DB] Database initialized at {}", db_path);

    let engine = Arc::new(EngineManager::new());
    if engine.has_stockfish() {
        println!("[ENGINE] Analysis engine activated.");
    } else {
        println!("[ENGINE] Fast heuristic chess engine activated.");
    }

    let matchmaker = Arc::new(Matchmaker::new(db.clone()));

    // Background task: Periodic matchmaking expander & clock flagger
    let mm_bg = Arc::clone(&matchmaker);
    tokio::spawn(async move {
        let mut interval = tokio::time::interval(Duration::from_millis(500));
        loop {
            interval.tick().await;

            // Check clock timeouts on active games
            let flagged_games: Vec<(String, String, String, crate::models::GameRecord)> = {
                let mut games = mm_bg.active_games.write().await;
                let mut list = Vec::new();
                for game in games.values_mut() {
                    if game.is_over {
                        continue;
                    }
                    if let Some(prev) = game.last_move_time {
                        let elapsed_ms = prev.elapsed().as_millis() as i64;
                        let turn = game.turn_color();
                        if turn == shakmaty::Color::White {
                            if game.white_time_ms - elapsed_ms <= 0 {
                                game.white_time_ms = 0;
                                game.is_over = true;
                                game.winner = Some("black".to_string());
                                game.end_reason = Some("timeout".to_string());
                                let rec = game.to_game_record(chrono::Utc::now().to_rfc3339());
                                list.push((game.id.clone(), "black".to_string(), "timeout".to_string(), rec));
                            }
                        } else {
                            if game.black_time_ms - elapsed_ms <= 0 {
                                game.black_time_ms = 0;
                                game.is_over = true;
                                game.winner = Some("white".to_string());
                                game.end_reason = Some("timeout".to_string());
                                let rec = game.to_game_record(chrono::Utc::now().to_rfc3339());
                                list.push((game.id.clone(), "white".to_string(), "timeout".to_string(), rec));
                            }
                        }
                    }
                }
                list
            };

            for (_, winner, reason, rec) in flagged_games {
                mm_bg.finalize_game(&rec, &winner, &reason).await;
            }
        }
    });

    let state = AppState {
        db,
        matchmaker,
        engine,
    };

    let app = create_router(state);
    let addr: SocketAddr = format!("{}:{}", host, port).parse()?;

    println!("[SERVER] Listening on http://localhost:{}", port);
    println!("[SERVER] Open in your browser: http://127.0.0.1:{}", port);

    let listener = tokio::net::TcpListener::bind(addr).await?;
    axum::serve(listener, app).await?;

    Ok(())
}
