[CmdletBinding()]
param(
    [Parameter(Mandatory = $true)]
    [string]$ResourceRoot
)

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'
Add-Type -AssemblyName System.Drawing

$symbolNames = @(
    'sun', 'moon', 'star', 'crown', 'sword', 'shield', 'tower', 'dragon', 'wolf', 'eagle',
    'lion', 'horse', 'hammer', 'anvil', 'heart', 'flame', 'wave', 'leaf', 'flower', 'diamond',
    'mountain', 'river', 'cross', 'lightning', 'compass'
)
$textureRoot = Join-Path $ResourceRoot 'assets/crownscoins/textures/item/overlay'

function Write-PositionedOverlay([string]$SourcePath, [string]$TargetPath, [int]$X, [int]$Y, [int]$Size) {
    $source = [System.Drawing.Bitmap]::new($SourcePath)
    $target = [System.Drawing.Bitmap]::new(32, 32, [System.Drawing.Imaging.PixelFormat]::Format32bppArgb)
    $graphics = [System.Drawing.Graphics]::FromImage($target)
    try {
        $graphics.Clear([System.Drawing.Color]::Transparent)
        $graphics.CompositingMode = [System.Drawing.Drawing2D.CompositingMode]::SourceCopy
        $graphics.CompositingQuality = [System.Drawing.Drawing2D.CompositingQuality]::HighSpeed
        $graphics.InterpolationMode = [System.Drawing.Drawing2D.InterpolationMode]::NearestNeighbor
        $graphics.PixelOffsetMode = [System.Drawing.Drawing2D.PixelOffsetMode]::Half
        $graphics.DrawImage($source, [System.Drawing.Rectangle]::new($X, $Y, $Size, $Size))
        [void][System.IO.Directory]::CreateDirectory([System.IO.Path]::GetDirectoryName($TargetPath))
        $target.Save($TargetPath, [System.Drawing.Imaging.ImageFormat]::Png)
    } finally {
        $graphics.Dispose()
        $target.Dispose()
        $source.Dispose()
    }
}

foreach ($index in 0..($symbolNames.Count - 1)) {
    $name = ('{0:D2}_{1}' -f ($index + 1), $symbolNames[$index])
    $symbolSource = Join-Path $textureRoot "symbol/$name.png"
    $crestSource = Join-Path $textureRoot "crest/$name.png"
    Write-PositionedOverlay $crestSource (Join-Path $textureRoot "crest_center/$name.png") 7 7 18
    Write-PositionedOverlay $symbolSource (Join-Path $textureRoot "symbol_left/$name.png") 0 12 10
    Write-PositionedOverlay $symbolSource (Join-Path $textureRoot "symbol_right/$name.png") 22 12 10
    Write-PositionedOverlay $symbolSource (Join-Path $textureRoot "symbol_bottom/$name.png") 11 22 10
}

Write-Output "Generated aligned crest, left, right, and bottom coin overlays under $textureRoot"
