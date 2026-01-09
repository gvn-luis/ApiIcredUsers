package com.examplex.demo.model.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class DadosComplementaresDto {

    @JsonProperty("telefonePIN")
    private String telefonePIN;

    @JsonProperty("managementGroups_nome")
    private String managementGroupsNome;

    @JsonProperty("managementGroups_uuid")
    private String managementGroupsUuid;
}