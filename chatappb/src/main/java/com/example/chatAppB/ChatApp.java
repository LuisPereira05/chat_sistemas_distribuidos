package com.example.chatAppB;

import java.awt.BorderLayout;
import java.awt.FlowLayout;
import java.awt.event.ActionListener;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.TimeoutException;

import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;

import com.rabbitmq.client.AMQP;
import com.rabbitmq.client.BuiltinExchangeType;
import com.rabbitmq.client.Channel;
import com.rabbitmq.client.Connection;
import com.rabbitmq.client.ConnectionFactory;
import com.rabbitmq.client.DeliverCallback;

public class ChatApp extends JFrame {

    // Fixed clientId from environment variable or fallback
    private static final String clientId = System.getenv("CLIENT_ID") != null
            ? System.getenv("CLIENT_ID")
            : "default-client";

    private final String queueName = "chat-queue-" + clientId;
    private static final String EXCHANGE_NAME = "chat_exchange";

    private JTextArea chatArea;
    private JTextField inputField;
    private JButton sendButton;

    private Connection connection;
    private Channel channel;

    public ChatApp() {
        super("RabbitMQ Chat App - " + clientId);

        // Setup GUI
        chatArea = new JTextArea(20, 50);
        chatArea.setEditable(false);
        JScrollPane scrollPane = new JScrollPane(chatArea);

        inputField = new JTextField(40);
        sendButton = new JButton("Send");

        JPanel inputPanel = new JPanel(new FlowLayout());
        inputPanel.add(inputField);
        inputPanel.add(sendButton);

        this.getContentPane().setLayout(new BorderLayout());
        this.getContentPane().add(scrollPane, BorderLayout.CENTER);
        this.getContentPane().add(inputPanel, BorderLayout.SOUTH);

        this.pack();
        this.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        this.setLocationRelativeTo(null);

        // Connect to RabbitMQ
        try {
            setupRabbitMQ();
        } catch (Exception e) {
            JOptionPane.showMessageDialog(this, "Failed to connect to RabbitMQ: " + e.getMessage(),
                    "Error", JOptionPane.ERROR_MESSAGE);
            System.exit(1);
        }

        // Send message on button click or enter key
        ActionListener sendAction = e -> sendMessage();
        sendButton.addActionListener(sendAction);
        inputField.addActionListener(sendAction);

        // Cleanup on close
        this.addWindowListener(new WindowAdapter() {
            public void windowClosing(WindowEvent e) {
                cleanup();
            }
        });
    }

    private void setupRabbitMQ() throws IOException, TimeoutException {
        ConnectionFactory factory = new ConnectionFactory();
        String rabbitHost = System.getenv().getOrDefault("RABBIT_HOST", "localhost");
        factory.setHost(rabbitHost);
        factory.setHost("rabbitmq"); // Use "localhost" if running locally without Docker

        connection = factory.newConnection();
        channel = connection.createChannel();

        // Durable fanout exchange
        channel.exchangeDeclare(EXCHANGE_NAME, BuiltinExchangeType.FANOUT, true);

        // Declare durable queue per client, persistent messages
        channel.queueDeclare(queueName, true, false, false, null);
        channel.queueBind(queueName, EXCHANGE_NAME, "");

        DeliverCallback deliverCallback = (consumerTag, delivery) -> {
            Map<String, Object> headers = delivery.getProperties().getHeaders();
            final String sender;
            if (headers != null && headers.get("sender") != null) {
                sender = headers.get("sender").toString();
            } else {
                sender = "";
            }

            if (!sender.equals(clientId)) {
                String message = new String(delivery.getBody(), StandardCharsets.UTF_8);
                SwingUtilities.invokeLater(() -> {
                    chatArea.append("[From " + sender + "]: " + message + "\n");
                    chatArea.setCaretPosition(chatArea.getDocument().getLength());
                });
            }

            channel.basicAck(delivery.getEnvelope().getDeliveryTag(), false);
        };

        // Consume messages manually acked
        channel.basicConsume(queueName, false, deliverCallback, consumerTag -> {});

        chatArea.append("Connected as client: " + clientId + "\n");
    }

    private void sendMessage() {
        String message = inputField.getText().trim();
        if (message.isEmpty())
            return;

        try {
            AMQP.BasicProperties props = new AMQP.BasicProperties.Builder()
                    .headers(Map.of("sender", clientId))
                    .deliveryMode(2) // Persistent messages
                    .build();

            channel.basicPublish(EXCHANGE_NAME, "", props, message.getBytes(StandardCharsets.UTF_8));

            chatArea.append("[Sent]: " + message + "\n");
            chatArea.setCaretPosition(chatArea.getDocument().getLength());
            inputField.setText("");
        } catch (IOException e) {
            JOptionPane.showMessageDialog(this, "Failed to send message: " + e.getMessage(), "Error",
                    JOptionPane.ERROR_MESSAGE);
        }
    }

    private void cleanup() {
        try {
            if (channel != null && channel.isOpen())
                channel.close();
            if (connection != null && connection.isOpen())
                connection.close();
        } catch (Exception e) {
            // Ignore exceptions on close
        }
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            ChatApp app = new ChatApp();
            app.setVisible(true);
        });
    }
}