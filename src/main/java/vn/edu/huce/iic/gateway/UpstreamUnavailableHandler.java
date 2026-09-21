package vn.edu.huce.iic.gateway;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.webflux.error.ErrorWebExceptionHandler;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.net.ConnectException;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.TimeoutException;

/**
 * Service phía sau không kết nối được hoặc quá hạn kết nối: trả 503 JSON rõ ràng thay vì 500 mặc định,
 * để frontend hiển thị "đang bảo trì". Các lỗi khác được giao lại cho bộ xử lý lỗi mặc định.
 */
@Component
@Order(-2)
public class UpstreamUnavailableHandler implements ErrorWebExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(UpstreamUnavailableHandler.class);

    @Override
    public Mono<Void> handle(ServerWebExchange exchange, Throwable ex) {
        if (!isUpstreamDown(ex) || exchange.getResponse().isCommitted()) {
            return Mono.error(ex);
        }
        log.warn("Upstream unavailable for {} {}: {}", exchange.getRequest().getMethod(),
                exchange.getRequest().getPath().value(), rootMessage(ex));
        var response = exchange.getResponse();
        response.setStatusCode(HttpStatus.SERVICE_UNAVAILABLE);
        response.getHeaders().setContentType(MediaType.APPLICATION_JSON);
        String body = "{\"code\":503,\"isSuccess\":false,\"message\":\"Hệ thống đang bảo trì hoặc tạm thời "
                + "không phản hồi, vui lòng thử lại sau.\",\"errors\":{\"type\":\"SERVICE_UNAVAILABLE\"}}";
        return response.writeWith(Mono.just(response.bufferFactory().wrap(body.getBytes(StandardCharsets.UTF_8))));
    }

    private static boolean isUpstreamDown(Throwable ex) {
        for (Throwable t = ex; t != null; t = t.getCause()) {
            if (t instanceof ConnectException || t instanceof TimeoutException) {
                return true;
            }
            if (t.getCause() == t) {
                break;
            }
        }
        return false;
    }

    private static String rootMessage(Throwable ex) {
        Throwable t = ex;
        while (t.getCause() != null && t.getCause() != t) {
            t = t.getCause();
        }
        return t.getMessage();
    }
}
