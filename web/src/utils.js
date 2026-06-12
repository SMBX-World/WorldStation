const getXsrfToken = () => {
  const token = document.cookie
    .split('; ')
    .find(row => row.startsWith('XSRF-TOKEN='));
  return token ? token.split('=')[1] : null;
}

const GAME_VERSION_INFO = {
  "SMBX_38A_145": {
    name: "SMBX-38A 1.4.5",
    short: "1.4.5",
    icon: "/static/smbx-38a.png"
  },
  "SMBX_38A_144": {
    name: "SMBX-38A 1.4.4",
    short: "1.4.4",
    icon: "/static/smbx-38a.png"
  },
  "SMBX_38A_OTHERS": {
    name: "SMBX-38A 早期版本",
    short: "1.4.x",
    icon: "/static/smbx-38a.png"
  },
  "SMBX_2_0": {
    name: "SMBX 2.0",
    short: "2.0",
    icon: "/static/smbx2.png"
  },
  "SMBX_1_3": {
    name: "SMBX 1.3",
    short: "1.3",
    icon: "/static/smbx-legacy.png"
  },
  "SMBX_THEXTECH": {
    name: "TheXTech",
    short: "TheXTech",
    icon: "/static/thextech.png"
  },
  "UNKNOWN": {
    name: "未知版本",
    short: "未知版本",
    icon: "/static/unknown-version.png"
  },
}

const DOWNLOAD_PROVIDER_INFO = {
  "DIRECT_LINK": {
    name: "下载",
    icon: "/static/direct-link.png",
    enabled: true
  },
  "THIRD_PARTY_WEBPAGE": {
    name: "跳转到第三方网页下载",
    icon: "/static/third-party.png",
    enabled: true
  },
  "UNKNOWN": {
    name: "不提供下载",
    icon: "/static/unknown-download.png",
    enabled: false
  }
}

function apiResponse(data, status = 200, statusText = "OK") {
  return {
    ok: status >= 200 && status < 300,
    status,
    statusText,
    text: () => Promise.resolve(JSON.stringify(data)),
    json: () => Promise.resolve(data)
  }
}

async function requestJson(url, options = {}) {
  const headers = {
    ...(options.headers || {}),
    'X-XSRF-TOKEN': getXsrfToken()
  }
  const response = await fetch(url, {
    ...options,
    headers
  })
  const data = await response.json()
  if (!response.ok || data.code !== 0) {
    const error = new Error(data.message || response.statusText || '请求失败')
    error.status = response.status
    error.data = data
    throw error
  }
  return data.data
}

async function sha256Hex(blob) {
  const buffer = await blob.arrayBuffer()
  const hash = await crypto.subtle.digest('SHA-256', buffer)
  return [...new Uint8Array(hash)]
    .map(b => b.toString(16).padStart(2, '0'))
    .join('')
}

function sleep(ms) {
  return new Promise(resolve => setTimeout(resolve, ms))
}

async function uploadChunk(uploadId, chunkIndex, chunk, file, chunkSize, checksum, confirmedBytes, onProgress) {
  return new Promise((resolve, reject) => {
    const xhr = new XMLHttpRequest();

    xhr.open('PUT', `/api/onedrive/uploads/${uploadId}/chunks/${chunkIndex}`, true)

    const xsrfToken = getXsrfToken()
    const start = chunkIndex * chunkSize
    const end = start + chunk.size - 1

    xhr.setRequestHeader('Content-Type', file.type || 'application/octet-stream');
    xhr.setRequestHeader('X-XSRF-TOKEN', xsrfToken)
    xhr.setRequestHeader('Content-Range', `bytes ${start}-${end}/${file.size}`)
    xhr.setRequestHeader('X-Chunk-SHA256', checksum)

    xhr.upload.onprogress = (event) => {
      if (event.lengthComputable) {
        const percentComplete = Math.min(99, Math.round(((confirmedBytes + event.loaded) / file.size) * 99));
        if (onProgress) {
          onProgress(percentComplete);
        }
      }
    };

    xhr.onload = () => {
      let data = null
      try {
        data = JSON.parse(xhr.responseText)
      } catch (_) {
        data = {code: xhr.status, message: '解析JSON失败'}
      }
      if (xhr.status >= 200 && xhr.status < 300) {
        resolve(data);
      } else {
        const error = new Error(data.message || xhr.statusText || '上传分块失败')
        error.status = xhr.status
        error.data = data
        reject(error)
      }
    };

    xhr.onerror = () => {
      reject(new Error('上传请求失败'));
    };

    xhr.send(chunk);
  });
}

async function uploadChunkWithRetry(uploadId, chunkIndex, chunk, file, chunkSize, confirmedBytes, onProgress) {
  let lastError = null
  for (let attempt = 0; attempt < 3; attempt++) {
    const checksum = await sha256Hex(chunk)
    try {
      return await uploadChunk(uploadId, chunkIndex, chunk, file, chunkSize, checksum, confirmedBytes, onProgress)
    } catch (error) {
      lastError = error
      if (error.status !== 422) {
        throw error
      }
      await sleep(500 * (attempt + 1))
    }
  }
  throw lastError
}

async function waitUploadCompleted(uploadId, onProgress) {
  while (true) {
    const status = await requestJson(`/api/onedrive/uploads/${uploadId}`)
    if (status.status === 'COMPLETED') {
      if (onProgress) onProgress(100)
      return status.finalUrl
    }
    if (status.status === 'FAILED' || status.status === 'ABORTED' || status.status === 'EXPIRED') {
      throw new Error(status.error || '服务器处理文件失败')
    }
    if (onProgress) {
      const processingProgress = status.totalChunks > 0
        ? Math.min(99, Math.round(99 + (status.savedChunkCount / status.totalChunks)))
        : 99
      onProgress(processingProgress)
    }
    await sleep(2000)
  }
}

async function uploadFile(file, fileName, uploadKind, onProgress) {
  try {
    if (onProgress) onProgress(0)

    const session = await requestJson('/api/onedrive/uploads', {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json'
      },
      body: JSON.stringify({
        uploadKind,
        fileName,
        contentType: file.type || 'application/octet-stream',
        totalSize: file.size
      })
    })

    const receivedChunks = new Set(session.receivedChunks || [])
    let confirmedBytes = 0
    for (const index of receivedChunks) {
      const start = index * session.chunkSize
      const end = Math.min(start + session.chunkSize, file.size)
      confirmedBytes += end - start
    }

    for (let index = 0; index < session.totalChunks; index++) {
      const start = index * session.chunkSize
      const end = Math.min(start + session.chunkSize, file.size)
      if (receivedChunks.has(index)) {
        if (onProgress) onProgress(Math.min(99, Math.round((confirmedBytes / file.size) * 99)))
        continue
      }

      const chunk = file.slice(start, end)
      await uploadChunkWithRetry(
        session.uploadId,
        index,
        chunk,
        file,
        session.chunkSize,
        confirmedBytes,
        onProgress
      )
      confirmedBytes += chunk.size
      if (onProgress) onProgress(Math.min(99, Math.round((confirmedBytes / file.size) * 99)))
    }

    const completed = await requestJson(`/api/onedrive/uploads/${session.uploadId}/complete`, {
      method: 'POST'
    })

    if (completed.status === 'COMPLETED') {
      if (onProgress) onProgress(100)
      return apiResponse({code: 0, message: '文件上传成功', data: completed.finalUrl})
    }

    if (onProgress) onProgress(99)
    const finalUrl = await waitUploadCompleted(session.uploadId, onProgress)
    return apiResponse({code: 0, message: '文件上传成功', data: finalUrl})
  } catch (error) {
    return apiResponse({
      code: error.status || 500,
      message: error.message || '上传失败',
      data: null
    }, error.status || 500, error.message || '上传失败')
  }
}

export {
  getXsrfToken,
  GAME_VERSION_INFO,
  DOWNLOAD_PROVIDER_INFO,
  uploadFile
}
