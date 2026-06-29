$ErrorActionPreference = 'Continue'
$jp  = 'C:\Program Files\Java\jdk-21\bin\javap.exe'
$jar = 'd:\IDEA\project\ArcartXSuite\libs\Chemdah-1.1.33-FREE-patched.jar'
$out = 'd:\IDEA\project\ArcartXSuite\tools\_dec\jvp_refs.txt'
Add-Type -A System.IO.Compression.FileSystem
$z = [IO.Compression.ZipFile]::OpenRead($jar)
$names = $z.Entries | %{ $_.FullName } | Select-String -SimpleMatch 'ink/ptms/chemdah/core/database/DatabaseSQL' | %{ $_.Line -replace '\.class$','' -replace '/','.' }
$z.Dispose()
if (Test-Path $out) { Remove-Item $out }
foreach ($n in $names) {
  $refs = & $jp -v -p -classpath $jar $n 2>&1 | Select-String -Pattern 'taboolib/module/database|kotlin1822' | Select-String -Pattern '= Methodref|= InterfaceMethodref|= Fieldref' | %{ $_.Line.Trim() }
  if ($refs) {
    Add-Content -Path $out -Value ("###### " + $n)
    Add-Content -Path $out -Value ($refs | Sort-Object -Unique)
  }
}
'done'
