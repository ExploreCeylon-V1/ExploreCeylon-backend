package com.exploreceylon.backend.service;

import com.exploreceylon.backend.model.VerificationCode.CodeType;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

/**
 * The active {@link CodeSender}: in dev mode it defers to {@link LogCodeSender}
 * (console only, as before); otherwise it dispatches real email/SMS via
 * {@link EmailSenderService} / {@link SmsSenderService}. Delivery failures are
 * logged with full detail and re-thrown as a plain {@link RuntimeException}
 * with a user-friendly message so {@code GlobalExceptionHandler} turns them
 * into a clean 400 instead of a 500/crash.
 */
@Component
@Primary
@Slf4j
public class CompositeCodeSender implements CodeSender {

    private final LogCodeSender logCodeSender;
    private final EmailSenderService emailSenderService;
    private final SmsSenderService smsSenderService;

    @Value("${app.dev-mode:true}")
    private boolean devMode;

    private static final int EXPIRY_MINUTES = 10;

    public CompositeCodeSender(LogCodeSender logCodeSender,
                                EmailSenderService emailSenderService,
                                SmsSenderService smsSenderService) {
        this.logCodeSender = logCodeSender;
        this.emailSenderService = emailSenderService;
        this.smsSenderService = smsSenderService;
    }

    @Override
    public void send(String destination, CodeType type, String code) {
        if (devMode) {
            logCodeSender.send(destination, type, code);
            return;
        }
        try {
            // PASSWORD_RESET is email-delivered too — only PHONE routes to SMS.
            if (type == CodeType.PHONE) {
                smsSenderService.sendVerificationCode(destination, code);
            } else {
                emailSenderService.sendVerificationCode(destination, null, code, EXPIRY_MINUTES);
            }
        } catch (RuntimeException ex) {
            log.error("Verification code delivery failed for {} ({})", destination, type, ex);
            throw new RuntimeException("We couldn't deliver your verification code right now. Please try again in a moment.");
        }
    }
}
