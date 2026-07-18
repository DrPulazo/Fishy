$ErrorActionPreference = 'Stop'
$res = 'D:\Fishy31\app\src\main\res'
$rx = [regex]'<string\s+name="([^"]+)"\s*>(.*?)</string>'
# Cyrillic letter range via codepoints to avoid script encoding issues
$cyrillic = [regex]::new('[\u0400-\u04FF]')

function Get-Map($path) {
  $t = [IO.File]::ReadAllText($path, [Text.Encoding]::UTF8)
  $m = @{}
  foreach ($x in $rx.Matches($t)) { $m[$x.Groups[1].Value] = $x.Groups[2].Value }
  return $m
}

$base = Get-Map (Join-Path $res 'values\strings.xml')
Write-Host ("base " + $base.Count)

foreach ($loc in @('en','es','zh','ja','ko')) {
  $path = Join-Path $res "values-$loc\strings.xml"
  $map = Get-Map $path
  Write-Host ("$loc keys=" + $map.Count)
  $leaks = New-Object System.Collections.Generic.List[string]
  foreach ($k in $map.Keys) {
    if ($k -eq 'home_title_ru') { continue }
    if ($k.StartsWith('lang_')) { continue }
    $v = $map[$k]
    if ($cyrillic.IsMatch($v) -and $v -eq $base[$k]) {
      [void]$leaks.Add($k)
    }
  }
  Write-Host ("  russian-fallback leaks: " + $leaks.Count)
  $n = [Math]::Min(50, $leaks.Count)
  for ($i = 0; $i -lt $n; $i++) { Write-Host ("    " + $leaks[$i]) }
}
