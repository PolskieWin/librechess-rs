#![allow(dead_code)]

use aes_gcm::{
    aead::{Aead, KeyInit, OsRng},
    Aes256Gcm, Nonce,
};
use rand::RngCore;
use sha2::{Digest, Sha256};

/// Hash password using SHA-256 + 16-byte random salt + 50,000 PBKDF2-style stretching rounds
pub fn hash_password(password: &str) -> (String, String) {
    let mut salt_bytes = [0u8; 16];
    OsRng.fill_bytes(&mut salt_bytes);
    let salt_hex = hex::encode(salt_bytes);
    let hash = stretch_hash(password, &salt_hex);
    (hash, salt_hex)
}

/// Verify password against stored hash and salt
pub fn verify_password(password: &str, salt_hex: &str, expected_hash: &str) -> bool {
    let computed_hash = stretch_hash(password, salt_hex);
    // Constant time comparison to prevent timing attacks
    subtle_eq(&computed_hash, expected_hash)
}

fn stretch_hash(password: &str, salt: &str) -> String {
    let mut hasher = Sha256::new();
    hasher.update(password.as_bytes());
    hasher.update(salt.as_bytes());
    let mut current = hasher.finalize();

    // 50,000 iterative rounds for brute-force resistance
    for i in 0u32..50_000u32 {
        let mut h = Sha256::new();
        h.update(current);
        h.update(salt.as_bytes());
        h.update(&i.to_be_bytes());
        current = h.finalize();
    }

    hex::encode(current)
}

fn subtle_eq(a: &str, b: &str) -> bool {
    if a.len() != b.len() {
        return false;
    }
    let mut diff = 0u8;
    for (x, y) in a.bytes().zip(b.bytes()) {
        diff |= x ^ y;
    }
    diff == 0
}

/// Generate a cryptographically secure random session token (64 hex characters)
pub fn generate_secure_token() -> String {
    let mut token_bytes = [0u8; 32];
    OsRng.fill_bytes(&mut token_bytes);
    hex::encode(token_bytes)
}

/// AES-256-GCM encryption for sensitive data at rest
pub fn encrypt_aes_gcm(plaintext: &[u8], key_32bytes: &[u8; 32]) -> Result<String, String> {
    let cipher = Aes256Gcm::new_from_slice(key_32bytes).map_err(|e| e.to_string())?;
    let mut nonce_bytes = [0u8; 12];
    OsRng.fill_bytes(&mut nonce_bytes);
    let nonce = Nonce::from_slice(&nonce_bytes);

    let ciphertext = cipher
        .encrypt(nonce, plaintext)
        .map_err(|e| e.to_string())?;

    // Prepend nonce to ciphertext and hex encode
    let mut combined = nonce_bytes.to_vec();
    combined.extend_from_slice(&ciphertext);
    Ok(hex::encode(combined))
}

/// AES-256-GCM decryption for sensitive data at rest
pub fn decrypt_aes_gcm(hex_encrypted: &str, key_32bytes: &[u8; 32]) -> Result<Vec<u8>, String> {
    let bytes = hex::decode(hex_encrypted).map_err(|e| e.to_string())?;
    if bytes.len() < 12 {
        return Err("Ciphertext too short".to_string());
    }
    let (nonce_bytes, ciphertext) = bytes.split_at(12);
    let cipher = Aes256Gcm::new_from_slice(key_32bytes).map_err(|e| e.to_string())?;
    let nonce = Nonce::from_slice(nonce_bytes);
    cipher.decrypt(nonce, ciphertext).map_err(|e| e.to_string())
}
