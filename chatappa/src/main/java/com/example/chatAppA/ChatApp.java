package com.example.chatAppA;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.TimeoutException;

import com.rabbitmq.client.AMQP;
import com.rabbitmq.client.BuiltinExchangeType;
import com.rabbitmq.client.Channel;
import com.rabbitmq.client.Connection;
import com.rabbitmq.client.ConnectionFactory;
import com.rabbitmq.client.DeliverCallback;

public class ChatApp {

    private static final String clientId = System.getenv("CLIENT_ID") != null
            ? System.getenv("CLIENT_ID")
            : "client-A";

    private static final String queueName = "chat-queue-" + clientId;
    private static final String EXCHANGE_NAME = "chat_exchange";

    private Connection connection;
    private Channel channel;

    public static void main(String[] args) {
        ChatApp app = new ChatApp();
        try {
            app.setupRabbitMQ();
            System.out.println("Conetado como: " + clientId);
            app.startConsoleChat();
        } catch (Exception e) {
            System.err.println("Erro para abrir o chat: " + e.getMessage());
            e.printStackTrace();
            System.exit(1);
        }
    }

    private void setupRabbitMQ() throws IOException, TimeoutException, InterruptedException {
        ConnectionFactory factory = new ConnectionFactory();
        String rabbitHost = System.getenv().getOrDefault("RABBIT_HOST", "localhost");
        factory.setHost(rabbitHost);

        int maxRetries = 30;
        for (int i = 1; i <= maxRetries; i++) {
            try {
                connection = factory.newConnection();
                break;
            } catch (IOException | TimeoutException e) {
                if (i == maxRetries) throw e;
                System.out.println("Esperando Conexão... (" + i + "/" + maxRetries + ")");
                Thread.sleep(1000);
            }
        }

        channel = connection.createChannel();
        channel.exchangeDeclare(EXCHANGE_NAME, BuiltinExchangeType.FANOUT, true);
        channel.queueDeclare(queueName, true, false, false, null);
        channel.queueBind(queueName, EXCHANGE_NAME, "");

        DeliverCallback deliverCallback = (consumerTag, delivery) -> {
            Map<String, Object> headers = delivery.getProperties().getHeaders();
            String sender = "";
            if (headers != null && headers.get("sender") != null) {
                sender = headers.get("sender").toString();
            }
            if (!sender.equals(clientId)) {
                String message = new String(delivery.getBody(), StandardCharsets.UTF_8);
                System.out.println("\n[De " + sender + "]: " + message);
                System.out.print("> ");
                System.out.flush();
            }
            channel.basicAck(delivery.getEnvelope().getDeliveryTag(), false);
        };

        channel.basicConsume(queueName, false, deliverCallback, consumerTag -> {});
    }

    private void startConsoleChat() throws IOException {
        BufferedReader reader = new BufferedReader(new InputStreamReader(System.in));
        System.out.println("Escreva as mensajens. (Ctrl + C para terminar o programa)");
        System.out.print("> ");

        String line;
        while ((line = reader.readLine()) != null) {
            String message = line.trim();
            if (message.isEmpty()) {
                System.out.print("> ");
                continue;
            }
            sendMessage(message);
            System.out.print("> ");
        }
        cleanup();
    }

    private void sendMessage(String message) {
        try {
            AMQP.BasicProperties props = new AMQP.BasicProperties.Builder()
                    .headers(Map.of("sender", clientId))
                    .deliveryMode(2)
                    .build();

            channel.basicPublish(EXCHANGE_NAME, "", props, message.getBytes(StandardCharsets.UTF_8));
            System.out.println("[Enviado]: " + message);
        } catch (IOException e) {
            System.err.println("Erro ao enviar mensajem: " + e.getMessage());
        }
    }

    private void cleanup() {
        try {
            if (channel != null && channel.isOpen()) channel.close();
            if (connection != null && connection.isOpen()) connection.close();
        } catch (Exception e) {

        }
        System.out.println("App fechada.");
    }
}