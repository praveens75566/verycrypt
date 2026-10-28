# VeriCrypt Documentation Architecture Plan (README.md)

A plan for authoring a production-grade, minimalist, noise-free, and visually striking `README.md` that perfectly represents the VeriCrypt codebase, cryptographic standards, architectural design, and GitHub CI/CD automation.

## User Review & Critical Decisions

> [!IMPORTANT]
> **Minimalist, Focused, Noise-Free Design**: Avoid verbose fluff, AI boilerplate, or redundant marketing jargon. Every section will be dense with actionable engineering details: exact cryptographic standards (NIST FIPS, RFCs), clear component tables, ASCII architecture diagrams, size benchmark comparisons, and reproduction build steps.

- **Visual Style**: Clean minimal badges (Kotlin, Compose, Android, NIST FIPS 202, MIT/Apache License), high-contrast ASCII layout diagrams, structured specification tables, and code snippets with syntax highlighting.
- **Audience Balance**: Detailed enough for security engineers and developers auditing cryptographic primitives, yet intuitive for users seeking offline file encryption and integrity verification workflows.

---

## 1. Document Structure & Content Hierarchy

```
┌────────────────────────────────────────────────────────────────────────┐
│                        README.md Layout Schema                         │
├────────────────────────────────────────────────────────────────────────┤
│ 1. Hero Header & Mission Statement                                     │
│    • Project title, description, and status badges                     │
├────────────────────────────────────────────────────────────────────────┤
│ 2. Core Cryptographic Capabilities Matrix                              │
│    • Algorithm, Standard/RFC, Key/Digest Size, Hardware Acceleration    │
├────────────────────────────────────────────────────────────────────────┤
│ 3. Architecture & Security Model                                       │
│    • ASCII Engine Flow: UI -> ViewModel -> Engine -> Keystore / Room   │
│    • Zero-knowledge local-first storage model                          │
├────────────────────────────────────────────────────────────────────────┤
│ 4. Key Workspaces & UI Modules                                         │
│    • Hash Studio (SHA-256, SHA-512, SHA3-512, ZIP Dedup)              │
│    • Cipher Lab (AES-256-GCM, XChaCha20-Poly1305, RSA-4096)           │
│    • Signature Studio (ECDSA P-384, RSA-PSS)                           │
│    • Vault & Secret Notes (Biometric, PIN, Auto-shredding clipboard)   │
│    • Entropy & KDF Sandbox (Argon2id, PBKDF2, HKDF, CSPRNG)            │
│    • Security Hub & Hardware Diagnostics                               │
├────────────────────────────────────────────────────────────────────────┤
│ 5. Performance & APK Footprint Benchmark                               │
│    • Optimization table: 19.4 MB -> 2.1 MB (Release) / 5.1 MB (Debug)  │
│    • Techniques: R8 Full Mode, System Typography, Locale Filtering     │
├────────────────────────────────────────────────────────────────────────┤
│ 6. GitHub CI/CD Pipeline & Automated Releases                          │
│    • Workflow overview, secrets guide, and tag-triggered distribution  │
├────────────────────────────────────────────────────────────────────────┤
│ 7. Build, Test & Development Setup                                     │
│    • Gradle build commands, Robolectric test suite execution           │
└────────────────────────────────────────────────────────────────────────┘
```

---

## 2. Technical Accuracy & Details to Include

1. **Cryptographic Standards**:
   - **SHA3-512**: NIST FIPS 202 Keccak-p[1600, 24] sponge with hardware fallback.
   - **SHA-256 / SHA-512**: NIST FIPS 180-4 secure hash standards.
   - **AES-256-GCM**: NIST SP 800-38D authenticated encryption.
   - **XChaCha20-Poly1305**: RFC 8439 with extended 192-bit nonce.
   - **ECDSA**: FIPS 186-4 (P-256, P-384 curves).
   - **Argon2id & PBKDF2**: RFC 9106 and RFC 8018 password-based key derivation.
2. **Local Security Boundaries**:
   - Android Keystore Hardware Security Module (HSM / StrongBox).
   - BiometricPrompt with cryptographic cipher binding.
   - In-memory clipboard timer with auto-clearing sensitive data.
3. **Build & CI/CD Documentation**:
   - Command reference for `./gradlew assembleRelease` and `./gradlew testDebugUnitTest`.
   - Setup instructions for `RELEASE_KEYSTORE_BASE64` GitHub Secrets.

---

## 3. Step-by-Step Execution Plan

1. **Audit & Verification**: Double-check all active package names, file locations, test classes, and Gradle configurations.
2. **Author `README.md`**: Create `/README.md` with complete, noise-free formatting, badges, tables, and architectural diagrams.
3. **Validate Consistency**: Verify file references in `README.md` against the repository structure.
