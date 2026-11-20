@echo off
chcp 65001 >nul 2>nul
setlocal
cd /d "%~dp0"
title McLanFix 全局启用

set "JAR=%~dp0McLanFix.jar"
if not exist "%JAR%" (
  echo [错误] 当前文件夹里没有 McLanFix.jar
  pause
  exit /b 1
)

echo 即将写入用户环境变量 JAVA_TOOL_OPTIONS:
echo   -javaagent:%JAR%
echo.
echo 注意：这是"全局"方式，本机所有 Java 程序都会加载这个补丁。
echo       测完请双击【关闭补丁(全局).bat】清除。
echo.
setx JAVA_TOOL_OPTIONS "-javaagent:%JAR%" >nul
if errorlevel 1 (
  echo [错误] 写入失败，可能没有权限。
  pause
  exit /b 1
)

echo [完成] 已写入。
echo.
echo 下一步：
echo   1. 完全退出 PCL（不是最小化），再重新打开
echo   2. 启动游戏，日志里出现 [McLanFix] 已加载 即生效
echo.
pause
