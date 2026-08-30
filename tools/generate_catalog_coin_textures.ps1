$ErrorActionPreference = 'Stop'

Add-Type -AssemblyName System.Drawing

$assetsRoot = Join-Path $PSScriptRoot '..\src\main\resources\assets\crownscoins\textures'
$coinDirectory = Join-Path $assetsRoot 'item\coin'
$symbolDirectory = Join-Path $assetsRoot 'item\overlay\symbol'
$crownPath = Join-Path $assetsRoot 'item\overlay\crest_center\04_crown.png'
$targetDirectory = Join-Path $assetsRoot 'gui\catalog_coin'
New-Item -ItemType Directory -Force -Path $targetDirectory | Out-Null

$metals = @{
    bronze = @{
        coin = 'copper'
        face = [System.Drawing.Color]::FromArgb(255, 126, 61, 24)
        inner = [System.Drawing.Color]::FromArgb(255, 181, 93, 40)
        highlight = [System.Drawing.Color]::FromArgb(255, 242, 177, 98)
    }
    iron = @{
        coin = 'iron'
        face = [System.Drawing.Color]::FromArgb(255, 61, 67, 78)
        inner = [System.Drawing.Color]::FromArgb(255, 113, 124, 140)
        highlight = [System.Drawing.Color]::FromArgb(255, 238, 241, 243)
    }
    gold = @{
        coin = 'gold'
        face = [System.Drawing.Color]::FromArgb(255, 118, 79, 7)
        inner = [System.Drawing.Color]::FromArgb(255, 198, 144, 13)
        highlight = [System.Drawing.Color]::FromArgb(255, 255, 228, 122)
    }
}

function Get-VisibleBounds([System.Drawing.Bitmap]$bitmap) {
    $left = $bitmap.Width
    $top = $bitmap.Height
    $right = -1
    $bottom = -1
    for ($x = 0; $x -lt $bitmap.Width; $x++) {
        for ($y = 0; $y -lt $bitmap.Height; $y++) {
            if ($bitmap.GetPixel($x, $y).A -gt 0) {
                $left = [Math]::Min($left, $x)
                $top = [Math]::Min($top, $y)
                $right = [Math]::Max($right, $x)
                $bottom = [Math]::Max($bottom, $y)
            }
        }
    }
    if ($right -lt $left -or $bottom -lt $top) {
        return [System.Drawing.Rectangle]::Empty
    }
    return [System.Drawing.Rectangle]::FromLTRB($left, $top, $right + 1, $bottom + 1)
}

function Draw-NormalizedSymbol(
    [System.Drawing.Graphics]$graphics,
    [System.Drawing.Bitmap]$bitmap,
    [int]$x,
    [int]$y,
    [int]$size
) {
    $bounds = Get-VisibleBounds $bitmap
    if ($bounds.IsEmpty) {
        return
    }
    $scale = [Math]::Min($size / $bounds.Width, $size / $bounds.Height)
    $width = [Math]::Max(1, [int][Math]::Round($bounds.Width * $scale))
    $height = [Math]::Max(1, [int][Math]::Round($bounds.Height * $scale))
    $destination = New-Object System.Drawing.Rectangle ($x + [int](($size - $width) / 2)), ($y + [int](($size - $height) / 2)), $width, $height
    $graphics.DrawImage($bitmap, $destination, $bounds, [System.Drawing.GraphicsUnit]::Pixel)
}

$crown = [System.Drawing.Bitmap]::FromFile($crownPath)
try {
    Get-ChildItem -LiteralPath $symbolDirectory -Filter '*.png' | Sort-Object Name | ForEach-Object {
        $symbol = [System.Drawing.Bitmap]::FromFile($_.FullName)
        try {
            foreach ($metalName in $metals.Keys) {
                $metal = $metals[$metalName]
                $basePath = Join-Path $coinDirectory ("{0}_04_crown.png" -f $metal.coin)
                $base = [System.Drawing.Bitmap]::FromFile($basePath)
                $target = New-Object System.Drawing.Bitmap 32, 32, ([System.Drawing.Imaging.PixelFormat]::Format32bppArgb)
                try {
                    $graphics = [System.Drawing.Graphics]::FromImage($target)
                    try {
                        $graphics.InterpolationMode = [System.Drawing.Drawing2D.InterpolationMode]::NearestNeighbor
                        $graphics.PixelOffsetMode = [System.Drawing.Drawing2D.PixelOffsetMode]::Half
                        $graphics.CompositingQuality = [System.Drawing.Drawing2D.CompositingQuality]::HighSpeed
                        $graphics.SmoothingMode = [System.Drawing.Drawing2D.SmoothingMode]::None

                        $faceBrush = New-Object System.Drawing.SolidBrush $metal.face
                        $innerBrush = New-Object System.Drawing.SolidBrush $metal.inner
                        $highlightPen = New-Object System.Drawing.Pen $metal.highlight, 1
                        try {
                            $graphics.FillEllipse($faceBrush, 2, 2, 28, 28)
                            $graphics.FillEllipse($innerBrush, 5, 5, 22, 22)
                            $graphics.DrawEllipse($highlightPen, 4, 4, 23, 23)
                        } finally {
                            $faceBrush.Dispose()
                            $innerBrush.Dispose()
                            $highlightPen.Dispose()
                        }
                        $graphics.DrawImage($base, 0, 0, 32, 32)
                        # The fixed crown establishes the kingdom's default mark;
                        # each card then shows one clearly centred candidate symbol.
                        Draw-NormalizedSymbol $graphics $crown 10 5 12
                        Draw-NormalizedSymbol $graphics $symbol 11 17 11
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

Write-Output "Prepared 75 aligned crowned-coin catalogue thumbnails in $targetDirectory"
