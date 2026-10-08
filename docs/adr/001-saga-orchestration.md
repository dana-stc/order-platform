# ADR-001: Saga orchestrată (nu coregrafiată) pentru fluxul comenzii

**Status:** acceptat

## Context
Crearea unei comenzi atinge trei servicii cu baze de date separate (comenzi, stoc, plăți). O tranzacție distribuită (2PC/XA) nu e o opțiune: cuplează disponibilitatea serviciilor și Kafka nu o suportă. Avem nevoie de consistență eventuală cu compensări.

## Decizie
`order-service` este **orchestratorul**: ține starea sagăi (`OrderStatus`), trimite comenzi (`RESERVE_STOCK`, `PROCESS_PAYMENT`, `RELEASE_STOCK`) și reacționează la răspunsuri pe `saga.replies`. Participanții nu se cunosc între ei. Compensarea (eliberarea stocului) e un pas explicit al mașinii de stări.

## Alternative
* **Coregrafie** (fiecare serviciu reacționează la evenimentele celorlalte): fără punct central, dar fluxul e împrăștiat în 3 servicii, greu de urmărit, de depanat și de extins cu pași noi; riscul de cicluri crește.
* **Workflow engine** (Temporal, Camunda): durabilitate și vizibilitate excelente, dar o piesă de infrastructură în plus; rezonabil dacă numărul de sagi crește.

## Consecințe
* (+) Fluxul e într-un singur loc (`SagaOrchestrator`), ușor de testat unitar (vezi `SagaOrchestratorTest`) și de monitorizat (`orders_saga_duration_seconds`).
* (+) Starea explicită permite ignorarea în siguranță a mesajelor duplicate sau întârziate.
* (−) `order-service` devine un punct de coordonare; trebuie scalat și monitorizat ca atare. Replicile concurente sunt sigure datorită lock-ului pe rândul comenzii.
* (−) Participanții depind de contractul de mesaje al orchestratorului.
