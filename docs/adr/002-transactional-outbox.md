# ADR-002: Transactional outbox cu publisher bazat pe polling

**Status:** acceptat

## Context
Un handler trebuie să modifice baza de date **și** să publice un mesaj în Kafka. Fără coordonare apare problema *dual write*: commit reușit + trimitere eșuată (mesaj pierdut, saga blocată) sau invers (mesaj fără efect).

## Decizie
Mesajul se scrie în tabelul `outbox` în **aceeași tranzacție** cu modificarea de business (`OutboxService.enqueue`, `Propagation.MANDATORY`). `OutboxPublisher` citește periodic rândurile nepublicate cu `SELECT ... FOR UPDATE SKIP LOCKED`, le trimite în Kafka așteptând confirmarea și le marchează publicate. Contextul de trace (traceId/spanId) se salvează în rând, ca publicarea asincronă să rămână în același trace.

## Garanții
Livrare **cel puțin o dată**: dacă procesul cade după confirmarea Kafka dar înainte de commit, rândul se retrimite. De aceea toți consumatorii sunt idempotenți (ADR-003). Ordinea per comandă e păstrată: cheia Kafka este `orderId`, iar publisherul citește în ordinea `id`.

## Alternative
* **Debezium / CDC pe WAL**: latență mică și fără polling, dar cere Kafka Connect și operare suplimentară. Recomandat la volum mare; migrarea e simplă, schema tabelului `outbox` rămâne aceeași.
* **Kafka transactions + DB**: nu rezolvă atomicitatea între două sisteme diferite.
* **Retry în aplicație după commit**: pierde mesajele dacă procesul cade între cele două operații.

## Consecințe
* (+) Fără mesaje pierdute; Kafka poate fi indisponibil fără ca cererile să eșueze (backlog vizibil în `outbox_pending`, alertă `OutboxBacklog`).
* (−) Latență adăugată de intervalul de polling (500 ms implicit, configurabil) și sarcină constantă ușoară pe baza de date (index parțial pe rândurile nepublicate).
* (−) Rândurile publicate se șterg periodic (job de cleanup, 1 oră).
