import { useId, useState } from 'react'
import { createPortal } from 'react-dom'

const HINT_GAP_PX = 8

/**
 * A table header that explains itself on hover or keyboard focus. The hint is drawn on the page body
 * at the header's place, so a scrolling table or an animated card around it can't clip or shift it.
 */
export function HeaderHint({ label, title, children, className = '' }) {
  const [hint, setHint] = useState({ visible: false, position: undefined })
  const hintId = useId()

  const show = (event) => {
    const box = event.currentTarget.getBoundingClientRect()
    setHint({
      visible: true,
      position: { top: box.bottom + HINT_GAP_PX, right: window.innerWidth - box.right },
    })
  }
  const hide = () => setHint({ ...hint, visible: false })

  return (
    <th scope="col" className={className}>
      <span
        className="header-hint"
        tabIndex={0}
        aria-describedby={hintId}
        onPointerEnter={show}
        onPointerLeave={hide}
        onFocus={show}
        onBlur={hide}
      >
        {label}
      </span>
      {createPortal(
        <span
          id={hintId}
          role="tooltip"
          className={`header-hint-popup ${hint.visible ? 'is-visible' : ''}`}
          style={hint.position}
        >
          <strong>{title}</strong>
          {children}
        </span>,
        document.body,
      )}
    </th>
  )
}
