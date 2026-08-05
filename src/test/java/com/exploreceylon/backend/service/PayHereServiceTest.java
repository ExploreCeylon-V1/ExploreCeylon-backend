package com.exploreceylon.backend.service;

import com.exploreceylon.backend.dto.payment.PayHereInitRequest;
import com.exploreceylon.backend.dto.payment.PayHereInitResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PayHereServiceTest {

    private PayHereService payHereService;

    @BeforeEach
    void setUp() {
        payHereService = new PayHereService();
        ReflectionTestUtils.setField(payHereService, "merchantId", "1236728");
        ReflectionTestUtils.setField(payHereService, "merchantSecret", "secret123");
        ReflectionTestUtils.setField(payHereService, "sandbox", true);
        ReflectionTestUtils.setField(payHereService, "appBaseUrl", "http://exploreceylon.me:8080");
        ReflectionTestUtils.setField(payHereService, "frontendBaseUrl", "http://exploreceylon.me");
    }

    @Test
    void testBuildInitResponseUsesConfiguredUrls() {
        PayHereInitRequest request = new PayHereInitRequest();
        request.setBookingType("GUIDE");
        request.setFirstName("John");
        request.setLastName("Doe");
        request.setEmail("john@example.com");
        request.setPhone("0771234567");

        PayHereInitResponse response = payHereService.buildInitResponse(request, "GBK-1-ADV-123456", 50.00, "Guide Booking");

        assertEquals("http://exploreceylon.me/payment/success?order=GBK-1-ADV-123456", response.getReturnUrl());
        assertEquals("http://exploreceylon.me/payment/cancel?order=GBK-1-ADV-123456", response.getCancelUrl());
        assertEquals("http://exploreceylon.me:8080/api/v1/payments/guide/notify", response.getNotifyUrl());
        assertTrue(response.getAction().contains("sandbox.payhere.lk"));
    }
}
