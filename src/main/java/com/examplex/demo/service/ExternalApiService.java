package com.examplex.demo.service;

import com.examplex.demo.model.dto.ApiRequestDto;
import com.examplex.demo.model.dto.ApiResponseDto;
import com.examplex.demo.model.dto.ApiCreateRequestDto;
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

            // Se for erro de autenticação, invalida o token
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

                // Extrai a nova senha da resposta se existir
                String newPassword = null;
                if (response.getBody() != null && response.getBody().containsKey("newPassword")) {
                    newPassword = (String) response.getBody().get("newPassword");
                    log.info("Nova senha gerada para usuário {}", userExternalKey);
                }

                return new ApiResponseDto(true, "Usuário desbloqueado com sucesso", newPassword);
            } else {
                log.error("Erro ao desbloquear usuário {}: Status {}", userExternalKey, response.getStatusCode());
                return new ApiResponseDto(false, "Erro no desbloqueio: " + response.getStatusCode(), null);
            }

        } catch (RestClientException e) {
            log.error("Erro na chamada da API para desbloquear usuário {}: {}", userExternalKey, e.getMessage());

            // Se for erro de autenticação, invalida o token
            if (e.getMessage() != null && (e.getMessage().contains("401") || e.getMessage().contains("403"))) {
                log.warn("Token inválido detectado, invalidando para renovação");
                authTokenService.invalidateToken();
            }

            return new ApiResponseDto(false, "Erro na chamada da API: " + e.getMessage(), null);
        }
    }
}