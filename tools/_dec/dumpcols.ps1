$ErrorActionPreference = 'Continue'
$jp  = 'C:\Program Files\Java\jdk-21\bin\javap.exe'
$jar = 'd:\IDEA\project\ArcartXSuite\libs\Chemdah-1.1.8.jar'
$out = 'd:\IDEA\project\ArcartXSuite\tools\_dec\jvp_cols.txt'
$names = @(
  'DatabaseSQL$tableUser$1','DatabaseSQL$tableUser$1$1','DatabaseSQL$tableUser$1$2','DatabaseSQL$tableUser$1$2$1','DatabaseSQL$tableUser$1$3','DatabaseSQL$tableUser$1$3$1','DatabaseSQL$tableUser$1$4',
  'DatabaseSQL$tableUserData$1','DatabaseSQL$tableUserData$1$1','DatabaseSQL$tableUserData$1$2','DatabaseSQL$tableUserData$1$2$1','DatabaseSQL$tableUserData$1$3','DatabaseSQL$tableUserData$1$3$1','DatabaseSQL$tableUserData$1$4','DatabaseSQL$tableUserData$1$5',
  'DatabaseSQL$tableVariables$1','DatabaseSQL$tableVariables$1$1','DatabaseSQL$tableVariables$1$2','DatabaseSQL$tableVariables$1$2$1','DatabaseSQL$tableVariables$1$3','DatabaseSQL$tableVariables$1$4'
)
if (Test-Path $out) { Remove-Item $out }
foreach ($n in $names) {
  Add-Content -Path $out -Value ("###### " + $n)
  $full = 'ink.ptms.chemdah.core.database.' + $n
  $res = & $jp -c -p -classpath $jar $full 2>&1 | Select-String -NotMatch '^Constant pool:|^\s+#\d+ =|^\{|^\}|minor version|major version|flags:|this_class|super_class|interfaces:|Compiled from' | %{ $_.Line }
  Add-Content -Path $out -Value $res
}
'done'
