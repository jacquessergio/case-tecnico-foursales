package com.foursales.ecommerce.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.data.domain.Page;

import java.util.List;

/**
 * Generic wrapper for paginated responses
 * Provides a cleaner API contract than Spring's Page interface
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Generic paginated response containing data and pagination metadata")
public class PagedResponse<T> {

    @Schema(description = "List of items in the current page", required = true)
    private List<T> content;

    @Schema(description = "Current page number - zero-based", example = "0", required = true)
    private int page;

    @Schema(description = "Number of items per page", example = "20", required = true)
    private int size;

    @Schema(description = "Total number of elements across all pages", example = "150", required = true)
    private long totalElements;

    @Schema(description = "Total number of available pages", example = "8", required = true)
    private int totalPages;

    @Schema(description = "Indicates if this is the first page", example = "true", required = true)
    private boolean first;

    @Schema(description = "Indicates if this is the last page", example = "false", required = true)
    private boolean last;

    @Schema(description = "Number of elements in the current page", example = "20", required = true)
    private int numberOfElements;

    @Schema(description = "Indicates if the page is empty", example = "false", required = true)
    private boolean empty;

    /**
     * Factory method to create PagedResponse from Spring Page
     */
    public static <T> PagedResponse<T> of(Page<T> page) {
        return new PagedResponse<>(
                page.getContent(),
                page.getNumber(),
                page.getSize(),
                page.getTotalElements(),
                page.getTotalPages(),
                page.isFirst(),
                page.isLast(),
                page.getNumberOfElements(),
                page.isEmpty());
    }
}
