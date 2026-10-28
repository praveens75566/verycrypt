# Professional GitHub Actions CI/CD Pipeline Plan

An enterprise-grade GitHub Actions CI/CD architecture to automate validation, testing, R8 release APK & AAB compilation, secure keystore signing, and automatic GitHub Releases with cryptographic checksums.

## User Review & Critical Decisions

> [!IMPORTANT]
> **Production CI/CD Standards**: The pipeline adheres to GitHub Actions security best practices: least-privilege permissions, dependency caching via `gradle/actions/setup-gradle@v4`, dual signing pathways (signed release when GitHub Secrets are configured, with graceful fallback to unsigned artifacts), and automated checksum verification.

- **Trigger Strategy**:
  - `push` to `main` and `master` branches.
  - `pull_request` targeting `main` and `master`.
  - `tags: ['v*']` for triggering automated production GitHub Releases.
  - `workflow_dispatch` for manual runs from the GitHub UI with customizable build options.
- **Signing Key Handling via GitHub Secrets**:
  - `KEYSTORE_BASE64`: Base64-encoded JKS/Keystore.
  - `KEYSTORE_PASSWORD`, `KEY_ALIAS`, `KEY_PASSWORD`: Credentials injected safely into build environment variables (never committed to repository).
  - Graceful fallback: If secrets are omitted in new forks, CI continues building unsigned release APKs and App Bundles without failing.
- **Artifacts & Distribution**:
  - **Pull Requests & Commits**: Uploads signed/unsigned release APK (`app-release.apk`), App Bundle (`app-release.aab`), and test reports as workflow artifacts with a 30-day retention.
  - **Git Tag Releases**: Automatically publishes a GitHub Release with changelog, attaching the release APK, AAB, and calculated SHA-256 and SHA3-512 checksum files.

---

## 1. CI/CD Workflow Architecture

```
┌────────────────────────────────────────────────────────────────────────┐
│                        GitHub Actions Triggers                         │
│            push (main)  │  pull_request  │  tags ('v*')  │  dispatch   │
└───────────────────────────────────┬────────────────────────────────────┘
                                    │
                                    ▼
┌────────────────────────────────────────────────────────────────────────┐
│                    Job 1: Validate & Run Unit Tests                    │
│  • Ubuntu-latest runner with Temurin JDK 17                            │
│  • Gradle dependency caching via `setup-gradle@v4`                     │
│  • Execute: `./gradlew :app:testDebugUnitTest`                         │
│  • Verifies NIST FIPS 202 SHA3-512, SHA-512, SHA-256, AES-256-GCM      │
│  • Upload HTML test reports on failure or success                      │
└───────────────────────────────────┬────────────────────────────────────┘
                                    │
                                    ▼ (passes)
┌────────────────────────────────────────────────────────────────────────┐
│                  Job 2: Build & Package Release APK / AAB              │
│  • Decode Base64 Keystore from Secrets (if configured)                 │
│  • Execute: `./gradlew :app:assembleRelease :app:bundleRelease`        │
│  • Run R8 bytecode optimization, shrink resources, strip unused locales │
│  • Calculate SHA-256 and SHA3-512 checksums for artifacts              │
│  • Upload release artifacts (`app-release.apk`, `app-release.aab`)     │
└───────────────────────────────────┬────────────────────────────────────┘
                                    │
                                    ▼ (tag push only)
┌────────────────────────────────────────────────────────────────────────┐
│                     Job 3: Publish GitHub Release                      │
│  • Triggered on `v*` tags (e.g. `v1.0.0`)                              │
│  • Create official GitHub Release with auto-generated release notes    │
│  • Attach `VeriCrypt-vX.X.X.apk`, `.aab`, and `checksums.txt`          │
└────────────────────────────────────────────────────────────────────────┘
```

---

## 2. Implementation Deliverables

### 1. GitHub Actions Workflow Configuration
- **File**: `.github/workflows/android-ci.yml`
  - Multi-job pipeline: `test`, `build-release`, and conditional `publish-release`.
  - Uses `actions/checkout@v4`, `actions/setup-java@v4`, `gradle/actions/setup-gradle@v4`, and `softprops/action-gh-release@v2`.
  - Step to compute cryptographic checksums:
    ```bash
    sha256sum app-release.apk > checksums.txt
    ```

### 2. Gradle Wrapper Generation & Headless CI Compatibility
- Ensure standard `gradlew` script and `gradle/wrapper/gradle-wrapper.jar` are committed so GitHub runner can execute `./gradlew` without needing global Gradle installation.

### 3. Graceful Release Signing Configuration in Gradle
- **File**: `app/build.gradle.kts`
  - Ensure release build signing checks if the keystore file exists before binding the signing configuration, ensuring local and forked CI builds succeed even before secrets are provisioned.

### 4. Setup Guide & Secrets Documentation
- **File**: `.github/CI_CD_GUIDE.md`
  - Documentation explaining how to generate a release keystore, encode to Base64, and configure GitHub Secrets (`KEYSTORE_BASE64`, `KEYSTORE_PASSWORD`, `KEY_ALIAS`, `KEY_PASSWORD`).

---

## 3. Step-by-Step Execution Plan

1. **Gradle Build Update**: Make release signing in `app/build.gradle.kts` conditional on file existence so headless builds without secrets produce unsigned APKs cleanly.
2. **Generate Gradle Wrapper**: Execute `gradle wrapper` to generate portable `gradlew` and `gradle-wrapper.jar`.
3. **Create Workflow File**: Write `.github/workflows/android-ci.yml` with test, build, artifact upload, and tag release steps.
4. **Create CI/CD Documentation**: Add `.github/CI_CD_GUIDE.md` detailing secrets configuration and release triggering.
5. **Verification**: Run `compile_applet` and local test tasks to ensure compatibility with Gradle wrapper.
