$ErrorActionPreference = "Continue"
$src = "D:\纺专\横向项目\科技处\审批文件"
$dst = "D:\纺专\横向项目\scitech\templates-src"
foreach ($f in (Get-ChildItem -LiteralPath $src -Filter *.doc)) {
  $out = Join-Path $dst ($f.BaseName + ".docx")
  if (Test-Path -LiteralPath $out) { Write-Host ("SKIP " + $f.Name); continue }
  $word = $null
  try {
    $word = New-Object -ComObject Word.Application
    $word.Visible = $false
    $word.DisplayAlerts = 0
    $doc = $word.Documents.Open($f.FullName, $false, $true, $false)
    $doc.SaveAs2($out, 16)
    $doc.Close($false)
    Write-Host ("OK " + $f.Name)
  } catch {
    Write-Host ("FAIL " + $f.Name + " : " + $_.Exception.Message)
  } finally {
    if ($word) { try { $word.Quit() } catch {} }
  }
}
