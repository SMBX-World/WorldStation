import { defineConfig } from 'vite'
import fs from 'fs'
import path from 'path'
import vue from '@vitejs/plugin-vue'
import esbuild from 'esbuild'

// https://vite.dev/config/
export default defineConfig({
  plugins: [
    vue(),
    {
      name: 'minify-sw',
      apply: 'build',
      async closeBundle() {
        const distDir = path.resolve(__dirname, 'dist')
        const swPath = path.join(distDir, 'sw.js')

        if (fs.existsSync(swPath)) {
          const code = fs.readFileSync(swPath, 'utf-8')

          // 使用 esbuild 压缩
          const result = await esbuild.transform(code, {
            minify: true,
            loader: 'js',
          })

          fs.writeFileSync(swPath, result.code)
          console.log('[vite-plugin] sw.js 压缩完成')
        }
      }
    }
  ],
  server: {
    proxy: {
      // 完整选项
      '/api': {
        target: 'http://localhost:8080',
        changeOrigin: true,
        cookieDomainRewrite: 'localhost'
      },

    }
  },
  build: {
    cssCodeSplit: false,
    rollupOptions: {
      output: {
        manualChunks: (id) => {
          if (id.includes('node_modules')) {
            if (id.includes('/vue') || id.includes('/@vue+')) {
              return 'vue'
            } else if (id.includes('marked')) {
              return 'marked'
            }
            return 'vendor'
          } else if (id.includes('.vue')) {
            return 'components'
          } else if (id.includes('static/')) {
            return 'static'
          }
          return null
        }
      },
    }
  }
})
