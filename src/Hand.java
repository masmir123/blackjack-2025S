import java.util.ArrayList;
import java.util.List;

public class Hand {
    private List<Card> karten = new ArrayList<>();
    private double einsatz;
    private boolean isStand = false; // Um zu wissen, ob für diese Hand keine Züge mehr gemacht werden

    public Hand(double einsatz) {
        this.einsatz = einsatz;
    }

    public void addKarte(Card karte) {
        this.karten.add(karte);
    }

    // Getter und Setter nach Bedarf...
    public List<Card> getKarten() {
        return karten;
    }

    public double getEinsatz() {
        return einsatz;
    }

    public void setStand(boolean stand) {
        isStand = stand;
    }

    @Override
    public String toString() {
        return "Hand{" +
                "karten=" + karten +
                ", einsatz=" + einsatz +
                ", isStand=" + isStand +
                '}';
    }

    public void setEinsatz(double v) {
        this.einsatz = v;
    }
}