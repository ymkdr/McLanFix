# 贡献指南

## 反馈问题

请使用 [Issue 模板](.github/ISSUE_TEMPLATE/bug_report.yml)，**务必附上**：

- 游戏版本、加载器、Java 版本
- 游戏日志前 30 行（要能看到 `[McLanFix]` 那几行）
- 复现步骤

没有日志的 issue 通常无法处理。

## 提交代码

1. Fork 仓库，新建分支：`git checkout -b fix/xxx`
2. 修改 `src/lanfix/Agent.java`（或 `tools/src/lanpin/`）
3. 本地编译通过（见 README「构建」）
4. 提交并发起 Pull Request，说明改了什么、为什么

## 代码约定

- Java 8 兼容（`--release 8`），不用 `var`、`List.of`
- 中文注释，保持现有风格
- 不引入除 ASM 之外的第三方依赖
- 不提交构建产物（`out/`、`build/`、`*.jar` 已在 .gitignore）
