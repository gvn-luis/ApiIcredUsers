package com.examplex.demo.model;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

@Entity
@Table(name = "crm_Login_ManagementGroups")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class LoginManagementGroups {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "crm_Login_ManagementGroups_Id")
    private Integer id;

    @Column(name = "crm_Login_ManagementGroups_uuid")
    private String uuid;

    @Column(name = "crm_Login_ManagementGroups_Nome")
    private String nome;
}