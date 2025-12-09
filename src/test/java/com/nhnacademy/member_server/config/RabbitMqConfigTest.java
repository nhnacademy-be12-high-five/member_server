package com.nhnacademy.member_server.config;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

class RabbitMqConfigTest {
    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withUserConfiguration(RabbitMqConfig.class);

    @Test
    @DisplayName("app.rabbitmq.enabled=true -> 빈 생성")
    void whenEnabled_thenBeansAreCreated() {
        contextRunner
                .withPropertyValues("app.rabbitmq.enabled=true")
                .run(context -> {
                    assertThat(context).hasSingleBean(Queue.class);
                    assertThat(context).hasSingleBean(Jackson2JsonMessageConverter.class);

                    assertThat(context.getBean(Queue.class).getName()).isEqualTo("point-queue");
                });
    }

    @Test
    @DisplayName("app.rabbitmq.enabled=false -> 빈 생성 x")
    void whenDisabled_thenBeansAreNotCreated() {
        contextRunner
                .withPropertyValues("app.rabbitmq.enabled=false")
                .run(context -> {
                    assertThat(context).doesNotHaveBean(Queue.class);
                    assertThat(context).doesNotHaveBean(Jackson2JsonMessageConverter.class);
                });
    }

    @Test
    @DisplayName("설정값 x -> 빈 생성 x")
    void whenMissing_thenBeansAreNotCreated() {
        contextRunner
                .run(context -> {
                    assertThat(context).doesNotHaveBean(Queue.class);
                });
    }
}