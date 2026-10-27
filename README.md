# ChillOTGConTro

Ứng dụng Android Kotlin (API 23+) hỗ trợ dò USB HID, quản lý thư viện game, lưu cấu hình phím riêng và ánh xạ phím thành chạm qua Accessibility Service.

## Tính năng

- Giao diện game hub dark-neon làm lại toàn bộ: hero **Chơi ngay**, thư viện game có icon, thẻ trạng thái OTG và thanh điều hướng **Trò chơi / Điều khiển / Cài đặt**.
- Tab Điều khiển gom game đang chọn, trạng thái ánh xạ, danh sách nút phím và thao tác vào game để mở editor nổi; tab Cài đặt tách riêng quyền/OTG.
- Nút quét/kết nối OTG chỉ hiển thị USB HID thật; USB tự nhận bởi Android, không cần ghép đôi kiểu Bluetooth.
- Thêm/bỏ nhiều app/game có màn hình launcher vào thư viện, chọn một game active; lưu profile riêng theo package và nút **Chơi ngay** mở game.
- Cấu hình phím mẫu WASD + SPACE; kéo điểm chạm trực tiếp trong editor nổi và profile được lưu riêng cho từng game.
- Chọn **Gán phím** rồi nhấn phím trên bàn phím OTG để thay phím gán.
- Trong game, bong bóng mở panel có **Thêm nút phím**, **Sửa/kéo nút**, **Đổi phím**, **Xóa** và **Ẩn bong bóng**.
- Editor phủ lên game: **+ Thêm nút** → chạm điểm trên màn hình → nhấn phím OTG; chạm nút để chọn, kéo để di chuyển, hoặc đổi/xóa.
- Accessibility Service lọc một số sự kiện phím và gửi tap đơn tại vị trí tương đối trên màn hình.
- Nút nổi có thể kéo; trang chính cũng có nút bật lại sau khi ẩn.
- Trước khi mở Cài đặt Trợ năng, app hiển thị disclosure riêng, yêu cầu người dùng xác nhận và mô tả dữ liệu được xử lý.
- Hồ sơ cục bộ mẫu và hướng dẫn ghép nối Wi‑Fi ADB qua cài đặt Android.

## Giới hạn quan trọng

Android không cho ứng dụng thông thường điều khiển `adbd`/tự ghép nối Wi‑Fi ADB, đưa con trỏ hệ thống về giữa màn hình hoặc bảo đảm Accessibility Service nhận phím/chạm trong mọi game. Game có thể chặn sự kiện tổng hợp hoặc lọc phím. Bản hiện tại chưa hỗ trợ giữ chạm liên tục, ánh xạ chuột đầy đủ, nhập/xuất profile, hay camera mouse kiểu FPS. Hãy thử trên thiết bị và trò chơi bạn sở hữu, kiểm tra điều khoản trò chơi và chỉ bật Accessibility sau khi đọc phần mô tả quyền.

## Biên dịch APK

### GitHub Actions (khuyến nghị)

Push mã lên GitHub. Workflow `Android APK` chạy `assembleDebug`; trong GitHub mở **Actions → Android APK → workflow run → Artifacts** để tải `ChillOTGConTro-debug-apk`, bên trong là `ChillOTGConTro-debug.apk`. Đây là bản debug ký tạm; để cài/cập nhật ổn định, dùng APK signed ở GitHub Release.

### Máy có Android SDK

Cài JDK 17+, Android SDK Platform 35 và Build Tools 35.0.0, sau đó tại thư mục dự án:

```bash
./gradlew assembleDebug
```

APK debug: `app/build/outputs/apk/debug/app-debug.apk`.

Bản release đã ký cần đặt khóa ngoài repository và truyền các biến `ANDROID_KEYSTORE_PATH`, `ANDROID_KEYSTORE_PASSWORD`, `ANDROID_KEY_PASSWORD`, `ANDROID_KEY_ALIAS` (`chillotgcontro`), sau đó chạy `./gradlew assembleRelease`. Dùng đúng khóa đã phát hành để APK mới có thể cập nhật bản cũ.

Cài qua USB debugging:

```bash
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

Hoặc chép APK vào điện thoại, mở tệp và cho phép cài ứng dụng từ nguồn đó. Bản debug có thể cần gỡ trước khi cài bản ký bằng khóa khác.

## Cài đặt và khắc phục “Ứng dụng chưa được cài đặt”

Tải đủ tệp APK, kiểm tra dung lượng, mở bằng trình quản lý tệp và cho phép trình duyệt/trình quản lý tệp **Cài ứng dụng không rõ nguồn gốc**. Điện thoại cần Android 6.0 trở lên và còn đủ bộ nhớ.

Nếu trước đó đã cài APK debug từ GitHub Actions rồi Android từ chối cập nhật, nguyên nhân thường là chữ ký khác giữa các lần build. Bản phát hành `v0.1.4` dùng cùng khóa ổn định của các release trước; để chuyển từ APK debug cũ, gỡ ChillOTGConTro cũ rồi cài lại. Gỡ ứng dụng có thể xóa hồ sơ lưu cục bộ. Các bản release tiếp theo phải dùng đúng khóa phát hành đó.

## Thiết lập trên điện thoại

1. Cài APK và mở ChillOTGConTro.
2. Vào tab **Cài đặt** hoặc khung OTG trang **Trò chơi**, cắm bàn phím/chuột HID rồi bấm **Quét**. USB HID được Android tự nhận, không cần pairing.
3. Tại trang **Trò chơi**, bấm **Thêm**, chọn game trong danh sách; chạm game để chọn và dùng **Chơi ngay** để mở.
4. Đọc disclosure Trợ năng trong app, đồng ý nếu chấp thuận, rồi bật dịch vụ ChillOTG trong Cài đặt Android.
5. Cấp quyền hiển thị trên ứng dụng khác và bật bong bóng.
6. Mở game, chạm bong bóng → **Thêm nút phím** → **+ Thêm nút** → chạm điểm cần điều khiển → nhấn phím OTG. Chạm nút để chọn, kéo để di chuyển, hoặc dùng **Đổi phím/Xóa**.
7. Bật ánh xạ trước khi chơi. Kết quả thay đổi theo phiên bản Android, thiết bị và game.

Google Play Protect có thể chặn app cài từ nguồn ngoài Play nếu manifest khai báo Accessibility. Disclosure cải thiện minh bạch và là yêu cầu quan trọng khi phân phối qua Play, nhưng không đảm bảo bỏ cảnh báo khi sideload. Không tắt bảo vệ để né cảnh báo; nhà phát triển có thể yêu cầu Google xem xét/kháng nghị nếu tin là nhận diện nhầm.

## Tạo bản release

Keystore và mật khẩu ký là bí mật: giữ riêng, sao lưu an toàn và không commit/đăng lên GitHub. Nếu mất khóa, Android không cho cập nhật đè cùng package bằng khóa mới.
