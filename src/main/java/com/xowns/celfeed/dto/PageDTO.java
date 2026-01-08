package com.xowns.celfeed.dto;

import lombok.Getter;
import org.springframework.data.domain.Page;

import java.util.List;

@Getter
public class PageDTO<T> {
    private List<T> content;
    private int currentPage;
    private boolean hasPrevious;
    private boolean hasNext;
    private int pageSize;
    private int totalPages;
    private long totalElements;

    private PageDTO(Page<T> page) {
        this.content = page.getContent();
        this.currentPage = page.getNumber();
        this.hasPrevious = page.hasPrevious();
        this.hasNext = page.hasNext();
        this.pageSize = page.getSize();
        this.totalPages = page.getTotalPages();
        this.totalElements = page.getTotalElements();
    }

    public static <D> PageDTO<D> of(Page<D> page) {
        return new PageDTO<>(page);
    }
}
