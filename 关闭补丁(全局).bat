@echo off
chcp 65001 >nul 2>nul
title McLanFix 关闭全局启用

reg delete "HKCU\Environment" /v JAVA_TOOL_OPTIONS /f >nul 2>nul
if errorlevel 1 (
  echo 没有找到 JAVA_TOOL_OPTIONS，可能已经清除过了。
) else (
  echo [完成] 已删除 JAVA_TOOL_OPTIONS。
)
echo.
echo 请完全退出 PCL 再重新打开，之后启动的游戏就不再带补丁。
pause
