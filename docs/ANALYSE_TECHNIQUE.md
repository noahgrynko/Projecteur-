# 1. Analyse technique

## 1.1 Capacités matérielles du Google Pixel 9a utiles ici

| Élément | Présent ? | Conséquence |
|---|---|---|
| Émetteur infrarouge (« IR blaster ») | **Non** | Impossible d'émettre de l'IR sans accessoire. L'application le vérifie à l'exécution via `ConsumerIrManager.hasIrEmitter()` et `PackageManager.FEATURE_CONSUMER_IR`. |
| Récepteur infrarouge | **Non** | L'apprentissage IR exige un récepteur externe. |
| Bluetooth classique + BLE | Oui | Recherche, lecture des services, association, et profil **HID Device** (le téléphone peut se présenter comme une télécommande/un clavier). |
| Wi-Fi | Oui | Utilisable seulement si un réseau existe (ex. Wi-Fi créé par le projecteur). |
| USB-C avec mode hôte (OTG) | Oui | Permet de brancher un émetteur IR USB, un adaptateur USB-C → Ethernet ou un adaptateur audio USB-C → jack. |
| Prise jack 3,5 mm | Non | Un adaptateur USB-C → jack est nécessaire pour un émetteur IR « audio ». |

Les valeurs affichées dans l'application ne reposent pas sur ce tableau : elles sont
**mesurées sur le téléphone** au lancement (page 🧪 Diagnostic).

## 1.2 Solutions étudiées

### Infrarouge (la télécommande d'origine)
C'est le moyen universel : presque tous les projecteurs de salle de cours ont un capteur IR.
Le Pixel 9a n'ayant pas d'émetteur, il faut **un accessoire**. L'application prend en charge des
accessoires dont le protocole est **publié** :

1. **USB IR Toy v2 (Dangerous Prototypes) / Irdroid USB IR Transceiver** — émission + réception,
   protocole « sampling mode » documenté publiquement, USB CDC (identifiant 04D8:FD08).
2. **Pont PROJIR** (fourni dans `firmware/`) — une carte Arduino/RP2040/ESP32 + une LED IR
   (+ un récepteur TSOP38238 en option) ; protocole texte simple documenté dans
   [FIRMWARE_PROTOCOL.md](FIRMWARE_PROTOCOL.md). Firmware vérifié à la compilation pour
   Arduino Leonardo, Arduino Nano et Raspberry Pi Pico.
3. **Émetteur IR « audio »** (LED IR sur prise jack, via adaptateur USB-C → jack) — technique
   connue : une sinusoïde à f/2 en opposition de phase sur les deux voies fait clignoter deux LED
   tête-bêche à f (38 kHz). **Limite** : Android ne voit qu'une sortie audio, il est impossible de
   vérifier qu'une LED y est branchée ; le mode est donc activé manuellement et doit être validé
   par un essai.
4. **Émetteur IR intégré** (autres téléphones : certains Xiaomi, etc.) via l'API officielle
   `ConsumerIrManager` — géré automatiquement s'il existe.

Les petits dongles IR USB-C vendus « pour smartphone » avec une application propriétaire
utilisent souvent un protocole **non documenté** : ils ne sont pas pris en charge (sauf s'ils
sont de type audio). L'application les affiche comme « périphérique USB non reconnu » ou
« émetteur IR externe non compatible » au lieu de prétendre les utiliser.

### Bluetooth
Il n'existe **aucun profil Bluetooth standard de contrôle de vidéoprojecteur**. Cas réels :
- **Bluetooth audio (A2DP)** : le projecteur sert d'enceinte ou envoie son son vers une enceinte.
  → **Aucun contrôle possible.** L'application l'explique en lisant les services annoncés.
- **Projecteur « smart » (Android/Google TV, ex. XGIMI, Nebula, Dangbei…)** : il accepte des
  télécommandes et claviers Bluetooth (HID). Le téléphone peut s'y connecter **comme une
  télécommande Bluetooth standard** grâce à l'API officielle `BluetoothHidDevice` (Android 9+) :
  flèches, OK, Retour, Menu, Volume, Mute, Power.
- **Service série (SPP) ou service BLE propriétaire** : sans documentation officielle du
  fabricant, rien n'est envoyé.

### Réseau local (PJLink)
PJLink est la norme ouverte de contrôle des vidéoprojecteurs (Epson, NEC/Sharp, Panasonic, Sony,
Hitachi/Maxell, Canon, BenQ, Optoma…). Elle nécessite une liaison IP locale :
- **Wi-Fi créé par le projecteur** (« Simple AP », « Quick Wireless ») : le téléphone le rejoint
  même sans Internet ; l'application lie ses sockets à ce réseau (`Network.socketFactory`) pour
  qu'Android n'envoie pas le trafic vers la 4G/5G.
- **Câble LAN** : adaptateur USB-C → Ethernet relié au port LAN du projecteur. Le projecteur et le
  téléphone doivent être dans le même sous-réseau (DHCP côté projecteur ou adressage lien-local).
- Contrôle possible : Power, Source/HDMI/VGA, Mute, Blank (classe 1), Volume et Freeze (classe 2).
  Pas de menus ni de flèches (la norme ne les prévoit pas).

### Autres interfaces
- **HDMI-CEC** : le téléphone n'est pas une source HDMI ici (et ne doit pas l'être), et Android
  n'expose pas le CEC aux applications. Écarté.
- **RS-232** : possible avec un câble USB-série et le protocole propre au fabricant ; non intégré
  (protocoles très variables). Écarté de cette version.

## 1.3 Pourquoi un simple site web (ou GitHub Pages) ne suffit pas

| Besoin | Navigateur (Chrome Android) | Application native |
|---|---|---|
| Émetteur IR intégré | Aucune API | `ConsumerIrManager` |
| Profil Bluetooth HID Device (être une télécommande) | Impossible (Web Bluetooth = client BLE GATT seulement, pas de Bluetooth classique, pas de rôle périphérique) | `BluetoothHidDevice` |
| Lire les services SDP d'un appareil Bluetooth classique | Impossible | `fetchUuidsWithSdp()` |
| Accessoire USB série (IR Toy, Arduino) | WebUSB/Web Serial limités ou absents sur Android | API USB Host + pilotes |
| Socket TCP vers le port 4352 (PJLink), diffusion UDP | Impossible (pas de sockets bruts dans le navigateur) | `java.net.Socket`, `DatagramSocket` |
| Forcer le trafic sur un Wi-Fi sans Internet | Impossible | `ConnectivityManager` / `Network` |
| Fonctionnement garanti sans Internet | Dépend du cache | APK autonome |

GitHub Pages ne peut donc pas être la solution principale. GitHub sert uniquement à **héberger
le code et distribuer l'APK** avant d'aller en salle.

## 1.4 Architecture retenue

Application **Android native** (Kotlin, Jetpack Compose, minSdk 28, targetSdk 35), sans
serveur, sans service cloud, avec la base de codes IR embarquée dans l'APK. Détails :
[ARCHITECTURE.md](ARCHITECTURE.md).
