package vn.edu.huce.iic.gateway.dto.response;

public record ApiResponse<T>(int code, boolean isSuccess, String message, T data, ErrorResponse errors) {
}