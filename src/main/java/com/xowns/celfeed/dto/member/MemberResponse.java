package com.xowns.celfeed.dto.member;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.xowns.celfeed.domain.basic.Member;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
public class MemberResponse {
    private Long id;
    private String nickname;
    private String email;
    private String role;

    @JsonFormat(pattern = "yyyy-MM-dd")
    private LocalDateTime createdAt;

    private MemberResponse(Long id, String nickname, String email, String role, LocalDateTime createdAt) {
        this.id = id;
        this.nickname = nickname;
        this.email = email;
        this.role = role;
        this.createdAt = createdAt;
    }

    public static MemberResponse of(Member member) {
        return new MemberResponse(member.getId(), member.getNickname(), member.getEmail(), member.getRole().name(), member.getCreatedAt());
    }
}
