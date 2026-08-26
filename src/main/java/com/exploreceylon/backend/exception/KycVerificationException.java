package com.exploreceylon.backend.exception;

import lombok.Getter;

@Getter
public class KycVerificationException extends RuntimeException {

    private final String code;
    private final String rejectionReason;

    public KycVerificationException(String code, String message) {
        super(message);
        this.code = code;
        this.rejectionReason = null;
    }

    public KycVerificationException(String code, String message, String rejectionReason) {
        super(message);
        this.code = code;
        this.rejectionReason = rejectionReason;
    }
}
