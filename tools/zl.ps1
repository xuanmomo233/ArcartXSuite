param([string]$jar,[string]$pat)
Add-Type -AssemblyName System.IO.Compression.FileSystem
$z=[IO.Compression.ZipFile]::OpenRead($jar)
$z.Entries | Where-Object { $_.FullName -match $pat } | ForEach-Object { $_.FullName }
$z.Dispose()
