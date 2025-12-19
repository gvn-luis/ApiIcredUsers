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
@RequestMapping("/api/monitoring")
@RequiredArgsConstructor
@Slf4j
public class MonitoringController {

    private final LoginManagementService loginManagementService;
    private final AuthTokenService authTokenService;
    private final ExternalApiService externalApiService;

    /**
     * Endpoint para testar a obtenção de token
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
     * Endpoint para forçar renovação do token
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
     * Endpoint para obter estatísticas detalhadas
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
     * Endpoint para testar API de Block (apenas para testes - use com cuidado!)
     */
    @PostMapping("/test-block/{externalKey}")
    public ResponseEntity<Map<String, Object>> testBlock(@PathVariable String externalKey) {
        try {
            log.warn("TESTE: Executando bloqueio para externalKey: {}", externalKey);

            var result = externalApiService.blockUser(externalKey);

            return ResponseEntity.ok(Map.of(
                    "success", true,
                    "message", "Teste de bloqueio executado",
                    "apiResult", Map.of(
                            "success", result.isSuccess(),
                            "message", result.getMessage(),
                            "data", result.getData()
                    )
            ));
        } catch (Exception e) {
            log.error("Erro no teste de bloqueio: {}", e.getMessage(), e);

            return ResponseEntity.status(500).body(Map.of(
                    "success", false,
                    "message", "Erro no teste de bloqueio: " + e.getMessage()
            ));
        }
    }

    /**
     * Endpoint para testar API de Unblock (apenas para testes - use com cuidado!)
     */
    @PostMapping("/test-unblock/{externalKey}")
    public ResponseEntity<Map<String, Object>> testUnblock(@PathVariable String externalKey) {
        try {
            log.warn("TESTE: Executando desbloqueio para externalKey: {}", externalKey);

            var result = externalApiService.unblockUser(externalKey);

            return ResponseEntity.ok(Map.of(
                    "success", true,
                    "message", "Teste de desbloqueio executado",
                    "apiResult", Map.of(
                            "success", result.isSuccess(),
                            "message", result.getMessage(),
                            "data", result.getData(),
                            "newPassword", result.getData()  // Destacar a senha se houver
                    )
            ));
        } catch (Exception e) {
            log.error("Erro no teste de desbloqueio: {}", e.getMessage(), e);

            return ResponseEntity.status(500).body(Map.of(
                    "success", false,
                    "message", "Erro no teste de desbloqueio: " + e.getMessage()
            ));
        }
    }

    /**
     * Endpoint para verificar saúde do sistema
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
                dbHealthy = true; // Se chegou até aqui, o DB está ok
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