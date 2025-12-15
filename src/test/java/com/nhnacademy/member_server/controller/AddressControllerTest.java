package com.nhnacademy.member_server.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nhnacademy.member_server.dto.request.member.AddressRequest;
import com.nhnacademy.member_server.dto.response.member.AddressListResponse;
import com.nhnacademy.member_server.dto.response.member.AddressResponse;
import com.nhnacademy.member_server.service.member.AddressService;
import com.nhnacademy.member_server.service.member.AuthService;
import com.nhnacademy.member_server.service.member.MemberService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = AddressController.class, properties = "jwt.refresh_expiration_time=604800000")
@AutoConfigureMockMvc(addFilters = false)
class AddressControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private AddressService addressService;

    @MockitoBean
    private AuthService authService;
    @MockitoBean
    private MemberService memberService;

    @Test
    @DisplayName("기본 배송지 조회 (GET /api/address/default)")
    void getDefaultAddressSuccess() throws Exception {
        Long memberId = 1L;
        AddressResponse response = AddressResponse.builder()
                .alias("우리집")
                .roadAddress("서울시 강남구")
                .build();

        given(addressService.findDefaultAddress(memberId)).willReturn(response);

        mockMvc.perform(get("/api/address/default")
                        .header("X-User-ID", memberId)
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.alias").value("우리집"))
                .andExpect(jsonPath("$.roadAddress").value("서울시 강남구"));
    }

    @Test
    @DisplayName("모든 배송지 목록 조회 (GET /api/address)")
    void getAddressListSuccess() throws Exception {
        Long memberId = 1L;
        AddressResponse addr1 = AddressResponse.builder().alias("집").build();
        AddressResponse addr2 = AddressResponse.builder().alias("회사").build();
        AddressListResponse listResponse = new AddressListResponse(List.of(addr1, addr2));

        given(addressService.findAddressList(memberId)).willReturn(listResponse);

        mockMvc.perform(get("/api/address")
                        .header("X-User-ID", memberId)
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.addressList.size()").value(2))
                .andExpect(jsonPath("$.addressList[0].alias").value("집"));
    }

    @Test
    @DisplayName("특정 배송지 조회 (GET /api/address/{address-id})")
    void getAddressSuccess() throws Exception {
        Long memberId = 1L;
        Long addressId = 10L;
        AddressResponse response = AddressResponse.builder().alias("친구집").build();

        given(addressService.findAddress(memberId, addressId)).willReturn(response);

        mockMvc.perform(get("/api/address/{address-id}", addressId)
                        .header("X-User-ID", memberId)
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.alias").value("친구집"));
    }

    @Test
    @DisplayName("배송지 추가 (POST /api/address)")
    void createAddressSuccess() throws Exception {
        Long memberId = 1L;
        AddressRequest request = AddressRequest.builder()
                .alias("새 배송지")
                .roadAddress("부산시 해운대구")
                .detailAddress("101동")
                .build();

        AddressResponse response = AddressResponse.builder()
                .alias("새 배송지")
                .roadAddress("부산시 해운대구")
                .build();

        given(addressService.registerAddress(eq(memberId), any(AddressRequest.class)))
                .willReturn(response);

        mockMvc.perform(post("/api/address")
                        .header("X-User-ID", memberId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.alias").value("새 배송지"));
    }

    @Test
    @DisplayName("기본 배송지 설정 (POST /api/address/{address-id}/default)")
    void setDefaultAddressSuccess() throws Exception {
        Long memberId = 1L;
        Long addressId = 20L;
        AddressResponse response = AddressResponse.builder().alias("기본집").build();

        given(addressService.setDefaultAddress(memberId, addressId)).willReturn(response);

        mockMvc.perform(post("/api/address/{address-id}/default", addressId)
                        .header("X-User-ID", memberId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.alias").value("기본집"));
    }

    @Test
    @DisplayName("배송지 수정 (PATCH /api/address/{address-id})")
    void updateAddressSuccess() throws Exception {
        Long memberId = 1L;
        Long addressId = 30L;
        AddressRequest request = AddressRequest.builder()
                .alias("이사 간 집")
                .roadAddress("광주시")
                .detailAddress("505호")
                .build();

        AddressResponse response = AddressResponse.builder()
                .alias("이사 간 집")
                .roadAddress("광주시")
                .build();

        given(addressService.modifyAddress(eq(memberId), eq(addressId), any(AddressRequest.class)))
                .willReturn(response);

        mockMvc.perform(patch("/api/address/{address-id}", addressId)
                        .header("X-User-ID", memberId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.alias").value("이사 간 집"));
    }

    @Test
    @DisplayName("배송지 삭제 (DELETE /api/address/{address-id})")
    void deleteAddressSuccess() throws Exception {
        Long memberId = 1L;
        Long addressId = 40L;

        mockMvc.perform(delete("/api/address/{address-id}", addressId)
                        .header("X-User-ID", memberId))
                .andExpect(status().isOk());

        verify(addressService).removeAddress(memberId, addressId);
    }
}