[CmdletBinding()]
param(
    [Parameter(Mandatory = $true)] [string]$IronSource,
    [Parameter(Mandatory = $true)] [string]$CopperSource,
    [Parameter(Mandatory = $true)] [string]$GoldSource,
    [Parameter(Mandatory = $true)] [string]$MintHouseSource,
    [Parameter(Mandatory = $true)] [string]$ResourceRoot
)

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'
Add-Type -AssemblyName System.Drawing

$SymbolNames = @(
    'sun', 'moon', 'star', 'crown', 'sword', 'shield', 'tower', 'dragon', 'wolf', 'eagle',
    'lion', 'horse', 'hammer', 'anvil', 'heart', 'flame', 'wave', 'leaf', 'flower', 'diamond',
    'mountain', 'river', 'cross', 'lightning', 'compass'
)

$Patterns = @{
    sun       = @('..#..#..', '...##...', '.#.##.#.', '..####..', '########', '..####..', '.#.##.#.', '...##...')
    moon      = @('...####.', '..##....', '.##.....', '.##.....', '.##.....', '.##.....', '..##....', '...####.')
    star      = @('...#....', '..###...', '#######.', '..###...', '.#####..', '##.#.##.', '...#....', '..#.#...')
    crown     = @('#..#..#.', '##.#.##.', '.#####..', '.#####..', '#######.', '#######.', '..###...', '........')
    sword     = @('...#....', '...#....', '...#....', '...#....', '.#####..', '...#....', '..###...', '.#####..')
    shield    = @('.######.', '##....##', '##....##', '.######.', '.######.', '..####..', '...##...', '........')
    tower     = @('.##.##..', '.##.##..', '########', '..####..', '..####..', '..####..', '..####..', '########')
    dragon    = @('.#....#.', '.##..##.', '..####..', '.##.##..', '##...##.', '...##...', '..#..#..', '.#....#.')
    wolf      = @('#......#', '##....##', '.##..##.', '..####..', '.######.', '..#..#..', '.#....#.', '........')
    eagle     = @('#......#', '##....##', '.##..##.', '..####..', '...##...', '..#..#..', '.#....#.', '........')
    lion      = @('.######.', '##.##.##', '##....##', '.######.', '...##...', '.#....#.', '.######.', '........')
    horse     = @('..###...', '.#####..', '##.##...', '.#####..', '..##.##.', '..##..##', '.##...##', '........')
    hammer    = @('.#####..', '.#####..', '...#....', '...#....', '...#....', '...#....', '...#....', '..###...')
    anvil     = @('########', '.######.', '...##...', '..####..', '.######.', '...##...', '...##...', '..####..')
    heart     = @('.##.##..', '########', '########', '.######.', '..####..', '...##...', '....#...', '........')
    flame     = @('...#....', '..###...', '.#####..', '.######.', '..####..', '..####..', '...##...', '........')
    wave      = @('........', '........', '.##..##.', '##.##.##', '..##.##.', '...##...', '........', '........')
    leaf      = @('....#...', '...###..', '..#####.', '.######.', '..#####.', '...###..', '....#...', '........')
    flower    = @('...#....', '.#.#.#..', '..###...', '.#####..', '..###...', '.#.#.#..', '...#....', '........')
    diamond   = @('...#....', '..###...', '.#####..', '#######.', '.#####..', '..###...', '...#....', '........')
    mountain  = @('........', '...#....', '..###...', '.#####..', '#######.', '..#.#...', '........', '........')
    river     = @('.##.....', '..##....', '...##...', '.##.##..', '##...##.', '...##...', '....##..', '.....##.')
    cross     = @('...#....', '...#....', '.#####..', '.#####..', '...#....', '...#....', '...#....', '........')
    lightning = @('....###.', '...##...', '..##....', '.#####..', '....##..', '...##...', '.###....', '........')
    compass   = @('...#....', '..###...', '.#####..', '#######.', '.#####..', '..###...', '...#....', '........')
}

function Ensure-Directory([string]$Path) {
    [void][System.IO.Directory]::CreateDirectory($Path)
}

function Save-Png([System.Drawing.Bitmap]$Bitmap, [string]$Path) {
    Ensure-Directory ([System.IO.Path]::GetDirectoryName($Path))
    $Bitmap.Save($Path, [System.Drawing.Imaging.ImageFormat]::Png)
}

function New-ScaledBitmap([string]$SourcePath, [int]$Size) {
    $source = [System.Drawing.Bitmap]::new($SourcePath)
    try {
        $result = [System.Drawing.Bitmap]::new($Size, $Size, [System.Drawing.Imaging.PixelFormat]::Format32bppArgb)
        $graphics = [System.Drawing.Graphics]::FromImage($result)
        try {
            $graphics.Clear([System.Drawing.Color]::Transparent)
            $graphics.CompositingMode = [System.Drawing.Drawing2D.CompositingMode]::SourceCopy
            $graphics.CompositingQuality = [System.Drawing.Drawing2D.CompositingQuality]::HighSpeed
            $graphics.InterpolationMode = [System.Drawing.Drawing2D.InterpolationMode]::NearestNeighbor
            $graphics.PixelOffsetMode = [System.Drawing.Drawing2D.PixelOffsetMode]::Half
            $graphics.DrawImage($source, [System.Drawing.Rectangle]::new(0, 0, $Size, $Size))
        } finally {
            $graphics.Dispose()
        }
        return $result
    } finally {
        $source.Dispose()
    }
}

function Add-StyleMarks([System.Drawing.Bitmap]$Bitmap, [int]$StyleId, [System.Drawing.Color]$Color) {
    $positions = @(@(3, 3), @(26, 3), @(3, 26), @(26, 26), @(14, 2))
    for ($bit = 0; $bit -lt $positions.Count; $bit++) {
        if (($StyleId -band (1 -shl $bit)) -ne 0) {
            $point = $positions[$bit]
            for ($x = 0; $x -lt 2; $x++) {
                for ($y = 0; $y -lt 2; $y++) {
                    $Bitmap.SetPixel($point[0] + $x, $point[1] + $y, $Color)
                }
            }
        }
    }
}

function Write-CoinTextures([string]$MetalName, [string]$SourcePath, [System.Drawing.Color]$MarkColor) {
    for ($index = 0; $index -lt $SymbolNames.Count; $index++) {
        $styleId = $index + 1
        $name = ('{0:D2}_{1}' -f $styleId, $SymbolNames[$index])
        $bitmap = New-ScaledBitmap $SourcePath 32
        try {
            Add-StyleMarks $bitmap $styleId $MarkColor
            Save-Png $bitmap (Join-Path $TextureRoot ("item/coin/{0}_{1}.png" -f $MetalName, $name))
            if ($styleId -eq 1) {
                Save-Png $bitmap (Join-Path $TextureRoot ("item/{0}_coin.png" -f $MetalName))
            }
        } finally {
            $bitmap.Dispose()
        }
    }
}

function Write-Overlay([string]$Name, [string[]]$Pattern, [System.Drawing.Color]$Color, [string]$Path) {
    $bitmap = [System.Drawing.Bitmap]::new(32, 32, [System.Drawing.Imaging.PixelFormat]::Format32bppArgb)
    try {
        $shadow = [System.Drawing.Color]::FromArgb(175, 34, 24, 12)
        for ($row = 0; $row -lt 8; $row++) {
            for ($column = 0; $column -lt 8; $column++) {
                if ($Pattern[$row][$column] -eq '#') {
                    $x = 4 + $column * 3
                    $y = 4 + $row * 3
                    for ($offsetX = 0; $offsetX -lt 3; $offsetX++) {
                        for ($offsetY = 0; $offsetY -lt 3; $offsetY++) {
                            if ($x + $offsetX + 1 -lt 32 -and $y + $offsetY + 1 -lt 32) {
                                $bitmap.SetPixel($x + $offsetX + 1, $y + $offsetY + 1, $shadow)
                            }
                            $bitmap.SetPixel($x + $offsetX, $y + $offsetY, $Color)
                        }
                    }
                }
            }
        }
        Save-Png $bitmap $Path
    } finally {
        $bitmap.Dispose()
    }
}

$TextureRoot = Join-Path $ResourceRoot 'assets/crownscoins/textures'
$blank = [System.Drawing.Bitmap]::new(32, 32, [System.Drawing.Imaging.PixelFormat]::Format32bppArgb)
try {
    Save-Png $blank (Join-Path $TextureRoot 'item/overlay/blank.png')
} finally {
    $blank.Dispose()
}
Write-CoinTextures 'iron' $IronSource ([System.Drawing.Color]::FromArgb(220, 240, 244, 248))
Write-CoinTextures 'copper' $CopperSource ([System.Drawing.Color]::FromArgb(220, 255, 220, 160))
Write-CoinTextures 'gold' $GoldSource ([System.Drawing.Color]::FromArgb(220, 255, 250, 192))

for ($index = 0; $index -lt $SymbolNames.Count; $index++) {
    $name = ('{0:D2}_{1}' -f ($index + 1), $SymbolNames[$index])
    Write-Overlay $name $Patterns[$SymbolNames[$index]] ([System.Drawing.Color]::FromArgb(255, 255, 219, 115)) (Join-Path $TextureRoot ("item/overlay/crest/{0}.png" -f $name))
    Write-Overlay $name $Patterns[$SymbolNames[$index]] ([System.Drawing.Color]::FromArgb(255, 244, 244, 232)) (Join-Path $TextureRoot ("item/overlay/symbol/{0}.png" -f $name))
}

$blockTexture = New-ScaledBitmap $MintHouseSource 32
try {
    foreach ($name in @('mint_house_top', 'mint_house_bottom', 'mint_house_front', 'mint_house_back', 'mint_house_side')) {
        Save-Png $blockTexture (Join-Path $TextureRoot ("block/{0}.png" -f $name))
    }
} finally {
    $blockTexture.Dispose()
}

Write-Output "Generated 75 coin bases, 50 transparent overlays, and 5 Mint House textures under $TextureRoot"
