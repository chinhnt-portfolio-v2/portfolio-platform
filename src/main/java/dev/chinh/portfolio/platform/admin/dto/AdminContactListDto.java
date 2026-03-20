package dev.chinh.portfolio.platform.admin.dto;

import java.util.List;

/**
 * Paginated list of contact submissions for admin view.
 *
 * @param content   page of submissions
 * @param page      current page number (0-indexed)
 * @param size      page size
 * @param totalElements  total number of submissions across all pages
 * @param totalPages    total number of pages
 */
public record AdminContactListDto(
        List<AdminContactDetailDto> content,
        int page,
        int size,
        long totalElements,
        int totalPages
) {}
