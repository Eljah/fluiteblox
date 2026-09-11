param(
    [string] $AndroidHome = $env:ANDROID_HOME,
    [string] $AndroidPlatform = "36",
    [string] $BuildTools = "36.1.0",
    [string] $KeyAlias = "release",
    [string] $KeystorePassword = "Tatarstan1920",
    [string] $KeyPassword = "Tatarstan1920"
)

$ErrorActionPreference = "Stop"

$RepoRoot = (Resolve-Path (Join-Path $PSScriptRoot "..")).Path
if ([string]::IsNullOrWhiteSpace($AndroidHome)) {
    throw "ANDROID_HOME is not set. Pass -AndroidHome or set the environment variable."
}

$KeystorePath = Join-Path $RepoRoot "keystore\release-key.jks"
$KeystoreB64 = Join-Path $RepoRoot "keystore\release-key.jks.b64"
if (!(Test-Path -LiteralPath $KeystorePath) -and (Test-Path -LiteralPath $KeystoreB64)) {
    [IO.File]::WriteAllBytes($KeystorePath, [Convert]::FromBase64String((Get-Content -Raw -LiteralPath $KeystoreB64)))
}
if (!(Test-Path -LiteralPath $KeystorePath)) {
    throw "Release keystore not found: $KeystorePath"
}

$Aapt2 = Join-Path $AndroidHome "build-tools\$BuildTools\aapt2.exe"
if (!(Test-Path -LiteralPath $Aapt2)) {
    throw "aapt2 not found at $Aapt2"
}

$AndroidJar = Join-Path $AndroidHome "platforms\android-$AndroidPlatform\android.jar"
if (!(Test-Path -LiteralPath $AndroidJar)) {
    throw "Android platform jar not found at $AndroidJar"
}

$BundletoolJar = Join-Path $RepoRoot "tools\bundletool.jar"
if (!(Test-Path -LiteralPath $BundletoolJar)) {
    New-Item -ItemType Directory -Path (Split-Path -Parent $BundletoolJar) -Force | Out-Null
    Invoke-WebRequest `
        -Uri "https://github.com/google/bundletool/releases/download/1.16.0/bundletool-all-1.16.0.jar" `
        -OutFile $BundletoolJar
}

$WorkDir = Join-Path ([IO.Path]::GetTempPath()) ("fluiteblox-aab-" + [Guid]::NewGuid().ToString("N"))
$CompiledResDir = Join-Path $WorkDir "compiled-res"
$StableIdsForAapt = Join-Path $WorkDir "stable-ids.txt"
$BaseApk = Join-Path $WorkDir "base.apk"
$ApkDir = Join-Path $WorkDir "apk"
$ModuleDir = Join-Path $WorkDir "module"
$ModuleZip = Join-Path $WorkDir "base.zip"
$AabPath = Join-Path $RepoRoot "target\app-release.aab"
$StableIds = Join-Path $RepoRoot "target\R.txt"

try {
    New-Item -ItemType Directory -Path $CompiledResDir, $ApkDir, $ModuleDir -Force | Out-Null
    if (!(Test-Path -LiteralPath $StableIds)) {
        throw "Stable resource IDs not found: $StableIds. Run mvn package or mvn test first to generate target\R.txt."
    }
    Get-Content -LiteralPath $StableIds |
        Where-Object { $_ -match '^int\s+\S+\s+\S+\s+0x[0-9a-fA-F]+$' } |
        ForEach-Object {
            $Parts = $_ -split '\s+'
            "tatar.eljah.fluitblox:$($Parts[1])/$($Parts[2]) = $($Parts[3])"
        } |
        Set-Content -LiteralPath $StableIdsForAapt -Encoding ASCII

    $CompiledArgs = @()
    $ResDirs = @(Join-Path $RepoRoot "src\main\res")
    $UnpackedLibs = Join-Path $RepoRoot "target\unpacked-libs"
    if (Test-Path -LiteralPath $UnpackedLibs) {
        $ResDirs += Get-ChildItem -Path $UnpackedLibs -Recurse -Directory -Filter res | Select-Object -ExpandProperty FullName
    }

    $Index = 0
    foreach ($ResDir in $ResDirs) {
        if (Test-Path -LiteralPath $ResDir) {
            $CompiledZip = Join-Path $CompiledResDir "res-$Index.zip"
            & $Aapt2 compile --dir $ResDir -o $CompiledZip
            if ($LASTEXITCODE -ne 0) { throw "aapt2 compile failed for $ResDir" }
            $CompiledArgs += @("-R", $CompiledZip)
            $Index++
        }
    }

    & $Aapt2 link --proto-format `
        -I $AndroidJar `
        --manifest (Join-Path $RepoRoot "src\main\AndroidManifest.xml") `
        --auto-add-overlay `
        --stable-ids $StableIdsForAapt `
        -o $BaseApk `
        @CompiledArgs
    if ($LASTEXITCODE -ne 0) { throw "aapt2 link failed" }

    tar -xf $BaseApk -C $ApkDir
    New-Item -ItemType Directory `
        -Path (Join-Path $ModuleDir "manifest"), (Join-Path $ModuleDir "dex"), (Join-Path $ModuleDir "res"), (Join-Path $ModuleDir "lib") `
        -Force | Out-Null
    Copy-Item -LiteralPath (Join-Path $ApkDir "AndroidManifest.xml") -Destination (Join-Path $ModuleDir "manifest\AndroidManifest.xml")
    Copy-Item -LiteralPath (Join-Path $ApkDir "resources.pb") -Destination (Join-Path $ModuleDir "resources.pb")

    $ApkRes = Join-Path $ApkDir "res"
    if (Test-Path -LiteralPath $ApkRes) {
        Copy-Item -Path (Join-Path $ApkRes "*") -Destination (Join-Path $ModuleDir "res") -Recurse
    }

    Get-ChildItem -Path (Join-Path $RepoRoot "target") -Filter "classes*.dex" -File |
        Copy-Item -Destination (Join-Path $ModuleDir "dex")

    if (Test-Path -LiteralPath $UnpackedLibs) {
        Get-ChildItem -Path $UnpackedLibs -Recurse -File -Filter "*.so" |
            Where-Object { $_.FullName -match "\\jni\\([^\\]+)\\[^\\]+\.so$" } |
            ForEach-Object {
                $Abi = [Regex]::Match($_.FullName, "\\jni\\([^\\]+)\\[^\\]+\.so$").Groups[1].Value
                $AbiDir = Join-Path $ModuleDir "lib\$Abi"
                New-Item -ItemType Directory -Path $AbiDir -Force | Out-Null
                Copy-Item -LiteralPath $_.FullName -Destination $AbiDir
            }
    }

    if (Test-Path -LiteralPath $ModuleZip) {
        Remove-Item -LiteralPath $ModuleZip -Force
    }
    & jar cMf $ModuleZip -C $ModuleDir .
    if ($LASTEXITCODE -ne 0) { throw "module zip creation failed" }

    New-Item -ItemType Directory -Path (Join-Path $RepoRoot "target") -Force | Out-Null
    & java -jar $BundletoolJar build-bundle --modules=$ModuleZip --output=$AabPath --overwrite
    if ($LASTEXITCODE -ne 0) { throw "bundletool build-bundle failed" }

    & jarsigner -keystore $KeystorePath -storepass $KeystorePassword -keypass $KeyPassword $AabPath $KeyAlias
    if ($LASTEXITCODE -ne 0) { throw "jarsigner failed" }

    Write-Output $AabPath
} finally {
    if (Test-Path -LiteralPath $WorkDir) {
        Remove-Item -LiteralPath $WorkDir -Recurse -Force
    }
}
