<#
.SYNOPSIS
  生成 RePoi 跨平台图标资产（Option A：维基双层档案卡 + S型律动飘带 H 标 + 暖橙底托）。
.DESCRIPTION
  根据 Issue #106 用户确认的 Option A 设计方案：
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

function BuildCapsule([float]$x, [float]$y, [float]$w, [float]$h, [float]$r) {
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

function BuildRibbon([float]$scale, [float]$dx, [float]$dy) {
    function X([float]$v) { return $v * $scale + $dx }
    function Y([float]$v) { return $v * $scale + $dy }
    $p = New-Object System.Drawing.Drawing2D.GraphicsPath
    $p.StartFigure()
    $p.AddBezier([float](X 38), [float](Y 56), [float](X 38), [float](Y 49), [float](X 43), [float](Y 46), [float](X 48), [float](Y 46))
    $p.AddBezier([float](X 48), [float](Y 46), [float](X 54), [float](Y 46), [float](X 56), [float](Y 42), [float](X 63), [float](Y 42))
    $p.AddBezier([float](X 63), [float](Y 42), [float](X 67), [float](Y 42), [float](X 68), [float](Y 45), [float](X 68), [float](Y 48))
    $p.AddLine([float](X 68), [float](Y 48), [float](X 68), [float](Y 52))
    $p.AddBezier([float](X 68), [float](Y 52), [float](X 68), [float](Y 58), [float](X 63), [float](Y 60), [float](X 57), [float](Y 60))
    $p.AddBezier([float](X 57), [float](Y 60), [float](X 51), [float](Y 60), [float](X 48), [float](Y 64), [float](X 42), [float](Y 64))
    $p.AddBezier([float](X 42), [float](Y 64), [float](X 39), [float](Y 64), [float](X 38), [float](Y 61), [float](X 38), [float](Y 56))
    $p.CloseFigure()
    return $p
}

function RenderOptionAIconBitmap([int]$size, [bool]$isRound) {
    $bmp = New-Object System.Drawing.Bitmap $size, $size, ([System.Drawing.Imaging.PixelFormat]::Format32bppArgb)
    $g = [System.Drawing.Graphics]::FromImage($bmp)
    $g.SmoothingMode = [System.Drawing.Drawing2D.SmoothingMode]::AntiAlias
    $g.InterpolationMode = [System.Drawing.Drawing2D.InterpolationMode]::HighQualityBicubic
    $g.PixelOffsetMode = [System.Drawing.Drawing2D.PixelOffsetMode]::HighQuality
    $g.Clear([System.Drawing.Color]::Transparent)

    $pad = [float]($size * 0.04)
    $boxSize = [float]($size - $pad * 2)

    # 1. Base Container (Deep Ocean Blue Gradient)
    $rect = New-Object System.Drawing.RectangleF $pad, $pad, $boxSize, $boxSize
    $baseGrad = New-Object System.Drawing.Drawing2D.LinearGradientBrush(
        $rect,
        [System.Drawing.ColorTranslator]::FromHtml('#24789B'),
        [System.Drawing.ColorTranslator]::FromHtml('#175D7D'),
        [System.Drawing.Drawing2D.LinearGradientMode]::Vertical
    )

    if ($isRound) {
        $g.FillEllipse($baseGrad, $rect)
    } else {
        $cornerR = [float]($boxSize * 0.24)
        $bgPath = BuildCapsule $pad $pad $boxSize $boxSize $cornerR
        $g.FillPath($baseGrad, $bgPath)
        $bgPath.Dispose()
    }
    $baseGrad.Dispose()

    # Scale mapping from 108 coordinate space
    $scale = [float]($size / 108.0)
    function S([float]$v) { return $v * $scale }

    $shadowBrush = New-Object System.Drawing.SolidBrush ([System.Drawing.Color]::FromArgb(40, 0, 20, 36))

    # 2. White Card (Option A: single offset card peeking at bottom-right)
    $whiteCardShadow = BuildCapsule (S 28) (S 29) (S 56) (S 60) (S 16)
    $g.FillPath($shadowBrush, $whiteCardShadow)
    $whiteCardShadow.Dispose()

    $whiteBrush = New-Object System.Drawing.SolidBrush ([System.Drawing.Color]::FromArgb(245, 255, 255, 255))
    $whiteCard = BuildCapsule (S 28) (S 26) (S 56) (S 60) (S 16)
    $g.FillPath($whiteBrush, $whiteCard)
    $whiteBrush.Dispose()
    $whiteCard.Dispose()

    # 3. Top Cyan Card
    $cyanCardShadow = BuildCapsule (S 24) (S 25) (S 56) (S 60) (S 16)
    $g.FillPath($shadowBrush, $cyanCardShadow)
    $cyanCardShadow.Dispose()

    $cyanBrush = New-Object System.Drawing.SolidBrush ([System.Drawing.ColorTranslator]::FromHtml('#C7EDF9'))
    $cyanCard = BuildCapsule (S 24) (S 22) (S 56) (S 60) (S 16)
    $g.FillPath($cyanBrush, $cyanCard)
    $cyanBrush.Dispose()
    $cyanCard.Dispose()

    # 4. Left Pillar Shadow & Solid Fill (#1C698D)
    $lpShadow = BuildCapsule (S 36) (S 33.5) (S 10) (S 40) (S 5)
    $g.FillPath($shadowBrush, $lpShadow)
    $lpShadow.Dispose()

    $lpBrush = New-Object System.Drawing.SolidBrush ([System.Drawing.ColorTranslator]::FromHtml('#1C698D'))
    $lp = BuildCapsule (S 36) (S 32) (S 10) (S 40) (S 5)
    $g.FillPath($lpBrush, $lp)
    $lpBrush.Dispose()
    $lp.Dispose()

    # 5. Right Pillar (White Top + Peach Bottom)
    $rpTopBrush = New-Object System.Drawing.SolidBrush ([System.Drawing.Color]::White)
    $rpTop = BuildCapsule (S 62) (S 32) (S 10) (S 27) (S 5)
    $g.FillPath($rpTopBrush, $rpTop)
    $rpTopBrush.Dispose()
    $rpTop.Dispose()

    $peachBrush = New-Object System.Drawing.SolidBrush ([System.Drawing.ColorTranslator]::FromHtml('#FFA880'))
    $rpBottom = BuildCapsule (S 62) (S 57) (S 10) (S 15) (S 5)
    $g.FillPath($peachBrush, $rpBottom)
    $peachBrush.Dispose()
    $rpBottom.Dispose()

    # 6. Ribbon Shadow & Fill (#79D1E8)
    $ribbonShadowDy = [float][Math]::Max(1.0, $size * 0.018)
    $ribbonShadow = BuildRibbon $scale 0 (S $ribbonShadowDy)
    $ribbonShadowBrush = New-Object System.Drawing.SolidBrush ([System.Drawing.Color]::FromArgb(50, 0, 26, 44))
    $g.FillPath($ribbonShadowBrush, $ribbonShadow)
    $ribbonShadowBrush.Dispose()
    $ribbonShadow.Dispose()

    $ribbonBrush = New-Object System.Drawing.SolidBrush ([System.Drawing.ColorTranslator]::FromHtml('#79D1E8'))
    $ribbon = BuildRibbon $scale 0 0
    $g.FillPath($ribbonBrush, $ribbon)
    $ribbonBrush.Dispose()
    $ribbon.Dispose()

    $shadowBrush.Dispose()
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
        $bmp = RenderOptionAIconBitmap $s $false
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
    Write-Host "Created Option A ICO: $outPath ($($sizes -join ', ') px)"
}

# --- 执行生成 ---
$resolvedRoot = (Resolve-Path $ProjectRoot).Path
$utf8NoBom = New-Object System.Text.UTF8Encoding $false

# 1. Desktop SVG
$svgContent = @'
<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 256 256" width="256" height="256">
  <defs>
    <linearGradient id="bgGrad" x1="0%" y1="0%" x2="0%" y2="100%">
      <stop offset="0%" stop-color="#24789B" />
      <stop offset="100%" stop-color="#175D7D" />
    </linearGradient>
    <filter id="cardShadow" x="-20%" y="-20%" width="140%" height="150%">
      <feDropShadow dx="0" dy="5" stdDeviation="4" flood-color="#001524" flood-opacity="0.22" />
    </filter>
    <filter id="ribbonShadow" x="-20%" y="-20%" width="140%" height="150%">
      <feDropShadow dx="0" dy="4" stdDeviation="3" flood-color="#001A2C" flood-opacity="0.25" />
    </filter>
  </defs>

  <!-- Base Squircle Container -->
  <rect x="8" y="8" width="240" height="240" rx="56" fill="url(#bgGrad)" />

  <!-- Option A: Single White Card Peeking out at Bottom-Right -->
  <rect x="66" y="61" width="133" height="142" rx="38" fill="#FFFFFF" opacity="0.94" filter="url(#cardShadow)" />

  <!-- Top Cyan Main Card -->
  <rect x="57" y="52" width="133" height="142" rx="38" fill="#C7EDF9" filter="url(#cardShadow)" />

  <!-- Left Deep Blue Pillar -->
  <rect x="85" y="76" width="24" height="95" rx="12" fill="#1C698D" />

  <!-- Right Pillar White Top -->
  <rect x="147" y="76" width="24" height="64" rx="12" fill="#FFFFFF" />

  <!-- Right Pillar Peach Bottom -->
  <rect x="147" y="135" width="24" height="36" rx="12" fill="#FFA880" />

  <!-- Floating S-Curve Wave Ribbon -->
  <g filter="url(#ribbonShadow)">
    <path d="M 90,133 C 90,116 102,109 114,109 C 128,109 133,99 150,99 C 159,99 161,107 161,114 L 161,123 C 161,137 150,142 135,142 C 121,142 114,152 100,152 C 92,152 90,145 90,133 Z" fill="#79D1E8" />
  </g>
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
$resPng = RenderOptionAIconBitmap 256 $false
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
    $squareBmp = RenderOptionAIconBitmap $dim $false
    $squarePath = Join-Path $dir "ic_launcher.png"
    $squareBmp.Save($squarePath, [System.Drawing.Imaging.ImageFormat]::Png)
    $squareBmp.Dispose()

    # Round launcher
    $roundBmp = RenderOptionAIconBitmap $dim $true
    $roundPath = Join-Path $dir "ic_launcher_round.png"
    $roundBmp.Save($roundPath, [System.Drawing.Imaging.ImageFormat]::Png)
    $roundBmp.Dispose()

    Write-Host "Updated Android $folder : ${dim}x${dim} (regular & round)"
}

Write-Host "Option A Icons generated successfully!"
