# Changelog

本项目遵循 [语义化版本](https://semver.org/lang/zh-CN/)。

## [1.0.0] - 2026-11-20

### 新增
- JVM Agent：替换 authlib 的 `joinServer` / `hasJoinedServer`，让离线账号可加入局域网世界
- 自检脚本 `检查补丁.bat`：验证补丁可加载并打印需复制的参数
- 全局启用/关闭脚本（`JAVA_TOOL_OPTIONS`，启动器无关）
- 固定端口转发器 `tools/McLanPin.jar`（适用于 1.7 ~ 1.19.3）
- 端到端测试：广播发现、端口跟随、并发、1.4~1.6 老式 ping

### 验证
- 1.20.1 原版 + 离线账号 + 樱花 TCP 隧道，跨省联机通过
- authlib 4.0.43 离线验证通过
