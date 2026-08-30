[CmdletBinding()]
param(
    [string]$ResourceRoot = (Join-Path $PSScriptRoot '..\src\main\resources')
)

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'
Add-Type -AssemblyName System.Drawing

$symbolNames = @(
    'sun', 'moon', 'star', 'crown', 'sword', 'shield', 'tower', 'dragon', 'wolf', 'eagle',
    'lion', 'horse', 'hammer', 'anvil', 'heart', 'flame', 'wave', 'leaf', 'flower', 'diamond',
    'mountain', 'river', 'cross', 'lightning', 'compass'
)

$assetsRoot = Join-Path $ResourceRoot 'assets/crownscoins'
$coinRoot = Join-Path $assetsRoot 'textures/item/coin'
$overlayRoot = Join-Path $assetsRoot 'textures/item/overlay'
$outputRoot = Join-Path $assetsRoot 'textures/gui/catalog_coin'

function Get-VisibleBounds([System.Drawing.Bitmap]$Bitmap) {
    $left = $Bitmap.Width
    $top = $Bitmap.Height
    $right = -1
    $bottom = -1
    for ($x = 0; $x -lt $Bitmap.Width; $x++) {
        for ($y = 0; $y -lt $Bitmap.Height; $y++) {
            if ($Bitmap.GetPixel($x, $y).A -gt 0) {
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

function New-TintedBitmap([System.Drawing.Bitmap]$Source, [System.Drawing.Color]$Tint) {
    $result = [System.Drawing.Bitmap]::new($Source.Width, $Source.Height, [System.Drawing.Imaging.PixelFormat]::Format32bppArgb)
    for ($x = 0; $x -lt $Source.Width; $x++) {
        for ($y = 0; $y -lt $Source.Height; $y++) {
            $pixel = $Source.GetPixel($x, $y)
            if ($pixel.A -gt 0) {
                $result.SetPixel($x, $y, [System.Drawing.Color]::FromArgb($pixel.A, $Tint.R, $Tint.G, $Tint.B))
            }
        }
    }
    return $result
}

function Draw-NormalizedSymbol(
    [System.Drawing.Graphics]$Graphics,
    [System.Drawing.Bitmap]$Bitmap,
    [int]$X,
    [int]$Y,
    [int]$Size
) {
    $bounds = Get-VisibleBounds $Bitmap
    if ($bounds.IsEmpty) {
        return
    }
    $scale = [Math]::Min($Size / $bounds.Width, $Size / $bounds.Height)
    $width = [Math]::Max(1, [int][Math]::Round($bounds.Width * $scale))
    $height = [Math]::Max(1, [int][Math]::Round($bounds.Height * $scale))
    $destination = [System.Drawing.Rectangle]::new(
        $X + [int](($Size - $width) / 2),
        $Y + [int](($Size - $height) / 2),
        $width,
        $height
    )
    $Graphics.DrawImage($Bitmap, $destination, $bounds, [System.Drawing.GraphicsUnit]::Pixel)
}

$metals = @(
    [pscustomobject]@{ Name = 'bronze'; Base = 'copper_04_crown.png'; SymbolTint = [System.Drawing.Color]::FromArgb(255, 232, 159, 79) },
    [pscustomobject]@{ Name = 'iron';   Base = 'iron_04_crown.png';   SymbolTint = [System.Drawing.Color]::FromArgb(255, 222, 229, 235) },
    [pscustomobject]@{ Name = 'gold';   Base = 'gold_04_crown.png';   SymbolTint = [System.Drawing.Color]::FromArgb(255, 255, 220, 67) }
)
[void][System.IO.Directory]::CreateDirectory($outputRoot)

foreach ($metal in $metals) {
    $basePath = Join-Path $coinRoot $metal.Base
    $base = [System.Drawing.Bitmap]::new($basePath)
    # Catalogue coins deliberately use the same fully positioned overlays as
    # the item itself. A player can now recognise the big fixed Crown and the
    # one candidate side mark before clicking a tile.
    $crown = [System.Drawing.Bitmap]::new((Join-Path $overlayRoot 'crest_center/04_crown.png'))
    try {
        foreach ($index in 0..($symbolNames.Count - 1)) {
            $name = ('{0:D2}_{1}' -f ($index + 1), $symbolNames[$index])
            $symbol = [System.Drawing.Bitmap]::new((Join-Path $overlayRoot "symbol_left/$name.png"))
            $target = [System.Drawing.Bitmap]::new(32, 32, [System.Drawing.Imaging.PixelFormat]::Format32bppArgb)
            $graphics = [System.Drawing.Graphics]::FromImage($target)
            try {
                $graphics.Clear([System.Drawing.Color]::Transparent)
                $graphics.CompositingMode = [System.Drawing.Drawing2D.CompositingMode]::SourceCopy
                $graphics.DrawImageUnscaled($base, 0, 0)
                # SourceOver is essential: transparent pixels in an overlay
                # must not erase the coin below.
                $graphics.CompositingMode = [System.Drawing.Drawing2D.CompositingMode]::SourceOver
                $graphics.CompositingQuality = [System.Drawing.Drawing2D.CompositingQuality]::HighSpeed
                $graphics.InterpolationMode = [System.Drawing.Drawing2D.InterpolationMode]::NearestNeighbor
                $graphics.PixelOffsetMode = [System.Drawing.Drawing2D.PixelOffsetMode]::Half

                $graphics.DrawImageUnscaled($crown, 0, 0)
                $graphics.DrawImageUnscaled($symbol, 0, 0)

                $target.Save((Join-Path $outputRoot ("{0}_{1}.png" -f $metal.Name, $name)), [System.Drawing.Imaging.ImageFormat]::Png)
            } finally {
                $graphics.Dispose()
                $target.Dispose()
                $symbol.Dispose()
            }
        }
    } finally {
        $crown.Dispose()
        $base.Dispose()
    }
}

Write-Output "Generated 75 readable Crown-and-symbol catalogue coin textures under $outputRoot"
