package com.examplex.demo.controller;

import com.examplex.demo.service.AuthTokenService;
import com.examplex.demo.service.LoginManagementService;
import com.examplex.demo.service.ExternalApiService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

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
     * {
     *     "message": "Processamento executado com sucesso",
     *     "success": true
     * }
     */
    @PostMapping("/process")
    public ResponseEntity<Map<String, Object>> processManually() {
        try {
            log.info("Processamento manual iniciado via API");
            loginManagementService.processLoginManagement();

            return ResponseEntity.ok(Map.of(
                    "success", true,
                    "message", "Processamento executado com sucesso"
            ));
        } catch (Exception e) {
            log.error("Erro no processamento manual: {}", e.getMessage(), e);
            return ResponseEntity.status(500).body(Map.of(
                    "success", false,
                    "message", "Erro no processamento: " + e.getMessage()
            ));
        }
    }

    /**
     * Testa a obtenção de token de autenticação
     * {
     *     "tokenPrefix": "eyJraWQiOiIzYjdmMTlhNS02NzMxLTQ0NjAtODM1MC1mYzFhNj...",
     *     "success": true,
     *     "tokenLength": 2283,
     *     "message": "Token obtido com sucesso"
     * }
     */
    @GetMapping("/test-token")
    public ResponseEntity<Map<String, Object>> testToken() {
        try {
            log.info("Testando obtenção de token");
            String token = authTokenService.getValidToken();

            return ResponseEntity.ok(Map.of(
                    "success", true,
                    "message", "Token obtido com sucesso",
                    "tokenPrefix", token != null ? token.substring(0, Math.min(50, token.length())) + "..." : "null",
                    "tokenLength", token != null ? token.length() : 0
            ));
        } catch (Exception e) {
            log.error("Erro ao testar token: {}", e.getMessage(), e);
            return ResponseEntity.status(500).body(Map.of(
                    "success", false,
                    "message", "Erro ao obter token: " + e.getMessage()
            ));
        }
    }

    /**
     * Força a renovação do token de autenticação
     * {
     *     "tokenPrefix": "eyJraWQiOiIzYjdmMTlhNS02NzMxLTQ0NjAtODM1MC1mYzFhNj...",
     *     "success": true,
     *     "tokenLength": 2283,
     *     "message": "Token renovado com sucesso"
     * }
     */
    @PostMapping("/refresh-token")
    public ResponseEntity<Map<String, Object>> refreshToken() {
        try {
            log.info("Forçando renovação de token");
            authTokenService.invalidateToken();
            String newToken = authTokenService.getValidToken();

            return ResponseEntity.ok(Map.of(
                    "success", true,
                    "message", "Token renovado com sucesso",
                    "tokenPrefix", newToken != null ? newToken.substring(0, Math.min(50, newToken.length())) + "..." : "null",
                    "tokenLength", newToken != null ? newToken.length() : 0
            ));
        } catch (Exception e) {
            log.error("Erro ao renovar token: {}", e.getMessage(), e);
            return ResponseEntity.status(500).body(Map.of(
                    "success", false,
                    "message", "Erro ao renovar token: " + e.getMessage()
            ));
        }
    }

    /**
     * Retorna estatísticas do sistema
     * {
     *     "stats": {
     *         "systemStatus": "UP_TO_DATE",
     *         "pendingItems": 0,
     *         "typeCodes": {
     *             "block": -4104,
     *             "unblock": -4105
     *         },
     *         "statusCodes": {
     *             "error": -4108,
     *             "success": -4107,
     *             "queue": -4106
     *         }
     *     },
     *     "success": true,
     *     "message": "Estatísticas recuperadas com sucesso"
     * }
     */
    @GetMapping("/stats")
    public ResponseEntity<Map<String, Object>> getStats() {
        try {
            long pendingCount = loginManagementService.getPendingCount();

            return ResponseEntity.ok(Map.of(
                    "success", true,
                    "stats", Map.of(
                            "pendingItems", pendingCount,
                            "systemStatus", pendingCount > 0 ? "PROCESSING_NEEDED" : "UP_TO_DATE",
                            "statusCodes", Map.of(
                                    "queue", -4106,
                                    "success", -4107,
                                    "error", -4108
                            ),
                            "typeCodes", Map.of(
                                    "block", -4104,
                                    "unblock", -4105
                            )
                    ),
                    "message", "Estatísticas recuperadas com sucesso"
            ));
        } catch (Exception e) {
            log.error("Erro ao recuperar estatísticas: {}", e.getMessage(), e);
            return ResponseEntity.status(500).body(Map.of(
                    "success", false,
                    "message", "Erro ao recuperar estatísticas: " + e.getMessage()
            ));
        }
    }

    /**
     * Bloqueia um usuário diretamente (endpoint de teste)
     * {
     *     "data": "N/A",
     *     "message": "Usuário bloqueado com sucesso",
     *     "success": true
     * }
     */
    @PostMapping("/blockUserIcred/{externalKey}")
    public ResponseEntity<Map<String, Object>> blockUserIcred(@PathVariable String externalKey) {
        try {
            log.warn("TESTE: Bloqueando usuário com externalKey: {}", externalKey);
            var result = externalApiService.blockUser(externalKey);

            return ResponseEntity.ok(Map.of(
                    "success", result.isSuccess(),
                    "message", result.getMessage(),
                    "data", result.getData() != null ? result.getData() : "N/A"
            ));
        } catch (Exception e) {
            log.error("Erro ao bloquear usuário: {}", e.getMessage(), e);
            return ResponseEntity.status(500).body(Map.of(
                    "success", false,
                    "message", "Erro ao bloquear usuário: " + e.getMessage()
            ));
        }
    }

    /**
     * Desbloqueia um usuário diretamente (endpoint de teste)
     * {
     *     "newPassword": "Xe2q4rOi8V2K",
     *     "message": "Usuário desbloqueado com sucesso",
     *     "success": true
     * }
     */
    @PostMapping("/unblockUserIcred/{externalKey}")
    public ResponseEntity<Map<String, Object>> unblockUserIcred(@PathVariable String externalKey) {
        try {
            log.warn("TESTE: Desbloqueando usuário com externalKey: {}", externalKey);
            var result = externalApiService.unblockUser(externalKey);

            return ResponseEntity.ok(Map.of(
                    "success", result.isSuccess(),
                    "message", result.getMessage(),
                    "newPassword", result.getData() != null ? result.getData() : "N/A"
            ));
        } catch (Exception e) {
            log.error("Erro ao desbloquear usuário: {}", e.getMessage(), e);
            return ResponseEntity.status(500).body(Map.of(
                    "success", false,
                    "message", "Erro ao desbloquear usuário: " + e.getMessage()
            ));
        }
    }

    /**
     * Verifica a saúde geral do sistema
     */
    @GetMapping("/health")
    public ResponseEntity<Map<String, Object>> healthCheck() {
        try {
            boolean apiHealthy = false;
            boolean dbHealthy = false;
            String tokenStatus = "UNKNOWN";
            long pendingCount = 0;

            // Testa a conexão com a API
            try {
                String token = authTokenService.getValidToken();
                apiHealthy = token != null && !token.isEmpty();
                tokenStatus = apiHealthy ? "VALID" : "INVALID";
            } catch (Exception e) {
                tokenStatus = "ERROR: " + e.getMessage();
            }

            // Testa a conexão com o banco
            try {
                pendingCount = loginManagementService.getPendingCount();
                dbHealthy = true;
            } catch (Exception e) {
                log.error("Erro na conexão com banco: {}", e.getMessage());
            }

            boolean overallHealthy = apiHealthy && dbHealthy;

            return ResponseEntity.ok(Map.of(
                    "success", true,
                    "health", Map.of(
                            "overall", overallHealthy ? "HEALTHY" : "UNHEALTHY",
                            "api", apiHealthy ? "HEALTHY" : "UNHEALTHY",
                            "database", dbHealthy ? "HEALTHY" : "UNHEALTHY",
                            "tokenStatus", tokenStatus,
                            "pendingItems", pendingCount
                    ),
                    "message", "Health check executado com sucesso"
            ));
        } catch (Exception e) {
            log.error("Erro no health check: {}", e.getMessage(), e);
            return ResponseEntity.status(500).body(Map.of(
                    "success", false,
                    "health", Map.of(
                            "overall", "UNHEALTHY",
                            "error", e.getMessage()
                    ),
                    "message", "Erro no health check: " + e.getMessage()
            ));
        }
    }
}