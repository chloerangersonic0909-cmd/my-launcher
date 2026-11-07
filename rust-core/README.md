# My Launcher Rust Core (Prototype)

This crate is the first isolated step toward using Rust for suitable launcher
core logic.

## Current scope

- Validate Minecraft version identifiers before they are used as path segments.
- Calculate bounded download progress.
- Unit tests for both utilities.

## Integration status

This crate is **not yet connected to the Android application** and is not
included in the APK. The existing Java/Kotlin Android UI, Android lifecycle,
permissions, account flows, and game-launch integration remain unchanged.

The next engineering step is to add an Android NDK build for the required
ABIs, expose a small JNI boundary, and move one well-defined native operation
at a time. Each step must pass the existing Android build before it is merged
into the release branch.
