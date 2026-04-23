package com.minicloud.api.billing;

import com.minicloud.api.domain.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class BillingService {

    private final BillingRecordRepository billingRecordRepository;
    private final InstanceRepository instanceRepository;
    private final RdsRepository rdsRepository;
    private final BucketRepository bucketRepository;

    // Pricing Constants (Simulation)
    private static final double PRICE_EC2_HOUR = 0.05;
    private static final double PRICE_RDS_HOUR = 0.10;
    private static final double PRICE_S3_GB_MONTH = 0.02;

    /**
     * Accumulate costs for running resources every minute.
     * Real AWS bills per second, but we simulate every minute for accuracy in our dashboard.
     */
    @Scheduled(fixedRate = 60000)
    @Transactional
    public void accumulateCosts() {
        log.debug("Billing task: Accumulating hourly costs...");

        // 1. EC2 Instances (Running)
        List<Instance> instances = instanceRepository.findByState(InstanceState.RUNNING);
        for (Instance inst : instances) {
            if (inst.getAccountId() == null) continue;
            recordUsage(inst.getAccountId(), "EC2", inst.getId().toString(), inst.getName(), PRICE_EC2_HOUR / 60.0, "hour");
        }

        // 2. RDS Instances (Running)
        List<RdsInstance> rdsInstances = rdsRepository.findAll();
        for (RdsInstance rds : rdsInstances) {
            if (rds.getAccountId() == null) continue;
            recordUsage(rds.getAccountId(), "RDS", rds.getId().toString(), rds.getName(), PRICE_RDS_HOUR / 60.0, "hour");
        }

        // 3. S3 Storage (Total GB)
        List<Bucket> buckets = bucketRepository.findAll();
        for (Bucket b : buckets) {
            if (b.getAccountId() == null) continue;
            recordUsage(b.getAccountId(), "S3", b.getId().toString(), b.getName(), PRICE_S3_GB_MONTH / (30 * 24 * 60.0), "GB-month");
        }
    }

    private void recordUsage(String accountId, String service, String resId, String resName, double cost, String unit) {
        BillingRecord record = BillingRecord.builder()
                .accountId(accountId)
                .service(service)
                .resourceId(resId)
                .resourceName(resName)
                .unitPrice(cost) // Incremental cost for this minute
                .unitType(unit)
                .usageQuantity(1.0 / 60.0) // 1 minute of an hour
                .totalCost(cost)
                .startTime(LocalDateTime.now().minusMinutes(1))
                .endTime(LocalDateTime.now())
                .build();
        billingRecordRepository.save(record);
    }

    public List<BillingRecord> getAccountBills(String accountId) {
        return billingRecordRepository.findByAccountId(accountId);
    }

    public double getMonthToDateEstimate(String accountId) {
        return billingRecordRepository.findByAccountId(accountId).stream()
                .mapToDouble(BillingRecord::getTotalCost)
                .sum();
    }
}
