package com.minicloud.api.route;

import java.net.InetAddress;

/**
 * Fast bitwise CIDR matching engine for VPC, Security Groups, and NACLs.
 */
public final class CidrMatcher {

    private CidrMatcher() {}

    public static boolean matches(String cidr, String ip) {
        if (cidr == null || ip == null || cidr.isBlank() || ip.isBlank()) {
            return false;
        }

        cidr = cidr.trim();
        ip = ip.trim();

        if ("0.0.0.0/0".equals(cidr) || "*".equals(cidr)) {
            return true;
        }

        try {
            String[] parts = cidr.split("/");
            String networkIp = parts[0];
            int prefixLength = parts.length > 1 ? Integer.parseInt(parts[1]) : 32;

            if (prefixLength == 0) return true;
            if (prefixLength < 0 || prefixLength > 32) return false;

            int targetIpInt = ipToInt(ip);
            int networkIpInt = ipToInt(networkIp);
            int mask = prefixLength == 0 ? 0 : 0xFFFFFFFF << (32 - prefixLength);

            return (targetIpInt & mask) == (networkIpInt & mask);
        } catch (Exception e) {
            return false;
        }
    }

    public static int ipToInt(String ip) throws Exception {
        InetAddress address = InetAddress.getByName(ip);
        byte[] bytes = address.getAddress();
        if (bytes.length != 4) {
            throw new IllegalArgumentException("Only IPv4 addresses are currently supported: " + ip);
        }
        return ((bytes[0] & 0xFF) << 24) |
               ((bytes[1] & 0xFF) << 16) |
               ((bytes[2] & 0xFF) << 8)  |
               (bytes[3] & 0xFF);
    }

    public static String intToIp(int ip) {
        return String.format("%d.%d.%d.%d",
                (ip >>> 24) & 0xFF,
                (ip >>> 16) & 0xFF,
                (ip >>> 8)  & 0xFF,
                ip & 0xFF);
    }
}
