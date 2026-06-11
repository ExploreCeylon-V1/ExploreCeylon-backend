package com.exploreceylon.backend.service;

import com.exploreceylon.backend.dto.event.CreateEventRequest;
import com.exploreceylon.backend.dto.event.EventResponse;
import com.exploreceylon.backend.model.Event;
import com.exploreceylon.backend.repository.EventRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class EventService {

    private final EventRepository eventRepository;

    // ── Get All Events ─────────────────────────────────────
    public List<EventResponse> getAllEvents() {
        return eventRepository.findAll()
                .stream()
                .map(this::toResponse)
                .collect(Collectors.toList());
    }

    // ── Get Event By ID ────────────────────────────────────
    public EventResponse getEventById(Long id) {
        Event event = eventRepository.findById(id)
                .orElseThrow(() -> new RuntimeException(
                        "Event not found: " + id));
        return toResponse(event);
    }

    // ── Filter Events ──────────────────────────────────────
    public List<EventResponse> filterEvents(
            Integer month,
            String region,
            Event.EventCategory category) {

        List<Event> events;

        if (month != null && region != null) {
            events = eventRepository.findByMonthAndRegion(month, region);
        } else if (month != null) {
            events = eventRepository.findByMonth(month);
        } else if (region != null && category != null) {
            events = eventRepository
                    .findByRegionIgnoreCaseAndCategoryOrderByStartDate(
                            region, category);
        } else if (region != null) {
            events = eventRepository
                    .findByRegionIgnoreCaseOrderByStartDate(region);
        } else if (category != null) {
            events = eventRepository
                    .findByCategoryOrderByStartDate(category);
        } else {
            events = eventRepository.findAll();
        }

        return events.stream()
                .map(this::toResponse)
                .collect(Collectors.toList());
    }

    // ── Trip Sync — Events during trip dates ───────────────
    public List<EventResponse> getTripSyncEvents(
            LocalDate startDate, LocalDate endDate) {
        log.info("Syncing events for trip: {} to {}", startDate, endDate);
        return eventRepository
                .findEventsBetweenDates(startDate, endDate)
                .stream()
                .map(this::toResponse)
                .collect(Collectors.toList());
    }

    // ── Upcoming Events (next 30 days) ─────────────────────
    public List<EventResponse> getUpcomingEvents() {
        LocalDate today  = LocalDate.now();
        LocalDate future = today.plusDays(30);
        return eventRepository.findUpcomingEvents(today, future)
                .stream()
                .map(this::toResponse)
                .collect(Collectors.toList());
    }

    // ── Create Event (Admin) ───────────────────────────────
    public EventResponse createEvent(CreateEventRequest req) {
        log.info("Creating event: {}", req.getTitle());
        Event event = Event.builder()
                .title(req.getTitle())
                .description(req.getDescription())
                .category(req.getCategory())
                .region(req.getRegion())
                .startDate(req.getStartDate())
                .endDate(req.getEndDate())
                .imageUrls(req.getImageUrls() != null 
                    ? req.getImageUrls() : List.of())
                .isRecurring(req.getIsRecurring() != null
                        ? req.getIsRecurring() : true)
                .build();
        return toResponse(eventRepository.save(event));
    }

    // ── Update Event (Admin) ───────────────────────────────
    public EventResponse updateEvent(Long id, CreateEventRequest req) {
        Event event = eventRepository.findById(id)
                .orElseThrow(() -> new RuntimeException(
                        "Event not found: " + id));
        if (req.getTitle()       != null) event.setTitle(req.getTitle());
        if (req.getDescription() != null) event.setDescription(req.getDescription());
        if (req.getCategory()    != null) event.setCategory(req.getCategory());
        if (req.getRegion()      != null) event.setRegion(req.getRegion());
        if (req.getStartDate()   != null) event.setStartDate(req.getStartDate());
        if (req.getEndDate()     != null) event.setEndDate(req.getEndDate());
        if (req.getImageUrls()    != null) event.setImageUrls(req.getImageUrls());
        return toResponse(eventRepository.save(event));
    }

    // ── Delete Event (Admin) ───────────────────────────────
    public void deleteEvent(Long id) {
        eventRepository.deleteById(id);
        log.info("Event deleted: {}", id);
    }

    // ── MAPPER ─────────────────────────────────────────────
    private EventResponse toResponse(Event e) {
        EventResponse res = new EventResponse();
        res.setId(e.getId());
        res.setTitle(e.getTitle());
        res.setDescription(e.getDescription());
        res.setCategory(e.getCategory());
        res.setRegion(e.getRegion());
        res.setStartDate(e.getStartDate());
        res.setEndDate(e.getEndDate());
        res.setImageUrls(e.getImageUrls());
        res.setIsRecurring(e.getIsRecurring());
        res.setCreatedAt(e.getCreatedAt());
        return res;
    }
}