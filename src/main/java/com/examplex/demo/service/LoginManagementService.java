package com.examplex.demo.service;

import com.examplex.demo.model.LoginManagement;
import com.examplex.demo.model.dto.ApiResponseDto;
import com.examplex.demo.repository.LoginManagementRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class LoginManagementService {

    private final LoginManagementRepository repository;
    private final ExternalApiService externalApiService;

    // Status constants
    private static final int STATUS_ERROR = -4108;
    private static final int STATUS_SUCCESS = -4107;
    private static final int STATUS_QUEUE = -4106;

    // Management Type constants
    private static final int TYPE_UNBLOCK = -4105;
    private static final int TYPE_BLOCK = -4104;

    // Limite de caracteres para log_Alteracao_Rastro
    private static final int LOG_MAX_LENGTH = 50;

    /**
     * Processa todos os itens pendentes de Login Management
     */
    @Transactional
    public void processLoginManagement() {
        log.info("Iniciando processamento de Login Management");

        List<LoginManagement> pendingItems = repository.findPendingProcessing();

        if (pendingItems.isEmpty()) {
            log.info("Nenhum item pendente encontrado");
            return;
        }

        log.info("Encontrados {} itens para processamento", pendingItems.size());

        int successCount = 0;
        int errorCount = 0;

        for (LoginManagement item : pendingItems) {
            try {
                boolean success = processItem(item);
                if (success) {
                    successCount++;
                } else {
                    errorCount++;
                }

                Thread.sleep(500);

            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                log.error("Processamento interrompido");
                break;
            } catch (Exception e) {
                log.error("Erro inesperado ao processar item ID {}: {}", item.getId(), e.getMessage(), e);
                updateItemStatus(item.getId(), STATUS_ERROR, "Erro inesperado", null);
                errorCount++;
            }
        }

        log.info("Processamento finalizado. Sucessos: {}, Erros: {}", successCount, errorCount);
    }

    /**
     * Processa um item individual
     */
    private boolean processItem(LoginManagement item) {
        try {
            log.info("Processando item ID: {} | Tipo: {} | ExternalKey: {}",
                    item.getId(), getManagementTypeDescription(item.getManagementType()), item.getExternalKey());

            if (item.getExternalKey() == null || item.getExternalKey().trim().isEmpty()) {
                log.warn("ExternalKey vazia para item ID: {}", item.getId());
                updateItemStatus(item.getId(), STATUS_ERROR, "ExternalKey vazia", null);
                return false;
            }

            ApiResponseDto apiResponse;

            if (item.getManagementType() == TYPE_BLOCK) {
                apiResponse = externalApiService.blockUser(item.getExternalKey());

                if (apiResponse.isSuccess()) {
                    updateItemStatus(item.getId(), STATUS_SUCCESS, "Bloqueio OK", null);
                    log.info("Item ID: {} - Bloqueio realizado com sucesso", item.getId());
                    return true;
                } else {
                    String errorMsg = extractErrorCode(apiResponse.getMessage());
                    updateItemStatus(item.getId(), STATUS_ERROR, errorMsg, null);
                    log.error("Erro no bloqueio do item ID: {} - {}", item.getId(), apiResponse.getMessage());
                    return false;
                }

            } else if (item.getManagementType() == TYPE_UNBLOCK) {
                apiResponse = externalApiService.unblockUser(item.getExternalKey());

                if (apiResponse.isSuccess()) {
                    String newPassword = (String) apiResponse.getData();
                    String dadosComplementares = null;

                    if (newPassword != null && !newPassword.trim().isEmpty()) {
                        dadosComplementares = "{\"newPassword\":\"" + newPassword + "\"}";
                        log.info("Item ID: {} - Desbloqueio OK. Nova senha gerada", item.getId());
                    } else {
                        log.info("Item ID: {} - Desbloqueio OK", item.getId());
                    }

                    updateItemStatus(item.getId(), STATUS_SUCCESS, "Desbloqueio OK", dadosComplementares);
                    return true;
                } else {
                    String errorMsg = extractErrorCode(apiResponse.getMessage());
                    updateItemStatus(item.getId(), STATUS_ERROR, errorMsg, null);
                    log.error("Erro no desbloqueio do item ID: {} - {}", item.getId(), apiResponse.getMessage());
                    return false;
                }

            } else {
                log.warn("Tipo de management desconhecido: {} para item ID: {}", item.getManagementType(), item.getId());
                updateItemStatus(item.getId(), STATUS_ERROR, "Tipo desconhecido", null);
                return false;
            }

        } catch (Exception e) {
            log.error("Erro inesperado no processamento do item ID: {} - {}", item.getId(), e.getMessage(), e);
            updateItemStatus(item.getId(), STATUS_ERROR, "Erro no processamento", null);
            return false;
        }
    }

    /**
     * Extrai código de erro relevante de mensagens longas
     */
    private String extractErrorCode(String message) {
        if (message == null || message.isEmpty()) {
            return "Erro desconhecido";
        }

        if (message.contains("422")) {
            if (message.contains("ALREADY_ACTIVE")) {
                return "Já ativo";
            }
            return "Erro 422";
        }

        if (message.contains("401") || message.contains("403")) {
            return "Erro auth";
        }

        if (message.contains("404")) {
            return "Não encontrado";
        }

        if (message.contains("500")) {
            return "Erro servidor";
        }

        return truncateLog(message);
    }

    /**
     * Trunca a mensagem de log para caber no banco
     */
    private String truncateLog(String message) {
        if (message == null) {
            return "";
        }

        if (message.length() <= LOG_MAX_LENGTH) {
            return message;
        }

        return message.substring(0, LOG_MAX_LENGTH - 3) + "...";
    }

    /**
     * Atualiza o status de um item no banco de dados
     */
    private void updateItemStatus(Integer itemId, Integer newStatus, String logMessage, String dadosComplementares) {
        try {
            String truncatedLog = truncateLog(logMessage);

            if (dadosComplementares != null && !dadosComplementares.trim().isEmpty()) {
                repository.updateStatusWithData(itemId, newStatus, LocalDateTime.now(), truncatedLog, dadosComplementares);
                log.debug("Item ID: {} atualizado com dados complementares", itemId);
            } else {
                repository.updateStatus(itemId, newStatus, LocalDateTime.now(), truncatedLog);
            }

            log.debug("Status do item ID: {} atualizado para: {} - {}", itemId, getStatusDescription(newStatus), truncatedLog);
        } catch (Exception e) {
            log.error("Erro ao atualizar status do item ID: {} - {}", itemId, e.getMessage(), e);

            try {
                String minimalLog = "Erro";
                if (dadosComplementares != null && !dadosComplementares.trim().isEmpty()) {
                    repository.updateStatusWithData(itemId, newStatus, LocalDateTime.now(), minimalLog, dadosComplementares);
                } else {
                    repository.updateStatus(itemId, newStatus, LocalDateTime.now(), minimalLog);
                }
                log.info("Status atualizado com mensagem mínima para item ID: {}", itemId);
            } catch (Exception ex) {
                log.error("Falha crítica ao atualizar item ID: {}", itemId, ex);
            }
        }
    }

    /**
     * Retorna o número de itens pendentes
     */
    public long getPendingCount() {
        try {
            return repository.countPendingProcessing();
        } catch (Exception e) {
            log.error("Erro ao contar itens pendentes: {}", e.getMessage());
            return 0;
        }
    }

    private String getStatusDescription(Integer status) {
        switch (status) {
            case STATUS_ERROR: return "Erro(-4108)";
            case STATUS_SUCCESS: return "Sucesso(-4107)";
            case STATUS_QUEUE: return "Fila(-4106)";
            default: return "Desconhecido(" + status + ")";
        }
    }

    private String getManagementTypeDescription(Integer type) {
        switch (type) {
            case TYPE_BLOCK: return "Block(-4104)";
            case TYPE_UNBLOCK: return "Unblock(-4105)";
            default: return "Desconhecido(" + type + ")";
        }
    }
}