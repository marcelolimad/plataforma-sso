# Publicar uma cópia de teste no Render

Esta configuração cria uma aplicação web e **um banco PostgreSQL novo e separado** no Render. Use somente dados fictícios. Não envie o banco local nem arquivos com senhas para o repositório.

## Arquivos preparados

- `Dockerfile`: compila e inicia o aplicativo Java.
- `render.yaml`: cria um serviço web e um PostgreSQL gratuitos, liga ambos pela rede interna do Render e solicita as credenciais do primeiro ADMIN.
- `application-cloud.properties`: usa a porta fornecida pelo Render e marca o cookie de sessão como seguro para HTTPS.

## Publicação

1. Crie uma conta no Render e um repositório **privado** no seu provedor Git. Envie o código-fonte do projeto, incluindo `pom.xml`, `src/`, `Dockerfile` e `render.yaml`. Não envie senhas, dumps do banco ou dados de pacientes.
2. No painel do Render, escolha **New > Blueprint** e conecte esse repositório. Confira que o plano mostrado para o serviço e o banco é **Free** antes de confirmar.
3. Quando solicitado, informe `SSO_ADMIN_EMAIL` e `SSO_ADMIN_PASSWORD` (senha com pelo menos 12 caracteres). Guarde essas informações em local protegido. Elas criam a primeira conta somente quando o banco está vazio.
4. Aguarde o build e o deploy. Abra o endereço HTTPS `https://sso-hfag-teste.onrender.com` mostrado no painel (o nome exato pode variar), entre com a conta ADMIN e crie contas PROFISSIONAL para os testadores.
5. Faça um teste com atendimentos fictícios: cadastro, evolução, pesquisa e download XLSX. Compartilhe apenas a URL HTTPS e as credenciais individuais de cada testador.

Se o deploy falhar, abra **Logs** do serviço web e confira se o banco foi criado, se as duas variáveis ADMIN foram preenchidas e se o `DATABASE_URL` veio do PostgreSQL do próprio Blueprint. O aplicativo não usa o PostgreSQL do micro central nesse perfil.

## Limites do plano gratuito

O serviço gratuito pode suspender após ficar ocioso e demorar para atender a primeira visita. O PostgreSQL gratuito do Render expira após **30 dias** e não oferece backups. Portanto, este ambiente serve apenas para avaliação temporária com dados fictícios. Antes de usar dados reais, defina hospedagem apropriada, retenção de dados, backups e revisão de acesso.

Referências: [Blueprints](https://render.com/docs/blueprint-spec), [Docker no Render](https://render.com/docs/docker), [limites gratuitos](https://render.com/docs/free).
