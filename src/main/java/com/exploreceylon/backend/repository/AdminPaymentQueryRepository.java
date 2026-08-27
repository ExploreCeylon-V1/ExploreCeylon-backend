package com.exploreceylon.backend.repository;

import com.exploreceylon.backend.dto.admin.AdminPaymentSummaryResponse;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.Query;
import org.springframework.stereotype.Repository;

import java.sql.Date;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Repository
public class AdminPaymentQueryRepository {

    @PersistenceContext
    private EntityManager em;

    private static final Map<String, String> SORT_COLUMNS = Map.of(
            "createdAt", "created_at",
            "startDate", "start_date",
            "endDate", "end_date",
            "totalCost", "total_cost",
            "status", "status"
    );

    private static final String UNION_SQL =
            "SELECT vb.id AS id, 'VEHICLE' AS type, vb.status AS status, " +
            "       u.id AS customer_id, u.name AS customer_name, u.email AS customer_email, u.phone AS customer_phone, " +
            "       v.id AS provider_id, v.name AS provider_name, " +
            "       vb.trip_id AS trip_id, t.title AS trip_title, " +
            "       vb.pickup_date AS start_date, vb.dropoff_date AS end_date, " +
            "       vb.total_cost AS total_cost, vb.advance_amount AS advance_amount, vb.balance_amount AS balance_amount, " +
            "       vb.created_at AS created_at, " +
            "       (SELECT vp.paid_at FROM vehicle_payments vp WHERE vp.vehicle_booking_id = vb.id AND vp.payment_phase = 'ADVANCE' AND vp.status = 'COMPLETED' ORDER BY vp.id DESC LIMIT 1) AS initial_paid_at, " +
            "       (SELECT vp.paid_at FROM vehicle_payments vp WHERE vp.vehicle_booking_id = vb.id AND vp.payment_phase = 'FINAL' AND vp.status = 'COMPLETED' ORDER BY vp.id DESC LIMIT 1) AS final_paid_at " +
            "FROM vehicle_bookings vb " +
            "JOIN users u ON u.id = vb.user_id " +
            "JOIN vehicles v ON v.id = vb.vehicle_id " +
            "LEFT JOIN trips t ON t.id = vb.trip_id " +
            "WHERE vb.status IN ('CONFIRMED', 'COMPLETED') " +
            "UNION ALL " +
            "SELECT gb.id AS id, 'GUIDE' AS type, gb.status AS status, " +
            "       u.id AS customer_id, u.name AS customer_name, u.email AS customer_email, u.phone AS customer_phone, " +
            "       g.id AS provider_id, g.full_name AS provider_name, " +
            "       gb.trip_id AS trip_id, t.title AS trip_title, " +
            "       gb.start_date AS start_date, gb.end_date AS end_date, " +
            "       gb.total_cost AS total_cost, gb.advance_amount AS advance_amount, gb.balance_amount AS balance_amount, " +
            "       gb.created_at AS created_at, " +
            "       (SELECT gp.paid_at FROM guide_payments gp WHERE gp.guide_booking_id = gb.id AND gp.payment_phase = 'ADVANCE' AND gp.status = 'COMPLETED' ORDER BY gp.id DESC LIMIT 1) AS initial_paid_at, " +
            "       (SELECT gp.paid_at FROM guide_payments gp WHERE gp.guide_booking_id = gb.id AND gp.payment_phase = 'FINAL' AND gp.status = 'COMPLETED' ORDER BY gp.id DESC LIMIT 1) AS final_paid_at " +
            "FROM guide_bookings gb " +
            "JOIN users u ON u.id = gb.user_id " +
            "JOIN tour_guides g ON g.id = gb.guide_id " +
            "LEFT JOIN trips t ON t.id = gb.trip_id " +
            "WHERE gb.status IN ('CONFIRMED', 'COMPLETED')";

    public record PaymentRow(
            Long id, String type, String status,
            Long customerId, String customerName, String customerEmail, String customerPhone,
            Long providerId, String providerName,
            Long tripId, String tripTitle,
            LocalDate startDate, LocalDate endDate,
            Double totalCost, Double advanceAmount, Double balanceAmount,
            LocalDateTime createdAt,
            LocalDateTime initialPaidAt,
            LocalDateTime finalPaidAt) {}

    public record Result(List<PaymentRow> rows, long totalElements) {}

    public Result search(
            String type, String completionStatus, String search,
            LocalDate dateFrom, LocalDate dateTo,
            String sortBy, String sortDir, int page, int size) {

        StringBuilder where = new StringBuilder(" WHERE 1=1");
        List<Object[]> params = new ArrayList<>();

        if (type != null && !type.isBlank() && !type.equalsIgnoreCase("ALL")) {
            where.append(" AND type = :type");
            params.add(new Object[]{"type", type.toUpperCase()});
        }

        LocalDate today = LocalDate.now();
        if (completionStatus != null && !completionStatus.isBlank() && !completionStatus.equalsIgnoreCase("ALL")) {
            switch (completionStatus.toUpperCase()) {
                case "PARTIAL_20", "20% PAID", "PARTIAL" -> {
                    where.append(" AND status = 'CONFIRMED'");
                }
                case "FULL_100", "100% PAID", "FULL", "COMPLETED" -> {
                    where.append(" AND status = 'COMPLETED'");
                }
                case "OVERDUE" -> {
                    where.append(" AND status = 'CONFIRMED' AND end_date < :today");
                    params.add(new Object[]{"today", today});
                }
            }
        }

        if (search != null && !search.isBlank()) {
            where.append(" AND (LOWER(customer_name) LIKE :search OR LOWER(customer_email) LIKE :search OR LOWER(provider_name) LIKE :search OR CAST(id AS VARCHAR) LIKE :search)");
            params.add(new Object[]{"search", "%" + search.trim().toLowerCase() + "%"});
        }

        if (dateFrom != null) {
            where.append(" AND start_date >= :dateFrom");
            params.add(new Object[]{"dateFrom", dateFrom});
        }
        if (dateTo != null) {
            where.append(" AND start_date <= :dateTo");
            params.add(new Object[]{"dateTo", dateTo});
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
        List<PaymentRow> rows = rawRows.stream().map(this::mapRow).toList();

        return new Result(rows, total);
    }

    public AdminPaymentSummaryResponse getSummary(LocalDate today) {
        String sql = "SELECT " +
                "  COALESCE(SUM(CASE WHEN status = 'COMPLETED' THEN total_cost ELSE advance_amount END), 0.0) AS total_revenue, " +
                "  COUNT(CASE WHEN status = 'CONFIRMED' THEN 1 END) AS partial_20_count, " +
                "  COUNT(CASE WHEN status = 'COMPLETED' THEN 1 END) AS full_100_count, " +
                "  COUNT(CASE WHEN status = 'CONFIRMED' AND end_date < :today THEN 1 END) AS overdue_count " +
                "FROM (" + UNION_SQL + ") combined";

        Query q = em.createNativeQuery(sql);
        q.setParameter("today", today != null ? today : LocalDate.now());

        Object[] result = (Object[]) q.getSingleResult();
        Double totalRev = result[0] != null ? ((Number) result[0]).doubleValue() : 0.0;
        Long partialCount = result[1] != null ? ((Number) result[1]).longValue() : 0L;
        Long fullCount = result[2] != null ? ((Number) result[2]).longValue() : 0L;
        Long overdueCount = result[3] != null ? ((Number) result[3]).longValue() : 0L;

        return AdminPaymentSummaryResponse.builder()
                .totalRevenueCollected(totalRev)
                .partial20Count(partialCount)
                .full100Count(fullCount)
                .overdueCount(overdueCount)
                .build();
    }

    private PaymentRow mapRow(Object[] r) {
        return new PaymentRow(
                ((Number) r[0]).longValue(),
                (String) r[1],
                (String) r[2],
                r[3] != null ? ((Number) r[3]).longValue() : null,
                (String) r[4],
                (String) r[5],
                (String) r[6],
                r[7] != null ? ((Number) r[7]).longValue() : null,
                (String) r[8],
                r[9] != null ? ((Number) r[9]).longValue() : null,
                (String) r[10],
                toLocalDate(r[11]),
                toLocalDate(r[12]),
                r[13] != null ? ((Number) r[13]).doubleValue() : 0.0,
                r[14] != null ? ((Number) r[14]).doubleValue() : 0.0,
                r[15] != null ? ((Number) r[15]).doubleValue() : 0.0,
                toLocalDateTime(r[16]),
                toLocalDateTime(r[17]),
                toLocalDateTime(r[18])
        );
    }

    private LocalDate toLocalDate(Object value) {
        if (value == null) return null;
        if (value instanceof Date d) return d.toLocalDate();
        if (value instanceof LocalDate ld) return ld;
        return null;
    }

    private LocalDateTime toLocalDateTime(Object value) {
        if (value == null) return null;
        if (value instanceof Timestamp ts) return ts.toLocalDateTime();
        if (value instanceof LocalDateTime ldt) return ldt;
        return null;
    }
}
