package com.xowns.celfeed.dto;

import com.xowns.celfeed.domain.Member;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.ToString;
import org.hibernate.validator.constraints.Range;

@Getter @ToString
public class MemberDTO {

    @NotBlank(message = "닉네임은 필수값입니다.")
    private String nickname;

    @NotBlank(message = "이메일은 필수값입니다.")
    private String email;

    @NotBlank(message = "비밀번호는 필수값입니다.")
    @Size(min = 7, max = 20, message = "비밀번호는 7자 이상 20자 이내로 입력해주세요.")
    private String password;

    private Integer age;


    private Integer number;

    public Member toEntity() {
        return new Member(nickname, email, password);
    }
}
