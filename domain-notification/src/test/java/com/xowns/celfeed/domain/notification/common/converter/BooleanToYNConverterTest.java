package com.xowns.celfeed.domain.notification.common.converter;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class BooleanToYNConverterTest {

    private final BooleanToYNConverter converter = new BooleanToYNConverter();

    @Test
    @DisplayName("true 는 Y, false 와 null 은 N 으로 저장한다")
    void toDatabaseColumn() {
        assertThat(converter.convertToDatabaseColumn(true)).isEqualTo("Y");
        assertThat(converter.convertToDatabaseColumn(false)).isEqualTo("N");
        assertThat(converter.convertToDatabaseColumn(null)).isEqualTo("N");
    }

    @Test
    @DisplayName("Y 만 true 이고, 그 외 값(N, 소문자 y, null)은 false 로 읽는다")
    void toEntityAttribute() {
        assertThat(converter.convertToEntityAttribute("Y")).isTrue();
        assertThat(converter.convertToEntityAttribute("N")).isFalse();
        assertThat(converter.convertToEntityAttribute("y")).isFalse();
        assertThat(converter.convertToEntityAttribute(null)).isFalse();
    }
}
