param(
    [switch]$OpenFolder
)

$ErrorActionPreference = "Stop"
$repoRoot = Split-Path -Parent $PSScriptRoot
Set-Location $repoRoot

Write-Host "AU TV local test"
Write-Host "Repository: $repoRoot"
Write-Host ""

function Download-File {
    param(
        [Parameter(Mandatory=$true)][string]$Url,
        [Parameter(Mandatory=$true)][string]$OutFile
    )
    Write-Host "Fetching $OutFile..."
    Invoke-WebRequest -Uri $Url -OutFile $OutFile -UseBasicParsing -TimeoutSec 60
}

try {
    Download-File -Url "https://raw.githubusercontent.com/BuddyChewChew/app-m3u-generator/main/playlists/plex_au.m3u" -OutFile "base_upstream.m3u"
    Download-File -Url "https://www.apsattv.com/ssungaus.m3u" -OutFile "secondary_upstream.m3u"

    Write-Host "Building production and test playlists..."
    python scripts/merge.py
    if ($LASTEXITCODE -ne 0) { throw "merge.py failed with exit code $LASTEXITCODE" }

    $prodCount = (Select-String -Path "playlist.m3u" -Pattern "^#EXTINF:" -AllMatches).Count
    $testCount = (Select-String -Path "test-playlist.m3u" -Pattern "^#EXTINF:" -AllMatches).Count

    Write-Host ""
    Write-Host "Local build complete."
    Write-Host "Production channels: $prodCount"
    Write-Host "Test channels:       $testCount"
    Write-Host ""
    Write-Host "Candidate channels:"
    Get-Content "candidate_channels.txt" | Where-Object { $_.Trim() -and -not $_.Trim().StartsWith("#") } | ForEach-Object { Write-Host " - $_" }
    Write-Host ""
    Write-Host "Test files:"
    Write-Host " - $repoRoot\playlist.m3u"
    Write-Host " - $repoRoot\test-playlist.m3u"
    Write-Host ""
    Write-Host "Open test-playlist.m3u in VLC on this PC before publishing."

    if ($OpenFolder) { Start-Process explorer.exe $repoRoot }
}
finally {
    Remove-Item "base_upstream.m3u" -ErrorAction SilentlyContinue
    Remove-Item "secondary_upstream.m3u" -ErrorAction SilentlyContinue
}