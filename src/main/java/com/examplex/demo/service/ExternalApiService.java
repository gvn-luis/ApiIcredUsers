package com.examplex.demo.service;

import com.examplex.demo.model.dto.ApiRequestDto;
import com.examplex.demo.model.dto.ApiResponseDto;
import com.examplex.demo.model.dto.ApiCreateUserRequestDto;
import com.examplex.demo.model.dto.ApiCreateGroupRequestDto;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.client.RestClientException;

import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j
public class ExternalApiService {

    private final RestTemplate restTemplate;
    private final AuthTokenService authTokenService;

    @Value("${external-api.base-url}")
    private String baseUrl;

    @Value("${external-api.partner-uuid}")
    private String partnerUuid;

    @Value("${external-api.user-profile-id:5}")
    private Integer userProfileId;

    /**
     * Cria os headers padrão com autenticação
     */
    private HttpHeaders createAuthHeaders() {
        String token = authTokenService.getValidToken();

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(token);

        return headers;
    }

    /**
     * Cria um novo grupo de vendedores
     */
    public ApiResponseDto createSellerGroup(String name, String partnerExternalKey) {
        try {
            String url = baseUrl + "/partner-management/v1/seller-groups";

            ApiCreateGroupRequestDto request = new ApiCreateGroupRequestDto(
                    name,
                    name,  // label = name
                    partnerExternalKey
            );

            HttpHeaders headers = createAuthHeaders();
            HttpEntity<ApiCreateGroupRequestDto> httpEntity = new HttpEntity<>(request, headers);

            log.info("Criando grupo: {} na URL: {}", name, url);

            ResponseEntity<Map> response = restTemplate.exchange(
                    url, HttpMethod.POST, httpEntity, Map.class
            );

            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                String groupUuid = (String) response.getBody().get("uuid");

                log.info("Grupo criado com sucesso. UUID: {}", groupUuid);
                return new ApiResponseDto(true, "Grupo criado com sucesso", groupUuid);
            } else {
                log.error("Erro ao criar grupo: Status {}", response.getStatusCode());
                return new ApiResponseDto(false, "Erro na criação do grupo: " + response.getStatusCode(), null);
            }

        } catch (RestClientException e) {
            log.error("Erro na chamada da API para criar grupo {}: {}", name, e.getMessage());

            if (e.getMessage() != null && (e.getMessage().contains("401") || e.getMessage().contains("403"))) {
                log.warn("Token inválido detectado, invalidando para renovação");
                authTokenService.invalidateToken();
            }

            return new ApiResponseDto(false, "Erro na chamada da API: " + e.getMessage(), null);
        }
    }

    /**
     * Cria um novo usuário na API externa
     */
    public ApiResponseDto createUser(String personCode) {
        try {
            String url = baseUrl + "/partner-management/v1/users";

            ApiCreateUserRequestDto request = new ApiCreateUserRequestDto(
                    personCode,
                    userProfileId,
                    partnerUuid
            );

            HttpHeaders headers = createAuthHeaders();
            HttpEntity<ApiCreateUserRequestDto> httpEntity = new HttpEntity<>(request, headers);

            log.info("Criando usuário com personCode: {} na URL: {}", personCode, url);

            ResponseEntity<Map> response = restTemplate.exchange(
                    url, HttpMethod.POST, httpEntity, Map.class
            );

            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                // Extrai o UUID do usuário criado
                String userUuid = (String) response.getBody().get("uuid");

                log.info("Usuário criado com sucesso. UUID: {}", userUuid);
                return new ApiResponseDto(true, "Usuário criado com sucesso", userUuid);
            } else {
                log.error("Erro ao criar usuário: Status {}", response.getStatusCode());
                return new ApiResponseDto(false, "Erro na criação: " + response.getStatusCode(), null);
            }

        } catch (RestClientException e) {
            log.error("Erro na chamada da API para criar usuário {}: {}", personCode, e.getMessage());

            if (e.getMessage() != null && (e.getMessage().contains("401") || e.getMessage().contains("403"))) {
                log.warn("Token inválido detectado, invalidando para renovação");
                authTokenService.invalidateToken();
            }

            return new ApiResponseDto(false, "Erro na chamada da API: " + e.getMessage(), null);
        }
    }

    /**
     * Adiciona um usuário a um grupo
     */
    public ApiResponseDto addUserToGroup(String groupUuid, String userUuid) {
        try {
            String url = baseUrl + "/partner-management/v1/seller-groups/" + groupUuid + "/users/" + userUuid;

            HttpHeaders headers = createAuthHeaders();
            HttpEntity<Void> httpEntity = new HttpEntity<>(headers);

            log.info("Adicionando usuário {} ao grupo {} na URL: {}", userUuid, groupUuid, url);

            ResponseEntity<Object> response = restTemplate.exchange(
                    url, HttpMethod.PUT, httpEntity, Object.class
            );

            if (response.getStatusCode().is2xxSuccessful()) {
                log.info("Usuário {} adicionado ao grupo {} com sucesso", userUuid, groupUuid);
                return new ApiResponseDto(true, "Usuário adicionado ao grupo com sucesso", response.getBody());
            } else {
                log.error("Erro ao adicionar usuário ao grupo: Status {}", response.getStatusCode());
                return new ApiResponseDto(false, "Erro ao adicionar ao grupo: " + response.getStatusCode(), null);
            }

        } catch (RestClientException e) {
            log.error("Erro na chamada da API para adicionar usuário ao grupo: {}", e.getMessage());

            if (e.getMessage() != null && (e.getMessage().contains("401") || e.getMessage().contains("403"))) {
                log.warn("Token inválido detectado, invalidando para renovação");
                authTokenService.invalidateToken();
            }

            return new ApiResponseDto(false, "Erro na chamada da API: " + e.getMessage(), null);
        }
    }

    /**
     * Bloqueia um usuário na API externa
     */
    public ApiResponseDto blockUser(String userExternalKey) {
        try {
            String url = baseUrl + "/partner-management/v1/users/" + userExternalKey + "/block";

            ApiRequestDto request = new ApiRequestDto(partnerUuid, "iCred block");
            HttpHeaders headers = createAuthHeaders();
            HttpEntity<ApiRequestDto> httpEntity = new HttpEntity<>(request, headers);

            log.info("Bloqueando usuário: {} na URL: {}", userExternalKey, url);

            ResponseEntity<Object> response = restTemplate.exchange(
                    url, HttpMethod.POST, httpEntity, Object.class
            );

            if (response.getStatusCode().is2xxSuccessful()) {
                log.info("Usuário {} bloqueado com sucesso", userExternalKey);
                return new ApiResponseDto(true, "Usuário bloqueado com sucesso", response.getBody());
            } else {
                log.error("Erro ao bloquear usuário {}: Status {}", userExternalKey, response.getStatusCode());
                return new ApiResponseDto(false, "Erro no bloqueio: " + response.getStatusCode(), null);
            }

        } catch (RestClientException e) {
            log.error("Erro na chamada da API para bloquear usuário {}: {}", userExternalKey, e.getMessage());

            if (e.getMessage() != null && (e.getMessage().contains("401") || e.getMessage().contains("403"))) {
                log.warn("Token inválido detectado, invalidando para renovação");
                authTokenService.invalidateToken();
            }

            return new ApiResponseDto(false, "Erro na chamada da API: " + e.getMessage(), null);
        }
    }

    /**
     * Desbloqueia um usuário na API externa
     */
    public ApiResponseDto unblockUser(String userExternalKey) {
        try {
            String url = baseUrl + "/partner-management/v1/users/" + userExternalKey + "/unblock";

            ApiRequestDto request = new ApiRequestDto(partnerUuid, "iCred block");
            HttpHeaders headers = createAuthHeaders();
            HttpEntity<ApiRequestDto> httpEntity = new HttpEntity<>(request, headers);

            log.info("Desbloqueando usuário: {} na URL: {}", userExternalKey, url);

            ResponseEntity<Map> response = restTemplate.exchange(
                    url, HttpMethod.POST, httpEntity, Map.class
            );

            if (response.getStatusCode().is2xxSuccessful()) {
                log.info("Usuário {} desbloqueado com sucesso", userExternalKey);

                String newPassword = null;
                if (response.getBody() != null && response.getBody().containsKey("newPassword")) {
                    newPassword = (String) response.getBody().get("newPassword");
                    log.info("Nova senha gerada para usuário {}", userExternalKey);
                } else {
                    // Se não gerou senha, retorna mensagem padrão
                    newPassword = "Usuário ativo. Clicar em esqueci minha senha.";
                    log.info("Usuário {} desbloqueado sem geração de senha", userExternalKey);
                }

                return new ApiResponseDto(true, "Usuário desbloqueado com sucesso", newPassword);
            } else {
                log.error("Erro ao desbloquear usuário {}: Status {}", userExternalKey, response.getStatusCode());
                return new ApiResponseDto(false, "Erro no desbloqueio: " + response.getStatusCode(), null);
            }

        } catch (RestClientException e) {
            log.error("Erro na chamada da API para desbloquear usuário {}: {}", userExternalKey, e.getMessage());

            if (e.getMessage() != null && (e.getMessage().contains("401") || e.getMessage().contains("403"))) {
                log.warn("Token inválido detectado, invalidando para renovação");
                authTokenService.invalidateToken();
            }

            return new ApiResponseDto(false, "Erro na chamada da API: " + e.getMessage(), null);
        }
    }
}