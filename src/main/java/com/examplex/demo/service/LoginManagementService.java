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

    // Status constants baseados nos seus dados
    private static final int STATUS_ERROR = -4108;     // Erro
    private static final int STATUS_SUCCESS = -4107;   // Sucesso
    private static final int STATUS_QUEUE = -4106;     // Fila

    // Management Type constants baseados nos seus dados
    private static final int TYPE_UNBLOCK = -4105;     // Unblock
    private static final int TYPE_BLOCK = -4104;       // Block

    /**
     * Processa todos os itens pendentes de Login Management
     */
    @Transactional
    public void processLoginManagement() {
        log.info("Iniciando processamento de Login Management");

        List<LoginManagement> pendingItems = repository.findPendingProcessing();

        if (pendingItems.isEmpty()) {
            log.info("Nenhum item pendente encontrado para processamento");
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

                // Pequena pausa entre processamentos para evitar sobrecarga da API
                Thread.sleep(500);

            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                log.error("Processamento interrompido");
                break;
            } catch (Exception e) {
                log.error("Erro inesperado ao processar item ID {}: {}", item.getId(), e.getMessage(), e);
                updateItemStatus(item.getId(), STATUS_ERROR, "Erro inesperado: " + e.getMessage(), null);
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

            // Validação da ExternalKey
            if (item.getExternalKey() == null || item.getExternalKey().trim().isEmpty()) {
                log.warn("ExternalKey vazia para item ID: {}", item.getId());
                updateItemStatus(item.getId(), STATUS_ERROR, "ExternalKey não informada", null);
                return false;
            }

            ApiResponseDto apiResponse;

            if (item.getManagementType() == TYPE_BLOCK) {
                // Bloquear usuário
                apiResponse = externalApiService.blockUser(item.getExternalKey());

                if (apiResponse.isSuccess()) {
                    updateItemStatus(item.getId(), STATUS_SUCCESS, "Usuário bloqueado com sucesso", null);
                    log.info("Item ID: {} - Usuário bloqueado com sucesso", item.getId());
                    return true;
                } else {
                    updateItemStatus(item.getId(), STATUS_ERROR, "Erro no bloqueio: " + apiResponse.getMessage(), null);
                    log.error("Erro no bloqueio do item ID: {} - {}", item.getId(), apiResponse.getMessage());
                    return false;
                }

            } else if (item.getManagementType() == TYPE_UNBLOCK) {
                // Desbloquear usuário
                apiResponse = externalApiService.unblockUser(item.getExternalKey());

                if (apiResponse.isSuccess()) {
                    // Se há uma nova senha, salva nos dados complementares
                    String newPassword = (String) apiResponse.getData();
                    String dadosComplementares = null;

                    if (newPassword != null && !newPassword.trim().isEmpty()) {
                        // Pode ser um JSON simples ou apenas a senha, dependendo de como você quer armazenar
                        dadosComplementares = "{\"newPassword\":\"" + newPassword + "\"}";
                        log.info("Item ID: {} - Usuário desbloqueado com sucesso. Nova senha: {}",
                                item.getId(), newPassword);
                    } else {
                        log.info("Item ID: {} - Usuário desbloqueado com sucesso", item.getId());
                    }

                    updateItemStatus(item.getId(), STATUS_SUCCESS, "Usuário desbloqueado com sucesso", dadosComplementares);
                    return true;
                } else {
                    updateItemStatus(item.getId(), STATUS_ERROR,   apiResponse.getMessage(), null);
                    log.error("Erro no desbloqueio do item ID: {} - {}", item.getId(), apiResponse.getMessage());
                    return false;
                }

            } else {
                log.warn("Tipo de management desconhecido: {} para item ID: {}",
                        item.getManagementType(), item.getId());
                updateItemStatus(item.getId(), STATUS_ERROR, "Tipo de management desconhecido: " + item.getManagementType(), null);
                return false;
            }

        } catch (Exception e) {
            log.error("Erro inesperado no processamento do item ID: {} - {}", item.getId(), e.getMessage(), e);
            updateItemStatus(item.getId(), STATUS_ERROR, "Erro inesperado: " + e.getMessage(), null);
            return false;
        }
    }

    /**
     * Atualiza o status de um item no banco de dados
     */
    private void updateItemStatus(Integer itemId, Integer newStatus, String logMessage, String dadosComplementares) {
        try {
            if (dadosComplementares != null && !dadosComplementares.trim().isEmpty()) {
                repository.updateStatusWithData(itemId, newStatus, LocalDateTime.now(), logMessage, dadosComplementares);
                log.debug("Item ID: {} atualizado com dados complementares: {}", itemId, dadosComplementares);
            } else {
                repository.updateStatus(itemId, newStatus, LocalDateTime.now(), logMessage);
            }

            log.debug("Status do item ID: {} atualizado para: {} - {}", itemId, getStatusDescription(newStatus), logMessage);
        } catch (Exception e) {
            log.error("Erro ao atualizar status do item ID: {} - {}", itemId, e.getMessage(), e);
        }
    }

    /**
     * Retorna o número de itens pendentes de processamento
     */
    public long getPendingCount() {
        try {
            return repository.countPendingProcessing();
        } catch (Exception e) {
            log.error("Erro ao contar itens pendentes: {}", e.getMessage());
            return 0;
        }
    }

    // Métodos auxiliares para logs mais legíveis
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