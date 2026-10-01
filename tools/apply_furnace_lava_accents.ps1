param(
    [string]$Source = "${PSScriptRoot}\..\designs\ui_editable_guides\fornalha_molduras_alinhadas_480x360_v2.png",
    [string]$Destination = "${PSScriptRoot}\..\src\main\resources\assets\crownscoins\textures\gui\mint_furnace_user_layout.png"
)

Add-Type -AssemblyName System.Drawing

$sourcePath = [System.IO.Path]::GetFullPath($Source)
$destinationPath = [System.IO.Path]::GetFullPath($Destination)
if (-not (Test-Path -LiteralPath $sourcePath)) {
    throw "Source texture not found: $sourcePath"
}

$bitmap = [System.Drawing.Bitmap]::new($sourcePath)
try {
    if ($bitmap.Width -ne 480 -or $bitmap.Height -ne 360) {
        throw "Expected a 480x360 GUI texture; found $($bitmap.Width)x$($bitmap.Height)."
    }

    # Keep an untouched copy of every painted slot. The decorative background is
    # drawn first and the exact original frames are restored at the end.
    $slotFrameSnapshot = [System.Drawing.Bitmap]$bitmap.Clone()
    $graphics = [System.Drawing.Graphics]::FromImage($bitmap)
    try {
        $graphics.SmoothingMode = [System.Drawing.Drawing2D.SmoothingMode]::None
        $graphics.InterpolationMode = [System.Drawing.Drawing2D.InterpolationMode]::NearestNeighbor
        $graphics.PixelOffsetMode = [System.Drawing.Drawing2D.PixelOffsetMode]::Half

        # Decorative accents only. Slot frames remain untouched: the right panel's
        # cracks occupy frame gaps and the outer gutter, never a 18x18 slot rect.
        $coalShadow = [System.Drawing.Color]::FromArgb(255, 17, 18, 18)
        $coalMid = [System.Drawing.Color]::FromArgb(255, 38, 40, 39)
        $coalLight = [System.Drawing.Color]::FromArgb(255, 65, 66, 62)
        $lavaDark = [System.Drawing.Color]::FromArgb(255, 91, 25, 7)
        $lava = [System.Drawing.Color]::FromArgb(255, 192, 57, 10)
        $lavaLight = [System.Drawing.Color]::FromArgb(255, 239, 135, 25)

        function Draw-Coal([int]$x, [int]$y) {
            $graphics.FillRectangle([System.Drawing.SolidBrush]::new($coalShadow), $x, $y + 1, 6, 4)
            $graphics.FillRectangle([System.Drawing.SolidBrush]::new($coalMid), $x + 1, $y, 4, 5)
            $graphics.FillRectangle([System.Drawing.SolidBrush]::new($coalLight), $x + 2, $y + 1, 2, 1)
        }
        function Draw-Fissure([int[][]]$points) {
            for ($i = 0; $i -lt $points.Count - 1; $i++) {
                $from = $points[$i]
                $to = $points[$i + 1]
                $graphics.DrawLine([System.Drawing.Pen]::new($lavaDark, 3), $from[0], $from[1], $to[0], $to[1])
                $graphics.DrawLine([System.Drawing.Pen]::new($lava, 1), $from[0], $from[1], $to[0], $to[1])
            }
            foreach ($point in $points) {
                $graphics.FillRectangle([System.Drawing.SolidBrush]::new($lavaLight), $point[0], $point[1], 1, 1)
            }
        }

        function Fill-Charcoal([int]$x, [int]$y, [int]$width, [int]$height) {
            $graphics.FillRectangle([System.Drawing.SolidBrush]::new([System.Drawing.Color]::FromArgb(255, 21, 23, 22)), $x, $y, $width, $height)
            for ($py = $y + 2; $py -lt ($y + $height - 1); $py += 6) {
                for ($px = $x + 1; $px -lt ($x + $width - 1); $px += 7) {
                    $shade = if ((($px + $py) % 3) -eq 0) { $coalMid } else { $coalShadow }
                    $graphics.FillRectangle([System.Drawing.SolidBrush]::new($shade), $px, $py, 3, 2)
                    $graphics.FillRectangle([System.Drawing.SolidBrush]::new($coalLight), $px + 1, $py, 1, 1)
                }
            }
        }

        function Restore-Slot([int]$x, [int]$y) {
            $destination = [System.Drawing.Rectangle]::new($x, $y, 18, 18)
            $graphics.DrawImage(
                $slotFrameSnapshot,
                $destination,
                $x,
                $y,
                18,
                18,
                [System.Drawing.GraphicsUnit]::Pixel
            )
        }

        # Dense forge texture behind every fixed slot. The image is deliberately
        # restored for each frame below, so the 16x16 Minecraft click targets and
        # their visual guides never move.
        Fill-Charcoal 25 89 66 85
        Fill-Charcoal 360 88 106 86
        Fill-Charcoal 60 228 360 99

        Draw-Fissure @(@(27, 105), @(35, 111), @(43, 106))
        Draw-Fissure @(@(26, 151), @(35, 146), @(42, 152))
        Draw-Fissure @(@(64, 251), @(74, 257), @(87, 252))
        Draw-Fissure @(@(101, 280), @(110, 286), @(120, 281))
        Draw-Fissure @(@(143, 308), @(154, 314), @(165, 309))
        Draw-Fissure @(@(194, 248), @(204, 254), @(216, 249))
        Draw-Fissure @(@(262, 279), @(272, 285), @(283, 280))
        Draw-Fissure @(@(323, 309), @(334, 315), @(345, 309))
        Draw-Fissure @(@(386, 249), @(397, 255), @(409, 250))

        # Internal-chest panel: left/right gutter and 10px gaps between the fixed slots.
        # These rectangles deliberately avoid every 18x18 slot frame.
        Fill-Charcoal 360 88 16 86
        Fill-Charcoal 452 88 14 86
        Fill-Charcoal 376 88 76 10
        Fill-Charcoal 376 163 76 11
        Fill-Charcoal 396 99 9 64
        Fill-Charcoal 424 99 9 64
        Fill-Charcoal 376 117 76 5
        Fill-Charcoal 376 140 76 5
        Draw-Fissure @(@(362, 105), @(369, 108), @(373, 104))
        Draw-Fissure @(@(463, 102), @(458, 108), @(465, 113))
        Draw-Fissure @(@(396, 120), @(400, 123), @(404, 120))
        Draw-Fissure @(@(424, 143), @(428, 146), @(432, 143))
        Draw-Fissure @(@(396, 166), @(400, 169), @(404, 166))
        Draw-Coal 365 130
        Draw-Coal 402 132
        Draw-Coal 430 109
        Draw-Coal 457 151

        # A few restrained cracks in the empty lower background, always outside slots.
        Draw-Fissure @(@(63, 338), @(70, 334), @(76, 338))
        Draw-Fissure @(@(407, 338), @(414, 334), @(420, 338))
        Draw-Coal 68 345
        Draw-Coal 401 345

        # Restore every original visual slot frame after the new texture was painted.
        Restore-Slot 47 123
        for ($row = 0; $row -lt 3; $row++) {
            for ($column = 0; $column -lt 3; $column++) {
                Restore-Slot (377 + (28 * $column)) (99 + (23 * $row))
            }
        }
        for ($column = 0; $column -lt 9; $column++) {
            Restore-Slot (76 + (40 * $column)) 237
            Restore-Slot (76 + (40 * $column)) 270
            Restore-Slot (76 + (40 * $column)) 307
        }
    }
    finally {
        $graphics.Dispose()
        $slotFrameSnapshot.Dispose()
    }

    $destinationDirectory = [System.IO.Path]::GetDirectoryName($destinationPath)
    [System.IO.Directory]::CreateDirectory($destinationDirectory) | Out-Null
    $bitmap.Save($destinationPath, [System.Drawing.Imaging.ImageFormat]::Png)
}
finally {
    $bitmap.Dispose()
}

Write-Output "Saved $destinationPath"
