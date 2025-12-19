package com.examplex.demo.scheduler;

import com.examplex.demo.service.LoginManagementService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
@ConditionalOnProperty(value = "scheduler.login-management.enabled", havingValue = "true", matchIfMissing = true)
public class LoginManagementScheduler {

    private final com.examplex.demo.service.LoginManagementService loginManagementService;

    @Scheduled(cron = "${scheduler.login-management.cron:0 */5 * * * *}")
    public void executeLoginManagementProcessing() {
        log.info("Executando scheduler de Login Management");

        try {
            loginManagementService.processLoginManagement();
        } catch (Exception e) {
            log.error("Erro na execução do scheduler de Login Management: {}", e.getMessage(), e);
        }
    }
}
