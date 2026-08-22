package com.minicloud.api.billing;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Aggregated telemetry-driven rightsizing and cost recommendation response")
public class RightsizingResponseDTO {

    @Schema(description = "Total estimated monthly savings across all recommended instances in USD", example = "25.55")
    private double totalEstimatedMonthlySavings;

    @Schema(description = "Count of rightsizing recommendations identified", example = "1")
    private int recommendationsCount;

    @Schema(description = "ISO-8601 timestamp of evaluation", example = "2026-08-22T06:00:00Z")
    private String evaluatedAt;

    @Schema(description = "List of rightsizing recommendations for underutilized instances")
    private List<RightsizingRecommendationDTO> recommendations;
}
