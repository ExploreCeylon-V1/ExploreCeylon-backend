package com.exploreceylon.backend.service;

import com.exploreceylon.backend.dto.email.BookingConfirmationDetails;
import jakarta.mail.internet.MimeMessage;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.MailException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

/**
 * Sends real email via Gmail SMTP (Spring Mail / {@link JavaMailSender}).
 * Credentials are supplied entirely through environment-backed properties
 * (spring.mail.username/password) — never hard-coded.
 */
@Service
@Slf4j
public class EmailSenderService {

    private final JavaMailSender mailSender;

    @Value("${app.mail.from:}")
    private String fromAddress;

    @Value("${app.mail.from-name:Explore Ceylon}")
    private String fromName;

    public EmailSenderService(JavaMailSender mailSender) {
        this.mailSender = mailSender;
    }

    /**
     * Sends the branded verification-code email (HTML with a plain-text
     * fallback part). Throws {@link EmailDeliveryException} on failure so the
     * caller can present a user-friendly error instead of crashing.
     */
    public void sendVerificationCode(String toAddress, String recipientName, String code, int expiryMinutes) {
        String html = EmailTemplates.verificationCodeHtml(recipientName, code, expiryMinutes);
        String text = EmailTemplates.verificationCodePlainText(recipientName, code, expiryMinutes);
        send(toAddress, "Your Explore Ceylon verification code", text, html);
    }

    /**
     * Sends the KYC approval confirmation email.
     */
    public void sendKycApproved(String toAddress, String recipientName) {
        String html = EmailTemplates.kycApprovedHtml(recipientName);
        String text = EmailTemplates.kycApprovedPlainText(recipientName);
        send(toAddress, "Your Identity Verification is Approved! - Explore Ceylon", text, html);
    }

    /**
     * Sends the KYC rejection email with the specified reason.
     */
    public void sendKycRejected(String toAddress, String recipientName, String reason) {
        String html = EmailTemplates.kycRejectedHtml(recipientName, reason);
        String text = EmailTemplates.kycRejectedPlainText(recipientName, reason);
        send(toAddress, "Identity Verification Update - Explore Ceylon", text, html);
    }

    /**
     * Sends the booking confirmation email asynchronously.
     * Catches and logs any exceptions so payment transactions are never failed or rolled back.
     */
    @Async
    public void sendBookingConfirmation(BookingConfirmationDetails details) {
        if (details == null || details.getCustomerEmail() == null || details.getCustomerEmail().isBlank()) {
            log.warn("Cannot send booking confirmation email — recipient email is missing");
            return;
        }
        try {
            String prefix = "VEHICLE".equalsIgnoreCase(details.getBookingType()) ? "VBK-" : "GBK-";
            String subject = "Booking Confirmed! #" + prefix + details.getBookingId() + " - Explore Ceylon";
            String html = EmailTemplates.bookingConfirmedHtml(details);
            String text = EmailTemplates.bookingConfirmedPlainText(details);
            send(details.getCustomerEmail(), subject, text, html);
            log.info("Booking confirmation email sent to {} for booking #{}", details.getCustomerEmail(), details.getBookingId());
        } catch (Exception ex) {
            log.error("Failed to send booking confirmation email to {} for booking #{}: {}",
                    details.getCustomerEmail(), details.getBookingId(), ex.getMessage());
        }
    }

    private void send(String toAddress, String subject, String plainText, String html) {
        if (fromAddress == null || fromAddress.isBlank()) {
            throw new EmailDeliveryException("Email delivery is not configured on this server.");
        }
        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");
            helper.setFrom(fromAddress, fromName);
            helper.setTo(toAddress);
            helper.setSubject(subject);
            helper.setText(plainText, html);
            mailSender.send(message);
            log.info("Email sent to {} (subject: {})", toAddress, subject);
        } catch (MailException | java.io.UnsupportedEncodingException | jakarta.mail.MessagingException ex) {
            log.error("Failed to send email to {}", toAddress, ex);
            throw new EmailDeliveryException("We couldn't send the verification email. Please try again shortly.", ex);
        }
    }

    public static class EmailDeliveryException extends RuntimeException {
        public EmailDeliveryException(String message) {
            super(message);
        }

        public EmailDeliveryException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
