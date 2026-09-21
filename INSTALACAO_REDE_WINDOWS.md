# Instalação no micro central (Windows)

O micro central executa **PostgreSQL e o aplicativo Java**. As outras pessoas usam somente o navegador. O IntelliJ e o Maven são ferramentas de desenvolvimento e não precisam ser instalados no servidor.

## 1. Preparar o computador

1. Peça à equipe de TI um IP reservado na rede ou um nome interno, por exemplo `sso-hfag.interno`.
2. Instale um Java compatível com o projeto (Java 17 ou superior) e PostgreSQL. Mantenha o Windows e ambos atualizados.
3. Crie um usuário de sistema dedicado à aplicação, com permissão apenas nas pastas necessárias. Restrinja o acesso físico e remoto ao micro central.
4. No PostgreSQL, mantenha a escuta em `localhost` e não abra a porta **5432** no firewall para os computadores dos usuários.

## 2. Criar o banco

No pgAdmin, conectado como administrador do PostgreSQL no próprio micro central, execute uma vez:

```sql
CREATE ROLE sso_app LOGIN PASSWORD 'TROQUE_POR_UMA_SENHA_FORTE';
CREATE DATABASE sso_hfag OWNER sso_app ENCODING 'UTF8';
```

Se o banco já existir, não repita os comandos. O aplicativo cria as tabelas na primeira inicialização. Não copie o banco de testes para um ambiente com dados reais sem revisar seus registros e permissões.

## 3. Copiar e iniciar o aplicativo

Copie `target/plataforma-sso-0.1.0.jar` e `iniciar-rede.ps1` para uma pasta do micro central, por exemplo `C:\SSO-HFAG`. Na primeira execução, abra PowerShell nessa pasta e defina as variáveis **somente para essa sessão**:

```powershell
$env:SSO_DB_URL='jdbc:postgresql://localhost:5432/sso_hfag'
$env:SSO_DB_USER='sso_app'
$env:SSO_DB_PASSWORD='SENHA_DO_BANCO'
$env:SSO_ADMIN_EMAIL='admin@exemplo.interno'
$env:SSO_ADMIN_PASSWORD='SENHA_INICIAL_COM_12_OU_MAIS_CARACTERES'
.\iniciar-rede.ps1
```

A conta ADMIN inicial é criada apenas quando a tabela `usuarios` está vazia. Nas próximas inicializações, `SSO_ADMIN_EMAIL` e `SSO_ADMIN_PASSWORD` não são necessários. Nunca coloque senhas no script, no repositório ou em atalhos visíveis. Para iniciar automaticamente com o Windows, configure uma tarefa ou serviço sob a conta dedicada, com as variáveis protegidas pela equipe de TI. O script acima é para inicialização manual e encerra a aplicação quando a janela é fechada.

## 4. Acesso pela rede

O perfil `rede` faz o Spring Boot escutar em todas as interfaces na porta **8080**. Na rede interna, teste `http://IP_DO_MICRO_CENTRAL:8080/login` primeiro no micro central e depois em outro computador.

No Firewall do Windows, permita **entrada TCP 8080 apenas da sub-rede ou dos computadores autorizados**. Não abra a porta no roteador da internet. Os navegadores acessam somente a aplicação; o PostgreSQL permanece em `localhost:5432`.

Esse endereço HTTP serve apenas para testes com dados fictícios. Antes de usar dados reais de pacientes, coloque HTTPS na frente da aplicação com um certificado confiável pela rede interna, revise permissões e faça um teste de restauração de backup.

## 5. Operação e backup

- Faça backup regular do PostgreSQL com `pg_dump -Fc`, guardando o arquivo fora do micro central e com acesso restrito.
- Teste periodicamente a restauração em um banco separado com `pg_restore`.
- Configure o início automático do aplicativo e do PostgreSQL, monitore espaço em disco e use no-break se o micro central precisar permanecer disponível.
- Após alterar o código, gere um novo `.jar` no computador de desenvolvimento, pare a aplicação, substitua o arquivo e inicie novamente. Faça backup antes da atualização.

Exemplo de backup manual, executado no micro central com acesso ao PostgreSQL:

```powershell
pg_dump -h localhost -U sso_app -Fc -f 'D:\Backups\sso_hfag.dump' sso_hfag
```

O caminho de backup deve existir e estar em um local protegido. A senha do banco será solicitada pelo PostgreSQL se não houver configuração segura de credenciais.
