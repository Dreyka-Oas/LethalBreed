export default function JsonHighlight({ code }) {
  const highlighted = code
    .replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;')
    .replace(/"([^"]+)"(\s*:)/g, '<span class="k">"$1"</span>$2')
    .replace(/:\s*"([^"]*)"/g, ': <span class="s">"$1"</span>')
    .replace(/:\s*(-?\d+\.?\d*)/g, ': <span class="n">$1</span>')
    .replace(/:\s*(true|false)/g, ': <span class="b">$1</span>')
    .replace(/([{}[\],])/g, '<span class="p">$1</span>')

  return (
    <pre
      className="code-block"
      dangerouslySetInnerHTML={{ __html: highlighted }}
      style={{ margin: 0, fontSize: 12.5 }}
    />
  )
}
