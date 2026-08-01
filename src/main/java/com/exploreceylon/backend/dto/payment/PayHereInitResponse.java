package com.exploreceylon.backend.dto.payment;

import lombok.Builder;
import lombok.Data;

// Fields needed to submit PayHere hidden form from frontend
@Data @Builder
public class PayHereInitResponse {

    // PayHere form fields
    private String merchantId;
    private String orderId;
    private Double amount;
    private String currency;
    private String hash;

    // Item info (shown on PayHere page)
    private String itemsName;
    private String itemsNumber;
    private Integer quantity;

    // Customer
    private String firstName;
    private String lastName;
    private String email;
    private String phone;
    private String address;
    private String city;
    private String country;

    // Callbacks
    private String returnUrl;
    private String cancelUrl;
    private String notifyUrl;

    // Sandbox or live
    private String action;   // https://sandbox.payhere.lk/pay/checkout
}