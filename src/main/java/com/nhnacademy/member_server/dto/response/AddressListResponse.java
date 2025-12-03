package com.nhnacademy.member_server.dto.response;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;

import java.util.List;

@Getter
@AllArgsConstructor
public class AddressListResponse {
    List<AddressResponse> addressList;
}
