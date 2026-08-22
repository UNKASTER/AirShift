param(
    [datetime]$RosterDate = (Get-Date),
    [string]$OutputPath = (Join-Path $PSScriptRoot '..\testdata\synthetic_roster.png')
)

Add-Type -AssemblyName System.Drawing

$width = 2400
$height = 1100
$tableLeft = [int]($width * 0.032)
$tableWidth = [int]($width * 0.56)
$tableRight = $tableLeft + $tableWidth
$tableTop = 230
$headerHeight = 90
$rowHeight = 115
$columnEdges = @(0.000, 0.090, 0.160, 0.293, 0.394, 0.469, 0.640, 0.723, 0.778, 1.000)
$headers = @('机号', '机型', '进港航班', '前站', '预落', '出港航班', '到站', '计离', '接送机人员')
$rows = @(
    @('B0001', '32N', 'ZZ1001', '上海虹桥', '1340', 'ZZ1002', '北京大兴', '1520', 'TESTUSER TEAM1'),
    @('B0002', '73V', '', '', '', 'QZ4001', '广州白云', '1600', 'TESTUSER'),
    @('B0003', '321', 'ZZ3001', '成都天府', '1720', 'ZZ3002', '深圳宝安', '1900', 'OTHER TEAM1'),
    @('B0004', '32N', 'ZZ4001', '杭州萧山', '1800', '', '', '', 'OTHER'),
    @('B0005', '320', '', '', '', 'ZZ5001', '西安咸阳', '2030', 'SOMEONE'),
    @('B0006', '32N', 'ZZ6001', '重庆江北', '2350', 'ZZ6002', '北京大兴', '0030+', 'TESTUSER')
)
$supplementLines = @(
    @('要客：今日暂无要客', 230),
    @('候机室卫生：测试甲 测试乙', 345),
    @('整理单据 对讲机充电 桌面、地面卫生', 400),
    @('候机早班：早班甲 早班乙4', 500),
    @('候机中班：中班甲 中班乙5', 560),
    @('候机夜航：夜航甲 夜航乙4', 620),
    @('值班主任：主任甲 主任乙', 760),
    @('病假：病假甲', 875)
)

$bitmap = [System.Drawing.Bitmap]::new($width, $height)
$graphics = [System.Drawing.Graphics]::FromImage($bitmap)
$graphics.SmoothingMode = [System.Drawing.Drawing2D.SmoothingMode]::AntiAlias
$graphics.TextRenderingHint = [System.Drawing.Text.TextRenderingHint]::AntiAliasGridFit
$graphics.Clear([System.Drawing.Color]::White)

$fontFamily = [System.Drawing.FontFamily]::new('Microsoft YaHei UI')
$titleFont = [System.Drawing.Font]::new($fontFamily, 44, [System.Drawing.FontStyle]::Bold)
$subtitleFont = [System.Drawing.Font]::new($fontFamily, 24, [System.Drawing.FontStyle]::Regular)
$gridPen = [System.Drawing.Pen]::new([System.Drawing.Color]::FromArgb(70, 87, 103), 2)
$headerBrush = [System.Drawing.SolidBrush]::new([System.Drawing.Color]::FromArgb(219, 234, 254))
$alternateBrush = [System.Drawing.SolidBrush]::new([System.Drawing.Color]::FromArgb(248, 250, 252))
$textBrush = [System.Drawing.SolidBrush]::new([System.Drawing.Color]::FromArgb(15, 23, 42))
$mutedBrush = [System.Drawing.SolidBrush]::new([System.Drawing.Color]::FromArgb(71, 85, 105))
$centerFormat = [System.Drawing.StringFormat]::new()
$centerFormat.Alignment = [System.Drawing.StringAlignment]::Center
$centerFormat.LineAlignment = [System.Drawing.StringAlignment]::Center

function Draw-FittedText {
    param(
        [string]$Text,
        [System.Drawing.RectangleF]$Bounds,
        [bool]$Bold = $false
    )

    if ([string]::IsNullOrEmpty($Text)) { return }
    $style = if ($Bold) { [System.Drawing.FontStyle]::Bold } else { [System.Drawing.FontStyle]::Regular }
    $fontSize = 27
    while ($true) {
        $font = [System.Drawing.Font]::new($fontFamily, $fontSize, $style)
        $measured = $graphics.MeasureString($Text, $font)
        $fits = $measured.Width -le ($Bounds.Width - 12) -and $measured.Height -le ($Bounds.Height - 8)
        if ($fits -or $fontSize -le 12) { break }
        $font.Dispose()
        $fontSize -= 1
    }

    $graphics.DrawString($Text, $font, $textBrush, $Bounds, $centerFormat)
    $font.Dispose()
}

try {
    $titleBounds = [System.Drawing.RectangleF]::new($tableLeft, 35, $tableWidth, 70)
    $graphics.DrawString('航班保障排班表', $titleFont, $textBrush, $titleBounds, $centerFormat)

    $dateText = '{0}.{1}  固定模板 · SYNTHETIC TEST DATA' -f $RosterDate.Month, $RosterDate.Day
    $dateBounds = [System.Drawing.RectangleF]::new($tableLeft, 115, $tableWidth, 55)
    $graphics.DrawString($dateText, $subtitleFont, $mutedBrush, $dateBounds, $centerFormat)

    $graphics.FillRectangle($headerBrush, $tableLeft, $tableTop, $tableWidth, $headerHeight)
    for ($rowIndex = 0; $rowIndex -lt $rows.Count; $rowIndex++) {
        if ($rowIndex % 2 -eq 1) {
            $y = $tableTop + $headerHeight + ($rowIndex * $rowHeight)
            $graphics.FillRectangle($alternateBrush, $tableLeft, $y, $tableWidth, $rowHeight)
        }
    }

    $tableBottom = $tableTop + $headerHeight + ($rows.Count * $rowHeight)
    for ($edgeIndex = 0; $edgeIndex -lt $columnEdges.Count; $edgeIndex++) {
        $x = $tableLeft + [int]($tableWidth * $columnEdges[$edgeIndex])
        $graphics.DrawLine($gridPen, $x, $tableTop, $x, $tableBottom)
    }
    $graphics.DrawLine($gridPen, $tableLeft, $tableTop, $tableRight, $tableTop)
    $graphics.DrawLine($gridPen, $tableLeft, $tableTop + $headerHeight, $tableRight, $tableTop + $headerHeight)
    for ($rowIndex = 1; $rowIndex -le $rows.Count; $rowIndex++) {
        $y = $tableTop + $headerHeight + ($rowIndex * $rowHeight)
        $graphics.DrawLine($gridPen, $tableLeft, $y, $tableRight, $y)
    }

    for ($columnIndex = 0; $columnIndex -lt $headers.Count; $columnIndex++) {
        $left = $tableLeft + [int]($tableWidth * $columnEdges[$columnIndex])
        $right = $tableLeft + [int]($tableWidth * $columnEdges[$columnIndex + 1])
        $bounds = [System.Drawing.RectangleF]::new($left, $tableTop, $right - $left, $headerHeight)
        Draw-FittedText -Text $headers[$columnIndex] -Bounds $bounds -Bold $true
    }

    for ($rowIndex = 0; $rowIndex -lt $rows.Count; $rowIndex++) {
        for ($columnIndex = 0; $columnIndex -lt $headers.Count; $columnIndex++) {
            $left = $tableLeft + [int]($tableWidth * $columnEdges[$columnIndex])
            $right = $tableLeft + [int]($tableWidth * $columnEdges[$columnIndex + 1])
            $top = $tableTop + $headerHeight + ($rowIndex * $rowHeight)
            $bounds = [System.Drawing.RectangleF]::new($left, $top, $right - $left, $rowHeight)
            Draw-FittedText -Text $rows[$rowIndex][$columnIndex] -Bounds $bounds
        }
    }

    $supplementLeft = $tableRight + 35
    $supplementWidth = $width - $supplementLeft - 35
    foreach ($line in $supplementLines) {
        $bounds = [System.Drawing.RectangleF]::new($supplementLeft, $line[1], $supplementWidth, 50)
        Draw-FittedText -Text $line[0] -Bounds $bounds
    }

    $outputFile = [System.IO.Path]::GetFullPath($OutputPath)
    [System.IO.Directory]::CreateDirectory([System.IO.Path]::GetDirectoryName($outputFile)) | Out-Null
    $bitmap.Save($outputFile, [System.Drawing.Imaging.ImageFormat]::Png)
    Write-Output $outputFile
} finally {
    $centerFormat.Dispose()
    $mutedBrush.Dispose()
    $textBrush.Dispose()
    $alternateBrush.Dispose()
    $headerBrush.Dispose()
    $gridPen.Dispose()
    $subtitleFont.Dispose()
    $titleFont.Dispose()
    $fontFamily.Dispose()
    $graphics.Dispose()
    $bitmap.Dispose()
}
