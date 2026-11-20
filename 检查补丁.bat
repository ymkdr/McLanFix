@echo off
chcp 65001 >nul 2>nul
setlocal
cd /d "%~dp0"
title McLanFix 补丁检查

set "JAR=%~dp0McLanFix.jar"
if not exist "%JAR%" (
  echo [错误] 当前文件夹里没有 McLanFix.jar
  pause
  exit /b 1
)

echo ============================================================
echo   McLanFix 补丁自检
echo ============================================================
echo.

set "JAVA_EXE="
if defined JAVA_HOME if exist "%JAVA_HOME%\bin\java.exe" set "JAVA_EXE=%JAVA_HOME%\bin\java.exe"
if not defined JAVA_EXE for %%J in (java.exe) do set "JAVA_EXE=%%~$PATH:J"
set "PF86=%ProgramFiles(x86)%"
if not defined JAVA_EXE for /d %%D in ("%ProgramFiles%\Java\jre*" "%ProgramFiles%\Java\jdk*" "%ProgramFiles%\Eclipse Adoptium\jdk*" "%ProgramFiles%\Microsoft\jdk*" "%PF86%\Java\jre*") do if not defined JAVA_EXE if exist "%%~D\bin\java.exe" set "JAVA_EXE=%%~D\bin\java.exe"
if not defined JAVA_EXE for /f "delims=" %%J in ('dir /b /s "%APPDATA%\.minecraft\runtime\java.exe" 2^>nul') do if not defined JAVA_EXE set "JAVA_EXE=%%J"

if not defined JAVA_EXE (
  echo [错误] 没找到 Java。请先安装 Java 8 或更高版本。
  pause
  exit /b 1
)

echo 使用 Java: %JAVA_EXE%
echo.
echo ---- 下面是补丁加载结果（出现 [McLanFix] 即正常）----
echo.
"%JAVA_EXE%" -Dsun.stdout.encoding=UTF-8 -Dsun.stderr.encoding=UTF-8 -javaagent:"%JAR%" -version 2>&1
echo.
echo ---- 自检结束 ----
echo.
echo 请把下面这一整行，复制到【PCL - 设置 - 启动 - 游戏 JVM 参数】里：
echo.
echo -javaagent:%JAR%
echo.
echo 房主和访客都要加；游戏日志里出现 [McLanFix] 就说明生效了。
echo.
pause
