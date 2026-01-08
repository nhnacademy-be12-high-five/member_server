package com.nhnacademy.member_server.repository;

import com.nhnacademy.member_server.entity.member.Address;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface AddressRepository extends JpaRepository<Address, Long> {
    Optional<Address> findById(Long addressId);
}
