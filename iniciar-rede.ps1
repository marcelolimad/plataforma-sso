# Execute no micro central, na mesma pasta do arquivo .jar.
$ErrorActionPreference = 'Stop'
if (-not (Get-Command java -ErrorAction SilentlyContinue)) {
    throw 'Java 17 ou superior não foi encontrado no PATH.'
}
if ([string]::IsNullOrWhiteSpace($env:SSO_DB_PASSWORD)) {
    throw 'Defina SSO_DB_PASSWORD antes de iniciar. Não grave a senha neste script.'
}
$jar = Join-Path $PSScriptRoot 'plataforma-sso-0.1.0.jar'
if (-not (Test-Path -LiteralPath $jar)) {
    throw "Arquivo .jar não encontrado: $jar"
}
Write-Host 'Iniciando SSO_HFAG para a rede interna. Mantenha esta janela aberta.'
& java -jar $jar --spring.profiles.active=rede
if ($LASTEXITCODE -ne 0) { throw "A aplicação encerrou com código $LASTEXITCODE." }
