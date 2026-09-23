#!/usr/bin/env bash
# Met à jour la base de codes IR intégrée à partir de Flipper-IRDB (licence CC0).
# À exécuter AVEC Internet, avant d'aller en salle ; puis recompiler l'application.
#   ./tools/update_irdb.sh            # branche main
#   ./tools/update_irdb.sh <commit>   # version précise
set -euo pipefail
REF="${1:-main}"
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
DEST="$ROOT/app/src/main/assets/irdb"
TMP="$(mktemp -d)"
trap 'rm -rf "$TMP"' EXIT

git clone --quiet --filter=blob:none --sparse https://github.com/Lucaslhm/Flipper-IRDB.git "$TMP/irdb"
git -C "$TMP/irdb" sparse-checkout set Projectors
git -C "$TMP/irdb" checkout --quiet "$REF"
COMMIT="$(git -C "$TMP/irdb" rev-parse HEAD)"
DATE="$(git -C "$TMP/irdb" log -1 --format=%cs)"

rm -rf "$DEST/Projectors"
cp -r "$TMP/irdb/Projectors" "$DEST/"
cp "$TMP/irdb/LICENSE" "$DEST/LICENSE-CC0.txt"
cat > "$DEST/SOURCE.txt" <<TXT
Source: https://github.com/Lucaslhm/Flipper-IRDB (CC0 1.0)
Dossier: Projectors/
Commit: $COMMIT ($DATE)
Fichiers copiés sans modification. Mise à jour : tools/update_irdb.sh
TXT
echo "Base IR mise à jour : $(find "$DEST/Projectors" -name '*.ir' | wc -l) fichiers (commit $COMMIT)."
echo "Pensez à relancer les tests : ./gradlew testDebugUnitTest (le nombre de fichiers attendu peut changer)."
