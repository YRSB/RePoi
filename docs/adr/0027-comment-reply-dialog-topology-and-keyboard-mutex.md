# 评论回复面板纵向拓扑重构与软键盘表情互斥联动 (Comment Reply Dialog Topology & Keyboard-Emoji Mutex)

- 状态：已采纳 (Accepted) - 2026-11-19
- 修订/取代：优化 [CommentSection.kt](../../shared/src/commonMain/kotlin/com/nobody/repoi/ui/common/comment/CommentSection.kt) 的回复输入弹窗设计

## 背景与上下文 (Context)

在移动端屏幕（特别是软键盘弹起后可视高度仅剩 300~400dp 的场景）下，原评论回复浮层（`CommentReplyDialog`）存在两处严重的交互与布局缺陷：

1. **输入框默认行数过多**：输入框采用硬编码 `heightIn(min = 90.dp, max = 160.dp)`，初次打开或无内容时即强占近 4~5 行的空白高度，挤占了主楼上下文与操作可视区域；
2. **表情展开挤压操作栏**：原有布局顺序为 `[输入框] -> [已选表情] -> [表情面板] -> [操作栏(表情+发送按钮)]`。由于操作栏位于表情面板下方，当用户点击表情图标展开 220dp 的表情区时，操作栏被整体推挤至系统软键盘下方或屏幕可视区域外，导致用户无法点击“发送”且丢失表情切换入口；
3. **软键盘与表情面板缺乏互斥**：点击表情按钮时仅异步通知软键盘隐藏，在软键盘尚未完成收起动画或焦点未释放时，表情面板即开始渲染，造成两者高度叠加并拉爆视口高度。

## 架构决策 (Decision)

1. **重构纵向层级拓扑（操作栏置于面板上方）**：
   将操作工具栏提升至表情面板插槽上方，严格按以下从上至下拓扑排布：
   - 顶部把手（Handle bar）
   - 回复目标提示（`回复 @xxx`）
   - 紧凑自适应输入框
   - 已选表情贴纸横向预览条（`selectedStickers`，未选时不占高）
   - **悬浮操作工具栏**（左侧 😊 表情按钮与 ⌨️ 键盘按钮双常驻，右侧胶囊“发送”按钮）
   - **底部辅助面板插槽**（表情面板展开时在此呈现；表情面板关闭且软键盘升起时，由系统软键盘占用）
   此举确保操作栏与发送按钮始终紧贴在用户输入内容正下方，彻底免疫底部面板的高度变化，永不沉底。

2. **自适应紧凑输入框**：
   废弃固定 90dp 最小高度，采用 `heightIn(min = 44.dp, max = 120.dp)`：初始默认呈现紧凑单行/双行（44dp），随用户输入行数增加自然向下撑开，达到 120dp（约 4~5 行）后在内部垂直平滑滚动。

3. **操作栏按钮常驻与软键盘/表情互斥联动**：
   - **表情按钮 😊（常驻）**：点击时若表情面板已开则收起；若未开则自动收起软键盘并显式执行 `focusManager.clearFocus()` 清除输入框焦点，展开表情面板，激活时高亮主色；
   - **键盘按钮 ⌨️（常驻）与状态精确管理**：通过 `isKeyboardActive` 显式追踪软键盘活跃状态，解决 Android 子 Window 内部 `WindowInsets.ime.getBottom()` 恒为 0 导致无法识别键盘展开、点击键盘按钮无响应的问题；点击时若在表情面板则切回键盘，若已处于键盘模式则收起软键盘并清除焦点，若未激活则重新获焦呼起键盘；
   - **抽屉展开就位后再呼起键盘（杜绝二段跳）**：通过 `snapshotFlow { sheetState.currentValue }.filter { it == SheetValue.Expanded }.first()` 监听抽屉动画，在 BottomSheet 平滑滑入到位后再激活焦点拉起软键盘，彻底消除软键盘抢先弹出造成的画面剧烈跳动；
   - **光标与提示词（Hint）像素级竖直对齐**：统一采用 `MaterialTheme.typography.bodyMedium.copy(fontSize = 15.sp, lineHeight = 22.sp)` 样式，并在 `decorationBox` 中采用 `Box(contentAlignment = Alignment.CenterStart)` 居中承载，消除光标与 Hint 文本因字偶行高指标不一致引起的竖直偏置；
   - **点击输入框**：自动收起表情面板，重置并重新获得焦点呼起软键盘；`BasicTextField` 挂载 `onFocusChanged`，一旦聚焦自动收起表情面板并标记 `isKeyboardActive = true`；
   - **全面迁移至 Material 3 官方 `ModalBottomSheet` 消除子 Window 遮罩与焦点缺陷**：
     此前尝试使用 Compose `Dialog` + 自定义半透明 Window 垫片（`ReplyDialogEdgeToEdgeEffect`），导致原生子 Window 与 Activity Window 在 Inset 分发、焦点竞争以及 `FLAG_DIM_BEHIND` 上存在平台黑盒（易出现顶部把手透明透底、键盘收起后无法再次弹起等问题）。
     现全面重构为官方 `ModalBottomSheet`（配合 `rememberBottomSheetState`）：
     - 由官方 `ModalBottomSheet` 全面托管全局暗色 Scrim、原生手势下拉关闭与软键盘 Inset 自适应；
     - 使用统一的 `BottomSheetDefaults.DragHandle()` 与 `MaterialTheme.shapes.bottomSheet`（顶角 28dp 形状规范），根除把手区域透底问题；
     - 彻底删除平台层临时垫片 `ReplyDialogEdgeToEdgeEffect`，架构保持全 Compose Multiplatform 纯净规范；
     - 底部 Content Padding 动态结合 `isKeyboardActive` 与 `navigationBars` Insets，键盘激活时紧贴软键盘上沿（12dp），表情区展开或无键盘时预留安全避让区，避免被系统三键/手势条遮挡。

4. **固定 280dp 表情面板与渲染规范**：
   表情面板固定容器高度为 `280.dp`（紧密对齐移动端软键盘常规高度），颜文字与表情包 Tab 各自的 `LazyVerticalGrid` 在内部独立滚动，消除不同表情包项数差异引起的容器抖动；网格项必须严格标注稳定的 `key` 与 `contentType`（如 `face_text` 与 `sticker_item`）。

5. **返回键分级拦截**：
   当表情面板展开时，触发 Android 系统侧滑返回手势或 Desktop ESC 键，优先仅收起表情面板（保持回复草稿与弹窗）；表情面板关闭状态下再次返回才关闭整个弹窗。

## 考虑备选 (Considered Options)

- **维持原拓扑 + 允许外层全局垂直滑动**：操作栏仍在表情面板下方，但在内容超高时允许整窗滚动。此方案不仅增加操作链路（发评论前还需手动往下滑动找发送按钮），而且与软键盘嵌套滚动极易产生手势冲突。
- **全屏独立回复页面（非弹窗）**：打断当前评论浏览的沉浸感与上下文，对短评回复过重。
- **动态高度表情面板**：根据当前 Tab 内容自适应高度。会导致从 4 行颜文字切换到多行表情包时面板剧烈弹跳抖动，严重影响视觉体验。

## 后续影响 (Consequences)

- 评论与回复输入区域紧凑清爽，键盘弹起后依然保留充分上下文可视空间。
- 操作栏与发送按钮绝对稳定，杜绝了按钮掉落至键盘下方的恶性体验缺陷。
- 键盘与表情切换流畅顺滑，符合微信、B站等主流客户端交互共识。
