[CmdletBinding()]
param(
    [string]$ResourceRoot = (Join-Path $PSScriptRoot '..\src\main\resources'),
    [string]$SourceZip = 'C:\Users\JsDud\Documents\Codex\2026-08-30\prior-conversation-with-codex-conversation-role\outputs\alexis_64_coins_textures_originals.zip'
)

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

Add-Type -AssemblyName System.Drawing
Add-Type -AssemblyName System.IO.Compression.FileSystem

# The three first variants are copied byte-for-byte from the user-provided
# alexis_64_coins_textures_originals.zip archive.  The other silhouettes are
# made deterministically from each source coin's native palette, so they stay
# at the same Minecraft 16x16 scale without borrowing artwork from elsewhere.
$metals = @(
    [pscustomobject]@{ Id = 'copper'; ArchiveItem = 'item/copper_coin.png' },
    [pscustomobject]@{ Id = 'iron';   ArchiveItem = 'item/iron_coin.png' },
    [pscustomobject]@{ Id = 'gold';   ArchiveItem = 'item/gold_coin.png' }
)

$shapes = @(
    [pscustomobject]@{ Id = 1; Name = 'round'; Mask = $null },
    [pscustomobject]@{ Id = 2; Name = 'octagon'; Mask = @(
        '................',
        '................',
        '.....######.....',
        '....########....',
        '...##########...',
        '...##########...',
        '..############..',
        '..############..',
        '..############..',
        '..############..',
        '...##########...',
        '...##########...',
        '....########....',
        '.....######.....',
        '................',
        '................'
    ) },
    [pscustomobject]@{ Id = 3; Name = 'square_hole'; Mask = @(
        '................',
        '................',
        '....########....',
        '...##########...',
        '..############..',
        '..####....####..',
        '..####....####..',
        '..####....####..',
        '..####....####..',
        '..####....####..',
        '..####....####..',
        '..############..',
        '...##########...',
        '....########....',
        '................',
        '................'
    ) },
    [pscustomobject]@{ Id = 4; Name = 'scalloped'; Mask = @(
        '................',
        '................',
        '......####......',
        '....########....',
        '...##########...',
        '..####.##.####..',
        '..############..',
        '.##############.',
        '.##############.',
        '..############..',
        '..####.##.####..',
        '...##########...',
        '....########....',
        '......####......',
        '................',
        '................'
    ) },
    [pscustomobject]@{ Id = 5; Name = 'hexagon'; Mask = @(
        '................',
        '................',
        '.....######.....',
        '.....######.....',
        '....########....',
        '....########....',
        '...##########...',
        '...##########...',
        '...##########...',
        '...##########...',
        '....########....',
        '....########....',
        '.....######.....',
        '.....######.....',
        '................',
        '................'
    ) },
    [pscustomobject]@{ Id = 6; Name = 'chipped'; Mask = @(
        '................',
        '................',
        '......####......',
        '.....#######....',
        '...##########...',
        '...##########...',
        '..############..',
        '..############..',
        '..############..',
        '..##########....',
        '...#########....',
        '....#######.....',
        '....######......',
        '......####......',
        '................',
        '................'
    ) },
    [pscustomobject]@{ Id = 7; Name = 'oval'; Mask = @(
        '................',
        '................',
        '................',
        '....########....',
        '..############..',
        '.##############.',
        '.##############.',
        '.##############.',
        '.##############.',
        '.##############.',
        '.##############.',
        '..############..',
        '....########....',
        '................',
        '................',
        '................'
    ) },
    [pscustomobject]@{ Id = 8; Name = 'shield'; Mask = @(
        '................',
        '................',
        '...##########...',
        '..############..',
        '..############..',
        '...##########...',
        '...##########...',
        '....########....',
        '....########....',
        '.....######.....',
        '.....######.....',
        '......####......',
        '......####......',
        '.......##.......',
        '................',
        '................'
    ) },
    [pscustomobject]@{ Id = 9; Name = 'triangle'; Mask = @(
        '................',
        '................',
        '.......##.......',
        '......####......',
        '......####......',
        '.....######.....',
        '.....######.....',
        '....########....',
        '....########....',
        '...##########...',
        '...##########...',
        '..############..',
        '..############..',
        '..############..',
        '................',
        '................'
    ) },
    [pscustomobject]@{ Id = 10; Name = 'dodecagon_chipped'; Mask = @(
        '................',
        '................',
        '......####......',
        '.....######.....',
        '....########....',
        '...##########...',
        '..############..',
        '..############..',
        '..############..',
        '..############..',
        '..############..',
        '...#########....',
        '....#######.....',
        '......####......',
        '................',
        '................'
    ) }
)

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

function Copy-ZipEntry([System.IO.Compression.ZipArchive]$Archive, [string]$EntryName, [string]$Destination) {
    $entry = $Archive.GetEntry($EntryName)
    if ($null -eq $entry) {
        throw "Missing expected source texture '$EntryName' in archive."
    }

    [void][System.IO.Directory]::CreateDirectory([System.IO.Path]::GetDirectoryName($Destination))
    $input = $entry.Open()
    $output = [System.IO.File]::Create($Destination)
    try {
        $input.CopyTo($output)
    } finally {
        $output.Dispose()
        $input.Dispose()
    }
}

function Test-ShapePixel([string[]]$Mask, [int]$X, [int]$Y) {
    if ($X -lt 0 -or $X -ge 16 -or $Y -lt 0 -or $Y -ge 16) {
        return $false
    }
    return $Mask[$Y].Substring($X, 1) -eq '#'
}

function Test-ShapeBoundary([string[]]$Mask, [int]$X, [int]$Y) {
    if (-not (Test-ShapePixel $Mask $X $Y)) {
        return $false
    }

    for ($offsetY = -1; $offsetY -le 1; $offsetY++) {
        for ($offsetX = -1; $offsetX -le 1; $offsetX++) {
            if ($offsetX -eq 0 -and $offsetY -eq 0) {
                continue
            }
            if (-not (Test-ShapePixel $Mask ($X + $offsetX) ($Y + $offsetY))) {
                return $true
            }
        }
    }

    return $false
}

function Get-DarkestSourceColor([System.Drawing.Bitmap]$Source) {
    $darkest = [System.Drawing.Color]::Transparent
    $brightness = [double]::PositiveInfinity
    for ($x = 0; $x -lt $Source.Width; $x++) {
        for ($y = 0; $y -lt $Source.Height; $y++) {
            $pixel = $Source.GetPixel($x, $y)
            if ($pixel.A -eq 0) {
                continue
            }
            $candidate = ($pixel.R + $pixel.G + $pixel.B) / 3.0
            if ($candidate -lt $brightness) {
                $brightness = $candidate
                $darkest = $pixel
            }
        }
    }
    return $darkest
}

function Get-SampledSourceColor(
    [System.Drawing.Bitmap]$Source,
    [System.Drawing.Rectangle]$SourceBounds,
    [int]$TargetX,
    [int]$TargetY,
    [System.Drawing.Color]$Fallback
) {
    $normalX = ($TargetX - 2) / 11.0
    $normalY = ($TargetY - 2) / 11.0
    $sampleX = [Math]::Round($SourceBounds.Left + ($normalX * ($SourceBounds.Width - 1)))
    $sampleY = [Math]::Round($SourceBounds.Top + ($normalY * ($SourceBounds.Height - 1)))
    $sampleX = [Math]::Max($SourceBounds.Left, [Math]::Min($SourceBounds.Right - 1, $sampleX))
    $sampleY = [Math]::Max($SourceBounds.Top, [Math]::Min($SourceBounds.Bottom - 1, $sampleY))

    $pixel = $Source.GetPixel($sampleX, $sampleY)
    if ($pixel.A -gt 0) {
        return $pixel
    }

    $best = $Fallback
    $bestDistance = [double]::PositiveInfinity
    for ($sourceX = $SourceBounds.Left; $sourceX -lt $SourceBounds.Right; $sourceX++) {
        for ($sourceY = $SourceBounds.Top; $sourceY -lt $SourceBounds.Bottom; $sourceY++) {
            $candidate = $Source.GetPixel($sourceX, $sourceY)
            if ($candidate.A -eq 0) {
                continue
            }
            $distance = (($sampleX - $sourceX) * ($sampleX - $sourceX)) + (($sampleY - $sourceY) * ($sampleY - $sourceY))
            if ($distance -lt $bestDistance) {
                $bestDistance = $distance
                $best = $candidate
            }
        }
    }

    return $best
}

function New-ShapedCoin([System.Drawing.Bitmap]$Source, [string[]]$Mask) {
    $target = [System.Drawing.Bitmap]::new(16, 16, [System.Drawing.Imaging.PixelFormat]::Format32bppArgb)
    $sourceBounds = Get-VisibleBounds $Source
    $darkest = Get-DarkestSourceColor $Source
    try {
        for ($x = 0; $x -lt 16; $x++) {
            for ($y = 0; $y -lt 16; $y++) {
                if (-not (Test-ShapePixel $Mask $x $y)) {
                    continue
                }
                $pixel = Get-SampledSourceColor $Source $sourceBounds $x $y $darkest
                if (Test-ShapeBoundary $Mask $x $y) {
                    # A dark shared rim makes every silhouette legible at 16px,
                    # including the forged defects and the square opening.
                    $pixel = $darkest
                }
                $target.SetPixel($x, $y, $pixel)
            }
        }
        return $target
    } catch {
        $target.Dispose()
        throw
    }
}

function Save-ShapeIcon([System.Drawing.Bitmap]$Source, [string]$Destination) {
    $icon = [System.Drawing.Bitmap]::new(32, 32, [System.Drawing.Imaging.PixelFormat]::Format32bppArgb)
    $graphics = [System.Drawing.Graphics]::FromImage($icon)
    try {
        $graphics.Clear([System.Drawing.Color]::Transparent)
        $graphics.InterpolationMode = [System.Drawing.Drawing2D.InterpolationMode]::NearestNeighbor
        $graphics.PixelOffsetMode = [System.Drawing.Drawing2D.PixelOffsetMode]::Half
        $graphics.CompositingMode = [System.Drawing.Drawing2D.CompositingMode]::SourceCopy
        $graphics.DrawImage($Source, [System.Drawing.Rectangle]::new(0, 0, 32, 32), 0, 0, 16, 16, [System.Drawing.GraphicsUnit]::Pixel)
        $icon.Save($Destination, [System.Drawing.Imaging.ImageFormat]::Png)
    } finally {
        $graphics.Dispose()
        $icon.Dispose()
    }
}

function Write-Json([string]$Path, [object]$Data) {
    [void][System.IO.Directory]::CreateDirectory([System.IO.Path]::GetDirectoryName($Path))
    [System.IO.File]::WriteAllText($Path, ($Data | ConvertTo-Json -Depth 10), [System.Text.UTF8Encoding]::new($false))
}

foreach ($shape in $shapes | Where-Object { $null -ne $_.Mask }) {
    if ($shape.Mask.Count -ne 16 -or @($shape.Mask | Where-Object { $_.Length -ne 16 }).Count -ne 0) {
        throw "Shape '$($shape.Name)' must have exactly sixteen 16-character mask rows."
    }
}

$assetRoot = Join-Path $ResourceRoot 'assets/crownscoins'
$textureRoot = Join-Path $assetRoot 'textures/item/coin_shape'
$iconRoot = Join-Path $assetRoot 'textures/item/shape'
$modelRoot = Join-Path $assetRoot 'models/item/coin_shape'
[void][System.IO.Directory]::CreateDirectory($textureRoot)
[void][System.IO.Directory]::CreateDirectory($iconRoot)
[void][System.IO.Directory]::CreateDirectory($modelRoot)

$archive = [System.IO.Compression.ZipFile]::OpenRead($SourceZip)
try {
    foreach ($metal in $metals) {
        $roundName = ('{0}_01_round.png' -f $metal.Id)
        $roundPath = Join-Path $textureRoot $roundName
        Copy-ZipEntry $archive $metal.ArchiveItem $roundPath

        $source = [System.Drawing.Bitmap]::new($roundPath)
        try {
            foreach ($shape in $shapes) {
                $shapeName = ('{0}_{1:D2}_{2}' -f $metal.Id, $shape.Id, $shape.Name)
                $texturePath = Join-Path $textureRoot "$shapeName.png"
                if ($shape.Id -ne 1) {
                    $variant = New-ShapedCoin $source $shape.Mask
                    try {
                        $variant.Save($texturePath, [System.Drawing.Imaging.ImageFormat]::Png)
                    } finally {
                        $variant.Dispose()
                    }
                }

                $model = [ordered]@{
                    parent = 'minecraft:item/generated'
                    textures = [ordered]@{
                        layer0 = "crownscoins:item/coin_shape/$shapeName"
                    }
                }
                Write-Json (Join-Path $modelRoot "$shapeName.json") $model
            }
        } finally {
            $source.Dispose()
        }
    }

    # These neutral selector sprites are 2x nearest-neighbour previews of the
    # bronze variants.  Metal-specific full item icons stay in coin_shape/.
    foreach ($shape in $shapes) {
        $name = ('copper_{0:D2}_{1}.png' -f $shape.Id, $shape.Name)
        $source = [System.Drawing.Bitmap]::new((Join-Path $textureRoot $name))
        try {
            Save-ShapeIcon $source (Join-Path $iconRoot ('shape_{0:D2}.png' -f $shape.Id))
        } finally {
            $source.Dispose()
        }
    }
} finally {
    $archive.Dispose()
}

Write-Output "Generated 30 16x16 coin-shape textures, 30 item models, and 10 32x32 selector icons."
