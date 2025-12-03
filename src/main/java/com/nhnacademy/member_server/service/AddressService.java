package com.nhnacademy.member_server.service;

import com.nhnacademy.member_server.dto.request.AddressRequest;
import com.nhnacademy.member_server.dto.response.AddressListResponse;
import com.nhnacademy.member_server.dto.response.AddressResponse;

public interface AddressService {
    public AddressResponse findDefaultAddress(Long memberId);

    AddressListResponse findAddressList(Long memberId);

    AddressResponse findAddress(Long memberId, Long addressId);

    AddressResponse registerAddress(Long memberId, AddressRequest addressRequest);

    AddressResponse modifyAddress(Long memberId, Long addressId, AddressRequest addressRequest);

    void removeAddress(Long memberId, Long addressId);

    AddressResponse setDefaultAddress(Long memberId, Long addressId);
}
