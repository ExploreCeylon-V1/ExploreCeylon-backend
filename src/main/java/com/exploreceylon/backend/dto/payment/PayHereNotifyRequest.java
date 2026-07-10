package com.exploreceylon.backend.dto.payment;

import lombok.Data;

// PayHere IPN webhook payload (POST to /api/v1/payments/guide/notify)
@Data
public class PayHereNotifyRequest {

    private String merchant_id;
    private String order_id;
    private String payment_id;
    private String payhere_amount;
    private String payhere_currency;
    private String status_code;      // 2=success, 0=pending, -1=cancelled, -2=failed, -3=chargedback
    private String md5sig;           // verify this!
    private String method;           // VISA, MASTER, etc.
    private String status_message;
    private String customer_token;
}