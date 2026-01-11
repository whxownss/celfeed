package com.xowns.celfeed.dto.post;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;

@Getter
public class PostRequest {

    @NotBlank(message = "글 내용을 입력해 주세요.")
    @Size(min = 5, max = 250, message = "글 내용은 5자 이상 250자 이내로 입력해 주세요.")
    private String content;
}
