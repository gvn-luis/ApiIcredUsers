package com.examplex.demo.controller;

import com.examplex.demo.service.LoginManagementService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/login-management")
@RequiredArgsConstructor
@Slf4j
public class LoginManagementController {

    private final com.examplex.demo.service.LoginManagementService loginManagementService;

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

    @GetMapping("/status")
    public ResponseEntity<Map<String, Object>> getStatus() {
        try {
            long pendingCount = loginManagementService.getPendingCount();

            return ResponseEntity.ok(Map.of(
                    "success", true,
                    "pendingItems", pendingCount,
                    "message", "Status recuperado com sucesso"
            ));
        } catch (Exception e) {
            log.error("Erro ao recuperar status: {}", e.getMessage(), e);

            return ResponseEntity.status(500).body(Map.of(
                    "success", false,
                    "message", "Erro ao recuperar status: " + e.getMessage()
            ));
        }
    }
}
