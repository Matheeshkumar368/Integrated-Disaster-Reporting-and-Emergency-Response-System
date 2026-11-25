; ==============================================================================
; DisasterResponseSystem - Windows Installer Script (NSIS)
; ==============================================================================

!include "MUI2.nsh"
!include "FileFunc.nsh"

Name "Disaster Response System"
OutFile "..\release\DisasterResponseSystem-Setup.exe"
InstallDir "$LOCALAPPDATA\Programs\DisasterResponseSystem"
InstallDirRegKey HKCU "Software\DisasterResponseSystem" "Install_Dir"
RequestExecutionLevel user

; Interface Settings
!define MUI_ABORTWARNING
!define MUI_ICON "${NSISDIR}\Contrib\Graphics\Icons\modern-install.ico"
!define MUI_UNICON "${NSISDIR}\Contrib\Graphics\Icons\modern-uninstall.ico"

; Pages
!insertmacro MUI_PAGE_WELCOME
!insertmacro MUI_PAGE_DIRECTORY
!insertmacro MUI_PAGE_INSTFILES
!define MUI_FINISHPAGE_RUN "$INSTDIR\DisasterResponseSystem.exe"
!define MUI_FINISHPAGE_RUN_TEXT "Launch Disaster Response System Mission Control"
!insertmacro MUI_PAGE_FINISH

; Uninstaller Pages
!insertmacro MUI_UNPAGE_CONFIRM
!insertmacro MUI_UNPAGE_INSTFILES

; Languages
!insertmacro MUI_LANGUAGE "English"

; Installer Section
Section "DisasterResponseSystem (required)" SecMain
    SetOutPath "$INSTDIR"
    
    ; Copy application image files
    File /r "..\release\app-image\*.*"
    
    ; Write Uninstaller
    WriteUninstaller "$INSTDIR\Uninstall.exe"
    
    ; Registry keys for Add/Remove Programs
    WriteRegStr HKCU "Software\DisasterResponseSystem" "Install_Dir" "$INSTDIR"
    WriteRegStr HKCU "Software\Microsoft\Windows\CurrentVersion\Uninstall\DisasterResponseSystem" "DisplayName" "Disaster Response System"
    WriteRegStr HKCU "Software\Microsoft\Windows\CurrentVersion\Uninstall\DisasterResponseSystem" "UninstallString" '"$INSTDIR\Uninstall.exe"'
    WriteRegStr HKCU "Software\Microsoft\Windows\CurrentVersion\Uninstall\DisasterResponseSystem" "DisplayIcon" "$INSTDIR\DisasterResponseSystem.exe"
    WriteRegStr HKCU "Software\Microsoft\Windows\CurrentVersion\Uninstall\DisasterResponseSystem" "Publisher" "National Disaster Management Platform"
    WriteRegStr HKCU "Software\Microsoft\Windows\CurrentVersion\Uninstall\DisasterResponseSystem" "DisplayVersion" "1.0.0"
    WriteRegDWORD HKCU "Software\Microsoft\Windows\CurrentVersion\Uninstall\DisasterResponseSystem" "NoModify" 1
    WriteRegDWORD HKCU "Software\Microsoft\Windows\CurrentVersion\Uninstall\DisasterResponseSystem" "NoRepair" 1

    ; Create Shortcuts
    CreateDirectory "$SMPROGRAMS\Disaster Response System"
    CreateShortcut "$SMPROGRAMS\Disaster Response System\Disaster Response System.lnk" "$INSTDIR\DisasterResponseSystem.exe"
    CreateShortcut "$SMPROGRAMS\Disaster Response System\Configure Firewall.lnk" "$INSTDIR\setup-firewall.bat"
    CreateShortcut "$SMPROGRAMS\Disaster Response System\Uninstall.lnk" "$INSTDIR\Uninstall.exe"
    CreateShortcut "$DESKTOP\Disaster Response System.lnk" "$INSTDIR\DisasterResponseSystem.exe"
SectionEnd

; Uninstaller Section
Section "Uninstall"
    ; Remove Shortcuts
    Delete "$DESKTOP\Disaster Response System.lnk"
    RMDir /r "$SMPROGRAMS\Disaster Response System"
    
    ; Remove Registry keys
    DeleteRegKey HKCU "Software\Microsoft\Windows\CurrentVersion\Uninstall\DisasterResponseSystem"
    DeleteRegKey HKCU "Software\DisasterResponseSystem"
    
    ; Remove Files
    RMDir /r "$INSTDIR\runtime"
    RMDir /r "$INSTDIR\server"
    Delete "$INSTDIR\DisasterResponseSystem.exe"
    Delete "$INSTDIR\DisasterServerBackend.exe"
    Delete "$INSTDIR\setup-firewall.bat"
    Delete "$INSTDIR\start-server.bat"
    Delete "$INSTDIR\README.txt"
    Delete "$INSTDIR\Uninstall.exe"
    RMDir "$INSTDIR"
SectionEnd
