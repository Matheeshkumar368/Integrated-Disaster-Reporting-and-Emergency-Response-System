Add-Type -AssemblyName System.Drawing

$srcPath = Join-Path $PSScriptRoot "..\desktop\assets\icon.png"
$dstPng256 = Join-Path $PSScriptRoot "..\desktop\assets\icon-256.png"
$dstIco = Join-Path $PSScriptRoot "..\desktop\assets\icon.ico"

Write-Host "Loading: $srcPath"
$src = [System.Drawing.Image]::FromFile($srcPath)
$bmp256 = New-Object System.Drawing.Bitmap 256, 256
$g = [System.Drawing.Graphics]::FromImage($bmp256)
$g.InterpolationMode = [System.Drawing.Drawing2D.InterpolationMode]::HighQualityBicubic
$g.SmoothingMode = [System.Drawing.Drawing2D.SmoothingMode]::AntiAlias
$g.DrawImage($src, 0, 0, 256, 256)
$bmp256.Save($dstPng256, [System.Drawing.Imaging.ImageFormat]::Png)
$g.Dispose()
$bmp256.Dispose()
$src.Dispose()
Write-Host "Generated: $dstPng256"

# Create standard 256x256 PNG-wrapped Windows ICO file
$pngBytes = [System.IO.File]::ReadAllBytes($dstPng256)
$ms = New-Object System.IO.MemoryStream
$bw = New-Object System.IO.BinaryWriter $ms

# ICONDIR header (6 bytes)
$bw.Write([uint16]0) # Reserved
$bw.Write([uint16]1) # Type (1 = ICO)
$bw.Write([uint16]1) # Count (1 image)

# ICONDIRENTRY (16 bytes)
$bw.Write([byte]0)   # Width (0 = 256)
$bw.Write([byte]0)   # Height (0 = 256)
$bw.Write([byte]0)   # Colors
$bw.Write([byte]0)   # Reserved
$bw.Write([uint16]1) # Planes
$bw.Write([uint16]32)# Bit count
$bw.Write([uint32]$pngBytes.Length) # Image size in bytes
$bw.Write([uint32]22)# Image offset (6 + 16 = 22)

# Write PNG bytes directly
$bw.Write($pngBytes)
$bw.Flush()

[System.IO.File]::WriteAllBytes($dstIco, $ms.ToArray())
$bw.Dispose()
$ms.Dispose()

Write-Host "Generated valid 256x256 Windows ICO: $dstIco"

