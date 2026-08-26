package com.exploreceylon.backend.specification;

import com.exploreceylon.backend.model.User;
import com.exploreceylon.backend.model.UserVerification;
import com.exploreceylon.backend.model.UserVerification.VerificationStatus;
import jakarta.persistence.criteria.Join;
import jakarta.persistence.criteria.JoinType;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.jpa.domain.Specification;

import java.util.ArrayList;
import java.util.List;

public class UserVerificationSpecifications {

    private UserVerificationSpecifications() {}

    public static Specification<UserVerification> withFilters(VerificationStatus status, String search) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();

            if (status != null) {
                predicates.add(cb.equal(root.get("status"), status));
            }

            if (search != null && !search.isBlank()) {
                String needle = "%" + search.trim().toLowerCase() + "%";
                Join<UserVerification, User> userJoin = root.join("user", JoinType.LEFT);
                predicates.add(cb.or(
                        cb.like(cb.lower(userJoin.get("name")), needle),
                        cb.like(cb.lower(userJoin.get("email")), needle),
                        cb.like(cb.lower(cb.coalesce(userJoin.get("phone"), "")), needle),
                        cb.like(cb.lower(cb.coalesce(root.get("nationality"), "")), needle)
                ));
            }

            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }
}
