package com.nhnacademy.member_server.repository;

import com.nhnacademy.member_server.entity.Member;
import jakarta.persistence.LockModeType;
import jakarta.persistence.QueryHint;
import java.util.Optional;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.QueryHints;

public interface MemberRepository extends JpaRepository<Member, Long> {
    Optional<Member> findByLoginId(String loginId);
    boolean existsByLoginId(String loginId);
    Optional<Member> readById(Long id);

    boolean existsByEmail(@Email String email);

    boolean existsByPhone(@NotBlank String phone);
}