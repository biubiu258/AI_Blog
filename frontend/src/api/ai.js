import { fetchEventSource } from '@microsoft/fetch-event-source'

/**
 * AI 生成（POST + JSON 接收 SSE，契约 5.4.6）。
 * 原生 EventSource 不支持 POST/自定义头，这里使用 @microsoft/fetch-event-source。
 *
 * 组装规则（契约 5.4.3）：
 * - delta：多次出现，把 content 追加到本地缓冲
 * - done ：成功终态，用 done.content 整体替换本地缓冲
 * - error：失败终态，显示消息并丢弃缓冲
 *
 * @returns 由调用方持有 AbortController 用于“停止生成”
 */
export function aiGenerate(payload, handlers) {
  const token = localStorage.getItem('token')
  const { onDelta, onDone, onError, onOpen } = handlers

  return fetchEventSource('/api/ai/generate', {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
      Authorization: token ? `Bearer ${token}` : ''
    },
    body: JSON.stringify(payload),
    signal: handlers.signal,
    openWhenHidden: true,
    async onopen(response) {
      const contentType = response.headers.get('content-type') || ''
      if (!response.ok || !contentType.includes('text/event-stream')) {
        // 流建立前失败：统一 JSON 封包 { code, message, data }
        let message = 'AI 服务异常，请稍后重试'
        try {
          const body = await response.json()
          if (body?.message) message = body.message
        } catch {
          /* 非 JSON 错误体 */
        }
        throw new Error(message)
      }
      onOpen?.()
    },
    onmessage(event) {
      if (!event.data) return
      let frame
      try {
        frame = JSON.parse(event.data)
      } catch {
        return
      }
      if (event.event === 'delta') {
        onDelta?.(frame.content ?? '')
      } else if (event.event === 'done') {
        onDone?.(frame.content ?? '')
      } else if (event.event === 'error') {
        onError?.(frame.message || '生成失败，请重试', frame.code)
      }
    },
    onerror(err) {
      // 抛出以阻止默认自动重试，交由调用方处理
      throw err
    }
  })
}
