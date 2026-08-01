package com.exploreceylon.backend.service;

import com.exploreceylon.backend.dto.payment.PayHereInitRequest;
import com.exploreceylon.backend.dto.payment.PayHereInitResponse;
import com.exploreceylon.backend.dto.payment.PayHereNotifyRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.UUID;

@Service
@Slf4j
public class PayHereService {

    @Value("${payhere.merchant.id}")
    private String merchantId;

    @Value("${payhere.merchant.secret}")
    private String merchantSecret;

    @Value("${payhere.sandbox:true}")
    private boolean sandbox;

    @Value("${app.base.url:http://localhost:8080}")
    private String appBaseUrl;

    @Value("${frontend.base.url:http://localhost:3000}")
    private String frontendBaseUrl;

    private static final double COMMISSION_RATE = 0.15;

    // ── Build PayHere form payload ─────────────────────────
    // NOTE: orderId is generated ONCE by the caller and persisted on the payment
    // row, then passed in here — so the value sent to PayHere (and echoed back in
    // the IPN) matches the DB record. Do NOT regenerate it here.
    public PayHereInitResponse buildInitResponse(
        PayHereInitRequest req, String orderId, Double totalAmount, String itemName
    ) {
        String hash = generateHash(orderId, totalAmount);

        String action = sandbox
            ? "https://sandbox.payhere.lk/pay/checkout"
            : "https://www.payhere.lk/pay/checkout";

        String notifyPath = req.getBookingType().equalsIgnoreCase("GUIDE")
            ? "/api/v1/payments/guide/notify"
            : "/api/v1/payments/vehicle/notify";

        return PayHereInitResponse.builder()
            .merchantId(merchantId)
            .orderId(orderId)
            .amount(roundAmount(totalAmount))
            .currency("USD")
            .hash(hash)
            .itemsName(itemName)
            .itemsNumber(orderId)
            .quantity(1)
            .firstName(req.getFirstName())
            .lastName(req.getLastName())
            .email(req.getEmail())
            .phone(req.getPhone())
            .address(req.getAddress() != null ? req.getAddress() : "N/A")
            .city(req.getCity()    != null ? req.getCity()    : "Colombo")
            .country(req.getCountry() != null ? req.getCountry() : "Sri Lanka")
            .returnUrl(frontendBaseUrl + "/payment/success?order=" + orderId)
            .cancelUrl(frontendBaseUrl + "/payment/cancel?order=" + orderId)
            .notifyUrl(appBaseUrl + notifyPath)
            .action(action)
            .build();
    }

    // ── Verify PayHere IPN webhook ─────────────────────────
    public boolean verifyNotification(PayHereNotifyRequest notify) {
        try {
            String secretMd5  = md5(merchantSecret).toUpperCase();
            String localSig   = md5(
                merchantId +
                notify.getOrder_id() +
                notify.getPayhere_amount() +
                notify.getPayhere_currency() +
                notify.getStatus_code() +
                secretMd5
            ).toUpperCase();

            boolean valid = localSig.equals(notify.getMd5sig());
            if (!valid) {
                log.warn("PayHere signature mismatch for order: {}", notify.getOrder_id());
            }
            return valid;
        } catch (Exception e) {
            log.error("PayHere verification error", e);
            return false;
        }
    }

    // ── Calculate commission ───────────────────────────────
    public double calculateCommission(double amount) {
        return roundAmount(amount * COMMISSION_RATE);
    }

    public double calculatePayout(double amount) {
        return roundAmount(amount - calculateCommission(amount));
    }

    // ── Parse phase amount ─────────────────────────────────
    public double calcPhaseAmount(double totalCost, String phase) {
        return phase.equals("ADVANCE")
            ? roundAmount(totalCost * 0.20)
            : roundAmount(totalCost * 0.80);
    }

    public int phasePercent(String phase) {
        return phase.equals("ADVANCE") ? 20 : 80;
    }

    // ── Generate unique order ID ───────────────────────────
    public String generateOrderId(String type, Long bookingId, String phase) {
        String prefix = type.equalsIgnoreCase("GUIDE") ? "GBK" : "VBK";
        String phaseCode = phase.equalsIgnoreCase("ADVANCE") ? "ADV" : "FIN";
        return prefix + "-" + bookingId + "-" + phaseCode + "-" + UUID.randomUUID().toString().substring(0, 6).toUpperCase();
    }

    // ── Payment success status ─────────────────────────────
    public boolean isPaymentSuccess(String statusCode) {
        return "2".equals(statusCode);
    }

    // ── Hash generation ────────────────────────────────────
    private String generateHash(String orderId, Double amount) {
        try {
            String secretMd5 = md5(merchantSecret).toUpperCase();
            String input = merchantId + orderId + String.format("%.2f", amount) + "USD" + secretMd5;
            return md5(input).toUpperCase();
        } catch (Exception e) {
            throw new RuntimeException("Hash generation failed", e);
        }
    }

    private String md5(String input) throws Exception {
        MessageDigest md = MessageDigest.getInstance("MD5");
        byte[] hash = md.digest(input.getBytes(StandardCharsets.UTF_8));
        StringBuilder sb = new StringBuilder();
        for (byte b : hash) sb.append(String.format("%02x", b));
        return sb.toString();
    }

    private double roundAmount(double amount) {
        return BigDecimal.valueOf(amount).setScale(2, RoundingMode.HALF_UP).doubleValue();
    }
}