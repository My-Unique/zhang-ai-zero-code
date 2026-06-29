export type VisualEditElementInfo = {
  tagName: string
  id: string
  className: string
  textContent: string
  text: string
  selector: string
  pagePath: string
  rect: {
    width: number
    height: number
    top: number
    left: number
  }
}

export type VisualEditOverlayRect = {
  top: number
  left: number
  width: number
  height: number
}

export type VisualEditTarget = {
  element: Element
  info: VisualEditElementInfo
  overlayRect: VisualEditOverlayRect
}

type VisualEditMessage = {
  type: typeof VISUAL_EDIT_MESSAGE_TYPE
  element: VisualEditElementInfo
}

const VISUAL_EDIT_MESSAGE_TYPE = 'ZHANG_AI_VISUAL_EDIT_ELEMENT_SELECTED'
const STYLE_ID = 'zhang-ai-visual-edit-style'
const HOVER_CLASS = 'zhang-ai-visual-edit-hover'
const SELECTED_CLASS = 'zhang-ai-visual-edit-selected'
const CLEANUP_KEY = '__zhangAiVisualEditCleanup__'

type VisualEditWindow = Window & {
  [CLEANUP_KEY]?: () => void
}

const getElementText = (element: Element) => {
  return (element.textContent || '').replace(/\s+/g, ' ').trim().slice(0, 120)
}

const getElementSelector = (element: Element) => {
  const parts: string[] = []
  let current: Element | null = element

  while (current && current.nodeType === Node.ELEMENT_NODE && parts.length < 5) {
    const tagName = current.tagName.toLowerCase()
    if (current.id) {
      parts.unshift(`${tagName}#${CSS.escape(current.id)}`)
      break
    }

    const classList = Array.from(current.classList)
      .filter((className) => !className.startsWith('zhang-ai-visual-edit-'))
      .slice(0, 3)
    const classSelector = classList.map((className) => `.${CSS.escape(className)}`).join('')
    const parent = current.parentElement
    const sameTagIndex = parent
      ? Array.from(parent.children).filter((child) => child.tagName === current?.tagName).indexOf(current) + 1
      : 0
    const nthSelector = sameTagIndex > 1 ? `:nth-of-type(${sameTagIndex})` : ''

    parts.unshift(`${tagName}${classSelector || nthSelector}`)
    current = current.parentElement
  }

  return parts.join(' > ')
}

const getElementInfo = (element: Element): VisualEditElementInfo => {
  const rect = element.getBoundingClientRect()
  const textContent = getElementText(element)
  return {
    tagName: element.tagName.toLowerCase(),
    id: element.id || '',
    className: Array.from(element.classList)
      .filter((className) => !className.startsWith('zhang-ai-visual-edit-'))
      .join(' '),
    textContent,
    text: textContent,
    selector: getElementSelector(element),
    pagePath: element.ownerDocument.location?.pathname || '',
    rect: {
      width: Math.round(rect.width),
      height: Math.round(rect.height),
      top: Math.round(rect.top),
      left: Math.round(rect.left),
    },
  }
}

const getSameOriginDocument = (iframe: HTMLIFrameElement) => {
  try {
    return iframe.contentDocument
  } catch {
    throw new Error('预览页面不是同源地址，无法进入可视化编辑模式')
  }
}

export const getVisualEditTargetFromPoint = (
  iframe: HTMLIFrameElement,
  viewport: HTMLElement,
  clientX: number,
  clientY: number,
): VisualEditTarget | undefined => {
  const doc = getSameOriginDocument(iframe)
  if (!doc?.body) {
    throw new Error('预览页面尚未加载完成')
  }

  const iframeRect = iframe.getBoundingClientRect()
  const viewportRect = viewport.getBoundingClientRect()
  const scale = iframeRect.width / iframe.offsetWidth || 1
  const iframeX = (clientX - iframeRect.left) / scale
  const iframeY = (clientY - iframeRect.top) / scale

  if (iframeX < 0 || iframeY < 0 || iframeX > iframe.offsetWidth || iframeY > iframe.offsetHeight) {
    return undefined
  }

  const element = doc.elementFromPoint(iframeX, iframeY)
  if (!element || element === doc.documentElement || element === doc.body) {
    return undefined
  }

  const elementRect = element.getBoundingClientRect()
  return {
    element,
    info: getElementInfo(element),
    overlayRect: {
      top: iframeRect.top - viewportRect.top + elementRect.top * scale,
      left: iframeRect.left - viewportRect.left + elementRect.left * scale,
      width: elementRect.width * scale,
      height: elementRect.height * scale,
    },
  }
}

const injectVisualEditStyle = (doc: Document) => {
  if (doc.getElementById(STYLE_ID)) {
    return
  }

  const style = doc.createElement('style')
  style.id = STYLE_ID
  style.textContent = `
    .${HOVER_CLASS} {
      outline: 2px dashed #7c8cff !important;
      outline-offset: 2px !important;
      cursor: crosshair !important;
    }

    .${SELECTED_CLASS} {
      outline: 3px solid #3949f5 !important;
      outline-offset: 2px !important;
      box-shadow: 0 0 0 4px rgb(57 73 245 / 18%) !important;
    }
  `
  doc.head.appendChild(style)
}

export const enableVisualEdit = (iframe: HTMLIFrameElement) => {
  const iframeWindow = iframe.contentWindow as VisualEditWindow | null
  const doc = getSameOriginDocument(iframe)
  if (!iframeWindow || !doc?.body) {
    throw new Error('预览页面尚未加载完成')
  }

  iframeWindow[CLEANUP_KEY]?.()
  injectVisualEditStyle(doc)

  let hoveredElement: Element | null = null
  let selectedElement: Element | null = null

  const setHoveredElement = (element: Element | null) => {
    if (hoveredElement && hoveredElement !== selectedElement) {
      hoveredElement.classList.remove(HOVER_CLASS)
    }
    hoveredElement = element
    if (hoveredElement && hoveredElement !== selectedElement) {
      hoveredElement.classList.add(HOVER_CLASS)
    }
  }

  const handleMouseOver = (event: MouseEvent) => {
    const target = event.target
    if (target instanceof Element && target !== doc.documentElement && target !== doc.body) {
      setHoveredElement(target)
    }
  }

  const handleMouseOut = (event: MouseEvent) => {
    const relatedTarget = event.relatedTarget
    if (!relatedTarget || !(relatedTarget instanceof Node) || !doc.contains(relatedTarget)) {
      setHoveredElement(null)
    }
  }

  const handleClick = (event: MouseEvent) => {
    const target = event.target
    if (!(target instanceof Element) || target === doc.documentElement || target === doc.body) {
      return
    }

    event.preventDefault()
    event.stopPropagation()

    selectedElement?.classList.remove(SELECTED_CLASS)
    selectedElement = target
    selectedElement.classList.remove(HOVER_CLASS)
    selectedElement.classList.add(SELECTED_CLASS)

    iframeWindow.parent.postMessage(
      {
        type: VISUAL_EDIT_MESSAGE_TYPE,
        element: getElementInfo(selectedElement),
      } satisfies VisualEditMessage,
      window.location.origin,
    )
  }

  doc.addEventListener('mouseover', handleMouseOver, true)
  doc.addEventListener('mouseout', handleMouseOut, true)
  doc.addEventListener('click', handleClick, true)

  const cleanup = () => {
    doc.removeEventListener('mouseover', handleMouseOver, true)
    doc.removeEventListener('mouseout', handleMouseOut, true)
    doc.removeEventListener('click', handleClick, true)
    hoveredElement?.classList.remove(HOVER_CLASS)
    selectedElement?.classList.remove(SELECTED_CLASS)
    doc.getElementById(STYLE_ID)?.remove()
    iframeWindow[CLEANUP_KEY] = undefined
  }

  iframeWindow[CLEANUP_KEY] = cleanup
  return cleanup
}

export const listenVisualEditSelection = (callback: (element: VisualEditElementInfo) => void) => {
  const handleMessage = (event: MessageEvent<VisualEditMessage>) => {
    if (event.origin !== window.location.origin || event.data?.type !== VISUAL_EDIT_MESSAGE_TYPE) {
      return
    }
    callback(event.data.element)
  }

  window.addEventListener('message', handleMessage)
  return () => window.removeEventListener('message', handleMessage)
}

export const formatVisualEditElement = (element: VisualEditElementInfo) => {
  const lines = [
    `标签: ${element.tagName}`,
    `选择器: ${element.selector}`,
    `尺寸: ${element.rect.width} x ${element.rect.height}`,
  ]

  if (element.id) {
    lines.push(`id: ${element.id}`)
  }
  if (element.className) {
    lines.push(`class: ${element.className}`)
  }
  if (element.pagePath) {
    lines.push(`页面路径: ${element.pagePath}`)
  }
  if (element.textContent || element.text) {
    lines.push(`文本: ${element.textContent || element.text}`)
  }

  return lines.join('\n')
}
