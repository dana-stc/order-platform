# ADR-004: O bază de date per serviciu

**Status:** acceptat

## Context
Serviciile trebuie să evolueze și să fie scalate independent. O schemă partajată ar crea cuplaj ascuns (JOIN-uri, migrări coordonate) și ar anula motivul pentru care folosim microservicii.

## Decizie
Fiecare serviciu are propria bază (`orders`, `inventory`, `payments`, `notifications`), migrări Flyway proprii și nu are acces la tabelele altuia. Local și în Kubernetes rulează pe aceeași instanță PostgreSQL pentru simplitate; în producție pot fi instanțe separate fără modificări de cod (doar `DB_URL`). Comunicarea se face exclusiv prin mesaje.

Tabelele comune (`outbox`, `processed_message`) vin ca migrare `V1` din `platform-commons`; migrările serviciilor încep de la `V2`.

## Consecințe
* (+) Autonomie de deploy și de schemă, izolarea defectelor.
* (−) Fără interogări peste mai multe servicii (soluții: API composition sau read model alimentat din evenimente, de pildă CQRS).
* (−) Consistență eventuală între servicii, gestionată prin saga (ADR-001).
