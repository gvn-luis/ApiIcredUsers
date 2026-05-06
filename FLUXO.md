# Fluxo de Processamento — ApiIcredUsers

## Tabela de Status (gpa_DropDown_ManagementLoginStatus)

| Código  | Nome             | Descrição                                              | Processado? |
|---------|------------------|--------------------------------------------------------|-------------|
| `-4109` | **PENDENTE**     | Falhou antes, aguardando retry. **PRIORIDADE MÁXIMA**  | ✅ Sim       |
| `-4106` | **FILA**         | Novo item, aguardando processamento                    | ✅ Sim       |
| `-4108` | **ERRO**         | Erro grave, aguardando processamento                   | ✅ Sim       |
| `-4107` | **SUCESSO**      | Processado com sucesso. Estado final                   | ❌ Não       |

> **Ordem de prioridade na fila:** `-4109` → `-4106` → `-4108`
> Items com status `-4109` são sempre os primeiros a serem processados pois são retentativas de falhas anteriores.

---

## Tabela de Tipos (gpa_DropDown_LoginManagementType)

| Código  | Nome           | Descrição                                  |
|---------|----------------|--------------------------------------------|
| `-4104` | **BLOCK**      | Bloquear usuário no iCred                  |
| `-4105` | **UNBLOCK**    | Desbloquear usuário no iCred               |
| `2268`  | **RESET**      | Resetar senha (block + unblock)            |
| `3833`  | **CREATE**     | Criar novo usuário no iCred                |
| `4480`  | **LINK_GROUP** | Vincular usuário a um grupo de vendedores  |
| `5050`  | **BANNED**     | Usuário banido (estado final, não retenta) |

---

## Ciclo do Scheduler

```
Cron: 0 */1 * * * *  (a cada 1 minuto)
Habilitado via: scheduler.login-management.enabled=true

          ┌─────────────────────┐
          │   SCHEDULER INICIA  │
          └──────────┬──────────┘
                     │
                     ▼
     ┌───────────────────────────────┐
     │  Busca itens pendentes no DB  │
     │  STATUS IN (-4109,-4106,-4108)│
     │  Ordem: -4109 → -4106 → -4108│
     └──────────────┬────────────────┘
                    │
            ┌───────┴───────┐
            │  Fila vazia?  │
            └───────┬───────┘
          Sim ◄─────┘──────► Não
           │                  │
           ▼                  ▼
         FIM        Para cada item (+ delay 500ms)
                              │
                              ▼
                    ┌─────────────────┐
                    │  processItem()  │
                    │  veja fluxos    │
                    │  abaixo         │
                    └────────┬────────┘
                             │
                    Sucesso ─┤─ Falha
                             │
                    STATUS_SUCCESS(-4107) ou STATUS_PENDING(-4109)
```

---

## Fluxo 1 — CREATE (Tipo 3833)

> Cria um novo usuário no iCred e opcionalmente o vincula a um grupo.

```
Item com tipo=3833
        │
        ▼
 userCode (CPF) preenchido?
        │
   Não ─┤─ Sim
        │      │
STATUS  │      ▼
-4109   │  POST /partner-management/v1/users
        │  { personCode: CPF, userProfileId: 5, partnerUuid }
        │      │
        │  ┌───┴───────────────────────┐
        │  │  Usuário BANIDO?          │
        │  │  (errorCode = banned)     │
        │  └───┬───────────────────────┘
        │  Sim ┤ Não
        │      │    │
        │      │    ▼
        │      │  Criado com sucesso?
        │      │    │
        │      │ Não┤ Sim → userUuid obtido
        │      │    │          │
        │    STATUS │          ▼
        │    -4109  │   dadosComplementares tem groupUuid?
        │           │          │
        │           │  Sim ────┼──── Não → tem groupNome?
        │           │    │     │               │
        │           │    │     │    Sim ────────┤─── Não (sem grupo)
        │           │    │     │         │      │
        │           │    ▼     │         ▼      │
        │           │  addUserToGroup    createGroupAndLink
        │           │  (groupUuid, CPF)  └─ extrair pessoaId do nome
        │           │                   └─ buscar partnerExternalKey no DB
        │           │                   └─ POST /seller-groups
        │           │                   └─ salvar grupo no DB
        │           │                   └─ addUserToGroup
        │           │
        │           ▼
        │   executeBlockAndUnblock(userUuid)
        │   └─ POST /users/{uuid}/block
        │   └─ aguarda 500ms
        │   └─ POST /users/{uuid}/unblock
        │   └─ extrai nova senha da resposta
        │
        │           ▼
        │   STATUS_SUCCESS (-4107)
        │   externalKey = userUuid
        │   dadosComplementares = { newPassword: "..." }
        │
        ▼
   TYPE_BANNED (5050)
   STATUS_SUCCESS (-4107)
   dadosComplementares = { banned: true, message: "..." }
```

---

## Fluxo 2 — BLOCK (Tipo -4104)

> Bloqueia um usuário existente no iCred.

```
Item com tipo=-4104
        │
        ▼
 externalKey preenchida?
        │
   Não ─┤─ Sim
        │      │
STATUS  │      ▼
-4109   │  POST /partner-management/v1/users/{externalKey}/block
        │  { partnerUuid, reason: "iCred block" }
        │      │
        │  ┌───┴──────────┐
        │  │  Sucesso?    │
        │  └───┬──────────┘
        │  Sim ┤ Não
        │      │    │
        │      │  STATUS -4109 (retry)
        │      │
        │      ▼
        │  STATUS_SUCCESS (-4107)
```

---

## Fluxo 3 — UNBLOCK (Tipo -4105)

> Desbloqueia um usuário existente e gera nova senha.

```
Item com tipo=-4105
        │
        ▼
 externalKey preenchida?
        │
   Não ─┤─ Sim
        │      │
STATUS  │      ▼
-4109   │  POST /partner-management/v1/users/{externalKey}/unblock
        │  { partnerUuid, reason: "iCred unblock" }
        │      │
        │  ┌───┴──────────────────────────┐
        │  │  Resposta                    │
        │  └───┬──────────────────────────┘
        │      │
        │   Sucesso ──────────────────────────────────────────────┐
        │      │                                                   │
        │  "já está ativo"?                                        │
        │      │                                                   │
        │  Sim ┤ Não                                               │
        │      │     │                                             │
        │      │   STATUS -4109                                    │
        │      │                                                   │
        │      ▼                                                   ▼
        │  Executa RESET (ver fluxo 4)         STATUS_SUCCESS (-4107)
        │  (block + unblock para gerar senha)  dadosComplementares = { newPassword: "..." }
```

---

## Fluxo 4 — RESET (Tipo 2268)

> Reseta a senha do usuário via ciclo block → unblock.

```
Item com tipo=2268
        │
        ▼
 externalKey preenchida?
        │
   Não ─┤─ Sim
        │      │
STATUS  │      ▼
-4109   │  POST /partner-management/v1/users/{externalKey}/block
        │      │
        │  ┌───┴──────────────────────────────────┐
        │  │  Erro "user_not_related_to_partner"? │
        │  └───┬──────────────────────────────────┘
        │  Sim ┤ Não
        │      │    │
        │      │  Sucesso? ── Não → STATUS -4109
        │      │    │
        │      │    ▼
        │      │  aguarda 500ms
        │      │    │
        │      │    ▼
        │      │  POST /partner-management/v1/users/{externalKey}/unblock
        │      │    │
        │      │  Sucesso? ── Não → STATUS -4109
        │      │    │
        │      │    ▼
        │      │  STATUS_SUCCESS (-4107)
        │      │  dadosComplementares = { newPassword: "..." }
        │      │
        │      ▼
        │  Executa APENAS UNBLOCK
        │  (vincula usuário ao corban)
        │  POST /unblock → STATUS_SUCCESS
```

---

## Fluxo 5 — LINK_GROUP (Tipo 4480)

> Vincula um usuário a um grupo de vendedores. Cria o grupo se não existir.

```
Item com tipo=4480
        │
        ▼
 userCode (CPF) preenchido?
        │
   Não ─┤─ Sim
        │      │
STATUS  │      ▼
-4109   │  Parse dadosComplementares
        │  { managementGroupsUuid, managementGroupsNome }
        │      │
        │  ┌───┴──────────────────────────────────────┐
        │  │ groupUuid preenchido?                    │
        │  └───┬──────────────────────────────────────┘
        │  Sim ┤ Não → groupNome preenchido?
        │      │              │
        │      │         Sim ─┤─ Não → STATUS -4109
        │      │              │
        │      │              ▼
        │      │         Extrair pessoaId do nome
        │      │         Formato: "GVN | 12345 | Nome"
        │      │              │
        │      │              ▼
        │      │         Buscar partnerExternalKey no DB
        │      │         (crm_Login_Management + crm_Login + crm_Conta)
        │      │              │
        │      │         Não encontrado? → STATUS -4109
        │      │              │
        │      │              ▼
        │      │         POST /partner-management/v1/seller-groups
        │      │         { name, label: pessoaId, partnerExternalKey }
        │      │              │
        │      │         Falhou? → STATUS -4109
        │      │              │
        │      │              ▼
        │      │         Salvar grupo no DB (crm_Login_Management_Groups)
        │      │              │
        │      │              ▼
        │      │         groupUuid = novo UUID criado
        │      │
        │      ▼
        │  POST /partner-management/v1/seller-groups/{groupUuid}/sellers
        │  { personCode: CPF }
        │      │
        │  Sucesso? ── Não → STATUS -4109
        │      │
        │      ▼
        │  STATUS_SUCCESS (-4107)
```

---

## APIs Externas Utilizadas (iCred)

| Operação             | Método | Endpoint                                                |
|----------------------|--------|---------------------------------------------------------|
| Obter token          | POST   | `/authorization-server/oauth2/token`                    |
| Criar usuário        | POST   | `/partner-management/v1/users`                          |
| Bloquear usuário     | POST   | `/partner-management/v1/users/{uuid}/block`             |
| Desbloquear usuário  | POST   | `/partner-management/v1/users/{uuid}/unblock`           |
| Criar grupo          | POST   | `/partner-management/v1/seller-groups`                  |
| Vincular ao grupo    | POST   | `/partner-management/v1/seller-groups/{uuid}/sellers`   |
| Listar grupo         | GET    | `/partner-management/v1/seller-groups/{uuid}/sellers`   |

---

## Endpoints da API (uso manual)

| Método | Endpoint                                    | Descrição                                      |
|--------|---------------------------------------------|------------------------------------------------|
| POST   | `/api/icredGvnUser/process`                 | Dispara o processamento manualmente            |
| GET    | `/api/icredGvnUser/test-token`              | Testa se o token está válido                   |
| POST   | `/api/icredGvnUser/refresh-token`           | Força renovação do token                       |
| GET    | `/api/icredGvnUser/stats`                   | Qtd de itens pendentes e status do sistema     |
| POST   | `/api/icredGvnUser/blockUserIcred/{uuid}`   | Bloqueia usuário diretamente                   |
| POST   | `/api/icredGvnUser/unblockUserIcred/{uuid}` | Desbloqueia usuário diretamente                |
| POST   | `/api/icredGvnUser/createUserIcred`         | Cria usuário diretamente                       |
| POST   | `/api/icredGvnUser/addUserToGroup`          | Adiciona usuário a grupo diretamente           |
| GET    | `/api/icredGvnUser/listGroupSellers/{uuid}` | Lista vendedores de um grupo                   |
| GET    | `/actuator/health`                          | Health check (usado pelo Koyeb)                |

---

## Campos do dadosComplementares (JSON)

Armazenado em `crm_Login_Management_DadosComplementares`:

```json
{
  "managementGroupsUuid": "uuid-do-grupo",
  "managementGroupsNome": "GVN | 12345 | Nome do Vendedor",
  "newPassword": "senha gerada pelo iCred",
  "banned": true,
  "message": "motivo do banimento"
}
```
