package com.exploreceylon.backend.service;

import com.exploreceylon.backend.dto.admin.AdminReviewResponse;
import com.exploreceylon.backend.dto.admin.BulkReviewDeleteRequest;
import com.exploreceylon.backend.dto.admin.PageResponse;
import com.exploreceylon.backend.repository.AdminReviewQueryRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.stream.Collectors;

// Aggregates the 4 independent review systems (Destination/Gem/Guide/Vehicle
// — see the Phase 1 audit note that they share no common table or base
// class) into a single moderation view, without changing how each domain
// stores or writes its own reviews. Listing is one UNION ALL query via
// AdminReviewQueryRepository so filter/sort/page all execute in
// PostgreSQL; delete still dispatches to each domain's own service so the
// existing rating-resync logic isn't duplicated.
@Service
@RequiredArgsConstructor
@Slf4j
public class AdminReviewService {

    private final AdminReviewQueryRepository adminReviewQueryRepository;

    private final DestinationReviewService destinationReviewService;
    private final Gemreviewservice          gemReviewService;
    private final TourGuideService          tourGuideService;
    private final LocalVehicleService       localVehicleService;

    public PageResponse<AdminReviewResponse> getAllReviews(
            String entityType, String search, Integer rating,
            LocalDate dateFrom, LocalDate dateTo,
            String sortBy, String sortDir, int page, int size) {

        int safePage = Math.max(page, 0);
        int safeSize = Math.max(size, 1);

        AdminReviewQueryRepository.Result result = adminReviewQueryRepository.search(
                entityType, search, rating, dateFrom, dateTo, sortBy, sortDir, safePage, safeSize);

        List<AdminReviewResponse> content = result.rows().stream()
                .map(r -> AdminReviewResponse.builder()
                        .id(r.id())
                        .entityType(r.entityType())
                        .entityId(r.entityId())
                        .entityName(r.entityName())
                        .reviewerUserId(r.reviewerUserId())
                        .reviewerName(r.reviewerName())
                        .rating(r.rating())
                        .comment(r.comment())
                        .createdAt(r.createdAt())
                        .build())
                .collect(Collectors.toList());

        int totalPages = (int) Math.ceil(result.totalElements() / (double) safeSize);

        return PageResponse.<AdminReviewResponse>builder()
                .content(content)
                .totalElements(result.totalElements())
                .totalPages(totalPages)
                .page(safePage)
                .size(safeSize)
                .build();
    }

    @Transactional
    public void deleteReview(String entityType, Long id) {
        switch (entityType == null ? "" : entityType.toUpperCase()) {
            case "DESTINATION" -> destinationReviewService.deleteReview(id);
            case "GEM"         -> gemReviewService.deleteReview(id);
            case "GUIDE"       -> tourGuideService.deleteReview(id);
            case "VEHICLE"     -> localVehicleService.deleteReview(id);
            default -> throw new RuntimeException("Unknown review entity type: " + entityType);
        }
        log.info("Admin deleted {} review: {}", entityType, id);
    }

    // ── Bulk Delete ──────────────────────────────────────────
    // Reviews span 4 unrelated tables/services with no shared batch-delete
    // path, so this loops deleteReview() per item (each call already does
    // a single-row delete + rating resync — there's no cheaper batched
    // equivalent without duplicating that resync logic per domain).
    @Transactional
    public int bulkDelete(List<BulkReviewDeleteRequest.ReviewRef> items) {
        int count = 0;
        for (BulkReviewDeleteRequest.ReviewRef ref : items) {
            deleteReview(ref.getEntityType(), ref.getId());
            count++;
        }
        return count;
    }
}
