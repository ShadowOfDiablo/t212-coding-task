import { useEffect } from 'react'

/** While `open`, a press outside `ref` or the Escape key calls `onDismiss`. */
export function useDismiss(ref, open, onDismiss) {
  useEffect(() => {
    if (!open) {
      return undefined
    }
    const dismissOutside = (event) => {
      if (!ref.current?.contains(event.target)) {
        onDismiss()
      }
    }
    const dismissOnEscape = (event) => event.key === 'Escape' && onDismiss()
    document.addEventListener('pointerdown', dismissOutside)
    document.addEventListener('keydown', dismissOnEscape)
    return () => {
      document.removeEventListener('pointerdown', dismissOutside)
      document.removeEventListener('keydown', dismissOnEscape)
    }
  }, [ref, open, onDismiss])
}
