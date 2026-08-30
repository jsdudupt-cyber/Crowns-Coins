param(
    [Parameter(Mandatory = $true)]
    [string]$SourcePath
)

$ErrorActionPreference = 'Stop'

Add-Type -AssemblyName System.Drawing

$targetPath = Join-Path $PSScriptRoot '..\src\main\resources\assets\crownscoins\textures\gui\mint_house_menu.png'
$targetDirectory = Split-Path -Parent $targetPath
New-Item -ItemType Directory -Force -Path $targetDirectory | Out-Null

$source = [System.Drawing.Image]::FromFile((Resolve-Path -LiteralPath $SourcePath))
$target = New-Object System.Drawing.Bitmap 720, 540
$graphics = [System.Drawing.Graphics]::FromImage($target)
$graphics.InterpolationMode = [System.Drawing.Drawing2D.InterpolationMode]::NearestNeighbor
$graphics.PixelOffsetMode = [System.Drawing.Drawing2D.PixelOffsetMode]::Half
$graphics.CompositingQuality = [System.Drawing.Drawing2D.CompositingQuality]::HighSpeed
$graphics.SmoothingMode = [System.Drawing.Drawing2D.SmoothingMode]::None
$graphics.DrawImage($source, (New-Object System.Drawing.Rectangle 0, 0, 720, 540))
$graphics.Dispose()
$source.Dispose()

if (Test-Path -LiteralPath $targetPath) {
    Remove-Item -LiteralPath $targetPath -Force
}
$target.Save($targetPath, [System.Drawing.Imaging.ImageFormat]::Png)
$target.Dispose()

Write-Output "Prepared $targetPath"
