<#
.SYNOPSIS
  DEV ONLY: fills a FitCheck account with sample clothing items so you have something to look at.

.DESCRIPTION
  Logs in (or registers the account if it doesn't exist), then uploads simple generated images
  through the real API (POST /api/clothing), exactly like the app would.
  Needs the backend running on http://localhost:8080 and docker compose up (Postgres + Azurite).

.EXAMPLE
  .\scripts\seed-wardrobe.ps1
  .\scripts\seed-wardrobe.ps1 -Email you@example.com -Password yourpassword
#>
param(
    [string]$Email = "demo@example.com",
    [string]$Password = "supersecret",
    [string]$Api = "http://localhost:8080/api"
)

$ErrorActionPreference = "Stop"
Add-Type -AssemblyName System.Drawing

function Invoke-Json($Method, $Url, $Body, $Token) {
    $headers = @{}
    if ($Token) { $headers.Authorization = "Bearer $Token" }
    Invoke-RestMethod -Method $Method -Uri $Url -Headers $headers -ContentType "application/json" `
        -Body ($Body | ConvertTo-Json -Compress)
}

# 1. Log in, or create the account the first time.
$credentials = @{ email = $Email; password = $Password }
try {
    $auth = Invoke-Json POST "$Api/auth/login" $credentials
    Write-Host "Logged in as $Email"
} catch {
    $auth = Invoke-Json POST "$Api/auth/register" $credentials
    Write-Host "Created account $Email (password: $Password)"
}
$token = $auth.accessToken

# 2. Sample items: name, category, color name, RGB for the picture, tags.
$samples = @(
    @("White tee", "TOP", "white", @(245, 245, 245), @("casual", "basics")),
    @("Striped shirt", "TOP", "navy", @(30, 50, 110), @("smart casual")),
    @("Blue jeans", "BOTTOM", "blue", @(60, 100, 170), @("casual", "denim")),
    @("Black trousers", "BOTTOM", "black", @(25, 25, 25), @("work")),
    @("Floral dress", "DRESS", "pink", @(230, 140, 170), @("summer")),
    @("Trench coat", "OUTERWEAR", "beige", @(205, 180, 140), @("rain", "classic")),
    @("White sneakers", "SHOES", "white", @(235, 235, 230), @("casual")),
    @("Leather boots", "SHOES", "brown", @(110, 70, 40), @("winter")),
    @("Silver necklace", "ACCESSORY", "silver", @(190, 190, 200), @("evening"))
)

$tmp = Join-Path $env:TEMP "fitcheck-seed"
New-Item -ItemType Directory -Force $tmp | Out-Null

foreach ($s in $samples) {
    $name, $category, $color, $rgb, $tags = $s

    # A 300x400 picture: background in the item's color with its name written on it.
    $file = Join-Path $tmp "item.png"
    $bmp = New-Object System.Drawing.Bitmap 300, 400
    $g = [System.Drawing.Graphics]::FromImage($bmp)
    $g.Clear([System.Drawing.Color]::FromArgb($rgb[0], $rgb[1], $rgb[2]))
    $dark = ($rgb[0] + $rgb[1] + $rgb[2]) -lt 380
    $brush = if ($dark) { [System.Drawing.Brushes]::White } else { [System.Drawing.Brushes]::Black }
    $font = New-Object System.Drawing.Font "Segoe UI", 20
    $g.DrawString($name, $font, $brush, 20, 170)
    $g.Dispose(); $bmp.Save($file, [System.Drawing.Imaging.ImageFormat]::Png); $bmp.Dispose()

    $meta = Join-Path $tmp "meta.json"
    @{ name = $name; category = $category; color = $color; tags = $tags } | ConvertTo-Json -Compress |
        Set-Content -Encoding ascii $meta

    $code = curl.exe -s -o NUL -w "%{http_code}" -X POST "$Api/clothing" -H "Authorization: Bearer $token" `
        -F "image=@$file;type=image/png" -F "metadata=<$meta;type=application/json"
    Write-Host ("  {0,-16} {1,-10} -> HTTP {2}" -f $name, $category, $code)
}

Write-Host "Done. Log in at http://localhost:5173 as $Email / $Password"
