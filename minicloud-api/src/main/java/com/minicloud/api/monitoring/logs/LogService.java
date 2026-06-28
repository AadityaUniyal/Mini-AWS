package com.minicloud.api.monitoring.logs;

import com.minicloud.api.domain.LogEvent;
import com.minicloud.api.domain.LogEventRepository;
import com.minicloud.api.domain.LogStream;
import com.minicloud.api.domain.LogStreamRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class LogService {

    private final LogStreamRepository logStreamRepository;
    private final LogEventRepository logEventRepository;

    @Transactional
    public LogStream createOrGetStream(String accountId, String logGroupName, String logStreamName) {
        return logStreamRepository.findByLogGroupNameAndLogStreamName(logGroupName, logStreamName)
                .orElseGet(() -> {
                    LogStream stream = LogStream.builder()
                            .accountId(accountId)
                            .logGroupName(logGroupName)
                            .logStreamName(logStreamName)
                            .createdAt(LocalDateTime.now())
                            .build();
                    return logStreamRepository.save(stream);
                });
    }

    @Transactional
    public void putLogEvent(UUID logStreamId, String message) {
        LogEvent event = LogEvent.builder()
                .logStreamId(logStreamId)
                .message(message)
                .timestamp(System.currentTimeMillis())
                .ingestionTime(LocalDateTime.now())
                .build();
        logEventRepository.save(event);
    }

    @Transactional(readOnly = true)
    public List<LogEvent> getLogEvents(UUID logStreamId) {
        return logEventRepository.findByLogStreamIdOrderByTimestampAsc(logStreamId);
    }

    @Transactional(readOnly = true)
    public List<LogStream> getLogStreams(String accountId) {
        return logStreamRepository.findByAccountId(accountId);
    }
}
