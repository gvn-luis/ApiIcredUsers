// ApiRequestDto.java
package com.examplex.demo.model.dto;

import lombok.Data;
import lombok.AllArgsConstructor;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ApiRequestDto {
    private String partnerUuid;
    private String history;
}