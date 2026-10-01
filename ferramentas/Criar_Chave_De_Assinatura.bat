@echo off
REM ============================================================================
REM  MotoristaPro - cria a chave de assinatura e prepara os segredos do GitHub.
REM
REM  Rode UMA VEZ. Depois guarde o arquivo motoristapro.jks num lugar seguro
REM  (pendrive, Google Drive): perdendo ele, voce NAO consegue mais atualizar
REM  o app instalado nos celulares - so desinstalando e instalando de novo.
REM
REM  Como usar: clique duas vezes neste arquivo.
REM ============================================================================
setlocal
chcp 65001 >nul
cd /d "%~dp0"

echo.
echo  === MotoristaPro: chave de assinatura ===
echo.

REM --- Achar o keytool (vem junto com o Android Studio) ---
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
  echo         Instale o Android Studio ou um JDK 17 e rode de novo.
  echo.
  pause
  exit /b 1
)

if not exist motoristapro.jks goto :criar

echo  Ja existe um motoristapro.jks nesta pasta.
echo  Se criar outro, os celulares que tem o app instalado NAO vao conseguir atualizar.
echo.
set /p RESP="  Criar mesmo assim? (digite SIM para continuar): "
if /i not "%RESP%"=="SIM" goto :base64
del motoristapro.jks

:criar

echo.
echo  Vou pedir uma SENHA. Escolha uma e ANOTE - voce vai precisar dela no GitHub.
echo  Use a mesma senha nas duas perguntas, e no "nome e sobrenome" pode por MotoristaPro.
echo.

"%KEYTOOL%" -genkeypair -v ^
  -keystore motoristapro.jks ^
  -alias motoristapro ^
  -keyalg RSA -keysize 2048 -validity 10000

if not exist motoristapro.jks (
  echo.
  echo  [ERRO] A chave nao foi criada. Tente de novo.
  pause
  exit /b 1
)

:base64
echo.
echo  Gerando o texto do segredo KEYSTORE_BASE64...
powershell -NoProfile -Command ^
  "[Convert]::ToBase64String([IO.File]::ReadAllBytes('motoristapro.jks')) | Set-Content -NoNewline -Encoding ascii 'KEYSTORE_BASE64.txt'"

echo.
echo  ===========================================================
echo   Pronto. Nesta pasta ficaram:
echo.
echo    motoristapro.jks       ^<- GUARDE. Nao envie para o GitHub.
echo    KEYSTORE_BASE64.txt    ^<- o conteudo vai no segredo do GitHub.
echo.
echo   No GitHub: Settings ^> Secrets and variables ^> Actions ^> New secret
echo.
echo     KEYSTORE_BASE64   = todo o texto de KEYSTORE_BASE64.txt
echo     KEYSTORE_SENHA    = a senha que voce digitou
echo     KEY_SENHA         = a mesma senha
echo     KEY_ALIAS         = motoristapro
echo   ===========================================================
echo.
pause
