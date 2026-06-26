package win.pokedemo;

public class MoveSlot {
    public String moveId;

    /** Current PP. */
    public int pp;

    /** Base PP from move definition (before PP Up/Max). */
    public int basePp;

    /** PP Up used times (0..3). */
    public int ppUpsUsed;

    /** Max PP after PP Up/Max. */
    public int maxPp;

    public void recalcMaxPp() {
        if (basePp <= 0) basePp = 1;
        if (ppUpsUsed < 0) ppUpsUsed = 0;
        if (ppUpsUsed > 3) ppUpsUsed = 3;
        // Gen1+: maxPP = floor(basePP * (1 + 0.2 * ppUpsUsed))
        this.maxPp = (int) Math.floor(basePp * (1.0 + 0.2 * ppUpsUsed));
        if (this.maxPp < 1) this.maxPp = 1;
        if (pp > this.maxPp) pp = this.maxPp;
    }

    public static MoveSlot of(Move m) {
        MoveSlot s = new MoveSlot();
        if (m == null) {
            s.moveId = "tackle";
            s.basePp = 35;
            s.ppUpsUsed = 0;
            s.recalcMaxPp();
            s.pp = s.maxPp;
            return s;
        }
        s.moveId = m.id();
        s.basePp = m.pp();
        s.ppUpsUsed = 0;
        s.recalcMaxPp();
        s.pp = s.maxPp;
        return s;
    }
}
