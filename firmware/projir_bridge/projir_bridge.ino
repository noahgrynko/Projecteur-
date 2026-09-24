/*
 * PROJIR — pont infrarouge USB pour l'application Android « Projecteur Remote ».
 *
 * Matériel : une carte avec USB natif ou convertisseur USB-série (Raspberry Pi Pico,
 * ESP32-S3, Arduino Leonardo/Pro Micro, Arduino Nano…), une LED IR 940 nm pilotée par un
 * transistor, et en option un récepteur démodulé TSOP38238 pour l'apprentissage.
 * Schéma et câblage : docs/MATERIEL_IR.md.
 *
 * Bibliothèque requise : IRremote >= 4.4 (Arduino-IRremote, licence MIT),
 * à installer AVANT d'aller en salle (Gestionnaire de bibliothèques de l'IDE Arduino).
 *
 * Protocole série, 115200 bauds, lignes terminées par '\n' (docs/FIRMWARE_PROTOCOL.md) :
 *   I                       -> PROJIR 1 TX [RX] <max_durées>
 *   T <kHz> <n> d1 ... dn   -> OK | ERR <message>      (durées en µs, impulsion en premier)
 *   L <timeout_ms>          -> R <n> d1 ... dn | TIMEOUT
 */
#include <Arduino.h>

// ---- Configuration ------------------------------------------------------------------------
#define HAS_RECEIVER 1          // 0 si aucun récepteur TSOP n'est câblé
#define IR_RECEIVE_PIN 2        // sortie du TSOP38238

#if defined(__AVR__)
  #define MAX_DURATIONS 200     // mémoire limitée (2 Ko de RAM)
  #define RAW_BUFFER_LENGTH 200
  // Sur AVR, la broche d'émission est imposée par le timer matériel utilisé par IRremote :
  // broche 9 sur Leonardo/Pro Micro (ATmega32U4), broche 3 sur Uno/Nano (ATmega328P).
  #if defined(__AVR_ATmega32U4__)
    #define AVR_SEND_PIN 9
  #else
    #define AVR_SEND_PIN 3
  #endif
#else
  #define MAX_DURATIONS 1000
  #define RAW_BUFFER_LENGTH 750
  #define IR_SEND_PIN 3         // broche GPIO de la LED IR (via transistor)
#endif

#define DISABLE_CODE_FOR_RECEIVER_IF_NOT_USED
#define NO_LED_FEEDBACK_CODE
#include <IRremote.hpp>

static uint16_t durations[MAX_DURATIONS];

// ---- Lecture de jetons sans stocker la ligne entière (économie de RAM) ----------------------
static bool lineEnded = false;

static int readCharTimeout(unsigned long timeoutMs) {
  unsigned long start = millis();
  while (!Serial.available()) {
    if (millis() - start > timeoutMs) return -1;
  }
  return Serial.read();
}

// Lit un jeton ; renvoie false en fin de ligne ou délai dépassé.
static bool readToken(char *buf, size_t size) {
  size_t len = 0;
  if (lineEnded) return false;
  int c;
  do {                                  // saute les espaces
    c = readCharTimeout(1000);
    if (c < 0) return false;
    if (c == '\n') { lineEnded = true; return false; }
  } while (c == ' ' || c == '\r' || c == '\t');
  while (c >= 0 && c != ' ' && c != '\n' && c != '\r' && c != '\t') {
    if (len + 1 < size) buf[len++] = (char)c;
    c = readCharTimeout(1000);
  }
  if (c == '\n') lineEnded = true;
  buf[len] = 0;
  return len > 0;
}

static void skipLine() {
  while (!lineEnded) {
    int c = readCharTimeout(200);
    if (c < 0 || c == '\n') lineEnded = true;
  }
}

// ---- Commandes ----------------------------------------------------------------------------
static void cmdIdentify() {
  Serial.print(F("PROJIR 1 TX"));
  if (HAS_RECEIVER) Serial.print(F(" RX"));
  Serial.print(' ');
  Serial.println(MAX_DURATIONS);
}

static void cmdTransmit() {
  char tok[12];
  if (!readToken(tok, sizeof tok)) { Serial.println(F("ERR frequence manquante")); return; }
  long khz = atol(tok);
  if (!readToken(tok, sizeof tok)) { Serial.println(F("ERR nombre manquant")); return; }
  long n = atol(tok);
  if (khz < 20 || khz > 60) { skipLine(); Serial.println(F("ERR frequence hors plage (20-60 kHz)")); return; }
  if (n < 1 || n > MAX_DURATIONS) { skipLine(); Serial.println(F("ERR trop de durees")); return; }
  for (long i = 0; i < n; i++) {
    if (!readToken(tok, sizeof tok)) { Serial.println(F("ERR durees incompletes")); return; }
    long d = atol(tok);
    if (d < 1) d = 1;
    if (d > 65535) d = 65535;
    durations[i] = (uint16_t)d;
  }
  skipLine();
  IrSender.sendRaw(durations, (uint_fast16_t)n, (uint_fast8_t)khz);
  Serial.println(F("OK"));
}

static void cmdLearn() {
  char tok[12];
  unsigned long timeoutMs = readToken(tok, sizeof tok) ? (unsigned long)atol(tok) : 10000UL;
  skipLine();
#if HAS_RECEIVER
  IrReceiver.start();
  IrReceiver.resume();
  unsigned long start = millis();
  while (millis() - start < timeoutMs) {
    if (IrReceiver.decode()) {
      uint16_t rawlen = IrReceiver.decodedIRData.rawlen;
      uint16_t count = rawlen > 1 ? rawlen - 1 : 0;   // rawbuf[0] est inutilisé (compatibilité)
      Serial.print(F("R "));
      Serial.print(count);
      for (uint16_t i = 1; i <= count; i++) {
        long us = (long)IrReceiver.irparams.rawbuf[i] * MICROS_PER_TICK;
        // Compensation classique du retard du récepteur démodulé.
        us += (i % 2 == 1) ? -MARK_EXCESS_MICROS : MARK_EXCESS_MICROS;
        if (us < 1) us = 1;
        Serial.print(' ');
        Serial.print(us);
      }
      Serial.println();
      IrReceiver.stop();
      return;
    }
  }
  IrReceiver.stop();
  Serial.println(F("TIMEOUT"));
#else
  Serial.println(F("ERR pas de recepteur"));
#endif
}

void setup() {
  Serial.begin(115200);
#if defined(__AVR__)
  IrSender.begin(AVR_SEND_PIN);
#else
  IrSender.begin();   // IR_SEND_PIN défini plus haut
#endif
#if HAS_RECEIVER
  IrReceiver.begin(IR_RECEIVE_PIN, DISABLE_LED_FEEDBACK);
  IrReceiver.stop();
#endif
}

void loop() {
  char tok[12];
  lineEnded = false;
  if (!Serial.available()) return;
  if (!readToken(tok, sizeof tok)) return;
  if (strcmp(tok, "I") == 0) { skipLine(); cmdIdentify(); }
  else if (strcmp(tok, "T") == 0) cmdTransmit();
  else if (strcmp(tok, "L") == 0) cmdLearn();
  else { skipLine(); Serial.println(F("ERR commande inconnue")); }
}
