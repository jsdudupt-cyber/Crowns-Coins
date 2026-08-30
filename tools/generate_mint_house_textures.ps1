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
    # Dark values still read as iron/wood rather than broad black voids once the
    # small faces are shaded by Minecraft itself.
    Void = [System.Drawing.Color]::FromArgb(255, 34, 22, 13)
    WoodDark = [System.Drawing.Color]::FromArgb(255, 65, 34, 14)
    Wood = [System.Drawing.Color]::FromArgb(255, 105, 56, 22)
    WoodWarm = [System.Drawing.Color]::FromArgb(255, 155, 82, 30)
    WoodLight = [System.Drawing.Color]::FromArgb(255, 204, 132, 53)
    IronDark = [System.Drawing.Color]::FromArgb(255, 54, 57, 61)
    Iron = [System.Drawing.Color]::FromArgb(255, 100, 106, 112)
    IronLight = [System.Drawing.Color]::FromArgb(255, 196, 202, 205)
    BrassDark = [System.Drawing.Color]::FromArgb(255, 116, 67, 11)
    Brass = [System.Drawing.Color]::FromArgb(255, 205, 135, 21)
    Gold = [System.Drawing.Color]::FromArgb(255, 255, 208, 78)
    Ember = [System.Drawing.Color]::FromArgb(255, 250, 118, 18)
    Flame = [System.Drawing.Color]::FromArgb(255, 255, 228, 96)
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
    foreach ($y in @(1, 8, 15, 22, 29)) {
        Paint-Rect $Graphics $palette.Wood 0 $y 32 6
        Paint-Line $Graphics $palette.WoodDark 0 ($y + 5) 31 ($y + 5)
        Paint-Line $Graphics $palette.WoodWarm 1 ($y + 1) 30 ($y + 1)
    }
    foreach ($x in @(5, 15, 25)) {
        Paint-Line $Graphics $palette.WoodLight $x 3 $x 28
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
    Paint-Rect $g $palette.IronDark 0 0 32 2
    Paint-Rect $g $palette.Iron 1 1 30 1
    Paint-Rect $g $palette.IronDark 0 30 32 2
    Paint-Rect $g $palette.Iron 1 30 30 1
    Paint-Rect $g $palette.IronDark 0 0 2 32
    Paint-Rect $g $palette.IronDark 30 0 2 32
    foreach ($point in @(@(1, 1), @(28, 1), @(1, 28), @(28, 28))) { Paint-Rivet $g $point[0] $point[1] }

    # A bright crowned maker's plate and drawer keep the cabinet recognisable
    # at Minecraft's native scale without a large empty black hatch.
    Paint-Rect $g $palette.IronDark 10 6 12 13
    Paint-Rect $g $palette.Iron 11 7 10 11
    Paint-Rect $g $palette.BrassDark 12 8 8 9
    Paint-Rect $g $palette.Brass 13 9 6 7
    Paint-Rect $g $palette.Gold 14 8 4 2
    Paint-Rect $g $palette.Gold 13 10 1 2
    Paint-Rect $g $palette.Gold 18 10 1 2
    Paint-Rect $g $palette.Gold 15 7 1 2
    Paint-Rect $g $palette.Gold 17 7 1 2
    Paint-Rect $g $palette.IronDark 5 21 22 7
    Paint-Rect $g $palette.WoodWarm 6 22 20 5
    Paint-Line $g $palette.WoodLight 7 23 24 23
    Paint-Rect $g $palette.BrassDark 14 23 4 3
    Paint-Rect $g $palette.Gold 15 24 2 1
    Paint-Rect $g $palette.Iron 4 14 2 10
    Paint-Rect $g $palette.Iron 26 14 2 10
    Paint-Rivet $g 4 17
    Paint-Rivet $g 25 17
}

Save-BlockTexture 'mint_house_top' {
    param($g)
    Paint-Wood $g
    Paint-Rect $g $palette.IronDark 0 0 32 2
    Paint-Rect $g $palette.IronDark 0 30 32 2
    Paint-Rect $g $palette.IronDark 0 0 2 32
    Paint-Rect $g $palette.IronDark 30 0 2 32
    Paint-Rect $g $palette.WoodWarm 3 3 26 26
    Paint-Ellipse $g $palette.IronDark 7 5 18 18
    Paint-Ellipse $g $palette.Iron 8 6 16 16
    Paint-Ellipse $g $palette.IronLight 10 8 12 12
    Paint-Ellipse $g $palette.BrassDark 12 10 8 8
    Paint-Ellipse $g $palette.Brass 13 11 6 6
    Paint-Rect $g $palette.Gold 14 11 4 1
    Paint-Rect $g $palette.Gold 13 12 1 2
    Paint-Rect $g $palette.Gold 18 12 1 2
    Paint-Rect $g $palette.Gold 15 10 1 2
    Paint-Rect $g $palette.Gold 17 10 1 2
    Paint-Rect $g $palette.IronDark 5 24 22 4
    Paint-Rect $g $palette.BrassDark 6 25 5 2
    Paint-Rect $g $palette.Iron 13 25 5 2
    Paint-Rect $g $palette.Brass 20 25 5 2
    foreach ($point in @(@(1, 1), @(28, 1), @(1, 28), @(28, 28))) { Paint-Rivet $g $point[0] $point[1] }
}

Save-BlockTexture 'mint_house_side' {
    param($g)
    Paint-Wood $g
    Paint-Rect $g $palette.IronDark 0 0 2 32
    Paint-Rect $g $palette.IronDark 30 0 2 32
    Paint-Rect $g $palette.IronDark 0 0 32 2
    Paint-Rect $g $palette.IronDark 0 30 32 2
    Paint-Ellipse $g $palette.IronDark 9 9 15 15
    Paint-Ellipse $g $palette.Iron 10 10 13 13
    Paint-Ellipse $g $palette.BrassDark 12 12 9 9
    Paint-Ellipse $g $palette.Brass 13 13 7 7
    Paint-Rect $g $palette.Gold 15 11 2 11
    Paint-Rect $g $palette.Gold 11 15 11 2
    Paint-Line $g $palette.IronLight 16 7 16 10 1
    Paint-Line $g $palette.IronLight 16 22 16 25 1
    Paint-Rect $g $palette.IronDark 2 19 7 3
    Paint-Rect $g $palette.WoodWarm 3 20 6 1
    Paint-Rect $g $palette.Iron 7 17 3 7
    foreach ($point in @(@(1, 1), @(28, 1), @(1, 28), @(28, 28))) { Paint-Rivet $g $point[0] $point[1] }
}

Save-BlockTexture 'mint_house_back' {
    param($g)
    Paint-Wood $g
    Paint-Rect $g $palette.IronDark 0 0 32 2
    Paint-Rect $g $palette.IronDark 0 30 32 2
    Paint-Rect $g $palette.IronDark 0 0 2 32
    Paint-Rect $g $palette.IronDark 30 0 2 32
    Paint-Line $g $palette.Iron 5 6 27 26 2
    Paint-Line $g $palette.IronLight 6 6 26 25 1
    Paint-Line $g $palette.Iron 5 26 27 6 2
    Paint-Line $g $palette.IronLight 6 25 26 6 1
    Paint-Rect $g $palette.BrassDark 12 12 8 8
    Paint-Rect $g $palette.Brass 13 13 6 6
    Paint-Rect $g $palette.Gold 15 14 2 3
    foreach ($point in @(@(1, 1), @(28, 1), @(1, 28), @(28, 28), @(14, 13), @(18, 13))) { Paint-Rivet $g $point[0] $point[1] }
}

Save-BlockTexture 'mint_house_bottom' {
    param($g)
    Paint-Rect $g $palette.WoodDark 0 0 32 32
    foreach ($x in @(2, 9, 16, 23, 30)) {
        Paint-Rect $g $palette.Wood  $x 0 5 32
        Paint-Line $g $palette.WoodLight ($x + 1) 1 ($x + 1) 30
    }
    Paint-Rect $g $palette.IronDark 0 0 32 2
    Paint-Rect $g $palette.IronDark 0 30 32 2
    Paint-Rect $g $palette.IronDark 0 0 2 32
    Paint-Rect $g $palette.IronDark 30 0 2 32
    foreach ($point in @(@(1, 1), @(28, 1), @(1, 28), @(28, 28))) { Paint-Rivet $g $point[0] $point[1] }
}

Save-BlockTexture 'mint_house_metal' {
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

Save-BlockTexture 'mint_house_press' {
    param($g)
    Paint-Rect $g $palette.Iron 0 0 32 32
    Paint-Ellipse $g $palette.IronDark 3 3 26 26
    Paint-Ellipse $g $palette.Iron 4 4 24 24
    Paint-Ellipse $g $palette.IronLight 7 7 18 18
    Paint-Ellipse $g $palette.BrassDark 10 10 12 12
    Paint-Ellipse $g $palette.Brass 11 11 10 10
    Paint-Rect $g $palette.Gold 14 10 4 2
    Paint-Rect $g $palette.Gold 13 12 1 2
    Paint-Rect $g $palette.Gold 18 12 1 2
    Paint-Rect $g $palette.Gold 15 9 1 2
    Paint-Rect $g $palette.Gold 17 9 1 2
    Paint-Rect $g $palette.Gold 15 13 2 6
    Paint-Rect $g $palette.Gold 12 15 8 2
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
