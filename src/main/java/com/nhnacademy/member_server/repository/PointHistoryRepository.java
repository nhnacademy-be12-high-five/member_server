package com.nhnacademy.member_server.repository;

import com.nhnacademy.member_server.entity.PointHistory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PointHistoryRepository extends JpaRepository<PointHistory, Long> {
    Page<PointHistory> findAllByMemberId(Long memberId, Pageable pageable);
}
