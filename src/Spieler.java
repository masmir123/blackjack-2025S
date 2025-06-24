import org.json.JSONObject;
import java.io.IOException;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Scanner;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.SynchronousQueue;
import java.util.concurrent.atomic.AtomicBoolean;

public class Spieler {
    // Netzwerkkonfiguration
    private DatagramSocket socket;
    private InetAddress croupierAddress;
    private int croupierPort;
    private InetAddress kartenzaehlerAddress;
    private int kartenzaehlerPort;

    // Spieler-Zustand
    private final String name;
    private double guthaben;
    private List<Hand> haende = new ArrayList<>();

    private final AtomicBoolean isWaitingForSpecificInput = new AtomicBoolean(false);
    private final BlockingQueue<String> inputQueue = new SynchronousQueue<>();

    public Spieler(String name, double startkapital, String croupierHost, int croupierPort, String kartenzaehlerHost, int kartenzaehlerPort, int spielerPort) throws SocketException, UnknownHostException {
        this.name = name;
        this.guthaben = startkapital;
        this.croupierAddress = InetAddress.getByName(croupierHost);
        this.croupierPort = croupierPort;
        this.kartenzaehlerAddress = InetAddress.getByName(kartenzaehlerHost);
        this.kartenzaehlerPort = kartenzaehlerPort;

        // Socket auf dem angegebenen Port erstellen
        this.socket = new DatagramSocket(spielerPort);
        System.out.println("Spieler " + name + " lauscht auf Port: " + spielerPort);
    }

    Thread listenerThread;
    public void start() {
        // Startet einen Thread, der auf eingehende Nachrichten lauscht
        listenerThread = new Thread(this::listen);
        listenerThread.start();

        Thread consolelistenThread = new Thread(this::consolelisten);
        consolelistenThread.start();

        // Registriert den Spieler beim Croupier
        registerWithCroupier();
    }

    private void listen() {
        byte[] buffer = new byte[4096];
        while (true) {
            try {
                DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
                socket.receive(packet);
                String messageStr = new String(packet.getData(), 0, packet.getLength(), StandardCharsets.UTF_8);
                JSONObject message = new JSONObject(messageStr);

                System.out.println("[" + name + "] Nachricht empfangen: " + message);
                handleIncomingMessage(message);

            } catch (IOException e) {
                System.err.println("Fehler beim Empfangen der Nachricht: " + e.getMessage());
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
        }
    }


    private void handleIncomingMessage(JSONObject message) throws InterruptedException {
        String aktion = message.getString("type");

        switch (aktion) {
            case "ACK":
                System.out.println("Erfolgreich als Spieler registriert. Warte auf Spielstart.");
                break;
            case "make_bet":
                this.haende.clear(); // Hände aus der Vorrunde löschen
                System.out.println("Aufforderung zum Einsatz erhalten.\n Guthaben: " + this.guthaben + "\nBitte Einsatz eingeben: (oder Enter für Standard 100)");
                isWaitingForSpecificInput.set(true);
                String input = inputQueue.take(); // Warte hier auf die Eingabe vom consolelistenThread
                int bet_amount =input.isEmpty() ? 100 : Integer.parseInt(input);
                this.haende.get(0).setEinsatz(bet_amount); // Speichern des aktuellen Einsatzes
                PlaceBet(bet_amount);
                isWaitingForSpecificInput.set(false);
                break;

            case "receive_card":
                Hand ersteHand = this.haende.get(message.has("hand") ? message.getInt("hand") : 0); // Es gibt anfangs nur eine Hand, aber nach Split kann es mehrere geben

                JSONObject kartenObj = message.getJSONObject("card");
                Card card = new Card(kartenObj.getString("rank"), kartenObj.getString("suit"));
                ersteHand.addKarte(card);

                System.out.println("Karte erhalten: " + card.getRang() + " of " + card.getFarbe());
                break;

            case "your_turn":
                System.out.println("Ich bin am Zug.");
                // Die Nachricht sollte die Hand-ID enthalten, falls gesplittet wurde
                int handIndex = message.optInt("handIndex", 0);
                // Die Nachricht sollte auch die offenen Karten des Croupiers enthalten
                JSONObject croupierKarteJSON = message.getJSONObject("croupier_card");
                Card croupierKarte = new Card(croupierKarteJSON.getString("rank"), croupierKarteJSON.getString("suit"));

                makeplayerAction(handIndex, croupierKarte);

                break;

            case "offer_surrender":
                System.out.println("Angebot zum Aufgeben erhalten. Möchten Sie aufgeben? (j/n)\n aktuelles Guthaben: " +
                        this.guthaben+ ". Aktueller Einsatz "+ this.haende.get(0).getEinsatz() + "\n aktuelle Hand: " + this.haende);

                isWaitingForSpecificInput.set(true);
                
                String surrenderInput = inputQueue.take();
                if (surrenderInput.equalsIgnoreCase("j")) {
                    surrender(true);
                    System.out.println("Aufgegeben. Warte auf die nächste Runde.");
                } else {
                    surrender(false);
                    System.out.println("Aufgabe abgelehnt.");
                }
                
                isWaitingForSpecificInput.set(false); 
                
                break;

            case "result":
                // Hier können Sie die Ergebnisse der Runde verarbeiten
                System.out.println("Runde beendet. Ergebnisse: " + message.getJSONArray("message"));
                System.out.println("Warte auf die nächste Runde.");
                this.guthaben += message.getInt("earnings");
                break;

            case "error":
                // Hier können Sie die Ergebnisse der Runde verarbeiten
                System.out.println("Error: " + message.getJSONArray("message"));
                break;


        }
    }

    public void consolelisten() {
        Scanner scanner = new Scanner(System.in);
        while (true) {
            String input = scanner.nextLine().trim();

            // Prüfen, ob ein anderer Thread auf diese Eingabe wartet
            if (isWaitingForSpecificInput.get()) {
                try {
                    inputQueue.put(input);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            } else {
                if (input.equalsIgnoreCase("exit")) {
                    System.out.println("Spieler " + name + " beendet das Spiel.");
                    listenerThread.interrupt(); // Stoppe den Listener-Thread
                    socket.close();
                    System.exit(0);
                    break;
                } else if (input.equalsIgnoreCase("status")) {
                    System.out.println("Aktueller Status: Guthaben: " + this.guthaben + ", Hände: " + this.haende);
                } else {
                    System.out.println("Unbekannter Befehl: '" + input + "'. Warte auf eine Aktion vom Croupier oder gib 'status' oder 'exit' ein.");
                }
            }
        }
    }

    // --- AKTIONEN DES SPIELERS ---

    private void registerWithCroupier() {
        JSONObject registrationMessage = new JSONObject();
        registrationMessage.put("type", "register");
        registrationMessage.put("role", "Spieler");
        registrationMessage.put("name", this.name);
        registrationMessage.put("credit", this.guthaben);
        sendMessage(croupierAddress, croupierPort, registrationMessage);
    }

    private void surrender(boolean answer) {
        JSONObject surrenderMessage = new JSONObject();
        surrenderMessage.put("type", "surrender");
        surrenderMessage.put("answer", answer ? "yes" : "no");
        sendMessage(croupierAddress, croupierPort, surrenderMessage);
        System.out.println("Aufgabe akzeptiert. Halber Einsatz wird zurückerstattet.");
        this.guthaben += this.haende.get(0).getEinsatz() / 2; // Halber Einsatz zurück
    }

    private void PlaceBet(int einsatz) {

        // Schritt 3: Einsatz platzieren
        this.haende.add(new Hand(einsatz));
        JSONObject betMessage = new JSONObject();
        betMessage.put("type", "bet");
        betMessage.put("amount", einsatz);
        sendMessage(croupierAddress, croupierPort, betMessage);
        System.out.println("Einsatz von " + einsatz + " gemacht.");
    }

    private void makeplayerAction(int handIndex, Card croupierKarte) throws InterruptedException {
        System.out.println("Aktuelle Hand: " + this.haende.get(handIndex));
        System.out.println("Offene Karte des Croupiers: " + croupierKarte.getRang() + " of " + croupierKarte.getFarbe());
        System.out.println("Mögliche Aktionen: Hit, Stand, Double Down, Split");
        isWaitingForSpecificInput.set(true);
        String actionInput = inputQueue.take().trim().toLowerCase();
        isWaitingForSpecificInput.set(false);
        JSONObject actionMessage = new JSONObject();
        actionMessage.put("type", "action");
        actionMessage.put("action", actionInput);
        actionMessage.put("handIndex", handIndex);

        switch (actionInput.toLowerCase()) {
            case "hit":
                sendMessage(croupierAddress, croupierPort, actionMessage);
                System.out.println("Hit ausgeführt.");
                break;
            case "stand":
                this.haende.get(handIndex).setStand(true);
                sendMessage(croupierAddress, croupierPort, actionMessage);
                System.out.println("Stand ausgeführt.");
                break;
            case "double down":
                if (this.guthaben >= this.haende.get(handIndex).getEinsatz()) {
                    this.guthaben -= this.haende.get(handIndex).getEinsatz(); // Verdopplung des Einsatzes
                    this.haende.get(handIndex).setEinsatz(this.haende.get(handIndex).getEinsatz() * 2);
                    sendMessage(croupierAddress, croupierPort, actionMessage);
                    System.out.println("Double Down ausgeführt.");
                } else {
                    System.out.println("Nicht genug Guthaben für Double Down.");
                    makeplayerAction(handIndex, croupierKarte); // Wiederholen der Aktion
                }
                break;
            case "split":
                if (this.haende.get(handIndex).getKarten().size() == 2 && this.haende.get(handIndex).getKarten().get(0).getRang().equals(this.haende.get(handIndex).getKarten().get(1).getRang())) {
                    // Split erlaubt, neue Hand erstellen
                    Hand neueHand = new Hand(this.haende.get(handIndex).getEinsatz());
                    neueHand.addKarte(this.haende.get(handIndex).getKarten().remove(1)); // Zweite Karte in die neue Hand verschieben
                    this.haende.add(neueHand);
                    sendMessage(croupierAddress, croupierPort, actionMessage);
                    System.out.println("Split ausgeführt.");
                } else {
                    System.out.println("Split nicht möglich.");
                    makeplayerAction(handIndex, croupierKarte); // Wiederholen der Aktion
                }
                break;
        }
    }
    // --- NETZWERK-HILFSMETHODE ---

    private void sendMessage(InetAddress address, int port, JSONObject message) {
        try {
            byte[] buffer = message.toString().getBytes(StandardCharsets.UTF_8);
            DatagramPacket packet = new DatagramPacket(buffer, buffer.length, address, port);
            socket.send(packet);
        } catch (IOException e) {
            System.err.println("Fehler beim Senden der Nachricht: " + e.getMessage());
        }
    }

    // --- MAIN METHODE ---

    public static void main(String[] args) {
        Scanner scanner = new Scanner(System.in);

        try {
            System.out.println("Geben Sie die IP-Adresse des Croupiers ein (Standard: localhost (enter drücken)):");
            String croupierHost = scanner.nextLine().trim();
            if (croupierHost.isEmpty()) {
                croupierHost = "127.0.0.1";
            }

            System.out.println("Geben Sie den Port des Croupiers ein (Standard: 5000 (enter drücken)):");
            String croupierPortInput = scanner.nextLine().trim();
            int croupierPort = croupierPortInput.isEmpty() ? 5000 : Integer.parseInt(croupierPortInput);

            System.out.println("Geben Sie die IP-Adresse des Kartenzählers ein (Standard: localhost (enter drücken)):");
            String kartenzaehlerHost = scanner.nextLine().trim();
            if (kartenzaehlerHost.isEmpty()) {
                kartenzaehlerHost = "127.0.0.1";
            }

            System.out.println("Geben Sie den Port des Kartenzählers ein (Standard: 5001 (enter drücken)):");
            String kartenzaehlerPortInput = scanner.nextLine().trim();
            int kartenzaehlerPort = kartenzaehlerPortInput.isEmpty() ? 5001 : Integer.parseInt(kartenzaehlerPortInput);

            System.out.println("Geben Sie den Port ein, auf dem der Spieler lauschen soll (Standard: 5002 (enter drücken):");
            String spielerPortInput = scanner.nextLine().trim();
            int spielerPort = spielerPortInput.isEmpty() ? 5002 : Integer.parseInt(spielerPortInput);

            System.out.println("Geben Sie das Startkapital an (Standard: 1000 (enter drücken):");
            String StartkapitalInput = scanner.nextLine().trim();
            int Startkapital = StartkapitalInput.isEmpty() ? 1000 : Integer.parseInt(StartkapitalInput);

            System.out.println("Wie soll der Spieler heißen? (Standard: Bob (enter drücken)):");
            String Spielername = scanner.nextLine().trim();
            if (Spielername.isEmpty()) {
                Spielername = "Bob";
            }

            Spieler spieler1 = new Spieler(Spielername, Startkapital, croupierHost, croupierPort, kartenzaehlerHost, kartenzaehlerPort, spielerPort);
            spieler1.start();
        } catch (SocketException | UnknownHostException e) {
            e.printStackTrace();
        }
    }
}