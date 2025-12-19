package com.examplex.demo.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.client.RestClientException;

import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j
public class AuthTokenService {

    private final RestTemplate restTemplate;

    @Value("${external-api.auth-url}")
    private String authUrl;

    @Value("${external-api.authorization-header}")
    private String authorizationHeader;

    private String currentToken;
    private long tokenExpirationTime = 0;

    /**
     * Obtém um token válido, renovando se necessário
     */
    public String getValidToken() {
        if (isTokenExpired()) {
            renewToken();
        }
        return currentToken;
    }

    /**
     * Verifica se o token atual está expirado ou prestes a expirar
     */
    private boolean isTokenExpired() {
        if (currentToken == null) {
            return true;
        }

        // Considera expirado se restam menos de 30 segundos
        long currentTime = System.currentTimeMillis();
        return currentTime >= (tokenExpirationTime - 30000);
    }

    /**
     * Renova o token fazendo uma nova requisição à API de autenticação
     */
    private void renewToken() {
        try {
            log.info("Renovando token de autenticação");

            HttpHeaders headers = new HttpHeaders();
            headers.set("Authorization", authorizationHeader);
            headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);

            MultiValueMap<String, String> formData = new LinkedMultiValueMap<>();
            formData.add("grant_type", "client_credentials");
            formData.add("scope", "partner_management");

            HttpEntity<MultiValueMap<String, String>> request = new HttpEntity<>(formData, headers);

            ResponseEntity<Map> response = restTemplate.exchange(
                    authUrl,
                    HttpMethod.POST,
                    request,
                    Map.class
            );

            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                Map<String, Object> responseBody = response.getBody();

                this.currentToken = (String) responseBody.get("access_token");
                Integer expiresIn = (Integer) responseBody.get("expires_in");

                if (this.currentToken != null && expiresIn != null) {
                    // Define o tempo de expiração baseado no expires_in
                    this.tokenExpirationTime = System.currentTimeMillis() + (expiresIn * 1000L);

                    log.info("Token renovado com sucesso. Expira em {} segundos", expiresIn);
                } else {
                    throw new RuntimeException("Token ou expires_in não encontrados na resposta");
                }
            } else {
                throw new RuntimeException("Resposta inválida da API de autenticação: " + response.getStatusCode());
            }

        } catch (RestClientException e) {
            log.error("Erro ao renovar token: {}", e.getMessage());
            throw new RuntimeException("Falha na autenticação: " + e.getMessage(), e);
        } catch (Exception e) {
            log.error("Erro inesperado ao renovar token: {}", e.getMessage());
            throw new RuntimeException("Erro inesperado na autenticação: " + e.getMessage(), e);
        }
    }

    /**
     * Força a renovação do token na próxima chamada
     */
    public void invalidateToken() {
        log.info("Token invalidado manualmente");
        this.currentToken = null;
        this.tokenExpirationTime = 0;
    }
}