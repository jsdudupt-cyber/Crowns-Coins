param(
    [Parameter(Mandatory = $true)]
    [string]$SourceImage
)

$ErrorActionPreference = 'Stop'

Add-Type -AssemblyName System.Drawing

$targetPath = Join-Path $PSScriptRoot '..\src\main\resources\assets\crownscoins\textures\gui\mint_currency_tab.png'
$source = [System.Drawing.Bitmap]::FromFile((Resolve-Path -LiteralPath $SourceImage))
try {
    # The source is the generated mint UI. This crop preserves its blank lower
    # plaque and crown medallion while code supplies the exact in-game text.
    $crop = New-Object System.Drawing.Rectangle 0, 520, $source.Width, ($source.Height - 520)
    $target = New-Object System.Drawing.Bitmap 640, 88, ([System.Drawing.Imaging.PixelFormat]::Format32bppArgb)
    try {
        $graphics = [System.Drawing.Graphics]::FromImage($target)
        try {
            $graphics.InterpolationMode = [System.Drawing.Drawing2D.InterpolationMode]::NearestNeighbor
            $graphics.PixelOffsetMode = [System.Drawing.Drawing2D.PixelOffsetMode]::Half
            $graphics.SmoothingMode = [System.Drawing.Drawing2D.SmoothingMode]::None
            $graphics.DrawImage(
                $source,
                (New-Object System.Drawing.Rectangle 0, 0, $target.Width, $target.Height),
                $crop,
                [System.Drawing.GraphicsUnit]::Pixel
            )
        } finally {
            $graphics.Dispose()
        }
        $target.Save($targetPath, [System.Drawing.Imaging.ImageFormat]::Png)
    } finally {
        $target.Dispose()
    }
} finally {
    $source.Dispose()
}

Write-Output "Prepared $targetPath"
