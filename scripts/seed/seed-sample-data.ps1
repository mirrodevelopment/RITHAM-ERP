<#
.SYNOPSIS
    Seeds 10 sample customers with 1 to 5 orders each (30 orders total) plus all
    related measurements, payment ledger entries, branch assignments, and stage transitions
    via the running Ritham ERP REST API.

.DESCRIPTION
    Idempotent by customer mobile number (`9840100001` - `9840100010`). Safe to re-run:
    existing customers with orders are skipped without creating duplicates.
#>
[CmdletBinding()]
param(
    [string]$BaseUrl = "http://localhost:8080",
    [string]$Username = "admin",
    [string]$Password = "Admin@123"
)

$ErrorActionPreference = "Stop"
[Console]::OutputEncoding = [System.Text.UTF8Encoding]::new()

function Invoke-ErpApi {
    param(
        [Parameter(Mandatory)][string]$Method,
        [Parameter(Mandatory)][string]$Path,
        [object]$Body = $null,
        [string]$Token = ""
    )

    $uri = "$($BaseUrl.TrimEnd('/'))$Path"
    $headers = @{ "Accept" = "application/json" }
    if ($Token) {
        $headers["Authorization"] = "Bearer $Token"
    }

    $params = @{
        Method      = $Method
        Uri         = $uri
        Headers     = $headers
        ContentType = "application/json; charset=utf-8"
        TimeoutSec  = 30
    }
    $jsonBody = $null
    if ($null -ne $Body) {
        $jsonBody = ($Body | ConvertTo-Json -Depth 10 -Compress)
        $params["Body"] = [System.Text.Encoding]::UTF8.GetBytes($jsonBody)
    }

    try {
        return Invoke-RestMethod @params
    } catch {
        $errDetail = $_.Exception.Message
        if ($_.Exception.Response) {
            try {
                $reader = New-Object System.IO.StreamReader($_.Exception.Response.GetResponseStream())
                $errDetail = $reader.ReadToEnd()
                $reader.Dispose()
            } catch {}
        }
        throw "API $Method $Path failed: $errDetail"
    }
}

Write-Host "Logging in to $BaseUrl as '$Username'..." -ForegroundColor Cyan
$loginResp = Invoke-ErpApi -Method POST -Path "/api/auth/login" -Body @{
    username = $Username
    password = $Password
}
$token = $loginResp.data.accessToken
if (-not $token) {
    throw "Authentication failed: accessToken was not returned by /api/auth/login."
}

# ── 1. Ensure Branches Exist ──────────────────────────────────────────────────
$branchesResp = Invoke-ErpApi -Method GET -Path "/api/branches" -Token $token
$branches = @($branchesResp.data)

if ($branches.Count -eq 0) {
    Write-Host "No branches found. Creating MN (Ritham Main Studio) branch..." -ForegroundColor Yellow
    $b1 = Invoke-ErpApi -Method POST -Path "/api/branches" -Token $token -Body @{
        branchCode = "MN"
        name       = "Ritham Main Studio"
        city       = "Coimbatore"
        address    = "42 Race Course Road, Gopalapuram"
        phone      = "0422-2221100"
        email      = "main@rithamdesigns.com"
        gstNumber  = "33AABCR1234F1Z5"
    }
    $branches = @($b1.data)
}

$branch1Id = [long]$branches[0].id
$branch2Id = if ($branches.Count -gt 1) { [long]$branches[1].id } else { $branch1Id }
Write-Host ("Using branches: primary={0} (id={1}), secondary={2} (id={3})" -f $branches[0].branchCode, $branch1Id, $(if ($branches.Count -gt 1) { $branches[1].branchCode } else { $branches[0].branchCode }), $branch2Id) -ForegroundColor Green

# ── 2. Lookup Employee for Production Assignments ─────────────────────────────
$empResp = Invoke-ErpApi -Method GET -Path "/api/employees?page=0&size=20&sortBy=id&sortDir=asc" -Token $token
$employees = @($empResp.data.content)
$prodEmp = $employees | Where-Object { $_.role -match "PRODUCTION" } | Select-Object -First 1
if (-not $prodEmp) {
    $prodEmp = $employees | Select-Object -First 1
}
$assignedEmpId = if ($prodEmp) { [long]$prodEmp.id } else { $null }
$assignedEmpName = if ($prodEmp) { [string]$prodEmp.fullName } else { "Production Supervisor" }

# ── 3. Canonical Measurement Templates (matching order.html/measurement.html) ─
function Get-BlouseMeasurements {
    param([string]$Lth = "14.5", [string]$B2 = "36", [string]$B3 = "30", [string]$Lining = "Silk Cotton", [string]$Av = "Aari sleeve border")
    return @{
        "LTH"    = $Lth
        "SHO"    = "14.5"
        "H.S."   = "6.5"
        "H.L."   = "10.0"
        "H.LO"   = "11.0"
        "AK"     = "15.5"
        "AM"     = "12.0"
        "BN"     = "9.0"
        "FN"     = "6.5"
        "DP-1"   = "9.5"
        "DP-2"   = "11.0"
        "B-1"    = "34.0"
        "B-2"    = $B2
        "B-3"    = $B3
        "F.HOOK" = "Yes"
        "B.HOOK" = "Knot"
        "LINING" = $Lining
        "AV."    = $Av
        "SARI"   = "Falls & Pico"
    }
}

function Get-ChudiMeasurements {
    param([string]$Len = "42", [string]$Bust = "36", [string]$Hip = "38", [string]$Bottom = "Palazzo", [string]$Lining = "Full Lining")
    return @{
        "F.N."  = "6.0"
        "B.N."  = "7.0"
        "H.B."  = "34.0"
        "L"     = $Len
        "S.S."  = "18.0"
        "S.L."  = "15.0"
        "S.LO"  = "10.5"
        "AM"    = "13.0"
        "B"     = $Bust
        "H"     = $Hip
        "T.S."  = "21.0"
        "P.L."  = "38.0"
        "S."    = "24.0"
        "L."    = "12.0"
        "S.CUT" = "Straight"
        "LNG"   = $Lining
        "SHALL" = "Matching"
        "B.D."  = $Bottom
    }
}

$now = Get-Date
function Format-Dt {
    param([int]$OffsetDays, [int]$Hour = 18)
    return $now.Date.AddDays($OffsetDays).AddHours($Hour).ToString("yyyy-MM-dd'T'HH':'mm':'ss", [System.Globalization.CultureInfo]::InvariantCulture)
}

# ── 4. 10 Customers x (1 to 5 Orders each = 30 Orders Total) ─────────────────
$seedCustomers = @(
    @{
        mobile = "9840100001"; name = "Ananya Krishnan"
        profiles = @(
            @{ garment = "BLOUSE"; lining = "WITH_LINING"; notes = "Boat neck with gold kasavu piping"; measurements = (Get-BlouseMeasurements -Lth "14.5" -B2 "36" -B3 "30" -Lining "Pure Cotton" -Av "Heavy aari work") }
            @{ garment = "CHUDI";  lining = "WITH_LINING"; notes = "Umbrella flare top with ankle pant"; measurements = (Get-ChudiMeasurements -Len "44" -Bust "36" -Hip "38" -Bottom "Ankle Pant" -Lining "Full Crepe") }
        )
        orders = @(
            @{ branchId = $branch1Id; garment = "BLOUSE"; lining = "WITH_LINING";    delivery = (Format-Dt -2); total = 2400; advance = 1000; discount = 0;   payMode = "UPI";           extraPay = 0;   extraMode = $null;  status = "DELIVERED";         receiver = "Ananya Krishnan"; finalPay = 1400; finalMode = "UPI" }
            @{ branchId = $branch1Id; garment = "CHUDI";  lining = "WITH_LINING";    delivery = (Format-Dt -1); total = 1850; advance = 1000; discount = 150; payMode = "CASH";          extraPay = 0;   extraMode = $null;  status = "COMPLETED";         receiver = "Ananya Krishnan"; finalPay = 700;  finalMode = "CASH" }
            @{ branchId = $branch1Id; garment = "BLOUSE"; lining = "WITH_LINING";    delivery = (Format-Dt 2);  total = 1600; advance = 600;  discount = 0;   payMode = "UPI";           extraPay = 400; extraMode = "CARD"; status = "STITCHING" }
            @{ branchId = $branch1Id; garment = "BLOUSE"; lining = "WITHOUT_LINING"; delivery = (Format-Dt 4);  total = 850;  advance = 350;  discount = 0;   payMode = "CASH";          extraPay = 0;   extraMode = $null;  status = "CUTTING" }
            @{ branchId = $branch1Id; garment = "CHUDI";  lining = "WITHOUT_LINING"; delivery = (Format-Dt 6);  total = 1100; advance = 0;    discount = 0;   payMode = "CASH";          extraPay = 0;   extraMode = $null;  status = "PENDING" }
        )
    },
    @{
        mobile = "9840100002"; name = "Priya Subramanian"
        profiles = @(
            @{ garment = "BLOUSE"; lining = "WITH_LINING"; notes = "Deep pot back neck with tassels"; measurements = (Get-BlouseMeasurements -Lth "15.0" -B2 "38" -B3 "32" -Lining "Cotton Satin" -Av "Zardosi motif") }
            @{ garment = "CHUDI";  lining = "WITH_LINING"; notes = "Straight kurta with pocket"; measurements = (Get-ChudiMeasurements -Len "41" -Bust "38" -Hip "40" -Bottom "Straight Pant" -Lining "Cotton") }
        )
        orders = @(
            @{ branchId = $branch2Id; garment = "BLOUSE"; lining = "WITH_LINING";    delivery = (Format-Dt -3); total = 3200; advance = 1500; discount = 200; payMode = "CARD";          extraPay = 0;   extraMode = $null;  status = "DELIVERED";         receiver = "Priya Subramanian"; finalPay = 1500; finalMode = "CARD" }
            @{ branchId = $branch2Id; garment = "CHUDI";  lining = "WITH_LINING";    delivery = (Format-Dt -1); total = 1500; advance = 500;  discount = 0;   payMode = "UPI";           extraPay = 0;   extraMode = $null;  status = "QUALITY_CHECK" }
            @{ branchId = $branch2Id; garment = "BLOUSE"; lining = "WITH_LINING";    delivery = (Format-Dt 0);  total = 1900; advance = 900;  discount = 0;   payMode = "CASH";          extraPay = 0;   extraMode = $null;  status = "READY_TO_DELIVERY" }
            @{ branchId = $branch2Id; garment = "CHUDI";  lining = "WITHOUT_LINING"; delivery = (Format-Dt 5);  total = 950;  advance = 0;    discount = 0;   payMode = "UPI";           extraPay = 0;   extraMode = $null;  status = "DESIGNING" }
        )
    },
    @{
        mobile = "9840100003"; name = "Lakshmi Narayanan"
        profiles = @(
            @{ garment = "BLOUSE"; lining = "WITH_LINING"; notes = "Princess cut padded blouse"; measurements = (Get-BlouseMeasurements -Lth "14.0" -B2 "34" -B3 "28" -Lining "Heavy Mulmul" -Av "Mirror work cuffs") }
        )
        orders = @(
            @{ branchId = $branch1Id; garment = "BLOUSE"; lining = "WITH_LINING";    delivery = (Format-Dt -4); total = 2100; advance = 1000; discount = 100; payMode = "CASH";          extraPay = 0;   extraMode = $null;  status = "DELIVERED";         receiver = "Lakshmi Narayanan"; finalPay = 1000; finalMode = "UPI" }
            @{ branchId = $branch1Id; garment = "BLOUSE"; lining = "WITH_LINING";    delivery = (Format-Dt 1);  total = 1750; advance = 750;  discount = 0;   payMode = "BANK_TRANSFER"; extraPay = 0;   extraMode = $null;  status = "READY_TO_DELIVERY" }
            @{ branchId = $branch1Id; garment = "BLOUSE"; lining = "WITHOUT_LINING"; delivery = (Format-Dt 7);  total = 900;  advance = 0;    discount = 0;   payMode = "CASH";          extraPay = 0;   extraMode = $null;  status = "DESIGNING" }
        )
    },
    @{
        mobile = "9840100004"; name = "Meenakshi Sundaram"
        profiles = @(
            @{ garment = "CHUDI";  lining = "WITH_LINING"; notes = "Collar neck kurti with side pockets"; measurements = (Get-ChudiMeasurements -Len "43" -Bust "40" -Hip "42" -Bottom "Palazzo" -Lining "Pure Cotton") }
        )
        orders = @(
            @{ branchId = $branch2Id; garment = "CHUDI";  lining = "WITH_LINING";    delivery = (Format-Dt 0);  total = 1650; advance = 650;  discount = 0;   payMode = "UPI";           extraPay = 0;   extraMode = $null;  status = "QUALITY_CHECK" }
            @{ branchId = $branch2Id; garment = "CHUDI";  lining = "WITH_LINING";    delivery = (Format-Dt 2);  total = 1400; advance = 1400; discount = 0;   payMode = "CARD";          extraPay = 0;   extraMode = $null;  status = "FINAL_IRONING" }
        )
    },
    @{
        mobile = "9840100005"; name = "Kavitha Rajendran"
        profiles = @(
            @{ garment = "BLOUSE"; lining = "WITHOUT_LINING"; notes = "Daily wear katori cut cotton blouse"; measurements = (Get-BlouseMeasurements -Lth "13.5" -B2 "35" -B3 "29" -Lining "None" -Av "Plain piping") }
        )
        orders = @(
            @{ branchId = $branch1Id; garment = "BLOUSE"; lining = "WITHOUT_LINING"; delivery = (Format-Dt 4);  total = 750;  advance = 0;    discount = 0;   payMode = "CASH";          extraPay = 0;   extraMode = $null;  status = "PENDING" }
        )
    },
    @{
        mobile = "9840100006"; name = "Divya Bharathi"
        profiles = @(
            @{ garment = "BLOUSE"; lining = "WITH_LINING"; notes = "Bridal silk blouse with elbow sleeves"; measurements = (Get-BlouseMeasurements -Lth "15.0" -B2 "37" -B3 "31" -Lining "Shantoon Silk" -Av "Bridal maggam gold beadwork") }
            @{ garment = "CHUDI";  lining = "WITH_LINING"; notes = "Anarkali reception suit"; measurements = (Get-ChudiMeasurements -Len "48" -Bust "37" -Hip "39" -Bottom "Chudi" -Lining "Santoon Full") }
        )
        orders = @(
            @{ branchId = $branch1Id; garment = "BLOUSE"; lining = "WITH_LINING";    delivery = (Format-Dt -5); total = 4800; advance = 2000; discount = 300; payMode = "BANK_TRANSFER"; extraPay = 0;   extraMode = $null;  status = "COMPLETED";         receiver = "Divya Bharathi";    finalPay = 2500; finalMode = "UPI" }
            @{ branchId = $branch1Id; garment = "CHUDI";  lining = "WITH_LINING";    delivery = (Format-Dt 1);  total = 2600; advance = 1200; discount = 0;   payMode = "CARD";          extraPay = 0;   extraMode = $null;  status = "READY_TO_DELIVERY" }
            @{ branchId = $branch1Id; garment = "BLOUSE"; lining = "WITH_LINING";    delivery = (Format-Dt 3);  total = 2200; advance = 1000; discount = 0;   payMode = "UPI";           extraPay = 500; extraMode = "CASH"; status = "HEMMING" }
            @{ branchId = $branch2Id; garment = "CHUDI";  lining = "WITH_LINING";    delivery = (Format-Dt 6);  total = 1750; advance = 0;    discount = 0;   payMode = "CASH";          extraPay = 0;   extraMode = $null;  status = "LINING" }
            @{ branchId = $branch2Id; garment = "BLOUSE"; lining = "WITHOUT_LINING"; delivery = (Format-Dt 8);  total = 900;  advance = 0;    discount = 0;   payMode = "CASH";          extraPay = 0;   extraMode = $null;  status = "CANCELLED" }
        )
    },
    @{
        mobile = "9840100007"; name = "Sangeetha Venkat"
        profiles = @(
            @{ garment = "CHUDI";  lining = "WITH_LINING"; notes = "V-neck ethnic kurta with gathered sleeves"; measurements = (Get-ChudiMeasurements -Len "40" -Bust "34" -Hip "36" -Bottom "Cigarette Pant" -Lining "Voile Cotton") }
        )
        orders = @(
            @{ branchId = $branch2Id; garment = "CHUDI";  lining = "WITH_LINING";    delivery = (Format-Dt -2); total = 1550; advance = 750;  discount = 50;  payMode = "CARD";          extraPay = 0;   extraMode = $null;  status = "DELIVERED";         receiver = "Karthik Venkat";    finalPay = 750;  finalMode = "CARD" }
            @{ branchId = $branch2Id; garment = "CHUDI";  lining = "WITH_LINING";    delivery = (Format-Dt 3);  total = 2100; advance = 800;  discount = 0;   payMode = "UPI";           extraPay = 0;   extraMode = $null;  status = "HAND_MACHINE_WORK" }
            @{ branchId = $branch1Id; garment = "CHUDI";  lining = "WITHOUT_LINING"; delivery = (Format-Dt 5);  total = 1150; advance = 400;  discount = 0;   payMode = "CASH";          extraPay = 0;   extraMode = $null;  status = "STITCHING" }
        )
    },
    @{
        mobile = "9840100008"; name = "Revathi Balasubramani"
        profiles = @(
            @{ garment = "BLOUSE"; lining = "WITH_LINING"; notes = "High neck saree blouse with zip"; measurements = (Get-BlouseMeasurements -Lth "15.5" -B2 "39" -B3 "33" -Lining "Pure Silk Cotton" -Av "Pearl edging") }
        )
        orders = @(
            @{ branchId = $branch1Id; garment = "BLOUSE"; lining = "WITH_LINING";    delivery = (Format-Dt -3); total = 2800; advance = 1400; discount = 0;   payMode = "BANK_TRANSFER"; extraPay = 0;   extraMode = $null;  status = "DELIVERED";         receiver = "Revathi Balasubramani"; finalPay = 1400; finalMode = "BANK_TRANSFER" }
            @{ branchId = $branch1Id; garment = "BLOUSE"; lining = "WITH_LINING";    delivery = (Format-Dt 0);  total = 1950; advance = 1950; discount = 0;   payMode = "UPI";           extraPay = 0;   extraMode = $null;  status = "READY_TO_DELIVERY" }
            @{ branchId = $branch2Id; garment = "BLOUSE"; lining = "WITH_LINING";    delivery = (Format-Dt 2);  total = 1500; advance = 500;  discount = 0;   payMode = "CASH";          extraPay = 0;   extraMode = $null;  status = "STRETCHING" }
            @{ branchId = $branch2Id; garment = "BLOUSE"; lining = "WITH_LINING";    delivery = (Format-Dt 4);  total = 1700; advance = 700;  discount = 0;   payMode = "CARD";          extraPay = 0;   extraMode = $null;  status = "INITIAL_IRONING" }
        )
    },
    @{
        mobile = "9840100009"; name = "Nandhini Murugan"
        profiles = @(
            @{ garment = "CHUDI";  lining = "WITHOUT_LINING"; notes = "Casual cotton chudi set"; measurements = (Get-ChudiMeasurements -Len "39" -Bust "35" -Hip "37" -Bottom "Patiala" -Lining "No Lining") }
        )
        orders = @(
            @{ branchId = $branch1Id; garment = $null;    lining = $null;            delivery = (Format-Dt -1); total = 450;  advance = 200;  discount = 0;   payMode = "CASH";          extraPay = 0;   extraMode = $null;  status = "COMPLETED";         receiver = "Nandhini Murugan";  finalPay = 250;  finalMode = "CASH" }
            @{ branchId = $branch1Id; garment = "CHUDI";  lining = "WITHOUT_LINING"; delivery = (Format-Dt 3);  total = 1200; advance = 500;  discount = 0;   payMode = "UPI";           extraPay = 0;   extraMode = $null;  status = "CUTTING" }
        )
    },
    @{
        mobile = "9840100010"; name = "Deepika Senthil"
        profiles = @(
            @{ garment = "BLOUSE"; lining = "WITH_LINING"; notes = "Sweetheart neck silk cotton blouse"; measurements = (Get-BlouseMeasurements -Lth "14.2" -B2 "33" -B3 "27.5" -Lining "Cotton" -Av "Contrast thread piping") }
        )
        orders = @(
            @{ branchId = $branch2Id; garment = "BLOUSE"; lining = "WITH_LINING";    delivery = (Format-Dt 2);  total = 1650; advance = 650;  discount = 0;   payMode = "UPI";           extraPay = 0;   extraMode = $null;  status = "STITCHING" }
        )
    }
)

$createdCustomers = 0
$skippedCustomers = 0
$createdOrders = 0

foreach ($cust in $seedCustomers) {
    $mobile = $cust.mobile
    $existing = $null
    try {
        $resp = Invoke-ErpApi -Method GET -Path "/api/customers/$mobile" -Token $token
        $existing = $resp.data
    } catch {
        $existing = $null
    }

    if ($existing -and [int]$existing.totalOrders -gt 0) {
        Write-Host ("Skipping {0} ({1}) - already exists with {2} order(s)." -f $cust.name, $mobile, $existing.totalOrders) -ForegroundColor DarkGray
        $skippedCustomers++
        continue
    }

    if (-not $existing) {
        Invoke-ErpApi -Method POST -Path "/api/customers" -Token $token -Body @{
            customerMobile = $mobile
            customerName   = $cust.name
        } | Out-Null
        $createdCustomers++
        Write-Host ("Created customer: {0} ({1})" -f $cust.name, $mobile) -ForegroundColor Cyan
    }

    foreach ($prof in $cust.profiles) {
        Invoke-ErpApi -Method POST -Path "/api/customer-measurements" -Token $token -Body @{
            customerMobile = $mobile
            customerName   = $cust.name
            garmentType    = $prof.garment
            lining         = $prof.lining
            measurements   = $prof.measurements
            notes          = $prof.notes
        } | Out-Null
    }

    $profileByGarment = @{}
    foreach ($prof in $cust.profiles) {
        $profileByGarment[$prof.garment] = $prof.measurements
    }

    foreach ($ord in $cust.orders) {
        $orderBody = @{
            customerMobile = $mobile
            deliveryDate   = $ord.delivery
            totalAmount    = [decimal]$ord.total
            advanceAmount  = [decimal]$ord.advance
            discountAmount = 0
            paymentMode    = $ord.payMode
            branchId       = $ord.branchId
        }
        if ($ord.garment) {
            $orderBody["garmentType"]  = $ord.garment
            $orderBody["lining"]       = $ord.lining
            $orderBody["measurements"] = $profileByGarment[$ord.garment]
        }

        $createdOrderResp = Invoke-ErpApi -Method POST -Path "/api/orders" -Token $token -Body $orderBody
        $orderId = [long]$createdOrderResp.data.id
        $orderNum = $createdOrderResp.data.orderNumber
        $createdOrders++

        # Ensure customer_measurements keeps its configured lining + rich notes even after OrderService sync
        if ($ord.garment) {
            $profMatch = $cust.profiles | Where-Object { $_.garment -eq $ord.garment } | Select-Object -First 1
            if ($profMatch) {
                Invoke-ErpApi -Method POST -Path "/api/customer-measurements" -Token $token -Body @{
                    customerMobile = $mobile
                    customerName   = $cust.name
                    garmentType    = $profMatch.garment
                    lining         = $profMatch.lining
                    measurements   = $profMatch.measurements
                    notes          = $profMatch.notes
                } | Out-Null
            }
        }

        if ($ord.extraPay -gt 0) {
            Invoke-ErpApi -Method POST -Path "/api/orders/$orderId/payments" -Token $token -Body @{
                amount        = [decimal]$ord.extraPay
                paymentType   = "PARTIAL"
                paymentMethod = $ord.extraMode
                collector     = "Reception Desk"
            } | Out-Null
        }

        switch ($ord.status) {
            "PENDING" { }
            "CANCELLED" {
                Invoke-ErpApi -Method DELETE -Path "/api/orders/$orderId/cancel" -Token $token | Out-Null
            }
            { $_ -in @("DELIVERED", "COMPLETED") } {
                Invoke-ErpApi -Method PUT -Path "/api/orders/$orderId/status" -Token $token -Body @{
                    status               = "READY_TO_DELIVERY"
                    assignedEmployeeId   = $assignedEmpId
                    assignedEmployeeName = $assignedEmpName
                } | Out-Null

                $deliverBody = @{
                    status       = $ord.status
                    receiverName = $ord.receiver
                }
                if ($ord.discount -gt 0) { $deliverBody["discountAmount"] = [decimal]$ord.discount }
                if ($ord.finalPay -gt 0) {
                    $deliverBody["finalPayment"] = [decimal]$ord.finalPay
                    $deliverBody["paymentMode"]  = $ord.finalMode
                }
                Invoke-ErpApi -Method PUT -Path "/api/orders/$orderId/status" -Token $token -Body $deliverBody | Out-Null
            }
            default {
                Invoke-ErpApi -Method PUT -Path "/api/orders/$orderId/status" -Token $token -Body @{
                    status               = $ord.status
                    assignedEmployeeId   = $assignedEmpId
                    assignedEmployeeName = $assignedEmpName
                } | Out-Null
            }
        }

        $garmentLabel = if ($ord.garment) { $ord.garment } else { "STANDARD" }
        Write-Host ("  -> {0} [{1}] garment={2} total={3} branch={4}" -f $orderNum, $ord.status, $garmentLabel, $ord.total, $ord.branchId)
    }
}

Write-Host ""
Write-Host ("Seeding complete: created {0} customers, skipped {1}, created {2} orders." -f $createdCustomers, $skippedCustomers, $createdOrders) -ForegroundColor Green
