//! Rust core prototype for My Launcher.
//!
//! This crate is intentionally isolated from the Android app until its API and
//! Android NDK/JNI integration have been verified.

/// Accepts a Minecraft version identifier without allowing path traversal.
pub fn is_safe_version_id(value: &str) -> bool {
    !value.is_empty()
        && value.len() <= 64
        && value != "."
        && value != ".."
        && value
            .bytes()
            .all(|b| b.is_ascii_alphanumeric() || matches!(b, b'.' | b'_' | b'-'))
}

/// Converts a completed/total byte count into a bounded percentage.
/// A zero total is treated as 0% to avoid division by zero.
pub fn download_percent(completed: u64, total: u64) -> u8 {
    if total == 0 {
        return 0;
    }

    let bounded = completed.min(total) as u128;
    ((bounded * 100) / total as u128) as u8
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn accepts_normal_version_ids() {
        assert!(is_safe_version_id("1.20.1"));
        assert!(is_safe_version_id("fabric-loader-0.16.0-1.21"));
    }

    #[test]
    fn rejects_path_traversal_and_invalid_characters() {
        assert!(!is_safe_version_id(""));
        assert!(!is_safe_version_id("../1.20.1"));
        assert!(!is_safe_version_id("folder/name"));
        assert!(!is_safe_version_id(".."));
    }

    #[test]
    fn download_progress_is_bounded() {
        assert_eq!(download_percent(0, 100), 0);
        assert_eq!(download_percent(25, 100), 25);
        assert_eq!(download_percent(150, 100), 100);
        assert_eq!(download_percent(1, 0), 0);
    }
}
