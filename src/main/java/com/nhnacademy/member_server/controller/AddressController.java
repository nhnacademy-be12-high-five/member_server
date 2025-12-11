package com.nhnacademy.member_server.controller;

import com.nhnacademy.member_server.dto.request.member.AddressRequest;
import com.nhnacademy.member_server.dto.response.member.AddressListResponse;
import com.nhnacademy.member_server.dto.response.member.AddressResponse;
import com.nhnacademy.member_server.entity.MemberPrincipal;
import com.nhnacademy.member_server.service.AddressService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/address")
@RequiredArgsConstructor
public class AddressController {

    private final AddressService addressService;


    //해당 멤버 기본 주소 불러오기
    @GetMapping("/default")
    public ResponseEntity<AddressResponse> getDefaultAddress(@AuthenticationPrincipal MemberPrincipal principal) {
        Long memberId = principal.getMemberId();
        AddressResponse addressResponse = addressService.findDefaultAddress(memberId);
        return ResponseEntity.ok(addressResponse);
    }

    //해당 멤버 모든 주소 불러오기
    @GetMapping
    public ResponseEntity<AddressListResponse> getAddressList(@AuthenticationPrincipal MemberPrincipal principal) {
        Long memberId = principal.getMemberId();
        AddressListResponse addressListResponse = addressService.findAddressList(memberId);
        return ResponseEntity.ok(addressListResponse);
    }

    //특정 주소 정보 불러오기 (address Id 기준)
    @GetMapping("/{address-id}")
    public ResponseEntity<AddressResponse> getAddress(@AuthenticationPrincipal MemberPrincipal principal,
                                                       @PathVariable("address-id") Long addressId) {
        Long memberId = principal.getMemberId();
        AddressResponse addressResponse = addressService.findAddress(memberId, addressId);
        return ResponseEntity.ok(addressResponse);
    }


    //주소 추가하기
    @PostMapping
    public ResponseEntity<AddressResponse> createAddress(@AuthenticationPrincipal MemberPrincipal principal,
                                                         @Valid @RequestBody AddressRequest addressRequest) {
        Long memberId = principal.getMemberId();
        AddressResponse addressResponse = addressService.registerAddress(memberId, addressRequest);
        return ResponseEntity.ok(addressResponse);
    }

    @PostMapping("/{address-id}/default")
    public ResponseEntity<AddressResponse> setDefaultAddress(@AuthenticationPrincipal MemberPrincipal principal,
                                                             @PathVariable("address-id") Long addressId) {
        Long memberId = principal.getMemberId();
        AddressResponse addressResponse = addressService.setDefaultAddress(memberId, addressId);
        return ResponseEntity.ok(addressResponse);
    }

    //특정 주소 업데이트
    @PatchMapping("/{address-id}")
    public ResponseEntity<AddressResponse> updateAddress(@AuthenticationPrincipal MemberPrincipal principal,
                                                         @RequestBody AddressRequest addressRequest,
                                                         @PathVariable("address-id") Long addressId) {
        Long memberId = principal.getMemberId();
        AddressResponse addressResponse = addressService.modifyAddress(memberId, addressId, addressRequest);
        return ResponseEntity.ok(addressResponse);
    }

    //특정 주소 삭제
    @DeleteMapping("/{address-id}")
    public ResponseEntity<Void> deleteAddress(@AuthenticationPrincipal MemberPrincipal principal,
                                              @PathVariable("address-id") Long addressId) {
        Long memberId = principal.getMemberId();
        addressService.removeAddress(memberId, addressId);
        return ResponseEntity.ok().build();
    }

}