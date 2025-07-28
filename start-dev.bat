@echo off
echo Iniciando CRASA Backend y Frontend...
echo.

echo Iniciando Backend Spring Boot...
start "CRASA Backend" cmd /k "cd BACK && mvnw spring-boot:run"

echo Esperando 10 segundos para que el backend inicie...
timeout /t 10 /nobreak > nul

echo Iniciando Frontend React...
start "CRASA Frontend" cmd /k "cd FRONTEND && npm install && npm run dev"

echo.
echo Servicios iniciados:
echo - Backend: http://localhost:8080
echo - Frontend: http://localhost:3000
echo.
echo Presiona cualquier tecla para cerrar esta ventana...
pause > nul 