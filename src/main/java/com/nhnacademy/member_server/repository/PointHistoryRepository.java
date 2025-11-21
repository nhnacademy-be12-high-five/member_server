package com.nhnacademy.member_server.repository;

import com.nhnacademy.member_server.entity.PointHistory;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PointHistoryRepository extends JpaRepository<PointHistory, Long> {
}
