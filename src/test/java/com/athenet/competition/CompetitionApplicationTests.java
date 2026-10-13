package com.athenet.competition;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import com.athenet.competition.repository.*;

@SpringBootTest(properties = {
    "spring.autoconfigure.exclude=org.springframework.boot.jdbc.autoconfigure.DataSourceAutoConfiguration,org.springframework.boot.hibernate.autoconfigure.HibernateJpaAutoConfiguration",
    "spring.rabbitmq.listener.simple.auto-startup=false",
    "spring.rabbitmq.dynamic=false",
    "DB_PASSWORD=test-only",
    "RABBITMQ_PASSWORD=test-only"
})
class CompetitionApplicationTests {
    @MockitoBean CompetitionRepository competitions;
    @MockitoBean EnrollmentRepository enrollments;
    @MockitoBean MatchRepository matches;
    @MockitoBean JwtDecoder jwtDecoder;

	@Test
	void contextLoads() {
	}

}
