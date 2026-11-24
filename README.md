# Telegram Hidden Vault («Second Bottom» inside Archive)

A native Android UI & privacy feature concept built in pure Kotlin (`Views` + `Canvas` + `SpringAnimation` + `RenderEffect` + `BiometricPrompt`, zero third-party UI libraries), faithfully matching **Telegram Android 13.3**.

![Telegram Hidden Vault Preview](Media/preview.jpg)

## Concept Overview

Privacy-sensitive chats shouldn't live behind an obvious padlock icon on the main screen. **Hidden Vault** sits invisibly behind **Archived Chats** as a literal *"second bottom"*:

1. **Hide any chat cleanly:** Long-press a chat to enter Telegram 13.3 Selection Mode (green avatar checkmark + ActionMode top bar `✕ · Mute · Archive · Delete · ⋮`), open `⋮`, and tap **Hide in Vault**.
2. **Pull down for Archive (100% unchanged):** Pulling down the chat list behaves identically to Telegram 13.3 — revealing **Archived Chats** with the vertical capsule arrow and flood ripple. Tapping it opens regular Archived Chats.
3. **Hold Archive -> Biometric -> Vault:** Long-pressing the **Archived Chats** row triggers haptic confirmation, blurs the background with GPU `RenderEffect`, and presents a biometric prompt to unlock the hidden **Vault**.
4. **Zero leakage & auto-lock:**
   - Vault chats never appear in **Search Chats**, **All / folder counts**, or **Unread** totals while locked.
   - Leaving the app (`onStop()`) or tapping **Lock** immediately collapses and re-locks the Vault.

## Demo Media & APK

- **Showcase video (1080×1080, 60fps):** [`Media/vault-showcase.mp4`](Media/vault-showcase.mp4)
- **Installable Android APK:** [`Media/TelegramVault-demo.apk`](Media/TelegramVault-demo.apk)

## Architecture & Key Files

- [`MainActivity.kt`](app/src/main/kotlin/com/tgdesign/vault/ui/MainActivity.kt) — Telegram 13.3 shell, Selection ActionMode, anchored `⋮` popup menu, Archive hold-to-Vault trigger, privacy-filtered search, and `onStop()` auto-lock.
- [`GlassBottomBarView.kt`](app/src/main/kotlin/com/tgdesign/vault/ui/GlassBottomBarView.kt) — Floating frosted Liquid Glass bottom navigation bar with live API 31+ `RenderNode` + `RenderEffect` backdrop blur and spring-driven lens highlight.
- [`TwoStageVaultLayout.kt`](app/src/main/kotlin/com/tgdesign/vault/ui/TwoStageVaultLayout.kt) — Spring-physics pull-down-for-archive container with custom Canvas vertical capsule arrow and expanding flood ripple.
- [`ChatAdapter.kt`](app/src/main/kotlin/com/tgdesign/vault/ui/ChatAdapter.kt) — 72dp `DialogCell` metrics, verified/premium/status/muted badges, top-line pinned pills, and avatar green selection checkmarks.
- [`RecordableBiometricSheet.kt`](app/src/main/kotlin/com/tgdesign/vault/ui/RecordableBiometricSheet.kt) — In-window Material 3 biometric sheet with animated fingerprint-to-checkmark morph (system `BiometricPrompt` is also wired behind `useSystemBiometricPrompt` in `MainActivity.kt`).
- [`TelegramIntegration/android/INTEGRATION.md`](TelegramIntegration/android/INTEGRATION.md) — Drop-in integration guide for upstream [`TelegramMessenger/Telegram-Android`](https://github.com/TelegramMessenger/Telegram-Android) (`DialogsActivity`, `MessagesController`, `PasscodeController`).

## Build from Source

```bash
./gradlew assembleDebug
```
