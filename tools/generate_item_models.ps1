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

foreach ($metal in @('iron', 'copper', 'gold')) {
    $model = [ordered]@{
        type = 'minecraft:composite'
        models = @(
            (Select-Model "${Namespace}:coin_style" "${Namespace}:item/coin/$metal`_01_sun" "coin/$metal`_"),
            (Select-Model "${Namespace}:coin_crest" "${Namespace}:item/overlay/blank" 'overlay/crest_center/'),
            (Select-Model "${Namespace}:coin_symbol_one" "${Namespace}:item/overlay/blank" 'overlay/symbol_left/'),
            (Select-Model "${Namespace}:coin_symbol_two" "${Namespace}:item/overlay/blank" 'overlay/symbol_right/'),
            (Select-Model "${Namespace}:coin_symbol_three" "${Namespace}:item/overlay/blank" 'overlay/symbol_bottom/')
        )
    }
    Write-Json (Join-Path $ItemRoot "$metal`_coin.json") ([ordered]@{ model = $model })
}

Write-Output "Generated 75 base item models, 151 overlay models, and 3 modern composite item definitions."
