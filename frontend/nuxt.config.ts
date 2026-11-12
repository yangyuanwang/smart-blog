import tailwindcss from '@tailwindcss/vite'

// Nuxt3 前端配置：SSR + 暗色优先 + API 代理到 Spring Boot
export default defineNuxtConfig({
  compatibilityDate: '2026-01-01',
  devtools: { enabled: false },
  srcDir: 'app/',
  ssr: true,

  modules: ['@nuxtjs/color-mode', '@nuxt/eslint'],
  colorMode: {
    // 暗色优先，class 策略（不带后缀），切换无闪烁
    preference: 'dark',
    fallback: 'dark',
    classSuffix: ''
  },

  vite: {
    plugins: [tailwindcss()]
  },

  css: ['~/assets/css/tokens.css'],

  // 服务端路由（rss/sitemap/og）访问后端的源；容器化部署时指向 backend 服务
  runtimeConfig: {
    apiOrigin: process.env.NUXT_API_ORIGIN || 'http://localhost:8080'
  },

  // API 与上传目录代理：routeRules 同时覆盖浏览器请求与 SSR 内部 $fetch
  // （目标主机可用 NUXT_PROXY_TARGET 覆盖，容器化部署时指向 backend 服务名）
  routeRules: {
    '/api/**': { proxy: `${process.env.NUXT_PROXY_TARGET || 'http://localhost:8080'}/api/**` },
    '/uploads/**': { proxy: `${process.env.NUXT_PROXY_TARGET || 'http://localhost:8080'}/uploads/**` }
  },

  app: {
    head: {
      htmlAttrs: { lang: 'zh-CN' },
      titleTemplate: (title: string | undefined) =>
        title ? `${title} · 智序` : '智序 SMART WORKSPACE',
      meta: [
        { charset: 'utf-8' },
        { name: 'viewport', content: 'width=device-width, initial-scale=1' },
        { name: 'description', content: '智序个人博客：技术文章、作品集与思考记录' }
      ]
    }
  }
})
