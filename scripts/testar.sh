#!/usr/bin/env bash
#
# Sobe o Vagas para um teste de campo e, se possivel, cria um endereco HTTPS
# publico para mandar no grupo.
#
#   ./scripts/testar.sh
#
# Por que HTTPS: navegador so libera GPS em conexao segura. "localhost" conta,
# mas o celular do seu amigo nao e o seu localhost — por isso o tunel.
set -euo pipefail

cd "$(dirname "$0")/.."
PORT="${PORT:-8787}"

node_major=$(node -v | sed 's/v\([0-9]*\).*/\1/')
node_minor=$(node -v | sed 's/v[0-9]*\.\([0-9]*\).*/\1/')
if [ "$node_major" -lt 22 ] || { [ "$node_major" -eq 22 ] && [ "$node_minor" -lt 5 ]; }; then
  echo "Precisa de Node 22.5 ou mais novo (o banco embutido vem dele). Atual: $(node -v)"
  exit 1
fi

if [ ! -d node_modules ]; then
  echo "==> instalando dependencias"
  npm install
fi

echo "==> compilando o motor de deteccao"
npm run build -w @vagas/core >/dev/null

echo "==> subindo o servidor na porta $PORT"
PORT="$PORT" npm run api &
SERVER_PID=$!
trap 'kill $SERVER_PID 2>/dev/null || true' EXIT INT TERM
sleep 3

if ! kill -0 "$SERVER_PID" 2>/dev/null; then
  echo "O servidor nao subiu. Rode 'npm run api' para ver o erro."
  exit 1
fi

echo
if command -v cloudflared >/dev/null 2>&1; then
  echo "==> abrindo tunel HTTPS publico (Ctrl+C encerra tudo)"
  echo "    Procure abaixo a linha com https://<algo>.trycloudflare.com"
  echo "    Esse e o endereco para mandar para os seus amigos."
  echo
  cloudflared tunnel --url "http://localhost:$PORT"
else
  cat <<TXT
  Servidor rodando: http://localhost:$PORT

  Para abrir no celular, voce precisa de um endereco HTTPS. O caminho mais
  rapido e o cloudflared (nao precisa de conta):

    macOS:   brew install cloudflared
    Linux:   https://developers.cloudflare.com/cloudflare-one/connections/connect-networks/downloads/
    Windows: winget install --id Cloudflare.cloudflared

  Depois, em outro terminal:

    cloudflared tunnel --url http://localhost:$PORT

  Ele imprime um endereco https://<algo>.trycloudflare.com — e esse que voce
  manda no grupo. Enquanto este terminal estiver aberto, o teste esta no ar.

  Ctrl+C encerra.
TXT
  wait $SERVER_PID
fi
