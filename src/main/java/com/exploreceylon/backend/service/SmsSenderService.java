package com.exploreceylon.backend.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import reactor.util.retry.Retry;

import java.time.Duration;

/**
 * Sends OTP/verification SMS via the Notify.lk HTTP API. Credentials are
 * supplied entirely through environment-backed properties — never
 * hard-coded.
 */
@Service
@Slf4j
public class SmsSenderService {

    private final WebClient webClient;

    @Value("${notifylk.user-id:}")
    private String userId;

    @Value("${notifylk.api-key:}")
    private String apiKey;

    @Value("${notifylk.sender-id:ExploreCey}")
    private String senderId;

    private static final Duration TIMEOUT = Duration.ofSeconds(8);

    public SmsSenderService(@Value("${notifylk.base-url:https://app.notify.lk/api/v1/send}") String baseUrl) {
        this.webClient = WebClient.builder().baseUrl(baseUrl).build();
    }

    /** Sends a verification code SMS. Throws {@link SmsDeliveryException} on failure. */
    public void sendVerificationCode(String phone, String code) {
        String message = "Your Explore Ceylon verification code is " + code + ". It expires in 10 minutes.";
        send(phone, message);
    }

    private void send(String rawPhone, String message) {
        if (userId == null || userId.isBlank() || apiKey == null || apiKey.isBlank()) {
            throw new SmsDeliveryException("SMS delivery is not configured on this server.");
        }
        String phone = normalizeSriLankanNumber(rawPhone);
        try {
            String response = webClient.get()
                    .uri(uriBuilder -> uriBuilder
                            .queryParam("user_id", userId)
                            .queryParam("api_key", apiKey)
                            .queryParam("sender_id", senderId)
                            .queryParam("to", phone)
                            .queryParam("message", message)
                            .build())
                    .retrieve()
                    .bodyToMono(String.class)
                    .timeout(TIMEOUT)
                    .retryWhen(Retry.backoff(2, Duration.ofSeconds(1))
                            .filter(SmsSenderService::isRetryable))
                    .block();
            log.info("SMS sent to {} (notify.lk response: {})", phone, response);
        } catch (Exception ex) {
            log.error("Failed to send SMS to {}", phone, ex);
            throw new SmsDeliveryException("We couldn't send the verification SMS. Please try again shortly.", ex);
        }
    }

    private static boolean isRetryable(Throwable ex) {
        if (ex instanceof java.util.concurrent.TimeoutException) return true;
        if (ex instanceof WebClientResponseException wcre) {
            return wcre.getStatusCode().is5xxServerError()
                    || wcre.getStatusCode() == HttpStatus.TOO_MANY_REQUESTS;
        }
        return false;
    }

    /**
     * Normalizes Sri Lankan mobile numbers to Notify.lk's expected
     * international format (94XXXXXXXXX): 07XXXXXXXX → 947XXXXXXXX,
     * +947XXXXXXXX → 947XXXXXXXX. Already-normalized or foreign numbers are
     * passed through unchanged.
     */
    static String normalizeSriLankanNumber(String phone) {
        if (phone == null) return null;
        String digits = phone.trim().replaceAll("[\\s\\-()]", "");
        if (digits.startsWith("+")) digits = digits.substring(1);
        if (digits.startsWith("0") && digits.length() == 10) {
            return "94" + digits.substring(1);
        }
        if (digits.startsWith("94") && digits.length() == 11) {
            return digits;
        }
        return digits;
    }

    public static class SmsDeliveryException extends RuntimeException {
        public SmsDeliveryException(String message) {
            super(message);
        }

        public SmsDeliveryException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
