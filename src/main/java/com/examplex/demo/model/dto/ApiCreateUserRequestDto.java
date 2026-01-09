package com.examplex.demo.model.dto;

import lombok.Data;
import lombok.AllArgsConstructor;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ApiCreateUserRequestDto {
    private String personCode;
    private Integer userProfileId;
    private String partnerUuid;
}