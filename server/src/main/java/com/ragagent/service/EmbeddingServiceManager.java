package com.ragagent.service;

import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.util.concurrent.TimeUnit;

/**
 * Embedding Service 生命周期管理器
 *
 * <p>Spring Boot 启动时自动拉起 Python embedding 子进程，关闭时一起关。
 * 解决"手动开两个终端"的痛点。
 *
 * <p>配置（application.yml）：
 * <pre>
 * embedding:
 *   auto-start: true               # 总开关
 *   python-path: python            # python 可执行文件
 *   script-dir: embedding-service  # 相对 server/ 的路径
 * </pre>
 *
 * <p>如果想自己手动管理 Python 服务，把 auto-start 设为 false 即可。
 */
@Component
@Order(0)  // 必须在 KnowledgeIndexer(@Order(1)) 之前完成
public class EmbeddingServiceManager implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(EmbeddingServiceManager.class);
    private static final long STARTUP_TIMEOUT_MS = 5 * 60 * 1000L;  // 5 分钟

    @Value("${embedding.auto-start:true}")
    private boolean autoStart;

    @Value("${embedding.python-path:python}")
    private String pythonPath;

    @Value("${embedding.script-dir:embedding-service}")
    private String scriptDir;

    @Value("${embedding.base-url}")
    private String baseUrl;

    private Process pythonProcess;

    @Override
    public void run(String... args) {
        if (!autoStart) {
            log.info("Embedding service auto-start disabled (embedding.auto-start=false)");
            return;
        }

        // 1. 已经在跑就跳过
        if (isServiceReachable()) {
            log.info("Embedding service already reachable at {}, skip auto-start", baseUrl);
            return;
        }

        // 2. 解析脚本目录
        Path scriptPath = Paths.get(scriptDir).toAbsolutePath().normalize();
        File scriptDirFile = scriptPath.toFile();
        if (!scriptDirFile.exists() || !scriptDirFile.isDirectory()) {
            log.error("Embedding script dir not found: {} (set embedding.script-dir to fix)", scriptPath);
            return;
        }

        // 3. 派生 Python 子进程
        int port = URI.create(baseUrl).getPort();
        if (port <= 0) port = 8001;
        String portStr = String.valueOf(port);

        log.info("Auto-starting Embedding service: {} -m uvicorn ... --port {}", pythonPath, portStr);

        try {
            ProcessBuilder pb = new ProcessBuilder(
                    pythonPath, "-m", "uvicorn", "main:app",
                    "--host", "0.0.0.0", "--port", portStr
            );
            pb.directory(scriptDirFile);
            pb.environment().put("HF_HOME", scriptPath.resolve("models").toString());
            pb.environment().put("HF_HUB_CACHE", scriptPath.resolve("models").toString());
            pb.environment().put("HF_ENDPOINT", "https://hf-mirror.com");
            pb.environment().put("PYTHONUNBUFFERED", "1");

            File logFile = new File("logs/embedding-service.log");
            logFile.getParentFile().mkdirs();
            pb.redirectErrorStream(true);
            pb.redirectOutput(ProcessBuilder.Redirect.appendTo(logFile));

            pythonProcess = pb.start();
            log.info("Embedding service spawned, PID={}, logs -> {}", pythonProcess.pid(), logFile.getAbsolutePath());
        } catch (IOException e) {
            log.error("Failed to spawn Python process: {}", e.getMessage());
            return;
        }

        // 4. 轮询等待服务就绪
        log.info("Waiting for Embedding service to be ready (timeout {}s)...", STARTUP_TIMEOUT_MS / 1000);
        long deadline = System.currentTimeMillis() + STARTUP_TIMEOUT_MS;
        int dots = 0;
        while (System.currentTimeMillis() < deadline) {
            if (isServiceReachable()) {
                log.info("Embedding service is ready!");
                return;
            }
            if (!pythonProcess.isAlive()) {
                log.error("Embedding service died during startup. Check logs/embedding-service.log");
                return;
            }
            // 进度提示（每 2 秒打一个点）
            if (++dots % 5 == 0) {
                log.info("  still waiting... ({}s)", (dots * 2));
            }
            try {
                Thread.sleep(2000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            }
        }
        log.error("Embedding service did not become ready within {}s timeout. Check logs/embedding-service.log",
                STARTUP_TIMEOUT_MS / 1000);
    }

    @PreDestroy
    public void stop() {
        if (pythonProcess != null && pythonProcess.isAlive()) {
            log.info("Stopping Embedding service, PID={}", pythonProcess.pid());
            pythonProcess.destroy();
            try {
                if (!pythonProcess.waitFor(5, TimeUnit.SECONDS)) {
                    log.warn("Embedding service did not exit in 5s, force killing");
                    pythonProcess.destroyForcibly();
                } else {
                    log.info("Embedding service stopped");
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                pythonProcess.destroyForcibly();
            }
        }
    }

    private boolean isServiceReachable() {
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .version(HttpClient.Version.HTTP_1_1)   // 同步：避免 h2c upgrade 问题
                    .uri(URI.create(baseUrl + "/health"))
                    .timeout(Duration.ofSeconds(2))
                    .GET()
                    .build();
            HttpResponse<String> resp = HttpClient.newHttpClient()
                    .send(request, HttpResponse.BodyHandlers.ofString());
            return resp.statusCode() == 200;
        } catch (Exception e) {
            return false;
        }
    }
}