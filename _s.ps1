param([string]$Term)
Get-ChildItem -Path $PWD -Recurse -File -Include *.java,*.kt,*.py,*.pro,*.gradle,*.gradle.kts,*.yml,*.yaml,*.txt -ErrorAction SilentlyContinue |
  Where-Object { $_.FullName -notmatch '\\build\\' -and $_.FullName -notmatch '\\\.git\\' -and $_.FullName -notmatch '\\\.gradle\\' } |
  Select-String -Pattern $Term -SimpleMatch |
  Select-Object -First 60 |
  ForEach-Object { $_.Path.Substring($PWD.Path.Length+1) + ":" + $_.LineNumber + ": " + $_.Line.Trim() }
