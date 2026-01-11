package com.xowns.celfeed;

import com.xowns.celfeed.domain.Member;
import com.xowns.celfeed.domain.MemberRole;
import com.xowns.celfeed.repository.MemberRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;

@RequiredArgsConstructor
public class TestDataInit {
    private final MemberRepository memberRepository;

    @EventListener(ApplicationReadyEvent.class)
    public void initMemberData() {
        for (int i = 1; i <= 50; i++) {
            memberRepository.save(Member.create("kanye" + i, "naver" + i, "1234123", MemberRole.CELEB));
        }
    }
}
