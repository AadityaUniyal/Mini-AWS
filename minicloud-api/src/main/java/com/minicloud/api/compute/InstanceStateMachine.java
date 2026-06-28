package com.minicloud.api.compute;

import com.minicloud.api.domain.InstanceState;

import java.util.Map;
import java.util.Set;

/**
 * Finite State Machine for EC2 Instance lifecycle management.
 * 
 * Valid transitions:
 * PENDING → RUNNING → STOPPED → RUNNING
 * RUNNING → TERMINATED
 * STOPPED → TERMINATED
 * TERMINATED is a final state (no transitions out)
 * 
 * This follows the AWS EC2 instance lifecycle model.
 */
public class InstanceStateMachine {
    
    /**
     * Immutable map defining all valid state transitions.
     * Key = current state, Value = set of allowed next states
     */
    private static final Map<InstanceState, Set<InstanceState>> TRANSITIONS = Map.of(
        InstanceState.PENDING, Set.of(InstanceState.RUNNING),
        InstanceState.RUNNING, Set.of(InstanceState.STOPPED, InstanceState.TERMINATED),
        InstanceState.STOPPED, Set.of(InstanceState.RUNNING, InstanceState.TERMINATED),
        InstanceState.TERMINATED, Set.of()  // final state - no transitions out
    );
    
    /**
     * Check if a state transition is valid according to the FSM rules.
     * 
     * @param from Current state
     * @param to Target state
     * @return true if transition is allowed, false otherwise
     */
    public static boolean canTransition(InstanceState from, InstanceState to) {
        return TRANSITIONS.getOrDefault(from, Set.of()).contains(to);
    }
    
    /**
     * Validate a state transition and throw an exception if invalid.
     * Use this in service methods before performing state changes.
     * 
     * @param from Current state
     * @param to Target state
     * @throws IllegalStateException if transition is not allowed
     */
    public static void validateTransition(InstanceState from, InstanceState to) {
        if (!canTransition(from, to)) {
            Set<InstanceState> validTransitions = TRANSITIONS.get(from);
            throw new IllegalStateException(
                String.format("Cannot transition from %s to %s. Valid transitions from %s: %s", 
                    from, to, from, validTransitions != null ? validTransitions : "none"));
        }
    }
    
    /**
     * Get all valid next states from the current state.
     * 
     * @param currentState The current instance state
     * @return Set of valid next states (empty if terminal state)
     */
    public static Set<InstanceState> getValidNextStates(InstanceState currentState) {
        return TRANSITIONS.getOrDefault(currentState, Set.of());
    }
    
    /**
     * Check if a state is terminal (no outgoing transitions).
     * 
     * @param state The state to check
     * @return true if state is terminal
     */
    public static boolean isTerminalState(InstanceState state) {
        return TRANSITIONS.getOrDefault(state, Set.of()).isEmpty();
    }
}