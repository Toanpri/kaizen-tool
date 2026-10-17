# Kaizen Tool — M4 Plan: UI Overhaul (ZArchiver-style)

**Date:** 2026-10-15
**Status:** IMPLEMENTED ✅ (2026-10-15)
**Output:** `KaizenTool-m4.apk` (2.0MB, v1.0-m4)
**Goal:** Redesign Kaizen Tool's UI to match ZArchiver's layout and interactions (UI only, not their code). Output: `KaizenTool-m4.apk`.

**Nguyên tắc:** Lụm UI/UX của ZArchiver, code vẫn của mình (không dính bug freeze 😆).

---

## 1. Toolbar (top)

**ZArchiver style:**
- [ ] Bên trái: nút menu ☰ (mở drawer) hoặc nút back khi trong thư mục con
- [ ] Giữa: tiêu đề hiện tên thư mục hiện tại (hoặc path ngắn gọn)
- [ ] Bên phải: các nút icon — Tìm kiếm 🔍, Sắp xếp ↕️, Chế độ xem ▦/☰, ⋮ (more)
- [ ] Màu hồng sakura, chữ trắng/đen tùy theme

## 2. Long-press context menu (giữ vào file/folder)

**ZArchiver style — menu hiện các chức năng:**
- [ ] Mở / Mở bằng...
- [ ] Giải nén tại đây / Giải nén tới...
- [ ] Nén thành...
- [ ] Sao chép / Cắt / Dán
- [ ] Xóa / Đổi tên
- [ ] Thuộc tính (properties)
- [ ] Chia sẻ (share)
- [ ] Tạo shortcut? (nếu dễ)
- Hiện dưới dạng **bottom sheet** hoặc **popup list** (kiểu ZArchiver dùng dialog list)
- Nền hồng, icon tương ứng mỗi mục

## 3. Popup menus

- [ ] Popup Sắp xếp: các tùy chọn (Tên A-Z, Tên Z-A, Ngày mới nhất, Ngày cũ nhất, Kích thước, Loại) — kiểu radio list
- [ ] Popup ⋮ (đã có, giữ nguyên)
- [ ] Tất cả popup: nền hồng, anchor đúng vị trí

## 4. Selection mode (khi chọn nhiều)

**ZArchiver style:**
- [ ] Toolbar đổi sang chế độ chọn: hiện số lượng đã chọn + các nút action
- [ ] Các nút: Sao chép, Cắt, Xóa, Nén, Chia sẻ, Chọn tất cả, Bỏ chọn
- [ ] Dạng icon trên toolbar (không phải text)

## 5. File list items

- [ ] Layout mỗi dòng: icon trái (thumbnail/icon loại file), tên file (1 dòng, ellipsize), dòng 2: kích thước • ngày sửa (file) hoặc "N mục" (folder)
- [ ] Vuốt? (ZArchiver không có vuốt, bỏ qua)
- [ ] Checkbox hiện khi ở selection mode (bên phải hoặc thay icon)

## 6. Các màn hình khác

- [ ] Drawer menu trái (nếu làm): các mục Home, Download, Ảnh, Nhạc, Tài liệu, Bookmark...
- [ ] Dialog tiến trình nén/giải nén: thanh progress + % + tên file hiện tại + nút Hủy

---

## 7. Out of Scope

- Chức năng mới (đã có đủ từ M1-M3)
- Chỉ redesign UI/UX, không thêm tính năng

---

## 8. Lưu ý

- Tất cả màu hồng sakura + background Rem (giữ theme hiện tại)
- Dark mode vẫn hỗ trợ
- Kazoi duyệt từng phần nếu cần (đặc biệt là long-press menu)
