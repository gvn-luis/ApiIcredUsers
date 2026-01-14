package com.examplex.demo.repository;

import com.examplex.demo.model.LoginManagement;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface LoginManagementRepository extends JpaRepository<LoginManagement, Integer> {

    /**
     * Busca itens pendentes de processamento (Status: Fila = -4106 ou Erro = -4108)
     */
    @Query("SELECT lm FROM LoginManagement lm WHERE lm.managementStatus IN (-4106, -4108) AND lm.registroExcluido = false")
    List<LoginManagement> findPendingProcessing();

    /**
     * Atualiza o status de um item
     */
    @Modifying
    @Transactional
    @Query("UPDATE LoginManagement lm SET lm.managementStatus = :newStatus, " +
            "lm.dataAlteracao = :dataAlteracao, lm.logAlteracaoRastro = :logRastro " +
            "WHERE lm.id = :id")
    void updateStatus(@Param("id") Integer id,
                      @Param("newStatus") Integer newStatus,
                      @Param("dataAlteracao") LocalDateTime dataAlteracao,
                      @Param("logRastro") String logRastro);

    /**
     * Atualiza o status e dados complementares (usado para salvar senha no unblock)
     */
    @Modifying
    @Transactional
    @Query("UPDATE LoginManagement lm SET lm.managementStatus = :newStatus, " +
            "lm.dataAlteracao = :dataAlteracao, lm.logAlteracaoRastro = :logRastro, " +
            "lm.dadosComplementares = :dadosComplementares " +
            "WHERE lm.id = :id")
    void updateStatusWithData(@Param("id") Integer id,
                              @Param("newStatus") Integer newStatus,
                              @Param("dataAlteracao") LocalDateTime dataAlteracao,
                              @Param("logRastro") String logRastro,
                              @Param("dadosComplementares") String dadosComplementares);

    /**
     * Atualiza o status, dados complementares E externalKey (usado no CREATE)
     */
    @Modifying
    @Transactional
    @Query("UPDATE LoginManagement lm SET lm.managementStatus = :newStatus, " +
            "lm.dataAlteracao = :dataAlteracao, lm.logAlteracaoRastro = :logRastro, " +
            "lm.dadosComplementares = :dadosComplementares, lm.externalKey = :externalKey " +
            "WHERE lm.id = :id")
    void updateStatusWithDataAndExternalKey(@Param("id") Integer id,
                                            @Param("newStatus") Integer newStatus,
                                            @Param("dataAlteracao") LocalDateTime dataAlteracao,
                                            @Param("logRastro") String logRastro,
                                            @Param("dadosComplementares") String dadosComplementares,
                                            @Param("externalKey") String externalKey);

    /**
     * Conta o número de itens pendentes
     */
    @Query("SELECT COUNT(lm) FROM LoginManagement lm WHERE lm.managementStatus IN (-4106, -4108) AND lm.registroExcluido = false")
    long countPendingProcessing();
}