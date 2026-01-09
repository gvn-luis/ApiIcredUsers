package com.examplex.demo.model.dto;

import lombok.Data;
import lombok.AllArgsConstructor;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ApiCreateGroupRequestDto {
    private String name;
    private String label;
    private String partnerExternalKey;
}