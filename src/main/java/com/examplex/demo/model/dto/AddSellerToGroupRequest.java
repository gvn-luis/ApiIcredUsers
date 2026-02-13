package com.examplex.demo.model.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Request para adicionar seller ao grupo
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class AddSellerToGroupRequest {
    private String personCode;
}