# 5–9. Installation, compilation, installation sur le Pixel 9a, hors ligne

Tout ce qui demande Internet se fait **avant** d'aller en salle.

## 5. Installation de l'environnement (avec Internet)

Deux possibilités.

### A. Sans rien installer : l'APK compilé par GitHub Actions
À chaque envoi sur GitHub, le workflow `.github/workflows/android.yml` exécute les tests et
compile l'APK.
- Onglet **Actions** → dernière exécution réussie → artefact **projecteur-remote-apk**, ou
- Onglet **Releases** (si un tag `v1.0.0` a été publié) → `projecteur-remote.apk`.

### B. Compiler soi-même
Prérequis :
- **JDK 17** ou plus récent (Temurin recommandé) ;
- **Android SDK** avec la plateforme 35 : le plus simple est d'installer
  [Android Studio](https://developer.android.com/studio) (qui fournit le SDK), ou les
  *command-line tools* :
  ```bash
  sdkmanager "platforms;android-35" "build-tools;35.0.0" "platform-tools"
  ```
- Git.

```bash
git clone https://github.com/noahgrynko/Projecteur-.git
cd Projecteur-
echo "sdk.dir=$HOME/Android/Sdk" > local.properties   # chemin de votre SDK (inutile avec Android Studio)
```

## 6. Compilation

```bash
./gradlew testDebugUnitTest   # 27 tests : protocoles IR, base intégrée, PJLink, garde-fous réseau, parcours de l'interface
./gradlew assembleDebug       # → app/build/outputs/apk/debug/app-debug.apk
```

La première compilation télécharge Gradle, le plugin Android et les dépendances (Google Maven,
Maven Central, JitPack). Les suivantes fonctionnent hors ligne (`./gradlew --offline assembleDebug`).

### APK de release signé (optionnel)
```bash
keytool -genkeypair -v -keystore ~/projecteur.jks -alias projecteur -keyalg RSA -keysize 2048 -validity 10000
export PROJECTEUR_KEYSTORE=~/projecteur.jks
export PROJECTEUR_KEYSTORE_PASSWORD=...  PROJECTEUR_KEY_ALIAS=projecteur  PROJECTEUR_KEY_PASSWORD=...
./gradlew assembleRelease     # → app/build/outputs/apk/release/app-release.apk
```
Ne publiez jamais le fichier `.jks` (il est exclu par `.gitignore`).

## 7. Installation sur le Pixel 9a

### Par câble (adb)
1. Pixel 9a : **Paramètres → À propos du téléphone → Numéro de build** (7 appuis) pour activer
   les options pour les développeurs, puis **Système → Options pour les développeurs → Débogage USB**.
2. Relier le téléphone à l'ordinateur et accepter l'empreinte.
3. `adb install -r app/build/outputs/apk/debug/app-debug.apk`

### Sans ordinateur
1. Télécharger l'APK (Releases/Actions) sur le téléphone.
2. L'ouvrir depuis **Fichiers** ; autoriser l'installation d'applications inconnues pour
   l'application utilisée (Chrome ou Fichiers) quand Android le demande.
3. Play Protect peut afficher un avertissement pour une application non publiée sur le Play Store :
   choisir « Installer quand même » si vous avez compilé/téléchargé l'APK depuis votre dépôt.

## 8. Préparation avant d'aller en salle (checklist)

- [ ] Application installée et **ouverte une fois**.
- [ ] Autorisation **Appareils à proximité** accordée (onglet 🔵 Bluetooth → Autoriser).
- [ ] Accessoire IR branché une fois → **Autoriser et vérifier** (cocher « Utiliser par défaut »
      dans la boîte de dialogue Android pour ne plus avoir à le refaire).
- [ ] Si vous connaissez la marque/le modèle : onglet 🔴 Infrarouge → *Sélection automatique*.
- [ ] Page 🧪 Diagnostic : vérifier « Profils IR chargés ✓ » et « Émetteur IR disponible ✓ ».
- [ ] Emporter l'adaptateur USB-C (OTG) si l'accessoire est en USB-A.

## 9. Fonctionnement hors ligne

- Codes IR : fichiers embarqués dans l'APK (`assets/irdb`), chargés localement.
- Aucune requête HTTP, aucun service Google, aucune analyse, aucun compte.
- Réseau : seules des sockets locales vers des adresses privées (PJLink) ; si le téléphone est
  relié à un Wi-Fi **sans Internet** (celui du projecteur), l'application lie ses sockets à ce
  réseau précis pour ne pas passer par les données mobiles.
- Vous pouvez activer le **mode avion** puis réactiver **seulement le Bluetooth** (et le Wi-Fi si le
  projecteur en crée un) : l'application fonctionne normalement.

```
Pixel 9a → application → (IR par accessoire | Bluetooth HID | PJLink local) → vidéoprojecteur
```
