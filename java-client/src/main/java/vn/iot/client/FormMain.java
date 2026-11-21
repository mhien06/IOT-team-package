package vn.iot.client;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.GridLayout;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;
import javax.swing.WindowConstants;
import javax.swing.border.EmptyBorder;

/**
 * Ban Client giao dien Java Swing (Form) dung Eclipse Paho MQTT ket noi truc tiep toi Mosquitto Broker.
 * Moi cap nhat giao dien tu luong MQTT deu di qua SwingUtilities.invokeLater de tranh treo UI.
 */
public final class FormMain extends JFrame {

    private static final long serialVersionUID = 1L;

    private static final DateTimeFormatter TIME_FORMATTER =
            DateTimeFormatter.ofPattern("HH:mm:ss dd/MM/yyyy").withZone(ZoneId.systemDefault());
    private static final DateTimeFormatter LOG_TIME_FORMATTER =
            DateTimeFormatter.ofPattern("HH:mm:ss").withZone(ZoneId.systemDefault());

    private static final Color COLOR_CONNECTED = new Color(22, 130, 62);
    private static final Color COLOR_DISCONNECTED = new Color(185, 38, 38);
    private static final Color COLOR_CONNECTING = new Color(186, 118, 12);

    private final JTextField brokerField;
    private final JButton connectButton;
    private final JLabel statusLabel;
    private final JLabel metaLabel;

    private final JLabel temperatureValueLabel;
    private final JLabel humidityValueLabel;
    private final JLabel updatedAtValueLabel;

    private final JButton ledOnButton;
    private final JButton ledOffButton;
    private final JLabel ledStateLabel;

    private final JTextArea logArea;

    // Luong rieng de gui lenh publish LED khong chan luong giao dien Swing (EDT)
    private final transient ExecutorService actionExecutor = Executors.newSingleThreadExecutor(runnable -> {
        Thread thread = new Thread(runnable, "form-mqtt-action");
        thread.setDaemon(true);
        return thread;
    });

    private final transient MqttService mqttService;

    public FormMain(String initialBrokerUri, String topicPrefix) {
        super("IoT MQTT Client - ESP32 DHT22 & LED (Swing Form)");

        brokerField = new JTextField(initialBrokerUri, 26);
        connectButton = new JButton("Connect");
        statusLabel = new JLabel("Trạng thái: Đang khởi tạo...");
        metaLabel = new JLabel(" ");

        temperatureValueLabel = new JLabel("--.- °C", SwingConstants.CENTER);
        humidityValueLabel = new JLabel("--.- %", SwingConstants.CENTER);
        updatedAtValueLabel = new JLabel("Chưa có dữ liệu", SwingConstants.CENTER);

        ledOnButton = new JButton("LED ON");
        ledOffButton = new JButton("LED OFF");
        ledStateLabel = new JLabel("Lệnh LED gần nhất: Chưa gửi");

        logArea = new JTextArea(7, 50);

        mqttService = new MqttService("iot-form", initialBrokerUri, topicPrefix, new MqttService.Listener() {
            @Override
            public void onStatusChanged(boolean connected, String message) {
                SwingUtilities.invokeLater(() -> {
                    updateConnectionUi(connected, message);
                    appendLog(message);
                });
            }

            @Override
            public void onTelemetryReceived(MqttService.SensorData data) {
                SwingUtilities.invokeLater(() -> {
                    temperatureValueLabel.setText(String.format("%.1f °C", data.temperatureC()));
                    humidityValueLabel.setText(String.format("%.1f %%", data.humidityPercent()));
                    updatedAtValueLabel.setText(TIME_FORMATTER.format(data.receivedAt()));
                    appendLog(String.format("Nhận dữ liệu: Nhiệt độ = %.1f °C, Độ ẩm = %.1f %%",
                            data.temperatureC(), data.humidityPercent()));
                });
            }

            @Override
            public void onError(String message) {
                SwingUtilities.invokeLater(() -> {
                    // Tranh ghi trung dong loi ket noi vi onStatusChanged da ghi
                    if (!message.startsWith("Không thể kết nối Broker")) {
                        appendLog("[LỖI] " + message);
                    }
                });
            }
        });

        buildUi();
        wireEvents();
        refreshMetaLabel();

        // Tu dong ket noi ngay khi mo form
        mqttService.connectAsync(initialBrokerUri);
    }

    private void buildUi() {
        setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);
        setMinimumSize(new Dimension(640, 500));

        JPanel root = new JPanel(new BorderLayout(12, 12));
        root.setBorder(new EmptyBorder(14, 14, 14, 14));
        setContentPane(root);

        // --- Khu vuc 1: Nhap dia chi Broker va trang thai ket noi ---
        JPanel topPanel = new JPanel(new BorderLayout(8, 6));
        topPanel.setBorder(BorderFactory.createTitledBorder("Kết nối MQTT Broker"));

        JPanel brokerRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 4));
        brokerRow.add(new JLabel("Broker URI:"));
        brokerRow.add(brokerField);
        brokerRow.add(connectButton);

        statusLabel.setFont(statusLabel.getFont().deriveFont(Font.BOLD, 13f));
        statusLabel.setForeground(COLOR_CONNECTING);
        statusLabel.setBorder(new EmptyBorder(0, 8, 2, 8));

        metaLabel.setFont(metaLabel.getFont().deriveFont(Font.PLAIN, 11.5f));
        metaLabel.setForeground(Color.DARK_GRAY);
        metaLabel.setBorder(new EmptyBorder(0, 8, 4, 8));

        JPanel statusStack = new JPanel(new GridLayout(2, 1, 2, 2));
        statusStack.add(statusLabel);
        statusStack.add(metaLabel);

        topPanel.add(brokerRow, BorderLayout.NORTH);
        topPanel.add(statusStack, BorderLayout.CENTER);
        root.add(topPanel, BorderLayout.NORTH);

        // --- Khu vuc 2: Hien thi cam bien + nut dieu khien LED ---
        JPanel centerPanel = new JPanel(new BorderLayout(10, 10));

        JPanel sensorPanel = new JPanel(new GridLayout(1, 3, 10, 10));
        sensorPanel.setBorder(BorderFactory.createTitledBorder("Dữ liệu cảm biến DHT22"));

        Font bigFont = new Font(Font.SANS_SERIF, Font.BOLD, 24);
        Font timeFont = new Font(Font.SANS_SERIF, Font.BOLD, 15);
        temperatureValueLabel.setFont(bigFont);
        temperatureValueLabel.setForeground(new Color(196, 69, 38));
        humidityValueLabel.setFont(bigFont);
        humidityValueLabel.setForeground(new Color(26, 109, 178));
        updatedAtValueLabel.setFont(timeFont);

        sensorPanel.add(createMetricCard("Nhiệt độ", temperatureValueLabel));
        sensorPanel.add(createMetricCard("Độ ẩm", humidityValueLabel));
        sensorPanel.add(createMetricCard("Cập nhật lúc", updatedAtValueLabel));

        JPanel ledPanel = new JPanel(new BorderLayout(8, 8));
        ledPanel.setBorder(BorderFactory.createTitledBorder("Điều khiển LED trên ESP32"));

        JPanel buttonRow = new JPanel(new FlowLayout(FlowLayout.CENTER, 16, 6));
        ledOnButton.setPreferredSize(new Dimension(130, 38));
        ledOffButton.setPreferredSize(new Dimension(130, 38));
        ledOnButton.setFont(ledOnButton.getFont().deriveFont(Font.BOLD, 14f));
        ledOffButton.setFont(ledOffButton.getFont().deriveFont(Font.BOLD, 14f));
        ledOnButton.setEnabled(false);
        ledOffButton.setEnabled(false);
        buttonRow.add(ledOnButton);
        buttonRow.add(ledOffButton);

        ledStateLabel.setHorizontalAlignment(SwingConstants.CENTER);
        ledStateLabel.setBorder(new EmptyBorder(0, 0, 4, 0));

        ledPanel.add(buttonRow, BorderLayout.CENTER);
        ledPanel.add(ledStateLabel, BorderLayout.SOUTH);

        centerPanel.add(sensorPanel, BorderLayout.CENTER);
        centerPanel.add(ledPanel, BorderLayout.SOUTH);
        root.add(centerPanel, BorderLayout.CENTER);

        // --- Khu vuc 3: Nhat ky su kien ---
        logArea.setEditable(false);
        logArea.setLineWrap(true);
        logArea.setWrapStyleWord(true);
        logArea.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));
        JScrollPane logScroll = new JScrollPane(logArea);
        logScroll.setBorder(BorderFactory.createTitledBorder("Nhật ký MQTT"));
        root.add(logScroll, BorderLayout.SOUTH);

        pack();
        setLocationRelativeTo(null);
    }

    private JPanel createMetricCard(String title, JLabel valueLabel) {
        JPanel card = new JPanel(new BorderLayout(4, 6));
        card.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(new Color(210, 214, 220)),
                new EmptyBorder(10, 8, 10, 8)
        ));
        JLabel titleLabel = new JLabel(title, SwingConstants.CENTER);
        titleLabel.setFont(titleLabel.getFont().deriveFont(Font.PLAIN, 13f));
        titleLabel.setForeground(Color.DARK_GRAY);
        card.add(titleLabel, BorderLayout.NORTH);
        card.add(valueLabel, BorderLayout.CENTER);
        return card;
    }

    private void wireEvents() {
        Runnable triggerConnect = () -> {
            String uri = brokerField.getText().trim();
            if (uri.isEmpty()) {
                uri = MqttService.DEFAULT_BROKER_URI;
                brokerField.setText(uri);
            }
            updateConnectionUi(false, "Đang kết nối tới " + uri + " ...");
            statusLabel.setForeground(COLOR_CONNECTING);
            mqttService.connectAsync(uri);
            refreshMetaLabel();
        };

        connectButton.addActionListener(e -> triggerConnect.run());
        brokerField.addActionListener(e -> triggerConnect.run());

        ledOnButton.addActionListener(e -> sendLedAsync("ON"));
        ledOffButton.addActionListener(e -> sendLedAsync("OFF"));

        addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                // Dong ket noi MQTT va luong nen sach se khi dong cua so
                actionExecutor.shutdownNow();
                mqttService.close();
            }
        });
    }

    private void sendLedAsync(String state) {
        actionExecutor.execute(() -> {
            try {
                mqttService.publishLedCommand(state);
                SwingUtilities.invokeLater(() -> {
                    ledStateLabel.setText("Lệnh LED gần nhất: " + state
                            + " (" + LOG_TIME_FORMATTER.format(Instant.now()) + ")");
                    appendLog("Đã gửi lệnh LED " + state + " tới " + mqttService.getLedTopic());
                });
            } catch (Exception ex) {
                SwingUtilities.invokeLater(() ->
                        appendLog("[LỖI] Không gửi được lệnh LED " + state + ": " + ex.getMessage()));
            }
        });
    }

    private void updateConnectionUi(boolean connected, String message) {
        statusLabel.setText("Trạng thái: " + message);
        if (connected) {
            statusLabel.setForeground(COLOR_CONNECTED);
        } else if (message.startsWith("Đang kết nối")) {
            statusLabel.setForeground(COLOR_CONNECTING);
        } else {
            statusLabel.setForeground(COLOR_DISCONNECTED);
        }
        ledOnButton.setEnabled(connected);
        ledOffButton.setEnabled(connected);
        refreshMetaLabel();
    }

    private void refreshMetaLabel() {
        metaLabel.setText("Client ID: " + mqttService.getClientId()
                + "  |  Telemetry: " + mqttService.getTelemetryTopic()
                + "  |  LED: " + mqttService.getLedTopic());
    }

    private void appendLog(String text) {
        String timestamp = LOG_TIME_FORMATTER.format(Instant.now());
        logArea.append("[" + timestamp + "] " + text + "\n");
        // Gioi han do dai log de khong ton RAM khi chay lau
        if (logArea.getLineCount() > 200) {
            try {
                int cutOffset = logArea.getLineEndOffset(logArea.getLineCount() - 150);
                logArea.replaceRange("", 0, cutOffset);
            } catch (Exception ignored) {
                // Bo qua neu khong cat duoc log
            }
        }
        logArea.setCaretPosition(logArea.getDocument().getLength());
    }

    public static void main(String[] args) {
        String brokerUri = (args.length > 0 && !args[0].isBlank())
                ? args[0].trim()
                : MqttService.resolveBrokerUriFromEnv();
        String topicPrefix = MqttService.resolveTopicPrefixFromEnv();

        SwingUtilities.invokeLater(() -> {
            try {
                UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
            } catch (Exception ignored) {
                // Dung giao dien Swing mac dinh neu khong ap dung duoc giao dien he thong
            }
            FormMain frame = new FormMain(brokerUri, topicPrefix);
            frame.setVisible(true);
        });
    }
}
