# Deep Professional Android APK Minimalist Optimization Plan

A senior Android performance engineering plan to systematically audit and compress every layer of the VeriCrypt APK (DEX, Resources, Assets, Packaging, and Reflection boundaries) to achieve the most minimalistic footprint possible while strictly preserving 100% of cryptographic, vault, and UI capabilities.

## User Review & Critical Decisions

> [!IMPORTANT]
> **Strict Zero-Feature-Loss Contract**: Every single cryptographic primitive (SHA-256, SHA-512, NIST FIPS 202 SHA3-512, AES-256-GCM, ChaCha20-Poly1305, RSA-4096, ECDSA P-384, Argon2id, PBKDF2), the Encrypted Vault, Hardware Keystore, Diagnostics, File Hash streaming, and Markdown previewer remain 100% functional. Optimization will be conducted with surgical precision on bytecode, dead-code elimination, and resource packaging.

### Core Architectural Optimizations
1. **DEX Bytecode & R8 Full Mode (`android.enableR8.fullMode=true`)**:
   - Enable R8 full mode to activate aggressive inter-procedural inlining, dead-branch elimination, class merging, and method devirtualization across library boundaries.
   - Enable `-allowaccessmodification` in `proguard-rules.pro` to widen access permissions, allowing R8 to inline private/package-private utility methods directly into call sites.
   - Eliminate over-broad `-keep class com.example.ui.** { *; }` rules that previously forced R8 to keep thousands of unused Compose synthetic methods and classes. Replace with targeted ProGuard preservation only for Room entities, biometric callbacks, and Keystore reflection boundaries.
   - Enable `-repackageclasses 'a'` to flatten internal class descriptors and minimize DEX string pool overhead.
2. **Resource Table Stripping (`resourceConfigurations += listOf("en")`)**:
   - External dependencies (`androidx.appcompat`, `material3`, `androidx.core`, `biometric`) bundle localized string XMLs across 80+ locales. Restricting packaging to `"en"` eliminates thousands of unused localized string entries from `resources.arsc`.
3. **Zero-Byte Native System Typography Migration**:
   - Replace bundled font binaries (`jetbrains_mono.ttf` [57KB] and `plus_jakarta_sans.ttf` [29KB]) with Android's pre-installed `/system/fonts/` (`FontFamily.Monospace` and `FontFamily.SansSerif`).
   - Retains crisp, pixel-perfect tabular alignment for 64-char (SHA-256) and 128-char (SHA-512 / SHA3-512) hex digests at zero APK storage cost.
4. **APK Packaging Exclusions**:
   - Exclude unnecessary `META-INF` files (`*.kotlin_module`, `*.version`, build metadata) from the final APK zip structure.

---

## 1. Professional APK Audit: Layer-by-Layer Breakdown

```
┌────────────────────────────────────────────────────────────────────────┐
│                        APK Footprint Layers                            │
├───────────────────────┬──────────────────────┬─────────────────────────┤
│ Component             │ Status Quo           │ Optimized State         │
├───────────────────────┼──────────────────────┼─────────────────────────┤
│ DEX Code (classes.dex)│ 1.8 MB (uncompressed)│ ~1.1 - 1.3 MB           │
│                       │ Broad keep rules     │ R8 Full Mode + Inlining │
├───────────────────────┼──────────────────────┼─────────────────────────┤
│ Fonts (`res/font/`)   │ 86.8 KB (bundled TTF)│ 0 KB (System fonts)     │
├───────────────────────┼──────────────────────┼─────────────────────────┤
│ `resources.arsc`      │ 248.8 KB             │ ~110 - 130 KB           │
│                       │ 80+ unused locales   │ Filtered: "en" only     │
├───────────────────────┼──────────────────────┼─────────────────────────┤
│ `META-INF/`           │ Kotlin modules, vers │ Stripped via packaging  │
├───────────────────────┼──────────────────────┼─────────────────────────┤
│ Net APK Compressed    │ ~2.3 MB              │ ~1.3 - 1.6 MB (MINIMAL) │
└───────────────────────┴──────────────────────┴─────────────────────────┘
```

---

## 2. In-Depth Technical Implementation Steps

### Step 1: Build Properties & R8 Configuration
- File: `/gradle.properties`
  - Add `android.enableR8.fullMode=true` to enable R8's strongest optimization engine.
  - Retain worker and daemon tuning for container performance.

### Step 2: ProGuard Rules Surgical Refinement
- File: `/app/proguard-rules.pro`
  - Add `-allowaccessmodification` for cross-package method inlining.
  - Add `-repackageclasses 'com.example.min'` to compress package paths.
  - Retain strictly required preservation rules:
    - `@androidx.room.Entity` and `@androidx.room.Dao` implementations.
    - Android framework entry points (`MainActivity`).
    - Cryptographic keystore models.
  - Remove blanket `-keep class com.example.ui.** { *; }` to permit R8 to prune unused Compose code.

### Step 3: Resource Configurations & Packaging Cleanup
- File: `/app/build.gradle.kts`
  - Add `resourceConfigurations += listOf("en")` to `defaultConfig`.
  - Add packaging exclusions for `META-INF/*.kotlin_module` and `META-INF/services/*`.

### Step 4: System Typography Migration
- File: `/app/src/main/java/com/example/ui/theme/Type.kt`
  - Migrate `PlusJakartaSansFamily` to `FontFamily.SansSerif` (Android system Roboto / Noto Sans).
  - Migrate `JetBrainsMonoFamily` to `FontFamily.Monospace` (Android system Roboto Mono / Droid Sans Mono).
- Files to delete:
  - `/app/src/main/res/font/jetbrains_mono.ttf`
  - `/app/src/main/res/font/plus_jakarta_sans.ttf`
  - `/app/src/main/res/font` directory.

### Step 5: Verification & Quality Assurance
- Run `gradle :app:testDebugUnitTest` to verify all cryptographic algorithms (NIST FIPS 202 SHA3-512, SHA-512, SHA-256, AES-256-GCM, ChaCha20-Poly1305, RSA, ECDSA) and Room persistence pass unit tests.
- Run `compile_applet` to verify compilation and packaging.
- Inspect the generated APK file size and content breakdown via `ls -lh` and `unzip -l`.
