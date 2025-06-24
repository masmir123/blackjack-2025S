public class Card {
    private final String rang;
    private final String farbe;

    public Card(String rang, String farbe) {
        this.rang = rang;
        this.farbe = farbe;
    }

    @Override
    public String toString() {
        return farbe + " " + rang;
    }

    // Getter...
    public String getRang() { return rang; }
    public String getFarbe() { return farbe; }
}