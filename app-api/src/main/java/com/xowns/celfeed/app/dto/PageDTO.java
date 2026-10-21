package com.xowns.celfeed.app.dto;

import com.xowns.celfeed.dto.SliceDTO;
import lombok.Getter;
import org.springframework.data.domain.Page;

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