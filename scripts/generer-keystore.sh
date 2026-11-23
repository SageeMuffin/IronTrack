#!/usr/bin/env bash
# Crée UNE SEULE FOIS le keystore de production d'IronTrack, en dehors du dépôt Git.
# Ne remplace jamais un keystore existant. Les mots de passe sont demandés par keytool (jamais en argument).
#
# Usage : scripts/generer-keystore.sh [chemin]   (par défaut : ~/irontrack-signing/irontrack-release.jks)
set -euo pipefail

dest="${1:-$HOME/irontrack-signing/irontrack-release.jks}"
alias="${IRONTRACK_KEY_ALIAS:-irontrack}"

repo_root="$(git rev-parse --show-toplevel 2>/dev/null || true)"
abs_dest="$(cd "$(dirname "$dest")" 2>/dev/null && pwd || dirname "$dest")/$(basename "$dest")"
if [ -n "$repo_root" ] && [[ "$abs_dest" == "$repo_root"* ]]; then
  echo "Refusé : le keystore ne doit pas être créé dans le dépôt Git ($repo_root)." >&2
  exit 1
fi
if [ -e "$dest" ]; then
  echo "Refusé : $dest existe déjà. Un keystore existant n'est jamais écrasé." >&2
  echo "Changer de clé rendrait impossibles les mises à jour des installations existantes." >&2
  exit 1
fi

mkdir -p "$(dirname "$dest")"
chmod 700 "$(dirname "$dest")"
keytool -genkeypair -v \
  -keystore "$dest" -storetype PKCS12 \
  -alias "$alias" -keyalg RSA -keysize 4096 -validity 10000 \
  -dname "CN=IronTrack"
chmod 600 "$dest"

echo
echo "Keystore créé : $dest (alias : $alias)"
echo "1. Sauvegardez ce fichier ET ses mots de passe dans au moins deux endroits sûrs (gestionnaire de mots de passe, clé USB chiffrée)."
echo "2. Valeur du secret ANDROID_KEYSTORE_BASE64 :"
echo "     base64 -w0 \"$dest\"      (macOS : base64 -i \"$dest\")"
echo "   Copiez-la directement dans GitHub > Settings > Secrets and variables > Actions, sans l'enregistrer dans un fichier du dépôt."
