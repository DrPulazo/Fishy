# Sync Fishy locale strings from base + JSON overlays.
$ErrorActionPreference = 'Stop'
[Console]::OutputEncoding = [System.Text.Encoding]::UTF8

$res = 'D:\Fishy31\app\src\main\res'
$overlays = 'D:\Fishy31\tools\overlays'
$basePath = Join-Path $res 'values\strings.xml'
$baseText = [System.IO.File]::ReadAllText($basePath, [System.Text.Encoding]::UTF8)

function Get-StringMap([string]$xmlText) {
    $map = @{}
    $rx = [regex]'<string\s+name="([^"]+)"\s*>(.*?)</string>'
    foreach ($m in $rx.Matches($xmlText)) {
        $map[$m.Groups[1].Value] = $m.Groups[2].Value
    }
    return $map
}

function Get-KeyOrder([string]$xmlText) {
    $list = New-Object System.Collections.Generic.List[string]
    $rx = [regex]'<string\s+name="([^"]+)"'
    foreach ($m in $rx.Matches($xmlText)) { [void]$list.Add($m.Groups[1].Value) }
    return $list
}

function Read-JsonMap([string]$path) {
    if (-not (Test-Path $path)) { return @{} }
    $raw = [System.IO.File]::ReadAllText($path, [System.Text.Encoding]::UTF8)
    $obj = $raw | ConvertFrom-Json
    $map = @{}
    foreach ($p in $obj.PSObject.Properties) { $map[$p.Name] = [string]$p.Value }
    return $map
}

function Write-Strings([string]$path, $order, $values) {
    $sb = New-Object System.Text.StringBuilder
    [void]$sb.AppendLine('<?xml version="1.0" encoding="utf-8"?>')
    [void]$sb.AppendLine('<resources>')
    foreach ($k in $order) {
        if (-not $values.ContainsKey($k)) { throw "Missing key $k for $path" }
        [void]$sb.AppendLine(('    <string name="{0}">{1}</string>' -f $k, $values[$k]))
    }
    [void]$sb.AppendLine('</resources>')
    $utf8NoBom = New-Object System.Text.UTF8Encoding $false
    [System.IO.File]::WriteAllText($path, $sb.ToString(), $utf8NoBom)
}

$order = Get-KeyOrder $baseText
$base = Get-StringMap $baseText
$lang = Read-JsonMap (Join-Path $overlays 'common_lang.json')
if ($lang.Count -eq 0) {
    throw 'common_lang.json missing'
}

function Merge-Locale([string]$folder, [string]$code, [bool]$fullOverlay) {
    $path = Join-Path $res "$folder\strings.xml"
    $existing = @{}
    if (Test-Path $path) {
        $existing = Get-StringMap ([System.IO.File]::ReadAllText($path, [System.Text.Encoding]::UTF8))
    }
    $overlay = Read-JsonMap (Join-Path $overlays "$code.json")

    $values = @{}
    foreach ($k in $order) { $values[$k] = $base[$k] }
    if (-not $fullOverlay) {
        foreach ($k in $existing.Keys) { $values[$k] = $existing[$k] }
    }
    foreach ($k in $overlay.Keys) { $values[$k] = $overlay[$k] }
    foreach ($k in $lang.Keys) { $values[$k] = $lang[$k] }

    Write-Strings $path $order $values
    Write-Host "wrote $folder ($($order.Count) keys)"
}

Merge-Locale 'values-en' 'en' $false
Merge-Locale 'values-ru' 'ru' $false
Merge-Locale 'values-es' 'es' $true
Merge-Locale 'values-zh' 'zh' $false
Merge-Locale 'values-ja' 'ja' $false
Merge-Locale 'values-ko' 'ko' $false
Write-Host 'done'
