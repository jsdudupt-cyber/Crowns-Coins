$ErrorActionPreference = 'Stop'

Add-Type -AssemblyName System.Drawing

$assetsRoot = Join-Path $PSScriptRoot '..\src\main\resources\assets\crownscoins\textures'
$sourceDirectory = Join-Path $assetsRoot 'item\overlay\symbol'
$targetDirectory = Join-Path $assetsRoot 'gui\catalog'
New-Item -ItemType Directory -Force -Path $targetDirectory | Out-Null

$metals = @{
    bronze = @(196, 118, 48)
    iron = @(214, 220, 228)
    gold = @(255, 205, 62)
}

Get-ChildItem -LiteralPath $sourceDirectory -Filter '*.png' | ForEach-Object {
    $source = [System.Drawing.Bitmap]::FromFile($_.FullName)
    try {
        foreach ($metal in $metals.Keys) {
            $target = New-Object System.Drawing.Bitmap $source.Width, $source.Height
            try {
                for ($x = 0; $x -lt $source.Width; $x++) {
                    for ($y = 0; $y -lt $source.Height; $y++) {
                        $pixel = $source.GetPixel($x, $y)
                        if ($pixel.A -eq 0) {
                            $target.SetPixel($x, $y, $pixel)
                            continue
                        }
                        $brightness = (($pixel.R + $pixel.G + $pixel.B) / 765.0)
                        $shade = 0.45 + (0.55 * $brightness)
                        $base = $metals[$metal]
                        $red = [Math]::Min(255, [Math]::Round($base[0] * $shade))
                        $green = [Math]::Min(255, [Math]::Round($base[1] * $shade))
                        $blue = [Math]::Min(255, [Math]::Round($base[2] * $shade))
                        $target.SetPixel($x, $y, [System.Drawing.Color]::FromArgb($pixel.A, $red, $green, $blue))
                    }
                }
                $target.Save((Join-Path $targetDirectory ("{0}_{1}" -f $metal, $_.Name)), [System.Drawing.Imaging.ImageFormat]::Png)
            } finally {
                $target.Dispose()
            }
        }
    } finally {
        $source.Dispose()
    }
}

Write-Output "Prepared 75 metal-specific catalogue symbols in $targetDirectory"
