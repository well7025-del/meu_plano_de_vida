# Publicar o app sem usar terminal

Se `npx wrangler login` não funcionou na sua máquina — ou se você nem tem o
projeto baixado —, este caminho não usa terminal nenhum. São duas páginas web,
uns 5 minutos, e depois é um botão.

O GitHub compila e publica para você.

---

## Passo 0 — Ativar os Workers na conta (conta nova precisa disso)

Numa conta recém-criada, os Workers só ficam disponíveis depois que você entra
na área uma vez e escolhe o subdomínio. **Sem isso, qualquer publicação falha**,
pelo terminal ou pelo GitHub.

1. Abra <https://dash.cloudflare.com>
2. Menu da esquerda → **Workers & Pages**
3. Se aparecer a tela de boas-vindas, escolha um subdomínio (ex.: `wellington`)
   e confirme — é o que vira `vagas.wellington.workers.dev`
4. Se pedir para escolher um plano, escolha o **Free**

Se você já vê uma lista (mesmo vazia) e um **Account ID** na direita, esse
passo já está feito.

---

## Passo 1 — Pegar o ID da sua conta Cloudflare

1. Abra <https://dash.cloudflare.com>
2. No menu da esquerda, clique em **Workers & Pages**
3. Na coluna da direita, procure **Account ID** e clique em copiar

É uma sequência de letras e números, tipo `a1b2c3d4e5f6...`. Guarde.

> Se não achar, o ID também está no endereço da página depois de
> `dash.cloudflare.com/`.

---

## Passo 2 — Criar o token

1. Abra <https://dash.cloudflare.com/profile/api-tokens>
2. Clique em **Create Token**
3. Procure **Edit Cloudflare Workers** e clique em **Use template**
4. Role até o fim e clique em **Continue to summary**
5. Clique em **Create Token**
6. **Copie o token agora** — a Cloudflare mostra ele uma vez só

Esse token permite publicar Workers na sua conta. Não cole em lugar nenhum
além do Passo 3, e nunca dentro de um arquivo do projeto.

---

## Passo 3 — Guardar os dois no GitHub

1. Abra
   <https://github.com/well7025-del/meu_plano_de_vida/settings/secrets/actions>
2. Clique em **New repository secret**
3. Crie o primeiro:
   - **Name:** `CLOUDFLARE_API_TOKEN`
   - **Secret:** o token do Passo 2
   - **Add secret**
4. Clique em **New repository secret** de novo e crie o segundo:
   - **Name:** `CLOUDFLARE_ACCOUNT_ID`
   - **Secret:** o ID do Passo 1
   - **Add secret**

Os nomes precisam ser exatamente esses, em maiúsculas.

---

## Passo 4 — Publicar

1. Abra
   <https://github.com/well7025-del/meu_plano_de_vida/actions/workflows/publicar.yml>
2. Clique em **Run workflow** (botão à direita)
3. Escolha o branch `claude/vagas-parking-detection-app-o30hao`
4. Clique no **Run workflow** verde

Leva uns 2 minutos. Quando ficar com o ✅, clique na execução, abra o passo
**Publicar** e procure a linha que termina em `workers.dev`:

```
https://vagas.seu-subdominio.workers.dev
```

**Esse é o endereço do teste.** Mande no grupo, e cada pessoa adiciona à tela
inicial do celular (o passo a passo do piloto está em
[TESTE-DE-CAMPO.md](TESTE-DE-CAMPO.md)).

A partir daí, toda vez que o app mudar, basta repetir o Passo 4 — ou nem isso:
qualquer alteração enviada ao branch republica sozinha.

---

## Se der erro

| O que aparece | O que fazer |
|---|---|
| O trabalho termina verde dizendo "Faltam os segredos" | Algum nome de segredo saiu diferente. Confira que são `CLOUDFLARE_API_TOKEN` e `CLOUDFLARE_ACCOUNT_ID`, maiúsculas, sem espaço |
| `Authentication error [code: 10000]` | O token foi copiado incompleto, ou é de outra conta. Refaça o Passo 2 |
| `workers.api.error.not_authorized` ou erro de permissão ao criar | O token precisa poder **criar** um Worker, não só editar. Refaça o Passo 2 e, na tela do token, adicione a permissão **Account → Workers Scripts → Edit** |
| `D1_ERROR: no such table` | O banco perdeu o esquema. Dá para recriar pela própria dashboard: **Workers & Pages → D1 → vagas → Console**, e colar o conteúdo de `services/worker/schema.sql` |
| A aba **Actions** não aparece no GitHub | Vá em **Settings → Actions → General** e marque **Allow all actions** |
| `code: 10015` / *not entitled to use Workers* | Faltou o **Passo 0**: entrar em Workers & Pages e escolher o subdomínio |
| `code: 10007` / *workers.dev subdomain not found* | Mesmo caso: o subdomínio da conta ainda não foi escolhido |
| `fetch failed` / `ETIMEDOUT` | Rede, firewall ou proxy. Tente de outra conexão (4G do celular serve) |

---

## E se eu preferir o terminal?

Continua valendo, e é mais rápido no dia a dia:

```bash
npx wrangler login
npm run publicar
```

Para isso você precisa do projeto baixado e do **Node 22.5 ou mais novo**
(<https://nodejs.org> — baixe a versão LTS). Se o `wrangler login` não abrir o
navegador (é o que costuma acontecer em WSL ou em máquina remota), use o token
do Passo 2 no lugar:

```bash
export CLOUDFLARE_API_TOKEN=cole-o-token-aqui   # Windows: set CLOUDFLARE_API_TOKEN=...
npm run publicar
```
