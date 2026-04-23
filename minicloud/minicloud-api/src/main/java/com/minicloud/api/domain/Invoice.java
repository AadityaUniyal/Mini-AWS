package com.minicloud.api.domain;

import jakarta.persistence.*;
import lombok.*;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "billing_invoices")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EntityListeners(AuditingEntityListener.class)
public class Invoice {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false)
    private String accountId;

    private String invoiceNumber; // e.g. INV-202404-1234
    private double totalAmount;
    private String status; // ISSUED, PAID, OVERDUE

    private LocalDateTime periodStart;
    private LocalDateTime periodEnd;

    @CreatedDate
    private LocalDateTime createdAt;
}
