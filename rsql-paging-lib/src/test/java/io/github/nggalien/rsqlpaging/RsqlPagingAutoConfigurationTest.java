package io.github.nggalien.rsqlpaging;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest(classes = RsqlPagingAutoConfigurationTest.TestConfig.class)
@ActiveProfiles("test")
class RsqlPagingAutoConfigurationTest {

    @Configuration
    @EnableAutoConfiguration
    @EntityScan(basePackageClasses = TestEntity.class)
    @EnableJpaRepositories(basePackageClasses = TestEntityRepository.class)
    @Import(RsqlPagingAutoConfiguration.class)
    static class TestConfig {}

    @Autowired
    private ApplicationContext context;

    @Test
    void shouldRegisterRsqlPagingExecutorBean() {
        assertThat(context.getBean(RsqlPagingExecutor.class)).isNotNull();
    }

    @Test
    void shouldRegisterRsqlPagingPropertiesBean() {
        var props = context.getBean(RsqlPagingProperties.class);
        assertThat(props).isNotNull();
        assertThat(props.maxIdCount()).isEqualTo(1_000_000);
    }
}
