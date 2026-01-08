package com.xowns.celfeed.dto;

import lombok.Getter;
import org.springframework.data.domain.Slice;

import java.util.List;

@Getter
public class SliceDTO<T> {
    private List<T> content;
    private int currentPage;
    private boolean hasPrevious;
    private boolean hasNext;
    private int pageSize;

    protected SliceDTO(Slice<T> slice) {
        this.content = slice.getContent();
        this.currentPage = slice.getNumber();
        this.hasPrevious = slice.hasPrevious();
        this.hasNext = slice.hasNext();
        this.pageSize = slice.getSize();
    }

    public static <D> SliceDTO<D> of(Slice<D> slice) {
        return new SliceDTO<>(slice);
    }
}
