-- Script de inicialização automática do MySQL
-- Executado automaticamente quando o container é criado pela primeira vez

-- Configurar charset padrão para o banco ecommerce_db
ALTER DATABASE ecommerce_db CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

-- Criar o usuário ecommerce_user com permissões completas
-- Deletar se existir para recriar com permissões corretas
DROP USER IF EXISTS 'ecommerce_user'@'%';
DROP USER IF EXISTS 'ecommerce_user'@'localhost';

-- Criar usuário para conexões locais e remotas
CREATE USER 'ecommerce_user'@'%' IDENTIFIED BY 'ecommerce_password';
CREATE USER 'ecommerce_user'@'localhost' IDENTIFIED BY 'ecommerce_password';
CREATE USER 'ecommerce_user'@'172.%' IDENTIFIED BY 'ecommerce_password';

-- Garantir que o usuário ecommerce_user tenha todas as permissões no banco ecommerce_db
GRANT ALL PRIVILEGES ON ecommerce_db.* TO 'ecommerce_user'@'%';
GRANT ALL PRIVILEGES ON ecommerce_db.* TO 'ecommerce_user'@'localhost';
GRANT ALL PRIVILEGES ON ecommerce_db.* TO 'ecommerce_user'@'172.%';

-- Permitir conexões do usuário root de qualquer host (para desenvolvimento)
-- Atualizar senha do root para conexões remotas
ALTER USER 'root'@'%' IDENTIFIED WITH mysql_native_password BY 'password';
GRANT ALL PRIVILEGES ON *.* TO 'root'@'%' WITH GRANT OPTION;

-- Aplicar as mudanças
FLUSH PRIVILEGES;

-- Verificar usuários criados
SELECT User, Host FROM mysql.user WHERE User IN ('root', 'ecommerce_user');

-- Log de confirmação
SELECT 'Database ecommerce_db initialized successfully!' as message;