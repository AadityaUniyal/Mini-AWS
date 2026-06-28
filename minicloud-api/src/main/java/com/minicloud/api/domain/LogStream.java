package com.minicloud.api.domain;

import jakarta.persistence.*;
import lombok.*;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "log_streams", uniqueConstraints = {
    @UniqueConstraint(columnNames = {"log_group_name", "log_stream_name"})
})
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EntityListeners(AuditingEntityListener.class)
public class LogStream {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "log_group_name", nullable = false)
    private String logGroupName;

    @Column(name = "log_stream_name", nullable = false)
    private String logStreamName;

    @Column(name = "account_id", nullable = false)
    private String accountId;

    @CreatedDate
    private LocalDateTime createdAt;
}
