package com.examplex.demo.service;

import com.examplex.demo.model.LoginManagement;
import com.examplex.demo.model.LoginManagementGroups;
import com.examplex.demo.model.dto.ApiResponseDto;
import com.examplex.demo.model.dto.DadosComplementaresDto;
import com.examplex.demo.repository.LoginManagementRepository;
import com.examplex.demo.repository.LoginManagementGroupsRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class LoginManagementService {

    private final LoginManagementRepository repository;
    private final LoginManagementGroupsRepository groupsRepository;
    private final ExternalApiService externalApiService;
    private final ObjectMapper objectMapper;

    @Value("${external-api.partner-uuid}")
    private String partnerUuid;

    // Status constants
    private static final int STATUS_ERROR = -4108;
    private static final int STATUS_SUCCESS = -4107;
    private static final int STATUS_QUEUE = -4106;

    // Management Type constants
    private static final int TYPE_UNBLOCK = -4105;
    private static final int TYPE_BLOCK = -4104;
    private static final int TYPE_CREATE = 3833;

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
            log.info("Processando item ID: {} | Tipo: {} | UserCode: {}",
                    item.getId(), getManagementTypeDescription(item.getManagementType()), item.getUserCode());

            // ========== CREATE ==========
            if (item.getManagementType() == TYPE_CREATE) {
                return processCreateUser(item);
            }
            // ========== BLOCK ==========
            else if (item.getManagementType() == TYPE_BLOCK) {
                return processBlockUser(item);
            }
            // ========== UNBLOCK ==========
            else if (item.getManagementType() == TYPE_UNBLOCK) {
                return processUnblockUser(item);
            }
            // ========== TIPO DESCONHECIDO ==========
            else {
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
     * Processa criação de usuário (com análise de grupo)
     */
    private boolean processCreateUser(LoginManagement item) {
        // Validação do UserCode
        if (item.getUserCode() == null || item.getUserCode().trim().isEmpty()) {
            log.warn("UserCode vazio para item ID: {}", item.getId());
            updateItemStatus(item.getId(), STATUS_ERROR, "UserCode vazio", null);
            return false;
        }

        // Parse do JSON de dadosComplementares
        DadosComplementaresDto dados = parseDadosComplementares(item.getDadosComplementares());

        if (dados == null) {
            log.warn("Item ID: {} - dadosComplementares inválido ou vazio", item.getId());
            // Se não tem dados complementares, cria usuário sem grupo
            return createUserOnly(item);
        }

        String groupUuid = dados.getManagementGroupsUuid();
        String groupNome = dados.getManagementGroupsNome();

        // Cenário 1: Tem UUID do grupo - vincular a grupo existente
        if (groupUuid != null && !groupUuid.trim().isEmpty()) {
            log.info("Item ID: {} - Criar usuário e vincular ao grupo UUID: {}", item.getId(), groupUuid);
            return createUserAndLinkToExistingGroup(item, groupUuid, groupNome);
        }

        // Cenário 2: Tem nome mas não tem UUID - criar novo grupo
        if (groupNome != null && !groupNome.trim().isEmpty()) {
            log.info("Item ID: {} - Criar usuário e criar novo grupo: {}", item.getId(), groupNome);
            return createUserAndCreateNewGroup(item, groupNome);
        }

        // Cenário 3: Não tem nem nome nem UUID - apenas criar usuário
        log.info("Item ID: {} - Criar usuário sem grupo", item.getId());
        return createUserOnly(item);
    }

    /**
     * Cenário 1: Criar usuário e vincular a grupo existente
     */
    private boolean createUserAndLinkToExistingGroup(LoginManagement item, String groupUuid, String groupNome) {
        // Passo 1: Criar usuário
        ApiResponseDto createResponse = externalApiService.createUser(item.getUserCode());

        if (!createResponse.isSuccess()) {
            String errorMsg = extractErrorCode(createResponse.getMessage());
            updateItemStatus(item.getId(), STATUS_ERROR, errorMsg, null);
            log.error("Erro ao criar usuário do item ID: {} - {}", item.getId(), createResponse.getMessage());
            return false;
        }

        String userUuid = (String) createResponse.getData();
        log.info("Item ID: {} - Usuário criado com UUID: {}", item.getId(), userUuid);

        // Passo 2: Vincular ao grupo existente
        ApiResponseDto groupResponse = externalApiService.addUserToGroup(groupUuid, userUuid);

        if (!groupResponse.isSuccess()) {
            log.warn("Item ID: {} - Usuário criado mas falhou ao vincular ao grupo: {}",
                    item.getId(), groupResponse.getMessage());
            String dados = buildDadosJson(userUuid, null, null, "Erro ao vincular");
            updateItemStatus(item.getId(), STATUS_SUCCESS, "Criado sem grupo", dados);
            return true;
        }

        log.info("Item ID: {} - Usuário vinculado ao grupo com sucesso", item.getId());
        String dados = buildDadosJson(userUuid, groupUuid, groupNome, null);
        updateItemStatus(item.getId(), STATUS_SUCCESS, "Criado com grupo", dados);
        return true;
    }

    /**
     * Cenário 2: Criar usuário e criar novo grupo
     */
    private boolean createUserAndCreateNewGroup(LoginManagement item, String groupNome) {
        // Passo 1: Criar usuário
        ApiResponseDto createResponse = externalApiService.createUser(item.getUserCode());

        if (!createResponse.isSuccess()) {
            String errorMsg = extractErrorCode(createResponse.getMessage());
            updateItemStatus(item.getId(), STATUS_ERROR, errorMsg, null);
            log.error("Erro ao criar usuário do item ID: {} - {}", item.getId(), createResponse.getMessage());
            return false;
        }

        String userUuid = (String) createResponse.getData();
        log.info("Item ID: {} - Usuário criado com UUID: {}", item.getId(), userUuid);

        // Passo 2: Criar novo grupo
        String partnerExternalKey = item.getUserCode(); // Usa o CPF como partnerExternalKey
        ApiResponseDto createGroupResponse = externalApiService.createSellerGroup(groupNome, partnerExternalKey);

        if (!createGroupResponse.isSuccess()) {
            log.warn("Item ID: {} - Usuário criado mas falhou ao criar grupo: {}",
                    item.getId(), createGroupResponse.getMessage());
            String dados = buildDadosJson(userUuid, null, null, "Erro ao criar grupo");
            updateItemStatus(item.getId(), STATUS_SUCCESS, "Criado sem grupo", dados);
            return true;
        }

        String newGroupUuid = (String) createGroupResponse.getData();
        log.info("Item ID: {} - Grupo criado com UUID: {}", item.getId(), newGroupUuid);

        // Passo 3: Salvar grupo no banco
        try {
            LoginManagementGroups newGroup = new LoginManagementGroups();
            newGroup.setUuid(newGroupUuid);
            newGroup.setNome(groupNome);
            newGroup.setPartnerExternalKey(partnerExternalKey);
            groupsRepository.save(newGroup);
            log.info("Item ID: {} - Grupo salvo no banco com ID: {}", item.getId(), newGroup.getId());
        } catch (Exception e) {
            log.error("Item ID: {} - Erro ao salvar grupo no banco: {}", item.getId(), e.getMessage());
        }

        // Passo 4: Vincular usuário ao grupo recém-criado
        ApiResponseDto linkResponse = externalApiService.addUserToGroup(newGroupUuid, userUuid);

        if (!linkResponse.isSuccess()) {
            log.warn("Item ID: {} - Grupo criado mas falhou ao vincular usuário: {}",
                    item.getId(), linkResponse.getMessage());
            String dados = buildDadosJson(userUuid, newGroupUuid, groupNome, "Grupo criado mas não vinculado");
            updateItemStatus(item.getId(), STATUS_SUCCESS, "Criado sem vínculo", dados);
            return true;
        }

        log.info("Item ID: {} - Usuário vinculado ao novo grupo com sucesso", item.getId());
        String dados = buildDadosJson(userUuid, newGroupUuid, groupNome, null);
        updateItemStatus(item.getId(), STATUS_SUCCESS, "Criado com novo grupo", dados);
        return true;
    }

    /**
     * Cenário 3: Criar apenas usuário (sem grupo)
     */
    private boolean createUserOnly(LoginManagement item) {
        ApiResponseDto createResponse = externalApiService.createUser(item.getUserCode());

        if (!createResponse.isSuccess()) {
            String errorMsg = extractErrorCode(createResponse.getMessage());
            updateItemStatus(item.getId(), STATUS_ERROR, errorMsg, null);
            log.error("Erro ao criar usuário do item ID: {} - {}", item.getId(), createResponse.getMessage());
            return false;
        }

        String userUuid = (String) createResponse.getData();
        log.info("Item ID: {} - Usuário criado com sucesso (sem grupo)", item.getId());

        String dados = buildDadosJson(userUuid, null, null, null);
        updateItemStatus(item.getId(), STATUS_SUCCESS, "Criado OK", dados);
        return true;
    }

    /**
     * Parse do JSON de dadosComplementares
     */
    private DadosComplementaresDto parseDadosComplementares(String json) {
        if (json == null || json.trim().isEmpty()) {
            return null;
        }

        try {
            return objectMapper.readValue(json, DadosComplementaresDto.class);
        } catch (Exception e) {
            log.warn("Erro ao parsear dadosComplementares: {}", e.getMessage());
            return null;
        }
    }

    /**
     * Constrói JSON de resultado para salvar em dadosComplementares
     */
    private String buildDadosJson(String userUuid, String groupUuid, String groupNome, String warning) {
        StringBuilder json = new StringBuilder("{");
        json.append("\"userUuid\":\"").append(userUuid).append("\"");

        if (groupUuid != null) {
            json.append(",\"groupUuid\":\"").append(groupUuid).append("\"");
        }

        if (groupNome != null) {
            json.append(",\"groupNome\":\"").append(groupNome).append("\"");
        }

        if (warning != null) {
            json.append(",\"warning\":\"").append(warning).append("\"");
        }

        json.append("}");
        return json.toString();
    }

    /**
     * Processa bloqueio de usuário
     */
    private boolean processBlockUser(LoginManagement item) {
        if (item.getExternalKey() == null || item.getExternalKey().trim().isEmpty()) {
            log.warn("ExternalKey vazia para item ID: {}", item.getId());
            updateItemStatus(item.getId(), STATUS_ERROR, "ExternalKey vazia", null);
            return false;
        }

        ApiResponseDto apiResponse = externalApiService.blockUser(item.getExternalKey());

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
    }

    /**
     * Processa desbloqueio de usuário
     */
    private boolean processUnblockUser(LoginManagement item) {
        if (item.getExternalKey() == null || item.getExternalKey().trim().isEmpty()) {
            log.warn("ExternalKey vazia para item ID: {}", item.getId());
            updateItemStatus(item.getId(), STATUS_ERROR, "ExternalKey vazia", null);
            return false;
        }

        ApiResponseDto apiResponse = externalApiService.unblockUser(item.getExternalKey());

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
            if (message.contains("ALREADY_EXISTS")) {
                return "Já existe";
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
            case TYPE_CREATE: return "Create(3833)";
            default: return "Desconhecido(" + type + ")";
        }
    }
}