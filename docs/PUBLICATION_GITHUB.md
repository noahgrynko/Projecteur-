# 17. Publication sur GitHub

## Envoyer le projet
```bash
git remote add origin https://github.com/<utilisateur>/<dépôt>.git   # si ce n'est pas déjà fait
git push -u origin main
```
Fichiers exclus par `.gitignore` : `build/`, `.gradle/`, `local.properties`, APK, clés `.jks`.

## Intégration continue
Le workflow `.github/workflows/android.yml` s'exécute à chaque envoi et *pull request* :
1. tests unitaires (`testDebugUnitTest`) ;
2. compilation de l'APK (`assembleDebug`) → artefact **projecteur-remote-apk** ;
3. compilation du firmware PROJIR pour Arduino Leonardo et Nano.

## Publier une version téléchargeable
```bash
git tag v1.0.0
git push origin v1.0.0
```
Le workflow crée une **Release** GitHub contenant `projecteur-remote.apk` (APK signé avec la clé
de débogage, installable directement). Pour un APK signé avec votre propre clé, compilez
`assembleRelease` localement (voir [INSTALLATION.md](INSTALLATION.md)) et joignez-le à la release.

## GitHub n'est pas nécessaire en salle
GitHub sert à stocker le code et distribuer l'APK. Une fois l'APK installé, l'application ne
contacte jamais GitHub ni aucun autre serveur. GitHub Pages n'est pas utilisé : un navigateur ne
peut accéder ni au profil Bluetooth HID, ni aux accessoires IR USB, ni aux sockets PJLink
(voir [ANALYSE_TECHNIQUE.md](ANALYSE_TECHNIQUE.md#13-pourquoi-un-simple-site-web-ou-github-pages-ne-suffit-pas)).

## Mettre à jour la base de codes IR
```bash
./tools/update_irdb.sh          # avec Internet
./gradlew testDebugUnitTest     # adapter le nombre de fichiers attendu si la base a changé
git commit -am "Mise à jour de la base Flipper-IRDB" && git push
```
