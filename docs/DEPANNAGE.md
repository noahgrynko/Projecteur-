# 18. Résolution des problèmes

| Message / symptôme | Cause probable | Solution |
|---|---|---|
| **Aucun émetteur infrarouge intégré détecté…** | Normal sur Pixel 9a | Brancher un émetteur IR USB-C ([MATERIEL_IR.md](MATERIEL_IR.md)) |
| **Périphérique USB non reconnu (aucun pilote série)** | Accessoire ni CDC, ni CH34x/CP210x/FTDI, ni IR Toy | Accessoire non pris en charge ; utiliser un modèle listé |
| **Émetteur IR externe non compatible** | Le périphérique n'a pas répondu `S01`/`PROJIR` | Vérifier le firmware (moniteur série : `I`), débrancher/rebrancher, réessayer |
| **Autorisation USB refusée** | Refus dans la boîte de dialogue Android | Débrancher/rebrancher puis *Autoriser et vérifier* |
| Émetteur prêt mais le projecteur ne réagit pas | Mauvais profil, mauvais angle, distance, capteur masqué | *Tester ces profils un par un* ; viser le capteur ; se rapprocher ; essayer une autre touche (Menu) |
| **Aucun profil IR compatible** | Aucun profil de la liste ne réagit | Autre marque / « Marque inconnue », importer un `.ir`, apprendre les codes (récepteur) |
| **Commande non disponible** | Touche absente du profil ou du protocole | Normal : la touche est désactivée plutôt qu'inventée ; voir *Tous les signaux* |
| **Protocole IR « Kaseikyo » non pris en charge** | Protocole non implémenté | Utiliser un autre profil ou apprendre le signal (enregistré en brut) |
| Power n'éteint pas | Beaucoup de projecteurs demandent 2 appuis | Appuyer à nouveau après ~1 s |
| **Bluetooth indisponible / désactivé** | Adaptateur coupé | Activer le Bluetooth (mode avion : le réactiver manuellement) |
| **Autorisation Bluetooth refusée** | « Appareils à proximité » refusé | Paramètres → Applications → Projecteur Remote → Autorisations |
| **Aucun appareil Bluetooth trouvé** | Projecteur sans Bluetooth, BT éteint, hors de portée | Normal pour beaucoup de projecteurs ; passer à l'IR |
| Services « A2DP » seulement | Bluetooth audio | **Pas de contrôle possible** par Bluetooth |
| **Non connecté… n'accepte peut-être pas les télécommandes Bluetooth** | Projecteur sans HID hôte, ou association non confirmée | Confirmer sur le projecteur ; sinon utiliser l'IR |
| **Profil HID Device non fourni par ce téléphone** | Profil désactivé par le constructeur | Utiliser l'IR ou le réseau |
| **Aucune interface réseau locale détectée** | Pas de Wi-Fi/Ethernet | Normal en salle sans réseau ; rejoindre le Wi-Fi du projecteur s'il existe |
| **Appareil non joignable** | Projecteur éteint, LAN désactivé en veille, mauvaise IP | Vérifier l'IP ; activer « réseau en veille » sur le projecteur (menu) |
| **Connexion refusée … PJLink désactivé** | PJLink désactivé ou absent | Activer PJLink dans le menu réseau du projecteur ; sinon IR |
| **Ce projecteur exige le mot de passe PJLink** / **Mot de passe PJLink incorrect** | Authentification PJLink active | Saisir le mot de passe défini dans le menu du projecteur (aucune tentative de le deviner) |
| **Commande indisponible pour le moment (ERR3)** | Veille, préchauffage ou refroidissement | Patienter 30–60 s |
| **Le projecteur signale une panne (ERR4)** | Lampe, température, filtre | Voir le voyant/erreur du projecteur |
| Ping : 100 % de perte mais TCP répond | ICMP filtré par l'appareil | Utiliser le mode TCP (port 4352) |
| **Adresse non privée refusée** / **hors des réseaux locaux** | Garde-fou de sécurité | Saisir une adresse privée du sous-réseau affiché |
| Wi-Fi du projecteur coupé par Android | « Pas d'accès Internet » | Choisir « Rester connecté » dans la notification Android |
| Compilation : `SDK location not found` | `local.properties` absent | `echo "sdk.dir=/chemin/Android/Sdk" > local.properties` |
| Compilation : dépendance JitPack introuvable | Pas d'Internet lors de la 1re compilation | Compiler une première fois avec Internet |
| Installation bloquée | Sources inconnues | Autoriser l'installation pour Fichiers/Chrome ; Play Protect → Installer quand même |

Rappel : l'application n'affiche jamais « Connecté » sans échange réel. Pour l'IR, l'absence de
retour est normale : le projecteur ne peut rien renvoyer.
