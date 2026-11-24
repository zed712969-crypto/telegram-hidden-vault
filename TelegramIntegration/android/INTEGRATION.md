# Integrating Hidden Vault into Telegram-Android

This prototype is structured to map directly onto [`TelegramMessenger/Telegram-Android`](https://github.com/TelegramMessenger/Telegram-Android) classes (`TMessagesProj/src/main/java/org/telegram/...`):

## 1. Dialog Storage (`MessagesController` / `MessagesStorage`)
- Add a hidden folder/flag (`Dialog.hiddenInVault` or reserved `folderId = 2`) persisted in SQLite (`dialogs` table).
- Filter `hiddenInVault` dialogs out of:
  - `MessagesController.getDialogs(folderId)` for `folderId == 0` and `folderId == 1`
  - `DialogsSearchAdapter` / global & local search queries while `VaultController.isUnlocked == false`
  - `NotificationsController` unread badge counters and `GlassTabsView` totals while locked.

## 2. Selection ActionMode & Overflow Menu (`DialogsActivity`)
- In `DialogsActivity.showActionModeMenu()` / `ActionBarMenuItem` overflow popup:
  - Add `"Hide in Vault"` (or `"Remove from Vault"` when `dialogsType == DIALOGS_TYPE_VAULT`).
  - Route selected `dialogIds` via `VaultController.moveToVault(selectedDialogIds)` or `restoreFromVault(...)`.

## 3. «Second Bottom» Trigger on `Archived Chats` (`DialogsAdapter` / `DialogCell`)
- In `DialogsActivity`'s `RecyclerListView.OnItemLongClickListener`:
  - Detect the pinned `DialogCell` row with `folder_id == 1` (`Archived Chats`).
  - Fire `HapticFeedbackConstants.CONFIRM`, apply `RenderEffect.createBlurEffect(20f, 20f, Shader.TileMode.CLAMP)` on the fragment root container (API 31+), and launch `BiometricPrompt` / `PasscodeActivity`.
  - On authentication success, present `DialogsActivity` in Vault mode (`dialogsType = DIALOGS_TYPE_VAULT`) with `Lock` action button and `onPause()` / `ApplicationLoader` background auto-lock.

## 4. Floating Glass Bar Backdrop (`GlassTabsView`)
- Mirror the scrolling `RecyclerListView` container into a clipped `RenderNode` with `RenderEffect.createBlurEffect(...)` behind `GlassTabsView` on API 31+ (falling back to `Theme.key_glassTabBackground` on API < 31), matching the reference prototype in [`GlassBottomBarView.kt`](../../app/src/main/kotlin/com/tgdesign/vault/ui/GlassBottomBarView.kt).
