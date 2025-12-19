package com.examplex.demo.model.dto;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ApiResponseDto {
    private boolean success;
    private String message;
    private Object data;
}