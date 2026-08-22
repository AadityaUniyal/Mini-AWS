package com.minicloud.api.billing;

import com.minicloud.api.domain.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;

@Slf4j
@Service
@RequiredArgsConstructor
public class BillingService {

    private final BillingRecordRepository billingRecordRepository;
    private final InstanceRepository instanceRepository;
    private final RdsRepository rdsRepository;
    private final BucketRepository bucketRepository;

    private static final BigDecimal PRICE_EC2_HOUR = new BigDecimal("0.05");
    private static final BigDecimal PRICE_RDS_HOUR = new BigDecimal("0.10");
    private static final BigDecimal PRICE_S3_GB_MONTH = new BigDecimal("0.02");

    /**
     * Accumulate costs for running resources every minute.
     * Real AWS bills per second, but we simulate every minute with high precision.
     */
    @Scheduled(fixedRate = 60000)
    @Transactional
    public void accumulateCosts() {
        log.debug("Billing task: Accumulating hourly costs...");

        BigDecimal minutesInHour = new BigDecimal("60");
        BigDecimal usageQty = BigDecimal.ONE.divide(minutesInHour, 10, RoundingMode.HALF_UP);

        // 1. EC2 Instances (Running)
        List<Instance> instances = instanceRepository.findByState(InstanceState.RUNNING);
        for (Instance inst : instances) {
            if (inst.getAccountId() == null) continue;
            
            double hourlyRate = inst.getType() != null ? inst.getType().getCostPerHour() : 0.05;
            BigDecimal priceEc2Hour = BigDecimal.valueOf(hourlyRate);
            BigDecimal cost = priceEc2Hour.divide(minutesInHour, 10, RoundingMode.HALF_UP);
            recordUsage(inst.getAccountId(), "EC2", inst.getId().toString(), inst.getName(), cost, "hour", usageQty);
        }

        // 2. RDS Instances (Running)
        List<RdsInstance> rdsInstances = rdsRepository.findAll();
        for (RdsInstance rds : rdsInstances) {
            if (rds.getAccountId() == null || !"RUNNING".equalsIgnoreCase(rds.getStatus())) continue;
            BigDecimal cost = PRICE_RDS_HOUR.divide(minutesInHour, 10, RoundingMode.HALF_UP);
            recordUsage(rds.getAccountId(), "RDS", rds.getId().toString(), rds.getName(), cost, "hour", usageQty);
        }

        // 3. S3 Storage (Actual Stored GB)
        List<Bucket> buckets = bucketRepository.findAll();
        BigDecimal minsInMonth = new BigDecimal(30 * 24 * 60);
        for (Bucket b : buckets) {
            if (b.getAccountId() == null) continue;
            long bytes = b.getTotalSizeBytes();
            double gigabytes = bytes / (1024.0 * 1024.0 * 1024.0);
            BigDecimal gbAmount = BigDecimal.valueOf(gigabytes);
            BigDecimal cost = PRICE_S3_GB_MONTH.multiply(gbAmount).divide(minsInMonth, 10, RoundingMode.HALF_UP);
            BigDecimal s3Usage = gbAmount.divide(minsInMonth, 10, RoundingMode.HALF_UP);
            recordUsage(b.getAccountId(), "S3", b.getId().toString(), b.getName(), cost, "GB-month", s3Usage);
        }
    }

    private void recordUsage(String accountId, String service, String resId, String resName, BigDecimal cost, String unit, BigDecimal usageQty) {
        BillingRecord record = BillingRecord.builder()
                .accountId(accountId)
                .service(service)
                .resourceId(resId)
                .resourceName(resName)
                .unitPrice(cost)
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

    public BigDecimal getMonthToDateEstimate(String accountId) {
        LocalDateTime startOfMonth = LocalDateTime.now().withDayOfMonth(1).toLocalDate().atStartOfDay();
        return billingRecordRepository.findByAccountId(accountId).stream()
                .filter(r -> r.getStartTime() != null && !r.getStartTime().isBefore(startOfMonth))
                .map(BillingRecord::getTotalCost)
                .filter(Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }
}
