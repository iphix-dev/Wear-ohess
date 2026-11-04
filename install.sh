#!/usr/bin/env bash
# Installe WristRelay sur le téléphone (ADB USB ou Wi-Fi) et sur la montre (ADB Wi-Fi).
# Usage : ./install.sh [dossier_apk]   (par défaut : télécharge la dernière release GitHub)
set -euo pipefail

REPO="iphix-dev/Wear-ohess"
DIR="${1:-./apk}"
PHONE_APK="$DIR/WristRelay-phone.apk"
WATCH_APK="$DIR/WristRelay-watch.apk"

command -v adb >/dev/null || { echo "adb introuvable (sudo pacman -S android-tools)"; exit 1; }

if [[ ! -f "$PHONE_APK" || ! -f "$WATCH_APK" ]]; then
  echo "==> Téléchargement des APK de la dernière release..."
  mkdir -p "$DIR"
  for f in WristRelay-phone.apk WristRelay-watch.apk; do
    curl -fL -o "$DIR/$f" "https://github.com/$REPO/releases/latest/download/$f"
  done
fi

echo
echo "=== TÉLÉPHONE ==="
echo "1) USB (débogage USB activé)"
echo "2) Wi-Fi (débogage sans fil)"
read -rp "Choix [1/2] : " mode
if [[ "$mode" == "2" ]]; then
  read -rp "IP:PORT d'appairage (ou vide si déjà appairé) : " ppair
  if [[ -n "$ppair" ]]; then adb pair "$ppair"; fi
  read -rp "IP:PORT de connexion du téléphone : " PHONE
  adb connect "$PHONE"
else
  adb devices -l
  read -rp "Numéro de série du téléphone : " PHONE
fi
echo "==> Installation sur le téléphone..."
adb -s "$PHONE" install -r "$PHONE_APK"

echo
echo "=== MONTRE (ADB Wi-Fi) ==="
echo "Sur la montre : Options développeur > Débogage ADB + Débogage via Wi-Fi > Appairer un nouvel appareil."
read -rp "IP:PORT d'appairage de la montre (vide si déjà appairée) : " wpair
if [[ -n "$wpair" ]]; then adb pair "$wpair"; fi
read -rp "IP:PORT de connexion de la montre : " WATCH
adb connect "$WATCH"
echo "==> Installation sur la montre..."
adb -s "$WATCH" install -r "$WATCH_APK"

echo
read -rp "Déconnecter le débogage Wi-Fi de la montre maintenant ? [O/n] " d
if [[ ! "$d" =~ ^[nN]$ ]]; then adb disconnect "$WATCH" || true; fi

echo "Terminé. Ouvrez WristRelay sur chaque appareil, autorisez Bluetooth/notifications, choisissez l'appareil associé."
