package com.ragagent.config;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.classic.spi.IThrowableProxy;
import ch.qos.logback.core.AppenderBase;
import com.ragagent.service.LogEventService;

/**
 * 只把 ERROR 级日志送入 log_events 表（经 {@link LogEventService} 异步落库）。
 * 由 {@link LogDbAppenderRegistrar} 在启动时挂到 root logger。
 */
public class LogDbAppender extends AppenderBase<ILoggingEvent> {

    private final LogEventService logEventService;

    public LogDbAppender(LogEventService logEventService) {
        this.logEventService = logEventService;
    }

    @Override
    protected void append(ILoggingEvent event) {
        if (event.getLevel() != Level.ERROR) return;
        String msg = event.getFormattedMessage();
        IThrowableProxy t = event.getThrowableProxy();
        if (t != null) {
            msg += " | " + t.getClassName() + ": " + t.getMessage();
        }
        logEventService.error(event.getLoggerName(), msg, event.getThreadName());
    }
}
