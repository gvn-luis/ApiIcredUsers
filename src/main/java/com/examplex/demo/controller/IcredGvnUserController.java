package com.examplex.demo.controller;

import com.examplex.demo.model.dto.ApiResponseDto;
import com.examplex.demo.model.dto.ApiCreateUserRequestDto;
import com.examplex.demo.model.dto.ApiCreateGroupRequestDto;
import com.examplex.demo.model.dto.AddUserToGroupRequest;
import com.examplex.demo.service.AuthTokenService;
import com.examplex.demo.service.LoginManagementService;
import com.examplex.demo.service.ExternalApiService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import jakarta.validation.Valid;
import java.util.Map;

/**
 * Controller REST para gerenciamento de usuários iCred
 *
 * RESPONSABILIDADES:
 * - Receber requisições HTTP
 * - Validar entrada básica (via @Valid)
 * - Delegar para Services
 * - Retornar respostas HTTP
 *
 * NÃO TEM:
 * - Lógica de negócio
 * - Try-catch (exceções tratadas globalmente)
 * - Validações complexas
 */
@RestController
@RequestMapping("/api/icredGvnUser")
@RequiredArgsConstructor
@Slf4j
public class IcredGvnUserController {

    private final LoginManagementService loginManagementService;
    private final AuthTokenService authTokenService;
    private final ExternalApiService externalApiService;

    /**
     * Processa todos os itens pendentes da fila
     */
    @PostMapping("/process")
    public ResponseEntity<Map<String, Object>> processManually() {
        log.info("Processamento manual iniciado via API");
        loginManagementService.processLoginManagement();

        return ResponseEntity.ok(Map.of(
                "success", true,
                "message", "Processamento executado com sucesso"
        ));
    }

    /**
     * Testa a obtenção de token de autenticação
     */
    @GetMapping("/test-token")
    public ResponseEntity<Map<String, Object>> testToken() {
        String token = authTokenService.getValidToken();

        return ResponseEntity.ok(Map.of(
                "success", true,
                "message", "Token obtido com sucesso",
                "tokenPrefix", token.substring(0, Math.min(50, token.length())) + "...",
                "tokenLength", token.length()
        ));
    }

    /**
     * Força a renovação do token de autenticação
     */
    @PostMapping("/refresh-token")
    public ResponseEntity<Map<String, Object>> refreshToken() {
        authTokenService.invalidateToken();
        String newToken = authTokenService.getValidToken();

        return ResponseEntity.ok(Map.of(
                "success", true,
                "message", "Token renovado com sucesso",
                "tokenPrefix", newToken.substring(0, Math.min(50, newToken.length())) + "...",
                "tokenLength", newToken.length()
        ));
    }

    /**
     * Retorna estatísticas do sistema
     */
    @GetMapping("/stats")
    public ResponseEntity<Map<String, Object>> getStats() {
        long pendingCount = loginManagementService.getPendingCount();

        return ResponseEntity.ok(Map.of(
                "success", true,
                "stats", Map.of(
                        "pendingItems", pendingCount,
                        "systemStatus", pendingCount > 0 ? "PROCESSING_NEEDED" : "UP_TO_DATE",
                        "statusCodes", Map.of(
                                "queue", -4106,
                                "success", -4107,
                                "pending", -4109
                        ),
                        "typeCodes", Map.of(
                                "block", -4104,
                                "unblock", -4105,
                                "create", 3833,
                                "reset", 2268,
                                "linkGroup", 4480,
                                "banned", 5050
                        )
                ),
                "message", "Estatísticas recuperadas com sucesso"
        ));
    }

    /**
     * Bloqueia um usuário diretamente
     */
    @PostMapping("/blockUserIcred/{externalKey}")
    public ResponseEntity<ApiResponseDto> blockUserIcred(@PathVariable String externalKey) {
        log.info("Bloqueando usuário: {}", externalKey);
        ApiResponseDto result = externalApiService.blockUser(externalKey);
        return ResponseEntity.ok(result);
    }

    /**
     * Desbloqueia um usuário diretamente
     */
    @PostMapping("/unblockUserIcred/{externalKey}")
    public ResponseEntity<ApiResponseDto> unblockUserIcred(@PathVariable String externalKey) {
        log.info("Desbloqueando usuário: {}", externalKey);
        ApiResponseDto result = externalApiService.unblockUser(externalKey);
        return ResponseEntity.ok(result);
    }



    /**
     * Cria um usuário diretamente
     */
    @PostMapping("/createUserIcred")
    public ResponseEntity<ApiResponseDto> createUserIcred(@Valid @RequestBody ApiCreateUserRequestDto request) {
        log.info("Criando usuário: {}", request.getPersonCode());
        ApiResponseDto result = externalApiService.createUser(request.getPersonCode());
        return ResponseEntity.ok(result);
    }

    /**
     * Adiciona um usuário a um grupo
     */
    @PostMapping("/addUserToGroup")
    public ResponseEntity<ApiResponseDto> addUserToGroup(@Valid @RequestBody AddUserToGroupRequest request) {
        log.info("Adicionando usuário {} ao grupo {}", request.getPersonCode(), request.getGroupUuid());
        ApiResponseDto result = externalApiService.addUserToGroup(
                request.getGroupUuid(),
                request.getPersonCode()
        );
        return ResponseEntity.ok(result);
    }

    /**
     * Lista vendedores de um grupo
     */
    @GetMapping("/listGroupSellers/{groupUuid}")
    public ResponseEntity<ApiResponseDto> listGroupSellers(@PathVariable String groupUuid) {
        log.info("Listando vendedores do grupo: {}", groupUuid);
        ApiResponseDto result = externalApiService.listGroupSellers(groupUuid);
        return ResponseEntity.ok(result);
    }
}