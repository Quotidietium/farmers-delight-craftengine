@echo off
rem ============================================================
rem Farmer's Delight Papo port - GUI 诊断日志观察器
rem 用法：服务器启动后运行本脚本，然后在客户端里右键
rem       橱柜/篮子/烹饪锅，本窗口会实时显示诊断行。
rem ============================================================
setlocal enabledelayedexpansion
set LOG=%APPDATA%\..\..\..\..\..\F\Github\repo\farmers-delight-craftengine\smoke\logs\latest.log
if not exist "%LOG%" set LOG=logs\latest.log
echo Watching: %LOG%
echo (right-click cabinets/basket/cooking pot in game; Ctrl+C to stop)
:loop
powershell -NoProfile -Command "Get-Content '%LOG%' -Tail 40 | Select-String '\[PATH-A\]|\[PATH-B\]|\[GUI\]|FarmersDelight'" 2>nul
timeout /t 3 /nobreak >nul
goto loop
