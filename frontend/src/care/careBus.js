// 极简事件总线：发布响应 → 关怀呈现组件（CareNote / CareDialog）解耦
const listeners = new Set()

export function onCare(fn) {
  listeners.add(fn)
  return () => offCare(fn)
}

export function offCare(fn) {
  listeners.delete(fn)
}

export function emitCare(payload) {
  listeners.forEach((fn) => fn(payload))
}
