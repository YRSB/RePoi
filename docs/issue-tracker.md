# Issue 跟踪与流转规范 (Issue Tracker)

本项目使用 GitHub Issues（`YRSB/RePoi`）进行需求定义、Bug 追踪与规格管理。日常操作通过 `gh` 命令行完成。

---

## 常用操作指令

- **创建 Issue**：`gh issue create --title "<title>" --body "<body>"`
- **查看 Issue**：`gh issue view <number> --json number,title,body,labels,comments`
- **列表查询**：`gh issue list --state open --label "<label>"`
- **追加评论**：`gh issue comment <number> --body "<comment>"`
- **标签流转**：`gh issue edit <number> --add-label "<label>"` / `--remove-label "<label>"`
- **关闭 Issue**：`gh issue close <number> --comment "<closing note>"`

---

## PR 关联

- 修复 Bug 或实现特性的 PR 必须在描述中注明 `Fixes #<number>` 或 `Closes #<number>`，由合并流程自动闭环。
  - **多个 issue 必须逐个写完整关键字**：GitHub 只识别逗号列表中的**第一个**编号，
    `Closes #75, #77, #78` 只关闭 #75。正确写法是每行一个：
    ```
    Closes #75
    Closes #77
    Closes #78
    ```
  - 合并后**必须核实** `gh issue view <n> --json state`，未闭环的用 `gh issue close <n> --comment "<交付说明>"` 补关。
- 纯文档 PR（仅 `docs/**`、`*.md` 等）走 `check-docs-only` 快速通道，并由 `auto-merge-docs` 自动合并。

---

## CI 跟踪与失败取证

- **跟踪**：一律 `gh run watch <run-id> --exit-status --interval 30`，禁用 `sleep N; gh pr checks` 组合轮询
- **编译失败**：`gh run view <run-id> --log-failed | grep -m5 "e: "` 取首个错误（CI 摘要页亦有镜像）
- **测试失败**：`gh run download <run-id> -n test-reports`，从 XML 的 `system-out` 节点提取 `DIAG-` 打印
- **服务端抖动**（`Internal Server Error` / GraphQL EOF）：等待后重试同一命令，不换方案
