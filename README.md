# MSS_Project

## Cose da implementare
- Per ora tutti vedono gli audio di tutti
- Dopo un giorno dall'input audio gli audio spariscono
- Potenzialmente cambiare rete neurale
  

## Schermata

-Le emozioni sono visibili solo dopo il tocco /la selezione di un luogo sulla mappa
  

## ToDo List
- [] Capire come salvare i dati --> Useare FireStore, perchè: permette la gestione dei TTL in modo facile (Cancellazione automatica degli elementi dopo un tot, quell oche vogliamo noi); permette ordinamento e filtro composti (RealTime database non permette query con ordinamento e filtro composti); in ottica di scalarizzazione "ospita i tuoi dati in più data center in regioni distinte, garantendo scalabilità globale e forte affidabilità";
Probabilmente conviene usare Google Cloud per salvare gli audio e mettere nella collezione il link per scaricare (2 chiamate HTTP); altrimenti mettere tutto su Firestore, riporto cosa suggerisce ChatGPT 
"
- Salvare direttamente l'audio in Firestore:
  - Vantaggi:
    Semplicità: Può semplificare il processo di gestione dei file, poiché l'audio è memorizzato direttamente nel database.
    Facilità di accesso: L'audio è immediatamente disponibile per l'applicazione mobile senza dover gestire ulteriori richieste HTTP o l'accesso a servizi di terze parti.
    Offline support: Se la tua app supporta il lavoro offline, avere l'audio direttamente nel database può semplificare la gestione dei dati offline.
  -  Svantaggi:
    Costo dello spazio: L'archiviazione diretta dei file audio in Firestore può diventare costosa se hai un gran numero di file o file di grandi dimensioni.
    Dimensioni limite dei documenti: Firestore ha un limite massimo di dimensioni per i documenti, quindi potresti dover affrontare problemi se l'audio è troppo grande.
    Possibili problemi di prestazioni: Caricare e scaricare grandi quantità di dati audio potrebbe influire sulle prestazioni dell'applicazione.
Salvare il link al file audio su Google Cloud Storage (GCS) in Firestore:
- Vantaggi:
    Risparmio di spazio: Conservando solo i link, puoi risparmiare spazio nel database Firestore.
    Elasticità delle dimensioni: Non sei limitato dalle dimensioni massime dei documenti di Firestore, poiché i file audio sono memorizzati esternamente su GCS.
    Costo: GCS potrebbe essere più economico rispetto all'archiviazione diretta in Firestore per grandi quantità di dati.
- Svantaggi:
    Complessità: Richiede l'implementazione di un sistema per gestire l'upload e il download dei file da GCS, oltre a dover gestire eventuali problemi di autorizzazione e sicurezza.
    Prestazioni: L'accesso ai file audio richiede un'ulteriore richiesta HTTP e potrebbe influire sulle prestazioni dell'applicazione, specialmente in caso di connessioni lente o instabili.
  "
- [] Una volta terminata l'app, controllare che nel ruotare lo schermo/ricevere una chiamata/chiudere riaprire l'app, questa non crashi
- [] Controllare che dark and white theme siano impostati bene

### Daniel e Lorenzo
- Implementare registrazione audio

### Marco e Davide
- Implementare ricerca dei luoghi
