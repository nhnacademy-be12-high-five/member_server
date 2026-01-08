package com.nhnacademy.member_server.config;

import com.nhnacademy.member_server.entity.PointPolicy;
import com.nhnacademy.member_server.entity.member.Grade;
import com.nhnacademy.member_server.repository.GradeRepository;
import com.nhnacademy.member_server.repository.PointPolicyRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

class InitDataConfigTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withUserConfiguration(InitDataConfig.class);

    @Test
    @DisplayName("프로필이 'test' -> 빈 생성 안 함 (CI/CD 테스트 환경 충돌 방지)")
    void whenProfileIsTest_thenBeanShouldNotBeCreated() {
        contextRunner
                .withPropertyValues("spring.profiles.active=test")
                .withBean(PointPolicyRepository.class, () -> Mockito.mock(PointPolicyRepository.class))
                .withBean(GradeRepository.class, () -> Mockito.mock(GradeRepository.class))
                .run(context -> {
                    assertThat(context).doesNotHaveBean(CommandLineRunner.class);
                });
    }

    @Test
    @DisplayName("프로필 'local' + DB 비어있음 -> 초기 데이터 저장 수행")
    void whenProfileIsLocal_andDbEmpty_thenSaveDefaultPolicyAndGrades() {
        PointPolicyRepository mockPolicyRepo = Mockito.mock(PointPolicyRepository.class);
        GradeRepository mockGradeRepo = Mockito.mock(GradeRepository.class);

        when(mockPolicyRepo.count()).thenReturn(0L);
        when(mockGradeRepo.findByGradeName(anyString())).thenReturn(Optional.empty());

        contextRunner
                .withPropertyValues("spring.profiles.active=local")
                .withBean(PointPolicyRepository.class, () -> mockPolicyRepo)
                .withBean(GradeRepository.class, () -> mockGradeRepo)
                .run(context -> {
                    assertThat(context).hasSingleBean(CommandLineRunner.class);
                    context.getBean(CommandLineRunner.class).run();

                    verify(mockPolicyRepo, times(1)).save(any(PointPolicy.class));

                    verify(mockGradeRepo, times(4)).save(any(Grade.class));
                });
    }

    @Test
    @DisplayName("데이터가 이미 있음 -> 저장 안 함")
    void whenDbNotEmpty_thenDoNotSave() {
        PointPolicyRepository mockPolicyRepo = Mockito.mock(PointPolicyRepository.class);
        GradeRepository mockGradeRepo = Mockito.mock(GradeRepository.class);

        when(mockPolicyRepo.count()).thenReturn(1L);
        when(mockGradeRepo.findByGradeName(anyString()))
                .thenReturn(Optional.of(Grade.builder().gradeName("EXISTING").build()));

        contextRunner
                .withPropertyValues("spring.profiles.active=local")
                .withBean(PointPolicyRepository.class, () -> mockPolicyRepo)
                .withBean(GradeRepository.class, () -> mockGradeRepo)
                .run(context -> {
                    assertThat(context).hasSingleBean(CommandLineRunner.class);
                    context.getBean(CommandLineRunner.class).run();

                    verify(mockPolicyRepo, never()).save(any(PointPolicy.class));
                    verify(mockGradeRepo, never()).save(any(Grade.class));
                });
    }
}