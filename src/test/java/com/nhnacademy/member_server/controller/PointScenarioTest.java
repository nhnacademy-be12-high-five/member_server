//package com.nhnacademy.member_server.controller;
//
//import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
//import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
//import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print;
//import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
//import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
//
//import com.fasterxml.jackson.databind.ObjectMapper;
//import com.nhnacademy.member_server.dto.request.PointEarnRequest;
//import com.nhnacademy.member_server.dto.request.PointTransactionRequest;
//import com.nhnacademy.member_server.entity.PointEventType;
//import com.nhnacademy.member_server.entity.member.Grade;
//import com.nhnacademy.member_server.entity.member.Member;
//import com.nhnacademy.member_server.entity.member.Role;
//import com.nhnacademy.member_server.entity.member.Status;
//import com.nhnacademy.member_server.repository.GradeRepository;
//import com.nhnacademy.member_server.repository.MemberRepository;
//import com.nhnacademy.member_server.service.impl.social.PaycoLoginStrategy;
//import com.nhnacademy.member_server.service.social.SocialLoginFactory;
//import java.math.BigDecimal;
//import java.time.LocalDate;
//import java.time.LocalDateTime;
//import org.junit.jupiter.api.BeforeEach;
//import org.junit.jupiter.api.DisplayName;
//import org.junit.jupiter.api.Test;
//import org.springframework.beans.factory.annotation.Autowired;
//import org.springframework.boot.autoconfigure.domain.EntityScan;
//import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
//import org.springframework.boot.test.context.SpringBootTest;
//import org.springframework.data.redis.connection.ReactiveRedisConnectionFactory;
//import org.springframework.data.redis.connection.RedisConnectionFactory;
//import org.springframework.data.redis.core.RedisTemplate;
//import org.springframework.http.MediaType;
//import org.springframework.test.context.ActiveProfiles;
//import org.springframework.test.context.bean.override.mockito.MockitoBean;
//import org.springframework.test.web.servlet.MockMvc;
//import org.springframework.transaction.annotation.Transactional;
//
//@SpringBootTest
//@EntityScan("com.nhnacademy.member_server.entity")
//@AutoConfigureMockMvc
//@ActiveProfiles("test")
//@Transactional
//class PointScenarioTest {
//
//    @Autowired MockMvc mockMvc;
//    @Autowired ObjectMapper objectMapper;
//    @Autowired MemberRepository memberRepository;
//    @Autowired GradeRepository gradeRepository;
//
//    @MockitoBean
//    private RedisTemplate<String, Object> redisTemplate;
//    @MockitoBean
//    private RedisConnectionFactory redisConnectionFactory;
//    @MockitoBean
//    private ReactiveRedisConnectionFactory reactiveRedisConnectionFactory;
//    @MockitoBean
//    SocialLoginFactory socialLoginFactory;
//    @MockitoBean
//    PaycoLoginStrategy paycoLoginStrategy;
//
//    private Long memberId;
//
//    @BeforeEach
//    void setUp() {
//        Grade grade = gradeRepository.save(Grade.builder()
//                .gradeName("GENERAL").min(0).pointRate(new BigDecimal("0.01")).build());
//
//        Member member = memberRepository.save(Member.builder()
//                .loginId("scenario_tester")
//                .name("김유저")
//                .password("1234")
//                .phone("010-1111-2222")
//                .email("test@scenario.com")
//                .birthDate(LocalDate.now())
//                .lastLoginAt(LocalDateTime.now())
//                .status(Status.ACTIVE)
//                .role(Role.USER)
//                .grade(grade)
//                .currentPoint(0L)
//                .build());
//
//        this.memberId = member.getId();
//    }
//
//    @Test
//    @DisplayName("시나리오: 적립 -> 사용 -> 취소 -> 잔액 조회")
//    void pointLifecycleScenario() throws Exception {
//
//        // 상품 구매로 5,000원 적립 요청 (주문 서버 -> 멤버 서버)
//        PointEarnRequest earnRequest = new PointEarnRequest(
//                memberId, PointEventType.EARN_ORDER, 500000L, 1001L
//        );
//
//        mockMvc.perform(post("/internal/points/earn")
//                        .contentType(MediaType.APPLICATION_JSON)
//                        .content(objectMapper.writeValueAsString(earnRequest)))
//                .andDo(print())
//                .andExpect(status().isOk())
//                .andExpect(jsonPath("$.currentPoint").value(5000));
//
//
//        // 다른 상품 구매로 2,000원 사용 요청 (결제 서버 -> 멤버 서버)
//        PointTransactionRequest useRequest = new PointTransactionRequest(
//                memberId, 2000L, 1002L
//        );
//
//        mockMvc.perform(post("/internal/points/use")
//                        .contentType(MediaType.APPLICATION_JSON)
//                        .content(objectMapper.writeValueAsString(useRequest)))
//                .andDo(print())
//                .andExpect(status().isOk())
//                .andExpect(jsonPath("$.currentPoint").value(3000));
//
//
//        // 결제 취소로 2,000원 복구 요청 (결제 서버 -> 멤버 서버)
//        PointTransactionRequest revertRequest = new PointTransactionRequest(
//                memberId, 2000L, 1002L
//        );
//
//        mockMvc.perform(post("/internal/points/revert")
//                        .contentType(MediaType.APPLICATION_JSON)
//                        .content(objectMapper.writeValueAsString(revertRequest)))
//                .andDo(print())
//                .andExpect(status().isOk())
//                .andExpect(jsonPath("$.currentPoint").value(5000));
//
//
//        // 마이페이지에서 최종 잔액 조회 (프론트 -> 게이트웨이 -> 멤버 서버)
//        mockMvc.perform(get("/api/points/balance")
//                        .header("X-USER-ID", memberId))
//                .andDo(print())
//                .andExpect(status().isOk())
//                .andExpect(jsonPath("$.currentPoint").value(5000))
//                .andExpect(jsonPath("$.totalEarnedPoint").value(7000));
//    }
//}