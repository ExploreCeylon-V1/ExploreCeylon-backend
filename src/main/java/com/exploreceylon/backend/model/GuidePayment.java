package com.exploreceylon.backend.model;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "guide_payments")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class GuidePayment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long guideId;

    @Column(length = 1000)
    private String bookingIds;

    private Double totalEarned;
    private Double commissionDeducted;
    private Double amountPaid;

    private LocalDate paymentDate;

    @Enumerated(EnumType.STRING)
    private PaymentStatus status;

    @Column(updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
    }

    public enum PaymentStatus { PAID, UNPAID }
}