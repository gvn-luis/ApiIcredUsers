package com.examplex.demo.model.dto;

import lombok.Data;

/**
 * Request para adicionar usuário ao grupo
 */
@Data
public class AddUserToGroupRequest {

    private String groupUuid;

    private String personCode;
}