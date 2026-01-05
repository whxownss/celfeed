package com.xowns.celfeed.dto;

import com.xowns.celfeed.domain.Member;
import lombok.Getter;

@Getter
public class MemberResponse {
    private Long id;
    private String nickname;
    private String email;

    private MemberResponse(Long id, String nickname, String email) {
        this.id = id;
        this.nickname = nickname;
        this.email = email;
    }

    public static MemberResponse from(Member member) {
        return new MemberResponse(member.getId(), member.getNickname(), member.getEmail());
    }
}
