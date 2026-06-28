package com.minicloud.api.monitoring;

import com.minicloud.api.dto.ApiResponse;
import com.minicloud.api.domain.Alarm;
import com.minicloud.api.monitoring.AlarmService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/monitoring/alarms")
@RequiredArgsConstructor
@Tag(name = "CloudWatch Alarms", description = "Metric-based threshold monitoring")
public class AlarmController {

    private final AlarmService alarmService;

    @GetMapping("/user/{userId}")
    @Operation(summary = "List alarms for a specific user")
    public ResponseEntity<ApiResponse<List<Alarm>>> listAlarms(@PathVariable UUID userId) {
        return ResponseEntity.ok(ApiResponse.ok(alarmService.getAlarmsForUser(userId)));
    }

    @PostMapping
    @Operation(summary = "Create a new alarm")
    public ResponseEntity<ApiResponse<Alarm>> createAlarm(@RequestBody Alarm alarm) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok("Alarm created", alarmService.createAlarm(alarm)));
    }
}
