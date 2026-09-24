# 2. Architecture

## 2.1 Vue d'ensemble

```
┌──────────────────────────── Application Android (APK autonome) ────────────────────────────┐
│  UI Jetpack Compose : Télécommande · Rechercher · Configuration · Diagnostic                │
│        │                                                                                    │
│  RemoteController ── méthode active (ControlTransport) ─┬─ IrTransport ──────┐              │
│                                                         ├─ BluetoothHidRemote │              │
│                                                         └─ PjLinkTransport    │              │
│                                                                               │              │
│  IrProfileRepository (126 profils CC0 embarqués + importés + appris)          │              │
│  IrProtocols / IrDecoder / FlipperIrFormat / CommandMapper                    │              │
│  IrHardwareManager ─ BuiltInIrEmitter · IrToyEmitter · SerialBridgeEmitter · AudioIrEmitter │
│  BluetoothScanner (SDP/GATT) · BluetoothServices (interprétation)                          │
│  NetworkInspector · PjLinkClient/Discovery · PingDiagnostic · LocalAddress (garde-fous)     │
│  ProjectorDiscovery (🔎) · AppContainer.selfTest (✓ ✗ ⚠)                                    │
└─────────────┬──────────────────────────┬──────────────────────────────┬────────────────────┘
              │ USB-C (USB Host)         │ Bluetooth (HID Device)       │ Wi-Fi / Ethernet USB-C
      Émetteur IR externe          Projecteur « smart »            Projecteur PJLink
              │ lumière IR
         Vidéoprojecteur
```

Aucun composant ne dépend d'Internet. Aucun composant ne capture, n'encode ou n'envoie l'écran.

## 2.2 Principes

- **Un seul contrat** : `ControlTransport` (`send(RemoteCommand)`, `state`, `supportedCommands`).
  La télécommande désactive automatiquement les touches absentes de `supportedCommands`.
- **Pas d'état mensonger** : `ConnectionState.Connected` n'est émis qu'après un échange réel
  (bannière + réponse PJLink, `STATE_CONNECTED` HID). L'IR utilise `ReadyOneWay`.
- **Pas de code inventé** : les trames IR sont générées uniquement à partir des fichiers de la
  base (ou des captures de l'utilisateur) et de protocoles publics. Protocole inconnu → exception
  explicite (`UnsupportedProtocolException`).
- **Matériel vérifié** : un accessoire USB n'est « compatible » qu'après une réponse correcte à
  son protocole (`S01` pour l'IR Toy, `PROJIR …` pour le pont).
- **Garde-fous réseau** (`LocalAddress.check`) : IPv4 littérale, privée, dans un sous-réseau local
  du téléphone, jamais l'adresse du téléphone, jamais de nom DNS.

## 2.3 Arborescence

```
Projecteur-/
├── README.md                     Présentation, matrice « Pixel 9a seul / accessoire »
├── LICENSE                       MIT (code) ; codes IR : CC0
├── build.gradle.kts, settings.gradle.kts, gradle.properties, gradlew, gradle/wrapper/
├── .github/workflows/android.yml CI : tests, APK, firmware ; release sur tag v*
├── docs/                         Documentation (ce dossier)
├── firmware/projir_bridge/
│   └── projir_bridge.ino         Firmware du pont IR USB (Arduino/RP2040/ESP32, IRremote)
├── tools/update_irdb.sh          Mise à jour de la base Flipper-IRDB (avec Internet)
└── app/
    ├── build.gradle.kts
    └── src/
        ├── main/
        │   ├── AndroidManifest.xml          Autorisations, filtre USB
        │   ├── assets/irdb/                 Projectors/<Marque>/*.ir (126 fichiers), SOURCE.txt, LICENSE-CC0.txt
        │   ├── res/                         Icône, thème, filtres USB, config réseau
        │   └── java/com/projecteur/remote/
        │       ├── ProjecteurApp.kt, MainActivity.kt, AppContainer.kt
        │       ├── core/          RemoteCommand, ControlTransport/ConnectionState, RemoteController
        │       ├── ir/            IrSignal, IrProtocols, IrDecoder, FlipperIrFormat, CommandMapper,
        │       │                  IrProfile, IrProfileRepository, IrTransport
        │       ├── ir/emitters/   IrEmitter, IrHardwareManager, BuiltInIrEmitter, IrToyEmitter,
        │       │                  SerialBridgeEmitter, SerialLink, AudioIrEmitter
        │       ├── bluetooth/     BluetoothScanner, BluetoothServices, BluetoothHidRemote, HidReports
        │       ├── network/       NetworkInspector, LocalAddress, PjLink, PjLinkClient,
        │       │                  PjLinkTransport, PjLinkDiscovery, PingDiagnostic
        │       ├── discovery/     ProjectorDiscovery
        │       ├── diagnostics/   CheckItem
        │       ├── data/          Settings (préférences locales)
        │       └── ui/            Thème, composants, écrans (Remote, Discovery, Config, Ir, Learn,
        │                          Bluetooth, Network, Diagnostic)
        └── test/java/…            Tests JVM : protocoles IR, base intégrée, PJLink, garde-fous réseau
```

## 2.4 Flux d'une commande

1. L'utilisateur appuie sur **VOL +**.
2. `RemoteController.send(VOL_UP)` appelle le transport actif.
3. Selon la méthode :
   - **IR** : `CommandMapper` a associé `VOL_UP` à l'entrée « Vol_up » du profil →
     `IrProtocols.encode("NECext", …)` → `IrSignal(38 kHz, durées)` → émetteur
     (IR Toy : unités de 21,33 µs ; pont : ligne `T 38 67 …` ; audio : PCM 48 kHz).
   - **Bluetooth HID** : usage Consumer Control 0xE9 (appui puis relâchement).
   - **PJLink** : `%2SVOL 1` sur TCP 4352, réponse `%2SVOL=OK`.
4. Le résultat (envoyé / échec avec raison / non disponible) s'affiche sous la télécommande.

## 2.5 Dépendances

| Bibliothèque | Rôle | Licence |
|---|---|---|
| AndroidX (Compose, Activity, Navigation, Lifecycle, Core) | Interface | Apache 2.0 |
| kotlinx-coroutines | Asynchronisme | Apache 2.0 |
| usb-serial-for-android 3.8.1 (JitPack) | Pilotes USB CDC/CH34x/CP210x/FTDI | MIT |

Toutes sont téléchargées **à la compilation** puis incluses dans l'APK : aucune n'est requise
en salle.
