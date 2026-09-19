# Publicar o app — pelo celular, sem terminal

Este guia é para quem está com o celular na mão e nada mais. Nenhum comando,
nenhum programa para instalar. São duas telas e um copiar-colar.

No fim, o app estará no ar em:

```
https://vagas.well7025.workers.dev
```

> **Dica para as duas telas:** use o **Chrome**, não o app do GitHub (o app não
> deixa cadastrar segredos). Se algum menu não aparecer, toque nos três
> pontinhos ⋮ do Chrome e marque **Site para computador**.

---

## Tela 1 — Criar o token na Cloudflare

1. Abra <https://dash.cloudflare.com/profile/api-tokens>
2. Toque em **Create Token**
3. Role até encontrar **Edit Cloudflare Workers** e toque em **Use template**
4. Não mude nada. Role até o fim e toque em **Continue to summary**
5. Toque em **Create Token**
6. Aparece uma caixa com o token. Toque em **Copy** — **é a única vez que ele
   é mostrado**

O token fica na sua área de transferência. Vá direto para a Tela 2 antes de
copiar qualquer outra coisa.

> Esse token permite publicar na sua conta Cloudflare. Cole só no campo da
> Tela 2. Nunca no chat, nunca num arquivo do projeto.

---

## Tela 2 — Guardar o token no GitHub

1. Abra
   <https://github.com/well7025-del/meu_plano_de_vida/settings/secrets/actions>
   (se pedir login, entre com a sua conta do GitHub)
2. Toque em **New repository secret**
3. No campo **Name**, digite exatamente:

   ```
   CLOUDFLARE_API_TOKEN
   ```

4. No campo **Secret**, cole o token da Tela 1 (segure o campo → **Colar**)
5. Toque em **Add secret**

Pronto. Essa foi a última coisa que precisava de você.

---

## Tela 3 — Publicar

Agora qualquer alteração no projeto publica sozinha. Para publicar **agora**:

1. Abra
   <https://github.com/well7025-del/meu_plano_de_vida/actions/workflows/publicar.yml>
2. Toque em **Run workflow**
3. Em **Use workflow from**, escolha `claude/vagas-parking-detection-app-o30hao`
4. Toque no **Run workflow** verde

Espere uns 2 minutos e atualize a página. Quando aparecer o ✅, o app está no
ar.

> **Não achou o botão "Run workflow"?** No celular ele às vezes some. Duas
> saídas: ative **Site para computador** no menu ⋮ do Chrome, ou simplesmente
> me avise aqui no chat — eu envio uma alteração qualquer para o projeto e isso
> dispara a publicação sozinho.

---

## Tela 4 — Instalar no celular

1. Abra <https://vagas.well7025.workers.dev>
2. Menu ⋮ do Chrome → **Adicionar à tela inicial**
3. Abra pelo ícone que apareceu
4. Toque em **Permitir localização e começar**

No iPhone é pelo botão de compartilhar → **Adicionar à Tela de Início**.

Esse é o link para mandar no grupo. O roteiro do teste, a mensagem pronta para
os amigos e o que anotar estão em [TESTE-DE-CAMPO.md](TESTE-DE-CAMPO.md).

---

## Se algo der errado

| O que aparece | O que fazer |
|---|---|
| O trabalho fica verde dizendo "Falta o segredo da Cloudflare" | O nome do segredo saiu diferente. Tem que ser `CLOUDFLARE_API_TOKEN`, tudo maiúsculo, sem espaço antes ou depois |
| ❌ vermelho no passo **Publicar**, com `Authentication error` | O token foi copiado pela metade. Refaça a Tela 1 e cole de novo na Tela 2 (dá para sobrescrever o segredo) |
| ❌ vermelho com `not_authorized` | O token ficou sem permissão de **criar** Worker. Refaça a Tela 1 conferindo que o modelo escolhido é **Edit Cloudflare Workers** |
| A aba **Actions** não aparece | **Settings → Actions → General → Allow all actions** |
| Não consigo entrar em **Settings** do repositório | Você precisa estar logado no GitHub com a conta dona do repositório (`well7025-del`) |
| A página abre mas fica em branco no celular | Ative **Site para computador** no menu ⋮ do Chrome |

Em qualquer outro caso: tire um print da tela do erro e me mande aqui.

---

## Alternativa: publicar direto pela Cloudflare

Na tela **Workers & Pages** existe o botão **Create application** → conectar ao
GitHub. Funciona, mas exige digitar comandos de build no teclado do celular —
mais chance de errar do que colar um token. Prefira o caminho acima.

---

## Para quando você estiver num computador

```bash
npx wrangler login
npm run publicar
```

Precisa do projeto baixado e do Node 22.5+ (<https://nodejs.org>). Se a
publicação falhar, o comando traduz o erro e diz o que fazer.
