param([Parameter(Mandatory = $true)][string]$ArtifactPath)
$ErrorActionPreference = 'Stop'
Add-Type -AssemblyName System.IO.Compression.FileSystem
$resolvedArtifact = (Resolve-Path -LiteralPath $ArtifactPath).Path
$archive = [System.IO.Compression.ZipFile]::OpenRead($resolvedArtifact)
$checked = 0
$failures = [System.Collections.Generic.List[string]]::new()
try {
    foreach ($entry in $archive.Entries) {
        if ($entry.FullName -notmatch '^(base/)?lib/(arm64-v8a|x86_64)/[^/]+\.so$') { continue }
        $memory = [System.IO.MemoryStream]::new()
        $stream = $entry.Open()
        try { $stream.CopyTo($memory); $bytes = $memory.ToArray() }
        finally { $stream.Dispose(); $memory.Dispose() }
        if ($bytes.Length -lt 64 -or $bytes[0] -ne 127 -or $bytes[1] -ne 69 -or $bytes[2] -ne 76 -or $bytes[3] -ne 70 -or $bytes[4] -ne 2 -or $bytes[5] -ne 1) {
            $failures.Add("Unsupported ELF format: $($entry.FullName)"); continue
        }
        $programOffset = [BitConverter]::ToUInt64($bytes, 32)
        $entrySize = [BitConverter]::ToUInt16($bytes, 54)
        $entryCount = [BitConverter]::ToUInt16($bytes, 56)
        $loadSegments = 0
        for ($index = 0; $index -lt $entryCount; $index++) {
            $offset = [int]($programOffset + $index * $entrySize)
            if ($offset -lt 0 -or $offset + 56 -gt $bytes.Length) { throw "Invalid ELF program header: $($entry.FullName)" }
            if ([BitConverter]::ToUInt32($bytes, $offset) -ne 1) { continue }
            $loadSegments++
            $fileOffset = [BitConverter]::ToUInt64($bytes, $offset + 8)
            $virtualAddress = [BitConverter]::ToUInt64($bytes, $offset + 16)
            $alignment = [BitConverter]::ToUInt64($bytes, $offset + 48)
            if ($alignment -lt 16384 -or ($fileOffset % 16384) -ne ($virtualAddress % 16384)) {
                $failures.Add("16 KB ELF alignment failed: $($entry.FullName), segment $index, alignment $alignment")
            }
        }
        if ($loadSegments -eq 0) { $failures.Add("No ELF load segments: $($entry.FullName)") }
        $checked++
    }
}
finally { $archive.Dispose() }
if ($checked -eq 0) { throw 'No Android 64-bit native libraries found; verify the selected APK/AAB.' }
if ($failures.Count -gt 0) { throw ($failures -join "`n") }
Write-Output "PASS: $checked Android 64-bit native libraries have compatible 16 KB ELF load alignment."
Write-Output 'This checks ELF alignment only. APK zip alignment, bundle validation and 16 KB device runtime testing are separate checks.'
