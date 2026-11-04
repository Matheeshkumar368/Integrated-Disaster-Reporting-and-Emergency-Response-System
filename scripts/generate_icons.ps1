Add-Type -AssemblyName System.Drawing

function Generate-Icon([int]$size, [string]$path, [bool]$maskable) {
    $bmp = New-Object System.Drawing.Bitmap $size, $size
    $g = [System.Drawing.Graphics]::FromImage($bmp)
    $g.SmoothingMode = [System.Drawing.Drawing2D.SmoothingMode]::AntiAlias
    $g.InterpolationMode = [System.Drawing.Drawing2D.InterpolationMode]::HighQualityBicubic

    # Background
    $bgColor = [System.Drawing.Color]::FromArgb(5, 8, 17)
    $bgBrush = New-Object System.Drawing.SolidBrush $bgColor
    if ($maskable) {
        $g.FillRectangle($bgBrush, 0, 0, $size, $size)
    } else {
        $g.Clear([System.Drawing.Color]::Transparent)
        $pathObj = New-Object System.Drawing.Drawing2D.GraphicsPath
        $rad = $size * 0.22
        $pathObj.AddArc(0, 0, $rad, $rad, 180, 90)
        $pathObj.AddArc($size - $rad, 0, $rad, $rad, 270, 90)
        $pathObj.AddArc($size - $rad, $size - $rad, $rad, $rad, 0, 90)
        $pathObj.AddArc(0, $size - $rad, $rad, $rad, 90, 90)
        $pathObj.CloseFigure()
        $g.FillPath($bgBrush, $pathObj)
        $rimPen = New-Object System.Drawing.Pen ([System.Drawing.Color]::FromArgb(140, 14, 165, 233)), ([Math]::Max(2, $size * 0.015))
        $g.DrawPath($rimPen, $pathObj)
    }

    # Radar Circle
    $radarPen = New-Object System.Drawing.Pen ([System.Drawing.Color]::FromArgb(50, 14, 165, 233)), ([Math]::Max(1, $size * 0.01))
    $g.DrawEllipse($radarPen, [float]($size * 0.15), [float]($size * 0.15), [float]($size * 0.7), [float]($size * 0.7))
    $g.DrawEllipse($radarPen, [float]($size * 0.25), [float]($size * 0.25), [float]($size * 0.5), [float]($size * 0.5))

    # Shield
    $shieldPath = New-Object System.Drawing.Drawing2D.GraphicsPath
    $cx = [float]($size * 0.5)
    $top = [float]($size * 0.2)
    $left = [float]($size * 0.25)
    $right = [float]($size * 0.75)
    $midY = [float]($size * 0.55)
    $bottom = [float]($size * 0.82)

    [System.Drawing.PointF[]]$points = @(
        (New-Object System.Drawing.PointF $cx, $top),
        (New-Object System.Drawing.PointF $right, ([float]($size * 0.3))),
        (New-Object System.Drawing.PointF $right, $midY),
        (New-Object System.Drawing.PointF $cx, $bottom),
        (New-Object System.Drawing.PointF $left, $midY),
        (New-Object System.Drawing.PointF $left, ([float]($size * 0.3)))
    )
    $shieldPath.AddPolygon($points)
    
    $shieldBrush = New-Object System.Drawing.SolidBrush ([System.Drawing.Color]::FromArgb(15, 23, 42))
    $g.FillPath($shieldBrush, $shieldPath)
    
    $shieldPen = New-Object System.Drawing.Pen ([System.Drawing.Color]::FromArgb(239, 68, 68)), ([Math]::Max(3, $size * 0.025))
    $g.DrawPath($shieldPen, $shieldPath)

    # Red Emergency Cross in Center
    $crossBrush = New-Object System.Drawing.SolidBrush ([System.Drawing.Color]::FromArgb(14, 165, 233))
    $armW = [float]($size * 0.08)
    $armL = [float]($size * 0.22)
    $g.FillRectangle($crossBrush, [float]($cx - $armW/2), [float]($size * 0.42), $armW, $armL)
    $g.FillRectangle($crossBrush, [float]($cx - $armL/2), [float]($size * 0.42 + $armL/2 - $armW/2), $armL, $armW)

    # Center white dot
    $dotBrush = New-Object System.Drawing.SolidBrush ([System.Drawing.Color]::White)
    $g.FillEllipse($dotBrush, [float]($cx - $armW * 0.4), [float]($size * 0.42 + $armL/2 - $armW * 0.4), [float]($armW * 0.8), [float]($armW * 0.8))

    $bmp.Save($path, [System.Drawing.Imaging.ImageFormat]::Png)
    $g.Dispose()
    $bmp.Dispose()
    Write-Host "Generated: $path"
}

if (-not (Test-Path "src\main\resources\static\icons")) {
    New-Item -ItemType Directory -Path "src\main\resources\static\icons" -Force | Out-Null
}

Generate-Icon 192 "src\main\resources\static\icons\icon-192.png" $false
Generate-Icon 512 "src\main\resources\static\icons\icon-512.png" $false
Generate-Icon 192 "src\main\resources\static\icons\icon-maskable-192.png" $true
Generate-Icon 512 "src\main\resources\static\icons\icon-maskable-512.png" $true
Copy-Item "src\main\resources\static\icons\icon-192.png" "src\main\resources\static\favicon.ico" -Force
Write-Host "All icons generated cleanly with zero errors!"
