#include <Arduino.h>
#include <WiFi.h>
#include <PubSubClient.h>
#include <DHT.h>

// ============================================================================
// CAU HINH MQTT BROKER — DAN HOST VA PORT CUA TUNNEL VAO DAY
// ----------------------------------------------------------------------------
// Wokwi chay tren web nen KHONG truy cap duoc IP LAN (192.168.x.x).
// Tren May 1 (Broker), chay file broker/start-tunnel.bat (ngrok tcp 1883)
// hoac dung phuong an du phong playit.gg, sau do dan host va port cong khai
// vao 2 hang so ben duoi.
//
// Vi du dong Forwarding cua ngrok:
//   tcp://0.tcp.xx.ngrok.io:12345 -> localhost:1883
//   -> MQTT_HOST = "0.tcp.xx.ngrok.io"   (KHONG ghi tien to tcp://)
//   -> MQTT_PORT = 12345
//
// Luu y:
// - Host ngrok la TEN MIEN nen dung duoc truc tiep voi PubSubClient (ESP32 tu phan giai DNS).
// - Moi lan chay lai ngrok ban mien phi thi host va port SE DOI,
//   bat buoc phai cap nhat lai 2 hang so nay trong sketch.ino truoc khi mo phong!
// - Neu dung Wokwi IoT Gateway (Wokwi Club), thay bang host/port cua gateway.
// ============================================================================
constexpr char MQTT_HOST[] = "REPLACE_WITH_BROKER_TUNNEL_HOST";  // dan host cua tunnel vao day
constexpr uint16_t MQTT_PORT = 1883;                             // dan port cua tunnel vao day

// Giu nguyen topic prefix de khop voi Java Client (Cmd va Form)
constexpr char TOPIC_PREFIX[] = "lopmang-iot/esp32-demo-01";

namespace {
constexpr uint8_t DHT_PIN = 16;
constexpr uint8_t LED_PIN = 4;
constexpr uint8_t DHT_TYPE = DHT22;
constexpr char WIFI_SSID[] = "Wokwi-GUEST";
constexpr uint32_t PUBLISH_INTERVAL_MS = 5000;

char telemetryTopic[96];
char ledCommandTopic[96];
WiFiClient wifiClient;
PubSubClient mqttClient(wifiClient);
DHT dht(DHT_PIN, DHT_TYPE);
uint32_t lastPublish = 0;

// Xu ly lenh bat/tat LED nhan tu topic <prefix>/led/set
void onMqttMessage(char *topic, byte *payload, unsigned int length) {
  if (strcmp(topic, ledCommandTopic) != 0) return;

  String command;
  command.reserve(length);
  for (unsigned int i = 0; i < length; ++i) {
    command += static_cast<char>(payload[i]);
  }
  command.trim();
  command.toUpperCase();

  if (command == "ON" || command == "1") {
    digitalWrite(LED_PIN, HIGH);
    Serial.println("MQTT: LED ON");
  } else if (command == "OFF" || command == "0") {
    digitalWrite(LED_PIN, LOW);
    Serial.println("MQTT: LED OFF");
  } else {
    Serial.printf("MQTT: Bo qua lenh khong hop le: %s\n", command.c_str());
  }
}

void connectWifi() {
  if (WiFi.status() == WL_CONNECTED) return;
  Serial.printf("Connecting to WiFi %s", WIFI_SSID);
  WiFi.begin(WIFI_SSID, "", 6);
  while (WiFi.status() != WL_CONNECTED) {
    delay(300);
    Serial.print('.');
  }
  Serial.printf("\nWiFi connected; IP: %s\n", WiFi.localIP().toString().c_str());
}

void connectMqtt() {
  while (!mqttClient.connected()) {
    // Tao clientId duy nhat theo MAC cua ESP32
    String clientId = "esp32-iot-" + String(static_cast<uint32_t>(ESP.getEfuseMac()), HEX);
    Serial.printf("Connecting to MQTT %s:%u... ", MQTT_HOST, MQTT_PORT);
    if (mqttClient.connect(clientId.c_str())) {
      Serial.println("MQTT connected");
      mqttClient.subscribe(ledCommandTopic);
      Serial.printf("Subscribed: %s\n", ledCommandTopic);
    } else {
      Serial.printf("failed (state %d); retry in 3 seconds\n", mqttClient.state());
      delay(3000);
    }
  }
}

// Doc nhiet do/do am tu DHT22 va publish dang JSON len <prefix>/telemetry
void publishReadings() {
  const float humidity = dht.readHumidity();
  const float temperature = dht.readTemperature();
  if (isnan(humidity) || isnan(temperature)) {
    Serial.println("DHT22 read failed; check wiring");
    return;
  }

  char payload[96];
  snprintf(payload, sizeof(payload),
           "{\"temperature_c\":%.1f,\"humidity_percent\":%.1f}",
           temperature, humidity);

  if (mqttClient.publish(telemetryTopic, payload)) {
    Serial.printf("Published %s -> %s\n", telemetryTopic, payload);
  } else {
    Serial.println("MQTT publish failed");
  }
}
}  // namespace

void setup() {
  Serial.begin(115200);
  pinMode(LED_PIN, OUTPUT);
  digitalWrite(LED_PIN, LOW);
  dht.begin();

  snprintf(telemetryTopic, sizeof(telemetryTopic), "%s/telemetry", TOPIC_PREFIX);
  snprintf(ledCommandTopic, sizeof(ledCommandTopic), "%s/led/set", TOPIC_PREFIX);

  mqttClient.setServer(MQTT_HOST, MQTT_PORT);
  mqttClient.setCallback(onMqttMessage);

  connectWifi();
  connectMqtt();
}

void loop() {
  if (WiFi.status() != WL_CONNECTED) connectWifi();
  if (!mqttClient.connected()) connectMqtt();
  mqttClient.loop();

  const uint32_t now = millis();
  if (now - lastPublish >= PUBLISH_INTERVAL_MS) {
    lastPublish = now;
    publishReadings();
  }
}
