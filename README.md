# WLM Messenger — Android Client

Modern Android messenger for Windows Live Messenger (MSNP18) protocol with custom servers.

## Build via GitHub Actions

### Step 1: Create a GitHub Repository

1. Go to [github.com/new](https://github.com/new)
2. Create a **public** repository (free CI/CD)
3. Do NOT initialize with README

### Step 2: Push the Code

In Termux, run these commands:

```bash
cd /storage/emulated/0/Download/wlm-client/project/downloads/wlm-client
git init
git add .
git commit -m "Initial commit: WLM Messenger client"
git branch -M main
git remote add origin https://github.com/YOUR_USERNAME/YOUR_REPO_NAME.git
git push -u origin main
```

Replace `YOUR_USERNAME` and `YOUR_REPO_NAME` with your actual GitHub username and repo name.

### Step 3: Wait for Build

1. Go to your repo → **Actions** tab
2. Wait for the "Build Debug APK" workflow to complete (~10-15 min)
3. Click on the workflow run → scroll to **Artifacts**
4. Download `wlm-messenger-debug.apk`

### Install APK

Transfer the APK to your device and install it.

## Project Structure

```
app/src/main/java/com/yourapp/wlm/
├── data/          — Database, network, repositories
├── domain/        — Models, use cases, repository interfaces
├── presentation/  — Compose UI, ViewModels, navigation
└── di/            — Hilt dependency injection
```

## Tech Stack

- **Kotlin 2.x** — 100% Kotlin
- **Jetpack Compose** — Declarative UI
- **Material Design 3** — Modern UI
- **Hilt** — Dependency injection
- **Room** — Local database
- **MSNP18** — WLM protocol

## Servers

| Service | Host |
|---------|------|
| Notification Server | `ms.msgrsvcs.ctsrv.gay:1863` |
| HTTP Gateway | `httpgws.ms.msgrsvcs.ctsrv.gay` |
| Passport/SSO | `pp.login.ugnet.gay` |
