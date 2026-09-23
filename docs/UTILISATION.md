# 10–16. Utilisation

Navigation : barre du bas **🎛 Télécommande · 🔎 Rechercher · ⚙ Configuration · 🧪 Diagnostic**.
La Configuration donne accès à 🔴 Infrarouge, 📡 Apprentissage IR, 🔵 Bluetooth et 🌐 Réseau.

---

## 10. Configuration Bluetooth (🔵)

1. **Autoriser** « Appareils à proximité » (recherche et connexion). Refus → message
   « Autorisation Bluetooth refusée » ; se corrige dans Paramètres → Applications →
   Projecteur Remote → Autorisations.
2. Si le Bluetooth est désactivé : bouton **Activer le Bluetooth** (demande système).
3. **Rechercher (12 s)** : appareils classiques et BLE, avec nom, adresse, type, classe
   d'appareil, puissance du signal. « projecteur possible » n'est qu'un **indice** (nom ou classe
   « écran vidéo »).
4. **Services** : lit les services réellement annoncés (SDP ou GATT) et explique chacun :
   - *A2DP Audio Sink/Source* → **son uniquement, ne permet pas de contrôler le projecteur** ;
   - *AVRCP* → commandes de lecture audio, pas de contrôle du projecteur ;
   - *SPP / service propriétaire* → non documenté : rien n'est envoyé.
5. **Mode télécommande HID** (projecteurs qui acceptent télécommandes/claviers Bluetooth) :
   - **Associer** depuis la liste, ou depuis le menu Bluetooth du projecteur après
     **Rendre le téléphone visible (120 s)**. Si un **code** ou une **confirmation** apparaît,
     validez-le sur les deux appareils. L'application ne contourne aucune authentification.
   - Puis **Connecter HID**. L'état passe à « Connecté » uniquement quand Android confirme la
     connexion ; alors **Utiliser comme télécommande**.
   - Touches disponibles : Power, Vol±, Mute, Menu, flèches, OK, Retour, Exit. Source, HDMI, VGA,
     Freeze et Blank n'ont pas d'équivalent HID standard : désactivées.
   - Si la connexion échoue ou retombe : le projecteur n'accepte probablement pas les
     télécommandes Bluetooth. Le Bluetooth ne sert alors pas au contrôle.

## 11. Configuration infrarouge (🔴)

1. **Émetteur** : l'écran affiche l'émetteur intégré (absent sur Pixel 9a), les périphériques USB
   branchés avec leur état, et les sorties audio filaires.
2. **Profil** :
   - *Marque et modèle connus* → **Sélection automatique** : choisit le profil uniquement si la
     correspondance est sans ambiguïté (ex. « Epson » + « EB-X12 »).
   - Sinon : **filtrer par marque**, rechercher, toucher un profil pour le sélectionner.
   - **Tester ces profils un par un** : choisir la touche de test (Power, Menu, Source ou Blank —
     Menu/Blank sont moins gênants en cours), puis pour chaque profil : *Envoyer* → *Oui, il a
     réagi* (le profil est retenu) ou *Non, suivant*. Fin de liste → « Aucun profil compatible ».
   - **Importer .ir** : fichier au format Flipper « IR signals file » (par ex. téléchargé à
     l'avance depuis Flipper-IRDB).
   - **Tous les signaux** : liste toutes les touches du fichier (y compris Keystone, Zoom…) avec
     un bouton *Émettre*.
3. **🔴 Utiliser l'infrarouge avec ce profil** → la télécommande est active.
   L'IR étant unidirectionnel, l'état reste « Émetteur prêt (sans retour) ».

Conseils : visez le capteur IR (face avant ou arrière du projecteur, parfois l'écran pour les
projecteurs au plafond : l'IR se réfléchit sur l'écran blanc). Beaucoup de projecteurs demandent
**deux appuis sur Power** pour s'éteindre.

## 12. Émetteur IR USB-C

Voir [MATERIEL_IR.md](MATERIEL_IR.md) pour le choix et le câblage. Mise en service :
1. Brancher l'accessoire (adaptateur OTG si besoin). Android propose d'ouvrir Projecteur Remote.
2. 🔴 Infrarouge → **Autoriser et vérifier** → accepter la demande d'accès USB.
3. L'application ouvre le port et vérifie le protocole :
   - IR Toy/Irdroid : réponse `S01` → « Compatible — vérifié » ;
   - pont PROJIR : réponse `PROJIR 1 TX RX 1000` → « Compatible — vérifié » ;
   - aucune réponse correcte → « Émetteur IR externe non compatible » (rien n'est émis) ;
   - aucun pilote → « Périphérique USB non reconnu ».
4. **Émetteur audio (jack)** : bouton *Utiliser* à côté de la sortie audio, confirmation
   explicite (non vérifiable), volume média au maximum, puis essai réel sur le projecteur.
   Ne jamais laisser un casque branché dans ce mode.

## 13. Apprentissage IR (📡)

Requiert un **récepteur IR** (IR Toy/Irdroid, ou pont PROJIR avec TSOP38238). Sans récepteur :
« Un récepteur IR compatible est nécessaire… » — la fonction n'est pas simulée.
1. Choisir le **nom** (une touche de la télécommande, ex. « Volume + », ou un nom libre).
2. Choisir la **porteuse** (38 kHz par défaut ; un récepteur démodulé ne peut pas la mesurer ;
   SIRC Sony détecté → 40 kHz automatiquement).
3. **📡 Apprendre**, puis appuyer brièvement sur la touche de la télécommande d'origine à 5–10 cm
   du récepteur (10 s max).
4. Résultat : protocole reconnu (NEC, NECext, Samsung32, SIRC 12/15/20 bits, avec adresse et
   commande) ou **signal brut** conservé tel quel.
5. **Tester (réémettre)**, puis **Enregistrer**. Les commandes apprises (fichier `learned.ir` au
   format Flipper, dans le stockage privé de l'application) **remplacent** la touche de même nom
   du profil actif, ou forment à elles seules une télécommande.

Utile si vous pouvez emprunter la télécommande d'un collègue pour un modèle identique.

## 14. Détection du vidéoprojecteur (🔎)

Le bouton **🔎 Rechercher le vidéoprojecteur** essaie, dans l'ordre :
1. **Bluetooth** : 12 s de recherche ; les appareils ressemblant à un projecteur deviennent des
   *candidats non confirmés*.
2. **Réseau local** : s'il existe une interface locale, un seul message de recherche PJLink
   (`%2SRCH`, UDP 4352, diffusé sur le sous-réseau). Chaque projecteur qui répond est interrogé
   (nom, fabricant, modèle, classe, alimentation, entrées, lampe) → *projecteur détecté*.
3. **Connexion directe** : adaptateur USB-C → Ethernet actif ou non.
4. **Matériel IR** : émetteur disponible ou accessoire nécessaire.
5. **Autres interfaces** : aucune autre interface officielle accessible sans matériel dédié.

Pour chaque projecteur : marque, modèle, méthode, état, informations et commandes disponibles,
avec *Utiliser (PJLink)* ou *Profils IR de la marque* (sélection automatique du profil si le
modèle correspond). Si rien n'est trouvé : « Aucun projecteur trouvé » et l'indication du
matériel nécessaire. Un projecteur PJLink **classe 1** ne répond pas à la recherche : saisissez
son adresse IP dans 🌐 Réseau.

## 15. Télécommande (🎛)

```
            ⏻ POWER
   VOL −    MUTE    VOL +
   SOURCE  [HDMI]  [VGA]
              ▲
         ◀    OK    ▶
              ▼
   MENU    RETOUR    EXIT
      FREEZE    BLANK
```
En haut : **méthode utilisée** et **état de connexion**. Raccourcis : Rechercher, Configuration,
Apprentissage IR, Diagnostic. Les touches non prises en charge sont grisées ; HDMI/VGA
n'apparaissent que si la méthode active les propose. Le résultat de chaque appui s'affiche
sous la télécommande (envoyé, échec avec la raison, ou commande non disponible).

| Touche | IR | Bluetooth HID | PJLink |
|---|---|---|---|
| Power | si dans le profil | ✓ | ✓ (confirmation) |
| Vol ± | si dans le profil | ✓ | classe 2 |
| Mute | si dans le profil | ✓ | ✓ (AVMT 2x) |
| Source / HDMI / VGA | si dans le profil | ✗ | ✓ (INPT) |
| Menu, flèches, OK, Exit, Retour | si dans le profil | ✓ | ✗ |
| Freeze | si dans le profil | ✗ | classe 2 |
| Blank | si dans le profil (Blank/AV Mute) | ✗ | ✓ (AVMT 1x) |

## 16. Diagnostic (🧪)

**Test automatique** (checklist ✓ Disponible · ✗ Indisponible · ⚠ Nécessite un accessoire) :
Bluetooth, profil HID, réseau local, périphérique USB, émetteur IR, récepteur IR, profils IR
chargés, vidéoprojecteur détecté, connexion établie, commandes disponibles.

**Diagnostic réseau** — seulement si une interface réseau locale existe ; sinon :
« Diagnostic réseau indisponible : aucune interface réseau locale détectée. »
1. Saisir une adresse IP **privée** du réseau local (ex. 192.168.1.20).
2. Choisir 1, 3, 5 ou 10 requêtes, et la méthode : **ICMP (ping)** ou **TCP** (port 4352 par
   défaut ; une connexion refusée prouve aussi que l'appareil répond).
3. Cocher « Je suis autorisé(e) à tester cet appareil », puis **Lancer**.
4. Résultats : requêtes envoyées, réponses reçues, perte, temps minimum, moyen et maximum.

Refusés : adresses publiques, noms d'hôte, adresses hors des sous-réseaux du téléphone, plages.
