Abgabe von Maxim Smirnov

# Aufgabe 1: Blackjack

Ich habe den Wikipedia-Artikel zu Blackjack gelesen und einige Videos geschaut, um die Regeln zu verstehen.

# Aufgabe 2: UDP

Man muss sich auf ein Protokoll einigen, damit die Instanzen sich gegenseitig verstehen können. Zudem ist es sinnvoll, die Abfolge der gesendeten Nachrichten zu definieren -> Sequenzdiagramm
Man könnte auch einen Handshake implementieren, damit die Instanzen bestätigen können, dass sie die Nachrichten empfangen haben.
Darauf haben wir jedoch verzichtet, da es nicht unbedingt notwendig ist und wir sonst TCP verwendet hätten.

# Aufgabe 3: Implementierung

Ich habe im src-Ordner den Spieler implementiert, da mein Nachname mit „S“ anfängt. Außerdem habe ich einen CroupierSimulator implementiert, um grob ein Spiel zu simulieren.

# Aufgabe 4: Kommunikation

Robert, Christian und ich haben gemeinsam ein Protokoll entworfen, das die Kommunikation zwischen den Instanzen regelt.
Dieses ist im bereitgestellten Google Docs-Dokument zu finden:
https://docs.google.com/document/d/1niXuoXUmEFF9Z4lBfNpRsipfqm2mRbbPYNEaDwvVmd4/edit?tab=t.ytanwnl134oj

Da sich leider sonst niemand im Google Docs-Dokument oder im Discord gemeldet hat, haben wir das Protokoll alleine entworfen und ohne Kartenzähler.
Deshalb haben wir die Nachrichten vom Kartenzähler nur grob notiert, und nicht komplett implementiert.
Robert und Christian haben sich um die Implementierung des Croupiers gekümmert, ich habe mich um den Spieler gekümmert.
Beim Testen gab es einige Bugs, aber letztendlich haben wir es geschafft, ein Spiel lokal erfolgreich durchzuführen.

der Fork von Christian ist hier zu finden:
https://github.com/Chrizzly02/blackjack-2025S
der Fork von Robert ist hier zu finden:
https://github.com/tecnaps/blackjack-2025S
