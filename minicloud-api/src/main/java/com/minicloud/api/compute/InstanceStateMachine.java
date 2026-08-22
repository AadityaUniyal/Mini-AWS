package com.minicloud.api.compute;

import com.minicloud.api.domain.InstanceState;

import java.util.Map;
import java.util.Set;

/**
 * Finite State Machine for EC2 Instance lifecycle management.
 * Follows the AWS EC2 instance lifecycle model with failure transitions.
 */
public class InstanceStateMachine {
    
    private static final Map<InstanceState, Set<InstanceState>> VALID_NEXT_STATES = Map.of(
        InstanceState.PENDING, Set.of(InstanceState.RUNNING),
        InstanceState.RUNNING, Set.of(InstanceState.STOPPED, InstanceState.TERMINATED),
        InstanceState.STOPPING, Set.of(InstanceState.STOPPED),
        InstanceState.STOPPED, Set.of(InstanceState.RUNNING, InstanceState.TERMINATED),
        InstanceState.FAILED, Set.of(InstanceState.PENDING, InstanceState.TERMINATED),
        InstanceState.TERMINATED, Set.of()
    );

    private static final Map<InstanceState, Set<InstanceState>> ALLOWED_TRANSITIONS = Map.of(
        InstanceState.PENDING, Set.of(InstanceState.RUNNING, InstanceState.FAILED),
        InstanceState.RUNNING, Set.of(InstanceState.STOPPING, InstanceState.STOPPED, InstanceState.TERMINATED, InstanceState.FAILED),
        InstanceState.STOPPING, Set.of(InstanceState.STOPPED, InstanceState.FAILED),
        InstanceState.STOPPED, Set.of(InstanceState.PENDING, InstanceState.RUNNING, InstanceState.TERMINATED),
        InstanceState.FAILED, Set.of(InstanceState.PENDING, InstanceState.RUNNING, InstanceState.TERMINATED),
        InstanceState.TERMINATED, Set.of()
    );
    
    public static Set<InstanceState> getValidNextStates(InstanceState state) {
        if (state == null) return Set.of();
        return VALID_NEXT_STATES.getOrDefault(state, Set.of());
    }

    public static boolean isTerminalState(InstanceState state) {
        return state == InstanceState.TERMINATED;
    }

    public static boolean canTransition(InstanceState from, InstanceState to) {
        if (from == null || to == null) return false;
        if (from == to) return true; // Idempotent transition
        return ALLOWED_TRANSITIONS.getOrDefault(from, Set.of()).contains(to);
    }
    
    public static void validateTransition(InstanceState from, InstanceState to) {
        if (!canTransition(from, to)) {
            Set<InstanceState> validTransitions = ALLOWED_TRANSITIONS.get(from);
            throw new IllegalStateException(
                String.format("Cannot transition from %s to %s. Valid transitions from %s: %s", 
                    from, to, from, validTransitions != null ? validTransitions : "none"));
        }
    }
}