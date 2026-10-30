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
