# Kaizen Tool — M2 Plan: ZIP Support

**Date:** 2026-10-15
**Status:** ✅ IMPLEMENTED (build OK 2026-10-15)
**Goal:** Add ZIP create/extract to Kaizen Tool. Output: `KaizenTool-m2.apk`.

---

## 1. Features

### 1.1 Create ZIP (nén)
- [ ] Chọn 1 hoặc nhiều file/folder → menu "Nén thành ZIP"
- [ ] Dialog: tên file ZIP (mặc định `ten.zip`), chọn nơi lưu, mức nén (Store/Fast/Normal/Max)
- [ ] Hỗ trợ nén folder đệ quy (giữ cấu trúc thư mục)
- [ ] Progress dialog khi nén (có % và tên file đang nén)
- [ ] Xử lý file trùng tên (auto-rename hoặc hỏi ghi đè)

### 1.2 Extract ZIP (giải nén)
- [ ] Tap vào file `.zip` → hiện dialog "Giải nén" hoặc xem nội dung trước
- [ ] Chọn nơi giải nén (mặc định cùng thư mục)
- [ ] Progress dialog khi giải nén
- [ ] Xử lý file trùng (hỏi ghi đè / bỏ qua / auto-rename)

### 1.3 Browse ZIP (xem nội dung)
- [ ] Tap vào ZIP → mở như 1 thư mục, duyệt file bên trong
- [ ] Có thể giải nén 1 file riêng lẻ từ trong ZIP
- [ ] Breadcrumb hiển thị đang ở trong ZIP (vd: `/Download/file.zip/subfolder/`)

### 1.4 APK Install
- [ ] Tap vào file `.apk` → dialog "Cài đặt" với icon + tên app + version
- [ ] Nút "Cài đặt" → mở system installer (`ACTION_INSTALL_PACKAGE` / `ACTION_VIEW` với FileProvider)
- [ ] Xử lý quyền `REQUEST_INSTALL_PACKAGES` (Android 8+: dẫn user cấp quyền cài app không rõ nguồn)
- [ ] Hiển thị thông tin APK: tên package, version, kích thước

### 1.5 UI Integration
- [ ] Icon ZIP riêng (tông hồng, đồng bộ với các icon loại file)
- [ ] Menu ngữ cảnh: khi chọn file → "Nén thành ZIP"; khi tap ZIP → "Giải nén" / "Mở"
- [ ] Dialogs theo theme hồng (tái dùng DialogTheme từ M1)

---

## 2. Technical

- **Thư viện:** `java.util.zip` (có sẵn trong Android, không cần thêm dependency)
  - `ZipOutputStream` cho nén, `ZipFile`/`ZipInputStream` cho giải nén
- **Threading:** Nén/giải nén chạy background thread (ExecutorService), progress update lên UI thread
- **Memory:** Stream-based (không load toàn bộ file vào RAM), buffer 8KB
- **Encoding:** UTF-8 cho tên file tiếng Việt (dùng flag UTF-8 trong ZIP)

---

## 3. Out of Scope (M3+)

- 7z, TAR, RAR → M3/M4
- Mã hóa ZIP (password) → tương lai
- Chia nhỏ ZIP (split) → tương lai

---

## 4. Build & Test

- [ ] `build.sh` → `KaizenTool-m2.apk`
- [ ] Test: nén 1 file, nén nhiều file, nén folder, giải nén, xem nội dung ZIP
- [ ] Test file tên tiếng Việt có dấu
- [ ] APK size target: < 3 MB (chỉ thêm code Java, không thêm lib)
