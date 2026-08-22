package com.minicloud.api.chaos;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

/**
 * Structured response for Chaos Monkey termination and self-healing recovery actions.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ChaosResultDTO {

    /** Action performed by chaos engine (e.g. "TERMINATE_INSTANCE") */
    private String chaosAction;

    /** Alias for chaosAction */
    private String action;

    /** ID of the randomly terminated compute instance */
    private String terminatedInstanceId;

    /** Auto Scaling Group ID / Name to which the instance belonged */
    private String autoScalingGroupId;

    /** Previous lifecycle state before chaos injection (e.g. "RUNNING") */
    private String previousState;

    /** Current state after termination ("TERMINATED") */
    private String currentState;

    /** Whether capacity deficit was detected below target */
    private boolean deficitDetected;

    /** ID of newly provisioned replacement instance */
    private String replacementInstanceId;

    /** State of replacement instance ("RUNNING") */
    private String replacementState;

    /** Overall status of self-healing operation ("SELF_HEALING_COMPLETED") */
    private String status;

    /** ISO-8601 timestamp of operation execution */
    private String timestamp;

    /** Nested details of terminated instance */
    private TerminatedInstanceDetails terminatedInstance;

    /** Nested details of self-healing replenishment */
    private SelfHealingDetails selfHealing;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class TerminatedInstanceDetails {
        private String id;
        private String name;
        private String type;
        private String previousState;
        private String newState;
        private String accountId;
        private String terminatedAt;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class SelfHealingDetails {
        private boolean replenishmentTriggered;
        private String replenishedInstanceId;
        private String replenishedInstanceName;
        private String status;
    }
}
