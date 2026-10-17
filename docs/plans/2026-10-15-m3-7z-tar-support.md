# Kaizen Tool — M3 Plan: 7z/TAR Support

**Date:** 2026-10-15
**Status:** ✅ IMPLEMENTED (2026-10-15)
**Goal:** Add 7z and TAR archive support. Output: `KaizenTool-m3.apk` (1.9MB, signed+verified).

---

## 1. Features

### 1.1 Create
- [ ] Tạo file `.7z` từ file/folder đã chọn (mức nén: Store/Fast/Normal/Max/Ultra)
- [ ] Tạo file `.tar`, `.tar.gz`, `.tgz` từ file/folder đã chọn
- [ ] Dialog chọn định dạng khi bấm "Nén" (ZIP/7z/TAR/TAR.GZ)

### 1.2 Extract
- [ ] Giải nén `.7z`, `.tar`, `.tar.gz`, `.tgz`, `.gz`, `.bz2`, `.xz`
- [ ] Xử lý file trùng (ghi đè / bỏ qua / auto-rename)

### 1.3 Browse
- [ ] Mở `.7z` như thư mục, duyệt nội dung, giải nén từng file (tái dùng UI browse ZIP từ M2)

### 1.4 UI Integration
- [ ] Icon riêng cho 7z/tar (tông hồng)
- [ ] Menu "Nén" hiện dialog chọn định dạng
- [ ] Tap vào file 7z/tar → dialog Mở/Giải nén (như ZIP)

---

## 2. Technical

- **Thư viện:** Apache Commons Compress (pure Java, ~500KB)
  - Hỗ trợ: 7z, tar, gz, bz2, xz
  - Không cần native lib → build đơn giản, APK vẫn nhẹ
  - Thêm JAR vào `libs/`, cập nhật `build.sh` để include vào dex
- **Threading:** Tái dùng pattern từ M2 (background thread + progress dialog)
- **Lưu ý 7z:** Nén 7z bằng Java chậm hơn native, nhưng đủ dùng cho file vừa và nhỏ

---

## 3. Out of Scope

- RAR (đã bỏ theo quyết định của Kazoi)
- Mã hóa/password → tương lai
- Chia nhỏ archive → tương lai

---

## 4. Build & Test

- [ ] Download `commons-compress-*.jar` vào `libs/`
- [ ] Cập nhật `build.sh` để dex kèm lib
- [ ] Test: tạo 7z, giải nén 7z, tạo/giải nén tar.gz, tên tiếng Việt
- [ ] APK size target: < 3 MB
