package com.xowns.celfeed.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.xowns.celfeed.domain.Member;
import lombok.Getter;
import org.springframework.format.annotation.DateTimeFormat;

import java.time.LocalDateTime;

@Getter
public class MemberResponse {
    private String nickname;
    private String email;
    private String role;

    @JsonFormat(pattern = "yyyy-MM-dd")
    private LocalDateTime createdAt;

    private MemberResponse(String nickname, String email, String role, LocalDateTime createdAt) {
        this.nickname = nickname;
        this.email = email;
        this.role = role;
        this.createdAt = createdAt;
    }

    public static MemberResponse of(Member member) {
        return new MemberResponse(member.getNickname(), member.getEmail(), member.getRole().name(), member.getCreatedAt());
    }
}
