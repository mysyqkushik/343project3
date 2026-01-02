# GreengrocerApp - Automated Deliverables Preparation Script
# Run this script from PowerShell in the GreengrocerApp directory

Write-Host "========================================" -ForegroundColor Cyan
Write-Host "GreengrocerApp Deliverables Preparation" -ForegroundColor Cyan
Write-Host "========================================" -ForegroundColor Cyan
Write-Host ""

# Step 1: Clean and Build
Write-Host "[1/6] Building project..." -ForegroundColor Yellow
mvn clean package -DskipTests
if ($LASTEXITCODE -ne 0) {
    Write-Host "Build failed! Please fix errors before continuing." -ForegroundColor Red
    exit 1
}
Write-Host "Build successful!" -ForegroundColor Green
Write-Host ""

# Step 2: Generate JavaDoc
Write-Host "[2/6] Generating JavaDoc..." -ForegroundColor Yellow
mvn javadoc:javadoc
if ($LASTEXITCODE -ne 0) {
    Write-Host "JavaDoc generation failed!" -ForegroundColor Red
    exit 1
}
Write-Host "JavaDoc generated in target/site/apidocs/" -ForegroundColor Green
Write-Host ""

# Step 3: Create deliverables directory
Write-Host "[3/6] Creating deliverables directory..." -ForegroundColor Yellow
$deliverables = "deliverables"
if (!(Test-Path $deliverables)) {
    New-Item -ItemType Directory -Path $deliverables | Out-Null
}
Write-Host "Created $deliverables/ directory" -ForegroundColor Green
Write-Host ""

# Step 4: Package Source Code
Write-Host "[4/6] Packaging source code..." -ForegroundColor Yellow
$sourceZip = "$deliverables/GroupSourceXX.zip"
if (Test-Path $sourceZip) { Remove-Item $sourceZip }
Compress-Archive -Path "src/*" -DestinationPath $sourceZip
Write-Host "Created $sourceZip" -ForegroundColor Green
Write-Host ""

# Step 5: Package JavaDoc
Write-Host "[5/6] Packaging JavaDoc..." -ForegroundColor Yellow
$docZip = "$deliverables/GroupDocXX.zip"
if (Test-Path $docZip) { Remove-Item $docZip }
if (Test-Path "target/site/apidocs") {
    Compress-Archive -Path "target/site/apidocs/*" -DestinationPath $docZip
    Write-Host "Created $docZip" -ForegroundColor Green
} else {
    Write-Host "JavaDoc directory not found!" -ForegroundColor Red
}
Write-Host ""

# Step 6: Package FXML files
Write-Host "[6/6] Packaging FXML files..." -ForegroundColor Yellow
$fxmlZip = "$deliverables/GroupFxmlXX.zip"
if (Test-Path $fxmlZip) { Remove-Item $fxmlZip }
Compress-Archive -Path "src/main/resources/fxml/*" -DestinationPath $fxmlZip
Write-Host "Created $fxmlZip" -ForegroundColor Green
Write-Host ""

# Summary
Write-Host "========================================" -ForegroundColor Cyan
Write-Host "Preparation Complete!" -ForegroundColor Cyan
Write-Host "========================================" -ForegroundColor Cyan
Write-Host ""
Write-Host "Files created in deliverables/ directory:" -ForegroundColor Green
Write-Host "  - GroupSourceXX.zip" -ForegroundColor White
Write-Host "  - GroupDocXX.zip" -ForegroundColor White
Write-Host "  - GroupFxmlXX.zip" -ForegroundColor White
Write-Host ""
Write-Host "MANUAL STEPS REQUIRED:" -ForegroundColor Yellow
Write-Host "  1. Export database:" -ForegroundColor White
Write-Host "     mysqldump -u myuser -p1234 greengrocer > deliverables/GroupXX.sql" -ForegroundColor Cyan
Write-Host ""
Write-Host "  2. Record demo video (max 8 minutes)" -ForegroundColor White
Write-Host "     Save as: deliverables/GroupXX.mp4" -ForegroundColor Cyan
Write-Host ""
Write-Host "  3. Create peer scoring file:" -ForegroundColor White
Write-Host "     Create: deliverables/GroupXX.txt" -ForegroundColor Cyan
Write-Host ""
Write-Host "  4. Copy intro.txt:" -ForegroundColor White
Write-Host "     Copy intro.txt to deliverables/" -ForegroundColor Cyan
Write-Host ""
Write-Host "  5. Package images (if any separate image files):" -ForegroundColor White
Write-Host "     Create: deliverables/GroupImagesXX.zip" -ForegroundColor Cyan
Write-Host ""
Write-Host "  6. Create final GroupXX.zip containing all deliverables" -ForegroundColor White
Write-Host ""
