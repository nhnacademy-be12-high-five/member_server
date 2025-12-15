package com.nhnacademy.member_server.service.impl.social;

import com.nhnacademy.member_server.dto.response.social.OAuth2UserInfo;
import com.nhnacademy.member_server.dto.response.social.PaycoMemberResponse;
import com.nhnacademy.member_server.dto.response.social.PaycoTokenResponse;
import com.nhnacademy.member_server.feign.PaycoApiFeignClient;
import com.nhnacademy.member_server.feign.PaycoAuthFeignClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;

@ExtendWith(MockitoExtension.class)
class PaycoLoginStrategyTest {

    @InjectMocks
    private PaycoLoginStrategy paycoLoginStrategy;

    @Mock
    private PaycoAuthFeignClient authClient;

    @Mock
    private PaycoApiFeignClient apiClient;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(paycoLoginStrategy, "clientId", "test-client-id");
        ReflectionTestUtils.setField(paycoLoginStrategy, "clientSecret", "test-client-secret");
    }

    @Test
    @DisplayName("PAYCO 로그인 성공 - 사용자 정보 반환")
    void getUserInfoSuccess() {
        String authCode = "valid_auth_code";
        String accessToken = "valid_access_token";

        PaycoTokenResponse tokenResponse = new PaycoTokenResponse();
        ReflectionTestUtils.setField(tokenResponse, "accessToken", accessToken);

        given(authClient.getToken(anyString(), anyString(), anyString(), anyString()))
                .willReturn(tokenResponse);


        PaycoMemberResponse.PaycoMember memberData = new PaycoMemberResponse.PaycoMember();
        ReflectionTestUtils.setField(memberData, "idNo", "payco_12345");
        ReflectionTestUtils.setField(memberData, "name", "홍길동");
        ReflectionTestUtils.setField(memberData, "email", "test@payco.com");
        ReflectionTestUtils.setField(memberData, "mobile", "010-1234-5678");
        ReflectionTestUtils.setField(memberData, "genderCode", "MALE");
        ReflectionTestUtils.setField(memberData, "birthdayMMdd", "0101");

        PaycoMemberResponse.PaycoData data = new PaycoMemberResponse.PaycoData();
        ReflectionTestUtils.setField(data, "member", memberData);

        PaycoMemberResponse.PaycoHeader header = new PaycoMemberResponse.PaycoHeader();
        ReflectionTestUtils.setField(header, "resultCode", 0);
        ReflectionTestUtils.setField(header, "resultMessage", "Success");

        PaycoMemberResponse memberResponse = new PaycoMemberResponse();
        ReflectionTestUtils.setField(memberResponse, "header", header);
        ReflectionTestUtils.setField(memberResponse, "data", data);

        given(apiClient.getMemberInfo(anyString(), anyString())).willReturn(memberResponse);

        OAuth2UserInfo userInfo = paycoLoginStrategy.getUserInfo(authCode);

        assertThat(userInfo.getProvider()).isEqualTo("PAYCO");
        assertThat(userInfo.getProviderId()).isEqualTo("payco_12345");
        assertThat(userInfo.getName()).isEqualTo("홍길동");
        assertThat(userInfo.getEmail()).isEqualTo("test@payco.com");
    }

    @Test
    @DisplayName("실패: PAYCO 토큰 발급 실패")
    void getUserInfoFail_TokenError() {
        String authCode = "invalid_code";

        given(authClient.getToken(anyString(), anyString(), anyString(), anyString()))
                .willReturn(null);

        assertThatThrownBy(() -> paycoLoginStrategy.getUserInfo(authCode))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("PAYCO 토큰");
    }

    @Test
    @DisplayName("실패: PAYCO API 응답 오류 (ResultCode != 0)")
    void getUserInfoFail_ApiError() {
        String authCode = "valid_code";

        PaycoTokenResponse tokenResponse = new PaycoTokenResponse();
        ReflectionTestUtils.setField(tokenResponse, "accessToken", "access_token");
        given(authClient.getToken(anyString(), anyString(), anyString(), anyString()))
                .willReturn(tokenResponse);

        PaycoMemberResponse.PaycoHeader header = new PaycoMemberResponse.PaycoHeader();
        ReflectionTestUtils.setField(header, "resultCode", 9999);
        ReflectionTestUtils.setField(header, "resultMessage", "Error Occurred");

        PaycoMemberResponse memberResponse = new PaycoMemberResponse();
        ReflectionTestUtils.setField(memberResponse, "header", header);

        given(apiClient.getMemberInfo(anyString(), anyString())).willReturn(memberResponse);

        assertThatThrownBy(() -> paycoLoginStrategy.getUserInfo(authCode))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("PAYCO API 오류");
    }
}