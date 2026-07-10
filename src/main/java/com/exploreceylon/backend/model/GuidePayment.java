package com.exploreceylon.backend.model;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "guide_payments")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class GuidePayment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // ── Relations ──────────────────────────────────────────
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "guide_booking_id", nullable = false)
    private GuideBooking guideBooking;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;   // traveler who paid

    // ── Payment phase ──────────────────────────────────────
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private PaymentPhase paymentPhase;   // ADVANCE | FINAL

    @Column(nullable = false)
    private Integer phasePercent;        // 40 or 60

    // ── Amounts (USD) ──────────────────────────────────────
    @Column(nullable = false)
    private Double amount;               // what traveler paid

    @Column(nullable = false)
    private Double commissionAmount;     // 15% → ExploreCeylon

    @Column(nullable = false)
    private Double guidePayout;          // 85% → guide

    @Builder.Default
    private String currency = "USD";

    // ── PayHere gateway ───────────────────────────────────
    private String payhereOrderId;       // our order ID
    private String payherePaymentId;     // PayHere payment_id
    private String payhereStatusCode;    // 2=success, 0=pending

    // ── Status ────────────────────────────────────────────
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private PaymentStatus status;

    // ── Timestamps ────────────────────────────────────────
    private LocalDateTime paidAt;

    @Column(updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    public void prePersist() {
        this.createdAt = LocalDateTime.now();
    }

    // ── Enums ─────────────────────────────────────────────
    public enum PaymentPhase {
        ADVANCE,  // 40% — paid at booking
        FINAL     // 60% — paid after service
    }

    public enum PaymentStatus {
        PENDING,    // initiated, not paid yet
        COMPLETED,  // PayHere confirmed
        FAILED,     // PayHere failed
        REFUNDED    // refunded to traveler
    }
}