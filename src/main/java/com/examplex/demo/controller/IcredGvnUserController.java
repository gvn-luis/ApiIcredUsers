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
                                    "unblock", -4105,
                                    "create", 3833,
                                    "reset", 2268
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
     * Cria um grupo de vendedores (endpoint de teste)
     */
    @PostMapping("/createSellerGroup")
    public ResponseEntity<Map<String, Object>> createSellerGroup(@RequestBody Map<String, String> body) {
        try {
            String name = body.get("name");
            String partnerExternalKey = body.get("partnerExternalKey");

            if (name == null || name.trim().isEmpty()) {
                return ResponseEntity.badRequest().body(Map.of(
                        "success", false,
                        "message", "name é obrigatório"
                ));
            }

            if (partnerExternalKey == null || partnerExternalKey.trim().isEmpty()) {
                return ResponseEntity.badRequest().body(Map.of(
                        "success", false,
                        "message", "partnerExternalKey é obrigatório"
                ));
            }

            log.warn("TESTE: Criando grupo: {}", name);
            var result = externalApiService.createSellerGroup(name, partnerExternalKey);

            return ResponseEntity.ok(Map.of(
                    "success", result.isSuccess(),
                    "message", result.getMessage(),
                    "groupUuid", result.getData() != null ? result.getData() : "N/A"
            ));
        } catch (Exception e) {
            log.error("Erro ao criar grupo: {}", e.getMessage(), e);
            return ResponseEntity.status(500).body(Map.of(
                    "success", false,
                    "message", "Erro ao criar grupo: " + e.getMessage()
            ));
        }
    }

    /**
     * Cria um usuário diretamente (endpoint de teste)
     */
    @PostMapping("/createUserIcred")
    public ResponseEntity<Map<String, Object>> createUserIcred(@RequestBody Map<String, String> body) {
        try {
            String personCode = body.get("personCode");

            if (personCode == null || personCode.trim().isEmpty()) {
                return ResponseEntity.badRequest().body(Map.of(
                        "success", false,
                        "message", "personCode é obrigatório"
                ));
            }

            log.warn("TESTE: Criando usuário com personCode: {}", personCode);
            var result = externalApiService.createUser(personCode);

            return ResponseEntity.ok(Map.of(
                    "success", result.isSuccess(),
                    "message", result.getMessage(),
                    "userUuid", result.getData() != null ? result.getData() : "N/A"
            ));
        } catch (Exception e) {
            log.error("Erro ao criar usuário: {}", e.getMessage(), e);
            return ResponseEntity.status(500).body(Map.of(
                    "success", false,
                    "message", "Erro ao criar usuário: " + e.getMessage()
            ));
        }
    }

    /**
     * Adiciona um usuário a um grupo (endpoint de teste)
     */
    @PostMapping("/addUserToGroup")
    public ResponseEntity<Map<String, Object>> addUserToGroup(@RequestBody Map<String, String> body) {
        try {
            String groupUuid = body.get("groupUuid");
            String userUuid = body.get("userUuid");

            if (groupUuid == null || groupUuid.trim().isEmpty() ||
                    userUuid == null || userUuid.trim().isEmpty()) {
                return ResponseEntity.badRequest().body(Map.of(
                        "success", false,
                        "message", "groupUuid e userUuid são obrigatórios"
                ));
            }

            log.warn("TESTE: Adicionando usuário {} ao grupo {}", userUuid, groupUuid);
            var result = externalApiService.addUserToGroup(groupUuid, userUuid);

            return ResponseEntity.ok(Map.of(
                    "success", result.isSuccess(),
                    "message", result.getMessage(),
                    "data", result.getData() != null ? result.getData() : "N/A"
            ));
        } catch (Exception e) {
            log.error("Erro ao adicionar usuário ao grupo: {}", e.getMessage(), e);
            return ResponseEntity.status(500).body(Map.of(
                    "success", false,
                    "message", "Erro ao adicionar usuário ao grupo: " + e.getMessage()
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