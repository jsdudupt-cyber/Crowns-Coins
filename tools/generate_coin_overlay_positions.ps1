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

function Get-VisibleBounds([System.Drawing.Bitmap]$Bitmap) {
    $left = $Bitmap.Width
    $top = $Bitmap.Height
    $right = -1
    $bottom = -1
    for ($x = 0; $x -lt $Bitmap.Width; $x++) {
        for ($y = 0; $y -lt $Bitmap.Height; $y++) {
            if ($Bitmap.GetPixel($x, $y).A -gt 0) {
                $left = [Math]::Min($left, $x)
                $top = [Math]::Min($top, $y)
                $right = [Math]::Max($right, $x)
                $bottom = [Math]::Max($bottom, $y)
            }
        }
    }
    if ($right -lt $left -or $bottom -lt $top) {
        return [System.Drawing.Rectangle]::Empty
    }
    return [System.Drawing.Rectangle]::FromLTRB($left, $top, $right + 1, $bottom + 1)
}

function Write-PositionedOverlay([string]$SourcePath, [string]$TargetPath, [int]$X, [int]$Y, [int]$Size) {
    $source = [System.Drawing.Bitmap]::new($SourcePath)
    $target = [System.Drawing.Bitmap]::new(32, 32, [System.Drawing.Imaging.PixelFormat]::Format32bppArgb)
    $graphics = [System.Drawing.Graphics]::FromImage($target)
    try {
        $bounds = Get-VisibleBounds $source
        $graphics.Clear([System.Drawing.Color]::Transparent)
        $graphics.CompositingMode = [System.Drawing.Drawing2D.CompositingMode]::SourceCopy
        $graphics.CompositingQuality = [System.Drawing.Drawing2D.CompositingQuality]::HighSpeed
        $graphics.InterpolationMode = [System.Drawing.Drawing2D.InterpolationMode]::NearestNeighbor
        $graphics.PixelOffsetMode = [System.Drawing.Drawing2D.PixelOffsetMode]::Half
        if (!$bounds.IsEmpty) {
            $scale = [Math]::Min($Size / $bounds.Width, $Size / $bounds.Height)
            $width = [Math]::Max(1, [int][Math]::Round($bounds.Width * $scale))
            $height = [Math]::Max(1, [int][Math]::Round($bounds.Height * $scale))
            $destination = [System.Drawing.Rectangle]::new(
                $X + [int](($Size - $width) / 2),
                $Y + [int](($Size - $height) / 2),
                $width,
                $height
            )
            $graphics.DrawImage($source, $destination, $bounds, [System.Drawing.GraphicsUnit]::Pixel)
        }
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
    # The crest is fixed at the centre.  The two selected marks remain small
    # and wholly inside the rim, leaving their left/right roles unmistakable.
    Write-PositionedOverlay $crestSource (Join-Path $textureRoot "crest_center/$name.png") 10 9 13
    Write-PositionedOverlay $symbolSource (Join-Path $textureRoot "symbol_left/$name.png") 3 13 6
    Write-PositionedOverlay $symbolSource (Join-Path $textureRoot "symbol_right/$name.png") 23 13 6
    # Retain the old lower position only for historic three-symbol coins.
    Write-PositionedOverlay $symbolSource (Join-Path $textureRoot "symbol_bottom/$name.png") 13 23 6
}

Write-Output "Generated aligned crest, left, right, and bottom coin overlays under $textureRoot"
