package com.ritham.erp.common.response;

import lombok.Builder;
import lombok.Getter;

import java.util.List;

/**
 * Paginated list response embedded as the {@code data} field in {@link ApiResponse}.
 *
 * @param <T> Type of each item
 */
@Getter
@Builder
public class PageResponse<T> {

    private final List<T>  content;
    private final int      page;
    private final int      size;
    private final long     totalElements;
    private final int      totalPages;
    private final boolean  first;
    private final boolean  last;

    public static <T> PageResponse<T> of(org.springframework.data.domain.Page<T> page) {
        return PageResponse.<T>builder()
                .content(page.getContent())
                .page(page.getNumber())
                .size(page.getSize())
                .totalElements(page.getTotalElements())
                .totalPages(page.getTotalPages())
                .first(page.isFirst())
                .last(page.isLast())
                .build();
    }
}
