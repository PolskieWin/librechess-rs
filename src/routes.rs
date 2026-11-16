use crate::crypto_utils::{generate_secure_token, hash_password, verify_password};
use crate::db::Database;
use crate::matchmaking::{ClientMessage, Matchmaker, ServerMessage};
use crate::models::{User, DEFAULT_STARTING_ELO};
use crate::stockfish::EngineManager;
use axum::{
    extract::{
        ws::{Message, WebSocket, WebSocketUpgrade},
        Path, Query, State,
    },
    http::{HeaderMap, StatusCode},
    response::Response,
    routing::{get, post},
    Json, Router,
};
use futures_util::{SinkExt, StreamExt};
use serde::{Deserialize, Serialize};
use std::sync::Arc;
use tokio::sync::mpsc;
use tower_http::cors::{Any, CorsLayer};
use tower_http::services::ServeDir;

#[derive(Clone)]
pub struct AppState {
    pub db: Database,
    pub matchmaker: Arc<Matchmaker>,
    pub engine: Arc<EngineManager>,
}

#[derive(Deserialize)]
pub struct AuthPayload {
    pub username: String,
    pub password: Option<String>,
}

#[derive(Serialize)]
pub struct AuthResponse {
    pub token: String,
    pub user: User,
}

#[derive(Deserialize)]
pub struct LeaderboardQuery {
    pub mode: Option<String>,
    pub limit: Option<i32>,
}

pub fn create_router(state: AppState) -> Router {
    let cors = CorsLayer::new()
        .allow_origin(Any)
        .allow_methods(Any)
        .allow_headers(Any);

    Router::new()
        .route("/api/auth/login", post(login_handler))
        .route("/api/auth/register", post(login_handler))
        .route("/api/auth/me", get(me_handler))
        .route("/api/auth/logout", post(logout_handler))
        .route("/api/profile/:username", get(profile_handler))
        .route("/api/games/recent", get(recent_games_handler))
        .route("/api/games/:id", get(game_details_handler))
        .route("/api/games/:id/analysis", get(game_analysis_handler))
        .route("/api/games/:id/analyze", post(trigger_analysis_handler))
        .route("/api/leaderboard", get(leaderboard_handler))
        .route("/ws", get(ws_upgrade_handler))
        .nest_service("/", ServeDir::new("public"))
        .layer(cors)
        .with_state(state)
}

fn extract_token(headers: &HeaderMap) -> Option<String> {
    headers
        .get("Authorization")
        .and_then(|v| v.to_str().ok())
        .and_then(|auth| {
            if auth.starts_with("Bearer ") {
                Some(auth[7..].to_string())
            } else {
                None
            }
        })
}

async fn login_handler(
    State(state): State<AppState>,
    Json(payload): Json<AuthPayload>,
) -> Result<Json<AuthResponse>, (StatusCode, String)> {
    let username = payload.username.trim();
    if username.is_empty() {
        return Err((
            StatusCode::BAD_REQUEST,
            "Username cannot be empty".into(),
        ));
    }
    if username.len() > 25 {
        return Err((
            StatusCode::BAD_REQUEST,
            "Username cannot exceed 25 characters".into(),
        ));
    }

    let user = state
        .db
        .get_or_create_user_by_name(username)
        .map_err(|e| (StatusCode::INTERNAL_SERVER_ERROR, e.to_string()))?;

    let token = generate_secure_token();
    let _ = state.db.create_session(&token, &user.id);

    Ok(Json(AuthResponse { token, user }))
}

async fn me_handler(
    State(state): State<AppState>,
    headers: HeaderMap,
) -> Result<Json<User>, (StatusCode, String)> {
    let token = extract_token(&headers).ok_or((StatusCode::UNAUTHORIZED, "Missing token".into()))?;
    let user = state
        .db
        .get_user_by_session(&token)
        .map_err(|e| (StatusCode::INTERNAL_SERVER_ERROR, e.to_string()))?
        .ok_or((StatusCode::UNAUTHORIZED, "Invalid session".into()))?;

    Ok(Json(user))
}

async fn logout_handler(
    State(state): State<AppState>,
    headers: HeaderMap,
) -> Result<StatusCode, (StatusCode, String)> {
    if let Some(token) = extract_token(&headers) {
        let _ = state.db.delete_session(&token);
    }
    Ok(StatusCode::OK)
}

async fn profile_handler(
    State(state): State<AppState>,
    Path(username): Path<String>,
) -> Result<Json<serde_json::Value>, (StatusCode, String)> {
    let user_opt = state
        .db
        .get_user_by_username(&username)
        .map_err(|e| (StatusCode::INTERNAL_SERVER_ERROR, e.to_string()))?;

    if let Some((user, _, _)) = user_opt {
        let win_rate = if user.games_played > 0 {
            (user.wins as f32 / user.games_played as f32) * 100.0
        } else {
            0.0
        };

        Ok(Json(serde_json::json!({
            "id": user.id,
            "username": user.username,
            "created_at": user.created_at,
            "elo_rapid": user.elo_rapid,
            "elo_blitz": user.elo_blitz,
            "elo_bullet": user.elo_bullet,
            "games_played": user.games_played,
            "wins": user.wins,
            "losses": user.losses,
            "draws": user.draws,
            "win_rate": (win_rate * 10.0).round() / 10.0,
            "brilliant_moves": user.brilliant_moves,
            "great_moves": user.great_moves,
        })))
    } else {
        Err((StatusCode::NOT_FOUND, "User not found".into()))
    }
}

async fn recent_games_handler(
    State(state): State<AppState>,
    headers: HeaderMap,
) -> Result<Json<Vec<crate::models::GameRecord>>, (StatusCode, String)> {
    let token = extract_token(&headers).ok_or((StatusCode::UNAUTHORIZED, "Missing token".into()))?;
    let user = state
        .db
        .get_user_by_session(&token)
        .map_err(|e| (StatusCode::INTERNAL_SERVER_ERROR, e.to_string()))?
        .ok_or((StatusCode::UNAUTHORIZED, "Invalid session".into()))?;

    let games = state
        .db
        .get_user_recent_games(&user.id, 20)
        .map_err(|e| (StatusCode::INTERNAL_SERVER_ERROR, e.to_string()))?;

    Ok(Json(games))
}

async fn game_details_handler(
    State(state): State<AppState>,
    Path(id): Path<String>,
) -> Result<Json<crate::models::GameRecord>, (StatusCode, String)> {
    let game = state
        .db
        .get_game_by_id(&id)
        .map_err(|e| (StatusCode::INTERNAL_SERVER_ERROR, e.to_string()))?
        .ok_or((StatusCode::NOT_FOUND, "Game not found".into()))?;

    Ok(Json(game))
}

async fn game_analysis_handler(
    State(state): State<AppState>,
    Path(id): Path<String>,
) -> Result<Json<serde_json::Value>, (StatusCode, String)> {
    if let Ok(Some(cached_json)) = state.db.get_analysis(&id) {
        if let Ok(val) = serde_json::from_str::<serde_json::Value>(&cached_json) {
            return Ok(Json(val));
        }
    }

    // If not analyzed yet, analyze now!
    let game = state
        .db
        .get_game_by_id(&id)
        .map_err(|e| (StatusCode::INTERNAL_SERVER_ERROR, e.to_string()))?
        .ok_or((StatusCode::NOT_FOUND, "Game not found".into()))?;

    let analysis = state.engine.analyze_game(&game.id, &game.moves_san);
    let json_str = serde_json::to_string(&analysis).unwrap_or_else(|_| "{}".into());
    let _ = state.db.save_analysis(&id, &json_str);

    let val: serde_json::Value = serde_json::from_str(&json_str).unwrap_or(serde_json::json!({}));
    Ok(Json(val))
}

async fn trigger_analysis_handler(
    State(state): State<AppState>,
    Path(id): Path<String>,
) -> Result<Json<serde_json::Value>, (StatusCode, String)> {
    game_analysis_handler(State(state), Path(id)).await
}

async fn leaderboard_handler(
    State(state): State<AppState>,
    Query(query): Query<LeaderboardQuery>,
) -> Result<Json<Vec<crate::models::UserPublicProfile>>, (StatusCode, String)> {
    let mode = query.mode.as_deref().unwrap_or("rapid");
    let limit = query.limit.unwrap_or(50).clamp(1, 100);

    let list = state
        .db
        .get_leaderboard(mode, limit)
        .map_err(|e| (StatusCode::INTERNAL_SERVER_ERROR, e.to_string()))?;

    Ok(Json(list))
}

async fn ws_upgrade_handler(
    ws: WebSocketUpgrade,
    headers: HeaderMap,
    State(state): State<AppState>,
) -> Response {
    // Check if token was provided in query or auth header, else guest
    let token = extract_token(&headers);
    let user = if let Some(ref t) = token {
        state.db.get_user_by_session(t).ok().flatten()
    } else {
        None
    };

    let (user_id, username, user_elo) = if let Some(u) = user {
        (u.id, u.username, u.elo_rapid)
    } else {
        let guest_id = format!("guest-{}", &uuid::Uuid::new_v4().to_string()[..8]);
        let guest_name = format!("Player_{}", &guest_id[6..10]);
        (guest_id, guest_name, DEFAULT_STARTING_ELO)
    };

    ws.on_upgrade(move |socket| handle_websocket(socket, state, user_id, username, user_elo))
}

async fn handle_websocket(
    socket: WebSocket,
    state: AppState,
    user_id: String,
    username: String,
    user_elo: i32,
) {
    let (mut ws_sender, mut ws_receiver) = socket.split();
    let (tx, mut rx) = mpsc::unbounded_channel::<ServerMessage>();

    // Register active user connection
    state.matchmaker.register_connection(&user_id, tx.clone()).await;

    let matchmaker = Arc::clone(&state.matchmaker);
    let uid = user_id.clone();
    let _uname = username.clone();

    // Outbound task: forward ServerMessages from mpsc to WebSocket client
    let outbound_task = tokio::spawn(async move {
        while let Some(msg) = rx.recv().await {
            if let Ok(json_str) = serde_json::to_string(&msg) {
                if ws_sender.send(Message::Text(json_str)).await.is_err() {
                    break;
                }
            }
        }
    });

    // Inbound task: read messages from client
    let mm = Arc::clone(&matchmaker);
    let uid_inbound = user_id.clone();
    let uname_inbound = username.clone();
    let tx_inbound = tx.clone();

    let inbound_task = tokio::spawn(async move {
        while let Some(Ok(msg)) = ws_receiver.next().await {
            match msg {
                Message::Text(text) => {
                    if let Ok(client_msg) = serde_json::from_str::<ClientMessage>(&text) {
                        match client_msg {
                            ClientMessage::JoinQueue {
                                time_control,
                                time_sec,
                                inc_sec,
                            } => {
                                mm.join_queue(
                                    uid_inbound.clone(),
                                    uname_inbound.clone(),
                                    user_elo,
                                    time_control,
                                    time_sec,
                                    inc_sec,
                                    tx_inbound.clone(),
                                )
                                .await;
                            }
                            ClientMessage::LeaveQueue => {
                                mm.leave_queue(&uid_inbound).await;
                                let _ = tx_inbound.send(ServerMessage::QueueLeft);
                            }
                            ClientMessage::CreateBotGame {
                                bot_level,
                                preferred_color,
                                time_sec,
                                inc_sec,
                            } => {
                                mm.create_bot_game(
                                    &uid_inbound,
                                    &uname_inbound,
                                    user_elo,
                                    bot_level,
                                    &preferred_color,
                                    time_sec,
                                    inc_sec,
                                    &tx_inbound,
                                )
                                .await;
                            }
                            ClientMessage::MakeMove { game_id, uci } => {
                                mm.handle_move(&uid_inbound, &game_id, &uci).await;
                            }
                            ClientMessage::Resign { game_id } => {
                                mm.handle_resign(&uid_inbound, &game_id).await;
                            }
                            ClientMessage::OfferDraw { game_id } => {
                                mm.handle_draw_offer(&uid_inbound, &game_id).await;
                            }
                            ClientMessage::Chat { game_id, text } => {
                                let games = mm.active_games.read().await;
                                if let Some(g) = games.get(&game_id) {
                                    let chat_msg = ServerMessage::ChatReceived {
                                        game_id: game_id.clone(),
                                        sender: uname_inbound.clone(),
                                        text,
                                    };
                                    mm.send_to_user(&g.white_id, chat_msg.clone()).await;
                                    mm.send_to_user(&g.black_id, chat_msg).await;
                                }
                            }
                            ClientMessage::RequestAnalysis { game_id } => {
                                if let Ok(Some(json)) = mm.db.get_analysis(&game_id) {
                                    let _ = tx_inbound.send(ServerMessage::AnalysisResult {
                                        game_id,
                                        analysis_json: json,
                                    });
                                }
                            }
                            ClientMessage::Ping => {
                                let _ = tx_inbound.send(ServerMessage::Pong);
                            }
                        }
                    }
                }
                Message::Close(_) => break,
                _ => {}
            }
        }
    });

    tokio::select! {
        _ = outbound_task => {},
        _ = inbound_task => {},
    };

    // Cleanup on disconnect
    matchmaker.remove_connection(&uid).await;
}
