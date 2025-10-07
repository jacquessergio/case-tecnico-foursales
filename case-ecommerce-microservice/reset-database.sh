#!/bin/bash

echo "🔄 Reiniciando banco de dados MySQL..."

# Parar e remover container MySQL existente
docker-compose stop mysql
docker-compose rm -f mysql

# Remover volume do MySQL para forçar reinicialização
docker volume rm desafio-foursales_mysql_data 2>/dev/null || true

echo "📦 Recriando container MySQL..."

# Recriar container MySQL
docker-compose up -d mysql

echo "⏳ Aguardando MySQL ficar pronto..."

# Aguardar MySQL ficar pronto
until docker exec ecommerce-mysql mysqladmin ping -h"localhost" --silent; do
    echo "Aguardando MySQL..."
    sleep 2
done

echo "✅ MySQL está pronto!"

# Testar conexão com ecommerce_user
echo "🔍 Testando conexão com ecommerce_user..."
docker exec ecommerce-mysql mysql -u ecommerce_user -pecommerce_password -e "SELECT 'Connection successful!' as status;"

if [ $? -eq 0 ]; then
    echo "✅ Conexão com ecommerce_user funcionando!"
    echo "🚀 Você pode agora executar a aplicação:"
    echo "   mvn spring-boot:run"
else
    echo "❌ Problema na conexão. Verifique os logs:"
    echo "   docker-compose logs mysql"
fi