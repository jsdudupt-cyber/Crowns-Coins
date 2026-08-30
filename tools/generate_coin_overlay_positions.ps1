[CmdletBinding()]
param(
    [Parameter(Mandatory = $true)]
    [string]$ResourceRoot
)

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'
Add-Type -AssemblyName System.Drawing

# These are deliberately bold, native-pixel emblems. The old workflow shrank
# a detailed 24 px drawing into an 8 px space, so most choices became a blur
# at the size of a Minecraft item. Seven pixels leaves enough room for the
# fixed crown and two genuinely readable secondary marks on one coin.
$SymbolNames = @(
    'sun', 'moon', 'star', 'crown', 'sword', 'shield', 'tower', 'dragon', 'wolf', 'eagle',
    'lion', 'horse', 'hammer', 'anvil', 'heart', 'flame', 'wave', 'leaf', 'flower', 'diamond',
    'mountain', 'river', 'cross', 'lightning', 'compass'
)

$SidePatterns = @{
    sun       = @('.##.##.', '#.###.#', '#######', '.#####.', '#######', '#.###.#', '.##.##.')
    moon      = @('..####.', '.##....', '.##....', '.##....', '.##....', '.##....', '..####.')
    star      = @('...#...', '.#####.', '..###..', '#######', '..###..', '.#####.', '...#...')
    crown     = @('#.#.#.#', '#######', '.#####.', '.#####.', '#######', '.......', '.......')
    sword     = @('...#...', '...#...', '...#...', '#######', '...#...', '..###..', '.#####.')
    shield    = @('.#####.', '##...##', '##...##', '.#####.', '.#####.', '..###..', '...#...')
    tower     = @('##.#.##', '#######', '..###..', '..###..', '..###..', '..###..', '#######')
    dragon    = @('...##..', '..####.', '.##.###', '...####', '..##.##', '.##...#', '##.....')
    wolf      = @('#.....#', '##...##', '.##.##.', '..###..', '..###..', '..#.#..', '.#...#.')
    eagle     = @('#.....#', '##...##', '###.###', '.#####.', '..###..', '...#...', '..#.#..')
    lion      = @('.#####.', '##.#.##', '##...##', '.#####.', '..###..', '.##.##.', '#######')
    horse     = @('..###..', '.#####.', '##.##..', '.#####.', '..##.##', '..##..#', '.##....')
    hammer    = @('.#####.', '.#####.', '...#...', '...#...', '...#...', '...#...', '..###..')
    anvil     = @('#######', '.#####.', '...#...', '..###..', '.#####.', '...#...', '..###..')
    heart     = @('.##.##.', '#######', '#######', '.#####.', '..###..', '...#...', '.......')
    flame     = @('...#...', '..###..', '.#####.', '######.', '.#####.', '..###..', '...#...')
    wave      = @('.......', '.##..##', '##.##.#', '.##.##.', '..##...', '.......', '.......')
    leaf      = @('....#..', '...###.', '..#####', '.######', '..#####', '...###.', '....#..')
    flower    = @('...#...', '.##.##.', '..###..', '#######', '..###..', '.##.##.', '...#...')
    diamond   = @('...#...', '..###..', '.#####.', '#######', '.#####.', '..###..', '...#...')
    mountain  = @('...#...', '..###..', '.#####.', '#######', '.##.##.', '.##.##.', '.......')
    river     = @('##.....', '.##....', '..##...', '...##..', '..##...', '.##....', '##.....')
    cross     = @('...#...', '...#...', '.#####.', '.#####.', '...#...', '...#...', '...#...')
    lightning = @('....###', '...##..', '..##...', '.#####.', '....##.', '...##..', '.###...')
    compass   = @('...#...', '..###..', '##.#.##', '.#####.', '##.#.##', '..###..', '...#...')
}

# A twelve-pixel crown gives the permanent kingdom mark priority over the two
# seven-pixel choices, while still leaving the side marks inside the rim.
$CenterCrownPattern = @(
    '#..#..#..#..',
    '##.#.##.#.##',
    '.##########.',
    '.##########.',
    '.##########.',
    '..########..',
    '...######...',
    '....####....'
)

$textureRoot = Join-Path $ResourceRoot 'assets/crownscoins/textures/item/overlay'
$outlineColor = [System.Drawing.Color]::FromArgb(255, 54, 26, 10)
$sideColor = [System.Drawing.Color]::FromArgb(255, 255, 244, 210)
$crownColor = [System.Drawing.Color]::FromArgb(255, 255, 220, 92)

function Assert-Pattern([string]$Name, [string[]]$Pattern, [int]$Width, [int]$Height) {
    if ($Pattern.Count -ne $Height -or @($Pattern | Where-Object { $_.Length -ne $Width }).Count -gt 0) {
        throw "Pattern '$Name' must be $Width by $Height pixels."
    }
}

function Write-PixelOverlay(
    [string[]]$Pattern,
    [string]$TargetPath,
    [int]$OriginX,
    [int]$OriginY,
    [System.Drawing.Color]$Color
) {
    $target = [System.Drawing.Bitmap]::new(32, 32, [System.Drawing.Imaging.PixelFormat]::Format32bppArgb)
    try {
        # First draw a one-pixel dark contour. It keeps pale emblems readable
        # on copper, iron, and gold without adding tiny decorative noise.
        for ($row = 0; $row -lt $Pattern.Count; $row++) {
            for ($column = 0; $column -lt $Pattern[$row].Length; $column++) {
                if ($Pattern[$row][$column] -ne '#') {
                    continue
                }
                for ($offsetY = -1; $offsetY -le 1; $offsetY++) {
                    for ($offsetX = -1; $offsetX -le 1; $offsetX++) {
                        $x = $OriginX + $column + $offsetX
                        $y = $OriginY + $row + $offsetY
                        if ($x -ge 0 -and $x -lt 32 -and $y -ge 0 -and $y -lt 32) {
                            $target.SetPixel($x, $y, $outlineColor)
                        }
                    }
                }
            }
        }
        # The opaque face is intentionally simple: each mark survives both the
        # inventory scale and the large minting preview.
        for ($row = 0; $row -lt $Pattern.Count; $row++) {
            for ($column = 0; $column -lt $Pattern[$row].Length; $column++) {
                if ($Pattern[$row][$column] -eq '#') {
                    $target.SetPixel($OriginX + $column, $OriginY + $row, $Color)
                }
            }
        }
        [void][System.IO.Directory]::CreateDirectory([System.IO.Path]::GetDirectoryName($TargetPath))
        $target.Save($TargetPath, [System.Drawing.Imaging.ImageFormat]::Png)
    } finally {
        $target.Dispose()
    }
}

foreach ($name in $SymbolNames) {
    $pattern = [string[]]$SidePatterns[$name]
    Assert-Pattern $name $pattern 7 7
    $id = $SymbolNames.IndexOf($name) + 1
    $fileName = ('{0:D2}_{1}.png' -f $id, $name)

    # The fixed crest only needs the crown today, but keeping the remaining
    # generated files valid avoids breaking legacy stacks and UI lookups.
    if ($name -eq 'crown') {
        Assert-Pattern 'center crown' $CenterCrownPattern 12 8
        Write-PixelOverlay $CenterCrownPattern (Join-Path $textureRoot "crest_center/$fileName") 10 11 $crownColor
    } else {
        Write-PixelOverlay $pattern (Join-Path $textureRoot "crest_center/$fileName") 13 13 $crownColor
    }

    Write-PixelOverlay $pattern (Join-Path $textureRoot "symbol_left/$fileName") 2 13 $sideColor
    Write-PixelOverlay $pattern (Join-Path $textureRoot "symbol_right/$fileName") 23 13 $sideColor
    # Kept only for legacy coins that used a lower third symbol.
    Write-PixelOverlay $pattern (Join-Path $textureRoot "symbol_bottom/$fileName") 13 22 $sideColor
}

Write-Output "Generated high-contrast Crown and secondary-symbol overlays under $textureRoot"
