package com.nhnacademy.member_server.repository;

import com.nhnacademy.member_server.entity.Grade;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface GradeRepository extends JpaRepository<Grade, Long> {
    Optional<Grade> findByGradeName(String gradeName);
}
