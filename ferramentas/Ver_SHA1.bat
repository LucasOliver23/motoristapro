@echo off
REM ============================================================================
REM  MotoristaPro - mostra o SHA-1 e o SHA-256 da chave de assinatura.
REM  Esses numeros vao cadastrados no Firebase para o login com Google funcionar.
REM
REM  Coloque este arquivo na MESMA pasta do motoristapro.jks e de dois cliques.
REM ============================================================================
setlocal
chcp 65001 >nul
cd /d "%~dp0"

set "KEYTOOL="
where keytool >nul 2>nul && set "KEYTOOL=keytool"
if not defined KEYTOOL (
  for %%P in (
    "%ProgramFiles%\Android\Android Studio\jbr\bin\keytool.exe"
    "%ProgramFiles%\Android\Android Studio\jre\bin\keytool.exe"
    "%LOCALAPPDATA%\Programs\Android Studio\jbr\bin\keytool.exe"
  ) do if exist %%P set "KEYTOOL=%%~P"
)
if not defined KEYTOOL (
  echo  [ERRO] Nao encontrei o keytool. Ele vem com o Android Studio.
  pause
  exit /b 1
)

if not exist motoristapro.jks (
  echo  [ERRO] Nao achei motoristapro.jks nesta pasta.
  echo         Coloque este .bat junto com a chave e rode de novo.
  pause
  exit /b 1
)

echo.
echo  Digite a senha da chave (ela nao aparece na tela, e normal):
echo.

"%KEYTOOL%" -list -v -keystore motoristapro.jks -alias motoristapro > sha1.txt 2>&1

echo.
echo  ===========================================================
findstr /C:"SHA1:" /C:"SHA-1:" /C:"SHA256:" /C:"SHA-256:" sha1.txt
echo  ===========================================================
echo.
echo  Copie a linha SHA1 (so o numero, sem o "SHA1:") e cadastre em:
echo    Firebase ^> Configuracoes do projeto ^> seu app Android
echo    ^> Adicionar impressao digital
echo.
echo  O texto completo tambem ficou salvo em sha1.txt nesta pasta.
echo.
pause
