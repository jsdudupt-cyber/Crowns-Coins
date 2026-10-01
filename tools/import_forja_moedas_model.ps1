[CmdletBinding()]
param(
    [string]$InputDirectory = (Join-Path $PSScriptRoot '..\build\incoming-forja-moedas')
)

$ErrorActionPreference = 'Stop'

$projectRoot = (Resolve-Path (Join-Path $PSScriptRoot '..')).Path
$inputDirectory = (Resolve-Path $InputDirectory).Path
$bbmodelPath = Join-Path $inputDirectory 'forja_moedas.bbmodel'
$texturePath = Join-Path $inputDirectory 'forja_moedas_textura.png'
$modelsDirectory = Join-Path $projectRoot 'src\main\resources\assets\crownscoins\models\block'
$texturesDirectory = Join-Path $projectRoot 'src\main\resources\assets\crownscoins\textures\block'
$backupDirectory = Join-Path $projectRoot 'build\forja-moedas-model-backup'

if (!(Test-Path -LiteralPath $bbmodelPath)) { throw "Modelo Blockbench ausente: $bbmodelPath" }
if (!(Test-Path -LiteralPath $texturePath)) { throw "Textura da forja ausente: $texturePath" }

function New-Vector {
    param([double]$X, [double]$Y, [double]$Z)
    return [pscustomobject]@{ X = $X; Y = $Y; Z = $Z }
}

function Convert-ToVector {
    param($Values)
    return New-Vector -X ([double]$Values[0]) -Y ([double]$Values[1]) -Z ([double]$Values[2])
}

function Test-HasRotation {
    param($Rotation)
    if ($null -eq $Rotation) { return $false }
    return ([math]::Abs([double]$Rotation[0]) -gt 0.00001) -or
           ([math]::Abs([double]$Rotation[1]) -gt 0.00001) -or
           ([math]::Abs([double]$Rotation[2]) -gt 0.00001)
}

function Rotate-Vector {
    param(
        $Point,
        $Origin,
        $Rotation
    )

    if (!(Test-HasRotation $Rotation)) { return New-Vector $Point.X $Point.Y $Point.Z }

    $x = [double]$Point.X - [double]$Origin.X
    $y = [double]$Point.Y - [double]$Origin.Y
    $z = [double]$Point.Z - [double]$Origin.Z

    $rx = [double]$Rotation[0] * [math]::PI / 180.0
    $ry = [double]$Rotation[1] * [math]::PI / 180.0
    $rz = [double]$Rotation[2] * [math]::PI / 180.0

    # Blockbench rotations are applied around the element/group origin.
    $cos = [math]::Cos($rx); $sin = [math]::Sin($rx)
    $nextY = $y * $cos - $z * $sin
    $nextZ = $y * $sin + $z * $cos
    $y = $nextY; $z = $nextZ

    $cos = [math]::Cos($ry); $sin = [math]::Sin($ry)
    $nextX = $x * $cos + $z * $sin
    $nextZ = -$x * $sin + $z * $cos
    $x = $nextX; $z = $nextZ

    $cos = [math]::Cos($rz); $sin = [math]::Sin($rz)
    $nextX = $x * $cos - $y * $sin
    $nextY = $x * $sin + $y * $cos
    $x = $nextX; $y = $nextY

    return New-Vector ($x + [double]$Origin.X) ($y + [double]$Origin.Y) ($z + [double]$Origin.Z)
}

function Get-TransformedCorner {
    param(
        $Point,
        $Element,
        [System.Collections.Generic.List[object]]$Groups
    )

    $result = New-Vector $Point.X $Point.Y $Point.Z
    if ((Test-HasRotation $Element.rotation) -and $null -ne $Element.origin) {
        $result = Rotate-Vector -Point $result -Origin (Convert-ToVector $Element.origin) -Rotation $Element.rotation
    }

    for ($i = $Groups.Count - 1; $i -ge 0; $i--) {
        $group = $Groups[$i]
        if ((Test-HasRotation $group.rotation) -and $null -ne $group.origin) {
            $result = Rotate-Vector -Point $result -Origin (Convert-ToVector $group.origin) -Rotation $group.rotation
        }
    }
    return $result
}

function Get-Faces {
    param($Element)
    $faces = [ordered]@{}
    foreach ($faceProperty in $Element.faces.PSObject.Properties) {
        $face = $faceProperty.Value
        if ($null -eq $face -or $face.enabled -eq $false -or $null -eq $face.uv) { continue }
        $uv = [System.Collections.Generic.List[double]]::new()
        foreach ($value in @($face.uv)) {
            [void]$uv.Add([math]::Round(([double]$value / 16.0), 5))
        }
        $faces[$faceProperty.Name] = [ordered]@{
            uv = [double[]]$uv.ToArray()
            texture = '#all'
        }
    }
    return $faces
}

function Round-Coordinate {
    param([double]$Value)
    return [math]::Round($Value, 4)
}

$bbmodel = Get-Content -LiteralPath $bbmodelPath -Raw | ConvertFrom-Json
$elementsById = @{}
foreach ($element in @($bbmodel.elements)) { $elementsById[[string]$element.uuid] = $element }
$groupsById = @{}
foreach ($group in @($bbmodel.groups)) { $groupsById[[string]$group.uuid] = $group }

$records = [System.Collections.Generic.List[object]]::new()
function Visit-OutlinerNode {
    param(
        $Node,
        [System.Collections.Generic.List[object]]$Ancestors
    )

    if ($Node -is [string]) {
        if ($elementsById.ContainsKey([string]$Node)) {
            [void]$records.Add([pscustomobject]@{
                Element = $elementsById[[string]$Node]
                Groups = $Ancestors
            })
        }
        return
    }

    if ($null -eq $Node -or $null -eq $Node.uuid) { return }
    $nextAncestors = [System.Collections.Generic.List[object]]::new()
    foreach ($ancestor in $Ancestors) { [void]$nextAncestors.Add($ancestor) }
    $nodeId = [string]$Node.uuid
    if ($groupsById.ContainsKey($nodeId)) { [void]$nextAncestors.Add($groupsById[$nodeId]) }

    foreach ($child in @($Node.children)) {
        Visit-OutlinerNode -Node $child -Ancestors $nextAncestors
    }
}

foreach ($rootNode in @($bbmodel.outliner)) {
    Visit-OutlinerNode -Node $rootNode -Ancestors ([System.Collections.Generic.List[object]]::new())
}

if ($records.Count -ne $bbmodel.elements.Count) {
    throw "A hierarquia do Blockbench encontrou $($records.Count) de $($bbmodel.elements.Count) cubos. Importação interrompida para não perder peças."
}

$pressElements = [System.Collections.Generic.List[object]]::new()
$furnaceElements = [System.Collections.Generic.List[object]]::new()
$elementNumber = 0
foreach ($record in $records) {
    $element = $record.Element
    $from = Convert-ToVector $element.from
    $to = Convert-ToVector $element.to
    $corners = @(
        (New-Vector $from.X $from.Y $from.Z), (New-Vector $from.X $from.Y $to.Z),
        (New-Vector $from.X $to.Y $from.Z),   (New-Vector $from.X $to.Y $to.Z),
        (New-Vector $to.X $from.Y $from.Z),   (New-Vector $to.X $from.Y $to.Z),
        (New-Vector $to.X $to.Y $from.Z),     (New-Vector $to.X $to.Y $to.Z)
    )
    $points = foreach ($corner in $corners) {
        Get-TransformedCorner -Point $corner -Element $element -Groups $record.Groups
    }

    # The Blockbench table uses X -16..16 and Z -8..8.  Minecraft models use
    # 0..32 over the two horizontal halves and 0..16 in depth.
    $minimumX = (($points | ForEach-Object { $_.X } | Measure-Object -Minimum).Minimum + 16.0)
    $maximumX = (($points | ForEach-Object { $_.X } | Measure-Object -Maximum).Maximum + 16.0)
    $minimumY = (($points | ForEach-Object { $_.Y } | Measure-Object -Minimum).Minimum)
    $maximumY = (($points | ForEach-Object { $_.Y } | Measure-Object -Maximum).Maximum)
    $minimumZ = (($points | ForEach-Object { $_.Z } | Measure-Object -Minimum).Minimum + 8.0)
    $maximumZ = (($points | ForEach-Object { $_.Z } | Measure-Object -Maximum).Maximum + 8.0)

    # Pieces spanning both blocks (the continuous base and worktop) are emitted
    # once with the press half. Keeping their original UV prevents seams or
    # duplicated geometry at the join.
    $crossesJoin = $minimumX -lt 16.0 -and $maximumX -gt 16.0
    $centerX = ($minimumX + $maximumX) / 2.0
    $isPressHalf = $crossesJoin -or $centerX -lt 16.0
    $localMinimumX = if ($isPressHalf) { $minimumX } else { $minimumX - 16.0 }
    $localMaximumX = if ($isPressHalf) { $maximumX } else { $maximumX - 16.0 }

    $elementNumber++
    $entry = [ordered]@{
        name = ('forja_{0:D3}_{1}' -f $elementNumber, (($element.name -replace '[^A-Za-z0-9_ -]', '') -replace '\s+', '_'))
        from = [double[]]@((Round-Coordinate $localMinimumX), (Round-Coordinate $minimumY), (Round-Coordinate $minimumZ))
        to = [double[]]@((Round-Coordinate $localMaximumX), (Round-Coordinate $maximumY), (Round-Coordinate $maximumZ))
        faces = (Get-Faces $element)
    }

    if ($isPressHalf) { [void]$pressElements.Add($entry) }
    else { [void]$furnaceElements.Add($entry) }
}

if ($pressElements.Count -eq 0 -or $furnaceElements.Count -eq 0) {
    throw 'A conversão não separou corretamente a prensa e a fornalha.'
}

New-Item -ItemType Directory -Path $backupDirectory -Force | Out-Null
foreach ($name in @('mint_house_royal_left.json', 'mint_house_furnace_left.json')) {
    $existing = Join-Path $modelsDirectory $name
    if (Test-Path -LiteralPath $existing) {
        Copy-Item -LiteralPath $existing -Destination (Join-Path $backupDirectory $name) -Force
    }
}

$textureId = 'crownscoins:block/forja_moedas'
function Write-BlockModel {
    param([string]$Path, [System.Collections.Generic.List[object]]$Elements)
    $model = [ordered]@{
        credit = 'Imported from the supplied forja_moedas Blockbench model.'
        textures = [ordered]@{
            particle = $textureId
            all = $textureId
        }
        elements = @($Elements.ToArray())
    }
    $json = $model | ConvertTo-Json -Depth 12
    $utf8 = New-Object System.Text.UTF8Encoding($false)
    [System.IO.File]::WriteAllText($Path, $json, $utf8)
}

Copy-Item -LiteralPath $texturePath -Destination (Join-Path $texturesDirectory 'forja_moedas.png') -Force
Write-BlockModel -Path (Join-Path $modelsDirectory 'mint_house_royal_left.json') -Elements $pressElements
Write-BlockModel -Path (Join-Path $modelsDirectory 'mint_house_furnace_left.json') -Elements $furnaceElements

Write-Host "Forja importada: $($pressElements.Count) cubos na prensa e $($furnaceElements.Count) cubos na fornalha."
