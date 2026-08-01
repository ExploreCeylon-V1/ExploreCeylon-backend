package com.exploreceylon.backend.dto.admin;

import lombok.Builder;
import lombok.Data;

import java.util.List;

// Simple in-memory pagination wrapper — mirrors the rest of the codebase's
// findAll()-then-filter-in-Java style (no Pageable/Spring Data paging is
// used anywhere yet) so admin list endpoints can offer paging/sorting
// without introducing a second, inconsistent pattern.
@Data
@Builder
public class PageResponse<T> {
    private List<T> content;
    private long totalElements;
    private int totalPages;
    private int page;
    private int size;
}
