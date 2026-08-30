$ErrorActionPreference = 'Stop'

Add-Type -AssemblyName System.Drawing

$assetsRoot = Join-Path $PSScriptRoot '..\src\main\resources\assets\crownscoins\textures'
$coinDirectory = Join-Path $assetsRoot 'item\coin'
$crestPath = Join-Path $assetsRoot 'item\overlay\crest_center\04_crown.png'
$symbolDirectory = Join-Path $assetsRoot 'item\overlay\symbol_right'
$targetDirectory = Join-Path $assetsRoot 'gui\catalog_coin'
New-Item -ItemType Directory -Force -Path $targetDirectory | Out-Null

$metals = @{
    bronze = 'copper'
    iron = 'iron'
    gold = 'gold'
}

$crown = [System.Drawing.Bitmap]::FromFile($crestPath)
try {
    Get-ChildItem -LiteralPath $symbolDirectory -Filter '*.png' | Sort-Object Name | ForEach-Object {
        $symbol = [System.Drawing.Bitmap]::FromFile($_.FullName)
        try {
            foreach ($metalName in $metals.Keys) {
                $basePath = Join-Path $coinDirectory ("{0}_04_crown.png" -f $metals[$metalName])
                $base = [System.Drawing.Bitmap]::FromFile($basePath)
                $target = [System.Drawing.Bitmap]::new(32, 32, [System.Drawing.Imaging.PixelFormat]::Format32bppArgb)
                try {
                    $graphics = [System.Drawing.Graphics]::FromImage($target)
                    try {
                        $graphics.CompositingMode = [System.Drawing.Drawing2D.CompositingMode]::SourceCopy
                        $graphics.InterpolationMode = [System.Drawing.Drawing2D.InterpolationMode]::NearestNeighbor
                        $graphics.PixelOffsetMode = [System.Drawing.Drawing2D.PixelOffsetMode]::Half
                        $graphics.SmoothingMode = [System.Drawing.Drawing2D.SmoothingMode]::None
                        # A catalog card is a true miniature of the finished
                        # coin: fixed crown in the centre + one candidate mark.
                        $graphics.DrawImageUnscaled($base, 0, 0)
                        $graphics.DrawImageUnscaled($crown, 0, 0)
                        $graphics.DrawImageUnscaled($symbol, 0, 0)
                    } finally {
                        $graphics.Dispose()
                    }
                    $target.Save((Join-Path $targetDirectory ("{0}_{1}" -f $metalName, $_.Name)), [System.Drawing.Imaging.ImageFormat]::Png)
                } finally {
                    $base.Dispose()
                    $target.Dispose()
                }
            }
        } finally {
            $symbol.Dispose()
        }
    }
} finally {
    $crown.Dispose()
}

Write-Output "Prepared 75 crown-centred coin catalogue thumbnails in $targetDirectory"
