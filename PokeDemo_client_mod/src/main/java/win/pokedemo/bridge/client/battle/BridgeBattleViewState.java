package win.pokedemo.bridge.client.battle;

import net.minecraft.text.Text;
import win.pokedemo.bridge.common.PartySlotState;
import win.pokedemo.bridge.common.PokemonStatus;

import java.util.ArrayList;
import java.util.List;

public record BridgeBattleViewState(
        Battler self,
        Battler foe,
        List<Move> moves,
        List<PartySlotState> party,
        List<String> logLines,
        boolean canFight,
        boolean canSwitch,
        boolean canBag,
        boolean canRun,
        boolean canForfeit,
        boolean pvp,
        boolean awaitingForcedSwitch,
        boolean processingTurn,
        String statusLine,
        String requestType
) {
    public record Battler(String species, String name, int level, int hp, int maxHp, PokemonStatus status, String gender) {}
    public record Move(String name, String detail, String pp, String type, String category, int power, int accuracy, int priority, String description, boolean disabled, String disabledReason) {}

    public boolean isWaiting() { return processingTurn || "WAIT".equalsIgnoreCase(requestType); }
    public boolean isForcedSwitchRequest() { return awaitingForcedSwitch || "FORCED_SWITCH".equalsIgnoreCase(requestType); }

    public static BridgeBattleViewState demo(List<PartySlotState> partySlots, @org.jetbrains.annotations.Nullable String foeSpecies) {
        PartySlotState selfSlot = partySlots.stream().filter(PartySlotState::occupied).findFirst()
                .orElse(new PartySlotState(0, null, false, "pikachu", "皮卡丘", 25, 70, 70, PokemonStatus.NONE, true, "M", false, "", "poke_ball"));
        Battler self = new Battler(selfSlot.species(), selfSlot.displayName(), selfSlot.level(), selfSlot.hp(), selfSlot.maxHp(), selfSlot.status(), selfSlot.gender());
        String foeSp = foeSpecies == null || foeSpecies.isBlank() ? "eevee" : foeSpecies;
        Battler foe = new Battler(foeSp, titleCase(foeSp), 23, 51, 51, PokemonStatus.NONE, "N");
        List<Move> moves = List.of(
                new Move("电击", "电 / 特殊", "24/30", "electric", "special", 40, 100, 0, "对目标造成电属性特殊伤害。", false, ""),
                new Move("电光一闪", "一般 / 物理", "30/30", "normal", "physical", 40, 100, 1, "先制招式，优先度较高。", false, ""),
                new Move("摇尾巴", "一般 / 变化", "30/30", "normal", "status", 0, 100, 0, "降低目标的防御。", false, ""),
                new Move("十万伏特", "电 / 特殊", "15/15", "electric", "special", 90, 100, 0, "对目标造成电属性特殊伤害，并有几率令其麻痹。", false, "")
        );
        List<String> log = new ArrayList<>();
        log.add("去吧！" + self.name() + "！");
        log.add("现在为第1回合。");
        log.add(titleCase(foeSp) + "正在等待行动。");
        return new BridgeBattleViewState(self, foe, moves, partySlots, log, true, true, true, true, false, false, false, false, "请选择一个动作。", "ACTION");
    }

    private static String titleCase(String species) {
        if (species == null || species.isBlank()) return "未知宝可梦";
        return Text.literal(species.replace('_', ' ')).getString();
    }
}
