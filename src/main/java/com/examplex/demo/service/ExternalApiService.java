package com.examplex.demo.service;

import com.examplex.demo.exception.ExternalApiException;
import com.examplex.demo.model.dto.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.util.Map;

/**
 * Service responsável por comunicação com a API externa iCred
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ExternalApiService {

    private final RestTemplate restTemplate;
    private final AuthTokenService authTokenService;
    private final ObjectMapper objectMapper;

    @Value("${external-api.base-url}")
    private String baseUrl;

    @Value("${external-api.partner-uuid}")
    private String partnerUuid;

    @Value("${external-api.user-profile-id:5}")
    private Integer userProfileId;

    /**
     * Cria headers com autenticação Bearer
     */
    private HttpHeaders createAuthHeaders() {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(authTokenService.getValidToken());
        return headers;
    }

    /**
     * Cria um novo grupo de vendedores
     * ATUALIZADO: Agora envia personCode no body
     */
    public ApiResponseDto createSellerGroup(String name, String personCode) {
        String endpoint = "/partner-management/v1/seller-groups";

        try {
            // ✅ MUDANÇA: Agora usa personCode ao invés de partnerExternalKey
            ApiCreateGroupRequestDto request = new ApiCreateGroupRequestDto(
                    name,      // name
                    name,      // label
                    personCode // personCode (era partnerExternalKey)
            );

            log.info("Criando grupo: {} (personCode: {})", name, personCode);

            ResponseEntity<Map> response = executePost(endpoint, request, Map.class);
            String groupUuid = extractUuid(response);

            log.info("Grupo criado com sucesso. UUID: {}", groupUuid);
            return ApiResponseDto.success("Grupo criado com sucesso", groupUuid);

        } catch (ExternalApiException e) {
            log.error("Erro ao criar grupo {}: {}", name, e.getMessage());
            return ApiResponseDto.error(e.getMessage());
        }
    }

    /**
     * Cria um novo usuário na API externa
     * @throws ExternalApiException se usuário estiver banido (CORE__PREVENT_FOUND)
     */
    public ApiResponseDto createUser(String personCode) {
        String endpoint = "/partner-management/v1/users";

        try {
            ApiCreateUserRequestDto request = new ApiCreateUserRequestDto(
                    personCode,
                    userProfileId,
                    partnerUuid
            );

            log.info("Criando usuário com personCode: {}", personCode);

            ResponseEntity<Map> response = executePost(endpoint, request, Map.class);
            String userUuid = extractUuid(response);

            log.info("Usuário criado com sucesso. UUID: {}", userUuid);
            return ApiResponseDto.success("Usuário criado com sucesso", userUuid);

        } catch (ExternalApiException e) {
            // Se for usuário banido, propaga a exceção para tratamento especial
            if (e.isBannedUser()) {
                log.warn("Usuário {} está banido: {}", personCode, e.getMessage());
                throw e;
            }
            log.error("Erro ao criar usuário {}: {}", personCode, e.getMessage());
            return ApiResponseDto.error(e.getMessage());
        }
    }

    /**
     * Adiciona um usuário (seller) a um grupo
     * ATUALIZADO: Agora usa /sellers e envia personCode no body
     *
     * @param groupUuid UUID do grupo
     * @param personCode CPF do usuário
     */
    public ApiResponseDto addUserToGroup(String groupUuid, String personCode) {
        // ✅ MUDANÇA: Rota mudou de /users/{userUuid} para /sellers
        String endpoint = String.format("/partner-management/v1/seller-groups/%s/sellers", groupUuid);

        try {
            // ✅ MUDANÇA: Agora envia personCode no body
            AddSellerToGroupRequest request = new AddSellerToGroupRequest(personCode);

            log.info("Adicionando seller (personCode: {}) ao grupo {}", personCode, groupUuid);

            ResponseEntity<Object> response = executePost(endpoint, request, Object.class);

            log.info("Seller {} adicionado ao grupo {} com sucesso", personCode, groupUuid);
            return ApiResponseDto.success("Seller adicionado ao grupo com sucesso", response.getBody());

        } catch (ExternalApiException e) {
            log.error("Erro ao adicionar seller ao grupo: {}", e.getMessage());
            return ApiResponseDto.error(e.getMessage());
        }
    }

    /**
     * Bloqueia um usuário
     */
    public ApiResponseDto blockUser(String userExternalKey) {
        String endpoint = String.format("/partner-management/v1/users/%s/block", userExternalKey);

        try {
            ApiRequestDto request = new ApiRequestDto(partnerUuid, "iCred block");

            log.info("Bloqueando usuário: {}", userExternalKey);

            ResponseEntity<Object> response = executePost(endpoint, request, Object.class);

            log.info("Usuário {} bloqueado com sucesso", userExternalKey);
            return ApiResponseDto.success("Usuário bloqueado com sucesso", response.getBody());

        } catch (ExternalApiException e) {
            log.error("Erro ao bloquear usuário {}: {}", userExternalKey, e.getMessage());
            return ApiResponseDto.error(e.getMessage());
        }
    }

    /**
     * Desbloqueia um usuário e retorna nova senha (se gerada)
     */
    public ApiResponseDto unblockUser(String userExternalKey) {
        String endpoint = String.format("/partner-management/v1/users/%s/unblock", userExternalKey);

        try {
            ApiRequestDto request = new ApiRequestDto(partnerUuid, "iCred unblock");

            log.info("Desbloqueando usuário: {}", userExternalKey);

            ResponseEntity<Map> response = executePost(endpoint, request, Map.class);
            String password = extractPassword(response);

            log.info("Usuário {} desbloqueado com sucesso", userExternalKey);
            return ApiResponseDto.success("Usuário desbloqueado com sucesso", password);

        } catch (ExternalApiException e) {
            log.error("Erro ao desbloquear usuário {}: {}", userExternalKey, e.getMessage());
            return ApiResponseDto.error(e.getMessage());
        }
    }

    /**
     * Lista vendedores de um grupo
     */
    public ApiResponseDto listGroupSellers(String groupUuid) {
        String endpoint = String.format("/partner-management/v1/seller-groups/%s/sellers", groupUuid);

        try {
            log.info("Listando vendedores do grupo: {}", groupUuid);

            ResponseEntity<Object> response = executeGet(endpoint, Object.class);

            log.info("Vendedores do grupo {} listados com sucesso", groupUuid);
            return ApiResponseDto.success("Vendedores listados com sucesso", response.getBody());

        } catch (ExternalApiException e) {
            log.error("Erro ao listar vendedores do grupo {}: {}", groupUuid, e.getMessage());
            return ApiResponseDto.error(e.getMessage());
        }
    }

    /**
     * Executa uma requisição POST
     */
    private <T, R> ResponseEntity<R> executePost(String endpoint, T body, Class<R> responseType) {
        return execute(endpoint, HttpMethod.POST, body, responseType);
    }

    /**
     * Executa uma requisição GET
     */
    private <R> ResponseEntity<R> executeGet(String endpoint, Class<R> responseType) {
        return execute(endpoint, HttpMethod.GET, null, responseType);
    }

    /**
     * Método genérico para executar requisições HTTP
     */
    private <T, R> ResponseEntity<R> execute(String endpoint, HttpMethod method, T body, Class<R> responseType) {
        String url = baseUrl + endpoint;

        try {
            HttpHeaders headers = createAuthHeaders();
            HttpEntity<T> entity = new HttpEntity<>(body, headers);

            return restTemplate.exchange(url, method, entity, responseType);

        } catch (HttpClientErrorException e) {
            handleHttpError(e);
            throw new ExternalApiException("Erro na chamada da API", e);
        } catch (RestClientException e) {
            log.error("Erro de comunicação com a API: {}", e.getMessage());
            invalidateTokenIfUnauthorized(e);
            throw new ExternalApiException("Erro de comunicação com a API: " + e.getMessage(), e);
        }
    }

    /**
     * Trata erros HTTP e extrai informações do corpo da resposta
     */
    private void handleHttpError(HttpClientErrorException e) {
        try {
            Map<String, Object> errorBody = objectMapper.readValue(
                    e.getResponseBodyAsString(),
                    Map.class
            );

            Integer httpStatus = (Integer) errorBody.get("httpStatus");
            Object errorsObj = errorBody.get("errors");

            if (errorsObj instanceof java.util.List) {
                java.util.List<Map<String, Object>> errors = (java.util.List<Map<String, Object>>) errorsObj;
                if (!errors.isEmpty()) {
                    Map<String, Object> firstError = errors.get(0);
                    String errorCode = (String) firstError.get("code");
                    String userMessage = (String) firstError.get("userMessage");

                    throw new ExternalApiException(userMessage, httpStatus, errorCode);
                }
            }

            throw new ExternalApiException("Erro HTTP " + e.getStatusCode(), e);

        } catch (Exception ex) {
            if (ex instanceof ExternalApiException) {
                throw (ExternalApiException) ex;
            }
            throw new ExternalApiException("Erro HTTP " + e.getStatusCode() + ": " + e.getMessage(), e);
        }
    }

    /**
     * Invalida token se erro for de autenticação
     */
    private void invalidateTokenIfUnauthorized(RestClientException e) {
        String message = e.getMessage();
        if (message != null && (message.contains("401") || message.contains("403"))) {
            log.warn("Token inválido detectado, invalidando para renovação");
            authTokenService.invalidateToken();
        }
    }

    /**
     * Extrai UUID da resposta
     */
    private String extractUuid(ResponseEntity<Map> response) {
        if (response.getBody() == null) {
            throw new ExternalApiException("Resposta vazia da API");
        }
        String uuid = (String) response.getBody().get("uuid");
        if (uuid == null) {
            throw new ExternalApiException("UUID não encontrado na resposta");
        }
        return uuid;
    }

    /**
     * Extrai senha da resposta de unblock
     */
    private String extractPassword(ResponseEntity<Map> response) {
        if (response.getBody() == null || !response.getBody().containsKey("newPassword")) {
            return "Usuário ativo. Clicar em esqueci minha senha.";
        }
        return (String) response.getBody().get("newPassword");
    }
}