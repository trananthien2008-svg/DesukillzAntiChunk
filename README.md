# Desukillz Anti Chunk

Plugin cho **Folia / Paper** dùng **ProtocolLib** để **ẩn mọi thứ dưới tầng y=0** (block, hiệu ứng, sinh vật) với người chơi đang ở xa. Khi người chơi xuống gần **y=10**, khu vực bên dưới mới bắt đầu được tải dần cho họ.

Dùng cho map có khu vực ngầm sâu (dungeon, hầm, thế giới tầng dưới) để:
- Chặn hack nhìn xuyên đất (X-ray, ESP) vào khu vực ở dưới.
- Giảm dữ liệu gửi cho người chơi đang ở trên cao.

## Mục lục

- [Cách hoạt động](#cách-hoạt-động)
- [Yêu cầu](#yêu-cầu)
- [Cài đặt](#cài-đặt)
- [Lệnh và quyền](#lệnh-và-quyền)
- [Cấu hình](#cấu-hình)
- [Giới hạn đã biết](#giới-hạn-đã-biết)
- [Build từ mã nguồn](#build-từ-mã-nguồn)
- [Xử lý sự cố](#xử-lý-sự-cố)
- [Cấu trúc project](#cấu-trúc-project)

## Cách hoạt động

```
 y > 14        Người chơi ở xa    -> khu vực dưới y=0 bị ẩn
 y <= 10       Người chơi xuống gần -> khu vực dưới y=0 được tải dần (4 chunk/tick)
 y > 14 lại    Người chơi lên cao   -> khu vực dưới y=0 bị ẩn lại
```

Hai ngưỡng `reveal-y` (10) và `hide-y` (14) khác nhau để người chơi đứng ngay mép y=10 không làm khu vực bên dưới nhấp nháy liên tục.

| Thứ bị ẩn | Cách làm |
| --- | --- |
| **Block** | Cắt các chunk section nằm dưới y=0 khỏi gói chunk (thay bằng section rỗng). Block entity (rương, biển, lò...) dưới y=0 bị loại bỏ. Các gói cập nhật block, hoạt ảnh phá block, mở rương ở vùng bị ẩn bị chặn. |
| **Hiệu ứng** | Chặn hạt (particle), âm thanh, hiệu ứng thế giới và vụ nổ có toạ độ dưới y=0. |
| **Sinh vật** | Chặn gói spawn của sinh vật, vật phẩm rơi, armor stand... sinh ra dưới y=0 cùng mọi gói liên quan. Plugin nhớ lại chúng và dựng lại khi người chơi xuống gần. |

Khi người chơi đổi trạng thái, plugin gửi lại chunk theo thứ tự **gần người chơi nhất trước**, mỗi tick một ít (`chunks-per-tick`) để không gây lag. Với dịch chuyển (teleport, hồi sinh, đổi world), trạng thái được cập nhật **trước** khi chunk ở nơi đến được gửi nên khu vực bên dưới mở đúng lúc.

## Yêu cầu

| Thành phần | Phiên bản |
| --- | --- |
| Server | Folia hoặc Paper 1.20 trở lên |
| Java | 17 trở lên |
| **ProtocolLib** | **5.1.0 trở lên** (từ bản này mới hỗ trợ Folia). Hãy dùng bản mới nhất hỗ trợ đúng phiên bản Minecraft của bạn, với Minecraft mới có thể cần bản Development Build |

Plugin tự nhận diện định dạng chunk của từng phiên bản Minecraft (định dạng đã đổi ở 1.21.5 và các bản sau), không cần chọn thủ công.

## Cài đặt

1. Cài **ProtocolLib** vào `plugins/`.
2. Bỏ `DesukillzAntiChunk.jar` vào `plugins/`.
3. Khởi động server. Plugin tạo `plugins/DesukillzAntiChunk/config.yml`.
4. Sửa `worlds` trong `config.yml` thành tên world của bạn rồi gõ `/antichunk reload`.
5. Đứng trên cao (y > 14), gõ `/antichunk status` phải thấy `Đang ẩn khu vực dưới y=0`. Xuống y <= 10 phải thấy `Đang thấy khu vực dưới y=0`.

## Lệnh và quyền

| Lệnh | Mô tả | Quyền |
| --- | --- | --- |
| `/antichunk status` (hoặc `/dac`) | Xem trạng thái của bạn: độ cao, đang ẩn hay thấy, số chunk đã cắt, số sinh vật đang ẩn | `desukillz.admin` |
| `/antichunk reload` | Tải lại `config.yml` | `desukillz.admin` |

| Quyền | Tác dụng | Mặc định |
| --- | --- | --- |
| `desukillz.admin` | Dùng lệnh `/antichunk` | OP |
| `desukillz.bypass` | Luôn thấy mọi thứ dưới y=0, không bị ẩn (dành cho staff) | Không ai |

`desukillz.bypass` mặc định **không** cấp cho OP, để khi bạn test bằng tài khoản OP thì plugin vẫn hoạt động. Cấp quyền cho staff bằng LuckPerms:

```
/lp group admin permission set desukillz.bypass true
```

## Cấu hình

`plugins/DesukillzAntiChunk/config.yml`:

| Key | Mặc định | Ý nghĩa |
| --- | --- | --- |
| `worlds` | `[world]` | Các world áp dụng. Dùng `"*"` cho mọi world |
| `hide-below-y` | `0` | Mọi thứ dưới độ cao này bị ẩn. Tự làm tròn xuống bội số của 16 |
| `reveal-y` | `10` | Người chơi có y <= giá trị này thì được tải khu vực bên dưới |
| `hide-y` | `14` | Người chơi lên y > giá trị này thì khu vực bên dưới bị ẩn lại |
| `chunks-per-tick` | `4` | Số chunk gửi lại mỗi tick cho mỗi người chơi khi đổi trạng thái |
| `modules.blocks` | `true` | Ẩn block và block entity |
| `modules.effects` | `true` | Ẩn hạt, âm thanh, hiệu ứng, vụ nổ |
| `modules.entities` | `true` | Ẩn sinh vật |
| `hide-players` | `true` | Ẩn cả người chơi khác đang đứng dưới y=0 |
| `debug` | `false` | In thêm thông tin gỡ lỗi |

Sau khi sửa, gõ `/antichunk reload`.

## Giới hạn đã biết

Hãy đọc kỹ phần này để biết plugin làm được gì và không làm được gì.

- **Chưa được thử trên server thật.** Logic đọc/cắt chunk đã được kiểm thử riêng với 1600 chunk giả ở cả 4 định dạng và toàn bộ code biên dịch sạch, nhưng phần giao tiếp với ProtocolLib và Folia chưa chạy trên server thật. Nếu có lỗi, console sẽ in một lần đoạn lỗi đầy đủ và gói tin đó được để nguyên (không làm crash người chơi).
- **Sinh vật được quyết định lúc spawn cho người chơi.** Sinh vật đi từ trên xuống dưới y=0 vẫn hiện cho đến khi nó rời tầm nhìn rồi quay lại. Sinh vật đang ẩn đi lên trên y=0 vẫn bị ẩn cho tới khi người chơi xuống gần.
- **Dữ liệu ánh sáng của chunk chưa bị cắt**, nên có thể lộ mờ nhạt hình dạng khu vực dưới qua ánh sáng. Block và sinh vật thì không lộ.
- **Khu vực bị ẩn hiện là khoảng trống (không khí).** Nếu có hang khoét thông từ trên xuống dưới y=0, người chơi sẽ thấy khoảng trống thay vì nền.
- **Gửi lại chunk dùng `World#refreshChunk`** nên người chơi khác đang xem chunk đó cũng nhận lại gói chunk (nội dung của họ vẫn đúng theo trạng thái của họ, chỉ tốn thêm băng thông). Nếu khu vực bên dưới không hiện ra khi xuống y<=10, xem phần [Xử lý sự cố](#xử-lý-sự-cố).
- Chỉ hỗ trợ client Java.

## Build từ mã nguồn

Cần JDK 17+ và Maven:

```
mvn clean package
```

Kết quả: `target/DesukillzAntiChunk.jar`.

Nếu Maven báo không tìm thấy `ProtocolLib:5.3.0`, mở `pom.xml` và đổi `<version>` thành một bản có trong https://repo.dmulloy2.net/service/rest/repository/browse/releases/com/comphenix/protocol/ProtocolLib/. Số này chỉ dùng để biên dịch, không ảnh hưởng bản ProtocolLib bạn chạy trên server.

### Build bằng GitHub Actions

Repo có `.github/workflows/build.yml`. Mỗi lần push, GitHub tự build. Vào tab **Actions**, mở lần chạy mới nhất, tải artifact **DesukillzAntiChunk** ở cuối trang.

## Xử lý sự cố

| Triệu chứng | Cách xử lý |
| --- | --- |
| Plugin không bật, báo thiếu ProtocolLib | Cài ProtocolLib 5.1.0 trở lên, đúng bản hỗ trợ phiên bản Minecraft của bạn |
| Gõ `/antichunk status` thấy `Chunk: đã cắt 0` | Kiểm tra `worlds` đúng tên world và `modules.blocks: true`. Đứng trên y=14 rồi vào lại world để chunk gửi lại |
| Báo `chưa nhận diện` ở dòng định dạng chunk | Chưa có chunk nào được cắt. Nếu đã thử đúng mà vẫn vậy, bật `debug: true` và gửi log cho người viết plugin (khả năng cao là định dạng chunk của phiên bản bạn dùng khác) |
| Xuống y<=10 mà khu vực bên dưới không hiện | Bật `debug: true`, xem console có lỗi `refresh-chunk` không. Nếu có, gửi kèm tên và phiên bản server (Folia bao nhiêu) |
| Console có lỗi ở phần `blocks`, `entities`, `effects` | Gửi đoạn lỗi in ra lần đầu. Mỗi phần chạy độc lập nên một phần lỗi không làm hỏng các phần còn lại. Có thể tắt riêng phần đó trong `modules` |
| Staff muốn thấy mọi thứ | Cấp `desukillz.bypass` |
| Build báo không tìm thấy `pom.xml` | `pom.xml` và thư mục `src` phải nằm ở gốc repo |

## Cấu trúc project

```
DesukillzAntiChunk/
├── pom.xml
├── README.md
├── .github/workflows/build.yml
└── src/main/
    ├── java/me/desukillz/antichunk/
    │   ├── AntiChunkPlugin.java   # Lớp chính, đăng ký listener và lịch chạy
    │   ├── AntiChunkCommand.java  # Lệnh /antichunk
    │   ├── Settings.java          # Cấu hình
    │   ├── PlayerState.java       # Trạng thái từng người chơi
    │   ├── StateManager.java      # Ngưỡng y=10/14, hàng đợi gửi lại chunk
    │   ├── PlayerEvents.java      # Theo dõi di chuyển, dịch chuyển, hồi sinh
    │   ├── ChunkListener.java     # Ẩn block (gói chunk và gói cập nhật block)
    │   ├── ChunkEditor.java       # Tạo bản sao gói chunk đã cắt section thấp
    │   ├── SectionCodec.java      # Đọc/cắt chunk section, tự nhận diện định dạng
    │   ├── EffectListener.java    # Ẩn hạt, âm thanh, hiệu ứng, vụ nổ
    │   ├── EntityListener.java    # Ẩn và dựng lại sinh vật
    │   ├── LowEntity.java         # Dữ liệu sinh vật đang bị ẩn
    │   └── PacketUtil.java        # Tiện ích gói tin
    └── resources/
        ├── plugin.yml
        └── config.yml
```
