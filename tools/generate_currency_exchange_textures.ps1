[CmdletBinding()]
param()

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'
Add-Type -AssemblyName System.Drawing

# Native pixel-art assets for the Currency Exchange.  Keeping this generator
# beside the Mint House generator makes each face reproducible and avoids
# relying on vanilla smithing-table art.
$assetsRoot = Join-Path $PSScriptRoot '..\src\main\resources\assets\crownscoins\textures'
$blockRoot = Join-Path $assetsRoot 'block'
$itemRoot = Join-Path $assetsRoot 'item'
New-Item -ItemType Directory -Force -Path $blockRoot, $itemRoot | Out-Null

$palette = @{
    Transparent = [System.Drawing.Color]::Transparent
    WoodDark = [System.Drawing.Color]::FromArgb(255, 65, 34, 14)
    Wood = [System.Drawing.Color]::FromArgb(255, 105, 56, 22)
    WoodWarm = [System.Drawing.Color]::FromArgb(255, 155, 82, 30)
    WoodLight = [System.Drawing.Color]::FromArgb(255, 204, 132, 53)
    IronDark = [System.Drawing.Color]::FromArgb(255, 54, 57, 61)
    Iron = [System.Drawing.Color]::FromArgb(255, 100, 106, 112)
    IronLight = [System.Drawing.Color]::FromArgb(255, 196, 202, 205)
    CopperDark = [System.Drawing.Color]::FromArgb(255, 104, 46, 13)
    Copper = [System.Drawing.Color]::FromArgb(255, 205, 93, 30)
    CopperLight = [System.Drawing.Color]::FromArgb(255, 247, 152, 71)
    BrassDark = [System.Drawing.Color]::FromArgb(255, 116, 67, 11)
    Brass = [System.Drawing.Color]::FromArgb(255, 205, 135, 21)
    Gold = [System.Drawing.Color]::FromArgb(255, 255, 208, 78)
}

function Paint-Rect([System.Drawing.Graphics]$Graphics, [System.Drawing.Color]$Color, [int]$X, [int]$Y, [int]$Width, [int]$Height) {
    $brush = [System.Drawing.SolidBrush]::new($Color)
    try { $Graphics.FillRectangle($brush, $X, $Y, $Width, $Height) } finally { $brush.Dispose() }
}

function Paint-Line([System.Drawing.Graphics]$Graphics, [System.Drawing.Color]$Color, [int]$X1, [int]$Y1, [int]$X2, [int]$Y2, [int]$Width = 1) {
    $pen = [System.Drawing.Pen]::new($Color, $Width)
    try { $Graphics.DrawLine($pen, $X1, $Y1, $X2, $Y2) } finally { $pen.Dispose() }
}

function Paint-Ellipse([System.Drawing.Graphics]$Graphics, [System.Drawing.Color]$Color, [int]$X, [int]$Y, [int]$Width, [int]$Height) {
    $brush = [System.Drawing.SolidBrush]::new($Color)
    try { $Graphics.FillEllipse($brush, $X, $Y, $Width, $Height) } finally { $brush.Dispose() }
}

function Paint-Rivet([System.Drawing.Graphics]$Graphics, [int]$X, [int]$Y) {
    Paint-Rect $Graphics $palette.IronDark $X $Y 3 3
    Paint-Rect $Graphics $palette.IronLight ($X + 1) ($Y + 1) 1 1
}

function Paint-Wood([System.Drawing.Graphics]$Graphics, [int]$Size = 32) {
    Paint-Rect $Graphics $palette.WoodDark 0 0 $Size $Size
    for ($y = 1; $y -lt $Size; $y += 7) {
        $height = [Math]::Min(6, $Size - $y)
        Paint-Rect $Graphics $palette.Wood 0 $y $Size $height
        Paint-Line $Graphics $palette.WoodDark 0 ($y + $height - 1) ($Size - 1) ($y + $height - 1)
        if ($height -gt 2) { Paint-Line $Graphics $palette.WoodWarm 1 ($y + 1) ($Size - 2) ($y + 1) }
    }
}

function Paint-Frame([System.Drawing.Graphics]$Graphics, [int]$Size = 32) {
    Paint-Rect $Graphics $palette.IronDark 0 0 $Size 2
    Paint-Rect $Graphics $palette.IronDark 0 ($Size - 2) $Size 2
    Paint-Rect $Graphics $palette.IronDark 0 0 2 $Size
    Paint-Rect $Graphics $palette.IronDark ($Size - 2) 0 2 $Size
    Paint-Line $Graphics $palette.Iron 2 1 ($Size - 3) 1
    Paint-Line $Graphics $palette.Iron 2 ($Size - 2) ($Size - 3) ($Size - 2)
    $lastRivet = $Size - 4
    foreach ($point in @(@(1, 1), @($lastRivet, 1), @(1, $lastRivet), @($lastRivet, $lastRivet))) {
        Paint-Rivet $Graphics $point[0] $point[1]
    }
}

function Paint-SwapMark([System.Drawing.Graphics]$Graphics, [int]$X, [int]$Y, [int]$Scale = 1) {
    # Two compact arrows.  It is intentionally pictographic so the block works
    # in every language and still reads as a place of exchange at 32 pixels.
    Paint-Rect $Graphics $palette.Gold ($X + 1 * $Scale) ($Y + 1 * $Scale) (6 * $Scale) (2 * $Scale)
    Paint-Rect $Graphics $palette.Gold ($X + 5 * $Scale) ($Y - 1 * $Scale) (2 * $Scale) (6 * $Scale)
    Paint-Rect $Graphics $palette.Gold ($X + 4 * $Scale) ($Y + 4 * $Scale) (2 * $Scale) (2 * $Scale)
    Paint-Rect $Graphics $palette.Brass ($X + 2 * $Scale) ($Y + 7 * $Scale) (6 * $Scale) (2 * $Scale)
    Paint-Rect $Graphics $palette.Brass ($X + 2 * $Scale) ($Y + 5 * $Scale) (2 * $Scale) (6 * $Scale)
    Paint-Rect $Graphics $palette.Brass ($X + 3 * $Scale) ($Y + 4 * $Scale) (2 * $Scale) (2 * $Scale)
}

function Save-BlockTexture([string]$Name, [scriptblock]$Painter) {
    $bitmap = [System.Drawing.Bitmap]::new(32, 32, [System.Drawing.Imaging.PixelFormat]::Format32bppArgb)
    $graphics = [System.Drawing.Graphics]::FromImage($bitmap)
    try {
        $graphics.CompositingMode = [System.Drawing.Drawing2D.CompositingMode]::SourceCopy
        $graphics.InterpolationMode = [System.Drawing.Drawing2D.InterpolationMode]::NearestNeighbor
        $graphics.PixelOffsetMode = [System.Drawing.Drawing2D.PixelOffsetMode]::Half
        $graphics.SmoothingMode = [System.Drawing.Drawing2D.SmoothingMode]::None
        & $Painter $graphics
        $bitmap.Save((Join-Path $blockRoot "$Name.png"), [System.Drawing.Imaging.ImageFormat]::Png)
    } finally {
        $graphics.Dispose()
        $bitmap.Dispose()
    }
}

Save-BlockTexture 'currency_exchange_front' {
    param($g)
    Paint-Wood $g
    Paint-Frame $g
    Paint-Rect $g $palette.IronDark 5 7 22 13
    Paint-Rect $g $palette.Iron 6 8 20 11
    Paint-Ellipse $g $palette.CopperDark 7 10 7 7
    Paint-Ellipse $g $palette.Copper 8 11 5 5
    Paint-Ellipse $g $palette.IronDark 18 10 7 7
    Paint-Ellipse $g $palette.IronLight 19 11 5 5
    Paint-SwapMark $g 12 10
    Paint-Rect $g $palette.BrassDark 9 22 14 5
    Paint-Rect $g $palette.WoodWarm 10 23 12 3
    Paint-Rect $g $palette.Gold 15 23 2 2
}

Save-BlockTexture 'currency_exchange_top' {
    param($g)
    Paint-Wood $g
    Paint-Frame $g
    Paint-Rect $g $palette.WoodWarm 3 3 26 26
    Paint-Ellipse $g $palette.IronDark 7 7 18 18
    Paint-Ellipse $g $palette.Iron 8 8 16 16
    Paint-Ellipse $g $palette.BrassDark 10 10 12 12
    Paint-Ellipse $g $palette.Brass 11 11 10 10
    Paint-Ellipse $g $palette.Copper 12 12 8 8
    Paint-Ellipse $g $palette.CopperLight 14 13 4 4
    Paint-SwapMark $g 11 21
}

Save-BlockTexture 'currency_exchange_side' {
    param($g)
    Paint-Wood $g
    Paint-Frame $g
    Paint-Ellipse $g $palette.IronDark 9 9 15 15
    Paint-Ellipse $g $palette.Iron 10 10 13 13
    Paint-Ellipse $g $palette.BrassDark 12 12 9 9
    Paint-Ellipse $g $palette.Brass 13 13 7 7
    Paint-Rect $g $palette.Gold 15 11 2 11
    Paint-Rect $g $palette.Gold 11 15 11 2
    Paint-Rect $g $palette.IronDark 2 20 8 3
    Paint-Rect $g $palette.WoodWarm 3 21 6 1
    Paint-Rect $g $palette.Iron 8 18 3 7
}

Save-BlockTexture 'currency_exchange_back' {
    param($g)
    Paint-Wood $g
    Paint-Frame $g
    Paint-Line $g $palette.Iron 5 6 27 26 2
    Paint-Line $g $palette.IronLight 6 6 26 25 1
    Paint-Line $g $palette.Iron 5 26 27 6 2
    Paint-Line $g $palette.IronLight 6 25 26 6 1
    Paint-Rect $g $palette.BrassDark 13 13 6 6
    Paint-Rect $g $palette.Brass 14 14 4 4
}

Save-BlockTexture 'currency_exchange_bottom' {
    param($g)
    Paint-Rect $g $palette.WoodDark 0 0 32 32
    foreach ($x in @(2, 9, 16, 23, 30)) {
        Paint-Rect $g $palette.Wood $x 0 5 32
        Paint-Line $g $palette.WoodLight ($x + 1) 1 ($x + 1) 30
    }
    Paint-Frame $g
}

Save-BlockTexture 'currency_exchange_metal' {
    param($g)
    Paint-Rect $g $palette.Iron 0 0 32 32
    foreach ($row in 0..3) {
        foreach ($column in 0..3) {
            $x = $column * 8
            $y = $row * 8
            Paint-Rect $g $palette.IronDark $x $y 8 1
            Paint-Rect $g $palette.IronDark $x $y 1 8
            Paint-Rect $g $palette.IronLight ($x + 1) ($y + 1) 6 2
            Paint-Rect $g $palette.IronDark ($x + 5) ($y + 5) 2 2
        }
    }
}

Save-BlockTexture 'currency_exchange_dial' {
    param($g)
    Paint-Rect $g $palette.Iron 0 0 32 32
    Paint-Ellipse $g $palette.IronDark 3 3 26 26
    Paint-Ellipse $g $palette.IronLight 6 6 20 20
    Paint-Ellipse $g $palette.BrassDark 9 9 14 14
    Paint-Ellipse $g $palette.Brass 10 10 12 12
    Paint-SwapMark $g 11 11
}

function Save-ExchangeItem() {
    $bitmap = [System.Drawing.Bitmap]::new(64, 64, [System.Drawing.Imaging.PixelFormat]::Format32bppArgb)
    $graphics = [System.Drawing.Graphics]::FromImage($bitmap)
    try {
        $graphics.Clear($palette.Transparent)
        $graphics.CompositingMode = [System.Drawing.Drawing2D.CompositingMode]::SourceCopy
        $graphics.InterpolationMode = [System.Drawing.Drawing2D.InterpolationMode]::NearestNeighbor
        $graphics.PixelOffsetMode = [System.Drawing.Drawing2D.PixelOffsetMode]::Half
        $graphics.SmoothingMode = [System.Drawing.Drawing2D.SmoothingMode]::None
        Paint-Rect $graphics $palette.IronDark 8 28 48 24
        Paint-Rect $graphics $palette.Iron 10 29 44 21
        Paint-Rect $graphics $palette.WoodDark 12 32 40 16
        Paint-Rect $graphics $palette.Wood 14 33 36 14
        Paint-Rect $graphics $palette.IronDark 6 23 52 8
        Paint-Rect $graphics $palette.Iron 8 24 48 5
        Paint-Rect $graphics $palette.WoodWarm 11 20 42 4
        Paint-Ellipse $graphics $palette.IronDark 19 8 26 22
        Paint-Ellipse $graphics $palette.Iron 21 9 22 19
        Paint-Ellipse $graphics $palette.BrassDark 24 12 16 13
        Paint-Ellipse $graphics $palette.Brass 25 13 14 11
        Paint-Ellipse $graphics $palette.Copper 11 15 13 11
        Paint-Ellipse $graphics $palette.CopperLight 14 17 7 6
        Paint-Ellipse $graphics $palette.Gold 40 15 13 11
        Paint-Ellipse $graphics $palette.Brass 43 17 7 6
        Paint-SwapMark $graphics 28 34 2
        foreach ($point in @(@(8, 28), @(52, 28), @(8, 48), @(52, 48))) { Paint-Rivet $graphics $point[0] $point[1] }
        $bitmap.Save((Join-Path $itemRoot 'currency_exchange.png'), [System.Drawing.Imaging.ImageFormat]::Png)
    } finally {
        $graphics.Dispose()
        $bitmap.Dispose()
    }
}

function Save-CopperNugget() {
    $bitmap = [System.Drawing.Bitmap]::new(16, 16, [System.Drawing.Imaging.PixelFormat]::Format32bppArgb)
    $graphics = [System.Drawing.Graphics]::FromImage($bitmap)
    try {
        $graphics.Clear($palette.Transparent)
        $graphics.CompositingMode = [System.Drawing.Drawing2D.CompositingMode]::SourceCopy
        $graphics.SmoothingMode = [System.Drawing.Drawing2D.SmoothingMode]::None
        # An irregular, compact lump: deliberately distinct from raw copper ore.
        Paint-Rect $graphics $palette.CopperDark 4 4 8 8
        Paint-Rect $graphics $palette.CopperDark 3 6 10 5
        Paint-Rect $graphics $palette.CopperDark 5 3 5 10
        Paint-Rect $graphics $palette.Copper 5 5 6 6
        Paint-Rect $graphics $palette.Copper 4 7 8 3
        Paint-Rect $graphics $palette.CopperLight 6 5 3 2
        Paint-Rect $graphics $palette.CopperLight 5 7 2 2
        Paint-Rect $graphics $palette.Brass 9 9 2 1
        $bitmap.Save((Join-Path $itemRoot 'copper_nugget.png'), [System.Drawing.Imaging.ImageFormat]::Png)
    } finally {
        $graphics.Dispose()
        $bitmap.Dispose()
    }
}

Save-ExchangeItem
Save-CopperNugget
Write-Output 'Created Currency Exchange faces, inventory sprite, and copper nugget texture.'
