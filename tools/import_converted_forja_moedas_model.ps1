[CmdletBinding()]
param(
    [string]$SourceModel = 'C:\Users\JsDud\Downloads\mine\completo\forja_moedas - Converted.json',
    [string]$SourceTexture = (Join-Path $PSScriptRoot '..\build\incoming-forja-moedas\forja_moedas_textura.png')
)

$ErrorActionPreference = 'Stop'

$projectRoot = (Resolve-Path (Join-Path $PSScriptRoot '..')).Path
$sourceModel = (Resolve-Path $SourceModel).Path
$sourceTexture = (Resolve-Path $SourceTexture).Path
$modelsDirectory = Join-Path $projectRoot 'src\main\resources\assets\crownscoins\models\block'
$texturesDirectory = Join-Path $projectRoot 'src\main\resources\assets\crownscoins\textures\block'
$backupDirectory = Join-Path $projectRoot 'build\forja-moedas-converted-backup'

$source = Get-Content -LiteralPath $sourceModel -Raw | ConvertFrom-Json
if ($null -eq $source.elements -or @($source.elements).Count -eq 0) {
    throw 'O arquivo convertido não contém elementos de modelo.'
}

function Copy-Faces {
    param($Faces)
    $result = [ordered]@{}
    foreach ($property in $Faces.PSObject.Properties) {
        $face = $property.Value
        $copy = [ordered]@{}
        if ($null -ne $face.uv) {
            $copy.uv = [double[]]@($face.uv | ForEach-Object { [double]$_ })
        }
        if ($null -ne $face.rotation) { $copy.rotation = [int]$face.rotation }
        if ($null -ne $face.tintindex) { $copy.tintindex = [int]$face.tintindex }
        if ($null -ne $face.cullface) { $copy.cullface = [string]$face.cullface }
        $copy.texture = '#all'
        $result[$property.Name] = $copy
    }
    return $result
}

function Write-Model {
    param(
        [string]$Path,
        [System.Collections.Generic.List[object]]$Elements,
        [string]$TextureId
    )
    $model = [ordered]@{
        credit = 'Imported from the supplied Blockbench Java conversion.'
        textures = [ordered]@{
            particle = $TextureId
            all = $TextureId
        }
        elements = @($Elements.ToArray())
    }
    $json = $model | ConvertTo-Json -Depth 12
    $utf8 = New-Object System.Text.UTF8Encoding($false)
    [System.IO.File]::WriteAllText($Path, $json, $utf8)
}

# Blockbench exported the 32-pixel-wide table centered on X=8, which makes it
# run from -8 to 24. The double block in the mod instead runs from FOOT (0..16)
# to HEAD (16..32), so each converted X coordinate is shifted by +8. Elements
# crossing the seam are emitted only with FOOT to preserve their wide UV map.
$pressElements = [System.Collections.Generic.List[object]]::new()
$furnaceElements = [System.Collections.Generic.List[object]]::new()
$index = 0
foreach ($element in @($source.elements)) {
    $convertedFromX = [double]$element.from[0]
    $convertedToX = [double]$element.to[0]
    $worldFromX = $convertedFromX + 8.0
    $worldToX = $convertedToX + 8.0
    $centerX = ($worldFromX + $worldToX) / 2.0
    $crossesJoin = $worldFromX -lt 16.0 -and $worldToX -gt 16.0
    $isPress = $crossesJoin -or $centerX -lt 16.0
    $localOffsetX = if ($isPress) { 8.0 } else { -8.0 }

    $index++
    $entry = [ordered]@{
        name = ('forja_java_{0:D3}_{1}' -f $index, (($element.name -replace '[^A-Za-z0-9_ -]', '') -replace '\s+', '_'))
        from = [double[]]@(
            ([math]::Round($convertedFromX + $localOffsetX, 4)),
            ([math]::Round([double]$element.from[1], 4)),
            ([math]::Round([double]$element.from[2], 4))
        )
        to = [double[]]@(
            ([math]::Round($convertedToX + $localOffsetX, 4)),
            ([math]::Round([double]$element.to[1], 4)),
            ([math]::Round([double]$element.to[2], 4))
        )
        faces = (Copy-Faces $element.faces)
    }

    if ($null -ne $element.rotation -and [math]::Abs([double]$element.rotation.angle) -gt 0.0001) {
        $entry.rotation = [ordered]@{
            angle = [double]$element.rotation.angle
            axis = [string]$element.rotation.axis
            origin = [double[]]@(
                ([math]::Round(([double]$element.rotation.origin[0] + $localOffsetX), 4)),
                ([math]::Round([double]$element.rotation.origin[1], 4)),
                ([math]::Round([double]$element.rotation.origin[2], 4))
            )
            rescale = $false
        }
    }

    if ($isPress) { [void]$pressElements.Add($entry) }
    else { [void]$furnaceElements.Add($entry) }
}

if ($pressElements.Count -eq 0 -or $furnaceElements.Count -eq 0) {
    throw 'A conversão não conseguiu separar a prensa da fornalha.'
}

New-Item -ItemType Directory -Path $backupDirectory -Force | Out-Null
foreach ($name in @('mint_house_royal_left.json', 'mint_house_furnace_left.json')) {
    $current = Join-Path $modelsDirectory $name
    if (Test-Path -LiteralPath $current) {
        Copy-Item -LiteralPath $current -Destination (Join-Path $backupDirectory $name) -Force
    }
}

$textureId = 'crownscoins:block/forja_moedas_textura'
Copy-Item -LiteralPath $sourceTexture -Destination (Join-Path $texturesDirectory 'forja_moedas_textura.png') -Force
Write-Model -Path (Join-Path $modelsDirectory 'mint_house_royal_left.json') -Elements $pressElements -TextureId $textureId
Write-Model -Path (Join-Path $modelsDirectory 'mint_house_furnace_left.json') -Elements $furnaceElements -TextureId $textureId

Write-Host "Modelo Java aplicado: $($pressElements.Count) peças na prensa e $($furnaceElements.Count) peças na fornalha."
