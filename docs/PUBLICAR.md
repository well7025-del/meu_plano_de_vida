# Publicar o app sem usar terminal

Se `npx wrangler login` não funcionou na sua máquina — ou se você nem tem o
projeto baixado —, este caminho não usa terminal nenhum. São duas páginas web,
uns 5 minutos, e depois é um botão.

O GitHub compila e publica para você.

---

## Passo 1 — Criar o token

1. Abra <https://dash.cloudflare.com/profile/api-tokens>
2. Clique em **Create Token**
3. Procure **Edit Cloudflare Workers** e clique em **Use template**
4. Role até o fim e clique em **Continue to summary**
5. Clique em **Create Token**
6. **Copie o token agora** — a Cloudflare mostra ele uma vez só

Esse token permite publicar Workers na sua conta. Não cole em lugar nenhum
além do Passo 2, e nunca dentro de um arquivo do projeto nem no chat.

---

## Passo 2 — Guardar o token no GitHub

1. Abra
   <https://github.com/well7025-del/meu_plano_de_vida/settings/secrets/actions>
2. Clique em **New repository secret**
3. Preencha:
   - **Name:** `CLOUDFLARE_API_TOKEN`
   - **Secret:** o token do Passo 1
   - **Add secret**

O nome precisa ser exatamente esse, em maiúsculas. É o único segredo
necessário: o ID da conta já está em `services/worker/wrangler.toml`.

---

## Passo 3 — Publicar

1. Abra
   <https://github.com/well7025-del/meu_plano_de_vida/actions/workflows/publicar.yml>
2. Clique em **Run workflow** (botão à direita)
3. Escolha o branch `claude/vagas-parking-detection-app-o30hao`
4. Clique no **Run workflow** verde

Leva uns 2 minutos. Quando ficar com o ✅, o endereço do teste é:

```
https://vagas.well7025.workers.dev
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
| O trabalho termina verde dizendo "Falta o segredo" | O nome do segredo saiu diferente. Tem que ser `CLOUDFLARE_API_TOKEN`, maiúsculas, sem espaço |
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
do Passo 1 no lugar:

```bash
export CLOUDFLARE_API_TOKEN=cole-o-token-aqui   # Windows: set CLOUDFLARE_API_TOKEN=...
npm run publicar
```
