package com.minibank.fraud.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import org.springframework.data.domain.Page;

import java.util.List;

/**
 * Stable JSON shape for paginated results (Spring's own Page is not a stable API contract).
 */
@Schema(description = "One page of results")
public record PageResponse<T>(
        @Schema(description = "Items on this page")
        List<T> content,

        @Schema(description = "Zero-based page index", example = "0")
        int page,

        @Schema(description = "Requested page size", example = "20")
        int size,

        @Schema(description = "Total number of items across all pages", example = "42")
        long totalElements,

        @Schema(description = "Total number of pages", example = "3")
        int totalPages
) {
    public static <T> PageResponse<T> of(Page<T> page) {
        return new PageResponse<>(page.getContent(), page.getNumber(), page.getSize(),
                page.getTotalElements(), page.getTotalPages());
    }
}
