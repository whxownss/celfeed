package com.xowns.celfeed.domain.basic;

import com.xowns.celfeed.common.converter.BooleanToYNConverter;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.ToString;

import static jakarta.persistence.FetchType.*;
import static jakarta.persistence.GenerationType.*;
import static lombok.AccessLevel.PROTECTED;

@Entity
@NoArgsConstructor(access = PROTECTED)
@Getter @ToString
public class Post extends BaseEntity {

    @Id @GeneratedValue(strategy = IDENTITY)
    private Long id;

    @ManyToOne(fetch = LAZY)
    @JoinColumn(name = "member_id", nullable = false)
    private Member member;

    @Column(nullable = false)
    private String content;

    @Column(columnDefinition = "VARCHAR(1)", nullable = false)
    @Convert(converter = BooleanToYNConverter.class)
    private boolean isDeleted;

    private Post(Member member, String content, boolean isDeleted) {
        this.member = member;
        this.content = content;
        this.isDeleted = isDeleted;
    }

    public static Post create(Member member, String content) {
        return new Post(member, content, false);
    }

    public void updateContent(String content) {
        this.content = content;
    }

    public void deletePost() {
        this.isDeleted = true;
    }
}
