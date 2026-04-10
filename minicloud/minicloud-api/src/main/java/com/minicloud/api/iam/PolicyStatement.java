package com.minicloud.api.iam;

import jakarta.persistence.*;
import lombok.*;

@Embeddable
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PolicyStatement {

    @Enumerated(EnumType.STRING)
    @Column(name = "effect", nullable = false)
    private Effect effect; // ALLOW or DENY

    @Column(name = "action", nullable = false)
    private String action; // e.g., "s3:ListBuckets", "ec2:*"

    @Column(name = "resource", nullable = false)
    private String resource; // e.g., "mc:s3:my-bucket", "*"

    public enum Effect {
        ALLOW, DENY
    }
}
