package com.exploreceylon.backend.service;

import com.exploreceylon.backend.exception.RateLimitException;
import com.exploreceylon.backend.model.User;
import com.exploreceylon.backend.model.VerificationCode;
import com.exploreceylon.backend.model.VerificationCode.CodeType;
import com.exploreceylon.backend.model.VerificationRateLimit;
import com.exploreceylon.backend.repository.UserRepository;
import com.exploreceylon.backend.repository.VerificationCodeRepository;
import com.exploreceylon.backend.repository.VerificationRateLimitRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.Duration;
import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class VerificationService {

    private final VerificationCodeRepository codeRepo;
    private final VerificationRateLimitRepository rateLimitRepo;
    private final UserRepository userRepo;
    private final CodeSender codeSender;

    @Value("${app.dev-mode:true}")
    private boolean devMode;

    // ── Rate-limit tuning (see application.properties) ─────────
    @Value("${app.verification.cooldown-seconds:60}")
    private long cooldownSeconds;              // min gap between sends
    @Value("${app.verification.max-sends-per-hour:5}")
    private int maxSendsPerHour;               // sends per rolling hour
    @Value("${app.verification.max-attempts:5}")
    private int maxAttempts;                   // failed verify attempts per code
    @Value("${app.verification.lock-minutes:30}")
    private long lockMinutes;                  // lockout after abuse

    private static final SecureRandom RANDOM = new SecureRandom();
    private static final int EXPIRY_MINUTES = 10;

    /**
     * Generate a 6-digit code, persist it, and "send" it — subject to rate
     * limiting. Throws {@link RateLimitException} (→ HTTP 429) if the caller is
     * inside the cooldown, over the hourly cap, or currently locked out.
     * Returns the code only in dev-mode (so tests/clients can read it).
     *
     * noRollbackFor: a rate-limit rejection still throws, but the lock/window
     * bookkeeping written just before must commit rather than roll back.
     */
    @Transactional(noRollbackFor = RateLimitException.class)
    public String generateAndSend(User user, CodeType type) {
        LocalDateTime now = LocalDateTime.now();
        VerificationRateLimit rl = rateLimitRepo.findByUserIdAndType(user.getId(), type)
                .orElseGet(() -> VerificationRateLimit.builder()
                        .userId(user.getId()).type(type)
                        .windowStart(now).sendsInWindow(0).build());

        // 1. Active lockout?
        if (rl.getLockedUntil() != null && now.isBefore(rl.getLockedUntil())) {
            throw new RateLimitException("Too many attempts. Try again in "
                    + minutesLeft(now, rl.getLockedUntil()) + " minute(s).");
        }

        // 2. 60-second cooldown between sends
        if (rl.getLastSentAt() != null) {
            long since = Duration.between(rl.getLastSentAt(), now).getSeconds();
            if (since < cooldownSeconds) {
                throw new RateLimitException("Please wait " + (cooldownSeconds - since)
                        + "s before requesting another code.");
            }
        }

        // 3. Roll the hourly window over if it's older than an hour
        if (rl.getWindowStart() == null
                || Duration.between(rl.getWindowStart(), now).toHours() >= 1) {
            rl.setWindowStart(now);
            rl.setSendsInWindow(0);
        }

        // 4. Hourly cap breached → 30-minute lockout
        if (rl.getSendsInWindow() >= maxSendsPerHour) {
            rl.setLockedUntil(now.plusMinutes(lockMinutes));
            rateLimitRepo.save(rl);
            throw new RateLimitException("Hourly limit reached. Try again in "
                    + lockMinutes + " minutes.");
        }

        // Passed the gates → mint + persist + send
        String code = String.format("%06d", RANDOM.nextInt(1_000_000));
        VerificationCode vc = VerificationCode.builder()
                .userId(user.getId())
                .type(type)
                .code(code)
                .expiresAt(now.plusMinutes(EXPIRY_MINUTES))
                .consumed(false)
                .attempts(0)
                .build();
        codeRepo.save(vc);

        rl.setSendsInWindow(rl.getSendsInWindow() + 1);
        rl.setLastSentAt(now);
        rateLimitRepo.save(rl);

        // PASSWORD_RESET is email-delivered too — only PHONE routes to the phone number.
        String destination = type == CodeType.PHONE ? user.getPhone() : user.getEmail();
        codeSender.send(destination, type, code);

        return devMode ? code : null;
    }

    /**
     * Verify a submitted code. Returns {@code false} for a wrong/expired/missing
     * code (with the attempt counted). Throws {@link RateLimitException} (→ 429)
     * if the channel is locked, or if this attempt is the one that trips the
     * per-code attempt limit. On success, flips the matching verified flag and
     * clears the rate-limit state.
     *
     * noRollbackFor: the attempt increment / burn + lockout written before the
     * throw must persist so repeated abuse actually accumulates.
     */
    @Transactional(noRollbackFor = RateLimitException.class)
    public boolean verify(User user, CodeType type, String submitted) {
        return doVerify(user, type, submitted, true);
    }

    /**
     * Same validation/attempt-tracking as {@link #verify}, but does NOT consume the
     * code or flip any verified flag. Used by the password-reset "verify code" step,
     * which is just a pre-check — the code is only actually spent at reset-password
     * time (see {@link com.exploreceylon.backend.service.AuthService#resetPassword}).
     */
    @Transactional(noRollbackFor = RateLimitException.class)
    public boolean checkOnly(User user, CodeType type, String submitted) {
        return doVerify(user, type, submitted, false);
    }

    private boolean doVerify(User user, CodeType type, String submitted, boolean consume) {
        LocalDateTime now = LocalDateTime.now();

        // Respect an active lockout on the verify path too
        VerificationRateLimit rl = rateLimitRepo.findByUserIdAndType(user.getId(), type).orElse(null);
        if (rl != null && rl.getLockedUntil() != null && now.isBefore(rl.getLockedUntil())) {
            throw new RateLimitException("Too many attempts. Try again in "
                    + minutesLeft(now, rl.getLockedUntil()) + " minute(s).");
        }

        if (submitted == null || submitted.isBlank()) return false;

        VerificationCode vc = codeRepo
                .findFirstByUserIdAndTypeAndConsumedFalseOrderByCreatedAtDesc(user.getId(), type)
                .orElse(null);

        if (vc == null) return false;
        if (vc.getExpiresAt().isBefore(now)) return false;

        if (!vc.getCode().equals(submitted.trim())) {
            vc.setAttempts(vc.getAttempts() + 1);
            if (vc.getAttempts() >= maxAttempts) {
                vc.setConsumed(true);           // burn the code
                codeRepo.save(vc);
                lock(user.getId(), type, now);  // repeated abuse → lockout
                throw new RateLimitException("Too many attempts. Verification locked for "
                        + lockMinutes + " minutes.");
            }
            codeRepo.save(vc);
            return false;
        }

        // Correct code
        if (!consume) return true;

        vc.setConsumed(true);
        codeRepo.save(vc);

        User managed = userRepo.findById(user.getId()).orElseThrow();
        if (type == CodeType.EMAIL) managed.setEmailVerified(true);
        else if (type == CodeType.PHONE) managed.setPhoneVerified(true);
        // PASSWORD_RESET has no verified-flag side effect.
        userRepo.save(managed);

        rateLimitRepo.deleteByUserIdAndType(user.getId(), type); // clean slate
        return true;
    }

    /**
     * Wipe pending codes + rate-limit state for a channel. Called when the
     * user changes their email or phone so the new value starts fresh.
     */
    @Transactional
    public void resetChannel(Long userId, CodeType type) {
        codeRepo.deleteByUserIdAndType(userId, type);
        rateLimitRepo.deleteByUserIdAndType(userId, type);
    }

    private void lock(Long userId, CodeType type, LocalDateTime now) {
        VerificationRateLimit rl = rateLimitRepo.findByUserIdAndType(userId, type)
                .orElseGet(() -> VerificationRateLimit.builder()
                        .userId(userId).type(type).windowStart(now).sendsInWindow(0).build());
        rl.setLockedUntil(now.plusMinutes(lockMinutes));
        rateLimitRepo.save(rl);
    }

    private long minutesLeft(LocalDateTime now, LocalDateTime until) {
        return Math.max(1, Duration.between(now, until).toMinutes() + 1);
    }
}
