package com.exploreceylon.backend.controller;

import com.exploreceylon.backend.dto.event.CreateEventRequest;
import com.exploreceylon.backend.dto.event.EventResponse;
import com.exploreceylon.backend.model.Event;
import com.exploreceylon.backend.service.EventService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/v1/events")
@RequiredArgsConstructor
public class EventController {

    private final EventService eventService;

    // GET /api/v1/events
    @GetMapping
    public ResponseEntity<List<EventResponse>> getAllEvents(
            @RequestParam(required = false) Integer month,
            @RequestParam(required = false) String region,
            @RequestParam(required = false) Event.EventCategory category) {

        if (month != null || region != null || category != null) {
            return ResponseEntity.ok(
                    eventService.filterEvents(month, region, category));
        }
        return ResponseEntity.ok(eventService.getAllEvents());
    }

    // GET /api/v1/events/upcoming
    @GetMapping("/upcoming")
    public ResponseEntity<List<EventResponse>> getUpcoming() {
        return ResponseEntity.ok(eventService.getUpcomingEvents());
    }

    // GET /api/v1/events/trip-sync
    @GetMapping("/trip-sync")
    public ResponseEntity<List<EventResponse>> getTripSync(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
            LocalDate startDate,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
            LocalDate endDate) {
        return ResponseEntity.ok(
                eventService.getTripSyncEvents(startDate, endDate));
    }

    // GET /api/v1/events/{id}
    @GetMapping("/{id}")
    public ResponseEntity<EventResponse> getEventById(
            @PathVariable Long id) {
        return ResponseEntity.ok(eventService.getEventById(id));
    }

    // POST /api/v1/events (Admin)
    @PostMapping
    public ResponseEntity<EventResponse> createEvent(
            @Valid @RequestBody CreateEventRequest request) {
        return ResponseEntity.ok(eventService.createEvent(request));
    }

    // PUT /api/v1/events/{id} (Admin)
    @PutMapping("/{id}")
    public ResponseEntity<EventResponse> updateEvent(
            @PathVariable Long id,
            @RequestBody CreateEventRequest request) {
        return ResponseEntity.ok(
                eventService.updateEvent(id, request));
    }

    // DELETE /api/v1/events/{id} (Admin)
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteEvent(
            @PathVariable Long id) {
        eventService.deleteEvent(id);
        return ResponseEntity.ok().build();
    }
}