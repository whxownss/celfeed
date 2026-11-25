package com.xowns.celfeed.common.web.dto;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.SliceImpl;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class SliceDTOTest {

    @Test
    @DisplayName("SliceDTO 는 Slice 의 내용과 페이지 정보를 그대로 옮긴다")
    void sliceDTO() {
        SliceImpl<String> slice = new SliceImpl<>(List.of("a", "b"), PageRequest.of(1, 2), true);

        SliceDTO<String> dto = SliceDTO.of(slice);

        assertThat(dto.getContent()).containsExactly("a", "b");
        assertThat(dto.getCurrentPage()).isEqualTo(1);
        assertThat(dto.getPageSize()).isEqualTo(2);
        assertThat(dto.isHasPrevious()).isTrue();
        assertThat(dto.isHasNext()).isTrue();
    }

    @Test
    @DisplayName("첫 페이지이면서 마지막 페이지인 경우 이전/다음이 모두 없다")
    void singlePageSlice() {
        SliceImpl<String> slice = new SliceImpl<>(List.of("a"), PageRequest.of(0, 10), false);

        SliceDTO<String> dto = SliceDTO.of(slice);

        assertThat(dto.isHasPrevious()).isFalse();
        assertThat(dto.isHasNext()).isFalse();
    }

    @Test
    @DisplayName("PageDTO 는 Slice 정보에 전체 페이지 수와 전체 개수를 더한다")
    void pageDTO() {
        PageImpl<String> page = new PageImpl<>(List.of("a", "b"), PageRequest.of(0, 2), 5);

        PageDTO<String> dto = PageDTO.of(page);

        assertThat(dto.getContent()).containsExactly("a", "b");
        assertThat(dto.getCurrentPage()).isZero();
        assertThat(dto.isHasPrevious()).isFalse();
        assertThat(dto.isHasNext()).isTrue();
        assertThat(dto.getTotalElements()).isEqualTo(5);
        assertThat(dto.getTotalPages()).isEqualTo(3);
    }

    @Test
    @DisplayName("빈 결과도 변환할 수 있다")
    void emptyPage() {
        PageImpl<String> page = new PageImpl<>(List.of(), PageRequest.of(0, 10), 0);

        PageDTO<String> dto = PageDTO.of(page);

        assertThat(dto.getContent()).isEmpty();
        assertThat(dto.getTotalElements()).isZero();
        assertThat(dto.getTotalPages()).isZero();
        assertThat(dto.isHasNext()).isFalse();
    }
}
