package com.examplex.demo.service;

import com.examplex.demo.exception.ExternalApiException;
import com.examplex.demo.model.LoginManagement;
import com.examplex.demo.model.LoginManagementGroups;
import com.examplex.demo.model.dto.ApiResponseDto;
import com.examplex.demo.model.dto.DadosComplementaresDto;
import com.examplex.demo.repository.LoginManagementRepository;
import com.examplex.demo.repository.LoginManagementGroupsRepository;
import com.examplex.demo.repository.PartnerKeyRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
@RequiredArgsConstructor
@Slf4j
public class LoginManagementService {

    private final LoginManagementRepository repository;
    private final LoginManagementGroupsRepository groupsRepository;
    private final PartnerKeyRepository partnerKeyRepository;
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

    private boolean processUnblockUser(LoginManagement item) {
        if (item.getExternalKey() == null || item.getExternalKey().trim().isEmpty()) {
            log.warn("⚠️  ExternalKey vazia");
            updateItemStatus(item.getId(), STATUS_PENDING, "ExternalKey vazia", null, null);
            return false;
        }

        log.info("🔓 Solicitando UNBLOCK...");
        ApiResponseDto unblockResponse = externalApiService.unblockUser(item.getExternalKey());

        if (unblockResponse.isSuccess()) {
            String newPassword = extractPassword(unblockResponse);
            String dadosComplementares = buildPasswordJson(newPassword);
            log.info("✅ UNBLOCK concluído - Senha: {}", maskPassword(newPassword));
            updateItemStatus(item.getId(), STATUS_SUCCESS, "Desbloqueio OK", dadosComplementares, null);
            return true;
        }

        if (unblockResponse.getMessage() != null &&
                unblockResponse.getMessage().toLowerCase().contains("já está ativo")) {
            log.warn("⚠️  Usuário já ativo. Executando RESET para gerar nova senha...");
            return executeReset(item, "RESET (já ativo)");
        }

        log.error("❌ Falha no UNBLOCK: {}", unblockResponse.getMessage());
        updateItemStatus(item.getId(), STATUS_PENDING,
                truncateLog(unblockResponse.getMessage()), null, null);
        return false;
    }

    private boolean processResetPassword(LoginManagement item) {
        if (item.getExternalKey() == null || item.getExternalKey().trim().isEmpty()) {
            log.warn("⚠️  ExternalKey vazia");
            updateItemStatus(item.getId(), STATUS_PENDING, "ExternalKey vazia", null, null);
            return false;
        }

        log.info("🔑 Solicitando RESET DE SENHA...");
        return executeReset(item, "Reset OK");
    }

    private boolean executeReset(LoginManagement item, String successMessage) {
        try {
            log.info("   → Bloqueando usuário...");
            ApiResponseDto blockResponse = externalApiService.blockUser(item.getExternalKey());

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
     *
     * NOVO: Se UUID vazio mas nome preenchido, CRIA o grupo primeiro
     */
    private boolean processLinkToGroup(LoginManagement item) {
        log.info("🔗 VINCULAR USUÁRIO A GRUPO");

        if (item.getUserCode() == null || item.getUserCode().trim().isEmpty()) {
            log.warn("⚠️  UserCode (CPF) vazio");
            updateItemStatus(item.getId(), STATUS_PENDING, "UserCode vazio", null, null);
            return false;
        }

        DadosComplementaresDto dados = parseDadosComplementares(item.getDadosComplementares());
        if (dados == null) {
            log.warn("⚠️  dadosComplementares inválido");
            updateItemStatus(item.getId(), STATUS_PENDING, "Dados inválidos", null, null);
            return false;
        }

        String groupUuid = dados.getManagementGroupsUuid();
        String groupNome = dados.getManagementGroupsNome();

        // Caso 1: UUID preenchido → Vincular diretamente
        if (groupUuid != null && !groupUuid.trim().isEmpty()) {
            return linkToExistingGroup(item, groupUuid);
        }

        // Caso 2: UUID vazio mas Nome preenchido → Criar grupo primeiro
        if (groupNome != null && !groupNome.trim().isEmpty()) {
            return createGroupAndLinkForType4480(item, groupNome);
        }

        // Caso 3: Ambos vazios → Erro
        log.warn("⚠️  UUID e Nome do grupo vazios");
        updateItemStatus(item.getId(), STATUS_PENDING, "UUID e Nome vazios", null, null);
        return false;
    }

    /**
     * Vincula a grupo existente (UUID preenchido)
     */
    private boolean linkToExistingGroup(LoginManagement item, String groupUuid) {
        log.info("   → PersonCode (CPF): {}", item.getUserCode());
        log.info("   → GroupUUID: {}", groupUuid);

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
     * Cria grupo e vincula (Nome preenchido, UUID vazio) - TYPE 4480
     */
    private boolean createGroupAndLinkForType4480(LoginManagement item, String groupNome) {
        log.info("📁 UUID vazio → Criando novo grupo: {}", groupNome);

        // Extrair pessoaId do nome (ex: "GVN | 46711 | Paula" → 46711)
        Integer pessoaId = extractPessoaIdFromGroupName(groupNome);
        if (pessoaId == null) {
            log.warn("⚠️  Não foi possível extrair pessoaId do nome: {}", groupNome);
            updateItemStatus(item.getId(), STATUS_PENDING, "PessoaId não encontrado", null, null);
            return false;
        }

        log.info("   → PessoaId extraído: {}", pessoaId);

        // Buscar partnerExternalKey no banco
        String partnerExternalKey = partnerKeyRepository.findPartnerExternalKeyByPessoaId(pessoaId);
        if (partnerExternalKey == null || partnerExternalKey.trim().isEmpty()) {
            log.warn("⚠️  PartnerExternalKey não encontrado para pessoaId: {}", pessoaId);
            updateItemStatus(item.getId(), STATUS_PENDING, "PartnerKey não encontrado", null, null);
            return false;
        }

        log.info("   → PartnerExternalKey: {}", partnerExternalKey);

        // Criar grupo
        // Label = pessoaId para evitar "Tamanho do label inválido"
        String label = String.valueOf(pessoaId);
        ApiResponseDto groupResponse = externalApiService.createSellerGroup(groupNome, label, partnerExternalKey);

        if (!groupResponse.isSuccess()) {
            log.error("❌ Falha ao criar grupo: {}", groupResponse.getMessage());
            updateItemStatus(item.getId(), STATUS_PENDING,
                    "Erro ao criar grupo: " + truncateLog(groupResponse.getMessage()), null, null);
            return false;
        }

        String newGroupUuid = (String) groupResponse.getData();
        log.info("✅ Grupo criado - UUID: {}", newGroupUuid);

        // Salvar grupo no banco
        saveGroupToDatabase(newGroupUuid, groupNome, partnerExternalKey);

        // Vincular usuário ao grupo
        ApiResponseDto linkResponse = externalApiService.addUserToGroup(newGroupUuid, item.getUserCode());

        if (!linkResponse.isSuccess()) {
            log.warn("⚠️  Grupo criado mas falha ao vincular: {}", linkResponse.getMessage());
            updateItemStatus(item.getId(), STATUS_PENDING,
                    "Grupo criado, erro vincular", null, null);
            return false;
        }

        log.info("✅ Usuário vinculado ao novo grupo com sucesso!");
        updateItemStatus(item.getId(), STATUS_SUCCESS, "Grupo criado e vinculado", null, null);
        return true;
    }

    /**
     * Extrai pessoaId do nome do grupo
     * Formato esperado: "GVN | 46711 | Paula" ou "GVN|46711|Paula"
     * Retorna: 46711
     */
    private Integer extractPessoaIdFromGroupName(String groupNome) {
        try {
            // Regex para extrair número no meio (formato: XXX | 12345 | XXX)
            Pattern pattern = Pattern.compile("\\|\\s*(\\d+)\\s*\\|");
            Matcher matcher = pattern.matcher(groupNome);

            if (matcher.find()) {
                String numberStr = matcher.group(1);
                return Integer.parseInt(numberStr);
            }

            log.warn("Formato do nome do grupo não reconhecido: {}", groupNome);
            return null;

        } catch (Exception e) {
            log.error("Erro ao extrair pessoaId de '{}': {}", groupNome, e.getMessage());
            return null;
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
            ApiResponseDto createResponse = externalApiService.createUser(item.getUserCode());

            if (!createResponse.isSuccess()) {
                log.error("❌ Falha ao criar usuário: {}", createResponse.getMessage());
                updateItemStatus(item.getId(), STATUS_PENDING,
                        truncateLog(createResponse.getMessage()), null, null);
                return false;
            }

            String userUuid = (String) createResponse.getData();
            log.info("✅ Usuário criado - UUID: {}", userUuid);

            DadosComplementaresDto dados = parseDadosComplementares(item.getDadosComplementares());

            if (dados != null &&
                    dados.getManagementGroupsUuid() != null &&
                    !dados.getManagementGroupsUuid().trim().isEmpty()) {
                linkUserToGroup(item, dados.getManagementGroupsUuid());
            } else if (dados != null &&
                    dados.getManagementGroupsNome() != null &&
                    !dados.getManagementGroupsNome().trim().isEmpty()) {
                createGroupAndLink(item, dados.getManagementGroupsNome());
            }

            executeBlockAndUnblock(item.getId(), userUuid);

            updateItemStatusWithExternalKey(item.getId(), STATUS_SUCCESS,
                    "Criado OK", null, userUuid);
            return true;

        } catch (ExternalApiException e) {
            if (e.isBannedUser()) {
                log.warn("🚫 Usuário BANIDO: {} - {}", item.getUserCode(), e.getMessage());
                updateItemAsBanned(item.getId(), e.getMessage());
                return true;
            }
            throw e;
        }
    }

    private void linkUserToGroup(LoginManagement item, String groupUuid) {
        log.info("🔗 Vinculando ao grupo: {}", groupUuid);

        ApiResponseDto response = externalApiService.addUserToGroup(groupUuid, item.getUserCode());

        if (response.isSuccess()) {
            log.info("✅ Vinculado ao grupo");
        } else {
            log.warn("⚠️  Falha ao vincular ao grupo: {}", response.getMessage());
        }
    }

    private void createGroupAndLink(LoginManagement item, String groupNome) {
        log.info("📁 Criando novo grupo: {}", groupNome);

        // Extrair pessoaId
        Integer pessoaId = extractPessoaIdFromGroupName(groupNome);
        String partnerExternalKey;

        if (pessoaId != null) {
            partnerExternalKey = partnerKeyRepository.findPartnerExternalKeyByPessoaId(pessoaId);
            if (partnerExternalKey == null) {
                log.warn("⚠️  PartnerKey não encontrado para pessoaId {}, usando userCode", pessoaId);
                partnerExternalKey = item.getUserCode();
            }
        } else {
            log.warn("⚠️  PessoaId não extraído, usando userCode como partnerExternalKey");
            partnerExternalKey = item.getUserCode();
        }

        // Label = pessoaId (se extraído) ou "GRP" + timestamp
        String label = (pessoaId != null) ? String.valueOf(pessoaId) : "GRP" + System.currentTimeMillis();

        ApiResponseDto groupResponse = externalApiService.createSellerGroup(groupNome, label, partnerExternalKey);

        if (groupResponse.isSuccess()) {
            String groupUuid = (String) groupResponse.getData();
            log.info("✅ Grupo criado - UUID: {}", groupUuid);

            saveGroupToDatabase(groupUuid, groupNome, partnerExternalKey);
            linkUserToGroup(item, groupUuid);
        } else {
            log.warn("⚠️  Falha ao criar grupo: {}", groupResponse.getMessage());
        }
    }

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

    private void executeBlockAndUnblock(Integer itemId, String userUuid) {
        try {
            log.info("🔐 Executando Block + Unblock...");

            externalApiService.blockUser(userUuid);
            Thread.sleep(500);

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