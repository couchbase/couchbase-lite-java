param(
    [Parameter(Mandatory=$true)][string]$NexusRepo,
    [Parameter(Mandatory=$true)][string]$Edition,
    [switch]$DebugLib
)

Push-Location $PSScriptRoot
$Sha = & "./litecore_sha.ps1" $Edition
Pop-Location

$OutputDir="$PSScriptRoot/../lite-core/windows/x86_64"
New-Item -Type directory -ErrorAction Ignore $OutputDir
Push-Location $OutputDir 

$suffix = ""
if($DebugLib) {
    $suffix = "-debug"
}

$platform = "windows-win64"
Write-Host "Fetching for $Sha..."
try {      
  Write-Host $NexusRepo/couchbase-litecore-$platform/$Sha/couchbase-litecore-$platform-$Sha$suffix.zip
  Invoke-WebRequest $NexusRepo/couchbase-litecore-$platform/$Sha/couchbase-litecore-$platform-$Sha$suffix.zip -OutFile litecore-$platform$suffix.zip
} catch [System.Net.WebException] {
    Pop-Location
    if($_.Exception.Status -eq [System.Net.WebExceptionStatus]::ProtocolError) {
        $res = $_.Exception.Response.StatusCode
        if($res -eq 404) {
            Write-Host "LiteCore for $Sha is not ready yet!"
            exit 1
        }
    }
    throw
}

if(Test-Path "litecore-$platform$suffix.zip") {
  & 7z e -y litecore-$platform$suffix$suffix.zip
  rm litecore-$platform$suffix.zip
}

Pop-Location