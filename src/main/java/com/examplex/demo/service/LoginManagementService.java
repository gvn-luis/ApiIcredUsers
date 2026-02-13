package com.examplex.demo.service;

import com.examplex.demo.exception.ExternalApiException;
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
import org.springframework.transaction.annotation.Propagation;
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
    private static final int STATUS_SUCCESS = -4107;
    private static final int STATUS_PENDING = -4109;

    // Management Type constants
    private static final int TYPE_UNBLOCK = -4105;
    private static final int TYPE_BLOCK = -4104;
    private static final int TYPE_CREATE = 3833;
    private static final int TYPE_RESET = 2268;
    private static final int TYPE_LINK_GROUP = 4480;
    private static final int TYPE_BANNED = 5050;

    private static final int LOG_MAX_LENGTH = 50;

    /**
     * Processa todos os itens pendentes
     * NÃO tem @Transactional para evitar rollback de toda a transação
     */
    public void processLoginManagement() {
        log.info("========================================");
        log.info("INICIANDO PROCESSAMENTO");
        log.info("========================================");

        List<LoginManagement> pendingItems = repository.findPendingProcessing();

        if (pendingItems.isEmpty()) {
            log.info("Nenhum item na fila");
            log.info("========================================");
            return;
        }

        log.info("Total na fila: {}", pendingItems.size());
        log.info("========================================");

        int successCount = 0;
        int pendingCount = 0;

        for (LoginManagement item : pendingItems) {
            try {
                boolean success = processItem(item);
                if (success) {
                    successCount++;
                } else {
                    pendingCount++;
                }
                Thread.sleep(500);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                log.error("Processamento interrompido");
                break;
            } catch (Exception e) {
                log.error("❌ ERRO CRÍTICO no item ID {}: {}", item.getId(), e.getMessage(), e);
                updateItemStatus(item.getId(), STATUS_PENDING, "Erro crítico", null, null);
                pendingCount++;
            }
        }

        log.info("========================================");
        log.info("PROCESSAMENTO FINALIZADO");
        log.info("✅ Sucessos: {} | ⏳ Pendências: {}", successCount, pendingCount);
        log.info("========================================");
    }

    /**
     * Processa um item individual
     */
    private boolean processItem(LoginManagement item) {
        log.info("📋 USUÁRIO: {} | Tipo: {} | ID: {}",
                item.getUserCode(),
                getManagementTypeDescription(item.getManagementType()),
                item.getId());

        switch (item.getManagementType()) {
            case TYPE_CREATE:
                return processCreateUser(item);
            case TYPE_RESET:
                return processResetPassword(item);
            case TYPE_BLOCK:
                return processBlockUser(item);
            case TYPE_UNBLOCK:
                return processUnblockUser(item);
            case TYPE_LINK_GROUP:
                return processLinkToGroup(item);
            default:
                log.warn("⚠️  Tipo desconhecido: {}", item.getManagementType());
                updateItemStatus(item.getId(), STATUS_PENDING, "Tipo desconhecido", null, null);
                return false;
        }
    }

    /**
     * TYPE -4105: UNBLOCK
     */
    private boolean processUnblockUser(LoginManagement item) {
        if (item.getExternalKey() == null || item.getExternalKey().trim().isEmpty()) {
            log.warn("⚠️  ExternalKey vazia");
            updateItemStatus(item.getId(), STATUS_PENDING, "ExternalKey vazia", null, null);
            return false;
        }

        log.info("🔓 Solicitando UNBLOCK...");

        ApiResponseDto unblockResponse = externalApiService.unblockUser(item.getExternalKey());

        // ✅ Caso 1: UNBLOCK funcionou
        if (unblockResponse.isSuccess()) {
            String newPassword = extractPassword(unblockResponse);
            String dadosComplementares = buildPasswordJson(newPassword);

            log.info("✅ UNBLOCK concluído - Senha: {}", maskPassword(newPassword));
            updateItemStatus(item.getId(), STATUS_SUCCESS, "Desbloqueio OK", dadosComplementares, null);
            return true;
        }

        // ⚠️ Caso 2: Usuário JÁ ESTÁ ATIVO → Fazer RESET
        if (unblockResponse.getMessage() != null &&
                unblockResponse.getMessage().toLowerCase().contains("já está ativo")) {

            log.warn("⚠️  Usuário já ativo. Executando RESET para gerar nova senha...");
            return executeReset(item, "RESET (já ativo)");
        }

        // ❌ Caso 3: Outro erro
        log.error("❌ Falha no UNBLOCK: {}", unblockResponse.getMessage());
        updateItemStatus(item.getId(), STATUS_PENDING,
                truncateLog(unblockResponse.getMessage()), null, null);
        return false;
    }

    /**
     * TYPE 2268: RESET
     */
    private boolean processResetPassword(LoginManagement item) {
        if (item.getExternalKey() == null || item.getExternalKey().trim().isEmpty()) {
            log.warn("⚠️  ExternalKey vazia");
            updateItemStatus(item.getId(), STATUS_PENDING, "ExternalKey vazia", null, null);
            return false;
        }

        log.info("🔑 Solicitando RESET DE SENHA...");
        return executeReset(item, "Reset OK");
    }

    /**
     * Executa RESET (Block + Unblock)
     */
    private boolean executeReset(LoginManagement item, String successMessage) {
        try {
            // Passo 1: Tentar BLOQUEAR
            log.info("   → Bloqueando usuário...");
            ApiResponseDto blockResponse = externalApiService.blockUser(item.getExternalKey());

            // ⚠️ Caso especial: Usuário não relacionado ao corban
            if (!blockResponse.isSuccess() && blockResponse.getMessage() != null) {
                String msg = blockResponse.getMessage().toLowerCase();

                if (msg.contains("user_not_related_to_partner") ||
                        msg.contains("não relacionado ao corban") ||
                        msg.contains("nao relacionado ao corban")) {

                    log.warn("⚠️  Usuário não vinculado ao corban. Executando apenas UNBLOCK...");
                    return executeOnlyUnblock(item, "UNBLOCK (vincular)");
                }
            }

            if (!blockResponse.isSuccess()) {
                log.error("❌ Falha ao bloquear: {}", blockResponse.getMessage());
                updateItemStatus(item.getId(), STATUS_PENDING,
                        "Erro block: " + truncateLog(blockResponse.getMessage()), null, null);
                return false;
            }

            log.info("   ✅ Bloqueado");
            Thread.sleep(500);

            // Passo 2: DESBLOQUEAR
            log.info("   → Desbloqueando usuário...");
            ApiResponseDto unblockResponse = externalApiService.unblockUser(item.getExternalKey());

            if (!unblockResponse.isSuccess()) {
                log.error("❌ Falha ao desbloquear: {}", unblockResponse.getMessage());
                updateItemStatus(item.getId(), STATUS_PENDING,
                        "Erro unblock: " + truncateLog(unblockResponse.getMessage()), null, null);
                return false;
            }

            String newPassword = extractPassword(unblockResponse);
            String dadosComplementares = buildPasswordJson(newPassword);

            log.info("   ✅ Desbloqueado - Senha: {}", maskPassword(newPassword));
            log.info("✅ RESET concluído");

            updateItemStatus(item.getId(), STATUS_SUCCESS, successMessage, dadosComplementares, null);
            return true;

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.error("❌ Processamento interrompido");
            updateItemStatus(item.getId(), STATUS_PENDING, "Interrompido", null, null);
            return false;
        } catch (Exception e) {
            log.error("❌ Erro durante RESET: {}", e.getMessage());
            updateItemStatus(item.getId(), STATUS_PENDING, "Erro no reset", null, null);
            return false;
        }
    }

    /**
     * Executa apenas UNBLOCK (sem BLOCK antes)
     */
    private boolean executeOnlyUnblock(LoginManagement item, String successMessage) {
        log.info("🔓 Executando UNBLOCK (vincular ao corban)...");

        ApiResponseDto unblockResponse = externalApiService.unblockUser(item.getExternalKey());

        if (!unblockResponse.isSuccess()) {
            log.error("❌ Falha ao ativar usuário: {}", unblockResponse.getMessage());
            updateItemStatus(item.getId(), STATUS_PENDING,
                    truncateLog(unblockResponse.getMessage()), null, null);
            return false;
        }

        String newPassword = extractPassword(unblockResponse);
        String dadosComplementares = buildPasswordJson(newPassword);

        log.info("✅ Usuário ativado e vinculado ao corban - Senha: {}", maskPassword(newPassword));
        updateItemStatus(item.getId(), STATUS_SUCCESS, successMessage, dadosComplementares, null);
        return true;
    }

    /**
     * TYPE -4104: BLOCK
     */
    private boolean processBlockUser(LoginManagement item) {
        if (item.getExternalKey() == null || item.getExternalKey().trim().isEmpty()) {
            log.warn("⚠️  ExternalKey vazia");
            updateItemStatus(item.getId(), STATUS_PENDING, "ExternalKey vazia", null, null);
            return false;
        }

        log.info("🔒 Solicitando BLOCK...");

        ApiResponseDto response = externalApiService.blockUser(item.getExternalKey());

        if (response.isSuccess()) {
            log.info("✅ BLOCK concluído");
            updateItemStatus(item.getId(), STATUS_SUCCESS, "Bloqueio OK", null, null);
            return true;
        } else {
            log.error("❌ Falha: {}", response.getMessage());
            updateItemStatus(item.getId(), STATUS_PENDING, truncateLog(response.getMessage()), null, null);
            return false;
        }
    }

    /**
     * TYPE 4480: LINK_GROUP
     * ✅ ATUALIZADO: Agora usa personCode ao invés de userUuid
     */
    private boolean processLinkToGroup(LoginManagement item) {
        log.info("🔗 VINCULAR USUÁRIO A GRUPO");

        // ✅ MUDANÇA: Agora precisa de userCode (CPF) ao invés de externalKey (UUID)
        if (item.getUserCode() == null || item.getUserCode().trim().isEmpty()) {
            log.warn("⚠️  UserCode (CPF) vazio");
            updateItemStatus(item.getId(), STATUS_PENDING, "UserCode vazio", null, null);
            return false;
        }

        DadosComplementaresDto dados = parseDadosComplementares(item.getDadosComplementares());
        if (dados == null || dados.getManagementGroupsUuid() == null) {
            log.warn("⚠️  UUID do grupo não informado");
            updateItemStatus(item.getId(), STATUS_PENDING, "UUID grupo vazio", null, null);
            return false;
        }

        String groupUuid = dados.getManagementGroupsUuid();
        log.info("   → PersonCode (CPF): {}", item.getUserCode());
        log.info("   → GroupUUID: {}", groupUuid);

        // ✅ MUDANÇA: Passa personCode (CPF) ao invés de userUuid
        ApiResponseDto response = externalApiService.addUserToGroup(groupUuid, item.getUserCode());

        if (response.isSuccess()) {
            log.info("✅ Usuário vinculado ao grupo com sucesso!");
            updateItemStatus(item.getId(), STATUS_SUCCESS, "Vinculado OK", null, null);
            return true;
        } else {
            log.error("❌ Falha ao vincular: {}", response.getMessage());
            updateItemStatus(item.getId(), STATUS_PENDING,
                    "Erro: " + truncateLog(response.getMessage()), null, null);
            return false;
        }
    }

    /**
     * TYPE 3833: CREATE
     */
    private boolean processCreateUser(LoginManagement item) {
        if (item.getUserCode() == null || item.getUserCode().trim().isEmpty()) {
            log.warn("⚠️  UserCode vazio");
            updateItemStatus(item.getId(), STATUS_PENDING, "UserCode vazio", null, null);
            return false;
        }

        log.info("👤 CRIAR USUÁRIO");

        try {
            // Tenta criar usuário
            ApiResponseDto createResponse = externalApiService.createUser(item.getUserCode());

            if (!createResponse.isSuccess()) {
                log.error("❌ Falha ao criar usuário: {}", createResponse.getMessage());
                updateItemStatus(item.getId(), STATUS_PENDING,
                        truncateLog(createResponse.getMessage()), null, null);
                return false;
            }

            String userUuid = (String) createResponse.getData();
            log.info("✅ Usuário criado - UUID: {}", userUuid);

            // Analisa dados complementares para grupo
            DadosComplementaresDto dados = parseDadosComplementares(item.getDadosComplementares());

            if (dados != null && dados.getManagementGroupsUuid() != null) {
                // Vincular a grupo existente
                linkUserToGroup(item, dados.getManagementGroupsUuid());
            } else if (dados != null && dados.getManagementGroupsNome() != null) {
                // Criar novo grupo e vincular
                createGroupAndLink(item, dados.getManagementGroupsNome());
            }

            // Block + Unblock para gerar senha
            executeBlockAndUnblock(item.getId(), userUuid);

            updateItemStatusWithExternalKey(item.getId(), STATUS_SUCCESS,
                    "Criado OK", null, userUuid);
            return true;

        } catch (ExternalApiException e) {
            // Usuário BANIDO
            if (e.isBannedUser()) {
                log.warn("🚫 Usuário BANIDO: {} - {}", item.getUserCode(), e.getMessage());
                updateItemAsBanned(item.getId(), e.getMessage());
                return true;
            }
            throw e;
        }
    }

    /**
     * Vincula usuário a grupo existente
     * ✅ ATUALIZADO: Agora usa personCode (CPF) ao invés de userUuid
     */
    private void linkUserToGroup(LoginManagement item, String groupUuid) {
        log.info("🔗 Vinculando ao grupo: {}", groupUuid);

        // ✅ MUDANÇA: Passa personCode (CPF) ao invés de userUuid
        ApiResponseDto response = externalApiService.addUserToGroup(groupUuid, item.getUserCode());

        if (response.isSuccess()) {
            log.info("✅ Vinculado ao grupo");
        } else {
            log.warn("⚠️  Falha ao vincular ao grupo: {}", response.getMessage());
        }
    }

    /**
     * Cria novo grupo e vincula usuário
     * ✅ ATUALIZADO: Agora usa personCode ao criar grupo e vincular
     */
    private void createGroupAndLink(LoginManagement item, String groupNome) {
        log.info("📁 Criando novo grupo: {}", groupNome);

        // ✅ MUDANÇA: Passa personCode (CPF) ao invés de partnerExternalKey
        ApiResponseDto groupResponse = externalApiService.createSellerGroup(
                groupNome,
                item.getUserCode()  // personCode (CPF)
        );

        if (groupResponse.isSuccess()) {
            String groupUuid = (String) groupResponse.getData();
            log.info("✅ Grupo criado - UUID: {}", groupUuid);

            // Salvar no banco
            saveGroupToDatabase(groupUuid, groupNome, item.getUserCode());

            // Vincular usuário
            linkUserToGroup(item, groupUuid);
        } else {
            log.warn("⚠️  Falha ao criar grupo: {}", groupResponse.getMessage());
        }
    }

    /**
     * Salva grupo no banco de dados
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    private void saveGroupToDatabase(String uuid, String nome, String partnerKey) {
        try {
            LoginManagementGroups group = new LoginManagementGroups();
            group.setUuid(uuid);
            group.setNome(nome);
            group.setPartnerExternalKey(partnerKey);
            groupsRepository.save(group);
            log.info("✅ Grupo salvo no banco");
        } catch (Exception e) {
            log.error("❌ Erro ao salvar grupo: {}", e.getMessage());
        }
    }

    /**
     * Executa Block + Unblock para gerar senha inicial
     */
    private void executeBlockAndUnblock(Integer itemId, String userUuid) {
        try {
            log.info("🔐 Executando Block + Unblock...");

            // Block
            externalApiService.blockUser(userUuid);
            Thread.sleep(500);

            // Unblock
            ApiResponseDto unblockResponse = externalApiService.unblockUser(userUuid);

            if (unblockResponse.isSuccess()) {
                String password = extractPassword(unblockResponse);
                log.info("✅ Senha gerada: {}", maskPassword(password));

                LoginManagement current = repository.findById(itemId).orElse(null);
                if (current != null) {
                    String updatedDados = addPasswordToDados(
                            current.getDadosComplementares(), password
                    );
                    updateItemStatus(itemId, STATUS_SUCCESS, "Criado e ativado", updatedDados, null);
                }
            }
        } catch (Exception e) {
            log.warn("⚠️  Erro em Block/Unblock (não crítico): {}", e.getMessage());
        }
    }

    /**
     * Marca item como usuário banido
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    private void updateItemAsBanned(Integer itemId, String message) {
        try {
            String dadosComplementares = "{\"banned\":true,\"message\":\"" + escapeJson(message) + "\"}";

            LoginManagement item = repository.findById(itemId).orElse(null);
            if (item == null) {
                log.error("❌ Item {} não encontrado para marcar como BANNED", itemId);
                return;
            }

            item.setManagementStatus(STATUS_SUCCESS);
            item.setManagementType(TYPE_BANNED);
            item.setLogAlteracaoRastro("Usuário banido");
            item.setDadosComplementares(dadosComplementares);
            item.setDataAlteracao(LocalDateTime.now());

            repository.save(item);

            log.info("✅ Item {} marcado como BANNED (Type=5050, Status=-4107)", itemId);

        } catch (Exception e) {
            log.error("❌ Erro ao atualizar item {} como BANNED: {}", itemId, e.getMessage());
        }
    }

    // ========== MÉTODOS AUXILIARES ==========

    private String extractPassword(ApiResponseDto response) {
        if (response.getData() != null && response.getData().toString().length() > 0) {
            return response.getData().toString();
        }
        return "Usuário ativo. Clicar em esqueci minha senha.";
    }

    private String buildPasswordJson(String password) {
        return "{\"newPassword\":\"" + escapeJson(password) + "\"}";
    }

    private String maskPassword(String password) {
        if (password == null || password.length() < 4) {
            return "[SENHA]";
        }
        return password.substring(0, 2) + "****" + password.substring(password.length() - 2);
    }

    private String addPasswordToDados(String currentDados, String newPassword) {
        if (currentDados == null || currentDados.trim().isEmpty()) {
            return buildPasswordJson(newPassword);
        }

        String dados = currentDados.trim();
        if (dados.endsWith("}")) {
            String base = dados.substring(0, dados.length() - 1);
            if (base.trim().equals("{")) {
                return buildPasswordJson(newPassword);
            }
            return base + ",\"newPassword\":\"" + escapeJson(newPassword) + "\"}";
        }

        return buildPasswordJson(newPassword);
    }

    private DadosComplementaresDto parseDadosComplementares(String json) {
        if (json == null || json.trim().isEmpty()) {
            return null;
        }

        try {
            return objectMapper.readValue(json, DadosComplementaresDto.class);
        } catch (Exception e) {
            log.error("❌ Erro ao parsear dadosComplementares: {}", e.getMessage());
            return null;
        }
    }

    private String escapeJson(String text) {
        if (text == null) return "";
        return text.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t");
    }

    private String truncateLog(String message) {
        if (message == null) return "";
        if (message.length() <= LOG_MAX_LENGTH) return message;
        return message.substring(0, LOG_MAX_LENGTH - 3) + "...";
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    private void updateItemStatus(Integer itemId, Integer newStatus, String logMessage,
                                  String dadosComplementares, String externalKey) {
        try {
            String truncatedLog = truncateLog(logMessage);

            if (externalKey != null && !externalKey.trim().isEmpty()) {
                repository.updateStatusWithDataAndExternalKey(itemId, newStatus,
                        LocalDateTime.now(), truncatedLog, dadosComplementares, externalKey);
            } else if (dadosComplementares != null && !dadosComplementares.trim().isEmpty()) {
                repository.updateStatusWithData(itemId, newStatus,
                        LocalDateTime.now(), truncatedLog, dadosComplementares);
            } else {
                repository.updateStatus(itemId, newStatus, LocalDateTime.now(), truncatedLog);
            }
        } catch (Exception e) {
            log.error("❌ Erro ao atualizar status do item {}: {}", itemId, e.getMessage());
        }
    }

    private void updateItemStatusWithExternalKey(Integer itemId, Integer newStatus,
                                                 String logMessage, String dadosComplementares,
                                                 String externalKey) {
        updateItemStatus(itemId, newStatus, logMessage, dadosComplementares, externalKey);
    }

    public long getPendingCount() {
        try {
            return repository.countPendingProcessing();
        } catch (Exception e) {
            log.error("Erro ao contar itens pendentes: {}", e.getMessage());
            return 0;
        }
    }

    private String getManagementTypeDescription(Integer type) {
        switch (type) {
            case TYPE_BLOCK: return "Block(-4104)";
            case TYPE_UNBLOCK: return "Unblock(-4105)";
            case TYPE_CREATE: return "Create(3833)";
            case TYPE_RESET: return "Reset(2268)";
            case TYPE_LINK_GROUP: return "VincularGrupo(4480)";
            case TYPE_BANNED: return "Banido(5050)";
            default: return "Desconhecido(" + type + ")";
        }
    }
}