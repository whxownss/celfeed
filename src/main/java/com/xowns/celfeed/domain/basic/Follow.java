package com.xowns.celfeed.domain.basic;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.ToString;

import static jakarta.persistence.FetchType.*;
import static jakarta.persistence.GenerationType.*;
import static lombok.AccessLevel.*;

@Entity
@Table(
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uk_follow",
                        columnNames = {"from_id", "to_id"}
                )
        }
)
@NoArgsConstructor(access = PROTECTED)
@Getter @ToString
public class Follow extends BaseCreateEntity {

    @Id @GeneratedValue(strategy = IDENTITY)
    private Long id;

    @ManyToOne(fetch = LAZY)
    @JoinColumn(name = "from_id", nullable = false)
    private Member fromMember;

    @ManyToOne(fetch = LAZY)
    @JoinColumn(name = "to_id", nullable = false)
    private Member toMember;

    private Follow(Member fromMember, Member toMember) {
        this.fromMember = fromMember;
        this.toMember = toMember;
    }

    public static Follow create(Member fromMember, Member toMember) {
        return new Follow(fromMember, toMember);
    }
}
