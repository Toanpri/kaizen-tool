# M5 Plan — Kaizen Tool

**Date:** 2026-10-16
**Status:** Approved by Kazoi ("ok triển đi")

## Scope

### 1. Bookmarks (Ghim thư mục yêu thích)
- Long-press folder → "Thêm bookmark"
- Drawer hiện section Bookmarks
- Lưu vào SharedPreferences (JSON list)
- Xóa bookmark: long-press bookmark → "Xóa"

### 2. Tìm kiếm toàn máy (Global Search)
- Nút search trên toolbar → tìm kiếm đệ quy từ /storage/emulated/0
- Chạy background thread, hiện progress
- Kết quả: list file/folder khớp tên, tap để mở vị trí

### 3. Root Browser
- Mở `/` (root filesystem) nếu có quyền
- Cần root hoặc Shizuku (xem #4)

### 4. Shizuku Integration
- Detect Shizuku có cài + đang chạy không
- Request permission qua Shizuku API
- Nếu có Shizuku: dùng để đọc /data, /system (không cần root)
- **Settings → Shizuku:** trạng thái kết nối, nút kết nối/ngắt, hướng dẫn

### 5. Copy/Cut/Paste UX mới
- Khi copy/cắt: hiện **nút paste lơ lửng** góc dưới-phải (FAB)
- Nút **back → thành ✕** để hủy thao tác
- Dialog xác nhận khi paste: "Dán N mục vào đây?" + nút Hủy
- Sau khi paste xong: nút FAB biến mất, back về bình thường

### 6. Path Bar Tap → Location Popup
- Bấm vào thanh đường dẫn → popup hiện các khu vực:
  - Trang chủ, Download, Ảnh, Nhạc, Tài liệu, Bookmarks
- Hoạt động **mọi lúc**, không chỉ khi copy/paste

### 7. Settings + About
- Menu drawer hoặc overflow → **Cài đặt**
  - Theme (sáng/tối)
  - Shizuku (trạng thái, kết nối)
  - Ngôn ngữ (Việt/Anh)
  - Xóa cache
- **Giới thiệu:** tên app, version, credit

### 8. Scroll Position Preservation
- **Vào folder:** scroll về đầu (position 0)
- **Back ra khỏi folder:** restore vị trí cuộn trước đó
- **Xóa file/folder:** giữ nguyên vị trí cuộn
- Implement: lưu `firstVisiblePosition` vào stack khi vào folder, pop khi back

## Technical Notes
- Shizuku: dùng `shizuku-api` (có thể cần thêm AIDL hoặc dùng reflection)
- Nếu không có Shizuku API sẵn: implement cơ bản qua `ShizukuService`
- FAB: `FrameLayout` overlay, custom draw hoặc ImageButton
- Scroll stack: `Stack<Integer>` trong MainActivity

## Out of Scope
- RAR support (đã drop từ M4)
- Cloud storage

## Build
- `build.sh` → `KaizenTool-m5.apk`
