package com.minicloud.api.billing;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Individual instance rightsizing recommendation details")
public class RightsizingRecommendationDTO {

    @Schema(description = "Compute Instance ID", example = "inst-123456")
    private String instanceId;

    @Schema(description = "Human-readable instance name", example = "web-server-1")
    private String instanceName;

    @Schema(description = "Current provisioned instance type", example = "T2_MEDIUM")
    private String currentInstanceType;

    @Schema(description = "Recommended right-sized instance type", example = "T2_SMALL")
    private String recommendedInstanceType;

    @Schema(description = "Rolling average CPU utilization percentage", example = "4.5")
    private double averageCpuUtilization;

    @Schema(description = "Current hourly cost in USD", example = "0.046")
    private double currentHourlyCost;

    @Schema(description = "Recommended hourly cost in USD", example = "0.023")
    private double recommendedHourlyCost;

    @Schema(description = "Net hourly cost savings in USD", example = "0.023")
    private double hourlySavings;

    @Schema(description = "Estimated monthly savings in USD based on 730 hours/month", example = "16.79")
    private double estimatedMonthlySavings;

    @Schema(description = "Detailed justification and threshold analysis for the recommendation",
            example = "Instance CPU utilization averaged 4.5% (<10.0% threshold) over rolling window.")
    private String reason;
}
