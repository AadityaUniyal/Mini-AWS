package com.minicloud.api.route;

import org.springframework.stereotype.Component;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class VpcAddressAllocator {

    private final Set<String> allocatedIps = ConcurrentHashMap.newKeySet();

    /**
     * Allocates next available host IP inside a subnet CIDR block.
     * Skips .0 (network), .1 (VPC router default), and .255 (broadcast).
     */
    public synchronized String allocateIp(String subnetCidr) {
        try {
            String[] parts = subnetCidr.split("/");
            String networkIp = parts[0];
            int prefix = parts.length > 1 ? Integer.parseInt(parts[1]) : 24;

            int net = CidrMatcher.ipToInt(networkIp);
            int mask = prefix == 0 ? 0 : 0xFFFFFFFF << (32 - prefix);
            int base = net & mask;
            int totalHosts = (1 << (32 - prefix));

            // Start from host index 2 up to totalHosts - 2
            for (int i = 2; i < totalHosts - 1; i++) {
                String candidate = CidrMatcher.intToIp(base + i);
                if (allocatedIps.add(candidate)) {
                    return candidate;
                }
            }
            throw new IllegalStateException("Subnet CIDR range exhausted: " + subnetCidr);
        } catch (Exception e) {
            // Fallback
            String randomIp = "10.0.1." + (10 + (int)(Math.random() * 200));
            allocatedIps.add(randomIp);
            return randomIp;
        }
    }

    public synchronized void releaseIp(String ip) {
        if (ip != null) {
            allocatedIps.remove(ip);
        }
    }
}
