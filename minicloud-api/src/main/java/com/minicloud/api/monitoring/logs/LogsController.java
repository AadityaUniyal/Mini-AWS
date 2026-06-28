package com.minicloud.api.monitoring.logs;

import com.minicloud.api.domain.LogEvent;
import com.minicloud.api.domain.LogStream;
import com.minicloud.api.dto.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/v1/logs")
@RequiredArgsConstructor
@Tag(name = "CloudWatch Logs", description = "Query log groups, streams, and event logs")
public class LogsController {

    private final LogService logService;

    @GetMapping("/streams/{accountId}")
    @Operation(summary = "Get log streams for an account")
    public ResponseEntity<ApiResponse<List<Map<String, Object>>>> getLogStreams(@PathVariable String accountId) {
        List<LogStream> streams = logService.getLogStreams(accountId);
        List<Map<String, Object>> data = streams.stream().map(s -> Map.<String, Object>of(
                "id", s.getId().toString(),
                "logGroupName", s.getLogGroupName(),
                "logStreamName", s.getLogStreamName(),
                "lastEventAt", s.getCreatedAt().toString()
        )).collect(Collectors.toList());
        return ResponseEntity.ok(ApiResponse.ok("Log streams retrieved", data));
    }

    @GetMapping("/events/{streamId}")
    @Operation(summary = "Get log events for a stream")
    public ResponseEntity<ApiResponse<List<LogEvent>>> getLogEvents(@PathVariable UUID streamId) {
        List<LogEvent> events = logService.getLogEvents(streamId);
        return ResponseEntity.ok(ApiResponse.ok("Log events retrieved", events));
    }
}
