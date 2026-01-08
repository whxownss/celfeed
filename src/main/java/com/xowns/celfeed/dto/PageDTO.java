package com.xowns.celfeed.dto;

import lombok.Getter;
import org.springframework.data.domain.Page;

import java.util.List;

@Getter
public class PageDTO<T> extends SliceDTO<T> {
    private int totalPages;
    private long totalElements;

    private PageDTO(Page<T> page) {
        super(page);
        this.totalPages = page.getTotalPages();
        this.totalElements = page.getTotalElements();
    }

    public static <D> PageDTO<D> of(Page<D> page) {
        return new PageDTO<>(page);
    }
}