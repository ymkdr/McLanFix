# McLanFix

[![release](https://img.shields.io/github/v/release/ymkdr/McLanFix?label=release)](https://github.com/ymkdr/McLanFix/releases) [![license](https://img.shields.io/github/license/ymkdr/McLanFix)](LICENSE) [![build](https://github.com/ymkdr/McLanFix/actions/workflows/build.yml/badge.svg)](https://github.com/ymkdr/McLanFix/actions/workflows/build.yml)

给 Minecraft Java 版用的小补丁。加一行启动参数，不用装 mod，离线账号也能进「对局域网开放」的世界，端口还能固定在 25565，樱花穿透正好指过来。

支持 1.7 ~ 1.21.x，原版 / Forge / Fabric / 整合包都行。1.20.1 原版 + 离线账号 + 樱花 TCP 隧道，跨省实测过了。

声明：不提供游戏本体和账号，仅用于朋友之间的局域网联机。

> 官方只在这个仓库发布：https://github.com/ymkdr/McLanFix 。别处下的同名东西跟作者没关系，文件对不对得上，看 Release 里的 SHA256。

## 为什么写这个

老版本（1.7.10、1.8.9 这些）一直找不到能用的「自定义局域网联机」。没有它，朋友点进来只会看到 429、无效会话，或者干脆连不上。

翻了半天，原因其实就两个：集成服务器会去验证正版会话，而局域网又是每次随机挑端口，樱花那边填死的端口永远指空。LSP 能同时解决这两件事，但它得按游戏版本单独编译，老版本就是没有。

所以换了个思路：不去改游戏，改 JVM。

## 原理

游戏启动的时候，Java 会先把 authlib 加载进来。这个 jar 是个 JVM Agent，在这时候把 authlib 里的两个方法换掉：

```
com.mojang.authlib.yggdrasil.YggdrasilMinecraftSessionService
  joinServer(...)      -> 直接返回（客户端不再向 Mojang 请求验证）
  hasJoinedServer(...) -> 直接返回玩家档案（服务端认为验证通过）
```

authlib 是 Mojang 自己的库，类名不混淆，接口这么多年也没怎么变，所以一份补丁能覆盖 1.7 到 1.21。authlib-injector 和各种皮肤站，走的也是同一个路子。

## 装法

把 `McLanFix.jar` 放到 `C:\McLanFix\`，然后 PCL 里「设置 → 启动 → Java 虚拟机参数」加一行（原有内容别删）：

```
-javaagent:C:\McLanFix\McLanFix.jar
```

HMCL 在「版本设置 → 高级设置」里也是同一栏。找不到地方的话，双击 `启用补丁(全局).bat` 也行，它会写环境变量，跟启动器无关，用完记得点 `关闭补丁(全局).bat`。

启动游戏，日志里出现下面这行就成了：

```
[McLanFix] v1.0.1 官方仓库 https://github.com/ymkdr/McLanFix
[McLanFix] 已加载，正在关闭正版会话验证（离线联机模式）
```

房主和访客都要装。

## 端口

1.20 以后原版「对局域网开放」界面自带端口框，填 25565 就行。1.7 ~ 1.19.3 没有这个框，用 `tools\启动转发器.bat`，它占住 25565，自动跟着游戏的随机端口走。

## 樱花这边

隧道类型选 TCP，本地 IP 127.0.0.1，本地端口 25565，远程端口让樱花分配。启动之后把 `域名:端口` 发给朋友，他在「直接连接」里粘进去。局域网列表里是刷不出来的，那个只在同一个局域网内有效。

## 兼容性

| | |
|---|---|
| 游戏版本 | 1.7 ~ 1.21.x，含快照 |
| 加载器 | 原版 / Forge / Fabric / NeoForge / 整合包 |
| Java | 8 或更高 |
| 系统 | Windows / macOS / Linux（转发器的端口扫描只做了 Windows） |
| 正版账号 | 不需要 |

## 出问题先看这里

**日志里没有 `[McLanFix]`**
参数没进到 JVM。确认填的是「Java 虚拟机参数」不是「游戏参数」，路径要绝对路径。用全局脚本的话，记得把启动器完全退掉再开。

**还是 429 或者无效会话**
访客那边没生效。让他也跑一遍 `检查补丁.bat`，看日志里有没有那两行。

**能进，但朋友超时**
基本是隧道的问题：没启动、远程端口被占、或者本地端口和游戏端口对不上。自己先连一次樱花地址，能进再叫朋友。

**朋友提示版本不兼容**
两边版本得完全一样，这个补丁管不了版本。

**25565 填不进去**
被占了。换 25566，樱花那边也跟着改；用转发器就加 `--port 25566`。

**杀软或者 SmartScreen 拦**
jar 没签名，第一次运行会提示，选「仍要运行」就行。

## 已知的限制

最大玩家数改不了，原版局域网还是 8 个上限。离线玩家和正版玩家同名的话，UUID 还是会打架，LSP 的「UUID 修复」这块没做。另外这东西只适合朋友之间玩，别拿去开公开服。

## 文件结构

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
```

## 自己编译

```
javac --release 8 -encoding UTF-8 -cp lib/asm-9.7.1.jar -d out src/lanfix/Agent.java
jar cfm McLanFix.jar manifest.txt -C out . -C asm-classes .
```

转发器在 `tools/src`，`javac --release 8 -d out tools/src/lanpin/*.java`，再 `jar cfe McLanPin.jar lanpin.Main -C out .`。

## 验证情况

Agent 在真实的 authlib-4.0.43 上验证过：打完补丁，`hasJoinedServer` 直接返回档案，`joinServer` 不再发网络请求；不打补丁两个方法都会抛网络异常。1.20.1 原版跨省联机实测通过。1.7.10 和 1.8.9 没实机跑过，有问题欢迎提 issue。

## 许可

MIT。跟 Mojang、Microsoft、樱花都没关系，Minecraft 是 Microsoft 的商标。
