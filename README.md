# Projecteur Remote — télécommande de vidéoprojecteur pour Android (Pixel 9a)

Application Android native (Kotlin + Jetpack Compose) qui transforme un téléphone en
**télécommande** de vidéoprojecteur, **hors ligne**, sans Wi-Fi, sans Internet et **sans jamais
projeter ni partager l'écran du téléphone**.

```
Pixel 9a ──(commande « Volume + »)──▶ vidéoprojecteur          ✔ ce que fait l'application
Pixel 9a ──(écran / vidéo)─────────▶ vidéoprojecteur          ✘ jamais (ni Cast, ni Miracast, ni AirPlay)
```

L'application ne contient aucun code de partage d'écran, de Cast, de Miracast ou d'AirPlay.
Elle n'envoie que des commandes de télécommande, par l'un de ces trois canaux :

| Canal | Ce qui est envoyé | Norme utilisée |
|---|---|---|
| 🔴 Infrarouge | trames IR de la télécommande d'origine | NEC, NECext, Samsung32, Sony SIRC, signaux bruts |
| 🔵 Bluetooth | touches d'une télécommande/d'un clavier Bluetooth | profil HID Device (Android 9+) |
| 🌐 Réseau local | commandes PJLink | PJLink classe 1 et 2 (TCP/UDP 4352) |

---

## Ce qui marche avec le Pixel 9a seul, et ce qui demande un accessoire

> **Le Google Pixel 9a n'a pas d'émetteur infrarouge.** Aucun Pixel n'en a jamais eu.
> L'application le vérifie au démarrage (`ConsumerIrManager.hasIrEmitter()`) et affiche :
> « Aucun émetteur infrarouge intégré détecté. Un émetteur IR externe compatible USB-C est
> nécessaire pour utiliser ce mode. »

| Fonction | Pixel 9a seul | Condition / matériel nécessaire |
|---|---|---|
| Télécommande **infrarouge** | ❌ | Émetteur IR USB-C externe (voir [docs/MATERIEL_IR.md](docs/MATERIEL_IR.md)) |
| **Apprendre** une commande IR | ❌ | Récepteur IR USB (IR Toy/Irdroid, ou pont PROJIR avec TSOP38238) |
| Base de 126 profils IR, recherche, test guidé | ✅ (consultation) | Il faut un émetteur pour émettre |
| **Bluetooth** : recherche, services, association | ✅ | — |
| **Bluetooth** : contrôle du projecteur | ⚠️ parfois | Seulement si le projecteur accepte une **télécommande/un clavier Bluetooth** (projecteurs « smart » Android/Google TV). Un Bluetooth qui ne sert qu'au son **ne permet pas** de contrôler le projecteur. |
| **Réseau** PJLink | ⚠️ parfois | Le projecteur doit avoir une interface réseau active : son propre Wi-Fi (« Simple AP »), ou un câble LAN via un **adaptateur USB-C → Ethernet** |
| Diagnostic réseau (ping) | ⚠️ | Seulement si une interface réseau locale existe |
| Recherche automatique, checklist | ✅ | — |

**Dans une salle sans réseau, avec un projecteur « classique » (sans Bluetooth de commande), le
seul moyen fiable est l'infrarouge, donc un accessoire IR USB-C.** L'application le dit clairement
au lieu de prétendre contrôler le projecteur.

---

## Démarrage rapide

1. **Avec Internet** (chez soi) : récupérer l'APK dans les *Releases*/*Actions* GitHub, ou compiler :
   ```bash
   git clone https://github.com/noahgrynko/Projecteur-.git && cd Projecteur-
   ./gradlew assembleDebug
   adb install -r app/build/outputs/apk/debug/app-debug.apk
   ```
2. Installer l'APK sur le Pixel 9a ([docs/INSTALLATION.md](docs/INSTALLATION.md)).
3. Brancher l'accessoire IR (s'il y en a un) et ouvrir l'application une première fois pour
   **accepter les autorisations** (USB, « Appareils à proximité »).
4. **En salle, Internet coupé** : 🔎 *Rechercher* → choisir la méthode trouvée → télécommande.

Tout est embarqué dans l'APK (codes IR compris) : ni GitHub, ni serveur, ni Internet ne sont
nécessaires pendant l'utilisation.

---

## Documentation

| # | Sujet | Document |
|---|---|---|
| 1 | Analyse technique (Pixel 9a, pourquoi pas un site web) | [docs/ANALYSE_TECHNIQUE.md](docs/ANALYSE_TECHNIQUE.md) |
| 2–3 | Architecture et arborescence | [docs/ARCHITECTURE.md](docs/ARCHITECTURE.md) |
| 5–8 | Installation, compilation, installation sur le Pixel 9a | [docs/INSTALLATION.md](docs/INSTALLATION.md) |
| 9 | Fonctionnement hors ligne | [docs/INSTALLATION.md#fonctionnement-hors-ligne](docs/INSTALLATION.md#fonctionnement-hors-ligne) |
| 10–16 | Utilisation : Bluetooth, IR, émetteur USB-C, apprentissage, détection, télécommande, diagnostic | [docs/UTILISATION.md](docs/UTILISATION.md) |
| 12 | Matériel IR compatible, câblage du pont PROJIR | [docs/MATERIEL_IR.md](docs/MATERIEL_IR.md) |
| — | Protocole du firmware PROJIR | [docs/FIRMWARE_PROTOCOL.md](docs/FIRMWARE_PROTOCOL.md) |
| 17 | Publication GitHub et releases | [docs/PUBLICATION_GITHUB.md](docs/PUBLICATION_GITHUB.md) |
| 18 | Résolution des problèmes | [docs/DEPANNAGE.md](docs/DEPANNAGE.md) |

---

## Fonctions

- **Télécommande** : POWER, VOL− / MUTE / VOL+, SOURCE (et HDMI/VGA si disponibles), croix ▲◀OK▶▼,
  MENU / RETOUR / EXIT, FREEZE / BLANK. Les touches non prises en charge par la méthode active sont
  **désactivées**. L'état affiché n'est « Connecté » qu'après un vrai échange (poignée de main
  PJLink, connexion HID confirmée par la pile Bluetooth). L'IR, unidirectionnel, reste « émetteur
  prêt (sans retour) ».
- **🔴 Infrarouge** : détection de l'émetteur intégré, des accessoires USB (vérifiés par leur
  protocole), sélection automatique du profil si la marque et le modèle sont connus, recherche
  par marque, **test guidé profil par profil avec confirmation**, import de fichiers `.ir`.
- **📡 Apprentissage IR** : capture réelle par un récepteur IR, décodage du protocole (NEC,
  NECext, Samsung32, SIRC) ou conservation du signal brut, réémission, enregistrement.
- **🔵 Bluetooth** : recherche classique + BLE, lecture des services (SDP/GATT) et explication de
  chacun (A2DP = son seulement, etc.), association standard, mode télécommande HID.
- **🔎 Rechercher le vidéoprojecteur** : Bluetooth → réseau local (recherche PJLink) → connexion
  directe → matériel IR → autres interfaces ; affiche marque, modèle, méthode, état, informations
  et commandes disponibles. Rien n'est « détecté » sans réponse réelle.
- **🧪 Diagnostic** : checklist ✓ / ✗ / ⚠ et, s'il existe un réseau local, test d'une adresse
  (1, 3, 5 ou 10 requêtes : envoyées, reçues, perte, min/moy/max).

## Codes infrarouges : aucune invention

Les 126 profils intégrés proviennent **sans modification** du dossier `Projectors` de la base
communautaire [Flipper-IRDB](https://github.com/Lucaslhm/Flipper-IRDB) (licence **CC0 1.0**,
voir `app/src/main/assets/irdb/SOURCE.txt`). Une touche absente d'un profil reste désactivée ;
un protocole non implémenté (Kaseikyo, 3 signaux Denon) est signalé et ignoré. Les tests
unitaires vérifient que la capture brute réelle d'une télécommande Epson se décode exactement
vers le code « Power » publié pour Epson (NECext 83 55 / 90 6F), ce qui valide les encodeurs
face à un signal réel.

## Sécurité

L'application utilise uniquement les moyens prévus par les fabricants : télécommande IR,
association Bluetooth standard (codes et confirmations respectés), PJLink avec le mot de passe
saisi par l'utilisateur. Elle ne contacte que des adresses IPv4 **privées** d'un sous-réseau
auquel le téléphone est directement relié, une adresse à la fois, sans balayage, sans Internet,
sans contournement d'authentification ni de pare-feu.

## Licence

Code : MIT (voir [LICENSE](LICENSE)). Codes IR : CC0 1.0 (Flipper-IRDB).
Dépendance : [usb-serial-for-android](https://github.com/mik3y/usb-serial-for-android) (MIT).
Firmware : utilise [Arduino-IRremote](https://github.com/Arduino-IRremote/Arduino-IRremote) (MIT).
