[CmdletBinding()]
param(
    [Parameter(Mandatory = $true)] [string]$ResourceRoot
)

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

$SymbolNames = @(
    'sun', 'moon', 'star', 'crown', 'sword', 'shield', 'tower', 'dragon', 'wolf', 'eagle',
    'lion', 'horse', 'hammer', 'anvil', 'heart', 'flame', 'wave', 'leaf', 'flower', 'diamond',
    'mountain', 'river', 'cross', 'lightning', 'compass'
)
$Namespace = 'crownscoins'
$ModelRoot = Join-Path $ResourceRoot 'assets/crownscoins/models/item'
$ItemRoot = Join-Path $ResourceRoot 'assets/crownscoins/items'

function Write-Json([string]$Path, [object]$Data) {
    [void][System.IO.Directory]::CreateDirectory([System.IO.Path]::GetDirectoryName($Path))
    [System.IO.File]::WriteAllText($Path, ($Data | ConvertTo-Json -Depth 20), [System.Text.UTF8Encoding]::new($false))
}

function Model-Reference([string]$Model) {
    return [ordered]@{ type = 'minecraft:model'; model = $Model }
}

function Select-Model([string]$Property, [string]$Fallback, [string]$ModelPrefix) {
    $cases = @()
    for ($index = 0; $index -lt $SymbolNames.Count; $index++) {
        $id = $index + 1
        $name = ('{0:D2}_{1}' -f $id, $SymbolNames[$index])
        $cases += [ordered]@{
            when = $id
            model = (Model-Reference ("${Namespace}:item/$ModelPrefix$name"))
        }
    }
    return [ordered]@{
        type = 'minecraft:select'
        property = $Property
        fallback = (Model-Reference $Fallback)
        cases = $cases
    }
}

function Select-CustomModelDataSymbol([int]$Index, [string]$Fallback, [string]$ModelPrefix) {
    $cases = @()
    for ($symbolIndex = 0; $symbolIndex -lt $SymbolNames.Count; $symbolIndex++) {
        $id = $symbolIndex + 1
        $name = ('{0:D2}_{1}' -f $id, $SymbolNames[$symbolIndex])
        $cases += [ordered]@{
            when = $SymbolNames[$symbolIndex]
            model = (Model-Reference ("${Namespace}:item/$ModelPrefix$name"))
        }
    }
    return [ordered]@{
        type = 'minecraft:select'
        property = 'minecraft:custom_model_data'
        index = $Index
        fallback = (Model-Reference $Fallback)
        cases = $cases
    }
}

foreach ($metal in @('iron', 'copper', 'gold')) {
    for ($index = 0; $index -lt $SymbolNames.Count; $index++) {
        $id = $index + 1
        $name = ('{0:D2}_{1}' -f $id, $SymbolNames[$index])
        Write-Json (Join-Path $ModelRoot ("coin/$metal`_$name.json")) ([ordered]@{
            parent = 'minecraft:item/generated'
            textures = [ordered]@{ layer0 = "${Namespace}:item/coin/$metal`_$name" }
        })
    }
}

foreach ($kind in @('crest', 'crest_center', 'symbol', 'symbol_left', 'symbol_right', 'symbol_bottom')) {
    for ($index = 0; $index -lt $SymbolNames.Count; $index++) {
        $id = $index + 1
        $name = ('{0:D2}_{1}' -f $id, $SymbolNames[$index])
        Write-Json (Join-Path $ModelRoot ("overlay/$kind/$name.json")) ([ordered]@{
            parent = 'minecraft:item/generated'
            textures = [ordered]@{ layer0 = "${Namespace}:item/overlay/$kind/$name" }
        })
    }
}

Write-Json (Join-Path $ModelRoot 'overlay/blank.json') ([ordered]@{
    parent = 'minecraft:item/generated'
    textures = [ordered]@{ layer0 = "${Namespace}:item/overlay/blank" }
})

Write-Json (Join-Path $ModelRoot 'overlay/steve_face.json') ([ordered]@{
    parent = 'minecraft:item/generated'
    textures = [ordered]@{ layer0 = "${Namespace}:item/overlay/steve_face" }
})

foreach ($metal in @('iron', 'copper', 'gold')) {
    # Coins deliberately stay visually neutral in inventories and in the
    # world.  The minting screen is where the selected crest and symbols are
    # displayed at a readable scale; layering them on a 16px item made every
    # denomination look cluttered and could expose an unwanted face overlay.
    $model = Model-Reference "${Namespace}:item/coin/$metal`_04_crown"
    Write-Json (Join-Path $ItemRoot "$metal`_coin.json") ([ordered]@{ model = $model })
}

Write-Output "Generated 75 base item models, 151 overlay models, and 3 clean coin item definitions."
