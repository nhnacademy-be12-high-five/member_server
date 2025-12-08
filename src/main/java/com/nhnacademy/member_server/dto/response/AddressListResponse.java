package com.nhnacademy.member_server.dto.response;

import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class AddressListResponse {
    List<AddressResponse> addressList;
}
