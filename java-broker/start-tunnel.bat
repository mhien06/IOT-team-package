@echo off
chcp 65001 >nul
title Mo TCP Tunnel (ngrok tcp 1883) cho Java MQTT Broker - May 1

echo ========================================================================
echo   MO TCP TUNNEL CONG 1883 CHO JAVA MQTT BROKER (MAY 1 - WINDOWS)
echo ========================================================================
echo.
echo [1] HUONG DAN DANG KY TAI KHOAN VA ADD AUTHTOKEN (chi lam 1 lan dau):
echo     - Buoc 1: Dang ky tai khoan mien phi tai https://ngrok.com
echo     - Buoc 2: Tai ngrok (hoac chay: winget install ngrok.ngrok)
echo     - Buoc 3: Lay Authtoken trong trang Dashboard cua ngrok roi chay:
echo               ngrok config add-authtoken ^<AUTHTOKEN_CUA_BAN^>
echo.
echo [2] CACH DOC HOST VA PORT CONG KHAI TU NGROK:
echo     Sau khi ngrok chay, xem dong Forwarding, vi du:
echo       Forwarding   tcp://0.tcp.xx.ngrok.io:12345 -^> localhost:1883
echo     Khi do dien vao dau file wokwi\sketch.ino:
echo       MQTT_HOST = "0.tcp.xx.ngrok.io"   (ten mien, KHONG ghi tcp://)
echo       MQTT_PORT = 12345
echo.
echo [3] LUU Y QUAN TRONG:
echo     - Moi lan tat va chay lai ngrok ban mien phi thi host va port SE DOI,
echo       can cap nhat lai MQTT_HOST va MQTT_PORT trong wokwi\sketch.ino!
echo     - May 3 (Client) cung mang LAN dung IP LAN May 1 (tcp://192.168.x.x:1883),
echo       hoac dung chung dia chi tunnel (tcp://0.tcp.xx.ngrok.io:12345) neu khac mang.
echo.
echo [4] PHUONG AN DU PHONG (playit.gg - mien phi, khong can the tin dung):
echo     - Tai agent tai https://playit.gg/download va chay tren May 1.
echo     - Tao tunnel loai Custom TCP tro ve 127.0.0.1:1883.
echo     - Lay host:port duoc cap dien vao MQTT_HOST va MQTT_PORT trong sketch.ino.
echo ========================================================================
echo.

where ngrok >nul 2>&1
if errorlevel 1 (
    echo [LOI] Khong tim thay lenh "ngrok" trong PATH!
    echo Hay cai ngrok bang lenh:  winget install ngrok.ngrok
    echo Hoac tai tai https://ngrok.com/download roi giai nen vao thu muc PATH.
    echo.
    pause
    exit /b 1
)

echo Dang khoi chay: ngrok tcp 1883 ...
echo (Dam bao Java MQTT Broker da chay truoc trong thu muc java-broker bang: mvn compile exec:java)
echo.
ngrok tcp 1883

if errorlevel 1 (
    echo.
    echo [LOI] ngrok dung dot ngot. Kiem tra da chay lenh:
    echo       ngrok config add-authtoken ^<AUTHTOKEN_CUA_BAN^>
    echo Hoac chuyen sang phuong an du phong playit.gg (https://playit.gg/download).
    echo.
    pause
)
