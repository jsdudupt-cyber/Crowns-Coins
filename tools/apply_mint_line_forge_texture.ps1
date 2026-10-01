param(
    [string]$Source = "${PSScriptRoot}\..\designs\ui_editable_guides\linha_cunhagem_atual_para_editar_480x360.png",
    [string]$Destination = "${PSScriptRoot}\..\src\main\resources\assets\crownscoins\textures\gui\mint_house_user_layout.png"
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

    # Static frames are restored after the new background is drawn. This preserves
    # the exact visual frame and the coordinates shared by MintHouseLayout.
    $slotFrameSnapshot = [System.Drawing.Bitmap]$bitmap.Clone()
    $graphics = [System.Drawing.Graphics]::FromImage($bitmap)
    try {
        $graphics.SmoothingMode = [System.Drawing.Drawing2D.SmoothingMode]::None
        $graphics.InterpolationMode = [System.Drawing.Drawing2D.InterpolationMode]::NearestNeighbor
        $graphics.PixelOffsetMode = [System.Drawing.Drawing2D.PixelOffsetMode]::Half

        $ironDark = [System.Drawing.Color]::FromArgb(255, 20, 23, 24)
        $ironMid = [System.Drawing.Color]::FromArgb(255, 37, 42, 42)
        $ironLight = [System.Drawing.Color]::FromArgb(255, 67, 71, 68)
        $oakDark = [System.Drawing.Color]::FromArgb(255, 43, 27, 16)
        $oakMid = [System.Drawing.Color]::FromArgb(255, 75, 45, 22)
        $oakLight = [System.Drawing.Color]::FromArgb(255, 111, 66, 30)
        $brass = [System.Drawing.Color]::FromArgb(255, 170, 110, 31)
        $brassLight = [System.Drawing.Color]::FromArgb(255, 228, 171, 62)
        $ember = [System.Drawing.Color]::FromArgb(255, 170, 54, 13)

        function Draw-Rivet([int]$x, [int]$y) {
            $graphics.FillRectangle([System.Drawing.SolidBrush]::new($ironDark), $x, $y, 4, 4)
            $graphics.FillRectangle([System.Drawing.SolidBrush]::new($brass), $x + 1, $y + 1, 2, 2)
            $graphics.FillRectangle([System.Drawing.SolidBrush]::new($brassLight), $x + 1, $y + 1, 1, 1)
        }
        function Draw-IronPlate([int]$x, [int]$y, [int]$width, [int]$height) {
            $graphics.FillRectangle([System.Drawing.SolidBrush]::new($ironDark), $x, $y, $width, $height)
            $graphics.DrawRectangle([System.Drawing.Pen]::new($ironLight, 1), $x, $y, $width - 1, $height - 1)
            $graphics.DrawLine([System.Drawing.Pen]::new($ironMid, 1), $x + 2, $y + 3, $x + $width - 3, $y + 3)
            Draw-Rivet ($x + 5) ($y + 5)
            Draw-Rivet ($x + $width - 9) ($y + 5)
            Draw-Rivet ($x + 5) ($y + $height - 9)
            Draw-Rivet ($x + $width - 9) ($y + $height - 9)
        }
        function Draw-Wood([int]$x, [int]$y, [int]$width, [int]$height) {
            $graphics.FillRectangle([System.Drawing.SolidBrush]::new($oakDark), $x, $y, $width, $height)
            for ($line = $y + 5; $line -lt ($y + $height - 2); $line += 8) {
                $graphics.DrawLine([System.Drawing.Pen]::new($oakMid, 2), $x + 2, $line, $x + $width - 3, $line)
                $graphics.DrawLine([System.Drawing.Pen]::new($oakLight, 1), $x + 8, $line + 1, $x + $width - 10, $line + 1)
            }
        }
        function Restore-Slot([int]$x, [int]$y) {
            $destinationRect = [System.Drawing.Rectangle]::new($x, $y, 18, 18)
            $graphics.DrawImage($slotFrameSnapshot, $destinationRect, $x, $y, 18, 18, [System.Drawing.GraphicsUnit]::Pixel)
        }

        # Coin-design gallery: heavy iron plates, dark oak sides and subtle forge glow.
        $graphics.FillRectangle([System.Drawing.SolidBrush]::new($ironDark), 18, 87, 444, 93)
        Draw-Wood 22 91 46 84
        Draw-Wood 412 91 46 84
        Draw-IronPlate 74 91 120 78
        Draw-IronPlate 199 91 120 78
        Draw-IronPlate 324 91 82 78
        foreach ($emberPixel in @(@(87, 105), @(177, 151), @(210, 118), @(296, 151), @(350, 110), @(392, 145))) {
            $graphics.FillRectangle([System.Drawing.SolidBrush]::new($ember), $emberPixel[0], $emberPixel[1], 2, 1)
        }

        # Internal chest: a dark-oak rack backed by iron braces.
        Draw-Wood 20 216 284 120
        $graphics.FillRectangle([System.Drawing.SolidBrush]::new($ironDark), 20, 216, 284, 7)
        $graphics.FillRectangle([System.Drawing.SolidBrush]::new($ironDark), 20, 330, 284, 6)
        for ($brace = 52; $brace -lt 302; $brace += 64) {
            $graphics.FillRectangle([System.Drawing.SolidBrush]::new($ironMid), $brace, 221, 4, 108)
            Draw-Rivet ($brace - 1) 224
            Draw-Rivet ($brace - 1) 316
        }
        foreach ($emberPixel in @(@(39, 246), @(74, 284), @(134, 237), @(183, 278), @(247, 248), @(282, 296))) {
            $graphics.FillRectangle([System.Drawing.SolidBrush]::new($ember), $emberPixel[0], $emberPixel[1], 2, 1)
        }

        # Restore every existing slot frame exactly after painting the textured panels.
        for ($row = 0; $row -lt 3; $row++) {
            for ($column = 0; $column -lt 9; $column++) {
                Restore-Slot (28 + (32 * $column)) (225 + (32 * $row))
            }
        }
        for ($column = 0; $column -lt 9; $column++) {
            Restore-Slot (83 + (18 * $column)) 317
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
