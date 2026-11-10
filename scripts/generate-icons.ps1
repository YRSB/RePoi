<#
.SYNOPSIS
  生成 RePoi 纯正无嵌套边框、大气醒目的 Option A（维基层叠纸面 + S型灵动浪花 H 标）图标资产。
.DESCRIPTION
  彻底消除“小方块套在大黑框里”的套娃问题：
  - 背景全幅铺满清爽天蓝渐变（#D6F1FA -> #BBE6F5）
  - 右侧叠合一层规整的半透白卡纸面（Option A 维基图鉴纸层）
  - H 标放大至充满安全区（高度 54dp / 柱宽 13.5dp），气场饱满、极速辨识
  - S型浪花飘带重塑为流畅丝滑的动感波浪
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

function BuildHeroicWave([float]$cx, [float]$cy, [float]$scale) {
    function X([float]$v) { return $cx + ($v - 54.0) * $scale }
    function Y([float]$v) { return $cy + ($v - 54.0) * $scale }
    $p = New-Object System.Drawing.Drawing2D.GraphicsPath
    $p.StartFigure()
    $p.AddBezier([float](X 33), [float](Y 59), [float](X 33), [float](Y 53), [float](X 38), [float](Y 48), [float](X 45), [float](Y 48))
    $p.AddBezier([float](X 45), [float](Y 48), [float](X 54), [float](Y 48), [float](X 62), [float](Y 42), [float](X 72), [float](Y 42))
    $p.AddBezier([float](X 72), [float](Y 42), [float](X 75), [float](Y 42), [float](X 76), [float](Y 45), [float](X 76), [float](Y 48))
    $p.AddBezier([float](X 76), [float](Y 48), [float](X 76), [float](Y 52), [float](X 71), [float](Y 53), [float](X 66), [float](Y 53))
    $p.AddBezier([float](X 66), [float](Y 53), [float](X 57), [float](Y 53), [float](X 51), [float](Y 60), [float](X 42), [float](Y 60))
    $p.AddBezier([float](X 42), [float](Y 60), [float](X 35), [float](Y 60), [float](X 33), [float](Y 63), [float](X 33), [float](Y 59))
    $p.CloseFigure()
    return $p
}

function RenderHeroicIconBitmap([int]$size, [bool]$isRound) {
    $bmp = New-Object System.Drawing.Bitmap $size, $size, ([System.Drawing.Imaging.PixelFormat]::Format32bppArgb)
    $g = [System.Drawing.Graphics]::FromImage($bmp)
    $g.SmoothingMode = [System.Drawing.Drawing2D.SmoothingMode]::AntiAlias
    $g.InterpolationMode = [System.Drawing.Drawing2D.InterpolationMode]::HighQualityBicubic
    $g.PixelOffsetMode = [System.Drawing.Drawing2D.PixelOffsetMode]::HighQuality
    $g.Clear([System.Drawing.Color]::Transparent)

    $pad = [float]($size * 0.04)
    $boxSize = [float]($size - $pad * 2)

    # 1. Base Squircle - 全幅清爽天蓝，彻底消灭外圈黑框套娃
    $rect = New-Object System.Drawing.RectangleF $pad, $pad, $boxSize, $boxSize
    $bgGrad = New-Object System.Drawing.Drawing2D.LinearGradientBrush(
        $rect,
        [System.Drawing.ColorTranslator]::FromHtml('#D6F1FA'),
        [System.Drawing.ColorTranslator]::FromHtml('#BBE6F5'),
        [System.Drawing.Drawing2D.LinearGradientMode]::Vertical
    )

    if ($isRound) {
        $g.FillEllipse($bgGrad, $rect)
    } else {
        $cornerR = [float]($boxSize * 0.24)
        $bgPath = BuildCapsule $pad $pad $boxSize $boxSize $cornerR
        $g.FillPath($bgGrad, $bgPath)
        $bgPath.Dispose()
    }
    $bgGrad.Dispose()

    # 坐标缩放映射 (基于 108 视口标准)
    $scale = [float]($size / 108.0)
    function S([float]$v) { return $v * $scale }

    # 2. Option A: 右侧微露的白色维基档案卡纸层（轻盈无硬边框）
    $sheetX = [float](S 58)
    $sheetY = [float](S 22)
    $sheetW = [float](S 38)
    $sheetH = [float](S 64)
    $sheetR = [float](S 16)
    $whiteSheet = BuildCapsule $sheetX $sheetY $sheetW $sheetH $sheetR
    $sheetBrush = New-Object System.Drawing.SolidBrush ([System.Drawing.Color]::FromArgb(215, 255, 255, 255))
    $g.FillPath($sheetBrush, $whiteSheet)
    $sheetBrush.Dispose()
    $whiteSheet.Dispose()

    # 3. 饱满大气的 H 标主体 (高度 54dp / 柱宽 13.5dp)
    $shadowBrush = New-Object System.Drawing.SolidBrush ([System.Drawing.Color]::FromArgb(35, 0, 30, 55))

    # 左柱阴影与本体 (#1C698D 深海蓝)
    $lpSh = BuildCapsule (S 31) (S 29) (S 13.5) (S 54) (S 6.75)
    $g.FillPath($shadowBrush, $lpSh)
    $lpSh.Dispose()

    $lpBrush = New-Object System.Drawing.SolidBrush ([System.Drawing.ColorTranslator]::FromHtml('#1C698D'))
    $lp = BuildCapsule (S 31) (S 27) (S 13.5) (S 54) (S 6.75)
    $g.FillPath($lpBrush, $lp)
    $lpBrush.Dispose()
    $lp.Dispose()

    # 右柱上半部：纯白
    $rpTopSh = BuildCapsule (S 63.5) (S 29) (S 13.5) (S 35) (S 6.75)
    $g.FillPath($shadowBrush, $rpTopSh)
    $rpTopSh.Dispose()

    $rpTopBrush = New-Object System.Drawing.SolidBrush ([System.Drawing.Color]::White)
    $rpTop = BuildCapsule (S 63.5) (S 27) (S 13.5) (S 35) (S 6.75)
    $g.FillPath($rpTopBrush, $rpTop)
    $rpTopBrush.Dispose()
    $rpTop.Dispose()

    # 右柱下半部：Hpoi 标志性蜜桃暖橙底托 (#FFA880)
    $peachSh = BuildCapsule (S 63.5) (S 62.5) (S 13.5) (S 20) (S 6.75)
    $g.FillPath($shadowBrush, $peachSh)
    $peachSh.Dispose()

    $peachBrush = New-Object System.Drawing.SolidBrush ([System.Drawing.ColorTranslator]::FromHtml('#FFA880'))
    $peach = BuildCapsule (S 63.5) (S 61) (S 13.5) (S 20) (S 6.75)
    $g.FillPath($peachBrush, $peach)
    $peachBrush.Dispose()
    $peach.Dispose()

    # 4. 纤细优雅的 S 型律动波浪飘带 (#6DCDE6)
    $cx = [float]($size / 2.0)
    $cy = [float]($size / 2.0)
    $waveSh = BuildHeroicWave $cx ($cy + (S 2.2)) $scale
    $waveShBrush = New-Object System.Drawing.SolidBrush ([System.Drawing.Color]::FromArgb(45, 0, 26, 48))
    $g.FillPath($waveShBrush, $waveSh)
    $waveShBrush.Dispose()
    $waveSh.Dispose()

    $waveBrush = New-Object System.Drawing.SolidBrush ([System.Drawing.ColorTranslator]::FromHtml('#6DCDE6'))
    $wave = BuildHeroicWave $cx $cy $scale
    $g.FillPath($waveBrush, $wave)
    $waveBrush.Dispose()
    $wave.Dispose()

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
        $bmp = RenderHeroicIconBitmap $s $false
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
    Write-Host "Created Heroic ICO: $outPath ($($sizes -join ', ') px)"
}

# --- 执行生成 ---
$resolvedRoot = (Resolve-Path $ProjectRoot).Path
$utf8NoBom = New-Object System.Text.UTF8Encoding $false

# 1. Desktop SVG
$svgContent = @'
<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 256 256" width="256" height="256">
  <defs>
    <linearGradient id="heroicBg" x1="0%" y1="0%" x2="0%" y2="100%">
      <stop offset="0%" stop-color="#D6F1FA" />
      <stop offset="100%" stop-color="#BBE6F5" />
    </linearGradient>
    <filter id="heroicShadow" x="-20%" y="-20%" width="140%" height="150%">
      <feDropShadow dx="0" dy="5" stdDeviation="4" flood-color="#001830" flood-opacity="0.18" />
    </filter>
    <filter id="waveShadow" x="-20%" y="-30%" width="140%" height="180%">
      <feDropShadow dx="0" dy="4" stdDeviation="3" flood-color="#001A2C" flood-opacity="0.22" />
    </filter>
  </defs>

  <!-- Base Squircle Container - Full Light Cyan -->
  <rect x="8" y="8" width="240" height="240" rx="56" fill="url(#heroicBg)" />

  <!-- Option A: White Wiki Card Layer -->
  <rect x="138" y="52" width="90" height="152" rx="38" fill="#FFFFFF" opacity="0.85" />

  <!-- Left Deep Blue Pillar -->
  <rect x="73" y="64" width="32" height="128" rx="16" fill="#1C698D" filter="url(#heroicShadow)" />

  <!-- Right Pillar White Top -->
  <rect x="151" y="64" width="32" height="83" rx="16" fill="#FFFFFF" filter="url(#heroicShadow)" />

  <!-- Right Pillar Peach Bottom -->
  <rect x="151" y="145" width="32" height="47" rx="16" fill="#FFA880" filter="url(#heroicShadow)" />

  <!-- Floating S-Curve Wave Ribbon -->
  <g filter="url(#waveShadow)">
    <path d="M 78,140 C 78,126 90,114 107,114 C 128,114 147,100 171,100 C 178,100 180,107 180,114 C 180,123 168,126 156,126 C 135,126 121,142 102,142 C 86,142 78,149 78,140 Z" fill="#6DCDE6" />
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
$resPng = RenderHeroicIconBitmap 256 $false
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
    $squareBmp = RenderHeroicIconBitmap $dim $false
    $squarePath = Join-Path $dir "ic_launcher.png"
    $squareBmp.Save($squarePath, [System.Drawing.Imaging.ImageFormat]::Png)
    $squareBmp.Dispose()

    # Round launcher
    $roundBmp = RenderHeroicIconBitmap $dim $true
    $roundPath = Join-Path $dir "ic_launcher_round.png"
    $roundBmp.Save($roundPath, [System.Drawing.Imaging.ImageFormat]::Png)
    $roundBmp.Dispose()

    Write-Host "Updated Android $folder : ${dim}x${dim} (regular & round)"
}

Write-Host "Heroic Option A Icons generated successfully!"
