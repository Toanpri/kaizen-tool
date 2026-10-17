# Shizuku clean-room redo — kế hoạch làm lại từ đầu

Ngày: 2026-10-17 (sáng thứ Bảy) · Trạng thái: RESEARCH XONG, chờ Kazoi gật đầu mới build
Quy ước: plan trước khi code (luật của Kazoi), build từng bước, test trên máy sau mỗi bước.

## 1. Tình trạng hiện tại (tối 16/10)

Sau chuỗi fix mù chiều nay (5 build, 2 vòng crash), bản cuối **không còn crash**:
- `AndroidManifest.xml`: giữ `moe.shizuku.manager.permission.API_V23` (dòng 17 — ĐÚNG, không được xóa),
  `ShizukuProvider` vẫn khai báo nhưng `android:enabled="false"` (dòng 55–62, "tạm tắt để fix crash").
- `libs/`: đã có `shizuku-api-13.1.5.jar` + `shizuku-provider-13.1.5.jar` (bản mới nhất).
- `src/`: **không còn dòng code Shizuku nào** (đã gỡ sạch để hết crash).
- Notification hiện % bên phải đã fix trong bản này — **build mới không được làm mất**.

Kết luận: với provider bị disable, Shizuku server không thể gửi binder → app không hiện
trong list "ứng dụng được hỗ trợ" của Shizuku là điều HIỂN NHIÊN, không phải bug mới.
Muốn hiện list thì phải bật provider lại + code đúng chuẩn.

## 2. Timeline hôm qua (để không lặp lại)

- 10:26 UTC — Fix 1: nút Shizuku "chọc" server xin binder → Kazoi test: **vẫn không hiện** ("có có đâu, phải hiện cơ").
- 10:32 UTC — Fix 2: tắt/bật provider ép Shizuku quét lại → Kazoi: **"vẫn không hiện, lên mạng tìm hiểu đi"**.
- 10:41 UTC — Tìm ra: thiếu `moe.shizuku.manager.permission.API_V23` trong manifest
  (server chỉ gửi binder cho app khai báo quyền này) → thêm vào → Shizuku báo "Đang chạy"
  nhưng **không hiện popup xin quyền**.
- 10:48 UTC — "Bốc nguyên official Shizuku API" → **app crash ngay khi mở**.
- 10:56 UTC — Chuyển listener sang lazy → **vẫn crash**.
- 10:58 UTC — Tắt provider → **vẫn crash** → chứng tỏ crash không nằm ở provider declaration.
- 11:03 UTC — Gỡ sạch code Shizuku → hết crash. Kazoi: **"oke nay nghỉ ngơi đi, fix mãi rồi 🤝"**.

## 3. Chuẩn official đã verify (RikkaApps/Shizuku-API README, đọc 16/10)

Nguồn: https://github.com/RikkaApps/Shizuku-API/blob/master/README.md

1. Dependency: `dev.rikka.shizuku:api` + `dev.rikka.shizuku:provider` (đã có 13.1.5 trong `libs/`, khớp changelog mới nhất).
2. Manifest — đúng y mẫu official (hiện tại đã đúng, chỉ cần bật `enabled="true"`):
   ```xml
   <uses-permission android:name="moe.shizuku.manager.permission.API_V23" />
   <provider
       android:name="rikka.shizuku.ShizukuProvider"
       android:authorities="com.kaizen.tool.shizuku"
       android:multiprocess="false"
       android:enabled="true"
       android:exported="true"
       android:permission="android.permission.INTERACT_ACROSS_USERS_FULL" />
   ```
3. Code — pattern official, KHÔNG gọi Shizuku ở static block / Application.onCreate:
   - `Shizuku.addBinderReceivedListener(...)` / `addBinderDeadListener(...)` đăng ký trong
     Activity `onCreate`, gỡ trong `onDestroy`.
   - **Chỉ gọi method của `Shizuku` khi binder còn sống** (`Shizuku.pingBinder()`),
     nếu không sẽ ném `IllegalStateException` → đây là nghi phạm số 1 của vụ crash hôm qua
     (code cũ chạy ngay lúc app khởi động).
   - Xin quyền: `Shizuku.checkSelfPermission()` → nếu chưa có thì `Shizuku.requestPermission(code)`,
     nhận kết quả qua `Shizuku.addRequestPermissionResultListener(...)`.
4. Binder chỉ được server gửi **khi process của app khởi động** → sau khi cài bản mới phải
   **force-stop app rồi mở lại**, không thì Shizuku vẫn thấy app cũ.
5. Muốn app hiện trong list Shizuku: bật provider → mở app (nhận binder) → bấm nút xin quyền
   → user bấm Cho phép (trong popup của Shizuku hoặc bật toggle trong app Shizuku → Quản lý ứng dụng).

## 4. Kế hoạch build (2 stage, test máy sau mỗi stage)

**Stage A — manifest only, không code:**
Bật `android:enabled="true"` cho provider, không thêm bất kỳ code Shizuku nào.
Cài → force-stop → mở lại → kiểm tra: (a) không crash, (b) màn hình Shizuku trong app báo
binder đã nhận. Nếu crash ở stage này → vấn đề nằm ở manifest/jar, không phải code.

**Stage B — thêm nút xin quyền theo đúng sample official:**
Nút trong Cài đặt: `pingBinder()` → chết thì hiện hướng dẫn mở app Shizuku;
sống thì `checkSelfPermission()` → chưa có → `requestPermission()` + result listener.
Cài → force-stop → mở lại → bấm nút → Cho phép → mở app Shizuku → **Kaizen Tool phải hiện
trong list "ứng dụng được hỗ trợ"** (ảnh 3 Kazoi gửi hôm qua).

Tuyệt đối không copy code của ZArchiver (proprietary) — chỉ dùng official API.

## 5. Đề xuất nhắn Kazoi sáng mai

Ngắn gọn, đúng register của Gose. Nội dung: research xong, có plan 2 stage làm lại từ đầu
theo đúng chuẩn official (đã tìm ra nghi phạm crash hôm qua: gọi Shizuku trước khi binder
sống → IllegalStateException), hỏi cậu sẵn sàng thì mình build. Không tự build trước khi
cậu gật.
