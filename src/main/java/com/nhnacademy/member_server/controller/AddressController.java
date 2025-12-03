package com.nhnacademy.member_server.controller;

import com.nhnacademy.member_server.dto.request.AddressRequest;
import com.nhnacademy.member_server.dto.response.AddressListResponse;
import com.nhnacademy.member_server.dto.response.AddressResponse;
import com.nhnacademy.member_server.entity.MemberPrincipal;
import com.nhnacademy.member_server.service.AddressService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

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
    @GetMapping("/{addressId}")
    public ResponseEntity<AddressResponse> getAddress(@PathVariable Long addressId) {
        AddressResponse addressResponse = addressService.findAddress(addressId);
        return ResponseEntity.ok(addressResponse);
    }


    //주소 추가하기
    @PostMapping
    public ResponseEntity<AddressResponse> createAddress(@AuthenticationPrincipal MemberPrincipal principal,
                                                         @RequestBody AddressRequest addressRequest) {
        Long memberId = principal.getMemberId();
        AddressResponse addressResponse = addressService.registerAddress(memberId, addressRequest);
        return ResponseEntity.ok(addressResponse);
    }

    @PostMapping("/{addressId}/default")
    public ResponseEntity<AddressResponse> setDefaultAddress(@AuthenticationPrincipal MemberPrincipal principal,
                                                             @PathVariable Long addressId) {
        Long memberId = principal.getMemberId();
        AddressResponse addressResponse = addressService.setDefaultAddress(memberId, addressId);
        return ResponseEntity.ok(addressResponse);
    }

    //특정 주소 업데이트
    @PatchMapping("/{addressId}")
    public ResponseEntity<AddressResponse> updateAddress(@RequestBody AddressRequest addressRequest,
                                                         @PathVariable Long addressId) {
        AddressResponse addressResponse = addressService.modifyAddress(addressId, addressRequest);
        return ResponseEntity.ok(addressResponse);
    }

    //특정 주소 삭제
    @DeleteMapping("/{addressId}")
    public ResponseEntity<Void> deleteAddress(@AuthenticationPrincipal MemberPrincipal principal,
                                              @PathVariable Long addressId) {
        Long memberId = principal.getMemberId();
        addressService.removeAddress(memberId, addressId);
        return ResponseEntity.ok().build();
    }

}