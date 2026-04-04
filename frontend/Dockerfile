FROM node:20.18.2-bookworm-slim

WORKDIR /app

COPY package.json package-lock.json ./

RUN npm ci

COPY index.html tsconfig.json tsconfig.node.json vite.config.ts ./
COPY src ./src

EXPOSE 5173

CMD ["npm", "run", "dev", "--", "--host", "0.0.0.0", "--port", "5173"]
