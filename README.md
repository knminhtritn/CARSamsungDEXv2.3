# VF3 Dex Open Dashboard v2.3

Bản này giữ nguyên chức năng v2.2 và sửa hoàn chỉnh quy trình GitHub Actions để build/tải APK ổn định hơn.

## Chức năng chính

- Dashboard 3D ô tô
- Chọn xe / nhập tên xe
- Đổi màu xe
- GPS speed, tọa độ, hướng, độ chính xác
- Màn app / cam lùi
- Cam lùi có guide line
- Chỉnh độ rộng guide line
- Chỉnh độ cong guide line
- Chỉnh vị trí guide line lên/xuống
- Lưu cấu hình line riêng theo từng xe, ví dụ VF 3
- Bản quyền: Kim Ngọc Minh Trí • Hàm Giang, Vĩnh Long

## Sửa trong v2.3

- Sửa file `.github/workflows/build-apk.yml`
- Không phụ thuộc đường dẫn APK cố định
- Tự tìm file `.apk` sau khi build
- Copy APK ra thư mục `apk-output`
- Upload artifact từ `apk-output/VF3DexOpenDashboard-debug.apk`

## Cấu trúc repo đúng

Sau khi upload lên GitHub, repo phải có:

```text
.github
app
README.md
build.gradle
settings.gradle
```

## Cách build

Vào GitHub:

```text
Actions / Hành động → Build APK → Run workflow
```

Khi chạy xong, vào Summary và tải artifact:

```text
VF3DexOpenDashboard-debug-apk
```
