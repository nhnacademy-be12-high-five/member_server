package com.nhnacademy.member_server.dto.response.member;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.List;

@Getter
@AllArgsConstructor
public class AddressListResponse {
    List<AddressResponse> addressList;
}
