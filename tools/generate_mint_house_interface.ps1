[CmdletBinding()]
param(
    [string]$SourceImage = (Join-Path $PSScriptRoot 'assets\mint_house_interface_v2_reference.png'),
    [string]$ResourceRoot = (Join-Path $PSScriptRoot '..\src\main\resources')
)

<#!
.SYNOPSIS
Builds the blank 720 x 540 Mint House background used by the in-game screen.

.DESCRIPTION
The high-resolution reference is intentionally text-free and icon-free: the
screen supplies every label, item stack, selected symbol, currency name and
button message at runtime.  This generator trims only the unused decorative
side margin, keeping all three functional panels visible in the 4:3 Minecraft
screen: metal cards, coin recess, a clean coin-preview field, inventory areas
and action panel.

Layout guides in the generated texture (all coordinates are local to 720x540):
  header       x 56..665, y 26..71
  metal panel  x   0..183, y 77..384
  preview      x 190..503, y 77..384
  clean preview x 509..720, y 77..384
  inventory    x   0..240, y 393..527
  coin chest   x 248..498, y 393..527
  actions      x 504..720, y 393..527

Real interactive slots (the Java menu uses these exact 18px positions):
  material     x  45, y 334
  player main  x  24, y 422 (9 columns x 3 rows)
  hotbar       x  24, y 482 (9 columns)
  coin chest   x 268, y 422 (9 columns x 3 rows)

#>

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'
Add-Type -AssemblyName System.Drawing

if (-not (Test-Path -LiteralPath $SourceImage -PathType Leaf)) {
    throw "Mint House GUI reference not found: $SourceImage"
}

function Ensure-Directory([string]$Path) {
    [void][System.IO.Directory]::CreateDirectory($Path)
}

function Save-Png([System.Drawing.Bitmap]$Bitmap, [string]$Path) {
    Ensure-Directory ([System.IO.Path]::GetDirectoryName($Path))
    $temporaryPath = Join-Path ([System.IO.Path]::GetDirectoryName($Path)) (".{0}.{1}.png" -f [System.IO.Path]::GetFileNameWithoutExtension($Path), [Guid]::NewGuid())
    try {
        $Bitmap.Save($temporaryPath, [System.Drawing.Imaging.ImageFormat]::Png)
        Move-Item -LiteralPath $temporaryPath -Destination $Path -Force
    } finally {
        if (Test-Path -LiteralPath $temporaryPath) {
            Remove-Item -LiteralPath $temporaryPath -Force
        }
    }
}

function Draw-InsetPanel(
    [System.Drawing.Graphics]$Graphics,
    [int]$X,
    [int]$Y,
    [int]$Width,
    [int]$Height,
    [System.Drawing.Brush]$Outer,
    [System.Drawing.Brush]$Inner,
    [System.Drawing.Pen]$Border,
    [System.Drawing.Pen]$Highlight
) {
    $Graphics.FillRectangle($Outer, $X, $Y, $Width, $Height)
    $Graphics.DrawRectangle($Border, $X, $Y, $Width - 1, $Height - 1)
    $Graphics.FillRectangle($Inner, $X + 2, $Y + 2, $Width - 4, $Height - 4)
    $Graphics.DrawLine($Highlight, $X + 2, $Y + 2, $X + $Width - 3, $Y + 2)
}

function Draw-MinecraftSlot(
    [System.Drawing.Graphics]$Graphics,
    [int]$X,
    [int]$Y,
    [System.Drawing.Brush]$SlotOuter,
    [System.Drawing.Brush]$SlotInner,
    [System.Drawing.Brush]$SlotHighlight,
    [System.Drawing.Brush]$SlotShadow
) {
    # Minecraft renders a 16px item inside an 18px slot at (x + 1, y + 1).
    # Keep this frame exactly 18x18 so items never overlap the art underneath.
    $Graphics.FillRectangle($SlotOuter, $X, $Y, 18, 18)
    $Graphics.FillRectangle($SlotInner, $X + 1, $Y + 1, 16, 16)
    $Graphics.FillRectangle($SlotHighlight, $X + 1, $Y + 1, 15, 1)
    $Graphics.FillRectangle($SlotHighlight, $X + 1, $Y + 1, 1, 15)
    $Graphics.FillRectangle($SlotShadow, $X + 1, $Y + 16, 16, 1)
    $Graphics.FillRectangle($SlotShadow, $X + 16, $Y + 1, 1, 16)
}

function Draw-SlotGrid(
    [System.Drawing.Graphics]$Graphics,
    [int]$X,
    [int]$Y,
    [int]$Columns,
    [int]$Rows,
    [System.Drawing.Brush]$SlotOuter,
    [System.Drawing.Brush]$SlotInner,
    [System.Drawing.Brush]$SlotHighlight,
    [System.Drawing.Brush]$SlotShadow
) {
    for ($row = 0; $row -lt $Rows; $row++) {
        for ($column = 0; $column -lt $Columns; $column++) {
            Draw-MinecraftSlot $Graphics ($X + $column * 18) ($Y + $row * 18) `
                $SlotOuter $SlotInner $SlotHighlight $SlotShadow
        }
    }
}

$source = [System.Drawing.Bitmap]::FromFile((Resolve-Path -LiteralPath $SourceImage))
try {
    $targetWidth = 720
    $targetHeight = 540
    # Keep the complete left, centre and right content panels.  The source
    # has a wide 3:2 presentation frame while Minecraft uses 4:3; trimming
    # only its unused side ornaments preserves the full clean preview panel.
    $sideTrim = [int][Math]::Round($source.Width * 0.023)
    $cropWidth = $source.Width - (2 * $sideTrim)
    $cropX = $sideTrim
    $crop = [System.Drawing.Rectangle]::new($cropX, 0, $cropWidth, $source.Height)
    $target = [System.Drawing.Bitmap]::new($targetWidth, $targetHeight, [System.Drawing.Imaging.PixelFormat]::Format32bppArgb)
    try {
        $graphics = [System.Drawing.Graphics]::FromImage($target)
        try {
            $graphics.Clear([System.Drawing.Color]::FromArgb(255, 11, 12, 13))
            $graphics.CompositingMode = [System.Drawing.Drawing2D.CompositingMode]::SourceCopy
            $graphics.CompositingQuality = [System.Drawing.Drawing2D.CompositingQuality]::HighSpeed
            $graphics.InterpolationMode = [System.Drawing.Drawing2D.InterpolationMode]::NearestNeighbor
            $graphics.PixelOffsetMode = [System.Drawing.Drawing2D.PixelOffsetMode]::Half
            $graphics.SmoothingMode = [System.Drawing.Drawing2D.SmoothingMode]::None
            $graphics.DrawImage(
                $source,
                [System.Drawing.Rectangle]::new(0, 0, $targetWidth, $targetHeight),
                $crop,
                [System.Drawing.GraphicsUnit]::Pixel
            )

            # The generated art reference used large visual cells.  Cover just
            # the inventory interiors with purpose-built Minecraft slot frames.
            # These coordinates are deliberately shared with MintHouseMenu and
            # MintHouseScreen, so the 16px sprites sit inside an 18px frame.
            $panelOuter = [System.Drawing.SolidBrush]::new([System.Drawing.Color]::FromArgb(255, 12, 14, 15))
            $panelInner = [System.Drawing.SolidBrush]::new([System.Drawing.Color]::FromArgb(255, 20, 22, 23))
            $slotOuter = [System.Drawing.SolidBrush]::new([System.Drawing.Color]::FromArgb(255, 7, 8, 9))
            $slotInner = [System.Drawing.SolidBrush]::new([System.Drawing.Color]::FromArgb(255, 38, 39, 38))
            $slotHighlight = [System.Drawing.SolidBrush]::new([System.Drawing.Color]::FromArgb(255, 91, 88, 79))
            $slotShadow = [System.Drawing.SolidBrush]::new([System.Drawing.Color]::FromArgb(255, 19, 20, 19))
            $bronze = [System.Drawing.Pen]::new([System.Drawing.Color]::FromArgb(255, 130, 88, 29), 1)
            $iron = [System.Drawing.Pen]::new([System.Drawing.Color]::FromArgb(255, 67, 65, 59), 1)
            $panelHighlight = [System.Drawing.Pen]::new([System.Drawing.Color]::FromArgb(255, 53, 49, 42), 1)
            try {
                # Lower-left material socket: retain the forged-card style but
                # put the real input directly in the small left holder.
                Draw-InsetPanel $graphics 28 319 143 54 $panelOuter $panelInner $bronze $panelHighlight
                Draw-MinecraftSlot $graphics 45 334 $slotOuter $slotInner $slotHighlight $slotShadow
                $arrowBrush = [System.Drawing.SolidBrush]::new([System.Drawing.Color]::FromArgb(255, 177, 121, 36))
                try {
                    $graphics.FillRectangle($arrowBrush, 82, 341, 18, 3)
                    $graphics.FillRectangle($arrowBrush, 97, 338, 3, 9)
                    $graphics.FillRectangle($arrowBrush, 100, 341, 3, 3)
                } finally {
                    $arrowBrush.Dispose()
                }

                # Two compact 9x3 panels plus the player's hotbar.  The outer
                # panels hide the obsolete 32px decorative cells from the art
                # reference while preserving the surrounding wood-and-metal UI.
                Draw-InsetPanel $graphics 10 414 220 94 $panelOuter $panelInner $iron $panelHighlight
                Draw-SlotGrid $graphics 24 422 9 3 $slotOuter $slotInner $slotHighlight $slotShadow
                Draw-SlotGrid $graphics 24 482 9 1 $slotOuter $slotInner $slotHighlight $slotShadow

                Draw-InsetPanel $graphics 258 414 232 64 $panelOuter $panelInner $iron $panelHighlight
                Draw-SlotGrid $graphics 268 422 9 3 $slotOuter $slotInner $slotHighlight $slotShadow

                # The former five-by-five symbol catalogue is intentionally
                # absent.  This single quiet steel inset now leaves visual
                # space for a clean, live coin preview drawn by the screen.
                Draw-InsetPanel $graphics 505 86 208 292 $panelOuter $panelInner $iron $panelHighlight
            } finally {
                $panelOuter.Dispose()
                $panelInner.Dispose()
                $slotOuter.Dispose()
                $slotInner.Dispose()
                $slotHighlight.Dispose()
                $slotShadow.Dispose()
                $bronze.Dispose()
                $iron.Dispose()
                $panelHighlight.Dispose()
            }

            # Restore a clean outside edge after the centre crop.  The thin
            # double outline is deliberately neutral so code can draw selected
            # states and warning colours above it.
            $outer = [System.Drawing.Pen]::new([System.Drawing.Color]::FromArgb(255, 7, 8, 9), 1)
            $inner = [System.Drawing.Pen]::new([System.Drawing.Color]::FromArgb(255, 170, 119, 40), 1)
            try {
                $graphics.DrawRectangle($outer, 0, 0, $targetWidth - 1, $targetHeight - 1)
                $graphics.DrawRectangle($inner, 2, 2, $targetWidth - 5, $targetHeight - 5)
            } finally {
                $outer.Dispose()
                $inner.Dispose()
            }
        } finally {
            $graphics.Dispose()
        }

        $textureRoot = Join-Path $ResourceRoot 'assets\crownscoins\textures\gui'
        # mint_house_workbench is the live screen texture.  Keep the legacy
        # alias in sync so preview tooling and resource packs show the same UI.
        Save-Png $target (Join-Path $textureRoot 'mint_house_workbench.png')
        Save-Png $target (Join-Path $textureRoot 'mint_house_menu.png')
    } finally {
        $target.Dispose()
    }
} finally {
    $source.Dispose()
}

Write-Output 'Generated Mint House GUI backgrounds: 720x540.'
