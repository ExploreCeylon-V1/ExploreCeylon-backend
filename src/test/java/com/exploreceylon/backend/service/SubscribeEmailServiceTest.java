package com.exploreceylon.backend.service;

import com.exploreceylon.backend.dto.SubscribeEmailResponseDTO;
import com.exploreceylon.backend.exception.DuplicateSubscriptionException;
import com.exploreceylon.backend.exception.ResourceNotFoundException;
import com.exploreceylon.backend.model.SubscribeEmail;
import com.exploreceylon.backend.repository.SubscribeEmailRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SubscribeEmailServiceTest {

    @Mock
    private SubscribeEmailRepository repository;

    @InjectMocks
    private SubscribeEmailService service;

    private SubscribeEmail sampleEntity;

    @BeforeEach
    void setUp() {
        sampleEntity = SubscribeEmail.builder()
                .id(1L)
                .email("test@example.com")
                .subscribedAt(Instant.now())
                .addedToGroup(false)
                .addedAt(null)
                .build();
    }

    @Test
    void subscribe_success() {
        when(repository.existsByEmail("test@example.com")).thenReturn(false);
        when(repository.save(any(SubscribeEmail.class))).thenReturn(sampleEntity);

        SubscribeEmailResponseDTO response = service.subscribe("  TEST@EXAMPLE.COM  ");

        assertNotNull(response);
        assertEquals(1L, response.getId());
        assertEquals("test@example.com", response.getEmail());
        assertFalse(response.isAddedToGroup());
        verify(repository, times(1)).existsByEmail("test@example.com");
        verify(repository, times(1)).save(any(SubscribeEmail.class));
    }

    @Test
    void subscribe_duplicateEmail_throwsDuplicateSubscriptionException() {
        when(repository.existsByEmail("duplicate@example.com")).thenReturn(true);

        DuplicateSubscriptionException ex = assertThrows(
                DuplicateSubscriptionException.class,
                () -> service.subscribe("duplicate@example.com")
        );

        assertEquals("This email is already subscribed", ex.getMessage());
        verify(repository, never()).save(any());
    }

    @Test
    void subscribe_invalidEmail_throwsIllegalArgumentException() {
        assertThrows(IllegalArgumentException.class, () -> service.subscribe("not-an-email"));
        assertThrows(IllegalArgumentException.class, () -> service.subscribe(""));
        assertThrows(IllegalArgumentException.class, () -> service.subscribe(null));
    }

    @Test
    void markAsAdded_stateTransitionSuccess() {
        when(repository.findById(1L)).thenReturn(Optional.of(sampleEntity));
        when(repository.save(any(SubscribeEmail.class))).thenAnswer(invocation -> invocation.getArgument(0));

        SubscribeEmailResponseDTO response = service.markAsAdded(1L);

        assertTrue(response.isAddedToGroup());
        assertNotNull(response.getAddedAt());
        verify(repository).save(sampleEntity);
    }

    @Test
    void markAsNotAdded_stateTransitionSuccess() {
        sampleEntity.setAddedToGroup(true);
        sampleEntity.setAddedAt(Instant.now());

        when(repository.findById(1L)).thenReturn(Optional.of(sampleEntity));
        when(repository.save(any(SubscribeEmail.class))).thenAnswer(invocation -> invocation.getArgument(0));

        SubscribeEmailResponseDTO response = service.markAsNotAdded(1L);

        assertFalse(response.isAddedToGroup());
        assertNull(response.getAddedAt());
        verify(repository).save(sampleEntity);
    }

    @Test
    void delete_success() {
        when(repository.findById(1L)).thenReturn(Optional.of(sampleEntity));

        service.delete(1L);

        verify(repository).delete(sampleEntity);
    }

    @Test
    void delete_notFound_throwsResourceNotFoundException() {
        when(repository.findById(99L)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> service.delete(99L));
    }
}
