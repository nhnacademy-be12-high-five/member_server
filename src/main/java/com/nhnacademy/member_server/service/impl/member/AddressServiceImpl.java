package com.nhnacademy.member_server.service.impl.member;

import com.nhnacademy.member_server.dto.request.member.AddressRequest;
import com.nhnacademy.member_server.dto.response.member.AddressListResponse;
import com.nhnacademy.member_server.dto.response.member.AddressResponse;
import com.nhnacademy.member_server.entity.Address;
import com.nhnacademy.member_server.entity.Member;
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
        Member member = memberRepository.findById(memberId).orElseThrow(() -> new RuntimeException("존재하지 않는 member 조회"));
        if (member.getDefaultAddressId() == null) {
            throw new RuntimeException("설정된 기본 배송지가 없습니다.");
        }
        Address defaultAddress = addressRepository.findById(member.getDefaultAddressId()).orElseThrow(() -> new RuntimeException("설정된 기본 배송지가 없음"));

        return AddressResponse.from(defaultAddress);
    }

    @Transactional(readOnly = true)
    @Override
    public AddressListResponse findAddressList(Long memberId) {
        Member member = memberRepository.findById(memberId).orElseThrow(() -> new RuntimeException("존재하지 않는 member 조회"));
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
        Address address = addressRepository.findById(addressId).orElseThrow(() -> new RuntimeException("존재하지 않는 주소"));
        if(!address.getMember().getId().equals(memberId)) throw new RuntimeException("사용자의 주소만 조회할 수 있습니다");
        return AddressResponse.from(address);
    }

    @Transactional
    @Override
    public AddressResponse registerAddress(Long memberId, AddressRequest addressRequest) {
        Member member = memberRepository.findById(memberId).orElseThrow(() -> new RuntimeException("존재하지 않는 member 조회"));
        if (member.getAddresses().size() > 10) {
            throw new RuntimeException("주소는 최대 10개까지만 등록할 수 있습니다");
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
        Address address = addressRepository.findById(addressId).orElseThrow(() -> new RuntimeException("존재하지 않는 주소 조회"));
        if(!address.getMember().getId().equals(memberId)) throw new RuntimeException("사용자의 주소에 대한 요청이 이닙니다");
        address.setAlias(addressRequest.getAlias());
        address.setRoadAddress(addressRequest.getRoadAddress());
        address.setDetailAddress(addressRequest.getDetailAddress());
        return AddressResponse.from(address);
    }

    @Transactional
    @Override
    public void removeAddress(Long memberId, Long addressId) {
        Member member = memberRepository.findById(memberId).orElseThrow(() -> new RuntimeException("존재하지 않는 member 조회"));
        Address address = addressRepository.findById(addressId).orElseThrow(() -> new RuntimeException("존재하지 않는 주소 조회"));

        if(address.getMember().getId().equals(memberId)) {
            if(member.getDefaultAddressId().equals(addressId)) {
                member.setDefaultAddressId(null);
            }
            addressRepository.deleteById(addressId);
        }
    }

    @Transactional
    @Override
    public AddressResponse setDefaultAddress(Long memberId, Long addressId) {
        Member member = memberRepository.findById(memberId).orElseThrow(() -> new RuntimeException("존재하지 않는 member 조회"));
        Address address = addressRepository.findById(addressId).orElseThrow(() -> new RuntimeException("존재하지 않는 주소 조회"));

        if (address.getMember().getId().equals(memberId)) {
            member.setDefaultAddressId(addressId);
        } else throw new RuntimeException("해당 멤버의 주소가 아닙니다");
        return AddressResponse.from(address);
    }
}
