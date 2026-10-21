# IR Remote VN

App Android điều khiển TV, đầu thu, quạt, điều hoà và máy chiếu bằng cổng phát hồng ngoại (`ConsumerIrManager`). Mã nguồn tự viết từ đầu (clean-room). App miễn phí, không quảng cáo, không cần tài khoản.

## Tính năng
- Bộ mã hoá tự viết: NEC, NECext, Samsung32, RC5/RC5X, RC6, SIRC 12/15/20, Kaseikyo/Panasonic, JVC, Pronto Hex, raw
- Dò mã từng bước: chọn loại thiết bị → hãng → gửi thử → "Thiết bị có phản ứng không?"
- Điều hoà nhớ trạng thái (bật/tắt, nhiệt độ, chế độ, quạt) và tự chọn mã khớp nhất
- Phòng, yêu thích, tự thêm/sửa nút, nhập và xuất file `.ir`
- Cảnh/macro: chuỗi nút có độ trễ
- Widget Glance (6 nút yêu thích) và ô Cài đặt nhanh (nút yêu thích đầu tiên)
- Giọng nói tiếng Việt qua SpeechRecognizer: "tắt tivi", "điều hoà 25 độ", "kênh 12", "chạy cảnh xem phim"
- Sao lưu và khôi phục bằng file JSON
- Báo rõ khi máy không có cổng phát IR

## Build
```
export ANDROID_HOME=~/android-sdk
./gradlew testDebugUnitTest assembleDebug
```
Cần JDK 17 trở lên và Android SDK 35. minSdk 24.

## Kho mã và giấy phép
`app/src/main/assets/irdb`: 48 file lấy từ [Flipper-IRDB](https://github.com/Lucaslhm/Flipper-IRDB), giấy phép **CC0-1.0**, commit `d126fb1`. Chỉ lấy các file được **thêm sau commit 2319685**, vì nội dung trước commit đó không thuộc CC0. Xem `SOURCE.txt` và `LICENSE-CC0.txt`.

## Cấu trúc
`ir/` mã hoá và đọc file `.ir` · `ac/` trạng thái điều hoà · `voice/` phân tích lệnh nói · `data/` Room và kho mã · `hw/` phát IR · `ui/` Compose (MVVM) · `widget/` widget và ô nhanh
