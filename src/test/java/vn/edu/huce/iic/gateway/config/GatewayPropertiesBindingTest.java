package vn.edu.huce.iic.gateway.config;

import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;

/** Bắt lỗi khởi động thật: GatewayProperties phải được Spring Boot gán được từ cấu hình (không chỉ new bằng tay). */
class GatewayPropertiesBindingTest {

    @EnableConfigurationProperties(GatewayProperties.class)
    static class Config {
    }

    private final ApplicationContextRunner runner = new ApplicationContextRunner().withUserConfiguration(Config.class);

    @Test
    void bindsFromProperties() {
        runner.withPropertyValues(
                        "gateway.jwt.secret=s3cret-s3cret-s3cret-s3cret-s3cret-12",
                        "gateway.public-paths[0]=/api/v1/xac-thuc/dang-nhap",
                        "gateway.blocked-paths[0]=/mcp/**")
                .run(ctx -> {
                    assertThat(ctx).hasNotFailed();
                    GatewayProperties p = ctx.getBean(GatewayProperties.class);
                    assertThat(p.jwt().secret()).startsWith("s3cret");
                    assertThat(p.publicPaths()).containsExactly("/api/v1/xac-thuc/dang-nhap");
                    assertThat(p.blockedPaths()).containsExactly("/mcp/**");
                    assertThat(p.rules().requireScope()).isFalse();
                });
    }
}
