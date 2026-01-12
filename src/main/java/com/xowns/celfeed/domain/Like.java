package com.xowns.celfeed.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.ToString;

import static jakarta.persistence.FetchType.*;
import static jakarta.persistence.GenerationType.*;
import static lombok.AccessLevel.PROTECTED;

@Entity
@Table(
        name = "likes",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uk_likes",
                        columnNames = {"post_id", "member_id"}
                )
        }
)
@NoArgsConstructor(access = PROTECTED)
@Getter @ToString
public class Like extends BaseCreateEntity {

    @Id @GeneratedValue(strategy = IDENTITY)
    private Long id;

    @ManyToOne(fetch = LAZY)
    @JoinColumn(name = "post_id", nullable = false)
    private Post post;

    @ManyToOne(fetch = LAZY)
    @JoinColumn(name = "member_id", nullable = false)
    private Member member;

    private Like(Post post, Member member) {
        this.post = post;
        this.member = member;
    }

    public static Like create(Post post, Member member) {
        return new Like(post, member);
    }
}
