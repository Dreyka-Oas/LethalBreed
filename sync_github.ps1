# ============================================================
#  sync_github.ps1  -  LethalBreed GitHub Sync
#  Version 4.0 - Synchronisation stricte Disque -> Distant
# ============================================================
#
#  PRINCIPE : 
#  Ce script prend l'état de votre disque local COMME SOURCE ABSOLUE.
#  Il ne téléchargera JAMAIS rien de GitHub pour l'appliquer localement.
#  S'il y a des fichiers sur GitHub qui ne sont pas sur votre PC,
#  ils seront supprimés sans pitié via un Force-Push ("c'est que coter disque to distant").
#
#  STRUCTURE LOCALE :
#    <repo>/
#      <loader>/          <- ex: fabric, forge...
#        <version>/       <- ex: 1.21.10, 1.21.11...
#          src/, build.gradle, ...
#
#  STRUCTURE GITHUB :
#    Branche main     -> docs et scripts (.gitignore, README.md, CURSEFORGE.md, .ps1)
#    Branche fabric   -> Contenu de 'fabric' à la racine (1.21.10/, 1.21.11/)
#    Branche forge    -> Contenu de 'forge' à la racine
#
#  USAGE :
#    .\sync_github.ps1                   # Tout synchroniser
#    .\sync_github.ps1 -DryRun           # Simulation textuelle
#    .\sync_github.ps1 -Loader fabric    # Un seul loader
#    .\sync_github.ps1 -MainOnly         # Seulement la branche main
#
# ============================================================

param(
    [switch]$DryRun,
    [string]$Loader = "",
    [switch]$MainOnly
)

$ErrorActionPreference = "Stop"
if ($PSVersionTable.PSVersion.Major -ge 7) {
    $PSNativeCommandUseErrorActionPreference = $false
}

# ── Config ──────────────────────────────────────────────────
$REPO        = "W:\Minecraft\Projets\LethalBreed"
$GITHUB_URL  = "https://github.com/Dreyka-Oas/LethalBreed"
$MAIN_DOCS   = @(".gitignore", "README.md", "CURSEFORGE.md")
$IGNORE_DIRS = @(".git", ".gradle", "build", "run", "node_modules", ".idea", ".vs")
$VER_REGEX   = "^\d+\.\d+"
# ────────────────────────────────────────────────────────────

# ============================================================
#  Helpers
# ============================================================
function Write-Banner {
    $w = 60
    Write-Host ("=" * $w) -ForegroundColor DarkCyan
    Write-Host ""
    Write-Host "   LethalBreed GitHub Sync  v4.0 (Absolute Force-Sync)" -ForegroundColor Cyan
    Write-Host "   Repo : $REPO" -ForegroundColor Gray
    Write-Host "   URL  : $GITHUB_URL" -ForegroundColor Gray
    Write-Host ""
    Write-Host ("=" * $w) -ForegroundColor DarkCyan
}

function Write-Section([string]$msg) {
    Write-Host "`n  ─── $msg ───" -ForegroundColor Yellow
}

function Write-OK  ([string]$m) { Write-Host "  [OK] $m" -ForegroundColor Green   }
function Write-Info([string]$m) { Write-Host "   >>  $m" -ForegroundColor Gray    }
function Write-Warn([string]$m) { Write-Host "  [!]  $m" -ForegroundColor Magenta }
function Write-Err ([string]$m) { Write-Host "  [X]  $m" -ForegroundColor Red     }

function Invoke-Git {
    param(
        [string[]]$GitArgs,
        [string]   $WorkDir   = $REPO,
        [switch]   $AllowFail
    )
    if ($DryRun) {
        Write-Host "       [DRY] git $($GitArgs -join ' ')" -ForegroundColor DarkGray
        return @{ OK = $true; Out = "" }
    }
    $prev = Get-Location
    Set-Location $WorkDir
    
    $oldErr = $ErrorActionPreference
    $ErrorActionPreference = "Continue"
    $outFile = Join-Path $env:TEMP "gitout.txt"
    & cmd.exe /c "git $($GitArgs -join ' ') > `"$outFile`" 2>&1"
    
    # Catch both cmd exit code and git exit code
    $ok = ($LASTEXITCODE -eq 0)
    $fullOut = Get-Content $outFile -Raw -ErrorAction SilentlyContinue
    if ($null -eq $fullOut) { $fullOut = "" } else { $fullOut = $fullOut.Trim() }
    
    $ErrorActionPreference = $oldErr
    Set-Location $prev

    if (-not $ok -and -not $AllowFail) {
        throw "git $($GitArgs -join ' ')  ->  $fullOut"
    }
    return @{ OK = $ok; Out = $fullOut }
}

# ============================================================
#  Detection loaders + versions
# ============================================================
function Get-Loaders {
    Get-ChildItem -Path $REPO -Directory |
        Where-Object { $IGNORE_DIRS -notcontains $_.Name } |
        Where-Object {
            (Get-ChildItem $_.FullName -Directory -ErrorAction SilentlyContinue |
             Where-Object { $_.Name -match $VER_REGEX }).Count -gt 0
        } |
        Select-Object -ExpandProperty Name
}

function Get-Versions([string]$LoaderName) {
    $path = Join-Path $REPO $LoaderName
    Get-ChildItem $path -Directory -ErrorAction SilentlyContinue |
        Where-Object { $_.Name -match $VER_REGEX } |
        Sort-Object Name |
        Select-Object -ExpandProperty Name
}

# ============================================================
#  MAIN : Fichiers racines uniquement
# ============================================================
function Sync-Main {
    Write-Section "Branche  main  (docs et scripts uniquement)"
    
    if ($DryRun) {
        Write-Info "[DRY] S'assurerait que seuls les '$MAIN_DOCS' et *.ps1 sont sur main."
        return
    }

    Invoke-Git @("checkout", "main") | Out-Null
    
    Write-Info "Configuration de l'index Git local (retrait des fichiers superflus)..."
    # Retire tout le tracking pour le forcer à la main (assure que les dossiers loaders ne fuient pas sur main)
    Invoke-Git @("rm", "-r", "--cached", ".") -AllowFail | Out-Null
    
    # On rajoute strictement ce qu'on veut
    foreach ($doc in $MAIN_DOCS) {
        if (Test-Path (Join-Path $REPO $doc)) {
            Invoke-Git @("add", $doc) -AllowFail | Out-Null
        }
    }
    # On ajoute aussi les scripts
    $scripts = Get-ChildItem -Path $REPO -Filter "*.ps1" | Select-Object -ExpandProperty Name
    foreach ($script in $scripts) {
        Invoke-Git @("add", $script) -AllowFail | Out-Null
    }

    $staged = (& git -C $REPO diff --cached --name-only) | Where-Object { $_ }
    if ($staged) {
        Invoke-Git @("commit", "-m", "chore(main): overwrite repo matching local exactly") -AllowFail | Out-Null
        Write-OK "Commit créé sur main"
    } else {
        Write-OK "main est déjà propre"
    }

    # Comme ordonné: "disque to distant donc on met tout à jour de force si erreur"
    Write-Info "Push main -> origin..."
    $pArgs = @("push", "origin", "main", "--force")
    Invoke-Git $pArgs | Out-Null
    Write-OK "main -> GitHub  OK"
}

# ============================================================
#  LOADER : Version à la racine via Dépôt Temporaire Jetable
# ============================================================
function Sync-Loader([string]$LoaderName) {
    Write-Section "Branche  $LoaderName"

    $versions = Get-Versions $LoaderName
    if ($versions.Count -eq 0) {
        Write-Warn "Aucune version trouvée dans $LoaderName/ - ignoré"
        return
    }
    Write-Info "Versions locales : $($versions -join ', ')"

    if ($DryRun) {
        Write-Info "[DRY] Créerait un dossier pour $LoaderName avec les versions."
        Write-Info "[DRY] Exécuterait git init -> git add -> git push origin HEAD:refs/heads/$LoaderName --force"
        return
    }

    $tempPath = Join-Path $env:TEMP "lb_sync_$LoaderName"
    
    # Nettoyer l'espace temp si vestige
    if (Test-Path $tempPath) {
        Remove-Item $tempPath -Recurse -Force -ErrorAction SilentlyContinue
    }
    New-Item -ItemType Directory -Path $tempPath > $null

    try {
        # ── Copier uniquement les versions locales vers le rep temp ──
        foreach ($ver in $versions) {
            $src = Join-Path (Join-Path $REPO $LoaderName) $ver
            $dst = Join-Path $tempPath $ver
            Write-Info "Prep version $ver..."
            Copy-Item -Path $src -Destination $dst -Recurse -Force
        }

        # ── Créer un Git vierge et pousser de force ──
        Set-Location $tempPath
        & git init --initial-branch=tmp > $null
        & git config core.longpaths true > $null
        
        # IMPORTANT: Copier le .gitignore pour que "git add ." ignore récursivement build, .gradle, etc.
        if (Test-Path (Join-Path $REPO ".gitignore")) {
            Copy-Item (Join-Path $REPO ".gitignore") (Join-Path $tempPath ".gitignore") -Force
        }
        
        # Astuce : On récupère l'URL distante locale pour éviter de dépendre du fichier
        $remoteUrl = (& git -C $REPO remote get-url origin 2>$null).Trim()
        if (-not $remoteUrl) {
            $remoteUrl = $GITHUB_URL
        }

        & git add . > $null
        & git commit -m "sync($LoaderName): absolute force-sync from disk (versions: $($versions -join ', '))" > $null
        
        Write-Info "Force-Push $LoaderName -> origin..."
        
        $oldErr = $ErrorActionPreference
        $ErrorActionPreference = "Continue"
        $outFileLog = Join-Path $env:TEMP "gitpush.txt"
        & cmd.exe /c "git push --force `"$remoteUrl`" HEAD:refs/heads/$LoaderName > `"$outFileLog`" 2>&1"
        $okPush = ($LASTEXITCODE -eq 0)
        $r = Get-Content $outFileLog -Raw -ErrorAction SilentlyContinue
        $ErrorActionPreference = $oldErr
        
        if ($okPush) {
            Write-OK "$LoaderName -> GitHub  OK (copie parfaite du disque)"
        } else {
            throw "Push force vers $LoaderName a échoué : $r"
        }
    } finally {
        Set-Location $REPO
        # On détruit la copie temporaire (elle a servi à écraser le distant)
        Remove-Item $tempPath -Recurse -Force -ErrorAction SilentlyContinue
    }
}

# ============================================================
#  NETTOYAGE : Supprime les branches distantes obsolètes
# ============================================================
function Sync-CleanupRemote([string[]]$ValidLoaders) {
    Write-Section "Nettoyage des branches orphelines sur GitHub"
    
    $remoteUrl = (& git -C $REPO remote get-url origin 2>$null).Trim()
    if (-not $remoteUrl) {
        $remoteUrl = $GITHUB_URL
    }

    $remoteBranchesRaw = & git -C $REPO ls-remote --heads $remoteUrl 2>$null
    # Extraction propre des noms de branche depuis la signature refs/heads/nom
    $remoteBranches = $remoteBranchesRaw | ForEach-Object { 
        if ($_ -match "refs/heads/(.+)") { $matches[1] } 
    }
    
    $protectedBranches = @("main", "master")
    $hasDeleted = $false

    foreach ($rb in $remoteBranches) {
        # Si la branche n'est pas main et n'appartient pas à la liste des loaders existants
        if ($rb -notin $protectedBranches -and $rb -notin $ValidLoaders) {
            $hasDeleted = $true
            Write-Warn "Branche distante '$rb' non reconnue localement -> SUPPRESSION"
            
            if ($DryRun) {
                Write-Info "[DRY] Exécuterait : git push --delete `"$remoteUrl`" $rb"
            } else {
                $oldErr = $ErrorActionPreference
                $ErrorActionPreference = "Continue"
                $outDel = Join-Path $env:TEMP "gitdel.txt"
                & cmd.exe /c "git -C `"$REPO`" push `"$remoteUrl`" --delete $rb > `"$outDel`" 2>&1"
                if ($LASTEXITCODE -eq 0) {
                    Write-OK "Branche $rb supprimée avec succès de GitHub."
                } else {
                    $errDel = Get-Content $outDel -Raw -ErrorAction SilentlyContinue
                    Write-Err "Impossible de supprimer la branche $rb : $errDel"
                }
                $ErrorActionPreference = $oldErr
            }
        }
    }

    if (-not $hasDeleted) {
         Write-Info "Aucune branche orpheline à supprimer."
    }
}

# ============================================================
#  ENTRYPOINT
# ============================================================
Write-Banner

if ($DryRun) {
    Write-Host ""
    Write-Host "  !! MODE DRY-RUN : Simulation seulement !!" -ForegroundColor DarkYellow
}

Set-Location $REPO

$loadersToSync = if ($Loader) { @($Loader) } else { Get-Loaders }

if ($loadersToSync.Count -eq 0 -and -not $MainOnly) {
    Write-Warn "Aucun loader détecté."
    Write-Warn "Structure attendue : <loader>/<version>/"
    exit 0
}

$startTime = Get-Date

try {
    # ── Branche main ──
    if (-not $Loader) {
        Sync-Main
    }

    # ── Branches loader ──
    if (-not $MainOnly) {
        if ($loadersToSync.Count -gt 0) {
            Write-Info "Loaders détectés : $($loadersToSync -join ', ')"
        }
        foreach ($l in $loadersToSync) {
            Sync-Loader $l
        }
    }
    
    # ── Nettoyage orphelines ──
    if (-not $Loader -and -not $MainOnly) {
        Sync-CleanupRemote $loadersToSync
    }

    # Re-S'assurer d'être sur main et au propre niveau travail
    Invoke-Git @("checkout", "main") -AllowFail | Out-Null
    
} catch {
    $err = $_
    Write-Err "Erreur fatale : $err"
    Set-Location $REPO
    Invoke-Git @("checkout", "main") -AllowFail | Out-Null
    exit 1
}

# ── Résumé ─────────────────────────────────────────────────
$elapsed = [math]::Round(((Get-Date) - $startTime).TotalSeconds, 1)
$w = 60
Write-Host "`n$("=" * $w)" -ForegroundColor DarkGreen
Write-Host "`n  SYNCHRONISATION TERMINEE  (${elapsed}s)" -ForegroundColor Green
Write-Host ""
if (-not $Loader) {
    Write-Host "  main   ->  $($MAIN_DOCS -join ', ') & *.ps1" -ForegroundColor White
}
if (-not $MainOnly) {
    foreach ($l in $loadersToSync) {
        $vers = Get-Versions $l
        Write-Host "  $l   ->  $($vers -join ', ')" -ForegroundColor White
    }
}
Write-Host "`n  Rappel: Toute divergence GitHub a été supprimée." -ForegroundColor Magenta
Write-Host "`n$("=" * $w)`n" -ForegroundColor DarkGreen
