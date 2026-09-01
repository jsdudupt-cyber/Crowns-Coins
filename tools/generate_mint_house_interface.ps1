[CmdletBinding()]
param(
    [string]$ResourceRoot
)

<#!
.SYNOPSIS
Builds the blank 480 x 360 compact Coin Forge background used by the in-game screen.

.DESCRIPTION
This texture is intentionally drawn as native 1:1 pixel art instead of being
scaled from the concept image.  That keeps every Minecraft 18px slot exactly
inside its own steel recess while retaining the dark iron, aged-bronze style of
the Coin Forge reference.  Text, stack counts, selected-metal highlights and
buttons are all rendered by MintHouseScreen at runtime.

Layout guides in the generated texture (all coordinates are local to 480x360):
  title header        x  12, y   6, w 456, h  44
  metal input panel   x  12, y  56, w  92, h  82
  forge preview       x 114, y  56, w 164, h  82
  coin chest          x 288, y  56, w 180, h  82
  metal cards         x  12/168/324, y 146, w 144, h 62
  player inventory    x  12, y 218, w 232, h 130
  action panel        x 254, y 218, w 214, h 130

Interactive slots (the Java menu uses these exact 18px positions):
  material            x  49, y  82
  coin chest          x 297, y  76 (9 columns x 3 rows)
  player main         x  48, y 252 (9 columns x 3 rows)
  player hotbar       x  48, y 318 (9 columns)

Runtime buttons (drawn as cavities so screen text remains sharp):
  mint                x 270, y 294, w 182, h 30
  back                x 307, y 329, w 108, h 16
#>

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'
Add-Type -AssemblyName System.Drawing

if ([string]::IsNullOrWhiteSpace($ResourceRoot)) {
    $ResourceRoot = Join-Path $PSScriptRoot '..\src\main\resources'
}

function Ensure-Directory([string]$Path) {
    [void][System.IO.Directory]::CreateDirectory($Path)
}

function Save-Png([System.Drawing.Bitmap]$Bitmap, [string]$Path) {
    Ensure-Directory ([System.IO.Path]::GetDirectoryName($Path))
    $temporaryPath = Join-Path ([System.IO.Path]::GetDirectoryName($Path)) ('.{0}.{1}.png' -f [System.IO.Path]::GetFileNameWithoutExtension($Path), [Guid]::NewGuid())
    try {
        $Bitmap.Save($temporaryPath, [System.Drawing.Imaging.ImageFormat]::Png)
        # Move-Item cannot replace an existing PNG consistently on Windows.
        # Copying over the requested generated asset is reliable and safe here.
        [System.IO.File]::Copy($temporaryPath, $Path, $true)
    } finally {
        if (Test-Path -LiteralPath $temporaryPath) {
            Remove-Item -LiteralPath $temporaryPath -Force
        }
    }
}

function Fill-Texture(
    [System.Drawing.Graphics]$Graphics,
    [int]$X,
    [int]$Y,
    [int]$Width,
    [int]$Height,
    [System.Drawing.Brush[]]$Brushes,
    [int]$Seed
) {
    # Small 4px tiles read as forged stone/iron at Minecraft GUI scale.  The
    # deterministic pattern is reproducible and avoids blurred photo texture.
    for ($row = 0; $row -lt $Height; $row += 4) {
        for ($column = 0; $column -lt $Width; $column += 4) {
            $index = [Math]::Abs((($column + $Seed) * 17) + (($row + $Seed) * 31) + (($column * $row) % 13)) % $Brushes.Count
            $drawWidth = [Math]::Min(4, $Width - $column)
            $drawHeight = [Math]::Min(4, $Height - $row)
            $Graphics.FillRectangle($Brushes[$index], $X + $column, $Y + $row, $drawWidth, $drawHeight)
        }
    }
}

function Draw-Rivet(
    [System.Drawing.Graphics]$Graphics,
    [int]$X,
    [int]$Y,
    [System.Drawing.Brush]$Dark,
    [System.Drawing.Brush]$Steel,
    [System.Drawing.Brush]$Highlight
) {
    $Graphics.FillRectangle($Dark, $X, $Y, 7, 7)
    $Graphics.FillRectangle($Steel, $X + 1, $Y + 1, 5, 5)
    $Graphics.FillRectangle($Highlight, $X + 2, $Y + 2, 2, 2)
    $Graphics.FillRectangle($Dark, $X + 4, $Y + 4, 2, 2)
}

function Draw-Frame(
    [System.Drawing.Graphics]$Graphics,
    [int]$X,
    [int]$Y,
    [int]$Width,
    [int]$Height,
    [System.Drawing.Brush]$Shadow,
    [System.Drawing.Brush]$Metal,
    [System.Drawing.Brush]$Edge,
    [System.Drawing.Brush]$Highlight,
    [System.Drawing.Brush]$Interior
) {
    $Graphics.FillRectangle($Shadow, $X, $Y, $Width, $Height)
    $Graphics.FillRectangle($Metal, $X + 1, $Y + 1, $Width - 2, $Height - 2)
    $Graphics.FillRectangle($Edge, $X + 2, $Y + 2, $Width - 4, $Height - 4)
    $Graphics.FillRectangle($Highlight, $X + 3, $Y + 3, $Width - 6, 1)
    $Graphics.FillRectangle($Highlight, $X + 3, $Y + 3, 1, $Height - 6)
    $Graphics.FillRectangle($Interior, $X + 5, $Y + 5, $Width - 10, $Height - 10)

    # Squared off forged corners make panels feel deliberately Minecraft-like.
    $Graphics.FillRectangle($Shadow, $X + 1, $Y + 1, 2, 2)
    $Graphics.FillRectangle($Shadow, $X + $Width - 3, $Y + 1, 2, 2)
    $Graphics.FillRectangle($Shadow, $X + 1, $Y + $Height - 3, 2, 2)
    $Graphics.FillRectangle($Shadow, $X + $Width - 3, $Y + $Height - 3, 2, 2)
}

function Draw-MinecraftSlot(
    [System.Drawing.Graphics]$Graphics,
    [int]$X,
    [int]$Y,
    [System.Drawing.Brush]$Outer,
    [System.Drawing.Brush]$Inner,
    [System.Drawing.Brush]$Highlight,
    [System.Drawing.Brush]$Shadow
) {
    # Minecraft's item sprite is 16px and renders at x+1/y+1 in an 18px slot.
    $Graphics.FillRectangle($Outer, $X, $Y, 18, 18)
    $Graphics.FillRectangle($Inner, $X + 1, $Y + 1, 16, 16)
    $Graphics.FillRectangle($Highlight, $X + 1, $Y + 1, 15, 1)
    $Graphics.FillRectangle($Highlight, $X + 1, $Y + 1, 1, 15)
    $Graphics.FillRectangle($Shadow, $X + 1, $Y + 16, 16, 1)
    $Graphics.FillRectangle($Shadow, $X + 16, $Y + 1, 1, 16)
}

function Draw-SlotGrid(
    [System.Drawing.Graphics]$Graphics,
    [int]$X,
    [int]$Y,
    [int]$Columns,
    [int]$Rows,
    [System.Drawing.Brush]$Outer,
    [System.Drawing.Brush]$Inner,
    [System.Drawing.Brush]$Highlight,
    [System.Drawing.Brush]$Shadow
) {
    for ($row = 0; $row -lt $Rows; $row++) {
        for ($column = 0; $column -lt $Columns; $column++) {
            Draw-MinecraftSlot $Graphics ($X + ($column * 18)) ($Y + ($row * 18)) $Outer $Inner $Highlight $Shadow
        }
    }
}

function Draw-TokenRecess(
    [System.Drawing.Graphics]$Graphics,
    [int]$CenterX,
    [int]$CenterY,
    [System.Drawing.Brush]$Outer,
    [System.Drawing.Brush]$Rim,
    [System.Drawing.Brush]$Inner,
    [System.Drawing.Brush]$Highlight
) {
    $outerPoints = [System.Drawing.Point[]]@(
        [System.Drawing.Point]::new($CenterX - 23, $CenterY - 29),
        [System.Drawing.Point]::new($CenterX + 23, $CenterY - 29),
        [System.Drawing.Point]::new($CenterX + 31, $CenterY - 21),
        [System.Drawing.Point]::new($CenterX + 31, $CenterY + 21),
        [System.Drawing.Point]::new($CenterX + 23, $CenterY + 29),
        [System.Drawing.Point]::new($CenterX - 23, $CenterY + 29),
        [System.Drawing.Point]::new($CenterX - 31, $CenterY + 21),
        [System.Drawing.Point]::new($CenterX - 31, $CenterY - 21)
    )
    $rimPoints = [System.Drawing.Point[]]@(
        [System.Drawing.Point]::new($CenterX - 20, $CenterY - 26),
        [System.Drawing.Point]::new($CenterX + 20, $CenterY - 26),
        [System.Drawing.Point]::new($CenterX + 27, $CenterY - 19),
        [System.Drawing.Point]::new($CenterX + 27, $CenterY + 19),
        [System.Drawing.Point]::new($CenterX + 20, $CenterY + 26),
        [System.Drawing.Point]::new($CenterX - 20, $CenterY + 26),
        [System.Drawing.Point]::new($CenterX - 27, $CenterY + 19),
        [System.Drawing.Point]::new($CenterX - 27, $CenterY - 19)
    )
    $innerPoints = [System.Drawing.Point[]]@(
        [System.Drawing.Point]::new($CenterX - 17, $CenterY - 22),
        [System.Drawing.Point]::new($CenterX + 17, $CenterY - 22),
        [System.Drawing.Point]::new($CenterX + 23, $CenterY - 16),
        [System.Drawing.Point]::new($CenterX + 23, $CenterY + 16),
        [System.Drawing.Point]::new($CenterX + 17, $CenterY + 22),
        [System.Drawing.Point]::new($CenterX - 17, $CenterY + 22),
        [System.Drawing.Point]::new($CenterX - 23, $CenterY + 16),
        [System.Drawing.Point]::new($CenterX - 23, $CenterY - 16)
    )
    $Graphics.FillPolygon($Outer, $outerPoints)
    $Graphics.FillPolygon($Rim, $rimPoints)
    $Graphics.FillPolygon($Inner, $innerPoints)
    $Graphics.FillRectangle($Highlight, $CenterX - 13, $CenterY - 20, 26, 2)
    $Graphics.FillRectangle($Highlight, $CenterX - 20, $CenterY - 13, 2, 18)
}

function Draw-ForgeMark(
    [System.Drawing.Graphics]$Graphics,
    [int]$X,
    [int]$Y,
    [System.Drawing.Brush]$Dark,
    [System.Drawing.Brush]$Steel,
    [System.Drawing.Brush]$Bronze,
    [System.Drawing.Brush]$Spark
) {
    # A small pixel arrow and anvil keep the central preview panel readable
    # even before the live coin preview is drawn above it by the screen.
    $Graphics.FillRectangle($Dark, $X - 61, $Y - 25, 118, 10)
    $Graphics.FillRectangle($Bronze, $X - 58, $Y - 23, 92, 4)
    $Graphics.FillRectangle($Bronze, $X + 31, $Y - 28, 10, 14)
    $Graphics.FillRectangle($Bronze, $X + 40, $Y - 24, 8, 10)
    $Graphics.FillRectangle($Bronze, $X + 47, $Y - 20, 8, 6)
    $Graphics.FillRectangle($Dark, $X - 31, $Y + 14, 61, 5)
    $Graphics.FillRectangle($Steel, $X - 22, $Y + 7, 42, 7)
    $Graphics.FillRectangle($Steel, $X - 11, $Y + 1, 21, 7)
    $Graphics.FillRectangle($Steel, $X - 6, $Y - 5, 10, 7)
    $Graphics.FillRectangle($Steel, $X - 14, $Y + 19, 28, 5)
    $Graphics.FillRectangle($Dark, $X - 19, $Y + 24, 38, 4)
    $Graphics.FillRectangle($Spark, $X + 26, $Y + 1, 3, 3)
    $Graphics.FillRectangle($Spark, $X + 33, $Y + 8, 2, 2)
    $Graphics.FillRectangle($Spark, $X - 30, $Y + 4, 2, 2)
}

$targetWidth = 480
$targetHeight = 360
$target = [System.Drawing.Bitmap]::new($targetWidth, $targetHeight, [System.Drawing.Imaging.PixelFormat]::Format32bppArgb)
try {
    $graphics = [System.Drawing.Graphics]::FromImage($target)
    try {
        $graphics.CompositingMode = [System.Drawing.Drawing2D.CompositingMode]::SourceCopy
        $graphics.CompositingQuality = [System.Drawing.Drawing2D.CompositingQuality]::HighSpeed
        $graphics.InterpolationMode = [System.Drawing.Drawing2D.InterpolationMode]::NearestNeighbor
        $graphics.PixelOffsetMode = [System.Drawing.Drawing2D.PixelOffsetMode]::Half
        $graphics.SmoothingMode = [System.Drawing.Drawing2D.SmoothingMode]::None

        $black = [System.Drawing.SolidBrush]::new([System.Drawing.Color]::FromArgb(255, 7, 8, 9))
        $deepIron = [System.Drawing.SolidBrush]::new([System.Drawing.Color]::FromArgb(255, 15, 17, 18))
        $iron = [System.Drawing.SolidBrush]::new([System.Drawing.Color]::FromArgb(255, 38, 40, 39))
        $ironEdge = [System.Drawing.SolidBrush]::new([System.Drawing.Color]::FromArgb(255, 70, 68, 61))
        $ironHighlight = [System.Drawing.SolidBrush]::new([System.Drawing.Color]::FromArgb(255, 119, 112, 96))
        $bronze = [System.Drawing.SolidBrush]::new([System.Drawing.Color]::FromArgb(255, 96, 59, 24))
        $bronzeEdge = [System.Drawing.SolidBrush]::new([System.Drawing.Color]::FromArgb(255, 154, 103, 37))
        $bronzeHighlight = [System.Drawing.SolidBrush]::new([System.Drawing.Color]::FromArgb(255, 219, 161, 67))
        $copper = [System.Drawing.SolidBrush]::new([System.Drawing.Color]::FromArgb(255, 160, 88, 41))
        $silver = [System.Drawing.SolidBrush]::new([System.Drawing.Color]::FromArgb(255, 135, 140, 141))
        $gold = [System.Drawing.SolidBrush]::new([System.Drawing.Color]::FromArgb(255, 206, 150, 35))
        $slotOuter = [System.Drawing.SolidBrush]::new([System.Drawing.Color]::FromArgb(255, 4, 5, 6))
        $slotInner = [System.Drawing.SolidBrush]::new([System.Drawing.Color]::FromArgb(255, 24, 26, 26))
        $slotHighlight = [System.Drawing.SolidBrush]::new([System.Drawing.Color]::FromArgb(255, 85, 81, 71))
        $slotShadow = [System.Drawing.SolidBrush]::new([System.Drawing.Color]::FromArgb(255, 11, 12, 13))
        $stoneBrushes = [System.Drawing.Brush[]]@(
            [System.Drawing.SolidBrush]::new([System.Drawing.Color]::FromArgb(255, 13, 15, 16)),
            [System.Drawing.SolidBrush]::new([System.Drawing.Color]::FromArgb(255, 15, 17, 18)),
            [System.Drawing.SolidBrush]::new([System.Drawing.Color]::FromArgb(255, 17, 19, 20)),
            [System.Drawing.SolidBrush]::new([System.Drawing.Color]::FromArgb(255, 20, 21, 21)),
            [System.Drawing.SolidBrush]::new([System.Drawing.Color]::FromArgb(255, 12, 13, 14))
        )
        $panelBrushes = [System.Drawing.Brush[]]@(
            [System.Drawing.SolidBrush]::new([System.Drawing.Color]::FromArgb(255, 20, 22, 22)),
            [System.Drawing.SolidBrush]::new([System.Drawing.Color]::FromArgb(255, 22, 24, 24)),
            [System.Drawing.SolidBrush]::new([System.Drawing.Color]::FromArgb(255, 24, 25, 24)),
            [System.Drawing.SolidBrush]::new([System.Drawing.Color]::FromArgb(255, 18, 20, 20))
        )
        try {
            $graphics.Clear([System.Drawing.Color]::FromArgb(255, 8, 9, 10))
            Fill-Texture $graphics 0 0 $targetWidth $targetHeight $stoneBrushes 23

            # Thick, layered bronze/iron outer frame.
            $graphics.FillRectangle($black, 0, 0, $targetWidth, $targetHeight)
            $graphics.FillRectangle($bronze, 3, 3, $targetWidth - 6, $targetHeight - 6)
            $graphics.FillRectangle($bronzeEdge, 5, 5, $targetWidth - 10, $targetHeight - 10)
            $graphics.FillRectangle($deepIron, 8, 8, $targetWidth - 16, $targetHeight - 16)
            $graphics.FillRectangle($iron, 11, 11, $targetWidth - 22, $targetHeight - 22)
            $graphics.FillRectangle($black, 14, 14, $targetWidth - 28, $targetHeight - 28)
            Fill-Texture $graphics 16 16 ($targetWidth - 32) ($targetHeight - 32) $stoneBrushes 51

            # Header and all functional zones.
            Draw-Frame $graphics 12 6 456 44 $black $bronze $bronzeEdge $bronzeHighlight $deepIron
            Fill-Texture $graphics 18 12 444 32 $panelBrushes 8

            Draw-Frame $graphics 12 56 92 82 $black $iron $ironEdge $ironHighlight $deepIron
            Fill-Texture $graphics 18 62 80 70 $panelBrushes 31
            Draw-Frame $graphics 114 56 164 82 $black $iron $ironEdge $ironHighlight $deepIron
            Fill-Texture $graphics 120 62 152 70 $panelBrushes 42
            Draw-Frame $graphics 288 56 180 82 $black $iron $ironEdge $ironHighlight $deepIron
            Fill-Texture $graphics 294 62 168 70 $panelBrushes 65

            Draw-Frame $graphics 12 146 144 62 $black $bronze $bronzeEdge $bronzeHighlight $deepIron
            Fill-Texture $graphics 18 152 132 50 $panelBrushes 17
            Draw-Frame $graphics 168 146 144 62 $black $iron $ironEdge $ironHighlight $deepIron
            Fill-Texture $graphics 174 152 132 50 $panelBrushes 29
            Draw-Frame $graphics 324 146 144 62 $black $bronze $gold $bronzeHighlight $deepIron
            Fill-Texture $graphics 330 152 132 50 $panelBrushes 43

            Draw-Frame $graphics 12 218 232 130 $black $iron $ironEdge $ironHighlight $deepIron
            Fill-Texture $graphics 18 224 220 118 $panelBrushes 71
            Draw-Frame $graphics 254 218 214 130 $black $iron $ironEdge $ironHighlight $deepIron
            Fill-Texture $graphics 260 224 202 118 $panelBrushes 79

            # Decorative header supports and bolts: labels sit above these at runtime.
            $graphics.FillRectangle($bronze, 28, 24, 62, 2)
            $graphics.FillRectangle($bronze, 390, 24, 62, 2)
            Draw-Rivet $graphics 19 14 $black $iron $ironHighlight
            Draw-Rivet $graphics 454 14 $black $iron $ironHighlight
            Draw-Rivet $graphics 19 36 $black $iron $ironHighlight
            Draw-Rivet $graphics 454 36 $black $iron $ironHighlight

            # Material well at the exact live material slot coordinate.
            Draw-Frame $graphics 34 67 48 48 $black $bronze $bronzeEdge $bronzeHighlight $deepIron
            Draw-MinecraftSlot $graphics 49 82 $slotOuter $slotInner $slotHighlight $slotShadow
            $graphics.FillRectangle($bronze, 23, 120, 27, 2)
            $graphics.FillRectangle($bronze, 72, 120, 14, 2)

            # Coin forge flow in the top centre: arrow + anvil under the live preview.
            Draw-ForgeMark $graphics 196 97 $black $iron $bronzeHighlight $gold

            # Persistent coin chest: nine columns by three rows fit exactly.
            Draw-Frame $graphics 291 70 174 65 $black $bronze $bronzeEdge $bronzeHighlight $deepIron
            Draw-SlotGrid $graphics 297 76 9 3 $slotOuter $slotInner $slotHighlight $slotShadow

            # Metal choice cards.  These are intentional recesses only; the
            # client renders the clean coin icon and selection outline on top.
            Draw-TokenRecess $graphics 121 177 $black $copper $deepIron $bronzeHighlight
            Draw-TokenRecess $graphics 277 177 $black $silver $deepIron $ironHighlight
            Draw-TokenRecess $graphics 433 177 $black $gold $deepIron $bronzeHighlight

            # Player inventory is moved to the central lower panel.  Keep its
            # 18px frame exact so the vanilla 16px item sprite is centred.
            Draw-Frame $graphics 38 242 182 100 $black $iron $ironEdge $ironHighlight $deepIron
            Draw-SlotGrid $graphics 48 252 9 3 $slotOuter $slotInner $slotHighlight $slotShadow
            Draw-SlotGrid $graphics 48 318 9 1 $slotOuter $slotInner $slotHighlight $slotShadow

            # Action panel has a quiet coin/readout well, then precise primary
            # and secondary button cavities at the coordinates supplied by the
            # screen.  The screen supplies all text and enabled/disabled state.
            Draw-Frame $graphics 270 230 182 55 $black $iron $ironEdge $ironHighlight $deepIron
            Draw-TokenRecess $graphics 361 258 $black $bronze $deepIron $bronzeHighlight
            Draw-Frame $graphics 270 294 182 30 $black $bronze $gold $bronzeHighlight $bronze
            Draw-Frame $graphics 307 329 108 16 $black $iron $ironEdge $ironHighlight $deepIron

            # Outer-corner rivets complete the heavy forge casing.
            Draw-Rivet $graphics 7 7 $black $iron $ironHighlight
            Draw-Rivet $graphics 466 7 $black $iron $ironHighlight
            Draw-Rivet $graphics 7 346 $black $iron $ironHighlight
            Draw-Rivet $graphics 466 346 $black $iron $ironHighlight
        } finally {
            $black.Dispose()
            $deepIron.Dispose()
            $iron.Dispose()
            $ironEdge.Dispose()
            $ironHighlight.Dispose()
            $bronze.Dispose()
            $bronzeEdge.Dispose()
            $bronzeHighlight.Dispose()
            $copper.Dispose()
            $silver.Dispose()
            $gold.Dispose()
            $slotOuter.Dispose()
            $slotInner.Dispose()
            $slotHighlight.Dispose()
            $slotShadow.Dispose()
            foreach ($brush in $stoneBrushes) { $brush.Dispose() }
            foreach ($brush in $panelBrushes) { $brush.Dispose() }
        }
    } finally {
        $graphics.Dispose()
    }

    $textureRoot = Join-Path $ResourceRoot 'assets\crownscoins\textures\gui'
    # The prior Coin Forge textures can remain open in a running client on
    # Windows.  Write the compact design under new names so Java can switch
    # safely without trying to replace a locked asset.
    Save-Png $target (Join-Path $textureRoot 'compact_coin_forge_workbench.png')
    Save-Png $target (Join-Path $textureRoot 'compact_coin_forge_menu.png')
} finally {
    $target.Dispose()
}

Write-Output 'Generated compact Coin Forge GUI backgrounds: 480x360.'
