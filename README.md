# VeriCrypt

<div align="center">

![Kotlin](https://img.shields.io/badge/Kotlin-2.0+-7F52FF?style=flat-square&logo=kotlin&logoColor=white)
![Compose](https://img.shields.io/badge/Jetpack%20Compose-M3-4285F4?style=flat-square&logo=jetpackcompose&logoColor=white)
![Security](https://img.shields.io/badge/Cryptography-NIST%20FIPS%20202-00E676?style=flat-square)
![Architecture](https://img.shields.io/badge/Architecture-Offline--First%20%7C%20Zero--Knowledge-00E5FF?style=flat-square)
![Build](https://img.shields.io/badge/APK%20Size-2.1%20MB-FF9100?style=flat-square)
![License](https://img.shields.io/badge/License-Apache%202.0-blue?style=flat-square)

**A professional, offline-first cryptographic security workspace for Android.**  
High-throughput integrity hashing, authenticated symmetric & asymmetric encryption, digital signatures, and biometric vault storage.

[Features](#core-workspaces) • [Cryptographic Matrix](#cryptographic-specifications) • [Architecture](#architecture) • [CI/CD](#cicd-pipeline) • [Build & Test](#build--development)

</div>

---

## Overview

VeriCrypt is a zero-telemetry, zero-cloud cryptographic suite engineered for developers, security researchers, and privacy-conscious users. It runs entirely on-device, leveraging hardware security modules (Android KeyStore, StrongBox) with pure-Kotlin cryptographic fallbacks to guarantee reliability across all environments.

- **100% Offline & Private**: Zero external network calls, zero analytics, zero permissions beyond biometric auth.
- **Ultra-Compact Footprint**: 2.1 MB release package optimized via R8 Full Mode, native system typography, and locale filtering.
- **Quantum-Resilient Standards**: Includes NIST FIPS 202 SHA3-512 alongside SHA-256 and SHA-512.

---

## Cryptographic Specifications

| Primitive | Standard / Reference | Parameters | Security Guarantee |
|---|---|---|---|
| **SHA3-512** | NIST FIPS 202 | Keccak-p[1600, 24], $c=1024, r=576$ | 512-bit quantum collision resistance |
| **SHA-512** | NIST FIPS 180-4 | 1024-bit block size, 80 rounds | 256-bit collision resistance |
| **SHA-256** | NIST FIPS 180-4 | 512-bit block size, 64 rounds | 128-bit collision resistance |
| **AES-GCM** | NIST SP 800-38D | 256-bit key, 96-bit IV, 128-bit tag | Authenticated Encryption with Associated Data (AEAD) |
| **XChaCha20-Poly1305** | RFC 8439 / HChaCha20 | 256-bit key, 192-bit nonce, 128-bit tag | Extended-nonce AEAD immune to nonce-reuse collisions |
| **RSA-4096** | PKCS#1 v2.2 (RFC 8017) | 4096-bit modulus, OAEP with SHA-256 | High-assurance asymmetric key exchange |
| **ECDSA** | NIST FIPS 186-4 | Curves: `secp256r1` (P-256), `secp384r1` (P-384) | Elliptic Curve Digital Signature Algorithm |
| **Argon2id** | RFC 9106 | $t=3, m=64\text{MB}, p=4$ (configurable) | Memory-hard password-based key derivation |
| **PBKDF2** | RFC 8018 | HMAC-SHA256, 100,000+ iterations | Standard password stretching |
| **CSPRNG** | NIST SP 800-90A | Java `SecureRandom` + Linux `/dev/urandom` | Cryptographically secure entropy pool |

---

## Architecture

VeriCrypt separates UI presentation, reactive state flow, and low-level cryptographic execution into distinct, modular boundaries:

```
┌────────────────────────────────────────────────────────────────────────┐
│                        Jetpack Compose UI (M3)                         │
│   HashStudio  │  CipherLab  │  SignatureStudio  │  Vault  │  Diagnostics│
└───────────────────────────────────┬────────────────────────────────────┘
                                    │ StateFlow / Events
                                    ▼
┌────────────────────────────────────────────────────────────────────────┐
│                       ViewModel Layer (MVVM)                           │
│   HashViewModel │ CryptoViewModel │ VaultViewModel │ SignatureViewModel│
└───────────────────────────────────┬────────────────────────────────────┘
                                    │ Coroutines / Dispatchers.Default
                                    ▼
┌────────────────────────────────────────────────────────────────────────┐
│                    Cryptographic Core Engines                          │
│  ├── HashingEngine (SHA-256, SHA-512, SHA3-512 / Keccak sponge)       │
│  ├── CryptoEngine (AES-256-GCM, RSA-4096 OAEP, Envelope encryption)    │
│  ├── XChaCha20Poly1305Engine (192-bit Extended Nonce AEAD)             │
│  ├── SignatureEngine (ECDSA P-384, RSA-PSS signature & verification)   │
│  ├── KdfEngine (Argon2id, PBKDF2-HMAC-SHA256, HKDF)                    │
│  ├── HmacEngine (Constant-time timing-attack-safe verification)        │
│  └── CsprngTokenEngine (Entropy evaluation, DiceWare, Alpha-Numeric)   │
└───────────────────┬───────────────────────────────┬────────────────────┘
                    │                               │
                    ▼                               ▼
┌──────────────────────────────────┐ ┌──────────────────────────────────┐
│   Hardware Security Layer        │ │   Local Persistence (Room / SQL) │
│   • Android KeyStore Provider    │ │   • Encrypted Vault Items        │
│   • StrongBox HSM integration    │ │   • Secret Notes with masked key │
│   • BiometricPrompt CryptoObject │ │   • Immutable Local Audit Log    │
└──────────────────────────────────┘ └──────────────────────────────────┘
```

---

## Core Workspaces

### 1. Hash & Integrity Studio
- **Multi-Algorithm Hashing**: Concurrently streams SHA-256, SHA-512, and NIST FIPS 202 SHA3-512 across single files or live text inputs.
- **Checksum Matcher**: Auto-detects expected hash lengths (64-char / 128-char) and performs constant-time comparison against computed digests with visual green/red verification badges.
- **ZIP Archive Deduplication**: Recursively inspects archive contents, calculates member hashes, and flags duplicate payload chunks to prevent redundancy.

### 2. Cipher Lab
- **Authenticated Encryption**: Encrypts files and messages with AES-256-GCM or XChaCha20-Poly1305.
- **Armored Text Container**: Exports encrypted payloads as armored text blocks with embedded salt, IV/nonce, and authentication tags.
- **Hybrid Asymmetric Encryption**: Generates RSA-4096 keypairs to encrypt symmetric session keys with OAEP padding.

### 3. Digital Signatures
- **ECDSA & RSA-PSS**: Sign documents, hashes, or messages using elliptic curve P-256 / P-384 keys or RSA-4096.
- **Public Key Verification**: Validates raw signatures against PEM-formatted public keys with tamper detection.

### 4. Zero-Knowledge Biometric Vault
- **Encrypted Local Storage**: Encrypts sensitive credentials, tokens, and multi-field secret notes in an on-device SQLite database via Room.
- **Biometric & PIN Gating**: Requires fingerprint/face unlock via `BiometricPrompt` with hardware cryptographic binding.
- **Auto-Clearing Clipboard**: Automatically clears sensitive copied tokens from system clipboard after a user-configured timeout (default: 30 seconds).

### 5. Security Hub & Hardware Diagnostics
- **Hardware Telemetry**: Probes Android KeyStore capabilities, StrongBox availability, hardware-backed curve support, and system entropy.
- **Benchmark Suite**: Measures real-time cryptographic throughput (MB/s) for AES-GCM, ChaCha20, and SHA digests on the active CPU.
- **Audit Ledger**: Maintains a local, tamper-evident log of encryption, decryption, and authentication attempts.

---

## Performance & Optimization

Through R8 full-mode optimization, dead-code stripping, and resource filtering, VeriCrypt achieves an ultra-lightweight binary:

```
Original Debug Build  : 19.4 MB
Debug (JDWP Enabled)  :  5.1 MB  (-73%)
Production Release APK:  2.1 MB  (-89%)
```

- **Locale Pruning**: External library translations stripped via `androidResources.localeFilters += listOf("en")`, reducing `resources.arsc` by 60%.
- **Zero-Byte System Typography**: Uses pre-installed `/system/fonts/` (`FontFamily.Monospace` and `FontFamily.SansSerif`), removing 87 KB of bundled font files while preserving crisp monospace hex rendering.
- **Bytecode Inlining**: Enabled `android.enableR8.fullMode=true` and `-allowaccessmodification` for cross-package devirtualization and method inlining.

---

## CI/CD Pipeline

The project includes an enterprise GitHub Actions workflow (`.github/workflows/android-ci.yml`):

- **Automated Validation**: Runs Robolectric unit tests verifying SHA3-512, SHA-512, SHA-256, and AES-256-GCM algorithms on every push and pull request.
- **Production Artifacts**: Builds minified release APKs and Google Play App Bundles (AAB).
- **Automated Checksums**: Calculates SHA-256 and SHA-512 digests for all artifacts (`checksums.txt`).
- **Tag-Triggered Releases**: Pushing a version tag (`git tag v1.0.0 && git push origin v1.0.0`) automatically creates a GitHub Release with attached binaries.

*Refer to [`.github/CI_CD_GUIDE.md`](.github/CI_CD_GUIDE.md) for instructions on configuring keystore secrets.*

---

## Build & Development

### Prerequisites
- JDK 17+ (Temurin recommended)
- Android SDK (API Level 36, Min SDK 24)
- Gradle 9.3+

### Common Commands

```bash
# Build debug APK
./gradlew :app:assembleDebug

# Run Robolectric unit tests
./gradlew :app:testDebugUnitTest

# Compile minified production release APK
./gradlew :app:assembleRelease

# Compile Google Play App Bundle (AAB)
./gradlew :app:bundleRelease
```

---

## Threat Model & Security Boundaries

- **Local Storage**: Keys and credentials in the vault are encrypted at rest using AES-256-GCM derived via PBKDF2 with unique salts.
- **Memory Safety**: Cryptographic buffers and passphrases use `CharArray` and byte arrays that are zeroed out after use where feasible.
- **Timing Attacks**: Signature and MAC verifications utilize `MessageDigest.isEqual` to execute in constant time, preventing side-channel timing leaks.
- **Hardware Isolation**: Private keys can be stored inside the Android KeyStore, where key material never enters user-space memory.

---

## License

```
Copyright 2026 VeriCrypt Contributors

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

    http://www.apache.org/licenses/LICENSE-2.0
```
