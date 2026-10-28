# VeriCrypt GitHub Actions CI/CD Guide

This repository includes a production-grade GitHub Actions CI/CD workflow (`.github/workflows/android-ci.yml`) that automates code quality validation, unit testing, R8 release builds, and GitHub Releases distribution.

---

## 1. Automated Workflow Overview

The CI/CD pipeline triggers automatically on:
- **Push** to `main` or `master` branches
- **Pull Requests** targeting `main` or `master`
- **Tag Pushes** matching `v*` (e.g. `git tag v1.0.0 && git push origin v1.0.0`)
- **Manual Trigger** (`workflow_dispatch`) from the GitHub Actions UI

### Pipeline Stages:
1. **🧪 Test & Code Validation**:
   - Runs on Ubuntu runner with Temurin JDK 17
   - Executes Robolectric unit tests (`./gradlew :app:testDebugUnitTest`)
   - Verifies NIST FIPS 202 SHA3-512, SHA-512, SHA-256, and AES-256-GCM algorithms
   - Saves HTML test results as a downloadable workflow artifact
2. **📦 Build Release APK & App Bundle**:
   - Compiles optimized, minified production APK (`assembleRelease`)
   - Compiles Google Play App Bundle (`bundleRelease`)
   - Computes SHA-256 and SHA-512 cryptographic checksums (`checksums.txt`)
   - Uploads APK, AAB, and checksums as build artifacts
3. **🚀 Publish GitHub Release (Tag Push only)**:
   - Triggers when pushing version tags like `v1.0.0`
   - Automatically generates release notes from commit history
   - Attaches `app-release.apk`, `app-release.aab`, and `checksums.txt` directly to the release page

---

## 2. Setting Up Release Signing Secrets (Optional)

By default, the workflow generates a temporary CI signing keystore so builds succeed out of the box. For publishing official production releases signed with your own Google Play key, configure the following **Repository Secrets** in GitHub (**Settings > Secrets and variables > Actions**):

| Secret Name | Description | Example |
|---|---|---|
| `RELEASE_KEYSTORE_BASE64` | Base64-encoded string of your `.jks` or `.keystore` file | `base64 -w 0 my-upload-key.jks` |
| `RELEASE_KEYSTORE_PASSWORD` | Password for your release keystore | `your_store_password` |
| `RELEASE_KEY_ALIAS` | Key alias in the keystore | `upload` |
| `RELEASE_KEY_PASSWORD` | Password for the key alias | `your_key_password` |

### How to generate a release keystore and Base64 string:
```bash
# 1. Generate release keystore (if you don't already have one)
keytool -genkeypair -v -keystore my-upload-key.jks -alias upload -keyalg RSA -keysize 2048 -validity 10000

# 2. Convert to Base64 to paste into GitHub Secrets
base64 -w 0 my-upload-key.jks
```

---

## 3. How to Trigger a Production Release

To trigger an official release with attached APKs:
```bash
# Tag the current commit
git tag v1.0.0

# Push tag to GitHub
git push origin v1.0.0
```

GitHub Actions will automatically run tests, build the release APK and AAB, compute SHA-256 checksums, and publish the release under **Releases** on your GitHub repository.
