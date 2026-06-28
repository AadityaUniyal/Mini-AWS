package com.minicloud.api.route;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class Route53Service {

    private final HostedZoneRepository zoneRepository;
    private final DnsRecordRepository recordRepository;

    public HostedZone createZone(String name, String accountId, String comment) {
        return zoneRepository.save(HostedZone.builder()
                .name(name.endsWith(".") ? name : name + ".")
                .accountId(accountId)
                .comment(comment)
                .callerReference(UUID.randomUUID().toString())
                .createdAt(LocalDateTime.now())
                .build());
    }

    public List<HostedZone> getZones(String accountId) {
        return zoneRepository.findByAccountId(accountId);
    }

    public DnsRecord createRecord(UUID zoneId, String name, String type, String value, Long ttl, String accountId) {
        HostedZone zone = zoneRepository.findById(zoneId)
                .orElseThrow(() -> new RuntimeException("Hosted Zone not found"));
                
        return recordRepository.save(DnsRecord.builder()
                .hostedZone(zone)
                .name(name.endsWith(".") ? name : name + ".")
                .type(type)
                .recordValue(value)
                .ttl(ttl != null ? ttl : 300L)
                .accountId(accountId)
                .build());
    }

    public List<DnsRecord> getRecords(UUID zoneId) {
        return recordRepository.findByHostedZoneId(zoneId);
    }

    public void deleteZone(UUID zoneId) {
        zoneRepository.deleteById(zoneId);
    }

    public void deleteRecord(UUID recordId) {
        recordRepository.deleteById(recordId);
    }
}
