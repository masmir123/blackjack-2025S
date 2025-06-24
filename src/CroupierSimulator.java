import org.json.JSONArray;
import org.json.JSONObject;

import java.io.IOException;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.Scanner;

/**
 * Eine Hilfsklasse, die einen Croupier simuliert, um den Spieler-Client zu testen.
 * Sie schickt eine vordefinierte Sequenz von Nachrichten, um alle Fälle
 * in der handleIncomingMessage-Methode des Spielers zu testen.
 */
public class CroupierSimulator {

    private final DatagramSocket socket;
    private InetAddress spielerAddress;
    private int spielerPort;

    public CroupierSimulator(int simulatorPort) throws SocketException {
        this.socket = new DatagramSocket(simulatorPort);
        System.out.println("[SIMULATOR] Croupier Simulator lauscht auf Port: " + simulatorPort);
    }

    /**
     * Wartet auf die erste Nachricht (sollte die Registrierung sein) vom Spieler,
     * um dessen Adresse und Port zu speichern.
     */
    public void warteAufSpielerRegistrierung() throws IOException {
        System.out.println("[SIMULATOR] Warte auf Registrierung vom Spieler-Client...");
        byte[] buffer = new byte[1024];
        DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
        socket.receive(packet); // Blockiert, bis eine Nachricht ankommt

        // Spieler-Adresse und Port aus dem empfangenen Paket extrahieren
        this.spielerAddress = packet.getAddress();
        this.spielerPort = packet.getPort();

        String messageStr = new String(packet.getData(), 0, packet.getLength(), StandardCharsets.UTF_8);
        JSONObject message = new JSONObject(messageStr);

        System.out.println("[SIMULATOR] Empfangen: " + message.toString());
        System.out.println("[SIMULATOR] Spieler '" + message.getString("name") + "' hat sich von " + spielerAddress + ":" + spielerPort + " registriert.");
    }

    /**
     * Startet die Testsequenz und sendet die Nachrichten an den Spieler.
     */
    public void starteTestSequenz() {
        if (spielerAddress == null) {
            System.err.println("[SIMULATOR] Spieleradresse ist unbekannt. Kann Test nicht starten.");
            return;
        }

        System.out.println("\n--- STARTE TESTSEQUENZ ---\n");

        // 1. Test "ACK"
        System.out.println("[SIMULATOR] Sende 'ACK' als Bestätigung der Registrierung.");
        sendeNachricht(erstelleJson("type", "ACK"));
        empfangeNachrichten(2000);

        // 2. Test "make_bet"
        System.out.println("[SIMULATOR] Sende 'make_bet' Anfrage. Bitte gib im Spieler-Fenster einen Einsatz ein.");
        sendeNachricht(erstelleJson("type", "make_bet"));
        empfangeNachrichten(10000);

        // 3. Test "receive_card" (2x senden)
        System.out.println("[SIMULATOR] Sende zwei 'receive_card' Nachrichten.");
        sendeNachricht(erstelleKartenNachricht("heart", "10"));
        empfangeNachrichten(5000);
        sendeNachricht(erstelleKartenNachricht("spade", "Q"));
        empfangeNachrichten(5000);

        // 4. Test "offer_surrender"
        System.out.println("[SIMULATOR] Sende 'offer_surrender' Anfrage. Bitte gib im Spieler-Fenster 'j' oder 'n' ein.");
        sendeNachricht(erstelleJson("type", "offer_surrender"));
        empfangeNachrichten(7000);

        // 5. Test "your_turn"
        System.out.println("[SIMULATOR] Sende 'your_turn' Anfrage. Bitte gib im Spieler-Fenster eine Aktion ein (hit, stand, etc.).");
        JSONObject yourTurnMsg = erstelleJson("type", "your_turn");
        JSONObject croupierCard = new JSONObject();
        croupierCard.put("suit", "spade");
        croupierCard.put("rank", "7");
        yourTurnMsg.put("croupier_card", croupierCard);
        yourTurnMsg.put("handIndex", 0);
        sendeNachricht(yourTurnMsg);
        empfangeNachrichten(7000);

        // 6. Test "result"
        System.out.println("[SIMULATOR] Sende 'result' Nachricht.");
        JSONObject resultMsg = erstelleJson("type", "result");
        resultMsg.put("earnings", -100);
        resultMsg.put("message", "Herzlichen Glückwunsch! Sie haben gewonnen!");
        sendeNachricht(resultMsg);
        empfangeNachrichten(2000);

        // 7. Test "error"
        System.out.println("[SIMULATOR] Sende 'error' Nachricht.");
        JSONObject errorMsg = erstelleJson("type", "error");
        errorMsg.put("message", "errorDetails");
        sendeNachricht(errorMsg);

        System.out.println("\n--- TESTSEQUENZ BEENDET ---");
    }

    /**
     * Lauscht für eine angegebene Dauer auf Nachrichten vom Spieler und gibt sie aus.
     * @param dauerMillis Die Zeit in Millisekunden, die auf Nachrichten gewartet werden soll.
     */
    private void empfangeNachrichten(int dauerMillis) {
        long endzeit = System.currentTimeMillis() + dauerMillis;
        try {
            socket.setSoTimeout(200); // Kurzer Timeout, um die Schleife nicht ewig zu blockieren
            while (System.currentTimeMillis() < endzeit) {
                try {
                    byte[] buffer = new byte[1024];
                    DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
                    socket.receive(packet);

                    if (packet.getAddress().equals(spielerAddress) && packet.getPort() == spielerPort) {
                        String messageStr = new String(packet.getData(), 0, packet.getLength(), StandardCharsets.UTF_8);
                        System.out.println("[SIMULATOR] Empfangen: " + messageStr);
                    }
                } catch (SocketTimeoutException e) {
                    // Erwarteter Timeout, wenn keine Nachricht kommt.
                }
            }
        } catch (IOException e) {
            System.err.println("[SIMULATOR] Fehler beim Warten auf Antwort: " + e.getMessage());
        } finally {
            try {
                socket.setSoTimeout(0); // Timeout zurücksetzen
            } catch (SocketException e) {
                // Socket könnte bereits geschlossen sein.
            }
        }
    }

    /**
     * Hilfsmethode, um eine einfache JSON-Nachricht mit einem Key-Value-Paar zu erstellen.
     */
    private JSONObject erstelleJson(String key, String value) {
        JSONObject json = new JSONObject();
        json.put(key, value);
        return json;
    }

    /**
     * Hilfsmethode, um eine "receive_card"-Nachricht zu erstellen.
     */
    private JSONObject erstelleKartenNachricht(String farbe, String rang) {
        JSONObject msg = erstelleJson("type", "receive_card");
        JSONObject card = new JSONObject();
        card.put("suit", farbe);
        card.put("rank", rang);
        msg.put("card", card);
        return msg;
    }

    /**
     * Sendet ein JSONObject an den gespeicherten Spieler.
     */
    private void sendeNachricht(JSONObject message) {
        try {
            byte[] buffer = message.toString().getBytes(StandardCharsets.UTF_8);
            DatagramPacket packet = new DatagramPacket(buffer, buffer.length, spielerAddress, spielerPort);
            socket.send(packet);
            System.out.println("[SIMULATOR] Gesendet: " + message.toString());
        } catch (IOException e) {
            System.err.println("[SIMULATOR] Fehler beim Senden der Nachricht: " + e.getMessage());
        }
    }



    public static void main(String[] args) {
        try {
            Scanner scanner = new Scanner(System.in);
            System.out.println("Geben Sie den Port ein, auf dem der Croupier-Simulator laufen soll (z.B. 5000):");
            int port = Integer.parseInt(scanner.nextLine().trim());

            CroupierSimulator simulator = new CroupierSimulator(port);
            simulator.warteAufSpielerRegistrierung();
            simulator.starteTestSequenz();

        } catch (SocketException e) {
            System.err.println("Fehler beim Erstellen des Sockets: " + e.getMessage());
        } catch (IOException e) {
            System.err.println("Netzwerkfehler beim Warten auf den Spieler: " + e.getMessage());
        } catch (NumberFormatException e) {
            System.err.println("Ungültige Portnummer.");
        }
    }
}