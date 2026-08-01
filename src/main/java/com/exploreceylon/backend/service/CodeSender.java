package com.exploreceylon.backend.service;

import com.exploreceylon.backend.model.VerificationCode.CodeType;

/**
 * Delivery seam for verification codes (email link/code, phone OTP).
 *
 * The current implementation ({@link LogCodeSender}) only logs the code — there
 * is no SMTP or SMS provider configured yet. Drop in a real sender (e.g. a
 * JavaMailSender-backed EmailCodeSender, a Twilio SmsCodeSender) behind this
 * interface later without touching VerificationService.
 */
public interface CodeSender {
    void send(String destination, CodeType type, String code);
}
