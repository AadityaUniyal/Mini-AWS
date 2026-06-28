package com.minicloud.api.compute;

import com.minicloud.api.domain.InstanceState;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class InstanceStateMachineTest {

    @Test
    void pending_can_transition_to_running() {
        boolean canTransition = InstanceStateMachine.canTransition(InstanceState.PENDING, InstanceState.RUNNING);
        assertThat(canTransition).isTrue();
    }

    @Test
    void pending_cannot_transition_to_stopped() {
        boolean canTransition = InstanceStateMachine.canTransition(InstanceState.PENDING, InstanceState.STOPPED);
        assertThat(canTransition).isFalse();
    }

    @Test
    void running_can_transition_to_stopped() {
        boolean canTransition = InstanceStateMachine.canTransition(InstanceState.RUNNING, InstanceState.STOPPED);
        assertThat(canTransition).isTrue();
    }

    @Test
    void running_can_transition_to_terminated() {
        boolean canTransition = InstanceStateMachine.canTransition(InstanceState.RUNNING, InstanceState.TERMINATED);
        assertThat(canTransition).isTrue();
    }

    @Test
    void stopped_can_transition_to_running() {
        boolean canTransition = InstanceStateMachine.canTransition(InstanceState.STOPPED, InstanceState.RUNNING);
        assertThat(canTransition).isTrue();
    }

    @Test
    void stopped_can_transition_to_terminated() {
        boolean canTransition = InstanceStateMachine.canTransition(InstanceState.STOPPED, InstanceState.TERMINATED);
        assertThat(canTransition).isTrue();
    }

    @Test
    void terminated_cannot_transition_to_any_state() {
        assertThat(InstanceStateMachine.canTransition(InstanceState.TERMINATED, InstanceState.RUNNING)).isFalse();
        assertThat(InstanceStateMachine.canTransition(InstanceState.TERMINATED, InstanceState.STOPPED)).isFalse();
        assertThat(InstanceStateMachine.canTransition(InstanceState.TERMINATED, InstanceState.PENDING)).isFalse();
    }

    @Test
    void validate_transition_throws_exception_for_invalid_transition() {
        assertThatThrownBy(() -> 
            InstanceStateMachine.validateTransition(InstanceState.PENDING, InstanceState.STOPPED))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("Cannot transition from PENDING to STOPPED");
    }

    @Test
    void validate_transition_succeeds_for_valid_transition() {
        // Should not throw any exception
        InstanceStateMachine.validateTransition(InstanceState.PENDING, InstanceState.RUNNING);
        InstanceStateMachine.validateTransition(InstanceState.RUNNING, InstanceState.STOPPED);
        InstanceStateMachine.validateTransition(InstanceState.STOPPED, InstanceState.RUNNING);
    }

    @Test
    void get_valid_next_states_returns_correct_states() {
        Set<InstanceState> pendingNext = InstanceStateMachine.getValidNextStates(InstanceState.PENDING);
        assertThat(pendingNext).containsExactly(InstanceState.RUNNING);

        Set<InstanceState> runningNext = InstanceStateMachine.getValidNextStates(InstanceState.RUNNING);
        assertThat(runningNext).containsExactlyInAnyOrder(InstanceState.STOPPED, InstanceState.TERMINATED);

        Set<InstanceState> stoppedNext = InstanceStateMachine.getValidNextStates(InstanceState.STOPPED);
        assertThat(stoppedNext).containsExactlyInAnyOrder(InstanceState.RUNNING, InstanceState.TERMINATED);

        Set<InstanceState> terminatedNext = InstanceStateMachine.getValidNextStates(InstanceState.TERMINATED);
        assertThat(terminatedNext).isEmpty();
    }

    @Test
    void is_terminal_state_correctly_identifies_terminal_states() {
        assertThat(InstanceStateMachine.isTerminalState(InstanceState.TERMINATED)).isTrue();
        assertThat(InstanceStateMachine.isTerminalState(InstanceState.PENDING)).isFalse();
        assertThat(InstanceStateMachine.isTerminalState(InstanceState.RUNNING)).isFalse();
        assertThat(InstanceStateMachine.isTerminalState(InstanceState.STOPPED)).isFalse();
    }
}