@echo off
setlocal EnableExtensions

:: This is a translation of the corresponding linux install script that
:: is adapted from https://github.com/saalfeldlab/n5-utils, by @axtimwalde & co
set "VERSION=0.2.0-SNAPSHOT"

if "%~1"=="" (
    set "INSTALL_DIR=%CD%"
) else (
    set "INSTALL_DIR=%~1"
)

:: read out total memory of system
for /F "skip=1" %%A in ('wmic ComputerSystem get TotalPhysicalMemory') do (
	set MEM=%%A
	goto :done
)
:done

:: batch can only handle integers below 2^31
:: -> cut last six digits of number in bytes (~MB) and divide by 1024 (~GB) 
set /A "MEMGB=%MEM:~,-6%/1024"
set /A "MEM=(3*MEMGB)/4"

echo Available memory: %MEMGB%GB, setting Java memory limit to %MEM%GB

:: Skip tests for now, since they don't work on windows.
call mvn clean install -DskipTests
call mvn -Dmdep.outputFile=cp.txt -Dmdep.includeScope=runtime dependency:build-classpath

call :install_command st-explorer.bat cmd.View
call :install_command st-render.bat cmd.RenderImage
call :install_command st-bdv-view.bat cmd.DisplayStackedSlides
call :install_command st-resave.bat cmd.Resave
call :install_command st-add-slice.bat cmd.AddSlice
call :install_command st-normalize.bat cmd.Normalize
call :install_command st-add-annotations.bat cmd.AddAnnotations
call :install_command st-align-pairs.bat cmd.PairwiseSectionAligner
call :install_command st-align-pairs-view.bat cmd.ViewPairwiseAlignment
call :install_command st-align-global.bat cmd.GlobalOpt
call :install_command st-help.bat cmd.PrintHelp

if "%CD%"=="%INSTALL_DIR%" (
   	echo Installation directory equals current directory, we are done.
) else (
	echo Creating directory %INSTALL_DIR% and moving files...
   	mkdir "%INSTALL_DIR%"
   	move "st-explorer.bat" "%INSTALL_DIR%\"
	move "st-bdv-view.bat" "%INSTALL_DIR%\"
	move "st-render.bat" "%INSTALL_DIR%\"
   	move "st-resave.bat" "%INSTALL_DIR%\"
	move "st-add-slice.bat" "%INSTALL_DIR%\"
	move "st-normalize.bat" "%INSTALL_DIR%\"
	move "st-add-annotations.bat" "%INSTALL_DIR%\"
	move "st-align-pairs.bat" "%INSTALL_DIR%\"
	move "st-align-pairs-view.bat" "%INSTALL_DIR%\"
	move "st-align-global.bat" "%INSTALL_DIR%\"
	move "st-help.bat" "%INSTALL_DIR%\"
)

del "cp.txt"
echo Installation finished.
goto :EOF


:: function that installs one command
:: %1 - command name
:: %2 - java class containing the functionality
:install_command
	echo Installing '%1' command into %INSTALL_DIR%
	(
		echo @echo off
		echo.
		echo java^^
		echo  -Xmx%MEM%g^^
		echo  -cp %USERPROFILE%\.m2\repository\net\preibisch\imglib2-st\%VERSION%\imglib2-st-%VERSION%.jar;^^
		type cp.txt
		echo ^^
		echo  %2 %%*
	) > %1
goto :EOF

endlocal



