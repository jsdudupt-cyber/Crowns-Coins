[CmdletBinding()]
param(
    [Parameter(Mandatory = $true)]
    [string]$ConceptPath
)

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'
Add-Type -AssemblyName System.Drawing

$assetsRoot = Join-Path $PSScriptRoot '..\src\main\resources\assets\crownscoins\textures'
$blockRoot = Join-Path $assetsRoot 'block'
$itemRoot = Join-Path $assetsRoot 'item'
New-Item -ItemType Directory -Force -Path $blockRoot, $itemRoot | Out-Null

$palette = @{
    Void = [System.Drawing.Color]::FromArgb(255, 13, 12, 11)
    WoodDark = [System.Drawing.Color]::FromArgb(255, 38, 22, 12)
    Wood = [System.Drawing.Color]::FromArgb(255, 82, 45, 20)
    WoodWarm = [System.Drawing.Color]::FromArgb(255, 126, 71, 29)
    WoodLight = [System.Drawing.Color]::FromArgb(255, 168, 101, 42)
    IronDark = [System.Drawing.Color]::FromArgb(255, 29, 31, 32)
    Iron = [System.Drawing.Color]::FromArgb(255, 72, 76, 79)
    IronLight = [System.Drawing.Color]::FromArgb(255, 145, 151, 152)
    BrassDark = [System.Drawing.Color]::FromArgb(255, 103, 63, 12)
    Brass = [System.Drawing.Color]::FromArgb(255, 184, 124, 25)
    Gold = [System.Drawing.Color]::FromArgb(255, 249, 190, 49)
    Ember = [System.Drawing.Color]::FromArgb(255, 239, 96, 15)
    Flame = [System.Drawing.Color]::FromArgb(255, 255, 207, 64)
}

function Paint-Rect([System.Drawing.Graphics]$Graphics, [System.Drawing.Color]$Color, [int]$X, [int]$Y, [int]$Width, [int]$Height) {
    $brush = [System.Drawing.SolidBrush]::new($Color)
    try {
        $Graphics.FillRectangle($brush, $X, $Y, $Width, $Height)
    } finally {
        $brush.Dispose()
    }
}

function Paint-Line([System.Drawing.Graphics]$Graphics, [System.Drawing.Color]$Color, [int]$X1, [int]$Y1, [int]$X2, [int]$Y2, [int]$Width = 1) {
    $pen = [System.Drawing.Pen]::new($Color, $Width)
    try {
        $Graphics.DrawLine($pen, $X1, $Y1, $X2, $Y2)
    } finally {
        $pen.Dispose()
    }
}

function Paint-Ellipse([System.Drawing.Graphics]$Graphics, [System.Drawing.Color]$Color, [int]$X, [int]$Y, [int]$Width, [int]$Height) {
    $brush = [System.Drawing.SolidBrush]::new($Color)
    try {
        $Graphics.FillEllipse($brush, $X, $Y, $Width, $Height)
    } finally {
        $brush.Dispose()
    }
}

function Paint-Rivet([System.Drawing.Graphics]$Graphics, [int]$X, [int]$Y) {
    Paint-Rect $Graphics $palette.IronDark $X $Y 3 3
    Paint-Rect $Graphics $palette.IronLight ($X + 1) ($Y + 1) 1 1
}

function Paint-Wood([System.Drawing.Graphics]$Graphics) {
    Paint-Rect $Graphics $palette.WoodDark 0 0 32 32
    foreach ($y in @(2, 9, 16, 23, 30)) {
        Paint-Rect $Graphics $palette.Wood 0 $y 32 5
        Paint-Line $Graphics $palette.WoodDark 0 ($y + 4) 31 ($y + 4)
        Paint-Line $Graphics $palette.WoodWarm 1 ($y + 1) 30 ($y + 1)
    }
    foreach ($x in @(4, 12, 21, 28)) {
        Paint-Rect $Graphics $palette.WoodLight $x 3 1 26
    }
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

Save-BlockTexture 'mint_house_front' {
    param($g)
    Paint-Wood $g
    Paint-Rect $g $palette.IronDark 0 0 32 3
    Paint-Rect $g $palette.Iron 1 1 30 1
    Paint-Rect $g $palette.IronDark 0 28 32 4
    Paint-Rect $g $palette.Iron 1 29 30 1
    Paint-Rect $g $palette.IronDark 0 0 3 32
    Paint-Rect $g $palette.IronDark 29 0 3 32
    foreach ($point in @(@(1, 1), @(28, 1), @(1, 28), @(28, 28))) { Paint-Rivet $g $point[0] $point[1] }

    # Crowned shield on the front, with a dark forge hatch below.
    Paint-Rect $g $palette.IronDark 11 9 10 13
    Paint-Rect $g $palette.BrassDark 12 10 8 11
    Paint-Rect $g $palette.Brass 13 11 6 9
    Paint-Rect $g $palette.WoodDark 14 12 4 6
    Paint-Rect $g $palette.Gold 14 10 4 2
    Paint-Rect $g $palette.Gold 13 12 1 2
    Paint-Rect $g $palette.Gold 18 12 1 2
    Paint-Rect $g $palette.Gold 15 9 1 2
    Paint-Rect $g $palette.Gold 17 9 1 2
    Paint-Rect $g $palette.IronDark 21 18 6 7
    Paint-Rect $g $palette.Iron 22 19 4 5
    Paint-Rect $g $palette.IronLight 23 20 2 1
    Paint-Rect $g $palette.IronDark 4 17 2 8
    Paint-Rect $g $palette.Iron 5 18 1 5
    Paint-Line $g $palette.IronLight 6 19 9 24 1
    Paint-Line $g $palette.IronLight 8 18 5 24 1
}

Save-BlockTexture 'mint_house_top' {
    param($g)
    Paint-Rect $g $palette.WoodDark 0 0 32 32
    Paint-Rect $g $palette.IronDark 0 0 32 3
    Paint-Rect $g $palette.IronDark 0 29 32 3
    Paint-Rect $g $palette.IronDark 0 0 3 32
    Paint-Rect $g $palette.IronDark 29 0 3 32
    Paint-Rect $g $palette.Wood 3 3 26 26
    Paint-Rect $g $palette.WoodWarm 5 5 22 22
    Paint-Ellipse $g $palette.IronDark 7 6 15 15
    Paint-Ellipse $g $palette.Iron 8 7 13 13
    Paint-Ellipse $g $palette.IronDark 10 9 9 9
    Paint-Ellipse $g $palette.IronLight 11 10 7 7
    Paint-Rect $g $palette.IronDark 13 11 3 5
    Paint-Rect $g $palette.IronDark 12 12 5 3
    Paint-Rect $g $palette.WoodDark 6 22 20 5
    Paint-Rect $g $palette.BrassDark 7 23 5 3
    Paint-Rect $g $palette.Iron 13 23 5 3
    Paint-Rect $g $palette.Brass 19 23 5 3
    foreach ($point in @(@(1, 1), @(28, 1), @(1, 28), @(28, 28))) { Paint-Rivet $g $point[0] $point[1] }
}

Save-BlockTexture 'mint_house_side' {
    param($g)
    Paint-Wood $g
    Paint-Rect $g $palette.IronDark 0 0 4 32
    Paint-Rect $g $palette.IronDark 28 0 4 32
    Paint-Rect $g $palette.IronDark 0 0 32 3
    Paint-Rect $g $palette.IronDark 0 29 32 3
    Paint-Ellipse $g $palette.IronDark 10 10 13 13
    Paint-Ellipse $g $palette.BrassDark 11 11 11 11
    Paint-Ellipse $g $palette.Brass 13 13 7 7
    Paint-Rect $g $palette.Gold 15 12 2 9
    Paint-Rect $g $palette.Gold 12 15 9 2
    Paint-Rect $g $palette.IronLight 16 8 1 4
    Paint-Rect $g $palette.IronLight 16 21 1 4
    foreach ($point in @(@(1, 1), @(28, 1), @(1, 28), @(28, 28))) { Paint-Rivet $g $point[0] $point[1] }
}

Save-BlockTexture 'mint_house_back' {
    param($g)
    Paint-Wood $g
    Paint-Rect $g $palette.IronDark 0 0 32 3
    Paint-Rect $g $palette.IronDark 0 29 32 3
    Paint-Rect $g $palette.IronDark 0 0 3 32
    Paint-Rect $g $palette.IronDark 29 0 3 32
    Paint-Line $g $palette.Iron 5 6 27 26 2
    Paint-Line $g $palette.IronDark 5 26 27 6 2
    Paint-Rect $g $palette.IronDark 12 12 8 8
    Paint-Rect $g $palette.Iron 13 13 6 6
    Paint-Rect $g $palette.IronLight 15 15 2 2
    foreach ($point in @(@(1, 1), @(28, 1), @(1, 28), @(28, 28), @(14, 13), @(18, 13))) { Paint-Rivet $g $point[0] $point[1] }
}

Save-BlockTexture 'mint_house_bottom' {
    param($g)
    Paint-Rect $g $palette.WoodDark 0 0 32 32
    foreach ($x in @(2, 9, 16, 23, 30)) {
        Paint-Rect $g $palette.Wood  $x 0 5 32
        Paint-Line $g $palette.WoodLight ($x + 1) 1 ($x + 1) 30
    }
    Paint-Rect $g $palette.IronDark 0 0 32 3
    Paint-Rect $g $palette.IronDark 0 29 32 3
    Paint-Rect $g $palette.IronDark 0 0 3 32
    Paint-Rect $g $palette.IronDark 29 0 3 32
    foreach ($point in @(@(1, 1), @(28, 1), @(1, 28), @(28, 28))) { Paint-Rivet $g $point[0] $point[1] }
}

Save-BlockTexture 'mint_house_metal' {
    param($g)
    Paint-Rect $g $palette.IronDark 0 0 32 32
    foreach ($row in 0..3) {
        foreach ($column in 0..3) {
            $x = $column * 8
            $y = $row * 8
            Paint-Rect $g $palette.Iron ($x + 1) ($y + 1) 6 6
            Paint-Rect $g $palette.IronLight ($x + 2) ($y + 2) 2 1
            Paint-Rect $g $palette.IronDark ($x + 5) ($y + 5) 1 1
        }
    }
}

Save-BlockTexture 'mint_house_press' {
    param($g)
    Paint-Rect $g $palette.IronDark 0 0 32 32
    Paint-Ellipse $g $palette.Iron 3 3 26 26
    Paint-Ellipse $g $palette.IronLight 6 6 20 20
    Paint-Ellipse $g $palette.IronDark 9 9 14 14
    Paint-Rect $g $palette.IronLight 14 11 4 10
    Paint-Rect $g $palette.IronLight 11 14 10 4
    Paint-Rect $g $palette.IronDark 15 12 2 8
    Paint-Rect $g $palette.IronDark 12 15 8 2
}

# The generated concept becomes the actual inventory sprite; placed faces use
# purpose-built 32x32 textures above so every side remains legible in-world.
$resolvedConceptPath = (Resolve-Path -LiteralPath $ConceptPath).Path
$source = [System.Drawing.Image]::FromFile($resolvedConceptPath)
$itemTexture = [System.Drawing.Bitmap]::new(128, 128, [System.Drawing.Imaging.PixelFormat]::Format32bppArgb)
$itemGraphics = [System.Drawing.Graphics]::FromImage($itemTexture)
try {
    $itemGraphics.Clear([System.Drawing.Color]::Transparent)
    $itemGraphics.CompositingMode = [System.Drawing.Drawing2D.CompositingMode]::SourceCopy
    $itemGraphics.InterpolationMode = [System.Drawing.Drawing2D.InterpolationMode]::HighQualityBicubic
    $itemGraphics.PixelOffsetMode = [System.Drawing.Drawing2D.PixelOffsetMode]::HighQuality
    $itemGraphics.DrawImage($source, [System.Drawing.Rectangle]::new(2, 2, 124, 124))
    $itemTexture.Save((Join-Path $itemRoot 'mint_house.png'), [System.Drawing.Imaging.ImageFormat]::Png)
} finally {
    $itemGraphics.Dispose()
    $itemTexture.Dispose()
    $source.Dispose()
}

Write-Output "Created dedicated Mint House faces and inventory texture."
