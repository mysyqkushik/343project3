# Test Application Script
# This script will help you verify the application works correctly

Write-Host "========================================" -ForegroundColor Cyan
Write-Host "GreengrocerApp Testing Guide" -ForegroundColor Cyan
Write-Host "========================================" -ForegroundColor Cyan
Write-Host ""

# Check prerequisites
Write-Host "Checking prerequisites..." -ForegroundColor Yellow
Write-Host ""

# Check Java
Write-Host "Java version:" -ForegroundColor White
java -version 2>&1 | Select-Object -First 1
Write-Host ""

# Check Maven
Write-Host "Maven version:" -ForegroundColor White
mvn -version 2>&1 | Select-Object -First 1
Write-Host ""

# Check MySQL
Write-Host "Testing MySQL connection..." -ForegroundColor White
$mysqlTest = mysql -u myuser -p1234 -e "SELECT 1;" 2>&1
if ($LASTEXITCODE -eq 0) {
    Write-Host "✅ MySQL connection successful" -ForegroundColor Green
} else {
    Write-Host "❌ MySQL connection failed!" -ForegroundColor Red
    Write-Host "Make sure MySQL is running and user 'myuser' exists with password '1234'" -ForegroundColor Yellow
}
Write-Host ""

# Check database
Write-Host "Checking database 'greengrocer'..." -ForegroundColor White
$dbTest = mysql -u myuser -p1234 -e "USE greengrocer; SELECT COUNT(*) FROM product_info;" 2>&1
if ($LASTEXITCODE -eq 0) {
    Write-Host "✅ Database 'greengrocer' accessible" -ForegroundColor Green
    Write-Host "Product count in database:" -ForegroundColor White
    echo $dbTest
} else {
    Write-Host "⚠️  Database may not exist or tables not created" -ForegroundColor Yellow
    Write-Host "This is normal for first run - the app will create tables automatically" -ForegroundColor Cyan
}
Write-Host ""

Write-Host "========================================" -ForegroundColor Cyan
Write-Host "Ready to test!" -ForegroundColor Cyan
Write-Host "========================================" -ForegroundColor Cyan
Write-Host ""
Write-Host "To start the application, run:" -ForegroundColor Yellow
Write-Host "  mvn javafx:run" -ForegroundColor Cyan
Write-Host ""
Write-Host "Test users:" -ForegroundColor Yellow
Write-Host "  Customer: cust / cust" -ForegroundColor White
Write-Host "  Carrier:  carr / carr" -ForegroundColor White
Write-Host "  Owner:    own  / own" -ForegroundColor White
Write-Host ""
Write-Host "Manual testing checklist:" -ForegroundColor Yellow
Write-Host "  1. Login with each user role" -ForegroundColor White
Write-Host "  2. Test customer: browse, cart, checkout" -ForegroundColor White
Write-Host "  3. Test carrier: claim orders, mark delivered" -ForegroundColor White
Write-Host "  4. Test owner: manage products, carriers, messages" -ForegroundColor White
Write-Host "  5. Test error cases: invalid inputs, stock limits" -ForegroundColor White
Write-Host ""
Write-Host "Press any key to launch the application..." -ForegroundColor Green
$null = $Host.UI.RawUI.ReadKey("NoEcho,IncludeKeyDown")

Write-Host ""
Write-Host "Starting application..." -ForegroundColor Yellow
mvn javafx:run
