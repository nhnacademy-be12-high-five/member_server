package com.nhnacademy.member_server.repository;

import com.nhnacademy.member_server.entity.Member;
import jakarta.persistence.LockModeType;

import java.util.List;
import java.util.Optional;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface MemberRepository extends JpaRepository<Member, Long> {
    Optional<Member> findByLoginId(String loginId);

    boolean existsByLoginId(String loginId);

    boolean existsByEmail(@Email String email);

    boolean existsByPhone(@NotBlank String phone);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select m from Member m where m.id = :id")
    Optional<Member> findByIdForUpdate(@Param("id") Long id);

    @Query("SELECT m.id FROM Member m WHERE MONTH(m.birthDate) = :month")
    List<Long> findAllIdsByBirthMonth(@Param("month") int month);

}