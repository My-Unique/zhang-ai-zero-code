const codeGenTypeLabelMap: Record<string, string> = {
  html: 'HTML 单文件',
  multi_file: '原生多文件',
  vue_project: 'Vue 多文件',
}

export const formatCodeGenType = (codeGenType?: string) => {
  if (!codeGenType) {
    return 'AI 应用'
  }
  return codeGenTypeLabelMap[codeGenType] || codeGenType
}
