package com.exploreceylon.backend.service;

import com.exploreceylon.backend.model.VerificationCode.CodeType;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Placeholder {@link CodeSender} — logs the code to the server console.
 * Replace with a real email/SMS sender once a provider is configured.
 */
@Component
@Slf4j
public class LogCodeSender implements CodeSender {

    @Override
    public void send(String destination, CodeType type, String code) {
        log.info("[VERIFICATION] {} code for {} = {} (dev delivery — no provider configured)",
                type, destination, code);
    }
}
