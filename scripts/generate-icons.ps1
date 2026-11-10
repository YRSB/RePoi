<#
.SYNOPSIS
  生成 RePoi 纯正 Material 3 黄金比例胶囊 H 标全平台应用图标资产。
.DESCRIPTION
  基于用户确定的方案 1（纯正 M3 原味微调版）：
  - 彻底杜绝套娃边框：背景全幅铺满清澈明净天蓝渐变（#DEEDFF -> #C8E2FF）
  - 核心 H 标采用深海蓝（#004D79 -> #003D61），立柱两端完整圆角胶囊
  - 光学黄金分割中心微调至 52.5%，配合连续平滑内圆角过渡（Fillet R）
  - 多层柔和发散投影（Elevation Level 2），具有真实的 Material 3 卡片触感
  - 生成全套 Android 矢量/位图资源（含 Android 13+ Monochrome 主题图标）与 Windows Desktop 图标（SVG、ICO、PNG）
#>

param(
    [string]$ProjectRoot = "$PSScriptRoot/.."
)

$ErrorActionPreference = "Stop"
Add-Type -AssemblyName System.Drawing

function GetSquirclePath([float]$x, [float]$y, [float]$w, [float]$h, [float]$r) {
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

function BuildOption1HPath([float]$ox, [float]$oy, [float]$scale = 1.0) {
    # 512 基础坐标系，按 scale 缩放
    # 左柱 X: 146 ~ 206 (宽 60, 半径 30)
    # 右柱 X: 306 ~ 366 (宽 60, 半径 30)
    # 顶部半圆圆心 Y: 166 (顶边缘 136)
    # 底部半圆圆心 Y: 346 (底边缘 376)
    # 横梁 Y: 230 ~ 290 (高 60, 内圆角半径 24)
    $p = New-Object System.Drawing.Drawing2D.GraphicsPath
    $p.StartFigure()

    # 左柱顶部半圆
    $p.AddArc($ox + 146*$scale, $oy + 136*$scale, 60*$scale, 60*$scale, 180.0, 180.0)
    # 左柱内侧直线向下至内圆角连接点
    $p.AddLine($ox + 206*$scale, $oy + 166*$scale, $ox + 206*$scale, $oy + 206*$scale)
    # 左上内凹圆角 (连接横梁上边缘)
    $p.AddArc($ox + 206*$scale, $oy + 182*$scale, 48*$scale, 48*$scale, 180.0, -90.0)
    # 横梁上边缘水平线
    $p.AddLine($ox + 230*$scale, $oy + 230*$scale, $ox + 282*$scale, $oy + 230*$scale)
    # 右上内凹圆角 (连接右柱内侧)
    $p.AddArc($ox + 258*$scale, $oy + 182*$scale, 48*$scale, 48*$scale, 90.0, -90.0)
    # 右柱内侧直线向上至顶部半圆
    $p.AddLine($ox + 306*$scale, $oy + 206*$scale, $ox + 306*$scale, $oy + 166*$scale)
    # 右柱顶部半圆
    $p.AddArc($ox + 306*$scale, $oy + 136*$scale, 60*$scale, 60*$scale, 180.0, 180.0)
    # 右柱外侧直线向下至底部半圆
    $p.AddLine($ox + 366*$scale, $oy + 166*$scale, $ox + 366*$scale, $oy + 346*$scale)
    # 右柱底部半圆
    $p.AddArc($ox + 306*$scale, $oy + 316*$scale, 60*$scale, 60*$scale, 0.0, 180.0)
    # 右柱内侧直线向上至右下内圆角
    $p.AddLine($ox + 306*$scale, $oy + 346*$scale, $ox + 306*$scale, $oy + 314*$scale)
    # 右下内凹圆角 (连接横梁下边缘)
    $p.AddArc($ox + 258*$scale, $oy + 290*$scale, 48*$scale, 48*$scale, 0.0, -90.0)
    # 横梁下边缘水平线
    $p.AddLine($ox + 282*$scale, $oy + 290*$scale, $ox + 230*$scale, $oy + 290*$scale)
    # 左下内凹圆角 (连接左柱内侧)
    $p.AddArc($ox + 206*$scale, $oy + 290*$scale, 48*$scale, 48*$scale, 270.0, -90.0)
    # 左柱内侧直线向下至底部半圆
    $p.AddLine($ox + 206*$scale, $oy + 314*$scale, $ox + 206*$scale, $oy + 346*$scale)
    # 左柱底部半圆
    $p.AddArc($ox + 146*$scale, $oy + 316*$scale, 60*$scale, 60*$scale, 0.0, 180.0)
    # 左柱外侧直线向上回起点
    $p.AddLine($ox + 146*$scale, $oy + 346*$scale, $ox + 146*$scale, $oy + 166*$scale)

    $p.CloseFigure()
    return $p
}

function RenderOption1IconBitmap([int]$size, [bool]$isRound) {
    $bmp = New-Object System.Drawing.Bitmap $size, $size, ([System.Drawing.Imaging.PixelFormat]::Format32bppArgb)
    $g = [System.Drawing.Graphics]::FromImage($bmp)
    $g.SmoothingMode = [System.Drawing.Drawing2D.SmoothingMode]::AntiAlias
    $g.InterpolationMode = [System.Drawing.Drawing2D.InterpolationMode]::HighQualityBicubic
    $g.PixelOffsetMode = [System.Drawing.Drawing2D.PixelOffsetMode]::HighQuality
    $g.Clear([System.Drawing.Color]::Transparent)

    $scale = [float]($size / 512.0)
    $pad = [float](28.0 * $scale)
    $box = [float]($size - $pad * 2)

    # 1. Base Squircle / Round Background (清澈明净天蓝全幅铺满)
    $bgRect = New-Object System.Drawing.RectangleF $pad, $pad, $box, $box
    $bgBrush = New-Object System.Drawing.Drawing2D.LinearGradientBrush(
        $bgRect,
        [System.Drawing.ColorTranslator]::FromHtml('#DEEDFF'),
        [System.Drawing.ColorTranslator]::FromHtml('#C8E2FF'),
        [System.Drawing.Drawing2D.LinearGradientMode]::Vertical
    )

    if ($isRound) {
        $g.FillEllipse($bgBrush, $bgRect)
    } else {
        $sqR = [float](104.0 * $scale)
        $sqPath = GetSquirclePath $pad $pad $box $box $sqR
        $g.FillPath($bgBrush, $sqPath)

        # 细腻微边框高光
        $borderPen = New-Object System.Drawing.Pen ([System.Drawing.Color]::FromArgb(120, 255, 255, 255)), (1.5 * $scale)
        $g.DrawPath($borderPen, $sqPath)
        $borderPen.Dispose()
        $sqPath.Dispose()
    }
    $bgBrush.Dispose()

    # 2. Material 3 柔和实体微阴影 (多层扩散投影)
    $sh1Brush = New-Object System.Drawing.SolidBrush ([System.Drawing.Color]::FromArgb(25, 0, 40, 75))
    $sh2Brush = New-Object System.Drawing.SolidBrush ([System.Drawing.Color]::FromArgb(40, 0, 30, 60))

    $hSh1 = BuildOption1HPath 0 (6.0 * $scale) $scale
    $hSh2 = BuildOption1HPath 0 (3.0 * $scale) $scale
    $g.FillPath($sh1Brush, $hSh1)
    $g.FillPath($sh2Brush, $hSh2)
    $sh1Brush.Dispose(); $sh2Brush.Dispose()
    $hSh1.Dispose(); $hSh2.Dispose()

    # 3. 核心深海蓝胶囊 H 标
    $hPath = BuildOption1HPath 0 0 $scale
    $hRect = New-Object System.Drawing.RectangleF (146*$scale), (136*$scale), (220*$scale), (240*$scale)
    $hBrush = New-Object System.Drawing.Drawing2D.LinearGradientBrush(
        $hRect,
        [System.Drawing.ColorTranslator]::FromHtml('#004D79'),
        [System.Drawing.ColorTranslator]::FromHtml('#003D61'),
        [System.Drawing.Drawing2D.LinearGradientMode]::Vertical
    )
    $g.FillPath($hBrush, $hPath)
    $hBrush.Dispose()
    $hPath.Dispose()

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
        $bmp = RenderOption1IconBitmap $s $false
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
    Write-Host "Created Option 1 M3 ICO: $outPath ($($sizes -join ', ') px)"
}

# --- 执行资产生成 ---
$resolvedRoot = (Resolve-Path $ProjectRoot).Path
$utf8NoBom = New-Object System.Text.UTF8Encoding $false

# 1. Desktop SVG
$svgContent = @'
<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 256 256" width="256" height="256">
  <defs>
    <linearGradient id="bgGrad" x1="0%" y1="0%" x2="0%" y2="100%">
      <stop offset="0%" stop-color="#DEEDFF" />
      <stop offset="100%" stop-color="#C8E2FF" />
    </linearGradient>
    <linearGradient id="hGrad" x1="0%" y1="0%" x2="0%" y2="100%">
      <stop offset="0%" stop-color="#004D79" />
      <stop offset="100%" stop-color="#003D61" />
    </linearGradient>
    <filter id="m3Shadow" x="-20%" y="-20%" width="140%" height="150%">
      <feDropShadow dx="0" dy="2" stdDeviation="2.5" flood-color="#001F35" flood-opacity="0.18" />
      <feDropShadow dx="0" dy="4" stdDeviation="5" flood-color="#002D4A" flood-opacity="0.12" />
    </filter>
  </defs>

  <!-- Base Squircle Container -->
  <rect x="14" y="14" width="228" height="228" rx="52" ry="52" fill="url(#bgGrad)" stroke="#FFFFFF" stroke-opacity="0.6" stroke-width="1.5" />

  <!-- Pure M3 Capsule H Mark with Fillets and Elevation -->
  <path d="M 73 83 A 15 15 0 0 1 103 83 L 103 103 A 12 12 0 0 0 115 115 L 141 115 A 12 12 0 0 0 153 103 L 153 83 A 15 15 0 0 1 183 83 L 183 173 A 15 15 0 0 1 153 173 L 153 157 A 12 12 0 0 0 141 145 L 115 145 A 12 12 0 0 0 103 157 L 103 173 A 15 15 0 0 1 73 173 Z" fill="url(#hGrad)" filter="url(#m3Shadow)" />
</svg>
'@

$svgPath = Join-Path $resolvedRoot "desktopApp/icons/RePoi.svg"
[System.IO.File]::WriteAllText($svgPath, ($svgContent.TrimEnd() + "`n"), $utf8NoBom)
Write-Host "Updated Desktop SVG: $svgPath"

# 2. Desktop ICO
$icoPath = Join-Path $resolvedRoot "desktopApp/icons/RePoi.ico"
SaveIcoFile $icoPath @(16, 24, 32, 48, 64, 128, 256)

# 3. Desktop Resources Window PNG
$resDir = Join-Path $resolvedRoot "desktopApp/src/main/resources/icons"
if (-not (Test-Path $resDir)) { [System.IO.Directory]::CreateDirectory($resDir) | Out-Null }
$resPng = RenderOption1IconBitmap 256 $false
$resPngPath = Join-Path $resDir "RePoi.png"
$resPng.Save($resPngPath, [System.Drawing.Imaging.ImageFormat]::Png)
$resPng.Dispose()
Write-Host "Created Desktop Window PNG: $resPngPath"

# 4. Android Adaptive Icon XML Assets
# 4.1 Background (全幅清爽天蓝渐变，无任何边框)
$androidResDir = Join-Path $resolvedRoot "androidApp/src/main/res"
$bgXmlContent = @'
<?xml version="1.0" encoding="utf-8"?>
<vector xmlns:android="http://schemas.android.com/apk/res/android"
    xmlns:aapt="http://schemas.android.com/aapt"
    android:width="108dp"
    android:height="108dp"
    android:viewportWidth="108"
    android:viewportHeight="108">
    <!-- Full-bleed Soft Blue Gradient: Pure M3 Background with Zero Nested Framing -->
    <path android:pathData="M0,0h108v108h-108z">
        <aapt:attr name="android:fillColor">
            <gradient
                android:startX="54"
                android:startY="0"
                android:endX="54"
                android:endY="108"
                android:startColor="#DEEDFF"
                android:endColor="#C8E2FF"
                android:type="linear" />
        </aapt:attr>
    </path>
</vector>
'@
$bgXmlPath = Join-Path $androidResDir "drawable/ic_launcher_background.xml"
[System.IO.File]::WriteAllText($bgXmlPath, ($bgXmlContent.TrimEnd() + "`n"), $utf8NoBom)
Write-Host "Updated Android Background: $bgXmlPath"

# 4.2 Foreground (纯正黄金比例胶囊 H 标 + M3 柔和实体微投影)
$fgXmlContent = @'
<?xml version="1.0" encoding="utf-8"?>
<vector xmlns:android="http://schemas.android.com/apk/res/android"
    xmlns:aapt="http://schemas.android.com/aapt"
    android:width="108dp"
    android:height="108dp"
    android:viewportWidth="108"
    android:viewportHeight="108">
    <!-- M3 Level 2 Ambient Elevation Shadow -->
    <path
        android:fillColor="#1E002540"
        android:pathData="M 31,36.5 C 31,33.05 33.8,30.25 37.25,30.25 C 40.7,30.25 43.5,33.05 43.5,36.5 L 43.5,45.5 C 43.5,48.26 45.74,50.5 48.5,50.5 L 59.5,50.5 C 62.26,50.5 64.5,48.26 64.5,45.5 L 64.5,36.5 C 64.5,33.05 67.3,30.25 70.75,30.25 C 74.2,30.25 77,33.05 77,36.5 L 77,74.5 C 77,77.95 74.2,80.75 70.75,80.75 C 67.3,80.75 64.5,77.95 64.5,74.5 L 64.5,68 C 64.5,65.24 62.26,63 59.5,63 L 48.5,63 C 45.74,63 43.5,65.24 43.5,68 L 43.5,74.5 C 43.5,77.95 40.7,80.75 37.25,80.75 C 33.8,80.75 31,77.95 31,74.5 Z" />
    <!-- M3 Level 1 Key-Light Elevation Shadow -->
    <path
        android:fillColor="#2B001A30"
        android:pathData="M 31,35.8 C 31,32.35 33.8,29.55 37.25,29.55 C 40.7,29.55 43.5,32.35 43.5,35.8 L 43.5,44.8 C 43.5,47.56 45.74,49.8 48.5,49.8 L 59.5,49.8 C 62.26,49.8 64.5,47.56 64.5,44.8 L 64.5,35.8 C 64.5,32.35 67.3,29.55 70.75,29.55 C 74.2,29.55 77,32.35 77,35.8 L 77,73.8 C 77,77.25 74.2,80.05 70.75,80.05 C 67.3,80.05 64.5,77.25 64.5,73.8 L 64.5,67.3 C 64.5,64.54 62.26,62.3 59.5,62.3 L 48.5,62.3 C 45.74,62.3 43.5,64.54 43.5,67.3 L 43.5,73.8 C 43.5,77.25 40.7,80.05 37.25,80.05 C 33.8,80.05 31,77.25 31,73.8 Z" />
    <!-- Core M3 Pure Capsule H Mark Surface -->
    <path android:pathData="M 31,35 C 31,31.55 33.8,28.75 37.25,28.75 C 40.7,28.75 43.5,31.55 43.5,35 L 43.5,44 C 43.5,46.76 45.74,49 48.5,49 L 59.5,49 C 62.26,49 64.5,46.76 64.5,44 L 64.5,35 C 64.5,31.55 67.3,28.75 70.75,28.75 C 74.2,28.75 77,31.55 77,35 L 77,73 C 77,76.45 74.2,79.25 70.75,79.25 C 67.3,79.25 64.5,76.45 64.5,73 L 64.5,66.5 C 64.5,63.74 62.26,61.5 59.5,61.5 L 48.5,61.5 C 45.74,61.5 43.5,63.74 43.5,66.5 L 43.5,73 C 43.5,76.45 40.7,79.25 37.25,79.25 C 33.8,79.25 31,76.45 31,73 Z">
        <aapt:attr name="android:fillColor">
            <gradient
                android:startX="54"
                android:startY="28.75"
                android:endX="54"
                android:endY="79.25"
                android:startColor="#004D79"
                android:endColor="#003D61"
                android:type="linear" />
        </aapt:attr>
    </path>
</vector>
'@
$fgXmlPath = Join-Path $androidResDir "drawable-v24/ic_launcher_foreground.xml"
[System.IO.File]::WriteAllText($fgXmlPath, ($fgXmlContent.TrimEnd() + "`n"), $utf8NoBom)
Write-Host "Updated Android Foreground: $fgXmlPath"

# 4.3 Monochrome (Android 13+ 主题图标适配)
$monoXmlContent = @'
<?xml version="1.0" encoding="utf-8"?>
<vector xmlns:android="http://schemas.android.com/apk/res/android"
    android:width="108dp"
    android:height="108dp"
    android:viewportWidth="108"
    android:viewportHeight="108">
    <!-- Android 13+ Themed Icons Silhouette: Pure M3 Capsule "H" Mark -->
    <path
        android:fillColor="#000000"
        android:pathData="M 31,35 C 31,31.55 33.8,28.75 37.25,28.75 C 40.7,28.75 43.5,31.55 43.5,35 L 43.5,44 C 43.5,46.76 45.74,49 48.5,49 L 59.5,49 C 62.26,49 64.5,46.76 64.5,44 L 64.5,35 C 64.5,31.55 67.3,28.75 70.75,28.75 C 74.2,28.75 77,31.55 77,35 L 77,73 C 77,76.45 74.2,79.25 70.75,79.25 C 67.3,79.25 64.5,76.45 64.5,73 L 64.5,66.5 C 64.5,63.74 62.26,61.5 59.5,61.5 L 48.5,61.5 C 45.74,61.5 43.5,63.74 43.5,66.5 L 43.5,73 C 43.5,76.45 40.7,79.25 37.25,79.25 C 33.8,79.25 31,76.45 31,73 Z" />
</vector>
'@
$monoXmlPath = Join-Path $androidResDir "drawable/ic_launcher_monochrome.xml"
[System.IO.File]::WriteAllText($monoXmlPath, ($monoXmlContent.TrimEnd() + "`n"), $utf8NoBom)
Write-Host "Updated Android Monochrome: $monoXmlPath"

# 5. Android Mipmap PNGs (全密度切图)
$densityMap = @{
    "mipmap-mdpi"    = 48
    "mipmap-hdpi"    = 72
    "mipmap-xhdpi"   = 96
    "mipmap-xxhdpi"  = 144
    "mipmap-xxxhdpi" = 192
}

foreach ($folder in $densityMap.Keys) {
    $dim = $densityMap[$folder]
    $dir = Join-Path $androidResDir $folder

    # Regular squircle launcher
    $squareBmp = RenderOption1IconBitmap $dim $false
    $squarePath = Join-Path $dir "ic_launcher.png"
    $squareBmp.Save($squarePath, [System.Drawing.Imaging.ImageFormat]::Png)
    $squareBmp.Dispose()

    # Round launcher
    $roundBmp = RenderOption1IconBitmap $dim $true
    $roundPath = Join-Path $dir "ic_launcher_round.png"
    $roundBmp.Save($roundPath, [System.Drawing.Imaging.ImageFormat]::Png)
    $roundBmp.Dispose()

    Write-Host "Updated Android $folder : ${dim}x${dim} (regular & round)"
}

Write-Host "Option 1 Material 3 Capsule H Icons generated successfully!"
