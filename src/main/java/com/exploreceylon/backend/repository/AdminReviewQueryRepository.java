package com.exploreceylon.backend.repository;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.Query;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

// Destination/Gem/Guide/Vehicle reviews are four independent tables with
// no shared parent (see the Phase 1 audit note on this). Same approach as
// AdminBookingQueryRepository: one UNION ALL native query so the admin
// Review Moderation list filters/sorts/pages inside PostgreSQL instead of
// loading all four tables into Java on every request.
@Repository
public class AdminReviewQueryRepository {

    @PersistenceContext
    private EntityManager em;

    private static final Map<String, String> SORT_COLUMNS = Map.of(
            "rating", "rating",
            "entity", "entity_name",
            "createdAt", "created_at"
    );

    private static final String UNION_SQL =
            "SELECT dr.id AS id, 'DESTINATION' AS entity_type, dr.destination_id AS entity_id, d.name AS entity_name, " +
            "       CAST(NULL AS BIGINT) AS reviewer_user_id, dr.traveler_name AS reviewer_name, " +
            "       dr.rating AS rating, dr.comment AS comment, dr.created_at AS created_at " +
            "FROM destination_reviews dr JOIN destinations d ON d.id = dr.destination_id " +
            "UNION ALL " +
            "SELECT gr.id, 'GEM', gr.gem_id, hg.title, " +
            "       CAST(NULL AS BIGINT), gr.traveler_name, " +
            "       gr.rating, gr.comment, gr.created_at " +
            "FROM gem_reviews gr JOIN hidden_gems hg ON hg.id = gr.gem_id " +
            "UNION ALL " +
            "SELECT gur.id, 'GUIDE', gur.guide_id, tg.full_name, " +
            "       u1.id, u1.name, " +
            "       gur.rating, gur.comment, gur.created_at " +
            "FROM guide_reviews gur " +
            "JOIN tour_guides tg ON tg.id = gur.guide_id " +
            "JOIN users u1 ON u1.id = gur.user_id " +
            "UNION ALL " +
            "SELECT vr.id, 'VEHICLE', vr.vehicle_id, v.name, " +
            "       u2.id, u2.name, " +
            "       vr.rating, vr.comment, vr.created_at " +
            "FROM vehicle_reviews vr " +
            "JOIN vehicles v ON v.id = vr.vehicle_id " +
            "JOIN users u2 ON u2.id = vr.user_id";

    public record ReviewRow(
            Long id, String entityType, Long entityId, String entityName,
            Long reviewerUserId, String reviewerName,
            Integer rating, String comment, LocalDateTime createdAt) {}

    public record Result(List<ReviewRow> rows, long totalElements) {}

    public Result search(
            String entityType, String search, Integer rating,
            LocalDate dateFrom, LocalDate dateTo,
            String sortBy, String sortDir, int page, int size) {

        StringBuilder where = new StringBuilder(" WHERE 1=1");
        List<Object[]> params = new ArrayList<>();

        if (entityType != null && !entityType.isBlank() && !entityType.equalsIgnoreCase("ALL")) {
            where.append(" AND entity_type = :entityType");
            params.add(new Object[]{"entityType", entityType.toUpperCase()});
        }
        if (rating != null) {
            where.append(" AND rating = :rating");
            params.add(new Object[]{"rating", rating});
        }
        if (search != null && !search.isBlank()) {
            where.append(" AND (LOWER(entity_name) LIKE :search OR LOWER(reviewer_name) LIKE :search OR LOWER(COALESCE(comment, '')) LIKE :search)");
            params.add(new Object[]{"search", "%" + search.trim().toLowerCase() + "%"});
        }
        if (dateFrom != null) {
            where.append(" AND created_at >= :dateFrom");
            params.add(new Object[]{"dateFrom", dateFrom.atStartOfDay()});
        }
        if (dateTo != null) {
            where.append(" AND created_at < :dateTo");
            params.add(new Object[]{"dateTo", dateTo.plusDays(1).atStartOfDay()});
        }

        String sortColumn = SORT_COLUMNS.getOrDefault(sortBy, "created_at");
        String direction = "asc".equalsIgnoreCase(sortDir) ? "ASC" : "DESC";

        String countSql = "SELECT COUNT(*) FROM (" + UNION_SQL + ") combined" + where;
        Query countQuery = em.createNativeQuery(countSql);
        params.forEach(p -> countQuery.setParameter((String) p[0], p[1]));
        long total = ((Number) countQuery.getSingleResult()).longValue();

        String dataSql = "SELECT * FROM (" + UNION_SQL + ") combined" + where +
                " ORDER BY " + sortColumn + " " + direction +
                " LIMIT :limit OFFSET :offset";
        Query dataQuery = em.createNativeQuery(dataSql);
        params.forEach(p -> dataQuery.setParameter((String) p[0], p[1]));
        dataQuery.setParameter("limit", size);
        dataQuery.setParameter("offset", (long) page * size);

        @SuppressWarnings("unchecked")
        List<Object[]> rawRows = dataQuery.getResultList();
        List<ReviewRow> rows = rawRows.stream().map(this::mapRow).toList();

        return new Result(rows, total);
    }

    // 1-5 star histogram across all 4 review tables, for Analytics —
    // a single grouped aggregate query, not a full-table load.
    @SuppressWarnings("unchecked")
    public List<Object[]> ratingDistribution() {
        String sql = "SELECT rating, COUNT(*) FROM (" + UNION_SQL + ") combined GROUP BY rating ORDER BY rating";
        return em.createNativeQuery(sql).getResultList();
    }

    // Total review count across all 4 tables in one query, for the
    // dashboard's "Total Reviews" stat.
    public long countAll() {
        String sql = "SELECT COUNT(*) FROM (" + UNION_SQL + ") combined";
        return ((Number) em.createNativeQuery(sql).getSingleResult()).longValue();
    }

    private ReviewRow mapRow(Object[] r) {
        return new ReviewRow(
                ((Number) r[0]).longValue(),
                (String) r[1],
                r[2] != null ? ((Number) r[2]).longValue() : null,
                (String) r[3],
                r[4] != null ? ((Number) r[4]).longValue() : null,
                (String) r[5],
                r[6] != null ? ((Number) r[6]).intValue() : null,
                (String) r[7],
                r[8] != null ? ((Timestamp) r[8]).toLocalDateTime() : null
        );
    }
}
