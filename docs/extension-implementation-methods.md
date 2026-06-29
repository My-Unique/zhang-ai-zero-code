# 扩展功能实现方法

本文档记录几个后续扩展功能的实现方式，目标是先固定技术边界和落点，避免后续直接改代码时把创建、修改、展示、上传等流程混在一起。

## 1. 创建和修改分离

### 目标

创建应用和修改应用使用不同的提示词、工具集和 AI Service 调用路径。

创建应用关注从零生成完整项目；修改应用关注基于当前版本做最小变更，避免模型复读历史内容、误改全站或不调用工具。

### 现状

- 入口在 `AppServiceImpl.chatToGenCode`。
- AI 调用在 `AiCodeGeneratorFacade.generateAndSaveCodeStream`。
- Vue 项目目前统一走 `AiCodeGeneratorService.generateVueProjectCodeStream`。
- Vue 项目工具统一由 `AiCodeGeneratorServiceFactory` 注入 `toolManager.getAllTools(versionNo)`。
- 当前修改流程已经会先准备新版本目录，并要求工具至少成功执行一次写入、修改或删除。

### 实现方法

后端新增生成模式：

```java
public enum AppGenerationModeEnum {
    CREATE,
    MODIFY
}
```

模式判断建议放在 `AppServiceImpl.chatToGenCode`：

- `newVersionNo == 1`：创建模式。
- `newVersionNo > 1`：修改模式。

`AiCodeGeneratorService` 拆出两个 Vue 方法：

```java
@SystemMessage(fromResource = "prompt/codegen-vue-project-create-system-prompt.txt")
@UserMessage("{{userMessage}}")
TokenStream generateVueProjectCreateStream(@MemoryId long appId, @V("userMessage") String userMessage);

@SystemMessage(fromResource = "prompt/codegen-vue-project-modify-system-prompt.txt")
@UserMessage("{{userMessage}}")
TokenStream generateVueProjectModifyStream(@MemoryId long appId, @V("userMessage") String userMessage);
```

`AiCodeGeneratorFacade` 按模式选择调用方法：

- 创建模式：调用 `generateVueProjectCreateStream`。
- 修改模式：调用 `generateVueProjectModifyStream`。

`ToolManager` 按模式返回工具：

- 创建模式：`writeFile`、`readDir`，必要时保留 `readFile`。
- 修改模式：`readDir`、`readFile`、`modifyFile`、`writeFile`、`deleteFile`。

修改模式提示词必须强调：

- 一切修改前必须先 `readFile`。
- 能 `modifyFile` 就不要整文件 `writeFile`。
- 如果用户提供了选中元素选择器，只修改该元素相关代码。
- 不要补做历史需求。
- 不要把说明文字写入代码文件。

### 验收标准

- 首次生成时可以完整生成 Vue 项目。
- 后续修改时日志中能看到先 `readFile`，再 `modifyFile` 或 `writeFile`。
- 用户只要求改一个文案时，不应重写整个项目。
- 模型无法判断修改目标时，应回复澄清问题，不应写入澄清文本到代码文件。

### 当前落地状态

已新增 `AppGenerationModeEnum`，并按版本号区分创建和修改：

- `newVersionNo <= 1` 使用创建模式。
- `newVersionNo > 1` 使用修改模式。

Vue 生成已拆成两个 AI Service 方法：

- `generateVueProjectCreateStream`
- `generateVueProjectModifyStream`

对应提示词：

- `prompt/codegen-vue-project-create-system-prompt.txt`
- `prompt/codegen-vue-project-modify-system-prompt.txt`

工具集合也已按模式区分：

- 创建模式：`writeFile`、`readDir`
- 修改模式：`writeFile`、`readFile`、`modifyFile`、`readDir`、`deleteFile`

## 2. 工具流式输出边界

### 目标

明确当前工具输出和“完整工具参数流式输出”的区别，避免误以为已经实现了参数级流式解析。

### 现状

当前实现是按事件流式输出：

- `AI_RESPONSE`：AI 普通文本片段。
- `TOOL_REQUEST`：工具选择事件。
- `TOOL_EXECUTED`：工具执行完成事件。

核心位置：

- `AiCodeGeneratorFacade.processTokenStream`
- `JsonMessageStreamHandler.handleJsonMessageChunk`
- `StreamHandlerExecutor.doExecute`

这意味着前端可以实时看到：

```text
[选择工具] 读取文件
[工具调用] 读取文件 src/pages/Home.vue
[选择工具] 修改文件
[工具调用] 修改文件 src/pages/Home.vue
```

但工具参数不是逐字段实时展示。比如 `writeFile.arguments.content` 通常是等工具参数完整、工具执行完成后，才格式化展示。

### 不推荐立即实现参数级流式解析

参数级流式解析需要处理：

- 半截 JSON。
- 字符串转义。
- 多工具并发。
- `oldContent`、`newContent`、`content` 的大文本。
- 字段值刚好跨 chunk 被截断。
- 模型输出异常 JSON 时的恢复策略。

如果要实现，建议单独增加 `ToolArgumentStreamAccumulator`，不要写在 `JsonMessageStreamHandler` 里。

### 推荐方案

先保持当前事件级流式输出，只优化前端展示样式。

对于 `writeFile`、`modifyFile` 这类工具，后端继续返回结构化工具结果，前端按工具名渲染为卡片、文件标签和代码区域。

## 3. 优化代码展示效果

### 目标

生成代码时，不在聊天区展示大段源码，而是在右侧代码区域或生成过程区域展示文件和代码。

聊天区只展示简短过程：

```text
STEP 1: 修改 src/pages/Home.vue
STEP 2: 修改 src/styles/main.css
```

代码区以 Tab 或文件列表形式展示文件内容。

### 现状

- 聊天内容由 `ChatMessageContent.vue` 渲染。
- 右侧已有 `VersionCodeWorkspace.vue`，支持版本、文件树、查看、编辑、对比、回滚。
- `AppChatPage.vue` 里已有 `rightPanelMode`，可以在预览和代码之间切换。

### 实现方法

后端新增结构化工具输出字段，建议在现有文本流之外新增轻量协议：

```json
{
  "type": "tool_result",
  "toolName": "modifyFile",
  "displayName": "修改文件",
  "filePath": "src/pages/Home.vue",
  "oldContent": "...",
  "newContent": "..."
}
```

短期也可以保持当前文本流，前端用规则解析：

- `[工具调用] 写入文件 xxx`
- `[工具调用] 修改文件 xxx`
- 代码块内容

但长期建议使用结构化 JSON，避免 Markdown 解析不稳定。

前端新增生成代码展示状态：

```ts
type GeneratedFilePanel = {
  path: string
  language: string
  content: string
  action: 'write' | 'modify' | 'delete' | 'read'
}
```

在 `AppChatPage.vue` 中：

- 收到工具结果后更新 `generatedFiles`。
- 自动把 `rightPanelMode` 切到 `code` 或新增 `generationCode` 模式。
- 用 Tab 展示多个文件。
- 聊天区只保留工具摘要，不展示完整源码。

推荐复用 `VersionCodeWorkspace` 的布局风格，但不要直接复用版本编辑逻辑。实时生成过程中的代码展示是临时态；版本保存完成后，再切换到正式版本文件读取接口。

### 验收标准

- 生成多个文件时，前端能以 Tab 或文件列表展示。
- 聊天区不再被大段代码刷屏。
- 工具调用摘要仍能保留在聊天历史里。
- 版本保存完成后，代码区能加载最新版本文件。

### 当前落地状态

已在 `AppChatPage.vue` 增加生成过程代码展示面板：

- 新增右侧 `生成` 模式。
- Vue 项目生成时自动切换到 `生成` 模式。
- 前端从工具输出中解析 `[工具调用] 写入文件 ...` 和 `[工具调用] 修改文件 ...`。
- 解析到文件路径和代码块后，在右侧按文件列表展示生成中的代码。
- 版本保存完成后仍会继续构建预览，并自动回到预览模式。

当前实现优先满足“生成时右边显示生成代码”。聊天区暂时仍保留原工具输出，后续可以在结构化流协议稳定后再改成只展示摘要。

## 4. 支持多媒体上传

### 目标

用户可以上传图片或文本文件，把文件信息作为需求上下文交给 AI。

图片类文件用于页面素材或用户描述辅助；文本类文件可以读取内容并拼进 Prompt。

### 后端实现方法

新增上传接口，例如：

```text
POST /app/assets/upload
```

请求使用 `multipart/form-data`：

```java
@PostMapping("/assets/upload")
public BaseResponse<AppAssetVO> uploadAppAsset(
        @RequestParam Long appId,
        @RequestPart MultipartFile file,
        HttpServletRequest request
)
```

返回结构：

```json
{
  "url": "https://...",
  "name": "banner.png",
  "contentType": "image/png",
  "size": 12345,
  "textContent": null
}
```

处理规则：

- 图片：上传到 COS，返回图片 URL。
- 文本：限制大小后读取内容，返回 `textContent`。
- docx：可先不支持，或后续用专门解析器提取正文。
- 所有文件都要校验大小、类型、登录用户和 app 权限。

建议新增：

- `AppAssetUploadRequest` 或直接使用 `MultipartFile`。
- `AppAssetVO`。
- `AppAssetService`。

图片上传后，Prompt 拼接方式：

```text
用户上传了图片素材：
- 文件名：xxx.png
- 图片 URL：https://...

如果模型不能直接识别图片内容，请只把该图片作为页面素材使用；
如果用户要求识别图片文字，需要用户明确提供图片中的文字内容。
```

文本上传后，Prompt 拼接方式：

```text
用户上传了文本文件：
<uploaded_file name="需求.md">
...
</uploaded_file>
```

### 前端实现方法

在 `AppChatPage.vue` 的输入区增加上传入口。

建议交互：

- 使用 Ant Design Vue `a-upload`。
- 上传成功后在输入框上方展示附件 chip。
- 发送消息时，把附件信息和用户输入一起提交。

因为当前 SSE 接口是 GET：

```text
GET /app/chat/gen/code?appId=xxx&message=xxx
```

上传文件后不建议直接把大文本塞进 URL。推荐后续把生成接口改为 POST SSE，或者先只把上传后的 URL、附件 id 拼入 message。

短期兼容方案：

- 图片：上传后只把 URL 拼进 message。
- 小文本：前端读取内容后拼进 message，但要限制长度。

长期推荐方案：

- 新增生成请求表 `generation_context` 或附件临时缓存。
- 前端发送附件 id。
- 后端生成时读取附件内容并拼 Prompt。

### 验收标准

- 上传图片后，AI 能把图片 URL 用作页面素材。
- 上传 txt/md 后，AI 能读取文本内容并根据内容生成页面。
- 文件过大或类型不支持时，有明确错误提示。
- appId 始终保持字符串传递，前端不转 Number。

### 当前落地状态

已新增后端附件上传接口：

```text
POST /app/assets/upload?appId={appId}
```

当前规则：

- 图片上传到 COS，返回图片 URL。
- `txt`、`md`、`json`、`csv`、`yml`、`yaml` 作为文本附件读取内容返回。
- 单个附件最大 10MB。
- 文本附件最大 256KB。
- 上传前校验登录用户是否有应用权限。

前端 `AppChatPage.vue` 已增加附件入口：

- 输入框下方可以选择图片或文本附件。
- 上传成功后显示附件 chip。
- 发送消息时把图片 URL 或文本内容拼入本次 Prompt。
- 前端仍保持 `appId` 字符串传递。

## 5. 优化代码编辑效果

### 目标

让用户在代码区修改生成结果时体验更接近编辑器。

### 现状

`VersionCodeWorkspace.vue` 当前使用 `textarea` 展示和编辑代码，已经具备保存为新版本、对比、回滚能力。

### 实现方法

后续可以替换为 Monaco Editor：

- 只在代码模式懒加载。
- 根据文件后缀设置语言。
- 支持只读、编辑、格式化、搜索。
- 保存时沿用现有 `saveVersionFile` 接口。

如果暂不引入 Monaco，可以先优化当前 `textarea`：

- 增加行号。
- 增加复制按钮。
- 保存前显示 diff。
- 大文件只读，避免浏览器卡顿。

### 验收标准

- 代码编辑区不影响预览区性能。
- 保存后生成新版本。
- appId 不转 Number。
- 大文件有保护提示。

## 推荐落地顺序

1. 创建和修改分离。
2. 上传接口和附件 Prompt 拼接。
3. 工具结果结构化输出。
4. 前端生成代码 Tab 展示。
5. 代码编辑器增强。
6. 最后再考虑工具参数级流式解析。

## 风险点

- 生成接口当前是 GET SSE，不适合携带大附件上下文。
- 参数级工具流式解析复杂度高，不建议和其他功能一起改。
- 前端不能把 `appId` 转成 Number，否则会丢精度。
- 创建模式和修改模式如果共用历史记忆，容易导致模型复读旧代码或旧说明。
- 上传图片不等于模型具备视觉识别能力，必须在产品文案和 Prompt 中区分“作为素材使用”和“识别图片内容”。
