# Supprime BACK/ml (ancien service Python IA, plus utilisé).
# Fermez d'abord les terminaux Cursor ouverts dans ce dossier, puis exécutez :
#   powershell -ExecutionPolicy Bypass -File delete-ml-folder.ps1

$path = Join-Path $PSScriptRoot 'ml'
if (-not (Test-Path -LiteralPath $path)) {
    Write-Host "Dossier deja supprime : $path"
    exit 0
}

try {
    Remove-Item -LiteralPath $path -Recurse -Force
    Write-Host "OK — dossier supprime : $path"
    exit 0
}
catch {
    Write-Host "Impossible de supprimer (dossier verrouille)."
    Write-Host "1. Fermez les onglets Terminal dans Cursor (ceux ouverts dans BACK\ml)"
    Write-Host "2. Fermez l'Explorateur Windows si ce dossier est ouvert"
    Write-Host "3. Relancez ce script"
    exit 1
}
