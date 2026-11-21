package vn.iot.client;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

import org.eclipse.paho.client.mqttv3.IMqttDeliveryToken;
import org.eclipse.paho.client.mqttv3.MqttCallbackExtended;
import org.eclipse.paho.client.mqttv3.MqttClient;
import org.eclipse.paho.client.mqttv3.MqttConnectOptions;
import org.eclipse.paho.client.mqttv3.MqttException;
import org.eclipse.paho.client.mqttv3.MqttMessage;
import org.eclipse.paho.client.mqttv3.persist.MemoryPersistence;

/**
 * Lop dich vu MQTT dung chung cho ca CmdMain va FormMain.
 * Ho tro ket noi truc tiep toi Mosquitto Broker qua Eclipse Paho,
 * tu dong ket noi lai khi Broker chua bat hoac bi mat mang,
 * subscribe du lieu cam bien va publish lenh dieu khien LED.
 */
public final class MqttService implements AutoCloseable {

    public static final String DEFAULT_BROKER_URI = "tcp://127.0.0.1:1883";
    public static final String DEFAULT_TOPIC_PREFIX = "lopmang-iot/esp32-demo-01";
    private static final int RECONNECT_DELAY_SECONDS = 5;

    /** Du lieu cam bien DHT22 moi nhat nhan duoc tu ESP32. */
    public record SensorData(double temperatureC, double humidityPercent, Instant receivedAt, String rawPayload) {
    }

    /** Callback thong bao trang thai va du lieu ve Cmd hoac Swing Form. */
    public interface Listener {
        default void onStatusChanged(boolean connected, String message) {
        }

        default void onTelemetryReceived(SensorData data) {
        }

        default void onError(String message) {
        }
    }

    private final String clientPrefix;
    private final String topicPrefix;
    private final String telemetryTopic;
    private final String ledTopic;
    private final Listener listener;

    private final ScheduledExecutorService worker = Executors.newSingleThreadScheduledExecutor(runnable -> {
        Thread thread = new Thread(runnable, "mqtt-service-worker");
        thread.setDaemon(true);
        return thread;
    });

    private final AtomicBoolean closed = new AtomicBoolean(false);
    private final AtomicLong connectionGeneration = new AtomicLong(0);
    private final AtomicReference<SensorData> latestData = new AtomicReference<>();
    private final AtomicReference<String> lastError = new AtomicReference<>("");
    private final AtomicReference<String> lastLedCommand = new AtomicReference<>("Chưa gửi");

    private volatile String brokerUri;
    private volatile String clientId;
    private volatile MqttClient client;

    public MqttService(String clientPrefix, String brokerUri, String topicPrefix, Listener listener) {
        this.clientPrefix = (clientPrefix == null || clientPrefix.isBlank()) ? "iot-client" : clientPrefix.trim();
        this.brokerUri = normalizeBrokerUri(brokerUri);
        this.topicPrefix = normalizeTopicPrefix(topicPrefix);
        this.telemetryTopic = this.topicPrefix + "/telemetry";
        this.ledTopic = this.topicPrefix + "/led/set";
        this.listener = listener == null ? new Listener() { } : listener;
        this.clientId = generateClientId(this.clientPrefix);
    }

    /** Doc dia chi Broker tu bien moi truong MQTT_BROKER, mac dinh tcp://127.0.0.1:1883. */
    public static String resolveBrokerUriFromEnv() {
        String env = System.getenv("MQTT_BROKER");
        return (env == null || env.isBlank()) ? DEFAULT_BROKER_URI : normalizeBrokerUri(env);
    }

    /** Doc topic prefix tu bien moi truong MQTT_TOPIC_PREFIX, mac dinh lopmang-iot/esp32-demo-01. */
    public static String resolveTopicPrefixFromEnv() {
        String env = System.getenv("MQTT_TOPIC_PREFIX");
        return (env == null || env.isBlank()) ? DEFAULT_TOPIC_PREFIX : normalizeTopicPrefix(env);
    }

    /** Bat dau ket noi (hoac doi sang dia chi Broker moi) tren luong nen de khong treo UI. */
    public void connectAsync(String newBrokerUri) {
        if (closed.get()) {
            return;
        }
        String normalizedUri = normalizeBrokerUri(newBrokerUri);
        long generation = connectionGeneration.incrementAndGet();
        this.brokerUri = normalizedUri;
        // Tao Client ID duy nhat co UUID ngau nhien de Cmd va Form chay song song khong bi da nhau
        this.clientId = generateClientId(this.clientPrefix);

        worker.execute(() -> {
            if (closed.get() || generation != connectionGeneration.get()) {
                return;
            }
            closeCurrentClientQuietly();
            attemptConnect(generation, normalizedUri, this.clientId);
        });
    }

    /** Ket noi toi dia chi Broker hien tai. */
    public void connectAsync() {
        connectAsync(this.brokerUri);
    }

    private void attemptConnect(long generation, String targetUri, String targetClientId) {
        if (closed.get() || generation != connectionGeneration.get()) {
            return;
        }

        notifyStatus(false, "Đang kết nối tới " + targetUri + " ...");
        MqttClient candidate = null;
        try {
            // Dung MemoryPersistence de tranh tao file khoa (.lck) trong thu muc lam viec
            candidate = new MqttClient(targetUri, targetClientId, new MemoryPersistence());
            final MqttClient activeCandidate = candidate;
            activeCandidate.setCallback(new MqttCallbackExtended() {
                @Override
                public void connectComplete(boolean reconnect, String serverURI) {
                    if (closed.get() || generation != connectionGeneration.get()) {
                        return;
                    }
                    // Dang ky lai topic moi khi ket noi thanh cong hoac tu reconnect (vi cleanSession = true)
                    try {
                        activeCandidate.subscribe(telemetryTopic, 0);
                        lastError.set("");
                        String msg = reconnect
                                ? "Đã tự động kết nối lại tới " + serverURI
                                : "Đã kết nối tới " + serverURI;
                        notifyStatus(true, msg);
                    } catch (MqttException ex) {
                        String err = "Kết nối được nhưng subscribe thất bại: " + ex.getMessage();
                        lastError.set(err);
                        notifyError(err);
                    }
                }

                @Override
                public void connectionLost(Throwable cause) {
                    if (closed.get() || generation != connectionGeneration.get()) {
                        return;
                    }
                    String reason = (cause == null || cause.getMessage() == null)
                            ? "Mất kết nối tới Broker"
                            : cause.getMessage();
                    lastError.set(reason);
                    notifyStatus(false, "Mất kết nối (" + reason + "). Đang tự động kết nối lại...");
                }

                @Override
                public void messageArrived(String topic, MqttMessage message) {
                    if (closed.get() || generation != connectionGeneration.get()) {
                        return;
                    }
                    handleIncomingMessage(topic, message);
                }

                @Override
                public void deliveryComplete(IMqttDeliveryToken token) {
                    // Khong can xu ly them voi QoS 0
                }
            });

            MqttConnectOptions options = new MqttConnectOptions();
            options.setCleanSession(true);
            options.setAutomaticReconnect(true);
            options.setConnectionTimeout(5);
            options.setKeepAliveInterval(30);

            activeCandidate.connect(options);
            // Kiem tra lai generation phong truong hop nguoi dung da doi broker trong luc dang connect
            if (closed.get() || generation != connectionGeneration.get()) {
                closeClientQuietly(activeCandidate);
                return;
            }
            this.client = activeCandidate;
        } catch (Exception ex) {
            closeClientQuietly(candidate);
            if (closed.get() || generation != connectionGeneration.get()) {
                return;
            }
            String detail = rootCauseMessage(ex);
            String errMsg = "Không thể kết nối Broker (" + targetUri + "): " + detail
                    + ". Tự thử lại sau " + RECONNECT_DELAY_SECONDS + " giây.";
            lastError.set(detail);
            notifyStatus(false, errMsg);
            notifyError(errMsg);

            // Paho chi tu reconnect sau lan ket noi dau thanh cong, nen can tu len lich thu lai neu Broker chua chay
            worker.schedule(
                    () -> attemptConnect(generation, targetUri, targetClientId),
                    RECONNECT_DELAY_SECONDS,
                    TimeUnit.SECONDS
            );
        }
    }

    /** Gui lenh bat/tat LED ("ON" hoac "OFF") len topic <prefix>/led/set. */
    public void publishLedCommand(String state) throws MqttException {
        Objects.requireNonNull(state, "Trang thai LED khong duoc null");
        String normalized = state.trim().toUpperCase(Locale.ROOT);
        if (!"ON".equals(normalized) && !"OFF".equals(normalized)) {
            throw new IllegalArgumentException("Lệnh LED chỉ chấp nhận ON hoặc OFF.");
        }

        MqttClient activeClient = this.client;
        if (activeClient == null || !activeClient.isConnected()) {
            throw new IllegalStateException("Chưa kết nối tới MQTT Broker (" + brokerUri + ").");
        }

        MqttMessage message = new MqttMessage(normalized.getBytes(StandardCharsets.UTF_8));
        message.setQos(0);
        message.setRetained(false);
        activeClient.publish(ledTopic, message);
        lastLedCommand.set(normalized);
    }

    private void handleIncomingMessage(String topic, MqttMessage message) {
        if (!telemetryTopic.equals(topic)) {
            return;
        }
        String payload = new String(message.getPayload(), StandardCharsets.UTF_8).trim();
        try {
            SensorData parsed = parseTelemetryJson(payload);
            latestData.set(parsed);
            listener.onTelemetryReceived(parsed);
        } catch (IllegalArgumentException ex) {
            // Bo qua JSON sai dinh dang va ghi log canh bao
            String warn = "Bỏ qua gói tin JSON không hợp lệ [" + payload + "]: " + ex.getMessage();
            System.err.println("[WARN] " + warn);
            notifyError(warn);
        }
    }

    /**
     * Phan tich va kiem tra JSON telemetry co dang:
     * {"temperature_c":28.5,"humidity_percent":65.0}
     * Tu kiem tra cu phap JSON co ban de khong can them thu vien ngoai Paho.
     */
    static SensorData parseTelemetryJson(String rawPayload) {
        if (rawPayload == null || rawPayload.isEmpty()) {
            throw new IllegalArgumentException("Payload rỗng");
        }
        Map<String, Double> numericFields = parseSimpleJsonObject(rawPayload);
        Double temperature = numericFields.get("temperature_c");
        Double humidity = numericFields.get("humidity_percent");
        if (temperature == null || humidity == null) {
            throw new IllegalArgumentException("Thiếu trường số temperature_c hoặc humidity_percent");
        }
        if (!Double.isFinite(temperature) || !Double.isFinite(humidity)) {
            throw new IllegalArgumentException("Giá trị nhiệt độ hoặc độ ẩm không hợp lệ");
        }
        return new SensorData(temperature, humidity, Instant.now(), rawPayload);
    }

    /** Parser gon nhe cho doi tuong JSON phang (ho tro gia tri so, chuoi, boolean, null). */
    private static Map<String, Double> parseSimpleJsonObject(String json) {
        int len = json.length();
        int i = skipWhitespace(json, 0);
        if (i >= len || json.charAt(i) != '{') {
            throw new IllegalArgumentException("JSON phải bắt đầu bằng '{'");
        }
        i = skipWhitespace(json, i + 1);
        Map<String, Double> numbers = new HashMap<>();

        if (i < len && json.charAt(i) == '}') {
            i = skipWhitespace(json, i + 1);
            if (i != len) {
                throw new IllegalArgumentException("Có ký tự thừa sau '}'");
            }
            return numbers;
        }

        while (true) {
            // Doc ten khoa dang chuoi "..."
            if (i >= len || json.charAt(i) != '"') {
                throw new IllegalArgumentException("Thiếu dấu nháy kép mở cho khóa JSON");
            }
            int keyEnd = findStringEnd(json, i);
            String key = json.substring(i + 1, keyEnd);
            i = skipWhitespace(json, keyEnd + 1);

            if (i >= len || json.charAt(i) != ':') {
                throw new IllegalArgumentException("Thiếu dấu ':' sau khóa " + key);
            }
            i = skipWhitespace(json, i + 1);
            if (i >= len) {
                throw new IllegalArgumentException("Thiếu giá trị cho khóa " + key);
            }

            char c = json.charAt(i);
            if (c == '"') {
                int valEnd = findStringEnd(json, i);
                i = valEnd + 1;
            } else if (c == '-' || (c >= '0' && c <= '9')) {
                int start = i;
                while (i < len) {
                    char ch = json.charAt(i);
                    if (ch == ',' || ch == '}' || Character.isWhitespace(ch)) {
                        break;
                    }
                    i++;
                }
                String numToken = json.substring(start, i);
                try {
                    numbers.put(key, Double.parseDouble(numToken));
                } catch (NumberFormatException ex) {
                    throw new IllegalArgumentException("Số không hợp lệ cho khóa " + key + ": " + numToken);
                }
            } else if (json.startsWith("true", i)) {
                i += 4;
            } else if (json.startsWith("false", i)) {
                i += 5;
            } else if (json.startsWith("null", i)) {
                i += 4;
            } else {
                throw new IllegalArgumentException("Giá trị JSON không được hỗ trợ tại vị trí " + i);
            }

            i = skipWhitespace(json, i);
            if (i >= len) {
                throw new IllegalArgumentException("Thiếu dấu '}' đóng JSON");
            }
            char sep = json.charAt(i);
            if (sep == ',') {
                i = skipWhitespace(json, i + 1);
            } else if (sep == '}') {
                i = skipWhitespace(json, i + 1);
                if (i != len) {
                    throw new IllegalArgumentException("Có ký tự thừa sau '}'");
                }
                return numbers;
            } else {
                throw new IllegalArgumentException("Mong đợi ',' hoặc '}' tại vị trí " + i);
            }
        }
    }

    private static int findStringEnd(String s, int openQuoteIndex) {
        int len = s.length();
        for (int i = openQuoteIndex + 1; i < len; i++) {
            char c = s.charAt(i);
            if (c == '\\') {
                i++; // bo qua ky tu duoc escape
            } else if (c == '"') {
                return i;
            }
        }
        throw new IllegalArgumentException("Chuỗi JSON chưa được đóng bằng dấu nháy kép");
    }

    private static int skipWhitespace(String s, int index) {
        int len = s.length();
        while (index < len && Character.isWhitespace(s.charAt(index))) {
            index++;
        }
        return index;
    }

    private void notifyStatus(boolean connected, String message) {
        listener.onStatusChanged(connected, message);
    }

    private void notifyError(String message) {
        listener.onError(message);
    }

    public boolean isConnected() {
        MqttClient activeClient = this.client;
        return activeClient != null && activeClient.isConnected();
    }

    public String getBrokerUri() {
        return brokerUri;
    }

    public String getClientId() {
        return clientId;
    }

    public String getTopicPrefix() {
        return topicPrefix;
    }

    public String getTelemetryTopic() {
        return telemetryTopic;
    }

    public String getLedTopic() {
        return ledTopic;
    }

    public SensorData getLatestData() {
        return latestData.get();
    }

    public String getLastError() {
        return lastError.get();
    }

    public String getLastLedCommand() {
        return lastLedCommand.get();
    }

    private static String generateClientId(String prefix) {
        String shortUuid = UUID.randomUUID().toString().replace("-", "").substring(0, 12);
        return prefix + "-" + shortUuid;
    }

    private static String normalizeBrokerUri(String uri) {
        if (uri == null || uri.isBlank()) {
            return DEFAULT_BROKER_URI;
        }
        String trimmed = uri.trim();
        if (!trimmed.contains("://")) {
            trimmed = "tcp://" + trimmed;
        }
        return trimmed;
    }

    private static String normalizeTopicPrefix(String prefix) {
        if (prefix == null || prefix.isBlank()) {
            return DEFAULT_TOPIC_PREFIX;
        }
        return prefix.trim().replaceAll("^/+|/+$", "");
    }

    private static String rootCauseMessage(Throwable throwable) {
        Throwable current = throwable;
        while (current.getCause() != null && current.getCause() != current) {
            current = current.getCause();
        }
        String message = current.getMessage();
        return (message == null || message.isBlank()) ? current.getClass().getSimpleName() : message;
    }

    private void closeCurrentClientQuietly() {
        MqttClient current = this.client;
        this.client = null;
        closeClientQuietly(current);
    }

    private static void closeClientQuietly(MqttClient mqttClient) {
        if (mqttClient == null) {
            return;
        }
        try {
            if (mqttClient.isConnected()) {
                mqttClient.disconnectForcibly(500, 500);
            }
        } catch (MqttException ignored) {
            // Bo qua loi khi dong ket noi
        }
        try {
            mqttClient.close(true);
        } catch (MqttException ignored) {
            // Bo qua loi khi giai phong tai nguyen
        }
    }

    @Override
    public void close() {
        if (!closed.compareAndSet(false, true)) {
            return;
        }
        connectionGeneration.incrementAndGet();
        worker.shutdownNow();
        closeCurrentClientQuietly();
    }
}

