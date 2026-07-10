package com.exploreceylon.backend.repository;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.Query;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

// Vehicle bookings and guide bookings are two separate tables with no
// shared parent — the admin Booking Management page needs one unified,
// searchable, sortable, paginated list across both. Rather than loading
// every row of both tables into Java (the old approach) or bolting a
// fake shared entity onto the schema, this runs one UNION ALL query and
// lets PostgreSQL do the filtering/sorting/paging, with a matching COUNT
// query for the total. ORDER BY column is whitelisted (see SORT_COLUMNS)
// since column names can't be bind parameters.
@Repository
public class AdminBookingQueryRepository {

    @PersistenceContext
    private EntityManager em;

    private static final Map<String, String> SORT_COLUMNS = Map.of(
            "startDate", "start_date",
            "totalCost", "total_cost",
            "status", "status",
            "createdAt", "created_at"
    );

    private static final String UNION_SQL =
            "SELECT vb.id AS id, 'VEHICLE' AS type, vb.status AS status, " +
            "       u.id AS customer_id, u.name AS customer_name, u.email AS customer_email, " +
            "       v.id AS provider_id, v.name AS provider_name, " +
            "       vb.trip_id AS trip_id, vb.pickup_date AS start_date, vb.dropoff_date AS end_date, " +
            "       vb.total_cost AS total_cost, vb.created_at AS created_at " +
            "FROM vehicle_bookings vb " +
            "JOIN users u ON u.id = vb.user_id " +
            "JOIN vehicles v ON v.id = vb.vehicle_id " +
            "UNION ALL " +
            "SELECT gb.id, 'GUIDE', gb.status, " +
            "       u.id, u.name, u.email, " +
            "       g.id, g.full_name, " +
            "       gb.trip_id, gb.start_date, gb.end_date, " +
            "       gb.total_cost, gb.created_at " +
            "FROM guide_bookings gb " +
            "JOIN users u ON u.id = gb.user_id " +
            "JOIN tour_guides g ON g.id = gb.guide_id";

    public record BookingRow(
            Long id, String type, String status,
            Long customerId, String customerName, String customerEmail,
            Long providerId, String providerName,
            Long tripId, LocalDate startDate, LocalDate endDate,
            Double totalCost, java.time.LocalDateTime createdAt) {}

    public record Result(List<BookingRow> rows, long totalElements) {}

    public Result search(
            String type, String status, String search, String customer, String provider,
            LocalDate dateFrom, LocalDate dateTo,
            String sortBy, String sortDir, int page, int size) {

        StringBuilder where = new StringBuilder(" WHERE 1=1");
        List<Object[]> params = new ArrayList<>(); // {name, value}

        if (type != null && !type.isBlank() && !type.equalsIgnoreCase("ALL")) {
            where.append(" AND type = :type");
            params.add(new Object[]{"type", type.toUpperCase()});
        }
        if (status != null && !status.isBlank() && !status.equalsIgnoreCase("ALL")) {
            where.append(" AND status = :status");
            params.add(new Object[]{"status", status.toUpperCase()});
        }
        if (search != null && !search.isBlank()) {
            where.append(" AND (LOWER(customer_name) LIKE :search OR LOWER(customer_email) LIKE :search OR LOWER(provider_name) LIKE :search)");
            params.add(new Object[]{"search", "%" + search.trim().toLowerCase() + "%"});
        }
        if (customer != null && !customer.isBlank()) {
            where.append(" AND (LOWER(customer_name) LIKE :customer OR LOWER(customer_email) LIKE :customer)");
            params.add(new Object[]{"customer", "%" + customer.trim().toLowerCase() + "%"});
        }
        if (provider != null && !provider.isBlank()) {
            where.append(" AND LOWER(provider_name) LIKE :provider");
            params.add(new Object[]{"provider", "%" + provider.trim().toLowerCase() + "%"});
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
        List<BookingRow> rows = rawRows.stream().map(this::mapRow).toList();

        return new Result(rows, total);
    }

    private BookingRow mapRow(Object[] r) {
        return new BookingRow(
                ((Number) r[0]).longValue(),
                (String) r[1],
                (String) r[2],
                r[3] != null ? ((Number) r[3]).longValue() : null,
                (String) r[4],
                (String) r[5],
                r[6] != null ? ((Number) r[6]).longValue() : null,
                (String) r[7],
                r[8] != null ? ((Number) r[8]).longValue() : null,
                toLocalDate(r[9]),
                toLocalDate(r[10]),
                r[11] != null ? ((Number) r[11]).doubleValue() : null,
                r[12] != null ? ((Timestamp) r[12]).toLocalDateTime() : null
        );
    }

    private LocalDate toLocalDate(Object value) {
        if (value == null) return null;
        if (value instanceof java.sql.Date d) return d.toLocalDate();
        if (value instanceof LocalDate ld) return ld;
        return null;
    }
}
