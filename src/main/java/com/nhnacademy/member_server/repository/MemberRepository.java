package com.nhnacademy.member_server.repository;

import com.nhnacademy.member_server.entity.Member;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MemberRepository extends JpaRepository<Member, Long> {
}
