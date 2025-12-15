package com.nhnacademy.member_server.service.impl.member;

import com.nhnacademy.member_server.dto.request.member.AddressRequest;
import com.nhnacademy.member_server.dto.response.member.AddressResponse;
import com.nhnacademy.member_server.entity.member.Address;
import com.nhnacademy.member_server.entity.member.Member;
import com.nhnacademy.member_server.repository.AddressRepository;
import com.nhnacademy.member_server.repository.MemberRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.ArrayList;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class AddressServiceImplTest {

    @InjectMocks
    private AddressServiceImpl addressService;

    @Mock
    private MemberRepository memberRepository;

    @Mock
    private AddressRepository addressRepository;

//    @Test
//    @DisplayName("주소 등록 성공 - 첫 주소는 자동으로 기본 배송지")
//    void registerAddressSuccess_FirstAddress() {
//
//        Long memberId = 1L;
//        AddressRequest request = AddressRequest.builder()
//                .alias("집").roadAddress("서울").detailAddress("101호").build();
//
//        Member member = Member.builder().id(memberId).addresses(new ArrayList<>()).build();
//
//        given(memberRepository.findById(memberId)).willReturn(Optional.of(member));
//        given(addressRepository.save(any(Address.class))).willAnswer(invocation -> invocation.getArgument(0));
//
//        AddressResponse response = addressService.registerAddress(memberId, request);
//
//        assertThat(response.getAlias()).isEqualTo("집");
//        assertThat(member.getDefaultAddressId()).isNotNull();
//    }

    @Test
    @DisplayName("주소 등록 실패 - 10개 초과")
    void registerAddressFail_MaxLimit() {
        Long memberId = 1L;
        Member member = Member.builder().id(memberId).addresses(new ArrayList<>()).build();

        for (int i = 0; i < 11; i++) {
            member.getAddresses().add(new Address());
        }

        given(memberRepository.findById(memberId)).willReturn(Optional.of(member));

        assertThatThrownBy(() -> addressService.registerAddress(memberId, AddressRequest.builder().build()))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("10개까지만");
    }

    @Test
    @DisplayName("기본 배송지 설정 성공")
    void setDefaultAddressSuccess() {
        Long memberId = 1L;
        Long addressId = 10L;
        Member member = Member.builder().id(memberId).build();
        Address address = Address.builder().id(addressId).member(member).alias("새 기본").build();

        given(memberRepository.findById(memberId)).willReturn(Optional.of(member));
        given(addressRepository.findById(addressId)).willReturn(Optional.of(address));

        AddressResponse response = addressService.setDefaultAddress(memberId, addressId);

        assertThat(member.getDefaultAddressId()).isEqualTo(addressId);
        assertThat(response.getAlias()).isEqualTo("새 기본");
    }

    @Test
    @DisplayName("주소 삭제 성공 - 내 주소 맞음")
    void removeAddressSuccess() {
        Long memberId = 1L;
        Long addressId = 10L;
        Member member = Member.builder().id(memberId).defaultAddressId(99L).build();
        Address address = Address.builder().id(addressId).member(member).build();

        given(memberRepository.findById(memberId)).willReturn(Optional.of(member));
        given(addressRepository.findById(addressId)).willReturn(Optional.of(address));

        addressService.removeAddress(memberId, addressId);

        verify(addressRepository).deleteById(addressId);
    }
}