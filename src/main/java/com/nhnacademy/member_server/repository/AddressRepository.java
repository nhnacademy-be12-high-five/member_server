package com.nhnacademy.member_server.repository;

import com.nhnacademy.member_server.entity.Address;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AddressRepository extends JpaRepository<Address, Long> {
    Optional<Address> findById(Long addressId);
}
