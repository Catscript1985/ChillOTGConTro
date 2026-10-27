# ChillOTGConTro

Ứng dụng Android Kotlin (API 26+) hỗ trợ kiểm tra thiết bị nhập USB, lưu cấu hình phím mẫu và thử ánh xạ phím thành chạm qua Accessibility Service.

## Tình trạng bản đầu

- Giao diện tiếng Việt, Material 3, trạng thái thiết bị và hướng dẫn cấp quyền.
- Cấu hình phím mẫu WASD + SPACE, chỉnh tọa độ chạm X/Y bằng thanh trượt và lưu riêng theo ba profile cục bộ.
- Accessibility Service lọc một số sự kiện phím và gửi tap đơn tại vị trí tương đối trên màn hình.
- Nút nổi có thể kéo, mở menu bật/tắt nhanh.
- Hồ sơ cục bộ mẫu và hướng dẫn ghép nối Wi‑Fi ADB qua cài đặt Android.

## Giới hạn quan trọng

Android không cho ứng dụng thông thường điều khiển `adbd`/tự ghép nối Wi‑Fi ADB, đưa con trỏ hệ thống về giữa màn hình hoặc bảo đảm Accessibility Service nhận phím/chạm trong mọi game. Game có thể chặn sự kiện tổng hợp hoặc lọc phím. Bản hiện tại chưa hỗ trợ nhấn giữ liên tục, ánh xạ chuột đầy đủ, tự phát hiện game để chọn hồ sơ, nhập/xuất hồ sơ, hay camera mouse kiểu FPS. Hãy thử trên thiết bị và trò chơi bạn sở hữu, kiểm tra điều khoản trò chơi và chỉ bật Accessibility sau khi đọc phần mô tả quyền.

## Biên dịch APK

### GitHub Actions (khuyến nghị)

Push mã lên GitHub. Workflow `Android APK` chạy `assembleDebug`; trong GitHub mở **Actions → Android APK → workflow run → Artifacts** để tải `ChillOTGConTro-apk`, bên trong là `ChillOTGConTro.apk`. APK Actions là bản debug.

### Máy có Android SDK

Cài JDK 17+, Android SDK Platform 35 và Build Tools 35.0.0, sau đó tại thư mục dự án:

```bash
./gradlew assembleDebug
```

APK debug: `app/build/outputs/apk/debug/app-debug.apk`.

Cài qua USB debugging:

```bash
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

Hoặc chép APK vào điện thoại, mở tệp và cho phép cài ứng dụng từ nguồn đó. Bản debug có thể cần gỡ trước khi cài bản ký bằng khóa khác.

## Cài đặt và khắc phục “Ứng dụng chưa được cài đặt”

Tải đủ tệp APK, kiểm tra dung lượng, mở bằng trình quản lý tệp và cho phép trình duyệt/trình quản lý tệp **Cài ứng dụng không rõ nguồn gốc**. Điện thoại cần Android 8.0 trở lên và còn đủ bộ nhớ.

Nếu trước đó đã cài APK debug từ GitHub Actions rồi Android từ chối cập nhật, nguyên nhân thường là chữ ký khác giữa các lần build. Bản phát hành `v0.1.1` dùng khóa ổn định riêng; để chuyển từ bản debug cũ sang bản này, gỡ ChillOTGConTro cũ rồi cài lại. Gỡ ứng dụng có thể xóa hồ sơ lưu cục bộ. Các bản release tiếp theo phải dùng đúng khóa phát hành đó.

## Thiết lập trên điện thoại

1. Cài APK và mở ChillOTGConTro.
2. Cắm bàn phím/chuột bằng USB OTG, bấm **Quét lại**.
3. Mở **Cài đặt Trợ năng**, bật dịch vụ ChillOTG. Android sẽ hiện hộp cảnh báo quyền hệ thống.
4. Cấp quyền **Hiển thị trên ứng dụng khác** nếu muốn nút nổi; sau đó bấm **Bật / tắt nút nổi**.
5. Chọn bật ánh xạ, mở game và kiểm tra thử. Kết quả thay đổi theo phiên bản Android, thiết bị và game.

## Tạo bản release

Release cần keystore riêng và ký APK/AAB. Không commit keystore hay mật khẩu vào repository.
