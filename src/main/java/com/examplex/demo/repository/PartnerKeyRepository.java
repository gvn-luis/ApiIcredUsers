package com.examplex.demo.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.examplex.demo.model.LoginManagement;

@Repository
public interface PartnerKeyRepository extends JpaRepository<LoginManagement, Integer> {

    /**
     * Busca o partnerExternalKey (crm_Login_Management_External_Key)
     * baseado no gpa_Pessoa_Id
     *
     * SELECT lm.crm_Login_Management_External_Key
     * FROM crm_Login_Management lm
     * INNER JOIN crm_Login l ON l.crm_Login_Id = lm.crm_Login_Id
     * WHERE l.gpa_Pessoa_Id = :pessoaId
     *   AND l.crm_Ferramenta_Id = -13
     * ORDER BY lm.crm_Login_Management_Id DESC
     */
    @Query(value = "SELECT TOP 1 lm.crm_Login_Management_External_Key " +
            "FROM GPA_CRM.dbo.crm_Login_Management lm " +
            "INNER JOIN GPA_CRM.dbo.crm_Login l ON l.crm_Login_Id = lm.crm_Login_Id " +
            "INNER JOIN GPA_CRM.dbo.crm_Conta C ON C.gpa_Pessoa_Id = l.gpa_Pessoa_Id " +
            "WHERE C.crm_Conta_Id = :contaId " +
            "  AND l.crm_Ferramenta_Id = -13 " +
            "  AND lm.crm_Login_Management_External_Key IS NOT NULL " +
            "  AND lm.crm_Login_Management_RegistroExcluido = 0 " +
            "  AND C.crm_Conta_RegistroExcluido = 0 " +
            "  AND l.crm_Login_RegistroExcluido = 0 " +
            "ORDER BY lm.crm_Login_Management_Id DESC",
            nativeQuery = true)
    String findPartnerExternalKeyByPessoaId(@Param("contaId") Integer pessoaId);
}