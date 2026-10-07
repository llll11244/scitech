$ErrorActionPreference = "Continue"
$src = "D:\纺专\横向项目\科技处\审批文件"
$dst = "D:\纺专\横向项目\scitech\templates-src"
New-Item -ItemType Directory -Force -Path $dst | Out-Null
$word = New-Object -ComObject Word.Application
$word.Visible = $false
$word.DisplayAlerts = 0
foreach ($f in (Get-ChildItem -LiteralPath $src -Filter *.doc)) {
  try {
    $doc = $word.Documents.Open($f.FullName, $false, $true)
    $out = Join-Path $dst ($f.BaseName + ".docx")
    $doc.SaveAs2($out, 16)
    $doc.Close($false)
    Write-Host ("OK " + $f.Name)
  } catch {
    Write-Host ("FAIL " + $f.Name + " : " + $_.Exception.Message)
  }
}
try { $word.Quit() } catch { Write-Host "Quit warning ignored" }
