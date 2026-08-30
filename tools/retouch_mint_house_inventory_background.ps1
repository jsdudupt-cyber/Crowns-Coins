$ErrorActionPreference = 'Stop'

Add-Type -AssemblyName System.Drawing

$texturePath = Join-Path $PSScriptRoot '..\src\main\resources\assets\crownscoins\textures\gui\mint_house_workbench.png'
$temporaryPath = Join-Path (Split-Path -Parent $texturePath) ("mint_house_workbench.{0}.png" -f [Guid]::NewGuid())
$source = [System.Drawing.Bitmap]::FromFile($texturePath)
$target = $null
try {
    $target = New-Object System.Drawing.Bitmap $source.Width, $source.Height, ([System.Drawing.Imaging.PixelFormat]::Format32bppArgb)
$graphics = [System.Drawing.Graphics]::FromImage($target)
    try {
        $graphics.DrawImageUnscaled($source, 0, 0)
        $graphics.SmoothingMode = [System.Drawing.Drawing2D.SmoothingMode]::None

        # Remove decorative oversize cells. Runtime code draws the real 9x3
        # inventory and hotbar slots at their exact Minecraft coordinates.
        $panelBrush = New-Object System.Drawing.SolidBrush ([System.Drawing.Color]::FromArgb(255, 18, 17, 15))
        $grainBrush = New-Object System.Drawing.SolidBrush ([System.Drawing.Color]::FromArgb(255, 27, 23, 17))
        $edgePen = New-Object System.Drawing.Pen ([System.Drawing.Color]::FromArgb(255, 112, 78, 40)), 1
        try {
            $graphics.FillRectangle($panelBrush, 70, 316, 580, 137)
            $graphics.FillRectangle($panelBrush, 70, 476, 580, 42)
            for ($y = 323; $y -lt 449; $y += 9) {
                $graphics.FillRectangle($grainBrush, 76, $y, 568, 1)
            }
            for ($y = 482; $y -lt 514; $y += 8) {
                $graphics.FillRectangle($grainBrush, 76, $y, 568, 1)
            }
            $graphics.DrawRectangle($edgePen, 70, 316, 579, 136)
            $graphics.DrawRectangle($edgePen, 70, 476, 579, 41)
        } finally {
            $panelBrush.Dispose()
            $grainBrush.Dispose()
            $edgePen.Dispose()
        }
    } finally {
        $graphics.Dispose()
    }
} finally {
    $source.Dispose()
}
try {
    $target.Save($temporaryPath, [System.Drawing.Imaging.ImageFormat]::Png)
} finally {
    $target.Dispose()
}
Copy-Item -LiteralPath $temporaryPath -Destination $texturePath -Force
Remove-Item -LiteralPath $temporaryPath -Force

Write-Output "Retouched Mint House inventory background."
