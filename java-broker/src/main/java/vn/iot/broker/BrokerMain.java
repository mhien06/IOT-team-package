package vn.iot.broker;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Collections;
import java.util.List;
import java.util.Properties;
import java.util.concurrent.CountDownLatch;

import io.moquette.broker.Server;
import io.moquette.broker.config.IConfig;
import io.moquette.broker.config.MemoryConfig;
import io.moquette.interception.AbstractInterceptHandler;
import io.moquette.interception.InterceptHandler;
import io.moquette.interception.messages.InterceptConnectMessage;
import io.moquette.interception.messages.InterceptConnectionLostMessage;
import io.moquette.interception.messages.InterceptDisconnectMessage;
import io.moquette.interception.messages.InterceptPublishMessage;
import io.moquette.interception.messages.InterceptSubscribeMessage;

/**
 * MQTT Broker viet bang Java su dung thu vien Moquette (May 1).
 * Lang nghe tren 0.0.0.0:1883, cho phep ket noi an danh va ghi log khi client
 * ket noi, ngat ket noi, dang ky topic hoac gui ban tin PUBLISH.
 */
public final class BrokerMain {

    private static final String DEFAULT_HOST = "0.0.0.0";
    private static final String DEFAULT_PORT = "1883";
    private static final DateTimeFormatter TIME_FORMATTER =
            DateTimeFormatter.ofPattern("HH:mm:ss").withZone(ZoneId.systemDefault());

    private BrokerMain() {
    }

    public static void main(String[] args) {
        String host = envOrDefault("MQTT_HOST", DEFAULT_HOST);
        String port = envOrDefault("MQTT_PORT", DEFAULT_PORT);

        Properties props = new Properties();
        props.setProperty(IConfig.HOST_PROPERTY_NAME, host);
        props.setProperty(IConfig.PORT_PROPERTY_NAME, port);
        props.setProperty(IConfig.ALLOW_ANONYMOUS_PROPERTY_NAME, "true");
        // Chay trong RAM de khong tao/khoa file co so du lieu tren o cung khi thuc hanh
        props.setProperty("persistence_enabled", "false");
        // Khong gui thong ke su dung Moquette hay tao file data/.moquette_uuid.
        // Khong anh huong topic telemetry cua cam bien ESP32.
        props.setProperty(IConfig.ENABLE_TELEMETRY_NAME, "false");

        IConfig config = new MemoryConfig(props);
        Server broker = new Server();
        List<? extends InterceptHandler> handlers = Collections.singletonList(new BrokerEventLogger());
        CountDownLatch shutdownLatch = new CountDownLatch(1);

        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            log("SYSTEM", "Đang dừng Java MQTT Broker...");
            broker.stopServer();
            log("SYSTEM", "Java MQTT Broker đã dừng.");
            shutdownLatch.countDown();
        }, "broker-shutdown-hook"));

        try {
            broker.startServer(config, handlers);
        } catch (IOException ex) {
            System.err.println("[LOI] Không thể khởi động Java MQTT Broker tại " + host + ":" + port
                    + " — " + ex.getMessage());
            System.err.println("Hãy kiểm tra cổng " + port + " có đang bị ứng dụng khác chiếm hay không.");
            System.exit(1);
        }

        System.out.println("============================================================");
        System.out.println("       JAVA MQTT BROKER (MOQUETTE) - MAY 1 DANG CHAY        ");
        System.out.println("============================================================");
        System.out.println("Địa chỉ lắng nghe : " + host + ":" + port);
        System.out.println("Kết nối ẩn danh   : Cho phép (allow_anonymous = true)");
        System.out.println("Lưu trữ phiên     : In-memory (persistence_enabled = false)");
        System.out.println("Nhấn Ctrl+C để dừng Broker.");
        System.out.println("------------------------------------------------------------");

        try {
            shutdownLatch.await();
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            broker.stopServer();
        }
    }

    /** Bo lang nghe su kien cua Moquette de in log client ket noi va message den. */
    private static final class BrokerEventLogger extends AbstractInterceptHandler {

        @Override
        public String getID() {
            return "iot-broker-event-logger";
        }

        @Override
        public void onConnect(InterceptConnectMessage msg) {
            log("CONNECT", "Client kết nối: " + msg.getClientID());
        }

        @Override
        public void onDisconnect(InterceptDisconnectMessage msg) {
            log("DISCONNECT", "Client ngắt kết nối: " + msg.getClientID());
        }

        @Override
        public void onConnectionLost(InterceptConnectionLostMessage msg) {
            log("LOST", "Mất kết nối từ client: " + msg.getClientID());
        }

        @Override
        public void onSubscribe(InterceptSubscribeMessage msg) {
            log("SUBSCRIBE", "Client " + msg.getClientID()
                    + " đăng ký topic: " + msg.getTopicFilter());
        }

        @Override
        public void onPublish(InterceptPublishMessage msg) {
            try {
                var payloadBuffer = msg.getPayload();
                String payload = (payloadBuffer == null)
                        ? ""
                        : payloadBuffer.toString(StandardCharsets.UTF_8);
                log("PUBLISH", "Client " + msg.getClientID()
                        + " -> topic=" + msg.getTopicName()
                        + " | payload=" + payload);
            } finally {
                // Giai phong ByteBuf do Moquette cap phat de tranh ro ri bo nho
                super.onPublish(msg);
            }
        }

        @Override
        public void onSessionLoopError(Throwable error) {
            String detail = (error == null) ? "Không rõ nguyên nhân" : String.valueOf(error.getMessage());
            log("ERROR", "Lỗi session event loop: " + detail);
        }
    }

    private static void log(String tag, String message) {
        System.out.printf("[%s] [%-10s] %s%n", TIME_FORMATTER.format(Instant.now()), tag, message);
    }

    private static String envOrDefault(String name, String defaultValue) {
        String value = System.getenv(name);
        return (value == null || value.isBlank()) ? defaultValue : value.trim();
    }
}
