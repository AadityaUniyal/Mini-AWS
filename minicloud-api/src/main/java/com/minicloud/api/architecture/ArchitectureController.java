package com.minicloud.api.architecture;

import com.minicloud.api.dto.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/architecture")
@RequiredArgsConstructor
@Tag(name = "Architecture & Topology", description = "Live cloud infrastructure topology and visual diagram generation")
public class ArchitectureController {

    private final ArchitectureTopologyService topologyService;

    @GetMapping("/topology")
    @Operation(summary = "Get current infrastructure topology graph (nodes and edges)")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getTopology() {
        return ResponseEntity.ok(ApiResponse.ok("Infrastructure topology retrieved", topologyService.getTopologyGraph()));
    }

    @GetMapping(value = "/diagram", produces = MediaType.TEXT_PLAIN_VALUE)
    @Operation(summary = "Generate live Mermaid diagram code representing running cloud resources")
    public ResponseEntity<String> getMermaidDiagram() {
        return ResponseEntity.ok()
                .contentType(MediaType.TEXT_PLAIN)
                .body(topologyService.generateMermaidDiagram());
    }
}
