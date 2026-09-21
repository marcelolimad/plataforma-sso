# Plataforma SSO HFAG — versão inicial para teste

Aplicação local em Java 17, Spring Boot, HTML/CSS/JavaScript e PostgreSQL. Inclui login, usuários, dashboard, cadastro e pesquisa de atendimentos, evoluções e relatório básico por período. Os dados de exemplo das imagens **não** são importados.

## 1. Preparar PostgreSQL

No pgAdmin 4, conectado como administrador do PostgreSQL, abra o Query Tool e execute, trocando a senha do exemplo por uma senha sua:

```sql
CREATE ROLE sso_app LOGIN PASSWORD 'ESCOLHA_UMA_SENHA_FORTE';
CREATE DATABASE sso_hfag OWNER sso_app ENCODING 'UTF8';
```

Se você já criou a conta ou o banco, não execute novamente os respectivos comandos. O pgAdmin 4 é a ferramenta de administração; o serviço PostgreSQL também precisa estar instalado e em execução. As tabelas são criadas automaticamente na primeira inicialização.

## 2. Rodar no seu computador

Abra o PowerShell na pasta do projeto e defina as variáveis **apenas nessa janela**:

```powershell
$env:SSO_DB_URL='jdbc:postgresql://localhost:5432/sso_hfag'
$env:SSO_DB_USER='sso_app'
$env:SSO_DB_PASSWORD='A_SENHA_QUE_VOCE_ESCOLHEU'
$env:SSO_ADMIN_EMAIL='seu.email@exemplo.local'
$env:SSO_ADMIN_PASSWORD='UMA_SENHA_INICIAL_COM_PELO_MENOS_12_CARACTERES'
java -jar .\target\plataforma-sso-0.1.0.jar
```

Abra <http://localhost:8080> e entre com o email e a senha definidos acima. O usuário administrador inicial é criado **somente se a tabela de usuários estiver vazia**. Em execuções posteriores, use a mesma conta; mudar as variáveis não redefine sua senha.

Se preferir iniciar pelo IntelliJ, abra a pasta que contém `pom.xml` como projeto Maven e execute `SsoApplication`. Defina as mesmas variáveis de ambiente na configuração de execução do IntelliJ.

## 3. Compilar após alterar o código

Com Java 17 e Maven instalados:

```powershell
mvn package
java -jar .\target\plataforma-sso-0.1.0.jar
```

Caso o Maven use um diretório de cache sem permissão de escrita, indique seu cache de usuário: `mvn '-Dmaven.repo.local=C:\Users\SEU_USUARIO\.m2\repository' package`.

## 4. Usar no computador central da empresa

Siga [INSTALACAO_REDE_WINDOWS.md](INSTALACAO_REDE_WINDOWS.md). O micro central precisa de Java e PostgreSQL, mas não precisa de IntelliJ. O perfil `rede` e o script `iniciar-rede.ps1` preparam a escuta na rede interna.

## 5. Publicar uma cópia temporária para teste externo

Siga [PUBLICAR_RENDER_TESTE.md](PUBLICAR_RENDER_TESTE.md). Essa publicação usa um PostgreSQL novo no Render e dados fictícios; não acessa o banco do micro central.

## Escopo desta primeira versão

- Relatório: resumo por período e exportação XLSX de atendimentos completos, atendimentos por mês e evoluções por mês.
- Categoria, situação, tipo e classificação: campos básicos; as regras de dependência da plataforma original ainda precisam ser definidas e cadastradas.
- Usuários: administrador pode criar contas, redefinir senhas e excluir contas. A exclusão bloqueia o acesso e preserva os registros históricos. Edição de conta e trilha completa de auditoria ainda não estão implementadas.
- A edição simultânea de evoluções do mesmo atendimento é detectada pela coluna `versao`: a segunda gravação recebe aviso para recarregar a tela.
- Não há importação automática dos registros existentes na plataforma antiga.

Esta é uma versão de teste funcional para validar o fluxo e a instalação antes de uso com dados reais.

