# ADR-003: Consumatori idempotenți cu tabel `processed_message`

**Status:** acceptat

## Context
Outbox + Kafka garantează livrare *cel puțin o dată*. Același mesaj poate ajunge de mai multe ori (retrimitere după crash, rebalansare de consumatori, retry-uri).

## Decizie
Fiecare handler începe cu `IdempotencyGuard.firstTime(messageId, consumer)`: `INSERT ... ON CONFLICT DO NOTHING` în `processed_message`, **în aceeași tranzacție** cu efectele handlerului. Dacă inserarea nu adaugă niciun rând, mesajul e un duplicat și handlerul nu face nimic. Dacă tranzacția se anulează, se anulează și înregistrarea, deci reîncercarea procesează mesajul normal.

Orchestratorul adaugă o a doua plasă: reacționează doar dacă starea comenzii îl permite (un `PAYMENT_COMPLETED` pe o comandă `PENDING` este ignorat).

La API, `Idempotency-Key` + index unic pe `orders.idempotency_key` protejează de dublarea cererilor clientului, inclusiv când două cereri identice sosesc simultan.

## Consecințe
* (+) Comportament corect sub retrimiteri; acoperit de teste (`duplicateMessage_isIgnored`, `duplicatedReply_isProcessedOnlyOnce`).
* (−) Tabelul crește; în producție se adaugă o politică de retenție (ștergere după câteva zile, mai mare decât retenția topicurilor).
