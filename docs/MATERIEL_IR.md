# 12. Matériel infrarouge pour le Pixel 9a

Le Pixel 9a **n'a pas d'émetteur IR**. Pour le mode 🔴 Infrarouge, il faut l'un des accessoires
ci-dessous, branché sur le port USB-C (avec un adaptateur **OTG USB-C → USB-A femelle** si
l'accessoire a une prise USB-A).

| Accessoire | Émission | Réception (apprentissage) | Vérification par l'app | Remarques |
|---|---|---|---|---|
| **Irdroid USB IR Transceiver** / **USB IR Toy v2** | ✓ | ✓ | ✓ protocole `S01` | Prêt à l'emploi ; protocole public ; ID USB 04D8:FD08 |
| **Pont PROJIR** (à fabriquer, firmware fourni) | ✓ | ✓ avec TSOP38238 | ✓ réponse `PROJIR` | ~10–15 € de composants ; portée réglable |
| **Émetteur IR « jack »** + adaptateur USB-C → jack (à DAC) | ✓ (faible) | ✗ | ✗ (non vérifiable) | Portée courte ; dépend du niveau de sortie de l'adaptateur |
| Dongle IR USB-C avec appli propriétaire | ✗ | ✗ | — | Protocole non documenté : **non pris en charge** |

## Pont PROJIR (recommandé si vous pouvez souder ou utiliser une plaque d'essai)

### Composants
- Une carte : **Raspberry Pi Pico** (recommandé, USB natif), Arduino **Leonardo/Pro Micro**
  (USB natif), ou Arduino **Nano** (convertisseur CH340/FT232, pris en charge).
- LED IR 940 nm (ex. TSAL6200), transistor NPN (2N2222 / BC337), résistance de base 1 kΩ,
  résistance de LED 33 Ω.
- Optionnel pour l'apprentissage : récepteur **TSOP38238** (38 kHz).
- Câble USB adapté + adaptateur OTG USB-C.

### Câblage
```
Broche d'émission ──[1 kΩ]──▶ base NPN
                              émetteur NPN ── GND
5 V (VBUS) ──[33 Ω]──▶ anode LED IR ; cathode LED ── collecteur NPN

TSOP38238 (face bombée vers soi) : broche 1 OUT → broche de réception
                                   broche 2 GND → GND
                                   broche 3 VS  → 3,3 V (Pico) ou 5 V (Arduino)
```

| Carte | Broche d'émission | Broche de réception |
|---|---|---|
| Raspberry Pi Pico | GP3 | GP2 |
| Arduino Leonardo / Pro Micro | 9 (imposée par le timer d'IRremote) | 2 |
| Arduino Nano / Uno | 3 (imposée par le timer d'IRremote) | 2 |

Sans récepteur, mettre `#define HAS_RECEIVER 0` dans le firmware.

### Programmation (avec Internet, avant la salle)
1. Installer l'[IDE Arduino](https://www.arduino.cc/en/software).
2. Gestionnaire de bibliothèques → installer **IRremote** (version 4.4 ou plus récente).
3. Pour le Pico : ajouter l'URL de cartes
   `https://github.com/earlephilhower/arduino-pico/releases/download/global/package_rp2040_index.json`
   puis installer « Raspberry Pi Pico/RP2040 ».
4. Ouvrir `firmware/projir_bridge/projir_bridge.ino`, choisir la carte, **Téléverser**.
5. Test depuis l'ordinateur (moniteur série 115200 bauds, fin de ligne « LF ») : taper `I` →
   `PROJIR 1 TX RX …`.

Le firmware a été compilé avec succès pour Arduino Leonardo, Arduino Nano et Raspberry Pi Pico
(IRremote 4.7.1).

### Portée
Avec 33 Ω sous 5 V (~100 mA en impulsions), comptez plusieurs mètres. Visez le capteur du
projecteur ; pour un projecteur au plafond, visez l'écran de projection ou le projecteur directement.

## Émetteur IR « jack » (audio)
Deux LED IR montées **tête-bêche** entre la pointe (gauche) et l'anneau (droite) d'une fiche
jack 3,5 mm (des dongles tout faits existent). Le Pixel 9a n'ayant pas de prise jack, il faut un
**adaptateur USB-C → jack avec DAC intégré**. L'application génère une sinusoïde à la moitié de
la porteuse (19 kHz pour 38 kHz) en opposition de phase sur les deux voies.

Limites honnêtes : puissance faible (portée souvent 1–3 m), certains adaptateurs filtrent les
fréquences proches de 20 kHz ou limitent le volume ; Android ne peut pas savoir si une LED est
branchée. Mettre le **volume média au maximum** et **ne jamais brancher de casque** dans ce mode.

## Pourquoi pas un dongle IR USB-C « pour smartphone » quelconque ?
La plupart fonctionnent uniquement avec l'application de leur fabricant, via un protocole non
publié. Sans documentation officielle, l'application ne peut pas les piloter correctement et ne
prétend pas le faire : ils apparaissent comme « non reconnus » ou « non compatibles ».
