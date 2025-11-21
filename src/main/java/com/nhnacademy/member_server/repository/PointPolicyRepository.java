package com.nhnacademy.member_server.repository;

import com.nhnacademy.member_server.entity.PointPolicy;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PointPolicyRepository extends JpaRepository<PointPolicy, Long> {
}
