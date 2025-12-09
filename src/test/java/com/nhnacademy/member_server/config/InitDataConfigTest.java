package com.nhnacademy.member_server.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.nhnacademy.member_server.entity.PointPolicy;
import com.nhnacademy.member_server.repository.PointPolicyRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

class InitDataConfigTest {
    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withUserConfiguration(InitDataConfig.class);

    @Test
    @DisplayName("프로필이 'test' -> 빈 생성 x")
    void whenProfileIsTest_thenBeanShouldNotBeCreated() {
        contextRunner
                .withPropertyValues("spring.profiles.active=test")
                .withBean(PointPolicyRepository.class, () -> Mockito.mock(PointPolicyRepository.class))
                .run(context -> {
                    assertThat(context).doesNotHaveBean(CommandLineRunner.class);
                });
    }

    @Test
    @DisplayName("프로필이 'local' + 데이터 x -> 초기 정책 생성")
    void whenProfileIsLocal_andDbEmpty_thenSaveDefaultPolicy() {
        PointPolicyRepository mockRepo = Mockito.mock(PointPolicyRepository.class);
        when(mockRepo.count()).thenReturn(0L);

        contextRunner
                .withPropertyValues("spring.profiles.active=local")
                .withBean(PointPolicyRepository.class, () -> mockRepo)
                .run(context -> {
                    assertThat(context).hasSingleBean(CommandLineRunner.class);

                    context.getBean(CommandLineRunner.class).run();

                    verify(mockRepo, times(1)).save(any(PointPolicy.class));
                });
    }

    @Test
    @DisplayName("데이터가 이미 있으면 -> 저장 x")
    void whenDbNotEmpty_thenDoNotSave() {
        PointPolicyRepository mockRepo = Mockito.mock(PointPolicyRepository.class);
        when(mockRepo.count()).thenReturn(1L);

        contextRunner
                .withPropertyValues("spring.profiles.active=local")
                .withBean(PointPolicyRepository.class, () -> mockRepo)
                .run(context -> {
                    assertThat(context).hasSingleBean(CommandLineRunner.class);

                    context.getBean(CommandLineRunner.class).run();

                    verify(mockRepo, never()).save(any(PointPolicy.class));
                });
    }
}