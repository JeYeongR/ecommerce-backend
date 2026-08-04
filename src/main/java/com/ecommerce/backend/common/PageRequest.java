package com.ecommerce.backend.common;

import org.springframework.data.domain.Pageable;

public record PageRequest(Integer page, Integer size) {

    private static final int DEFAULT_PAGE = 0;
    private static final int DEFAULT_SIZE = 20;
    private static final int MAX_SIZE = 100;

    public Pageable toPageable() {
        int resolvedPage = page != null ? page : DEFAULT_PAGE;
        int resolvedSize = size != null ? Math.min(size, MAX_SIZE) : DEFAULT_SIZE;
        return org.springframework.data.domain.PageRequest.of(resolvedPage, resolvedSize);
    }
}
