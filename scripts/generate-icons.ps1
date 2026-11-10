<#
.SYNOPSIS
  生成 RePoi 纯正 Material 3 质感（纸面层叠 + 物理微阴影）跨平台图标资产。
.DESCRIPTION
  根据 Issue #106 方案 1（M3 层叠纸面与浮动横梁）：
  - desktopApp/icons/RePoi.svg
  - desktopApp/icons/RePoi.ico (16, 24, 32, 48, 64, 128, 256)
  - desktopApp/src/main/resources/icons/RePoi.png
  - androidApp/src/main/res/mipmap-*/ic_launcher.png
  - androidApp/src/main/res/mipmap-*/ic_launcher_round.png
#>

param(
    [string]$ProjectRoot = "$PSScriptRoot/.."
)

$ErrorActionPreference = "Stop"
Add-Type -AssemblyName System.Drawing

function BuildCapsulePath([float]$x, [float]$y, [float]$w, [float]$h, [float]$r) {
    $p = New-Object System.Drawing.Drawing2D.GraphicsPath
    $p.StartFigure()
    $p.AddArc($x, $y, $r * 2, $r * 2, 180.0, 90.0)
    $p.AddLine($x + $r, $y, $x + $w - $r, $y)
    $p.AddArc($x + $w - $r * 2, $y, $r * 2, $r * 2, 270.0, 90.0)
    $p.AddLine($x + $w, $y + $r, $x + $w, $y + $h - $r)
    $p.AddArc($x + $w - $r * 2, $y + $h - $r * 2, $r * 2, $r * 2, 0.0, 90.0)
    $p.AddLine($x + $w - $r, $y + $h, $x + $r, $y + $h)
    $p.AddArc($x, $y + $h - $r * 2, $r * 2, $r * 2, 90.0, 90.0)
    $p.AddLine($x, $y + $h - $r, $x, $y + $r)
    $p.CloseFigure()
    return $p
}

function RenderM3IconBitmap([int]$size, [bool]$isRound) {
    $bmp = New-Object System.Drawing.Bitmap $size, $size, ([System.Drawing.Imaging.PixelFormat]::Format32bppArgb)
    $g = [System.Drawing.Graphics]::FromImage($bmp)
    $g.SmoothingMode = [System.Drawing.Drawing2D.SmoothingMode]::AntiAlias
    $g.InterpolationMode = [System.Drawing.Drawing2D.InterpolationMode]::HighQualityBicubic
    $g.PixelOffsetMode = [System.Drawing.Drawing2D.PixelOffsetMode]::HighQuality
    $g.Clear([System.Drawing.Color]::Transparent)

    $pad = [float]($size * 0.05)
    $boxSize = [float]($size - $pad * 2)

    # 1. M3 Surface Container Background (Soft Sky Gradient)
    $rect = New-Object System.Drawing.RectangleF $pad, $pad, $boxSize, $boxSize
    $bgGrad = New-Object System.Drawing.Drawing2D.LinearGradientBrush(
        $rect,
        [System.Drawing.ColorTranslator]::FromHtml('#E2EEFF'),
        [System.Drawing.ColorTranslator]::FromHtml('#C6DEFF'),
        [System.Drawing.Drawing2D.LinearGradientMode]::Vertical
    )

    if ($isRound) {
        $g.FillEllipse($bgGrad, $rect)
    } else {
        $cornerR = [float]($boxSize * 0.24)
        $bgPath = BuildCapsulePath $pad $pad $boxSize $boxSize $cornerR
        $g.FillPath($bgGrad, $bgPath)
        $bgPath.Dispose()
    }
    $bgGrad.Dispose()

    # 坐标缩放映射 (Base 108 -> Target Size)
    $scale = [float]($size / 108.0)
    function X([float]$v) { return $v * $scale }
    function Y([float]$v) { return $v * $scale }

    # 2. Elevation 1: Pillars Shadow & Fill
    $pillarShadowDy = [float][Math]::Max(1.0, $size * 0.015)
    $shadowBrush1 = New-Object System.Drawing.SolidBrush ([System.Drawing.Color]::FromArgb(35, 0, 34, 61))

    # Left Pillar Shadow
    $lpShadow = BuildCapsulePath (X 32) (Y (28 + $pillarShadowDy)) (X 12) (Y 52) (X 6)
    $g.FillPath($shadowBrush1, $lpShadow)
    $lpShadow.Dispose()

    # Right Pillar Shadow
    $rpShadow = BuildCapsulePath (X 64) (Y (28 + $pillarShadowDy)) (X 12) (Y 52) (X 6)
    $g.FillPath($shadowBrush1, $rpShadow)
    $rpShadow.Dispose()
    $shadowBrush1.Dispose()

    # Pillars Solid Fill (#005C8F)
    $pillarBrush = New-Object System.Drawing.SolidBrush ([System.Drawing.ColorTranslator]::FromHtml('#005C8F'))
    $lp = BuildCapsulePath (X 32) (Y 28) (X 12) (Y 52) (X 6)
    $g.FillPath($pillarBrush, $lp)
    $lp.Dispose()

    $rp = BuildCapsulePath (X 64) (Y 28) (X 12) (Y 52) (X 6)
    $g.FillPath($pillarBrush, $rp)
    $rp.Dispose()
    $pillarBrush.Dispose()

    # 3. Elevation 2: Floating Crossbar Shadow & Card
    $bridgeShadowDy = [float][Math]::Max(1.5, $size * 0.02)
    $bridgeShadowBrush = New-Object System.Drawing.SolidBrush ([System.Drawing.Color]::FromArgb(45, 0, 29, 50))
    $bridgeShadow = BuildCapsulePath (X 30) (Y (47 + $bridgeShadowDy)) (X 48) (Y 14) (X 7)
    $g.FillPath($bridgeShadowBrush, $bridgeShadow)
    $bridgeShadow.Dispose()
    $bridgeShadowBrush.Dispose()

    # Floating Card Surface (#D4E4FF)
    $bridgeBrush = New-Object System.Drawing.SolidBrush ([System.Drawing.ColorTranslator]::FromHtml('#D4E4FF'))
    $bridge = BuildCapsulePath (X 30) (Y 47) (X 48) (Y 14) (X 7)
    $g.FillPath($bridgeBrush, $bridge)
    $bridge.Dispose()
    $bridgeBrush.Dispose()

    # Floating Card Inner Core (#004A75)
    $coreBrush = New-Object System.Drawing.SolidBrush ([System.Drawing.ColorTranslator]::FromHtml('#004A75'))
    $core = BuildCapsulePath (X 36) (Y 50) (X 36) (Y 8) (X 4)
    $g.FillPath($coreBrush, $core)
    $core.Dispose()
    $coreBrush.Dispose()

    # 4. Tertiary Anime Collectible Star Accent (#FF7AA3)
    $starBrush = New-Object System.Drawing.SolidBrush ([System.Drawing.ColorTranslator]::FromHtml('#FF7AA3'))
    $starR = [float](X 2.8)
    $starX = [float](X 70) - $starR
    $starY = [float](Y 34.5) - $starR
    $g.FillEllipse($starBrush, $starX, $starY, $starR * 2, $starR * 2)
    $starBrush.Dispose()

    $g.Dispose()
    return $bmp
}

function SaveIcoFile([string]$outPath, [int[]]$sizes) {
    $ms = New-Object System.IO.MemoryStream
    $bw = New-Object System.IO.BinaryWriter $ms

    $bw.Write([uint16]0)
    $bw.Write([uint16]1)
    $bw.Write([uint16]$sizes.Length)

    $pngList = @()
    foreach ($s in $sizes) {
        $bmp = RenderM3IconBitmap $s $false
        $imgMs = New-Object System.IO.MemoryStream
        $bmp.Save($imgMs, [System.Drawing.Imaging.ImageFormat]::Png)
        $bmp.Dispose()
        $pngList += ,$imgMs.ToArray()
        $imgMs.Dispose()
    }

    $offset = 6 + ($sizes.Length * 16)
    for ($i = 0; $i -lt $sizes.Length; $i++) {
        $s = $sizes[$i]
        $pngData = $pngList[$i]
        $wByte = if ($s -ge 256) { [byte]0 } else { [byte]$s }
        $hByte = if ($s -ge 256) { [byte]0 } else { [byte]$s }

        $bw.Write($wByte)
        $bw.Write($hByte)
        $bw.Write([byte]0)
        $bw.Write([byte]0)
        $bw.Write([uint16]1)
        $bw.Write([uint16]32)
        $bw.Write([uint32]$pngData.Length)
        $bw.Write([uint32]$offset)

        $offset += $pngData.Length
    }

    foreach ($pngData in $pngList) {
        $bw.Write($pngData)
    }

    $bw.Flush()
    [System.IO.File]::WriteAllBytes($outPath, $ms.ToArray())
    $bw.Dispose()
    $ms.Dispose()
    Write-Host "Created M3 ICO: $outPath ($($sizes -join ', ') px)"
}

# --- 执行生成 ---
$resolvedRoot = (Resolve-Path $ProjectRoot).Path
$utf8NoBom = New-Object System.Text.UTF8Encoding $false

# 1. Desktop SVG
$svgContent = @'
<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 256 256" width="256" height="256">
  <defs>
    <linearGradient id="m3_bg" x1="0%" y1="0%" x2="0%" y2="100%">
      <stop offset="0%" stop-color="#E2EEFF" />
      <stop offset="100%" stop-color="#C6DEFF" />
    </linearGradient>
    <filter id="m3_drop1" x="-20%" y="-20%" width="140%" height="150%">
      <feDropShadow dx="0" dy="4" stdDeviation="3" flood-color="#00223D" flood-opacity="0.16" />
    </filter>
    <filter id="m3_bridge_drop" x="-20%" y="-30%" width="140%" height="180%">
      <feDropShadow dx="0" dy="3.5" stdDeviation="3" flood-color="#001D32" flood-opacity="0.22" />
    </filter>
  </defs>

  <!-- M3 Squircle Base -->
  <rect x="12" y="12" width="232" height="232" rx="56" fill="url(#m3_bg)" stroke="rgba(255,255,255,0.6)" stroke-width="2" />

  <!-- Base Elevation: Left & Right Pillars -->
  <g filter="url(#m3_drop1)">
    <rect x="74" y="66" width="28" height="124" rx="14" fill="#005C8F" />
    <rect x="154" y="66" width="28" height="124" rx="14" fill="#005C8F" />
  </g>

  <!-- Floating Elevation: Crossbar Card -->
  <g filter="url(#m3_bridge_drop)">
    <rect x="70" y="112" width="116" height="32" rx="16" fill="#D4E4FF" />
  </g>

  <!-- Crossbar Inner Core -->
  <rect x="84" y="119" width="88" height="18" rx="9" fill="#004A75" />

  <!-- Tertiary Anime Star Dot -->
  <circle cx="168" cy="82" r="7" fill="#FF7AA3" />
</svg>
'@

$svgPath = Join-Path $resolvedRoot "desktopApp/icons/RePoi.svg"
[System.IO.File]::WriteAllText($svgPath, ($svgContent.TrimEnd() + "`n"), $utf8NoBom)
Write-Host "Updated Desktop SVG: $svgPath"

# 2. Desktop ICO
$icoPath = Join-Path $resolvedRoot "desktopApp/icons/RePoi.ico"
SaveIcoFile $icoPath @(16, 24, 32, 48, 64, 128, 256)

# 3. Desktop resources PNG
$resDir = Join-Path $resolvedRoot "desktopApp/src/main/resources/icons"
if (-not (Test-Path $resDir)) { [System.IO.Directory]::CreateDirectory($resDir) | Out-Null }
$resPng = RenderM3IconBitmap 256 $false
$resPngPath = Join-Path $resDir "RePoi.png"
$resPng.Save($resPngPath, [System.Drawing.Imaging.ImageFormat]::Png)
$resPng.Dispose()
Write-Host "Created Desktop Window PNG: $resPngPath"

# 4. Android Legacy Mipmap PNGs
$densityMap = @{
    "mipmap-mdpi"    = 48
    "mipmap-hdpi"    = 72
    "mipmap-xhdpi"   = 96
    "mipmap-xxhdpi"  = 144
    "mipmap-xxxhdpi" = 192
}

$androidResDir = Join-Path $resolvedRoot "androidApp/src/main/res"
foreach ($folder in $densityMap.Keys) {
    $dim = $densityMap[$folder]
    $dir = Join-Path $androidResDir $folder

    # Regular squircle launcher
    $squareBmp = RenderM3IconBitmap $dim $false
    $squarePath = Join-Path $dir "ic_launcher.png"
    $squareBmp.Save($squarePath, [System.Drawing.Imaging.ImageFormat]::Png)
    $squareBmp.Dispose()

    # Round launcher
    $roundBmp = RenderM3IconBitmap $dim $true
    $roundPath = Join-Path $dir "ic_launcher_round.png"
    $roundBmp.Save($roundPath, [System.Drawing.Imaging.ImageFormat]::Png)
    $roundBmp.Dispose()

    Write-Host "Updated Android $folder : ${dim}x${dim} (regular & round)"
}

Write-Host "M3 Icons generated successfully!"
