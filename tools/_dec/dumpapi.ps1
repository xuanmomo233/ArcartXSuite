$jp  = 'C:\Program Files\Java\jdk-21\bin\javap.exe'
$jar = 'd:\IDEA\project\ArcartXSuite\libs\Chemdah-1.1.33-FREE.jar'
$out = 'd:\IDEA\project\ArcartXSuite\tools\_dec\api.txt'
$classes = @(
 'ink.ptms.chemdah.core.PlayerProfile',
 'ink.ptms.chemdah.core.DataContainer',
 'ink.ptms.chemdah.core.SimpleDataContainer',
 'ink.ptms.chemdah.core.DataContainerEventFactory',
 'ink.ptms.chemdah.core.DataContainerEventFactory$Companion',
 'ink.ptms.chemdah.core.Data',
 'ink.ptms.chemdah.core.database.ChangeTracker',
 'ink.ptms.chemdah.core.quest.Quest',
 'ink.ptms.chemdah.core.quest.Template',
 'ink.ptms.chemdah.core.database.UserIndex',
 'ink.ptms.chemdah.core.database.UserIndex$Companion',
 'ink.ptms.chemdah.core.database.QuestTable$SQL',
 'ink.ptms.chemdah.api.ChemdahAPI',
 'ink.ptms.chemdah.Chemdah'
)
if (Test-Path $out) { Remove-Item $out }
foreach ($c in $classes) {
  Add-Content $out ("###### " + $c)
  $r = & $jp -public -classpath $jar $c 2>&1
  Add-Content $out $r
}
'done'
