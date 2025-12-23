package com.nhnacademy.member_server.service.impl.member;

import com.nhnacademy.member_server.dto.request.member.AddressRequest;
import com.nhnacademy.member_server.dto.response.member.AddressListResponse;
import com.nhnacademy.member_server.dto.response.member.AddressResponse;
import com.nhnacademy.member_server.entity.member.Address;
import com.nhnacademy.member_server.entity.member.Member;
import com.nhnacademy.member_server.exception.BusinessException; // [추가]
import com.nhnacademy.member_server.exception.ErrorCode; // [추가]
import com.nhnacademy.member_server.repository.AddressRepository;
import com.nhnacademy.member_server.repository.MemberRepository;
import com.nhnacademy.member_server.service.member.AddressService;
import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AddressServiceImpl implements AddressService {
    private final MemberRepository memberRepository;
    private final AddressRepository addressRepository;

    @Transactional(readOnly = true)
    @Override
    public AddressResponse findDefaultAddress(Long memberId) {
        Member member = memberRepository.findById(memberId)
                .orElseThrow(() -> new BusinessException(ErrorCode.MEMBER_NOT_FOUND));

        if (member.getDefaultAddressId() == null) {
            throw new BusinessException(ErrorCode.DEFAULT_ADDRESS_NOT_FOUND);
        }
        Address defaultAddress = addressRepository.findById(member.getDefaultAddressId())
                .orElseThrow(() -> new BusinessException(ErrorCode.ADDRESS_NOT_FOUND));

        return AddressResponse.from(defaultAddress);
    }

    @Transactional(readOnly = true)
    @Override
    public AddressListResponse findAddressList(Long memberId) {
        Member member = memberRepository.findById(memberId)
                .orElseThrow(() -> new BusinessException(ErrorCode.MEMBER_NOT_FOUND));

        List<Address> addressList = member.getAddresses();
        List<AddressResponse> addressResponseList = new ArrayList<>();

        for (Address address : addressList) {
            addressResponseList.add(AddressResponse.from(address));
        }
        return new AddressListResponse(addressResponseList);
    }

    @Transactional(readOnly = true)
    @Override
    public AddressResponse findAddress(Long memberId, Long addressId) {
        Address address = addressRepository.findById(addressId)
                .orElseThrow(() -> new BusinessException(ErrorCode.ADDRESS_NOT_FOUND));

        if(!address.getMember().getId().equals(memberId)) {
            throw new BusinessException(ErrorCode.ADDRESS_ACCESS_DENIED);
        }
        return AddressResponse.from(address);
    }

    @Transactional
    @Override
    public AddressResponse registerAddress(Long memberId, AddressRequest addressRequest) {
        Member member = memberRepository.findById(memberId)
                .orElseThrow(() -> new BusinessException(ErrorCode.MEMBER_NOT_FOUND));

        if (member.getAddresses().size() >= 10) {
            throw new BusinessException(ErrorCode.MAX_ADDRESS_LIMIT_EXCEEDED);
        }

        Address address = Address.builder()
                .alias(addressRequest.getAlias())
                .roadAddress(addressRequest.getRoadAddress())
                .detailAddress(addressRequest.getDetailAddress())
                .member(member)
                .build();
        addressRepository.save(address);
        member.addAddress(address);
        if (member.getAddresses().size() == 1) {
            member.setDefaultAddressId(address.getId());
        }

        return AddressResponse.from(address);
    }

    @Transactional
    @Override
    public AddressResponse modifyAddress(Long memberId, Long addressId, AddressRequest addressRequest) {
        Address address = addressRepository.findById(addressId)
                .orElseThrow(() -> new BusinessException(ErrorCode.ADDRESS_NOT_FOUND));

        if(!address.getMember().getId().equals(memberId)) {
            throw new BusinessException(ErrorCode.ADDRESS_ACCESS_DENIED);
        }

        address.setAlias(addressRequest.getAlias());
        address.setRoadAddress(addressRequest.getRoadAddress());
        address.setDetailAddress(addressRequest.getDetailAddress());
        return AddressResponse.from(address);
    }

    @Transactional
    @Override
    public void removeAddress(Long memberId, Long addressId) {
        Member member = memberRepository.findById(memberId)
                .orElseThrow(() -> new BusinessException(ErrorCode.MEMBER_NOT_FOUND));
        Address address = addressRepository.findById(addressId)
                .orElseThrow(() -> new BusinessException(ErrorCode.ADDRESS_NOT_FOUND));

        if(!address.getMember().getId().equals(memberId)) {
            throw new BusinessException(ErrorCode.ADDRESS_ACCESS_DENIED);
        }

        if(member.getDefaultAddressId() != null && member.getDefaultAddressId().equals(addressId)) {
            member.setDefaultAddressId(null);
        }
        addressRepository.deleteById(addressId);
    }

    @Transactional
    @Override
    public AddressResponse setDefaultAddress(Long memberId, Long addressId) {
        Member member = memberRepository.findById(memberId)
                .orElseThrow(() -> new BusinessException(ErrorCode.MEMBER_NOT_FOUND));
        Address address = addressRepository.findById(addressId)
                .orElseThrow(() -> new BusinessException(ErrorCode.ADDRESS_NOT_FOUND));

        if (!address.getMember().getId().equals(memberId)) {
            throw new BusinessException(ErrorCode.ADDRESS_ACCESS_DENIED);
        }

        member.setDefaultAddressId(addressId);
        return AddressResponse.from(address);
    }
}