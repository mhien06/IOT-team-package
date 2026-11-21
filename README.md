# Dự án IoT 3 máy: Java MQTT Broker (Moquette) + Wokwi ESP32 + Java MQTT Client (Cmd & Form)

## Chạy thử trên một máy trước khi demo nhóm

Ba file ở thư mục gốc giúp chạy nhanh trên Windows:

1. Mở `demo-1-broker.bat`, chờ dòng `Địa chỉ lắng nghe : 127.0.0.1:1883`.
2. Mở `demo-2-cmd.bat`, chờ `Đã kết nối`, gõ `status`.
3. Mở `demo-3-form.bat`, kiểm tra trạng thái xanh `Đã kết nối`.

Mỗi file mở một cửa sổ; giữ Broker chạy trong suốt buổi demo. Nếu Broker đã chạy trên cổng 1883 thì không mở thêm bản thứ hai. Các file này đặt cố định địa chỉ localhost và topic mặc định để tập trên một máy. Khi chuyển sang ba máy, dùng các lệnh cấu hình IP ở phần hướng dẫn bên dưới.

**Kết nối thành công chưa có nghĩa là đã nhận cảm biến.** Form hiện `--.-` và CMD báo chưa có dữ liệu là bình thường nếu ESP32 chưa kết nối. Chỉ bấm ON/OFF mà chưa có thiết bị nhận cũng chưa chứng minh được LED hoạt động.

### Nối Wokwi vào bản demo một máy

Wokwi web vẫn dùng gateway bên ngoài máy bạn. Không điền `127.0.0.1` vào `MQTT_HOST` của ESP32 để nối với Broker Windows. Cần một TCP tunnel trỏ về `127.0.0.1:1883`, hoặc cấu hình Private Gateway phù hợp. Dán host/port thực tế vào `wokwi/sketch.ino`, rồi đưa ba file trong thư mục `wokwi` vào project ESP32. Client Java trên cùng máy vẫn dùng `tcp://127.0.0.1:1883`.

### Kịch bản tập demo (dựa trên chức năng của project)

| Bước | Thao tác | Bằng chứng cần thấy |
| --- | --- | --- |
| 1 | Giới thiệu ESP32 → Broker → Client và chiều điều khiển ngược lại | Broker Java chạy, Client báo kết nối, Serial Wokwi báo `MQTT connected` |
| 2 | Đổi DHT22 trên Wokwi thành 31.5 °C và 72% | Sau khoảng 5 giây, Form và lệnh `sensor` cùng hiện số mới; Broker ghi payload JSON |
| 3 | Gõ `on`, rồi `off` trong CMD | Broker ghi topic `/led/set`; Serial in `LED ON/OFF`; LED trên Wokwi sáng/tắt |
| 4 | Bấm `LED ON`, rồi `LED OFF` trên Form | LED trên Wokwi thay đổi tương ứng |
| 5 | Mở code và chỉ đường đi của bản tin | Xác định đúng hàm đọc cảm biến, publish, subscribe, callback và cập nhật giao diện |

Lời giới thiệu gợi ý: “Em đang chạy thử các vai trò trên cùng một laptop. ESP32 đọc DHT22 và publish JSON lên topic telemetry. Broker Java chuyển bản tin đến các Client đã subscribe. Chiều ngược lại, Client publish ON/OFF lên topic led/set, ESP32 nhận trong callback và đổi chân GPIO của LED.”

### Các chỗ cần biết trong code để giải thích

- `wokwi/sketch.ino`: `publishReadings()` đọc DHT22, tạo JSON và gọi `mqttClient.publish()`; `connectMqtt()` subscribe lệnh LED; `onMqttMessage()` nhận lệnh rồi gọi `digitalWrite()`.
- `java-broker/.../BrokerMain.java`: `broker.startServer(config, handlers)` khởi động Moquette; `BrokerEventLogger.onPublish()` chỉ ghi log bản tin, phần chuyển tiếp do thư viện Moquette xử lý.
- `java-client/.../MqttService.java`: `connectComplete()` subscribe telemetry; `messageArrived()` gọi `handleIncomingMessage()` để phân tích JSON; `publishLedCommand()` gửi lệnh LED.
- `java-client/.../CmdMain.java`: vòng `switch` nhận lệnh bàn phím; `showSensor()` hiển thị dữ liệu gần nhất.
- `java-client/.../FormMain.java`: `onTelemetryReceived()` cập nhật nhãn bằng `SwingUtilities.invokeLater`; các nút gọi hàm gửi lệnh.

Để kết thúc: gõ `quit` ở CMD, đóng Form, dừng mô phỏng Wokwi/tunnel, rồi Ctrl+C ở Broker.

---

Hệ thống mô phỏng IoT triển khai trên **3 máy tính riêng biệt**, giao tiếp trực tiếp qua giao thức **MQTT** với Broker viết bằng **Java (Moquette)** (không dùng broker công cộng, không qua server trung gian):

```text
+--------------------------+        MQTT (TCP 1883 / Tunnel)        +-------------------------------+
|   MÁY 2: Wokwi (Web)     | -------------------------------------> |   MÁY 1: Java MQTT Broker     |
|   ESP32 + DHT22 + LED    | <------------------------------------- |   Moquette (0.0.0.0:1883)     |
+--------------------------+                                        +---------------+---------------+
                                                                                    ^
                                                                                    | MQTT (Eclipse Paho)
                                                                                    | IP LAN hoặc Tunnel
                                                                                    v
                                                                    +---------------+---------------+
                                                                    |   MÁY 3: Java Client          |
                                                                    |   - Bản Cmd  (CmdMain)        |
                                                                    |   - Bản Form (FormMain Swing) |
                                                                    +-------------------------------+
```

---

## Cấu trúc thư mục

```text
IOT-team-package/
├── README.md
├── java-broker/
│   ├── pom.xml                                        # JDK 17+, Moquette 0.17, exec-maven-plugin
│   ├── start-tunnel.bat                               # Script mở TCP tunnel (ngrok tcp 1883) cho Wokwi
│   └── src/main/
│       ├── java/vn/iot/broker/BrokerMain.java         # Khởi động MQTT Broker 0.0.0.0:1883, in log sự kiện
│       └── resources/log4j.properties                 # Cấu hình mức log gọn cho Moquette
├── wokwi/
│   ├── sketch.ino                                     # Code ESP32 đọc DHT22 và điều khiển LED qua MQTT
│   ├── diagram.json                                   # Sơ đồ nối mạch ESP32 + DHT22 + LED + điện trở 220 Ohm
│   └── libraries.txt                                  # 3 thư viện Arduino cần cho Wokwi
└── java-client/
    ├── pom.xml                                        # JDK 17+, Eclipse Paho 1.2.5, exec-maven-plugin (Cmd & Form)
    └── src/main/java/vn/iot/client/
        ├── MqttService.java                           # Lớp MQTT dùng chung (connect, subscribe, publish, auto-reconnect)
        ├── CmdMain.java                               # Client dòng lệnh: sensor, on, off, status, help, quit
        └── FormMain.java                              # Client giao diện Java Swing
```

---

## Thông số MQTT dùng chung

| Thành phần | Giá trị |
| :--- | :--- |
| **Topic prefix** | `lopmang-iot/esp32-demo-01` |
| **Telemetry (Wokwi publish → Client subscribe)** | `lopmang-iot/esp32-demo-01/telemetry` |
| **Payload Telemetry (JSON)** | `{"temperature_c":28.0,"humidity_percent":65.0}` |
| **Điều khiển LED (Client publish → Wokwi subscribe)** | `lopmang-iot/esp32-demo-01/led/set` |
| **Payload LED** | `ON` hoặc `OFF` |
| **Biến môi trường của Client** | `MQTT_BROKER` (mặc định `tcp://127.0.0.1:1883`) |
| **Client ID** | Tự sinh kèm UUID ngẫu nhiên (`iot-cmd-<uuid>`, `iot-form-<uuid>`) để chạy đồng thời Cmd và Form không bị đá nhau |

---

## Xử lý vấn đề Wokwi không truy cập được IP LAN (`192.168.x.x`)

Trình mô phỏng **Wokwi** chạy trên máy chủ web (cloud), nên ESP32 ảo trong Wokwi **không nhìn thấy IP nội bộ LAN** (như `192.168.1.10`) của Máy 1. Để nối Máy 2 (Wokwi) vào Java MQTT Broker trên Máy 1, chọn 1 trong 2 cách sau:

### Cách chính (khuyên dùng): Mở TCP Tunnel trên Máy 1 cho cổng 1883

- **Dùng ngrok (có sẵn script `java-broker/start-tunnel.bat`):**
  1. Đăng ký tài khoản miễn phí tại https://ngrok.com, cài ngrok (`winget install ngrok.ngrok` hoặc tải tại https://ngrok.com/download) và thêm authtoken (chỉ làm 1 lần):
     ```powershell
     ngrok config add-authtoken <AUTHTOKEN_CUA_BAN>
     ```
  2. Chạy file `java-broker\start-tunnel.bat` (hoặc chạy trực tiếp `ngrok tcp 1883`) trên Máy 1.
  3. Đọc **host** và **port** công khai ở dòng `Forwarding`, có dạng:
     ```text
     Forwarding   tcp://0.tcp.xx.ngrok.io:12345 -> localhost:1883
     ```
     → Dán `MQTT_HOST = "0.tcp.xx.ngrok.io"` và `MQTT_PORT = 12345` vào đầu file `wokwi/sketch.ino` (**không** ghi `tcp://`).
  4. **Lưu ý quan trọng:**
     - Host ngrok là **tên miền** nên dùng được trực tiếp với thư viện `PubSubClient` trên ESP32 (không cần đổi sang IP).
     - **Mỗi lần chạy lại ngrok bản miễn phí thì host và port sẽ đổi**, phải cập nhật lại `MQTT_HOST` và `MQTT_PORT` trong `wokwi/sketch.ino`.

- **Phương án dự phòng: playit.gg** (miễn phí, không cần thẻ tín dụng, giữ địa chỉ ổn định):
  1. Tải agent tại https://playit.gg/download, chạy trên Máy 1 và liên kết tài khoản.
  2. Tạo tunnel kiểu **Custom TCP** trỏ về `127.0.0.1:1883`.
  3. Lấy `<host>` và `<port>` công khai dán vào `MQTT_HOST` và `MQTT_PORT` trong `wokwi/sketch.ino`.

- **Địa chỉ Broker cho Máy 3 (Java Client):**
  - Nếu Máy 3 **cùng mạng LAN** với Máy 1: kết nối bằng **IP LAN của Máy 1**, ví dụ `tcp://192.168.1.10:1883`.
  - Nếu Máy 3 **khác mạng** với Máy 1: dùng **cùng địa chỉ tunnel**, ví dụ `tcp://0.tcp.xx.ngrok.io:12345`.

### Cách phụ: Wokwi IoT Gateway (cần gói trả phí Wokwi Club)
Nếu có tài khoản **Wokwi Club**, cài và chạy công cụ `wokwi-gw` (Wokwi IoT Gateway) trên máy nội bộ, bật chế độ Private Gateway trên Wokwi rồi cấu hình `MQTT_HOST` và `MQTT_PORT` trỏ tới Broker trong mạng LAN.

---

## Hướng dẫn chạy hệ thống theo đúng thứ tự

### Bước 1 — Máy 1: Khởi chạy Java MQTT Broker (Moquette) + Tunnel

Yêu cầu: **JDK 17+** và **Apache Maven**.

1. Xem IPv4 LAN của Máy 1 (ví dụ `192.168.1.10`) để đưa cho Máy 3:
   ```powershell
   ipconfig
   ```
2. Mở Windows Firewall cho cổng TCP `1883` (chạy PowerShell với quyền **Administrator**):
   ```powershell
   New-NetFirewallRule -DisplayName "Java MQTT Broker 1883" -Direction Inbound -Protocol TCP -LocalPort 1883 -Action Allow
   ```
3. Khởi động **Java MQTT Broker** (lắng nghe `0.0.0.0:1883`, cho phép kết nối ẩn danh và in log sự kiện):
   ```powershell
   cd java-broker
   mvn compile exec:java
   ```
   *(Giữ nguyên cửa sổ này mở để theo dõi log `[CONNECT]`, `[SUBSCRIBE]`, `[PUBLISH]`, `[DISCONNECT]`.)*
4. Mở cửa sổ PowerShell thứ 2 trên Máy 1 (hoặc nháy đúp `java-broker\start-tunnel.bat`) để mở TCP tunnel cho Wokwi:
   ```powershell
   cd java-broker
   .\start-tunnel.bat
   ```
   *(Hoặc chạy trực tiếp `ngrok tcp 1883`; nếu không dùng ngrok thì chuyển sang phương án dự phòng `playit.gg`.)*

#### Kiểm tra Broker và Tunnel trước khi chạy Wokwi (bằng Cmd Client)
Mở một cửa sổ PowerShell trong thư mục `java-client` để chạy thử **Cmd Client** kết nối vào Broker vừa bật:

```powershell
# Test nội bộ trên Máy 1
cd java-client
$env:MQTT_BROKER = "tcp://127.0.0.1:1883"
mvn compile exec:java -Pcmd
```
1. Chờ dòng `[MQTT] Đã kết nối tới tcp://127.0.0.1:1883`.
2. Gõ `status` (thấy `ĐÃ KẾT NỐI`) rồi gõ `on` và `off`.
3. Quan sát cửa sổ **Java MQTT Broker** trên Máy 1 sẽ in ra các dòng log tương ứng:
   - `[CONNECT   ] Client kết nối: iot-cmd-...`
   - `[SUBSCRIBE ] Client iot-cmd-... đăng ký topic: lopmang-iot/esp32-demo-01/telemetry`
   - `[PUBLISH   ] Client iot-cmd-... -> topic=lopmang-iot/esp32-demo-01/led/set | payload=ON`
4. Gõ `quit` để thoát. *(Khuyên test thêm 1 lần với `$env:MQTT_BROKER = "tcp://<TUNNEL_HOST>:<TUNNEL_PORT>"` để chắc chắn tunnel đã chuyển tiếp thông suốt vào cổng 1883 của Máy 1.)*

---

### Bước 2 — Máy 2: Chạy mô phỏng Wokwi (ESP32 + DHT22 + LED)

1. Mở https://wokwi.com và tạo mới một dự án **ESP32** (Arduino).
2. Sao chép nội dung `wokwi/diagram.json` vào tab `diagram.json`.
3. Thêm 3 thư viện trong `wokwi/libraries.txt` vào tab **Library Manager** (hoặc dán vào `libraries.txt`):
   - `DHT sensor library`
   - `Adafruit Unified Sensor`
   - `PubSubClient`
4. Sao chép nội dung `wokwi/sketch.ino` vào tab `sketch.ino`, sau đó dán **host** và **port** của tunnel từ Máy 1 vào 2 hằng số ở đầu file (host ngrok là tên miền nên dùng trực tiếp được với `PubSubClient`; **nhớ cập nhật lại mỗi lần chạy lại ngrok bản miễn phí**):
   ```cpp
   constexpr char MQTT_HOST[] = "0.tcp.xx.ngrok.io"; // dán host của tunnel vào đây (không ghi tcp://)
   constexpr uint16_t MQTT_PORT = 12345;             // dán port của tunnel vào đây
   ```
5. Nhấn nút **Start Simulation** (màu xanh).
6. Đọc log trong **Serial Monitor** của Wokwi để xác nhận hoạt động:
   - `WiFi connected; IP: ...` → ESP32 đã kết nối WiFi ảo `Wokwi-GUEST`.
   - `MQTT connected` và `Subscribed: lopmang-iot/esp32-demo-01/led/set` → đã nối thành công tới Java MQTT Broker trên Máy 1 (đồng thời cửa sổ Broker cũng in log `[CONNECT]` và `[SUBSCRIBE]`).
   - `Published lopmang-iot/esp32-demo-01/telemetry -> {"temperature_c":28.0,"humidity_percent":65.0}` → cứ mỗi 5 giây gửi nhiệt độ/độ ẩm (có thể bấm vào cảm biến DHT22 trên sơ đồ để kéo thanh trượt đổi nhiệt độ/độ ẩm).
   - Khi Client gửi lệnh bật/tắt LED, Serial Monitor sẽ in `MQTT: LED ON` / `MQTT: LED OFF` và bóng LED đỏ trên mạch sẽ sáng/tắt tương ứng.

---

### Bước 3 — Máy 3: Chạy Java Client bản Cmd (dòng lệnh)

Yêu cầu: **JDK 17+** và **Apache Maven**.

Mở PowerShell trong thư mục `java-client`, đặt địa chỉ Broker của Máy 1 (thay `<IP-LAN-MAY-1>` bằng IPv4 thật từ bước `ipconfig`, hoặc dùng địa chỉ tunnel `tcp://<TUNNEL_HOST>:<TUNNEL_PORT>` nếu khác mạng) rồi chạy:

```powershell
cd java-client
$env:MQTT_BROKER = "tcp://<IP-LAN-MAY-1>:1883"
mvn compile exec:java -Pcmd
```

Các lệnh hỗ trợ trong Cmd:
- `sensor`: hiển thị nhiệt độ, độ ẩm và thời gian cập nhật gần nhất.
- `on`: gửi chuỗi `ON` lên `lopmang-iot/esp32-demo-01/led/set` để bật LED.
- `off`: gửi chuỗi `OFF` lên `lopmang-iot/esp32-demo-01/led/set` để tắt LED.
- `status`: xem trạng thái kết nối Broker, Client ID, topic và lỗi gần nhất (nếu có).
- `help`: xem lại danh sách lệnh.
- `quit`: đóng kết nối và thoát.

---

### Bước 4 — Máy 3: Chạy Java Client bản Form (Java Swing)

Mở **cửa sổ PowerShell thứ 2** trong thư mục `java-client` (có thể chạy song song cùng bản Cmd nhờ Client ID sinh UUID ngẫu nhiên):

```powershell
cd java-client
$env:MQTT_BROKER = "tcp://<IP-LAN-MAY-1>:1883"
mvn compile exec:java -Pform
```
*(Hoặc chạy bằng thuộc tính: `mvn compile exec:java -Dexec.mainClass=vn.iot.client.FormMain`)*

Trên giao diện Form:
1. Kiểm tra ô **Broker URI** (tự điền từ `MQTT_BROKER`, mặc định `tcp://127.0.0.1:1883`); có thể sửa trực tiếp rồi bấm **Connect**.
2. Quan sát trạng thái kết nối chuyển màu xanh **Đã kết nối**, nhiệt độ, độ ẩm và thời gian cập nhật tự nhảy mỗi 5 giây khi Wokwi gửi dữ liệu.
3. Bấm **LED ON** / **LED OFF** để điều khiển bóng LED trên Wokwi theo thời gian thực.

---

## Xử lý sự cố thường gặp

- **Java Broker báo lỗi cổng 1883 đã bị chiếm (`Address already in use`)**: kiểm tra có ứng dụng hoặc tiến trình Java Broker khác đang chạy trên cổng 1883 hay không (`netstat -ano | findstr :1883`), đóng tiến trình đó rồi chạy lại `mvn compile exec:java`.
- **Wokwi báo `failed (state -2); retry in 3 seconds`**: không kết nối được tới `MQTT_HOST:MQTT_PORT`. Kiểm tra lại đã điền đúng host/port mới nhất của ngrok/playit.gg chưa, không gõ kèm `tcp://` trong `MQTT_HOST`, và Java Broker trên Máy 1 đang chạy.
- **Máy 3 không kết nối được IP LAN Máy 1**: kiểm tra cả 2 máy có cùng mạng Wi-Fi/LAN không và đã mở Inbound Rule cổng TCP 1883 trên Windows Firewall của Máy 1 chưa.
- **Client báo chưa kết nối**: Client sẽ tự động báo rõ lỗi và tự thử kết nối lại mỗi 5 giây khi Broker chưa bật hoặc mất mạng.
