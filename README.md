# ChillOTGConTro

Ứng dụng Android Kotlin (API 23+) để quản lý game, nhận bàn phím/tay cầm qua Wireless debugging (ADB), và ánh xạ phím sang thao tác chạm trong game. Bản 0.1.5 **không khai báo AccessibilityService**, nên không yêu cầu bật quyền Trợ năng.

## Tính năng

- Game hub dark-neon với thư viện game, chọn game đã cài và nút mở game.
- Hồ sơ phím riêng theo game; editor nổi để thêm, kéo, đổi và xóa điểm chạm.
- Ghép nối Wireless ADB ngay trong app: nhập IP và hai cổng, gửi notification có ô nhập mã pairing 6 số, sau đó thử kết nối ADB.
- Chế độ ánh xạ đọc sự kiện phím HID qua ADB `getevent`, rồi gửi `input tap` tại tọa độ profile; chạy dưới foreground notification do người dùng bật.
- Bong bóng nổi có menu thêm/sửa nút, bật/tắt ánh xạ và ẩn bong bóng; cài đặt có nút ngắt phiên ADB của app.
- USB OTG/HID được Android tự nhận; app quét để hiển thị thiết bị.

## Cách ghép nối và dùng

1. Trên Android 11 trở lên, kết nối điện thoại với Wi‑Fi tin cậy, mở **Tùy chọn nhà phát triển → Wireless debugging** và bật tính năng.
2. Trong ChillOTGConTro → **Cài đặt → Ghép nối Wi‑Fi ADB**, nhập IP của chính điện thoại, cổng pairing trên màn hình **Pair device with pairing code**, và cổng Wireless debugging chính. Hai cổng thường khác nhau và có thể thay đổi.
3. Cho phép thông báo nếu Android hỏi; bấm **Gửi thông báo nhập mã**, mở rộng notification và nhập mã 6 chữ số đang hiện trong Android.
4. Chờ thông báo báo đã ghép đôi/kết nối. Vào **Trò chơi**, chọn game, cấp quyền cửa sổ nổi và bấm **Chơi ngay**.
5. Trong game, chạm bong bóng G → **Thêm nút phím** → **+ Thêm nút** → chạm vị trí → bấm phím OTG. Chạm/ kéo nút để chỉnh; bật ánh xạ để dùng.
6. Muốn dừng, tắt ánh xạ trong menu nổi hoặc app. **Ngắt phiên ADB của ChillOTG** đóng kết nối của ứng dụng; Android vẫn có thể giữ thiết bị trong danh sách đã ghép đôi. Muốn thu hồi hoàn toàn, tắt Wireless debugging hoặc xóa thiết bị ghép đôi trong Developer options.

## Giới hạn quan trọng

ADB pairing chỉ hỗ trợ Android có Wireless debugging (thường Android 11+). Cổng và IP có thể đổi sau khi Wi‑Fi/ngày khởi động thay đổi. Một số hãng/ROM không cho tiến trình `shell` đọc `getevent`; khi đó app sẽ báo lỗi và tắt mapping. Android thông thường không cung cấp API công khai khác để đọc/chặn phím toàn hệ thống nếu không dùng Accessibility, quyền hệ thống hoặc root. Bản này cố ý không xin quyền Trợ năng.

Phím vật lý gốc có thể vẫn được game nhận song song với tap tổng hợp; một số game chặn thao tác chạm tổng hợp hoặc không tương thích overlay. Tính năng hiện tại gửi tap đơn, chưa hỗ trợ giữ chạm, ánh xạ chuột/camera FPS, hay đảm bảo hoạt động với mọi game/thiết bị. Chỉ dùng trên thiết bị/game bạn có quyền sử dụng và tuân theo điều khoản của game.

## Cài đặt và cập nhật APK

- Bản phát hành ổn định: tải **ChillOTGConTro-v0.1.5.apk** từ GitHub Release `v0.1.5`.
- Nếu đã cài bản release trước, bản mới giữ cùng `applicationId` và khóa ký ổn định để Android cập nhật đè. Không cài đè APK debug do GitHub Actions ký tạm; gỡ bản debug trước (việc gỡ có thể xóa dữ liệu cục bộ).
- Nếu Android báo “Ứng dụng chưa được cài đặt”, kiểm tra dung lượng/tệp tải đủ, bật **Cài ứng dụng không rõ nguồn gốc** cho trình quản lý tệp đang mở APK, và gỡ bản cùng package có chữ ký khác. Thiết bị cần Android 6.0+.
- Bản 0.1.5 đã bỏ khai báo AccessibilityService để không cần cấp quyền bị hạn chế. Google Play Protect vẫn có thể cảnh báo APK sideload vì đây là app chưa được phân phối/đánh giá qua Play; thay đổi này không bảo đảm mọi thiết bị đều bỏ cảnh báo. Không tắt Play Protect để né cảnh báo.

## Build

```bash
./gradlew assembleDebug
```

APK debug: `app/build/outputs/apk/debug/app-debug.apk`.

Để ký Release, truyền các biến môi trường `ANDROID_KEYSTORE_PATH`, `ANDROID_KEYSTORE_PASSWORD`, `ANDROID_KEY_PASSWORD`, `ANDROID_KEY_ALIAS` (mặc định `chillotgcontro`) và dùng đúng khóa riêng đã phát hành. Không commit keystore/mật khẩu lên repository. GitHub Actions tạo artifact **debug** để kiểm tra; dùng GitHub Release để cài/cập nhật bản stable.
