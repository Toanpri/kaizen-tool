# Kaizen Tool — M1 Plan: Core File Manager + Rem Theme

**Date:** 2026-10-15
**Status:** APPROVED by Kazoi — M1 in progress
**Kazoi decisions (2026-10-15):**
- Folder icon: **mẫu 2** (Rem hugging pink folder from side) — approved, in `art/folder_rem.png`
- Background: **always on** (Rem maid wallpaper behind file list)
- Dark mode: **yes from M1** (`values-night/`)
- Primary color: sakura pink `#f06292`
- M1 = file manager core only; ZIP → M2
**Goal:** A working file manager app with full Rem theme, installable as `KaizenTool-m1.apk`.

---

## 1. Project Setup

| Item | Value |
|---|---|
| Project dir | `~/workspace/kaizen-tool/` |
| Package | `com.kaizen.tool` |
| App name | Kaizen Tool |
| Language | Native Android Java (no frameworks, same approach as Starlight) |
| Build | `build.sh` → `KaizenTool-m<N>.apk` (aapt + javac + d8 + apksigner) |
| minSdk | 21 (Android 5.0) |
| targetSdk | 30 |

### Permissions (M1)
- `READ_EXTERNAL_STORAGE`, `WRITE_EXTERNAL_STORAGE`
- `MANAGE_EXTERNAL_STORAGE` (Android 11+, request via Settings intent)

---

## 2. M1 Features

### 2.1 File Browser (core)
- [ ] List files/folders in current directory (ListView/RecyclerView or custom Canvas — TBD)
- [ ] Navigate: tap folder to enter, back button / up-button to go parent
- [ ] Show: name, size (files), modified date, item count (folders)
- [ ] File type icons: folder (Rem custom), image, video, audio, apk, zip, text, default
- [ ] Sort: by name (A-Z, Z-A), by date (new-old, old-new), by size, by type
- [ ] Hidden files toggle (show/hide dotfiles)

### 2.2 File Operations
- [ ] Multi-select (long-press to start, tap to toggle, select all)
- [ ] Copy / Cut / Paste
- [ ] Delete (with confirm dialog)
- [ ] Rename (dialog with text input)
- [ ] Create new folder (dialog)
- [ ] Create new file (dialog)
- [ ] File properties (dialog: path, size, modified, permissions)

### 2.3 Rem Theme
- [ ] App icon: Rem-themed launcher icon (AI-generated, Kazoi approves first)
- [ ] Color scheme: sakura pink primary (`#f06292` family), dark/light mode TBD (M1 = light pink only)
- [ ] Background: Rem wallpaper option (toggleable in settings or always-on — TBD by Kazoi)
- [ ] **Custom folder icons: AI-generated folder with Rem** (Kazoi approves design before applying)
  - Idea: pink folder with Rem chibi peeking out
  - Applied to ALL folder items in the browser

### 2.4 Navigation & UX
- [ ] Breadcrumb or path bar showing current directory
- [ ] Quick access: Home, SD card root (if available)
- [ ] Empty state ("Thư mục trống" with Rem illustration?)

---

## 3. Out of Scope for M1 (later milestones)

- ZIP create/extract → **M2**
- 7z/TAR support → **M3**
- RAR extract → **M4**
- Bookmarks, search, root browser → **M5+**
- Dark mode → TBD

---

## 4. Technical Notes

- **UI approach:** Native Android Views (LinearLayout, ListView) for speed of development. Custom Canvas only if needed for Rem effects.
- **No external dependencies** in M1 (pure Android SDK) — keeps APK small and build simple.
- **Storage:** Use `Environment.getExternalStorageDirectory()` + Storage Access Framework fallback if needed.
- **Threading:** File operations on background thread, UI updates on main thread. Progress dialog for copy/delete of large files.
- **Art rule (Kazoi):** All visual art AI-generated, never procedural. Folder icons and app icon drawn by AI, approved by Kazoi before use.

---

## 5. Build & Test

- [ ] `build.sh` compiles, packages, signs → `KaizenTool-m1.apk`
- [ ] Install on test device, verify: browse, copy/paste, delete, rename, multi-select, sort
- [ ] Verify Rem theme applied (icon, colors, folder icons, background)
- [ ] APK size target: < 5 MB (M1 has no native libs)

---

## 6. Open Questions for Kazoi

1. **Folder icon design:** Pink folder + Rem chibi peeking out? Or Rem hugging a folder? (Mình sẽ vẽ 2-3 mẫu cho cậu chọn)
2. **Background:** Rem wallpaper behind file list (always on), or toggleable?
3. **Dark mode:** M1 only light pink, or do you want dark from the start?
