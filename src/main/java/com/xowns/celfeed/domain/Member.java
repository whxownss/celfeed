package com.xowns.celfeed.domain;

import jakarta.persistence.*;
import lombok.*;

import static jakarta.persistence.GenerationType.*;
import static lombok.AccessLevel.*;

@Entity
@NoArgsConstructor(access = PROTECTED)
@Getter
@ToString(of = {"id", "nickname", "email", "role"})
@EqualsAndHashCode(callSuper = false, of = {"nickname"})
public class Member extends BaseEntity {

    @Id @GeneratedValue(strategy = IDENTITY)
    private Long id;

    @Column(unique = true, columnDefinition = "VARCHAR(20)", nullable = false)
    private String nickname;

    @Column(unique = true, columnDefinition = "VARCHAR(100)", nullable = false)
    private String email;

    @Column(columnDefinition = "VARCHAR(100)", nullable = false)
    private String password;

    @Enumerated(EnumType.STRING)
    @Column(columnDefinition = "VARCHAR(10)", nullable = false)
    private MemberRole role;

    private Member(String nickname, String email, String password, MemberRole role) {
        this.nickname = nickname;
        this.email = email;
        this.password = password;
        this.role = role;
    }

    public static Member create(String nickname, String email, String password, MemberRole role) {
        return new Member(nickname, email, password, role);
    }
}
