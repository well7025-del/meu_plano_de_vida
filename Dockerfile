# Imagem para deixar o teste no ar sem depender do seu computador ligado.
# Funciona em Fly.io, Render, Railway ou qualquer lugar que rode um container.
FROM node:22-alpine

WORKDIR /app

COPY package.json package-lock.json* ./
COPY packages/core/package.json packages/core/
COPY services/api/package.json services/api/
RUN npm install --no-audit --no-fund

COPY . .
RUN npm run build -w @vagas/core

# O banco fica em volume: sem ele, cada reinicio perde os eventos da ultima
# hora. Nao e tragedia (a retencao e curta), mas o mapa reabre vazio.
ENV VAGAS_DB=/data/vagas.sqlite
ENV PORT=8080
VOLUME /data
EXPOSE 8080

HEALTHCHECK --interval=30s --timeout=5s \
  CMD wget -qO- http://127.0.0.1:8080/healthz || exit 1

CMD ["npm", "run", "api"]
