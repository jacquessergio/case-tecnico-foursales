@echo off
echo 🔄 Reiniciando banco de dados MySQL...

REM Parar e remover container MySQL existente
docker-compose stop mysql
docker-compose rm -f mysql

REM Remover volume do MySQL para forçar reinicialização
docker volume rm desafio-foursales_mysql_data 2>nul

echo 📦 Recriando container MySQL...

REM Recriar container MySQL
docker-compose up -d mysql

echo ⏳ Aguardando MySQL ficar pronto...

REM Aguardar MySQL ficar pronto
:wait_mysql
docker exec ecommerce-mysql mysqladmin ping -h"localhost" --silent >nul 2>&1
if errorlevel 1 (
    echo Aguardando MySQL...
    timeout /t 2 >nul
    goto wait_mysql
)

echo ✅ MySQL está pronto!

REM Testar conexão com ecommerce_user
echo 🔍 Testando conexão com ecommerce_user...
docker exec ecommerce-mysql mysql -u ecommerce_user -pecommerce_password -e "SELECT 'Connection successful!' as status;"

if errorlevel 0 (
    echo ✅ Conexão com ecommerce_user funcionando!
    echo 🚀 Você pode agora executar a aplicação:
    echo    mvn spring-boot:run
) else (
    echo ❌ Problema na conexão. Verifique os logs:
    echo    docker-compose logs mysql
)

pause