package com.qst.smartsite.config;

import com.qst.smartsite.service.IotDataService;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * 设备通信 TCP 服务端（T-32 / 接口章节 4.2）
 * 监听 9001 端口，接收模拟设备（Node/Express TCP 客户端）按行上报的 JSON 报文，
 * 转发给 IotDataService 处理。
 *
 * 报文格式（单行 JSON，\n 结束）：
 *   {"deviceCode":"TC-001","type":"crane","data":{"load":3.2,"radius":25.0,"wind_speed":6.5,"height":45.0,"angle":120.5}}
 */
@Component
public class IotTcpServer {

    @Value("${iot.tcp-port:9001}")
    private int port;

    @Autowired
    private IotDataService iotDataService;

    private ServerSocket serverSocket;
    private final ExecutorService executor = Executors.newCachedThreadPool();
    private volatile boolean running = true;

    @PostConstruct
    public void start() {
        Thread t = new Thread(this::acceptLoop, "iot-tcp-acceptor");
        t.setDaemon(true);
        t.start();
        System.out.println("[IOT-TCP] 设备通信服务启动，监听端口 " + port);
    }

    private void acceptLoop() {
        try (ServerSocket ss = new ServerSocket(port)) {
            this.serverSocket = ss;
            while (running) {
                try {
                    Socket socket = ss.accept();
                    executor.submit(() -> handleClient(socket));
                } catch (Exception e) {
                    if (running) {
                        System.out.println("[IOT-TCP] 接受连接失败: " + e.getMessage());
                    }
                }
            }
        } catch (Exception e) {
            System.out.println("[IOT-TCP] 服务启动失败(端口 " + port + " 可能被占用): " + e.getMessage());
        }
    }

    /** 单连接处理：逐行读取报文，首条报文中的 deviceCode 用于登记连接 */
    private void handleClient(Socket socket) {
        String deviceCode = null;
        try {
            socket.setSoTimeout(0);
            BufferedReader reader = new BufferedReader(new InputStreamReader(socket.getInputStream(), "UTF-8"));
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.isBlank()) continue;
                if (deviceCode == null) {
                    deviceCode = extractDeviceCode(line);
                    iotDataService.onConnect(deviceCode);
                }
                iotDataService.handleReport(line);
            }
        } catch (Exception e) {
            // 连接异常或断开
        } finally {
            try {
                socket.close();
            } catch (Exception ignored) {
            }
            iotDataService.onDisconnect(deviceCode);
        }
    }

    private String extractDeviceCode(String line) {
        try {
            int idx = line.indexOf("\"deviceCode\"");
            if (idx < 0) return null;
            int start = line.indexOf('"', idx + 12) + 1;
            int end = line.indexOf('"', start);
            if (start > 0 && end > start) {
                return line.substring(start, end);
            }
        } catch (Exception ignored) {
        }
        return null;
    }

    @PreDestroy
    public void stop() {
        running = false;
        try {
            if (serverSocket != null) serverSocket.close();
        } catch (Exception ignored) {
        }
        executor.shutdownNow();
    }
}
