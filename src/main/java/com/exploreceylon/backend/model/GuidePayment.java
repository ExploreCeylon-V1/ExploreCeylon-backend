package com.exploreceylon.backend.model;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDate;

@Entity
@Table(name = "guide_payments")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class GuidePayment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long guideId;

    private Double totalEarned;
    private Double commissionDeducted;
    private Double amountPaid;

    private LocalDate paymentDate;

    @Enumerated(EnumType.STRING)
    private PaymentStatus status;

    public enum PaymentStatus { PAID, UNPAID }
}