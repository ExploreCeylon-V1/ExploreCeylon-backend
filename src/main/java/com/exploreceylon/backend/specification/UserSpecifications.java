package com.exploreceylon.backend.specification;

import com.exploreceylon.backend.model.User;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.jpa.domain.Specification;

import java.util.ArrayList;
import java.util.List;

// Builds the WHERE clause for the admin Users list at the database level —
// replaces the old findAll()-then-filter-in-Java approach so search/role/
// active/verified filters all execute as SQL predicates instead of loading
// every user row on every request.
public class UserSpecifications {

    private UserSpecifications() {}

    public static Specification<User> withFilters(
            String search, User.Role role, Boolean active,
            Boolean emailVerified, Boolean phoneVerified) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();

            if (search != null && !search.isBlank()) {
                String needle = "%" + search.trim().toLowerCase() + "%";
                predicates.add(cb.or(
                        cb.like(cb.lower(root.get("name")), needle),
                        cb.like(cb.lower(root.get("email")), needle),
                        cb.like(cb.lower(cb.coalesce(root.get("phone"), "")), needle)
                ));
            }
            if (role != null) {
                predicates.add(cb.equal(root.get("role"), role));
            }
            if (active != null) {
                predicates.add(cb.equal(root.get("active"), active));
            }
            if (emailVerified != null) {
                predicates.add(cb.equal(root.get("emailVerified"), emailVerified));
            }
            if (phoneVerified != null) {
                predicates.add(cb.equal(root.get("phoneVerified"), phoneVerified));
            }

            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }
}
