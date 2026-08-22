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
    private static final java.math.BigDecimal PRICE_EC2_HOUR = new java.math.BigDecimal("0.05");
    private static final java.math.BigDecimal PRICE_RDS_HOUR = new java.math.BigDecimal("0.10");
    private static final java.math.BigDecimal PRICE_S3_GB_MONTH = new java.math.BigDecimal("0.02");

    /**
     * Accumulate costs for running resources every minute.
     * Real AWS bills per second, but we simulate every minute for accuracy in our dashboard.
     */
    @Scheduled(fixedRate = 60000)
    @Transactional
    public void accumulateCosts() {
        log.debug("Billing task: Accumulating hourly costs...");

        java.math.BigDecimal minutesInHour = new java.math.BigDecimal("60");
        java.math.BigDecimal usageQty = java.math.BigDecimal.ONE.divide(minutesInHour, 10, java.math.RoundingMode.HALF_UP);

        // 1. EC2 Instances (Running)
        List<Instance> instances = instanceRepository.findByState(InstanceState.RUNNING);
        for (Instance inst : instances) {
            if (inst.getAccountId() == null) continue;
            
            double hourlyRate = inst.getType() != null ? inst.getType().getCostPerHour() : 0.05;
            java.math.BigDecimal priceEc2Hour = new java.math.BigDecimal(String.valueOf(hourlyRate));
            java.math.BigDecimal cost = priceEc2Hour.divide(minutesInHour, 10, java.math.RoundingMode.HALF_UP);
            recordUsage(inst.getAccountId(), "EC2", inst.getId().toString(), inst.getName(), cost, "hour", usageQty);
        }

        // 2. RDS Instances (Running)
        List<RdsInstance> rdsInstances = rdsRepository.findAll();
        for (RdsInstance rds : rdsInstances) {
            if (rds.getAccountId() == null || !"RUNNING".equalsIgnoreCase(rds.getStatus())) continue;
            java.math.BigDecimal cost = PRICE_RDS_HOUR.divide(minutesInHour, 10, java.math.RoundingMode.HALF_UP);
            recordUsage(rds.getAccountId(), "RDS", rds.getId().toString(), rds.getName(), cost, "hour", usageQty);
        }

        // 3. S3 Storage (Total GB)
        List<Bucket> buckets = bucketRepository.findAll();
        java.math.BigDecimal minsInMonth = new java.math.BigDecimal(30 * 24 * 60);
        for (Bucket b : buckets) {
            if (b.getAccountId() == null) continue;
            java.math.BigDecimal cost = PRICE_S3_GB_MONTH.divide(minsInMonth, 10, java.math.RoundingMode.HALF_UP);
            java.math.BigDecimal s3Usage = java.math.BigDecimal.ONE.divide(minsInMonth, 10, java.math.RoundingMode.HALF_UP);
            recordUsage(b.getAccountId(), "S3", b.getId().toString(), b.getName(), cost, "GB-month", s3Usage);
        }
    }

    private void recordUsage(String accountId, String service, String resId, String resName, java.math.BigDecimal cost, String unit, java.math.BigDecimal usageQty) {
        BillingRecord record = BillingRecord.builder()
                .accountId(accountId)
                .service(service)
                .resourceId(resId)
                .resourceName(resName)
                .unitPrice(cost) // Incremental cost for this minute
                .unitType(unit)
                .usageQuantity(usageQty)
                .totalCost(cost)
                .startTime(LocalDateTime.now().minusMinutes(1))
                .endTime(LocalDateTime.now())
                .build();
        billingRecordRepository.save(record);
    }

    public List<BillingRecord> getAccountBills(String accountId) {
        return billingRecordRepository.findByAccountId(accountId);
    }

    public java.math.BigDecimal getMonthToDateEstimate(String accountId) {
        LocalDateTime startOfMonth = LocalDateTime.now().withDayOfMonth(1).toLocalDate().atStartOfDay();
        return billingRecordRepository.findByAccountId(accountId).stream()
                .filter(r -> r.getStartTime() != null && !r.getStartTime().isBefore(startOfMonth))
                .map(BillingRecord::getTotalCost)
                .filter(java.util.Objects::nonNull)
                .reduce(java.math.BigDecimal.ZERO, java.math.BigDecimal::add);
    }
}
