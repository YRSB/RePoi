# 评论回复面板纵向拓扑重构与软键盘表情互斥联动 (Comment Reply Dialog Topology & Keyboard-Emoji Mutex)

- 状态：已采纳 (Accepted) - 2026-11-19
- 修订/取代：全面重构 [CommentSection.kt](../../shared/src/commonMain/kotlin/com/nobody/repoi/ui/common/comment/CommentSection.kt) 与 [CommentDetailScreen.kt](../../shared/src/commonMain/kotlin/com/nobody/repoi/ui/comment/CommentDetailScreen.kt) 的回复输入浮层设计，废弃 `Dialog` 与 `ModalBottomSheet` 二级子窗口实现，全面抄录并对齐 Google 官方 [Jetchat](https://github.com/android/compose-samples/tree/main/Jetchat) 架构规范

## 背景与上下文 (Context)

在移动端屏幕（特别是软键盘弹起后可视高度仅剩 300~400dp 的场景）下，原评论回复浮层（`CommentReplyDialog`）存在多处交互与布局缺陷：

1. **输入框默认行数过多**：输入框采用硬编码 `heightIn(min = 90.dp, max = 160.dp)`，初次打开或无内容时即强占近 4~5 行的空白高度，挤占了主楼上下文与操作可视区域；
2. **表情展开挤压操作栏**：原有布局顺序为 `[输入框] -> [已选表情] -> [表情面板] -> [操作栏(表情+发送按钮)]`。由于操作栏位于表情面板下方，当用户点击表情图标展开表情区时，操作栏被整体推挤至系统软键盘下方或屏幕可视区域外，导致用户无法点击“发送”且丢失表情切换入口；
3. **软键盘与表情面板缺乏互斥**：点击表情按钮时仅异步通知软键盘隐藏，在软键盘尚未完成收起动画或焦点未释放时，表情面板即开始渲染，造成两者高度叠加并拉爆视口高度；
4. **子窗口与 BottomSheet 的“二段跳”与透明缝隙**：
   - 此前尝试使用原生子 Window `Dialog` 或 Material 3 官方 `ModalBottomSheet`，由于属于 WindowManager 二级窗口，软键盘展开时与抽屉入场动画发生竞争，导致先弹起键盘后升起抽屉（或先升起抽屉后弹出键盘）的恶劣“二段跳”抖动；
   - `ModalBottomSheet` 自带的顶部 DragHandle（小横条）在键盘升降与窗口调整大小时产生半透明缝隙，透出底层评论文字；
   - 子 Window 内部的 `WindowInsets.ime` 在部分系统上返回 0，导致无法准确感知键盘展开，造成“已聚焦时点击软键盘按钮毫无反应”；
   - 输入框的 Hint 提示词与光标在垂直基线上存在偏移。

## 架构决策 (Decision)

全面参考 Google Android 官方 Compose 样例工程 **Jetchat (`com.example.compose.jetchat.conversation.UserInput`)** 的交互与架构设计，将回复浮层重构为主窗口同层 In-tree Overlay 架构：

1. **重构纵向层级拓扑（操作栏置于面板上方）**：
   将操作工具栏提升至表情面板插槽上方，严格按以下从上至下拓扑排布：
   - 顶部平滑圆角卡片边框（无 DragHandle 小横条，彻底根除透明缝隙与透底缺陷）
   - 回复目标提示（`回复 @xxx`）
   - 紧凑自适应输入框
   - 已选表情贴纸横向预览条（`selectedStickers`，未选时不占高）
   - **操作工具栏**（左侧 😊 表情按钮与 ⌨️ 键盘按钮双常驻，右侧胶囊“发送”按钮）
   - **底部辅助面板插槽**（表情面板展开时在此呈现；表情面板关闭且软键盘升起时，由系统软键盘占用）
   此举确保操作栏与发送按钮始终紧贴在用户输入内容正下方，彻底免疫底部面板的高度变化，永不沉底。

2. **自适应紧凑输入框与光标基线对齐**：
   采用 `heightIn(min = 40.dp, max = 120.dp)`：初始默认呈现紧凑单行/双行（40dp），随用户输入行数增加自然向下撑开，达到 120dp 后在内部垂直平滑滚动；
   输入框与提示词（Hint）在 `decorationBox` 中统一采用 `Alignment.TopStart` 与完全相同的 `MaterialTheme.typography.bodyMedium.copy(fontSize = 15.sp, lineHeight = 22.sp)`，确保首行字形与光标竖直基线像素级对齐。

3. **对齐 Jetchat 的 `CommentInputSelector` 状态机与焦点夺取（Focus Stealing）机制**：
   - 定义 `CommentInputSelector`（`NONE`, `EMOJI`）；
   - **打开表情区自动收起软键盘**：展开表情面板时，通过 `SideEffect { selectorFocusRequester.requestFocus() }` 与在面板容器挂载 `focusTarget()` 主动夺走焦点，触发 `BasicTextField` 自然失焦，系统软键盘平滑无缝收起，彻底杜绝多窗口焦点死锁与延迟；
   - **常驻键盘按钮与表情按钮**：
     - 点击表情按钮：在 `NONE` 与 `EMOJI` 间可靠切换；
     - 点击键盘按钮：若在表情区则切回键盘；若在输入状态则主动收起软键盘与清除焦点；若未激活则重新聚焦并唤起系统软键盘；高亮状态与当前活跃模式精确同步。

4. **主窗口同层 Overlay 架构（彻底杜绝二段跳与子 Window 缺陷）**：
   - 废弃 `Dialog` 与 `ModalBottomSheet` 二级子窗口，直接在页面根 `Box` 内呈现（半透明暗色遮罩 + 贴底 Surface）；
   - 贴底 Surface 直接挂载 `Modifier.imePadding().navigationBarsPadding()`，完全由主 Window 的系统级 Inset 动画驱动，键盘升降毫秒级响应，零二段跳、零抖动、零透底缝隙；
   - 点击外部暗色遮罩或按返回键安全关闭浮层并隐藏键盘。

5. **固定 280dp 表情面板与渲染规范**：
   表情面板固定容器高度为 `280.dp`（紧密对齐移动端软键盘常规高度），颜文字与表情包 Tab 各自的 `LazyVerticalGrid` 在内部独立滚动；网格项严格标注稳定的 `key` 与 `contentType`（如 `face_text` 与 `sticker_item`）。

6. **返回键分级拦截**：
   当表情面板展开时，触发 Android 系统侧滑返回手势或 Desktop ESC 键，优先仅收起表情面板（保持回复草稿与弹窗）；表情面板关闭状态下再次返回才关闭整个弹窗。

## 考虑备选 (Considered Options)

- **维持原拓扑 + 允许外层全局垂直滑动**：操作栏仍在表情面板下方，但在内容超高时允许整窗滚动。此方案不仅增加操作链路（发评论前还需手动往下滑动找发送按钮），而且与软键盘嵌套滚动极易产生手势冲突。
- **全屏独立回复页面（非弹窗）**：打断当前评论浏览的沉浸感与上下文，对短评回复过重。
- **动态高度表情面板**：根据当前 Tab 内容自适应高度。会导致从 4 行颜文字切换到多行表情包时面板剧烈弹跳抖动，严重影响视觉体验。

## 后续影响 (Consequences)

- 评论与回复输入区域紧凑清爽，键盘弹起后依然保留充分上下文可视空间。
- 操作栏与发送按钮绝对稳定，杜绝了按钮掉落至键盘下方的恶性体验缺陷。
- 键盘与表情切换流畅顺滑，符合微信、B站等主流客户端交互共识。
