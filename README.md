# SMTP Server Hausaufgabe

Dieses Projekt implementiert einen einfachen SMTP-Server gemäß [RFC 821](https://datatracker.ietf.org/doc/html/rfc821),
inklusive Unterstützung für grundlegende Kommandos und Nachrichtenpersistenz.

---

## Ausführung

### Projekt herunterladen
- Projektordner in IntelliJ IDEA:
  - Startseite -> Clone Repository -> Https Link einfügen

### Server starten
- Executable main in `SMTPServer` 
  (Pfad: `src/main/java/smtp/SMTPServer.java`)  
  - Ausführung ohne Argumente

---

## Automatische Ausführung mit Vorgabe-Client

Während der Host aktiv ist, führe den `SMTPClient` aus dem SMTPClient Ordner aus [ISIS](https://isis.tu-berlin.de/mod/folder/view.php?id=2004181) mit folgenden Argumenten aus:

java SMTPClient localhost 8025

---

## Manuelle Ausführung mit Netcat

### Linux/macOS

Im Terminal:

netcat localhost 8025

### Windows (mit Ncat/Nmap)

Im Terminal:

ncat localhost 8025

- Ncat ist Teil von [Nmap](https://nmap.org/ncat/)

---

## Nachrichten-Speicherung

Nach erfolgreicher Eingabe von `DATA` und Bestätigung durch `250 OK` wird die Nachricht gespeichert.

**Dateiablage** erfolgt unter:

```
<Projektverzeichnis>/data/<recipient>/<sender>_<messageId>
```

### Beispiel:

```
data/ghi@jkl.com/abc@def.edu_4029
```

- Die Verzeichnisse werden automatisch erstellt
- Nachrichteninhalt entspricht dem vom Client gesendeten Text und werden mit einem Timestamp am Anfang der Mail ausgestattet.
- ID wird zufällig vergeben

---

## Unterstützte SMTP-Kommandos

| Kommando         | Beschreibung                        |
|------------------|-------------------------------------|
| `HELO`           | Begrüßung des Servers               |
| `MAIL FROM:<...>`| Absenderadresse festlegen          |
| `RCPT TO:<...>`  | Empfängeradresse festlegen         |
| `DATA`           | Startet Nachrichteneingabe         |
| `QUIT`           | Beendet die Session                |
| `HELP [command]` | Optionales Hilfe-Kommando          |

---

## Relevante Projektstruktur (Auszug)

```
├── data/
│
└── src/
    └── main/
        └── java/
            ├── file/
            │   └── MailStorageService.java
            └── smtp/
                ├── model/
                │    ├── ClientSessionState.java
                │    ├── SMTPCommand.java
                │    └── SMTPCommandType.java
                ├── util/
                │    └── SMTPCommandParser.java
                └── SMTPServer.java
                     
```
---

Viel Erfolg beim Testen & Verstehen!
