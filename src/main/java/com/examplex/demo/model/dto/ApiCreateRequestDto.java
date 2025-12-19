package com.examplex.demo.model.dto;

import lombok.Data;
import lombok.AllArgsConstructor;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ApiCreateRequestDto {
    private String personCode;
    private String userProfileId;
    private String partnerUuid;
}
