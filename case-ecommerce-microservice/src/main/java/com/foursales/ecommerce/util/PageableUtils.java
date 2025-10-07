package com.foursales.ecommerce.util;

import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

import lombok.experimental.UtilityClass;

@UtilityClass
public class PageableUtils {

    private static final int MAX_PAGE_SIZE = 100;
    private static final String DEFAULT_SORT_FIELD = "createdAt";
    private static final Sort.Direction DEFAULT_SORT_DIRECTION = Sort.Direction.DESC;

    public static Pageable applyPaginationRules(Pageable pageable) {
        if (pageable.getSort().isUnsorted()) {
            return PageRequest.of(
                    pageable.getPageNumber(),
                    Math.min(pageable.getPageSize(), MAX_PAGE_SIZE),
                    Sort.by(DEFAULT_SORT_DIRECTION, DEFAULT_SORT_FIELD));
        }

        int safeSize = Math.min(pageable.getPageSize(), MAX_PAGE_SIZE);
        if (safeSize != pageable.getPageSize()) {
            return PageRequest.of(
                    pageable.getPageNumber(),
                    safeSize,
                    pageable.getSort());
        }

        return pageable;
    }
}
