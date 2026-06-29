$jp  = 'C:\Program Files\Java\jdk-21\bin\javap.exe'
$jar = 'd:\IDEA\project\ArcartXSuite\libs\Chemdah-1.1.33-FREE-patched.jar'
$out = 'd:\IDEA\project\ArcartXSuite\tools\_dec\mappers.txt'
$cls = @(
 'ink.ptms.chemdah.core.database.DatabaseSQL$getUserId$userId$2',
 'ink.ptms.chemdah.core.database.DatabaseSQL$getQuestId$questId$2',
 'ink.ptms.chemdah.core.database.DatabaseSQL$init$2'
)
if (Test-Path $out) { Remove-Item $out }
foreach ($c in $cls) {
  Add-Content $out ("###### " + $c)
  $r = & $jp -c -p -classpath $jar $c 2>&1 | Select-String -Pattern 'invoke|getString|getLong|getObject|getInt|ldc|TuplesKt|Coerce|String '
  Add-Content $out $r
}
'done'
