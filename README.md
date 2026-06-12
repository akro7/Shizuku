# Shizuku Root

A unified Android app combining **Shizuku** (privileged API manager) with **Root management** capabilities.

## Features

### Home Tab (Shizuku)
- Shizuku service status & management
- Start via root, wireless ADB, or USB ADB
- Authorized apps management
- Terminal integration (rish)
- Magisk Root card (install/patch/flash modules)
- Root Hide Scanner

### Superuser Tab
- View all apps with root access (reads Magisk DB)
- Grant/Revoke root permissions via toggle
- Root status indicator

### Settings Tab
- Dark/light theme
- Auto-start on boot
- Update checker

## Build

### Requirements
- Android Studio Hedgehog or newer
- Android SDK 36
- NDK 29.0.13113456
- CMake 3.31+

### Steps
```bash
git clone <repo>
cd ShizukuRoot
cp local.properties.example local.properties
# Edit local.properties with your SDK path
./gradlew :manager:assembleDebug
```

The APK will be at `manager/build/outputs/apk/debug/`.

## Architecture

```
ShizukuRoot/
├── manager/          # Main app (UI + Shizuku + Root management)
├── server/           # Shizuku server code
├── starter/          # Shizuku service starter
├── shell/            # rish shell integration
├── common/           # Shared utilities
└── api/              # Shizuku API submodule
    ├── api/          # Shizuku.java public API
    ├── aidl/         # AIDL interfaces
    ├── server-shared/
    ├── provider/
    ├── shared/
    └── rish/
```

## License
Based on Shizuku by RikkaApps — Apache 2.0
