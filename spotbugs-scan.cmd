@echo off
:: ============================================================
:: SpotBugs full-project scan — scan ALL modules then report
:: ============================================================
::
:: Usage (run from project root):
::   spotbugs-scan.cmd            -- scan only, never fails
::   spotbugs-scan.cmd --gate     -- scan all, then fail if findings found
::
:: Reports: <module>\target\spotbugsXml.xml  (one per module)
::
:: Why two steps?
::   Maven's reactor is sequential — the spotbugs:check goal runs per-module
::   and would abort the reactor on the first module with findings.
::   By running spotbugs:spotbugs first (scan only, never fails) across the
::   whole project, and then spotbugs:check separately with -fae (fail-at-end),
::   we guarantee that every module is scanned before any failure is reported.
:: ============================================================

setlocal EnableDelayedExpansion

set GATE=false
if "%~1"=="--gate" set GATE=true

echo ========================================================
echo  Step 1 -- Scanning all modules (never fails)
echo ========================================================

call mvn verify -Pspotbugs -Dspotbugs.skipCheck=true
if errorlevel 1 (
    echo.
    echo [WARN] mvn verify returned a non-zero exit code.
    echo        This is unexpected since skipCheck=true. Check output above.
)

echo.
echo ========================================================
echo  SpotBugs XML reports written to:
echo ========================================================
for /r %%F in (spotbugsXml.xml) do (
    echo    %%F
)
echo.
echo  Aggregate report (all modules combined):
echo    %~dp0target\spotbugs-aggregate.xml
echo ========================================================

if "%GATE%"=="true" (
    echo.
    echo ========================================================
    echo  Step 2 -- Checking all reports (gate mode)
    echo ========================================================
    call mvn spotbugs:check -Pspotbugs -Dspotbugs.skipCheck=false -fae
    if errorlevel 1 (
        echo.
        echo [FAIL] SpotBugs gate failed -- findings above threshold detected.
        echo        Review the spotbugsXml.xml files listed above.
        exit /b 1
    )
    echo Gate passed -- no findings above threshold.
)

endlocal
exit /b 0

