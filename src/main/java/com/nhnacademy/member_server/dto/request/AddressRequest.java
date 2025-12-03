package com.nhnacademy.member_server.dto.request;

import com.nhnacademy.member_server.dto.response.AddressResponse;
import lombok.Getter;

@Getter
public class AddressRequest {
    String alias;
    String roadAddress;
    String detailAddress;

}
