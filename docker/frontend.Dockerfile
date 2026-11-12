# 前端镜像：Nuxt3 SSR 构建
# 构建上下文 = frontend/

FROM node:22-alpine AS build
WORKDIR /app
RUN corepack enable
COPY package.json pnpm-lock.yaml pnpm-workspace.yaml .npmrc ./
RUN pnpm install --frozen-lockfile
COPY . .
ENV NUXT_API_ORIGIN=http://backend:8080
ENV NUXT_PROXY_TARGET=http://backend:8080
RUN pnpm build

FROM node:22-alpine
WORKDIR /app
ENV NODE_ENV=production
ENV NUXT_API_ORIGIN=http://backend:8080
ENV NUXT_PROXY_TARGET=http://backend:8080
COPY --from=build /app/.output ./.output
EXPOSE 3000
CMD ["node", ".output/server/index.mjs"]
