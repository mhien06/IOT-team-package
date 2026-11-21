package vn.iot.client;

import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.Scanner;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Ban Client dong lenh (Cmd) dung Eclipse Paho MQTT ket noi truc tiep toi Mosquitto Broker.
 * Ho tro cac lenh: sensor, on, off, status, help, quit.
 */
public final class CmdMain {

    private static final DateTimeFormatter TIME_FORMATTER =
            DateTimeFormatter.ofPattern("HH:mm:ss dd/MM/yyyy").withZone(ZoneId.systemDefault());

    private CmdMain() {
    }

    public static void main(String[] args) {
        // Cho phep truyen dia chi broker qua tham so dong lenh hoac bien moi truong MQTT_BROKER
        String brokerUri = (args.length > 0 && !args[0].isBlank())
                ? args[0].trim()
                : MqttService.resolveBrokerUriFromEnv();
        String topicPrefix = MqttService.resolveTopicPrefixFromEnv();

        AtomicBoolean firstTelemetryNotified = new AtomicBoolean(false);

        MqttService.Listener listener = new MqttService.Listener() {
            @Override
            public void onStatusChanged(boolean connected, String message) {
                System.out.println("\n[MQTT] " + message);
                System.out.print("> ");
            }

            @Override
            public void onTelemetryReceived(MqttService.SensorData data) {
                // Thong bao mot lan khi nhan duoc goi tin dau tien de khong lam troi man hinh nhap lenh
                if (firstTelemetryNotified.compareAndSet(false, true)) {
                    System.out.printf("%n[MQTT] Đã nhận dữ liệu cảm biến đầu tiên (%.1f °C, %.1f %%). Gõ 'sensor' để xem.%n> ",
                            data.temperatureC(), data.humidityPercent());
                }
            }

            @Override
            public void onError(String message) {
                // Loi ket noi da in o onStatusChanged; chi in rieng loi parse JSON hoac loi khac
                if (!message.startsWith("Không thể kết nối Broker")) {
                    System.out.println("\n[LOI] " + message);
                    System.out.print("> ");
                }
            }
        };

        try (MqttService mqtt = new MqttService("iot-cmd", brokerUri, topicPrefix, listener);
             Scanner scanner = new Scanner(System.in)) {

            System.out.println("===================================================");
            System.out.println("      JAVA IoT MQTT CLIENT (BAN DONG LENH - CMD)   ");
            System.out.println("===================================================");
            System.out.println("Broker URI : " + mqtt.getBrokerUri());
            System.out.println("Client ID  : " + mqtt.getClientId());
            System.out.println("Subscribe  : " + mqtt.getTelemetryTopic());
            System.out.println("Publish LED: " + mqtt.getLedTopic());
            printHelp();

            mqtt.connectAsync();

            while (true) {
                System.out.print("> ");
                if (!scanner.hasNextLine()) {
                    return;
                }
                String command = scanner.nextLine().trim().toLowerCase(Locale.ROOT);
                switch (command) {
                    case "sensor" -> showSensor(mqtt);
                    case "on" -> sendLedCommand(mqtt, "ON");
                    case "off" -> sendLedCommand(mqtt, "OFF");
                    case "status" -> showStatus(mqtt);
                    case "help" -> printHelp();
                    case "quit", "exit" -> {
                        System.out.println("Đang đóng kết nối MQTT và thoát...");
                        return;
                    }
                    case "" -> {
                        // Bo qua dong trong
                    }
                    default -> System.out.println("Lệnh chưa đúng. Gõ 'help' để xem danh sách lệnh.");
                }
            }
        }
    }

    private static void showSensor(MqttService mqtt) {
        MqttService.SensorData data = mqtt.getLatestData();
        if (data == null) {
            System.out.println("Chưa nhận được dữ liệu cảm biến nào.");
            System.out.println("Hãy kiểm tra Broker (Máy 1) và mô phỏng Wokwi (Máy 2) đang chạy.");
            return;
        }
        System.out.printf("Nhiệt độ          : %.1f °C%n", data.temperatureC());
        System.out.printf("Độ ẩm             : %.1f %%%n", data.humidityPercent());
        System.out.printf("Cập nhật gần nhất : %s%n", TIME_FORMATTER.format(data.receivedAt()));
    }

    private static void sendLedCommand(MqttService mqtt, String state) {
        try {
            mqtt.publishLedCommand(state);
            System.out.println("Đã gửi lệnh LED " + state + " tới topic " + mqtt.getLedTopic());
        } catch (Exception ex) {
            System.out.println("Không gửi được lệnh LED " + state + ": " + ex.getMessage());
        }
    }

    private static void showStatus(MqttService mqtt) {
        MqttService.SensorData data = mqtt.getLatestData();
        System.out.println("--- TRẠNG THÁI CLIENT ---");
        System.out.println("Kết nối Broker    : " + (mqtt.isConnected() ? "ĐÃ KẾT NỐI" : "CHƯA KẾT NỐI"));
        System.out.println("Broker URI        : " + mqtt.getBrokerUri());
        System.out.println("Client ID         : " + mqtt.getClientId());
        System.out.println("Topic Telemetry   : " + mqtt.getTelemetryTopic());
        System.out.println("Topic LED         : " + mqtt.getLedTopic());
        System.out.println("Lệnh LED gần nhất : " + mqtt.getLastLedCommand());
        System.out.println("Cập nhật cảm biến : "
                + (data == null ? "Chưa có dữ liệu" : TIME_FORMATTER.format(data.receivedAt())));
        if (!mqtt.getLastError().isBlank()) {
            System.out.println("Lỗi gần nhất      : " + mqtt.getLastError());
        }
    }

    private static void printHelp() {
        System.out.println("Các lệnh hỗ trợ:");
        System.out.println("  sensor  - Xem nhiệt độ, độ ẩm mới nhất từ DHT22");
        System.out.println("  on      - Bật LED trên ESP32 (publish ON)");
        System.out.println("  off     - Tắt LED trên ESP32 (publish OFF)");
        System.out.println("  status  - Xem trạng thái kết nối MQTT Broker");
        System.out.println("  help    - Hiện hướng dẫn các lệnh");
        System.out.println("  quit    - Thoát chương trình");
    }
}

