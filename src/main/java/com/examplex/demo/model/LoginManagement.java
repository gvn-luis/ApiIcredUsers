package com.examplex.demo.model;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import java.time.LocalDateTime;

@Entity
@Table(name = "crm_Login_Management")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class LoginManagement {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "crm_Login_Management_Id")
    private Integer id;

    @Column(name = "crm_Login_Id", nullable = false)
    private Integer crmLoginId;

    @Column(name = "crm_Login_Management_User_Code", nullable = false)
    private String userCode;

    @Column(name = "crm_Login_Management_External_Key")
    private String externalKey;

    @Column(name = "crm_Ferramenta_Id")
    private Integer ferramentaId;

    @Column(name = "crm_Credenciador_Id")
    private Integer credenciadorId;

    // Nomes corretos baseados no padrão das outras colunas
    @Column(name = "gpa_DropDown_LoginManagementType", nullable = false)
    private Integer managementType;

    @Column(name = "gpa_DropDown_ManagementLoginStatus", nullable = false)
    private Integer managementStatus;

    @Column(name = "crm_Login_Management_IdUsuarioCriacao")
    private Integer idUsuarioCriacao;

    @Column(name = "crm_Login_Management_DataCriacao")
    private LocalDateTime dataCriacao;

    @Column(name = "crm_Login_Management_IdUsuarioAlteracao")
    private Integer idUsuarioAlteracao;

    @Column(name = "crm_Login_Management_DataAlteracao")
    private LocalDateTime dataAlteracao;

    @Column(name = "crm_Login_Management_RegistroExcluido")
    private Boolean registroExcluido;

    @Column(name = "log_Alteracao_Rastro")
    private String logAlteracaoRastro;

    @Column(name = "log_OrigemRastro_Id")
    private Integer logOrigemRastroId;

    @Column(name = "crm_Login_Management_DadosComplementares")
    private String dadosComplementares;
}