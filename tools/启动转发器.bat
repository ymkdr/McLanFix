@echo off
chcp 65001 >nul 2>nul
setlocal
cd /d "%~dp0"
title McLanPin - 我的世界局域网固定端口

set "JAR=%~dp0McLanPin.jar"
if not exist "%JAR%" (
  echo [错误] 找不到 McLanPin.jar，请把本 bat 和 jar 放在同一个文件夹里。
  pause
  exit /b 1
)

set "JAVA_EXE="
if defined JAVA_HOME if exist "%JAVA_HOME%\bin\java.exe" set "JAVA_EXE=%JAVA_HOME%\bin\java.exe"
if not defined JAVA_EXE for %%J in (java.exe) do set "JAVA_EXE=%%~$PATH:J"
set "PF86=%ProgramFiles(x86)%"
if not defined JAVA_EXE for /d %%D in ("%ProgramFiles%\Java\jre*" "%ProgramFiles%\Java\jdk*" "%ProgramFiles%\Eclipse Adoptium\jdk*" "%ProgramFiles%\Microsoft\jdk*" "%PF86%\Java\jre*" "%PF86%\Java\jdk*") do if not defined JAVA_EXE if exist "%%~D\bin\java.exe" set "JAVA_EXE=%%~D\bin\java.exe"

rem 最后再找官方启动器自带的 Java 运行时（很多人只有这个）
if not defined JAVA_EXE for /f "delims=" %%J in ('dir /b /s "%APPDATA%\.minecraft\runtime\java.exe" 2^>nul') do if not defined JAVA_EXE set "JAVA_EXE=%%J"

if not defined JAVA_EXE (
  echo [错误] 没找到 Java 运行时。
  echo        请安装 Java 8 或更高版本后重试: https://www.java.com/
  echo        或者用启动器自带的 Java 手动运行:
  echo        "路径\java.exe" -jar "McLanPin.jar"
  pause
  exit /b 1
)

echo 使用 Java: %JAVA_EXE%
"%JAVA_EXE%" -Dsun.stdout.encoding=UTF-8 -Dsun.stderr.encoding=UTF-8 -jar "%JAR%" %*

echo.
echo 程序已退出。
pause
