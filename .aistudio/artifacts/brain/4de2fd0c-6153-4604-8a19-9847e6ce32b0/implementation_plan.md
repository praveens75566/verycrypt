# Implementation Plan: Optimize APK Size to within 10–20MB

## User Goal
Decrease the application APK size down to within **10–20MB max** while keeping **100% of existing features, tools, settings, vault capabilities, and UI polish** intact.

---

## Technical Root Cause Analysis
1. **DEX Bytecode Bloat from `material-icons-extended`**:
   - `androidx.compose.material:material-icons-extended` bundles over 10,000 vector icon classes directly into `classes.dex`, contributing ~25–30MB of uncompressed DEX size.
   - The application only uses ~15–20 specific icons from this library.
   - *Fix*: Replace extended icon imports with core icons or lightweight standalone `ImageVector` definitions in a dedicated icon utility (`AppIcons.kt`), then remove the massive extended icons dependency.

2. **Unused Pre-bundled Template Libraries**:
   - The project template includes unused libraries:
     - `firebase-ai` (Gemini SDK, gRPC, Protobuf, Netty dependencies)
     - `firebase-appcheck-recaptcha` & `firebase-appcheck-debug`
     - `retrofit`, `moshi`, `logging-interceptor`, `okhttp`
   - *Fix*: Comment out unused dependencies as instructed in the platform guidelines ("APK Size Optimization: You MUST comment out all unused dependencies to reduce APK size and keep build times fast").

3. **Code & Resource Shrinking Configuration**:
   - Configure R8 code minification (`isMinifyEnabled = true`) and resource shrinking (`isShrinkResources = true`) in `build.gradle.kts` for release builds, and tune packaging exclusions.

---

## Proposed Changes

### 1. Vector Icon Optimization (`AppIcons.kt`)
- Inspect all files currently importing `androidx.compose.material.icons.filled.*` or `androidx.compose.material.icons.outlined.*`.
- Create `com.example.ui.theme.AppIcons` providing standard `ImageVector` instances or mapping to existing `Icons.Default` / `Icons.AutoMirrored` icons without importing `material-icons-extended`.
- Update screens (`VaultScreen`, `ProtectScreen`, `ToolsScreen`, `SecurityHubScreen`, `SettingsScreen`, `MarkdownReaderView`, etc.) to use `AppIcons` or core icons.

### 2. Gradle Dependency & Build Optimization (`app/build.gradle.kts`)
- Remove or comment out unused template dependencies:
  - `libs.androidx.compose.material.icons.extended`
  - `libs.firebase.ai`
  - `libs.firebase.appcheck.recaptcha`
  - `libs.firebase.appcheck.debug`
  - `libs.retrofit`
  - `libs.converter.moshi`
  - `libs.moshi.kotlin`
  - `libs.logging.interceptor`
  - `libs.okhttp`
- Add packaging options to exclude duplicate metadata and licenses (`META-INF/DEPENDENCIES`, `META-INF/LICENSE*`).
- Configure release build type with `isMinifyEnabled = true` and `isShrinkResources = true`.

### 3. Feature & Screen Verification
- Ensure zero features are altered or removed:
  - Vault with encrypted storage and search
  - File and text encryption/decryption (AES-256-GCM, ChaCha20-Poly1305)
  - Hash integrity (SHA-256, SHA-512) and ECDSA digital signatures
  - CSPRNG token and password generator
  - Markdown text reader with reading metrics and display modes
  - Security hub and comprehensive security settings (OLED dark theme, auto-lock, clipboard clearing, shredding passes)

---

## Verification Plan

### Automated Verification
1. Run `compile_applet` to verify compilation and dependency resolution.
2. Run `gradle :app:testDebugUnitTest` to confirm all unit tests pass without errors.
3. Build the APK using `gradle :app:assembleDebug` and measure output size via `ls -lh .build-outputs/app-debug.apk` to verify it falls within the target range (10–20MB or lower).
