    package com.examplex.demo.model.dto;

    import lombok.AllArgsConstructor;
    import lombok.Data;
    import lombok.NoArgsConstructor;

    /**
     * DTO padrão para respostas da API externa
     */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public class ApiResponseDto {

    private boolean success;
    private String message;
    private Object data;

    /**
     * Cria uma resposta de sucesso
     */
    public static ApiResponseDto success(String message, Object data) {
        return new ApiResponseDto(true, message, data);
    }

    /**
     * Cria uma resposta de sucesso sem dados
     */
    public static ApiResponseDto success(String message) {
        return new ApiResponseDto(true, message, null);
    }

    /**
     * Cria uma resposta de erro
     */
    public static ApiResponseDto error(String message) {
        return new ApiResponseDto(false, message, null);
    }

    /**
     * Cria uma resposta de erro com dados
     */
    public static ApiResponseDto error(String message, Object data) {
        return new ApiResponseDto(false, message, data);
    }
}