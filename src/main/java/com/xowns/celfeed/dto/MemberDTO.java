package com.xowns.celfeed.dto;

import com.xowns.celfeed.domain.Member;
import lombok.Getter;
import lombok.ToString;

@Getter @ToString
public class MemberDTO {

    private String nickname;
    private String email;
    private String password;

    public Member toEntity() {
        return new Member(nickname, email, password);
    }
}
