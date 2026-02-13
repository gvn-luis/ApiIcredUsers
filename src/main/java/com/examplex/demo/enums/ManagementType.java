package com.examplex.demo.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum ManagementType {

    BLOCK(-4104, "Bloquear usuário"),
    UNBLOCK(-4105, "Desbloquear usuário"),
    RESET(2268, "Reset de senha"),
    CREATE(3833, "Criar usuário"),
    LINK_GROUP(4480, "Vincular a grupo"),
    BANNED(5050, "Usuário banido");  // NOVO: Status de banimento

    private final Integer code;
    private final String description;

    public static ManagementType fromCode(Integer code) {
        for (ManagementType type : values()) {
            if (type.code.equals(code)) {
                return type;
            }
        }
        throw new IllegalArgumentException("Tipo inválido: " + code);
    }
}