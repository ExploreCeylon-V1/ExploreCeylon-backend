package com.exploreceylon.backend.controller;

import com.exploreceylon.backend.dto.hotel.HotelResult;
import com.exploreceylon.backend.dto.hotel.HotelSearchRequest;
import com.exploreceylon.backend.service.HotelApiService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

import java.util.List;

@RestController
@RequestMapping("/api/v1/hotels")
@RequiredArgsConstructor
public class HotelController {

    private final HotelApiService hotelApiService;

    // POST /api/v1/hotels/search
    @PostMapping("/search")
    public Mono<ResponseEntity<List<HotelResult>>> searchHotels(
            @RequestBody HotelSearchRequest request) {
        return hotelApiService.searchHotels(request)
                .map(ResponseEntity::ok)
                .onErrorReturn(ResponseEntity.internalServerError().build());
    }

    // GET /api/v1/hotels/{hotelId}
    @GetMapping("/{hotelId}")
    public Mono<ResponseEntity<Object>> getHotelDetails(
            @PathVariable String hotelId) {
        return hotelApiService.getHotelDetails(hotelId)
                .map(result -> ResponseEntity.ok((Object) result))
                .onErrorReturn(ResponseEntity.notFound().build());
    }
}
