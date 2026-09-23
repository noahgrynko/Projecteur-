# Protocole série du pont PROJIR

Liaison série USB, **115200 bauds, 8N1**, lignes ASCII terminées par `\n` (un `\r` final est
toléré). Durées en **microsecondes**, en commençant par une impulsion (LED allumée, modulée).

| Requête | Réponse | Rôle |
|---|---|---|
| `I` | `PROJIR 1 TX RX 1000` | Identification : version, capacités (`RX` absent sans récepteur), nombre max. de durées |
| `T <kHz> <n> <d1> … <dn>` | `OK` ou `ERR <message>` | Émet `n` durées avec une porteuse de `kHz` (20–60) |
| `L <timeout_ms>` | `R <n> <d1> … <dn>` ou `TIMEOUT` | Attend un signal IR et renvoie les durées mesurées |

Exemples :
```
> I
< PROJIR 1 TX RX 1000
> T 38 67 9000 4500 560 1690 560 1690 560 560 … 560
< OK
> L 10000
< R 67 8950 4480 580 1670 …
```

Notes :
- Le nombre maximal de durées dépend de la carte (200 sur AVR, 1000 sur RP2040/ESP32).
- Les durées reçues sont corrigées de ±`MARK_EXCESS_MICROS` (retard typique d'un TSOP).
- La porteuse d'un signal appris n'est pas mesurable avec un récepteur démodulé ; l'application
  la fait choisir à l'utilisateur (38 kHz par défaut).
- Implémentation côté Android : `app/src/main/java/com/projecteur/remote/ir/emitters/SerialBridgeEmitter.kt`.
