package com.nhnacademy.member_server.dto.response;

import com.nhnacademy.member_server.entity.Address;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Builder
@Getter
@NoArgsConstructor
@AllArgsConstructor
public class AddressResponse {
    String alias;

    String roadAddress;

    String detailAddress;

    Long addressId;

    public static AddressResponse from(Address address) {
        return AddressResponse.builder()
                .addressId(address.getId())
                .alias(address.getAlias())
                .roadAddress(address.getRoadAddress())
                .detailAddress(address.getDetailAddress())
                .build();
    }
}
