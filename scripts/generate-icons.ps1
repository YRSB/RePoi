<#
.SYNOPSIS
  生成 RePoi 跨平台图标资产（Android Adaptive Icon 位图 / Desktop SVG / ICO / PNG）。
.DESCRIPTION
  根据 Issue #106 规范，基于 Material 3 Expressive 胶囊几何构型生成：
  - desktopApp/icons/RePoi.svg
  - desktopApp/src/main/resources/icons/RePoi.png
  - desktopApp/icons/RePoi.ico (16, 24, 32, 48, 64, 128, 256)
  - androidApp/src/main/res/mipmap-*/ic_launcher.png
  - androidApp/src/main/res/mipmap-*/ic_launcher_round.png
#>

param(
    [string]$ProjectRoot = "$PSScriptRoot/.."
)

$ErrorActionPreference = "Stop"
Add-Type -AssemblyName System.Drawing

function BuildHPath([float]$cx, [float]$cy, [float]$s) {
    function X([float]$v) { return $cx + ($v - 54.0) * $s }
    function Y([float]$v) { return $cy + ($v - 54.0) * $s }
    function D([float]$v) { return $v * $s }

    $p = New-Object System.Drawing.Drawing2D.GraphicsPath
    $p.StartFigure()
    $p.AddArc([float](X 32), [float](Y 30), [float](D 12), [float](D 12), 180.0, 180.0)
    $p.AddLine([float](X 44), [float](Y 36), [float](X 44), [float](Y 44))
    $p.AddArc([float](X 40), [float](Y 40), [float](D 8),  [float](D 8),  180.0, -90.0)
    $p.AddLine([float](X 48), [float](Y 48), [float](X 60), [float](Y 48))
    $p.AddArc([float](X 56), [float](Y 40), [float](D 8),  [float](D 8),  90.0,  -90.0)
    $p.AddLine([float](X 64), [float](Y 44), [float](X 64), [float](Y 36))
    $p.AddArc([float](X 64), [float](Y 30), [float](D 12), [float](D 12), 180.0, 180.0)
    $p.AddLine([float](X 76), [float](Y 36), [float](X 76), [float](Y 72))
    $p.AddArc([float](X 64), [float](Y 66), [float](D 12), [float](D 12), 0.0,   180.0)
    $p.AddLine([float](X 64), [float](Y 72), [float](X 64), [float](Y 64))
    $p.AddArc([float](X 56), [float](Y 60), [float](D 8),  [float](D 8),  0.0,   -90.0)
    $p.AddLine([float](X 60), [float](Y 60), [float](X 48), [float](Y 60))
    $p.AddArc([float](X 44), [float](Y 60), [float](D 8),  [float](D 8),  270.0, -90.0)
    $p.AddLine([float](X 44), [float](Y 64), [float](X 44), [float](Y 72))
    $p.AddArc([float](X 32), [float](Y 66), [float](D 12), [float](D 12), 0.0,   180.0)
    $p.AddLine([float](X 32), [float](Y 72), [float](X 32), [float](Y 36))
    $p.CloseFigure()
    return $p
}

function BuildSquirclePath([float]$x, [float]$y, [float]$w, [float]$h, [float]$r) {
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

function RenderIconBitmap([int]$size, [bool]$isRound) {
    $bmp = New-Object System.Drawing.Bitmap $size, $size, ([System.Drawing.Imaging.PixelFormat]::Format32bppArgb)
    $g = [System.Drawing.Graphics]::FromImage($bmp)
    $g.SmoothingMode = [System.Drawing.Drawing2D.SmoothingMode]::AntiAlias
    $g.InterpolationMode = [System.Drawing.Drawing2D.InterpolationMode]::HighQualityBicubic
    $g.PixelOffsetMode = [System.Drawing.Drawing2D.PixelOffsetMode]::HighQuality
    $g.Clear([System.Drawing.Color]::Transparent)

    $pad = [float]($size * 0.05)
    $boxSize = [float]($size - $pad * 2)

    $rect = New-Object System.Drawing.RectangleF $pad, $pad, $boxSize, $boxSize
    $gradBrush = New-Object System.Drawing.Drawing2D.LinearGradientBrush(
        $rect,
        [System.Drawing.ColorTranslator]::FromHtml('#E3F0FF'),
        [System.Drawing.ColorTranslator]::FromHtml('#C7E1FF'),
        [System.Drawing.Drawing2D.LinearGradientMode]::Vertical
    )

    if ($isRound) {
        $g.FillEllipse($gradBrush, $rect)
    } else {
        $cornerR = [float]($boxSize * 0.22)
        $bgPath = BuildSquirclePath $pad $pad $boxSize $boxSize $cornerR
        $g.FillPath($gradBrush, $bgPath)
        $bgPath.Dispose()
    }
    $gradBrush.Dispose()

    # H 图标缩放与微阴影
    $scale = [float]($size / 108.0 * 0.88)
    $cx = [float]($size / 2.0)
    $cy = [float]($size / 2.0)
    $shadowDy = [float][Math]::Max(1.0, $size * 0.015)

    # 绘制微投影
    $shadowPath = BuildHPath $cx ($cy + $shadowDy) $scale
    $shadowBrush = New-Object System.Drawing.SolidBrush ([System.Drawing.Color]::FromArgb(31, 0, 34, 61))
    $g.FillPath($shadowBrush, $shadowPath)
    $shadowBrush.Dispose()
    $shadowPath.Dispose()

    # 绘制主 H 标
    $hPath = BuildHPath $cx $cy $scale
    $hBrush = New-Object System.Drawing.SolidBrush ([System.Drawing.ColorTranslator]::FromHtml('#004B76'))
    $g.FillPath($hBrush, $hPath)
    $hBrush.Dispose()
    $hPath.Dispose()

    $g.Dispose()
    return $bmp
}

function SaveIcoFile([string]$outPath, [int[]]$sizes) {
    $ms = New-Object System.IO.MemoryStream
    $bw = New-Object System.IO.BinaryWriter $ms

    # ICONDIR
    $bw.Write([uint16]0)                # Reserved
    $bw.Write([uint16]1)                # Type = 1 (Icon)
    $bw.Write([uint16]$sizes.Length)    # Count

    $pngList = @()
    foreach ($s in $sizes) {
        $bmp = RenderIconBitmap $s $false
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

        $bw.Write($wByte)                       # Width
        $bw.Write($hByte)                       # Height
        $bw.Write([byte]0)                      # Color count
        $bw.Write([byte]0)                      # Reserved
        $bw.Write([uint16]1)                    # Planes
        $bw.Write([uint16]32)                   # BitCount
        $bw.Write([uint32]$pngData.Length)      # BytesInRes
        $bw.Write([uint32]$offset)              # ImageOffset

        $offset += $pngData.Length
    }

    foreach ($pngData in $pngList) {
        $bw.Write($pngData)
    }

    $bw.Flush()
    [System.IO.File]::WriteAllBytes($outPath, $ms.ToArray())
    $bw.Dispose()
    $ms.Dispose()
    Write-Host "Created ICO: $outPath ($($sizes -join ', ') px)"
}

# --- 执行生成 ---
$resolvedRoot = (Resolve-Path $ProjectRoot).Path

# 1. Desktop SVG
$svgContent = @'
<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 256 256" width="256" height="256">
  <defs>
    <linearGradient id="bg_grad" x1="0%" y1="0%" x2="0%" y2="100%">
      <stop offset="0%" stop-color="#E3F0FF" />
      <stop offset="100%" stop-color="#C7E1FF" />
    </linearGradient>
  </defs>

  <!-- Material 3 Rounded Squircle Background -->
  <rect x="12" y="12" width="232" height="232" rx="54" fill="url(#bg_grad)" />

  <!-- Material 3 Soft Drop Shadow -->
  <path fill="#1F00223D" d="M 73,86 C 73,77.7157 79.7157,71 88,71 C 96.2843,71 103,77.7157 103,86 L 103,106 C 103,111.5228 107.4772,116 113,116 L 143,116 C 148.5228,116 153,111.5228 153,106 L 153,86 C 153,77.7157 159.7157,71 168,71 C 176.2843,71 183,77.7157 183,86 L 183,176 C 183,184.2843 176.2843,191 168,191 C 159.7157,191 153,184.2843 153,176 L 153,156 C 153,150.4772 148.5228,146 143,146 L 113,146 C 107.4772,146 103,150.4772 103,156 L 103,176 C 103,184.2843 96.2843,191 88,191 C 79.7157,191 73,184.2843 73,176 L 73,86 Z" />

  <!-- Material 3 Expressive Capsule H Primary Mark -->
  <path fill="#004B76" d="M 73,83 C 73,74.7157 79.7157,68 88,68 C 96.2843,68 103,74.7157 103,83 L 103,103 C 103,108.5228 107.4772,113 113,113 L 143,113 C 148.5228,113 153,108.5228 153,103 L 153,83 C 153,74.7157 159.7157,68 168,68 C 176.2843,68 183,74.7157 183,83 L 183,173 C 183,181.2843 176.2843,188 168,188 C 159.7157,188 153,181.2843 153,173 L 153,153 C 153,147.4772 148.5228,143 143,143 L 113,143 C 107.4772,143 103,147.4772 103,153 L 103,173 C 103,181.2843 96.2843,188 88,188 C 79.7157,188 73,181.2843 73,173 L 73,83 Z" />
</svg>
'@

$svgPath = Join-Path $resolvedRoot "desktopApp/icons/RePoi.svg"
[System.IO.File]::WriteAllText($svgPath, ($svgContent.TrimEnd() + "
"), $utf8NoBom)
Write-Host "Updated Desktop SVG: $svgPath"

# 2. Desktop ICO (multi-size)
$icoPath = Join-Path $resolvedRoot "desktopApp/icons/RePoi.ico"
SaveIcoFile $icoPath @(16, 24, 32, 48, 64, 128, 256)

# 3. Desktop resources PNG (for in-app window icon)
$resDir = Join-Path $resolvedRoot "desktopApp/src/main/resources/icons"
if (-not (Test-Path $resDir)) { [System.IO.Directory]::CreateDirectory($resDir) | Out-Null }
$resPng = RenderIconBitmap 256 $false
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
    $squareBmp = RenderIconBitmap $dim $false
    $squarePath = Join-Path $dir "ic_launcher.png"
    $squareBmp.Save($squarePath, [System.Drawing.Imaging.ImageFormat]::Png)
    $squareBmp.Dispose()

    # Round launcher
    $roundBmp = RenderIconBitmap $dim $true
    $roundPath = Join-Path $dir "ic_launcher_round.png"
    $roundBmp.Save($roundPath, [System.Drawing.Imaging.ImageFormat]::Png)
    $roundBmp.Dispose()

    Write-Host "Updated Android $folder : ${dim}x${dim} (regular & round)"
}

Write-Host "All icons generated successfully!"
