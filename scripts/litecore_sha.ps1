param(
    [string]$Edition,
    [string]$OutPath,
    [switch]$Verbose
)

if($Edition -eq "") {
    $Edition = "CE"
}

Write-Host "LiteCore Edition : $Edition"

if($Edition -eq "EE") {
    Push-Location $PSScriptRoot\..\..\couchbase-lite-core-EE
    $FirstSha = (& git rev-parse HEAD).Substring(0, 40)
    if($Verbose) {
        Write-Host "EE SHA is: '$FirstSha'"
    }
    Pop-Location
}

Push-Location $PSScriptRoot\..\..\couchbase-lite-core
$SecondSha = (& git rev-parse HEAD).Substring(0, 40)
if($Verbose) {
    Write-Host "Base SHA is: '$SecondSha'"
}
Pop-Location

if($Edition -eq "EE") {
    $sha1 = New-Object System.Security.Cryptography.SHA1CryptoServiceProvider
    $amalgamation = $SecondSha + $FirstSha
    $finalSha = [System.BitConverter]::ToString($sha1.ComputeHash([System.Text.Encoding]::ASCII.GetBytes($amalgamation)))
} else {
    $finalSha = $SecondSha
}

Write-Output $finalSha.ToLowerInvariant().Replace("-", "")
if($OutPath) {
    Write-Output $finalSha.ToLowerInvariant().Replace("-", "") | Out-File -FilePath $OutPath -Force -NoNewline -Encoding ASCII
}