<#
.SYNOPSIS
    Gera os hashes SHA-256 dos artefatos de distribuicao (instalador .msi e pacote portatil).
.DESCRIPTION
    Script auxiliar para atendimento ao OWASP A08 (Software and Data Integrity Failures) e mitigacao de CWE-494.
    Calcula o hash criptografico SHA-256 e gera os arquivos .sha256 correspondentes na pasta target/dist/.
.EXAMPLE
    .\scripts\gerar_checksums.ps1
#>

$distDir = Join-Path $PSScriptRoot "..\target\dist"

if (-not (Test-Path $distDir)) {
    Write-Warning "Diretorio de distribuicao nao encontrado: $distDir"
    Write-Warning "Execute primeiro o comando de empacotamento Maven (ex.: mvn clean package -Pempacotar-windows ou -Pinstalador-msi -DskipTests)"
    exit 0
}

$msiFile = Join-Path $distDir "Sistema de Escala-1.0.0.msi"
$appImageExe = Join-Path $distDir "Sistema de Escala\Sistema de Escala.exe"

Write-Host "=== Gerador de Checksums SHA-256 (OWASP A08) ===" -ForegroundColor Cyan

$gerouAlgum = $false

if (Test-Path $msiFile) {
    $hashMsi = (Get-FileHash -Path $msiFile -Algorithm SHA256).Hash
    $msiChecksumFile = "$msiFile.sha256"
    "$hashMsi  Sistema de Escala-1.0.0.msi" | Out-File -FilePath $msiChecksumFile -Encoding ascii
    Write-Host "[OK] Instalador MSI:" -ForegroundColor Green
    Write-Host "     Arquivo:  $msiFile"
    Write-Host "     SHA-256:  $hashMsi"
    Write-Host "     Checksum: $msiChecksumFile"
    $gerouAlgum = $true
}

if (Test-Path $appImageExe) {
    $hashExe = (Get-FileHash -Path $appImageExe -Algorithm SHA256).Hash
    $exeChecksumFile = Join-Path $distDir "Sistema de Escala.exe.sha256"
    "$hashExe  Sistema de Escala.exe" | Out-File -FilePath $exeChecksumFile -Encoding ascii
    Write-Host "[OK] Executavel Portatil:" -ForegroundColor Green
    Write-Host "     Arquivo:  $appImageExe"
    Write-Host "     SHA-256:  $hashExe"
    Write-Host "     Checksum: $exeChecksumFile"
    $gerouAlgum = $true
}

if (-not $gerouAlgum) {
    Write-Warning "Nenhum artefato (.msi ou .exe) encontrado em $distDir para calcular o hash."
} else {
    Write-Host "=== Concluido com sucesso ===" -ForegroundColor Cyan
}
