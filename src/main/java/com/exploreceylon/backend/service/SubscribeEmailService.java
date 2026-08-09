package com.exploreceylon.backend.service;

import com.exploreceylon.backend.dto.SubscribeEmailResponseDTO;
import com.exploreceylon.backend.exception.DuplicateSubscriptionException;
import com.exploreceylon.backend.exception.ResourceNotFoundException;
import com.exploreceylon.backend.model.SubscribeEmail;
import com.exploreceylon.backend.repository.SubscribeEmailRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class SubscribeEmailService {

    private final SubscribeEmailRepository repository;

    private static final Pattern EMAIL_PATTERN = Pattern.compile("^[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$");

    @Transactional
    public SubscribeEmailResponseDTO subscribe(String rawEmail) {
        if (rawEmail == null || rawEmail.isBlank()) {
            throw new IllegalArgumentException("Email is required");
        }
        String cleanEmail = rawEmail.trim().toLowerCase();
        if (!EMAIL_PATTERN.matcher(cleanEmail).matches()) {
            throw new IllegalArgumentException("Invalid email format");
        }
        if (repository.existsByEmail(cleanEmail)) {
            throw new DuplicateSubscriptionException("This email is already subscribed");
        }

        SubscribeEmail entity = SubscribeEmail.builder()
                .email(cleanEmail)
                .subscribedAt(Instant.now())
                .addedToGroup(false)
                .addedAt(null)
                .build();

        SubscribeEmail saved = repository.save(entity);
        log.info("New subscriber registered: {}", cleanEmail);
        return toDTO(saved);
    }

    @Transactional(readOnly = true)
    public List<SubscribeEmailResponseDTO> getAll(String status) {
        List<SubscribeEmail> list;
        if (status == null || status.isBlank() || "all".equalsIgnoreCase(status.trim())) {
            list = repository.findAllByOrderBySubscribedAtDesc();
        } else if ("added".equalsIgnoreCase(status.trim())) {
            list = repository.findByAddedToGroupOrderBySubscribedAtDesc(true);
        } else if ("not-added".equalsIgnoreCase(status.trim()) || "not_added".equalsIgnoreCase(status.trim())) {
            list = repository.findByAddedToGroupOrderBySubscribedAtDesc(false);
        } else {
            list = repository.findAllByOrderBySubscribedAtDesc();
        }

        return list.stream()
                .map(this::toDTO)
                .collect(Collectors.toList());
    }

    @Transactional
    public SubscribeEmailResponseDTO markAsAdded(Long id) {
        SubscribeEmail entity = repository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Subscriber not found with id: " + id));

        entity.setAddedToGroup(true);
        entity.setAddedAt(Instant.now());
        SubscribeEmail updated = repository.save(entity);
        log.info("Subscriber marked as added: {}", updated.getEmail());
        return toDTO(updated);
    }

    @Transactional
    public SubscribeEmailResponseDTO markAsNotAdded(Long id) {
        SubscribeEmail entity = repository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Subscriber not found with id: " + id));

        entity.setAddedToGroup(false);
        entity.setAddedAt(null);
        SubscribeEmail updated = repository.save(entity);
        log.info("Subscriber marked as not-added: {}", updated.getEmail());
        return toDTO(updated);
    }

    @Transactional
    public void delete(Long id) {
        SubscribeEmail entity = repository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Subscriber not found with id: " + id));

        repository.delete(entity);
        log.info("Subscriber record deleted: {}", entity.getEmail());
    }

    private SubscribeEmailResponseDTO toDTO(SubscribeEmail entity) {
        return SubscribeEmailResponseDTO.builder()
                .id(entity.getId())
                .email(entity.getEmail())
                .subscribedAt(entity.getSubscribedAt())
                .addedToGroup(Boolean.TRUE.equals(entity.getAddedToGroup()))
                .addedAt(entity.getAddedAt())
                .build();
    }
}
