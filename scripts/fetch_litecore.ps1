param(
    [Parameter(Mandatory=$true)][string]$NexusRepo,
    [Parameter(Mandatory=$true)][string]$Edition,
    [switch]$DebugLib
)

$suffix = ""
if($DebugLib) {
    $suffix = "-debug"
}

Push-Location $PSScriptRoot
$Sha = & "./litecore_sha.ps1" $Edition
Pop-Location

$OutputDir="$PSScriptRoot/../lite-core/windows/x86_64"
New-Item -Type directory -ErrorAction Ignore $OutputDir
Push-Location $OutputDir 

$platform = "windows-win64"
$ZipUrl = "$NexusRepo/couchbase-litecore-$platform/$Sha/couchbase-litecore-$platform-$Sha$suffix.zip"
$ZipFile = "litecore-$platform$suffix.zip"
Write-Host "Fetching for $Sha..."
try {      
  Write-Host $ZipUrl
  Invoke-WebRequest $ZipUrl -OutFile $ZipFile
} catch [System.Net.WebException] {
    Pop-Location
    if($_.Exception.Status -eq [System.Net.WebExceptionStatus]::ProtocolError) {
        $res = $_.Exception.Response.StatusCode
        if($res -eq 404) {
            Write-Host "No LiteCore available for $Sha!"
            exit 1
        }
    }
    throw
}

if(Test-Path "$ZipFile") {
  & 7z e -y $ZipFile
  rm $ZipFile
}

Pop-Location
