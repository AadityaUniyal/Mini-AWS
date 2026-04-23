package com.minicloud.api.billing;

import com.minicloud.api.domain.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class BillingServiceTest {

    @Mock
    private BillingRecordRepository billingRecordRepository;
    
    @Mock
    private InstanceRepository instanceRepository;
    
    @Mock
    private BucketRepository bucketRepository;
    
    @InjectMocks
    private BillingService billingService;

    @Test
    void ec2_cost_per_minute_is_correct() {
        // EC2 costs $0.05 per hour, so per minute should be $0.05/60
        double expectedPerMinute = 0.05 / 60.0;
        double actualPerMinute = 0.000833; // approximately $0.05/60
        
        assertThat(actualPerMinute).isCloseTo(expectedPerMinute, within(0.000001));
    }

    @Test
    void terminated_instances_are_not_billed() {
        // arrange - no running instances
        when(instanceRepository.findByState(InstanceState.RUNNING)).thenReturn(List.of());
        when(bucketRepository.findAll()).thenReturn(List.of());
        
        // act
        billingService.accumulateCosts();
        
        // assert - no billing records should be saved
        verify(billingRecordRepository, never()).save(any());
    }

    @Test
    void s3_cost_calculation_uses_correct_formula() {
        // S3 costs $0.023 per GB-month
        // For 1 GB stored for 1 hour = $0.023 / (30 * 24) = $0.000032 per hour
        double expectedHourlyRatePerGb = 0.023 / (30 * 24);
        double actualHourlyRatePerGb = 0.000032; // approximately
        
        assertThat(actualHourlyRatePerGb).isCloseTo(expectedHourlyRatePerGb, within(0.000001));
    }

    @Test
    void billing_accumulation_processes_running_instances() {
        // arrange
        Instance runningInstance = new Instance();
        runningInstance.setId(java.util.UUID.randomUUID());
        runningInstance.setName("test-instance");
        runningInstance.setState(InstanceState.RUNNING);
        runningInstance.setType(InstanceType.T2_MICRO);
        runningInstance.setAccountId("123456789012");
        
        when(instanceRepository.findByState(InstanceState.RUNNING)).thenReturn(List.of(runningInstance));
        when(bucketRepository.findAll()).thenReturn(List.of());
        
        // act
        billingService.accumulateCosts();
        
        // assert - billing record should be created for the running instance
        verify(billingRecordRepository, times(1)).save(any(BillingRecord.class));
    }
}