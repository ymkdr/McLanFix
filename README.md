# McLanFix

[![release](https://img.shields.io/github/v/release/ymkdr/McLanFix?label=release)](https://github.com/ymkdr/McLanFix/releases) [![license](https://img.shields.io/github/license/ymkdr/McLanFix)](LICENSE) [![build](https://github.com/ymkdr/McLanFix/actions/workflows/build.yml/badge.svg)](https://github.com/ymkdr/McLanFix/actions/workflows/build.yml)

**免 mod 的 Minecraft 局域网联机补丁：让离线账号也能进「对局域网开放」的世界，并给樱花穿透一个固定端口可指。**

支持 Minecraft Java 版 **1.7 ~ 1.21.x**，原版 / Forge / Fabric / NeoForge / 整合包通用，**不需要安装任何 mod**。

> 已在 **1.20.1 原版 + 离线账号 + 樱花穿透 TCP 隧道** 的跨省联机场景实测通过。
>
> **官方唯一发布地址**：https://github.com/ymkdr/McLanFix 。其他来源的同名工具与作者无关；下载后请核对 Release 里公布的 SHA256。

---

## 为什么需要它

有些版本缺少对应的“自定义局域网联机mod”，导致大家在使用pcl加樱花时无法联机，这很令人不爽

## 原理

mod 需要按版本编译，是因为它依赖 Forge/Fabric 的 API 和该版本的映射表。McLanFix 不碰这些，
它是 **JVM Agent**：游戏启动时由 Java 直接加载，在类加载阶段改写 **Mojang 自己的 authlib**：

```
com.mojang.authlib.yggdrasil.YggdrasilMinecraftSessionService
  joinServer(...)      -> 直接返回（客户端不再向 Mojang 请求验证）
  hasJoinedServer(...) -> 直接返回玩家档案（服务端认为验证已通过）
```

authlib 是 Mojang 的库，**类名从不混淆、方法签名跨版本稳定**，所以一份补丁覆盖 1.7 ~ 1.21。
`authlib-injector`、各种皮肤站用的也是同一原理，只是它们改写的是认证服务器地址。

## 快速开始

### 0. 自检（推荐）

双击 `检查补丁.bat`：自动找 Java、实际加载一次补丁、并打印你该复制的那行参数。
看到 `[McLanFix] 已加载…` 说明补丁和路径都没问题。

### 1. 安装（房主和访客都要做）

把 `McLanFix.jar` 放到一个简单路径，例如 `C:\McLanFix\McLanFix.jar`。

**方式 A：启动器参数（推荐）**

PCL：`设置 → 启动 → Java 虚拟机参数`（或 `版本设置 → 高级 → JVM 参数`），在原有内容后**追加**：

```
-javaagent:C:\McLanFix\McLanFix.jar
```

HMCL：`版本设置 → 高级设置 → Java 虚拟机参数`，同样追加。

**方式 B：全局环境变量（找不到参数框时用）**

双击 `启用补丁(全局).bat`，然后**完全退出启动器再重开**。清除用 `关闭补丁(全局).bat`。

### 2. 验证

启动游戏，日志里应出现：

```
[McLanFix] 已加载，正在关闭正版会话验证（离线联机模式）
[McLanFix]   替换 joinServer(...)
[McLanFix]   替换 hasJoinedServer(...)
```

### 3. 端口

| 游戏版本 | 做法 |
|---|---|
| **1.20 及以上** | 「对局域网开放」界面自带端口框，填 `25565` |
| **1.7 ~ 1.19.3** | 双击 `tools\启动转发器.bat`，它占住 `25565` 并自动跟随随机端口 |

### 4. 樱花穿透

| 项目 | 填写 |
|---|---|
| 隧道类型 | **TCP** |
| 本地 IP | `127.0.0.1` |
| 本地端口 | `25565` |
| 远程端口 | 由樱花分配 |

把得到的 `节点域名:远程端口` 发给朋友，朋友在 MC 里「**直接连接**」输入。

## 兼容性

| 项目 | 支持 |
|---|---|
| 游戏版本 | 1.7 ~ 1.21.x（含快照），原版 / Forge / Fabric / NeoForge / 整合包 |
| Java | 8 或更高 |
| 系统 | Windows / macOS / Linux（转发器的端口扫描兜底仅 Windows） |
| 正版账号 | 不需要 |

## 常见问题

**Q：日志里没有 `[McLanFix]`？**
参数没进到 JVM：确认填的是「Java 虚拟机参数」而不是「游戏参数」；路径要绝对路径；用方式 B 时记得重启启动器。

**Q：还是 429 / 无效会话？**
访客端没生效。让朋友也跑一次 `检查补丁.bat`，确认他那边日志有 `[McLanFix]`。

**Q：能进但朋友超时？**
樱花隧道没启动、远程端口被占、或本地端口与游戏端口不一致。房主可以先用自己的客户端连一次樱花地址自测。

**Q：朋友「版本不兼容」？**
双方游戏版本必须完全一致（本补丁不改变版本兼容性）。

**Q：端口 25565 填不进去？**
被占用。换 `25566`，樱花本地端口同步改成 `25566`；用转发器时加 `--port 25566`。

**Q：杀软/SmartScreen 拦截？**
jar 未签名，首次运行可能提示。选择「仍要运行」，或把所在目录加入白名单。

## 已知限制

1. 不改变最大玩家数（原版局域网上限 8）。
2. 未实现 LSP 的「UUID 修复」：离线玩家与正版玩家同名时仍可能冲突。
3. **不要用于公开服务器**——它关闭的是本地会话验证，公网服应保持正版验证。
4. 无 GUI：agent 是启动参数，转发器是命令行窗口。

## 目录结构

```
McLanFix/
  McLanFix.jar            JVM Agent（核心，房主+访客都要）
  检查补丁.bat             自检 + 打印需要复制的参数
  启用补丁(全局).bat       写 JAVA_TOOL_OPTIONS，启动器无关
  关闭补丁(全局).bat       清除上面的环境变量
  tools/
    McLanPin.jar          固定端口转发器（仅 1.7~1.19.3）
    启动转发器.bat
    src/ test/
  src/lanfix/Agent.java   Agent 源码
  test/                   authlib 离线验证用例
  README.md  LICENSE
```

## 构建

```
# Agent（依赖 ASM 9.x）
javac --release 8 -encoding UTF-8 -cp lib/asm-9.7.1.jar -d out src/lanfix/Agent.java
jar cfm McLanFix.jar manifest.txt -C out . -C asm-classes .

# 转发器
javac --release 8 -encoding UTF-8 -d out src/lanpin/*.java
jar cfe McLanPin.jar lanpin.Main -C out .
```

## 验证记录

| 项目 | 结果 |
|---|---|
| Agent 在真实 `authlib-4.0.43` 上替换 `joinServer` / `hasJoinedServer` | ✅ 离线验证通过（替换后零网络请求） |
| 1.20.1 原版 + 离线账号 + 樱花 TCP 跨省联机 | ✅ 实测通过 |
| 转发器：广播发现 / 端口跟随 / 并发 / 1.4~1.6 老 ping | ✅ 端到端测试通过 |
| 1.7.10 / 1.8.9 实机 | ⚠️ 未实测，欢迎反馈 |

## 许可

MIT。与 Mojang、Microsoft、樱花穿透均无隶属关系；Minecraft 是 Microsoft 的商标。
