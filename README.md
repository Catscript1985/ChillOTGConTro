# ChillOTGConTro

Ứng dụng Android Kotlin (API 23+) quản lý game, nhận bàn phím/tay cầm qua Wireless debugging (ADB), và ánh xạ phím sang thao tác chạm trong game. Bản 0.1.7 không khai báo AccessibilityService.

## Tính năng

- Game hub với danh sách game cài trên máy và nút mở game.
- Hồ sơ mapping riêng theo package game.
- Editor nổi có nút chạm và joystick 4 hướng dạng vòng tròn; có thể kéo để đặt lại, chọn từng hướng để đổi phím.
- Thanh editor có **Lưu**, **Chia sẻ** keymap JSON và **Nhập keymap** từ tệp.
- Thư viện keymap: lưu preset có tên, tải một preset vào game đang chọn, chia sẻ/xóa preset và nhập JSON; có thể mở từ tab Điều khiển hoặc menu bong bóng nổi.
- Ghép nối Wireless ADB tự dò mDNS, không cần nhập IP/cổng; notification có ô nhập mã pairing 6 số.
- ADB `getevent` nhận phím HID và gửi `input tap`/`input swipe`; tiến trình chạy dưới foreground notification do người dùng khởi động.
- Bong bóng nổi có menu thêm/sửa nút, bật/tắt mapping, mở thư viện keymap và ẩn bong bóng.

## Ghép nối và sử dụng

1. Trên Android 11 trở lên, kết nối điện thoại với Wi‑Fi tin cậy, mở **Tùy chọn nhà phát triển → Wireless debugging** và bật tính năng.
2. Cho phép quyền **Thông báo** và **Thiết bị ở gần** nếu Android yêu cầu. Trong ChillOTGConTro → **Cài đặt → Bắt đầu ghép nối tự động**.
3. Mở **Pair device with pairing code** trong Wireless debugging. ChillOTG tự dò dịch vụ và gửi notification; nhập mã 6 số trong notification.
4. Chọn game, cấp quyền cửa sổ nổi và mở game. Chạm bong bóng G → **Thêm nút phím** → **+ Thành phần**.
5. Chọn **Nút chạm** để đặt một điểm rồi nhấn phím OTG; hoặc chọn **Joystick WASD**, chạm vị trí tâm vòng tròn. Joystick mặc định dùng W/A/S/D; chạm hướng cần đổi rồi dùng thao tác đổi phím trong editor nếu cần.
6. Kéo nút hoặc cả vòng joystick để căn vị trí, bấm **Lưu**. **Chia sẻ** gửi keymap JSON; **Nhập keymap** mở bộ chọn tệp. Để tái sử dụng cho game khác, mở **Lưu / tải keymap** từ tab Điều khiển hoặc menu bong bóng, lưu với tên dễ nhớ rồi chọn **Tải cho game** ở game đích.
7. Tắt mapping khi muốn dừng. Ngắt phiên ADB trong Cài đặt sẽ đóng kết nối của ứng dụng; Android có thể vẫn giữ thiết bị trong danh sách đã ghép đôi.

## Giới hạn quan trọng

Wireless ADB yêu cầu Android có Wireless debugging (thường Android 11+); USB debugging có dây không cấp quyền ADB hệ thống cho app Android đang chạy trên chính điện thoại. Tự dò dùng mDNS và có thể không hoạt động nếu ROM/router chặn mDNS. Một số ROM không cho shell đọc `getevent`.

Joystick hiện phát một **swipe ngắn 280 ms** từ tâm ra hướng tương ứng mỗi lần nhận phím nhấn/lặp. Đây không phải joystick analog liên tục/giữ chạm; cách game phản hồi phụ thuộc game và ROM. Nút chạm phát tap đơn. Phím vật lý gốc có thể đồng thời được game nhận, một số game chặn thao tác tổng hợp hoặc overlay, và app không đảm bảo tương thích với mọi trò chơi. Chỉ dùng trên thiết bị/game bạn có quyền sử dụng và tuân theo điều khoản của game.

## Cài đặt và cập nhật APK

- Bản phát hành ổn định: `ChillOTGConTro-v0.1.7.apk` trong GitHub Release `v0.1.7`.
- Bản mới giữ `applicationId` và khóa ký ổn định để cập nhật đè bản Release trước. Không cài đè APK debug do GitHub Actions ký tạm; gỡ bản debug trước (việc gỡ có thể xóa dữ liệu cục bộ).
- Nếu Android báo “Ứng dụng chưa được cài đặt”, kiểm tra tệp tải đủ, quyền **Cài ứng dụng không rõ nguồn gốc** cho trình quản lý tệp và xung đột chữ ký.
- Play Protect có thể cảnh báo APK sideload chưa được phân phối/đánh giá qua Play. Không tắt Play Protect để né cảnh báo.

## Build

```bash
./gradlew assembleDebug
```

APK debug: `app/build/outputs/apk/debug/app-debug.apk`.

Để ký Release, truyền `ANDROID_KEYSTORE_PATH`, `ANDROID_KEYSTORE_PASSWORD`, `ANDROID_KEY_PASSWORD`, `ANDROID_KEY_ALIAS` (mặc định `chillotgcontro`) bằng khóa riêng đã phát hành. Không commit keystore/mật khẩu lên repository. GitHub Actions tạo artifact debug để kiểm tra; dùng GitHub Release để cài/cập nhật bản stable.
