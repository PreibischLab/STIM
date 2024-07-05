@echo off
setlocal EnableExtensions

:: This is a translation of the corresponding linux install script that
:: is adapted from https://github.com/saalfeldlab/n5-utils, by @axtimwalde & co
set "VERSION=0.3.0-SNAPSHOT"

:: default for installation dir = current directory
set "INSTALL_DIR=%CD%"

:: default for repository dir = standard maven repository
set "REPO_DIR=%USERPROFILE%\.m2\repository"

:: parse arguments one by one
:parse
	if "%~1"=="" goto :doneparsing

	if /i "%~1"=="/h"    call :usage & goto :EOF
	if /i "%~1"=="/i"    set "INSTALL_DIR=%~2" & shift & shift & goto :parse
	if /i "%~1"=="/r"    set "REPO_DIR=%~2" & shift & shift & goto :parse
	
	:: argument doesn't match any of the above
	echo Unknown option "%~1"
	call :usage & goto :EOF
:doneparsing

echo.
echo Downloading dependencies into %REPO_DIR%
echo Installing into %INSTALL_DIR%

:: read out total memory of system
for /F "skip=1" %%A in ('wmic ComputerSystem get TotalPhysicalMemory') do (
	set MEM=%%A
	goto :donemem
)
:donemem

:: batch can only handle integers below 2^31
:: -> cut last six digits of number in bytes (~MB) and divide by 1024 (~GB) 
set /A "MEMGB=%MEM:~,-6%/1024"
set /A "MEM_LIMIT=(4*MEMGB)/5"

echo Available memory: %MEMGB%GB, setting Java memory limit to %MEM_LIMIT%GB

:: Skip tests for now, since they don't work on windows.
call mvn clean install -DskipTests -Dmaven.repo.local="%REPO_DIR%"
call mvn -Dmdep.outputFile=cp.txt -Dmdep.includeScope=runtime -Dmaven.repo.local="%REPO_DIR%" dependency:build-classpath

call :install_command st-explorer.bat cmd.View
call :install_command st-render.bat cmd.RenderImage
call :install_command st-bdv-view.bat cmd.DisplayStackedSlides
call :install_command st-resave.bat cmd.Resave
call :install_command st-add-slice.bat cmd.AddSlice
call :install_command st-normalize.bat cmd.Normalize
call :install_command st-add-annotations.bat cmd.AddAnnotations
call :install_command st-add-entropy.bat cmd.AddEntropy
call :install_command st-align-pairs.bat cmd.PairwiseSectionAligner
call :install_command st-align-pairs-view.bat cmd.ViewPairwiseAlignment
call :install_command st-align-global.bat cmd.GlobalOpt
call :install_command st-align-interactive.bat cmd.InteractiveAlignment
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
	move "st-add-entropy.bat" "%INSTALL_DIR%\"
	move "st-align-pairs.bat" "%INSTALL_DIR%\"
	move "st-align-pairs-view.bat" "%INSTALL_DIR%\"
	move "st-align-global.bat" "%INSTALL_DIR%\"
	move "st-align-interactive.bat" "%INSTALL_DIR%\"
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
		echo  -Xmx%MEM_LIMIT%g^^
		echo  -cp %REPO_DIR%\net\preibisch\imglib2-st\%VERSION%\imglib2-st-%VERSION%.jar;^^
		type cp.txt
		echo ^^
		echo  %2 %%*
	) > %1
goto :EOF

:usage
	echo USAGE: install_windows.bat [options]
	echo.
	echo. OPTIONS
	echo.   /h                    Display this help message
	echo.   /i [install_dir]      Install commands into [install_dir]
	echo.                         (default: current directory)
	echo.   /r [repository_dir]   Download dependencies into [repository_dir]
	echo.                         (default: %%USERPROFILE%%\.m2\repository)

endlocal
