package com.xowns.celfeed.dto.member;

import com.xowns.celfeed.domain.basic.Member;
import com.xowns.celfeed.domain.basic.MemberRole;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.ToString;

@Getter @ToString
public class MemberRequest {

    @NotBlank(message = "닉네임은 필수값입니다.")
    @Size(min = 2, max = 15, message = "닉네임은 2자 이상 15자 이내로 입력해 주세요.")
    private String nickname;

    @NotBlank(message = "이메일은 필수값입니다.")
    @Email(message = "이메일 형식이 올바르지 않습니다.")
    private String email;

    @NotBlank(message = "비밀번호는 필수값입니다.")
    @Size(min = 7, max = 20, message = "비밀번호는 7자 이상 20자 이내로 입력해 주세요.")
    private String password;

    public Member toEntity() {
        return Member.create(nickname, email, password, MemberRole.FAN);
    }
}