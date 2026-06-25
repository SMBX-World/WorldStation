const CACHE_NAME = 'smbx-world-static-cache-v2'
const BUNDLE_URL = '__BUNDLE_URL__'
const MAGIC = 'sMbXwRlD'

self.addEventListener('install', event => {
  event.waitUntil(self.skipWaiting())
})

self.addEventListener('activate', event => {
  event.waitUntil(
    caches.keys().then(keys => {
      return Promise.all(
        keys.filter(key => key !== CACHE_NAME).map(key => caches.delete(key))
      )
    }).then(() => self.clients.claim())
  )
})

self.addEventListener('fetch', event => {
  const [pathname, search] = (() => {
    const u = new URL(event.request.url)
    return [u.pathname, u.search]
  })()

  if (event.request.method !== "GET" || !pathname.startsWith('/static/')) {
    // 不是我想要的静态资源，直接返回
    return
  }

  if (search.endsWith('import')) {
    // Vite 开发服务器
    return
  }


  event.respondWith((async () => {
    const cache = await caches.open(CACHE_NAME)
    const cachedResponse = await cache.match(event.request)

    if (cachedResponse) {
      // 缓存好了单个文件
      return cachedResponse
    }

    // 没有单个文件的缓存，从资源包中获取
    let bundleResponse = await cache.match(BUNDLE_URL)

    if (!bundleResponse) {
      // 资源包没下载，现在下载
      bundleResponse = await fetch(BUNDLE_URL)
      if (!bundleResponse.ok) {
        // Vite 开发模式，没有资源包，返回散件
        return fetch(event.request)
      }
      await cache.put(BUNDLE_URL, bundleResponse.clone())
    }

    const bundle = new Uint8Array(await bundleResponse.arrayBuffer())
    const blob = extract(bundle, pathname)
    const mime = guessMime(pathname)
    if (!blob) {
      // bundle 中找不到，回退到网络请求（新文件未打包的场景）
      return fetch(event.request)
    }

    const resp = new Response(blob, {
      headers: {
        'Content-Type': mime,
      }
    })
    await cache.put(event.request, resp.clone())
    return resp
  })())
})

// 资源包相关
let index = null  // {filename: {d: offset, l: length}
let arr = null

function int4le(arr, offset) {
  return arr[offset] | (arr[offset + 1] << 8) | (arr[offset + 2] << 16) | (arr[offset + 3] << 24)
}

function extract(bundle, pathname) { // -> blob
  if (index == null || arr == null) {
    // 第一次加载资源包
    // console.log("解析资源包")
    const decoder = new TextDecoder('utf-8')

    const magic = decoder.decode(bundle.subarray(0, 8))
    if (magic !== MAGIC) {
      throw new Error("资源包格式错误")
    }

    const indexLength = int4le(bundle, 8)
    index = JSON.parse(decoder.decode(bundle.subarray(12, 12 + indexLength)))

    const arrLength = int4le(bundle, 12 + indexLength)
    arr = bundle.subarray(16 + indexLength, 16 + indexLength + arrLength)
  }

  const filename = pathname.slice(8) // 去掉 /static/
  const entry = index[filename]
  if (!entry) return null

  const offset = entry.d
  const length = entry.l
  // console.log(`提取 ${filename}，偏移: ${offset}, 长度: ${length}`)
  return new Blob([arr.subarray(offset, offset + length)])
}

function guessMime(pathname) {
  const ext = pathname.split('.').pop().toLowerCase()
  switch (ext) {
    case 'png': return 'image/png'
    case 'gif': return 'image/gif'
    case 'webp': return 'image/webp'
  }
}
