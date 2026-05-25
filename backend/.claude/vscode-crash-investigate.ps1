#Requires -Version 5.1
<#
.SYNOPSIS
    VSCode crash extension isolator
.DESCRIPTION
    All suspicious extensions are isolated. This script supports:
    1. Clean old LLDB versions (keep only latest)
    2. Binary-search / one-by-one restore to locate bad extension
    3. Log all actions
    NOTE: We do NOT modify extensions.json manually; VSCode rescans it on launch.
#>

param(
    [Parameter()]
    [ValidateSet("status","clean-lldb","restore-one","restore-group","restore-all","undo-last")]
    [string]$Action = "status",

    [Parameter()]
    [string]$ExtensionName,

    [Parameter()]
    [int]$GroupIndex = 0
)

$ErrorActionPreference = "Stop"
$timestamp = Get-Date -Format "yyyyMMdd-HHmmss"
$logFile = "$env:USERPROFILE\.vscode\extensions\crash-investigate-$timestamp.log"
$srcDir = "$env:USERPROFILE\.vscode\extensions-disabled"
$dstDir = "$env:USERPROFILE\.vscode\extensions"
$stateFile = "$dstDir\.investigate-state.json"

function Write-Log {
    param([string]$msg,[string]$level="INFO")
    $line = "[$(Get-Date -Format 'HH:mm:ss')] [$level] $msg"
    Write-Host $line
    Add-Content -Path $logFile -Value $line -Encoding UTF8 -ErrorAction SilentlyContinue
}

if (-not (Test-Path $dstDir)) { New-Item -ItemType Directory -Path $dstDir -Force | Out-Null }
if (-not (Test-Path $srcDir)) { New-Item -ItemType Directory -Path $srcDir -Force | Out-Null }

function Show-Status {
    Write-Log "========== Current Status =========="
    Write-Log ""
    Write-Log "[DISABLED / Quarantine] ($srcDir):"
    $disabled = @()
    if (Test-Path $srcDir) {
        $dirs = Get-ChildItem $srcDir -Directory | Sort-Object Name
        foreach ($d in $dirs) {
            $disabled += $d.Name
            Write-Log "  - $($d.Name)"
        }
    }
    if ($disabled.Count -eq 0) { Write-Log "  (none)" }
    Write-Log ""

    Write-Log "[Target List] (sorted by risk):"
    $targets = @(
        "anthropic.claude-code-2.1.143-win32-x64",
        "openai.chatgpt-26.513.21555-win32-x64",
        "github.copilot-chat-0.48.1",
        "marscode.marscode-extension-1.6.30",
        "alibaba-cloud.tongyi-lingma-2.5.20",
        "vscjava.migrate-java-to-azure-1.18.0-win32-x64"
    )
    foreach ($t in $targets) {
        $status = if ($disabled -contains $t) { "[DISABLED]" } else { "[ACTIVE]  " }
        Write-Log "  $status $t"
    }
    Write-Log ""

    Write-Log "[LLDB Versions]"
    $lldbDirs = @()
    if (Test-Path $srcDir) { $lldbDirs += Get-ChildItem $srcDir -Directory -Filter "vadimcn.vscode-lldb-*" -ErrorAction SilentlyContinue }
    if (Test-Path $dstDir) { $lldbDirs += Get-ChildItem $dstDir -Directory -Filter "vadimcn.vscode-lldb-*" -ErrorAction SilentlyContinue }
    $lldbDirs = $lldbDirs | Sort-Object Name
    if ($lldbDirs) {
        foreach ($d in $lldbDirs) {
            $loc = if ($d.FullName -like "*$srcDir*") { "quarantine" } else { "active" }
            Write-Log "  - $($d.Name)  [$loc]"
        }
    } else {
        Write-Log "  (none)"
    }
    Write-Log ""

    if (Test-Path $stateFile) {
        $state = Get-Content $stateFile -Raw -Encoding UTF8 | ConvertFrom-Json
        Write-Log "[Last Action]"
        Write-Log "  Action: $($state.lastAction)"
        Write-Log "  Time  : $($state.time)"
        Write-Log "  Exts  : $($state.extensions -join ', ')"
        Write-Log ""
    }

    Write-Log "========== Next Steps =========="
    Write-Log "System is stable (all suspicious extensions isolated)."
    Write-Log "Suggested binary-search order (max 3 rounds):"
    Write-Log ""
    Write-Log "Round 1 - High risk group (AI native x3):"
    Write-Log "  1. anthropic.claude-code-2.1.143-win32-x64"
    Write-Log "  2. openai.chatgpt-26.513.21555-win32-x64"
    Write-Log "  3. github.copilot-chat-0.48.1"
    Write-Log "Run: .\vscode-crash-investigate.ps1 -Action restore-group -GroupIndex 1"
    Write-Log ""
    Write-Log "Round 2 - Medium risk group (AI x2):"
    Write-Log "  4. marscode.marscode-extension-1.6.30"
    Write-Log "  5. alibaba-cloud.tongyi-lingma-2.5.20"
    Write-Log "Run: .\vscode-crash-investigate.ps1 -Action restore-group -GroupIndex 2"
    Write-Log ""
    Write-Log "Round 3 - Low risk group (Java migrate):"
    Write-Log "  6. vscjava.migrate-java-to-azure-1.18.0-win32-x64"
    Write-Log "Run: .\vscode-crash-investigate.ps1 -Action restore-group -GroupIndex 3"
    Write-Log ""
    Write-Log "Or restore one by one:"
    Write-Log "  .\vscode-crash-investigate.ps1 -Action restore-one -ExtensionName 'xxx'"
    Write-Log ""
}

function Invoke-CleanLldb {
    Write-Log "========== Clean LLDB Old Versions =========="
    $keepVersion = "vadimcn.vscode-lldb-1.11.5"
    $allLldb = @()
    if (Test-Path $srcDir) { $allLldb += Get-ChildItem $srcDir -Directory -Filter "vadimcn.vscode-lldb-*" -ErrorAction SilentlyContinue }
    if (Test-Path $dstDir) { $allLldb += Get-ChildItem $dstDir -Directory -Filter "vadimcn.vscode-lldb-*" -ErrorAction SilentlyContinue }

    if ($allLldb.Count -eq 0) {
        Write-Log "No LLDB versions found"
        return
    }

    Write-Log "Found $($allLldb.Count) LLDB version(s):"
    foreach ($d in $allLldb) { Write-Log "  - $($d.FullName)" }
    Write-Log ""
    Write-Log "Keep version: $keepVersion"
    Write-Log ""

    foreach ($d in $allLldb) {
        if ($d.Name -eq $keepVersion) {
            if ($d.FullName -like "*$srcDir*") {
                Write-Log "Move kept version from quarantine to active: $keepVersion"
                $target = Join-Path $dstDir $keepVersion
                if (Test-Path $target) { Remove-Item $target -Recurse -Force }
                Move-Item $d.FullName $dstDir -Force
            } else {
                Write-Log "Kept version already active: $keepVersion"
            }
        } else {
            Write-Log "Delete old version: $($d.Name)"
            Remove-Item $d.FullName -Recurse -Force
        }
    }

    Write-Log ""
    Write-Log "LLDB cleanup done! Remaining:"
    $remain = @()
    if (Test-Path $srcDir) { $remain += Get-ChildItem $srcDir -Directory -Filter "vadimcn.vscode-lldb-*" -ErrorAction SilentlyContinue }
    if (Test-Path $dstDir) { $remain += Get-ChildItem $dstDir -Directory -Filter "vadimcn.vscode-lldb-*" -ErrorAction SilentlyContinue }
    if ($remain.Count -eq 0) { Write-Log "  (none)" } else { foreach ($r in $remain) { Write-Log "  - $($r.FullName)" } }
}

function Invoke-RestoreOne {
    param([string]$ext)
    if (-not $ext) { throw "Please specify -ExtensionName" }
    $srcPath = Join-Path $srcDir $ext
    if (-not (Test-Path $srcPath)) {
        Write-Log "Extension not in quarantine: $ext" "ERROR"
        if (Test-Path (Join-Path $dstDir $ext)) { Write-Log "Extension already active" "WARN" }
        return
    }
    Write-Log "========== Restore Single Extension =========="
    Write-Log "Target: $ext"
    Write-Log ""
    $dstPath = Join-Path $dstDir $ext
    if (Test-Path $dstPath) { Remove-Item $dstPath -Recurse -Force }
    Move-Item $srcPath $dstDir -Force
    $state = @{ lastAction="restore-one"; extensions=@($ext); time=Get-Date -Format "yyyy-MM-dd HH:mm:ss" }
    $state | ConvertTo-Json -Depth 5 | Set-Content $stateFile -Encoding UTF8
    Write-Log ""
    Write-Log "Restored: $ext"
    Write-Log ""
    Write-Log "Please NOW:"
    Write-Log "  1. Fully close VSCode (all windows)"
    Write-Log "  2. Reopen VSCode"
    Write-Log "  3. Watch for 3-5 min for 'Extension Host Terminated'"
    Write-Log ""
    Write-Log "If crash -> culprit is: $ext"
    Write-Log "If stable -> continue with next extension"
    Write-Log ""
    Write-Log "Undo this: .\vscode-crash-investigate.ps1 -Action undo-last"
}

function Invoke-RestoreGroup {
    param([int]$idx)
    $groups = @(
        @("anthropic.claude-code-2.1.143-win32-x64","openai.chatgpt-26.513.21555-win32-x64","github.copilot-chat-0.48.1"),
        @("marscode.marscode-extension-1.6.30","alibaba-cloud.tongyi-lingma-2.5.20"),
        @("vscjava.migrate-java-to-azure-1.18.0-win32-x64")
    )
    if ($idx -lt 1 -or $idx -gt $groups.Count) { throw "GroupIndex must be 1..$($groups.Count)" }
    $group = $groups[$idx-1]
    Write-Log "========== Restore Group $idx =========="
    Write-Log "Restoring $($group.Count) extension(s):"
    foreach ($g in $group) { Write-Log "  - $g" }
    Write-Log ""
    $restored = @()
    foreach ($ext in $group) {
        $srcPath = Join-Path $srcDir $ext
        if (Test-Path $srcPath) {
            $dstPath = Join-Path $dstDir $ext
            if (Test-Path $dstPath) { Remove-Item $dstPath -Recurse -Force }
            Move-Item $srcPath $dstDir -Force
            $restored += $ext
            Write-Log "Restored: $ext"
        } else {
            Write-Log "Not found in quarantine: $ext (maybe already active)" "WARN"
        }
    }
    $state = @{ lastAction="restore-group-$idx"; extensions=$restored; time=Get-Date -Format "yyyy-MM-dd HH:mm:ss" }
    $state | ConvertTo-Json -Depth 5 | Set-Content $stateFile -Encoding UTF8
    Write-Log ""
    Write-Log "Total restored this round: $($restored.Count)"
    Write-Log ""
    Write-Log "Please NOW:"
    Write-Log "  1. Fully close VSCode (all windows)"
    Write-Log "  2. Reopen VSCode"
    Write-Log "  3. Watch for 3-5 min"
    Write-Log ""
    Write-Log "If crash -> culprit is among this round's extensions"
    Write-Log "If stable -> next round: .\vscode-crash-investigate.ps1 -Action restore-group -GroupIndex $($idx+1)"
    Write-Log ""
    Write-Log "Undo this: .\vscode-crash-investigate.ps1 -Action undo-last"
}

function Invoke-UndoLast {
    if (-not (Test-Path $stateFile)) { Write-Log "No previous action to undo" "ERROR"; return }
    $state = Get-Content $stateFile -Raw -Encoding UTF8 | ConvertFrom-Json
    Write-Log "========== Undo Last Action =========="
    Write-Log "Last action: $($state.lastAction)"
    Write-Log "Extensions: $($state.extensions -join ', ')"
    Write-Log ""
    foreach ($ext in $state.extensions) {
        $dstPath = Join-Path $dstDir $ext
        if (Test-Path $dstPath) {
            $srcPath = Join-Path $srcDir $ext
            if (Test-Path $srcPath) { Remove-Item $srcPath -Recurse -Force }
            Move-Item $dstPath $srcDir -Force
            Write-Log "Isolated: $ext"
        } else {
            Write-Log "Not found in active: $ext" "WARN"
        }
    }
    Remove-Item $stateFile -Force -ErrorAction SilentlyContinue
    Write-Log ""
    Write-Log "Undo complete. System returned to previous state."
}

function Invoke-RestoreAll {
    Write-Log "========== Restore All Isolated Extensions =========="
    if (-not (Test-Path $srcDir)) { Write-Log "Quarantine dir not found" "ERROR"; return }
    $dirs = Get-ChildItem $srcDir -Directory
    if ($dirs.Count -eq 0) { Write-Log "Quarantine is empty" ; return }
    foreach ($d in $dirs) {
        $dstPath = Join-Path $dstDir $d.Name
        if (Test-Path $dstPath) { Remove-Item $dstPath -Recurse -Force }
        Move-Item $d.FullName $dstDir -Force
        Write-Log "Restored: $($d.Name)"
    }
    Remove-Item $stateFile -Force -ErrorAction SilentlyContinue
    Write-Log ""
    Write-Log "All extensions restored. Please restart VSCode."
}

try {
    switch ($Action) {
        "status"        { Show-Status }
        "clean-lldb"    { Invoke-CleanLldb }
        "restore-one"   { Invoke-RestoreOne $ExtensionName }
        "restore-group" { Invoke-RestoreGroup $GroupIndex }
        "restore-all"   { Invoke-RestoreAll }
        "undo-last"     { Invoke-UndoLast }
    }
} catch {
    Write-Log "Script failed: $_" "ERROR"
    throw
}
Write-Log ""
Write-Log "Log saved to: $logFile"
