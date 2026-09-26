package com.ragagent.config;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.LoggerContext;
import com.ragagent.service.LogEventService;
import jakarta.annotation.PostConstruct;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * 启动时把 {@link LogDbAppender} 挂到 root logger。
 * 用编程方式注册（而非 logback-spring.xml），以免破坏 Spring Boot 既有的控制台/文件日志配置。
 */
@Component
public class LogDbAppenderRegistrar {

    private final LogEventService logEventService;

    public LogDbAppenderRegistrar(LogEventService logEventService) {
        this.logEventService = logEventService;
    }

    @PostConstruct
    public void register() {
        LoggerContext ctx = (LoggerContext) LoggerFactory.getILoggerFactory();
        Logger root = ctx.getLogger(Logger.ROOT_LOGGER_NAME);
        LogDbAppender appender = new LogDbAppender(logEventService);
        appender.setContext(ctx);
        appender.setName("LOG_DB");
        appender.start();
        root.addAppender(appender);
    }
}
