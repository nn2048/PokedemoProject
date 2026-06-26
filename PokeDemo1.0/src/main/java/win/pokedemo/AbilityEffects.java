package win.pokedemo;

import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Ability effects (特性) - lightweight implementation focused on 1v1 wild battles.
 *
 * <p>Design goals:
 * <ul>
 *   <li>Never crash battle logic if an ability is unknown.</li>
 *   <li>Prefer small, readable rules over full-gen edge cases.</li>
 *   <li>Keep all state on PokemonInstance (transient) where needed.</li>
 * </ul>
 */
public final class AbilityEffects {
    private AbilityEffects() {}

    /**
     * Battle context (thread local).
     *
     * <p>Some abilities (e.g. Neutralizing Gas) affect the whole battle and should be respected
     * by checks across multiple subsystems (damage, weather, end-of-turn residuals, etc.) without
     * plumbing the BattleSession everywhere.
     */
    private static final ThreadLocal<BattleSession> BATTLE_CTX = new ThreadLocal<>();

    public static void withContext(BattleSession s, Runnable r) {
        BattleSession prev = BATTLE_CTX.get();
        BATTLE_CTX.set(s);
        try { r.run(); }
        finally {
            if (prev == null) BATTLE_CTX.remove();
            else BATTLE_CTX.set(prev);
        }
    }

    public static <T> T withContext(BattleSession s, java.util.function.Supplier<T> sup) {
        BattleSession prev = BATTLE_CTX.get();
        BATTLE_CTX.set(s);
        try { return sup.get(); }
        finally {
            if (prev == null) BATTLE_CTX.remove();
            else BATTLE_CTX.set(prev);
        }
    }

    public static BattleSession contextSession() {
        return BATTLE_CTX.get();
    }

    /**
     * User-facing ability name.
     * <p>
     * Keep CN as fallback to preserve the original Chinese UX, but allow LangManager
     * (lang yml / Cobblemon json) to override for en/ja/ko.
     */
    public static String displayName(String abilityId) {
        if (abilityId == null) return "无";
        String id = norm(abilityId);
        String zh = zhAbilityName(id);
        LangManager l = lang();
        if (l != null) return l.abilityName(id, zh);
        return zh;
    }

    /** Chinese fallback mapping for abilities. */
    private static String zhAbilityName(String id) {
        if (id == null || id.isBlank()) return "无";
        return switch (id) {
            case "pressure" -> "压迫感";
            case "intimidate" -> "威吓";
            case "download" -> "下载";
            case "frisk" -> "察觉";
            case "levitate" -> "飘浮";
            case "flashfire" -> "引火";
            case "waterabsorb" -> "储水";
            case "voltabsorb" -> "蓄电";
            case "lightningrod" -> "避雷针";
            case "motordrive" -> "电气引擎";
            case "sapsipper" -> "食草";
            case "stormdrain" -> "引水";
            case "overgrow" -> "茂盛";
            case "blaze" -> "猛火";
            case "torrent" -> "激流";
            case "swarm" -> "虫之预感";
            case "hugepower", "purepower" -> "大力士";
            case "technician" -> "技术高手";
            case "thickfat" -> "厚脂肪";
            case "filter", "solidrock" -> "过滤";
            case "heatproof" -> "耐热";
            case "compoundeyes" -> "复眼";
            case "hustle" -> "活力";
            case "quickfeet" -> "飞毛腿";
            case "static" -> "静电";
            case "flamebody" -> "火焰之躯";
            case "poisonpoint" -> "毒刺";
            case "roughskin" -> "粗糙皮肤";
            case "ironbarbs" -> "铁刺";
            case "aftermath" -> "引爆";
            case "sturdy" -> "结实";
            case "rockhead" -> "坚硬脑袋";
            case "shellarmor", "battlearmor" -> "硬壳盔甲";
            case "innerfocus" -> "精神力";
            case "clearbody" -> "恒净之躯";
            case "whitesmoke" -> "白色烟雾";
            case "hypercutter" -> "怪力钳";
            case "keeneye" -> "锐利目光";
            case "bigpecks" -> "健壮胸肌";
            case "earlybird" -> "早起";
            case "shedskin" -> "蜕皮";
            case "damp" -> "湿气";
            case "soundproof" -> "隔音";
            case "owntempo" -> "我行我素";
            case "shadowtag" -> "踩影";
            case "arenatrap" -> "沙穴";
            case "magnetpull" -> "磁力";
            case "shielddust" -> "鳞粉";
            case "serenegrace" -> "天恩";
            case "sheerforce" -> "强行";
            case "liquidooze" -> "污泥浆";
            case "magicguard" -> "魔法防守";
            case "poisonheal" -> "毒疗";
            case "immunity" -> "免疫";
            case "insomnia" -> "不眠";
            case "vitalspirit" -> "干劲";
            case "limber" -> "柔软";
            case "waterveil" -> "水幕";
            case "chlorophyll" -> "叶绿素";
            case "effectspore" -> "孢子";
            case "trace" -> "复制";
            case "analytic" -> "分析";
            case "unnerve" -> "紧张感";
            case "noguard" -> "无防守";
            case "moldbreaker" -> "破格";
            case "skilllink" -> "连续攻击";
            case "unaware" -> "纯朴";
            case "multiscale" -> "多重鳞片";
            case "poisontouch" -> "毒手";
            case "cursedbody" -> "诅咒之躯";
            case "moxie" -> "自信过度";
            case "defiant" -> "不服输";
            case "competitive" -> "好胜";
            case "reckless" -> "舍身";
            case "overcoat" -> "防尘";
            case "oblivious" -> "迟钝";
            case "pickup" -> "捡拾";
            case "runaway" -> "逃跑";
            case "regenerator" -> "再生力";
            case "angerpoint" -> "愤怒穴位";
            case "steadfast" -> "不挠之心";
            case "rattled" -> "胆怯";
            case "stench" -> "恶臭";
            case "naturalcure" -> "自然回复";
            case "synchronize" -> "同步";
            case "cutecharm" -> "迷人之躯";
            case "weakarmor" -> "碎甲";
            case "scrappy" -> "胆量";
            case "infiltrator" -> "穿透";
            case "ironfist" -> "铁拳";
            case "justified" -> "正义之心";
            case "tangledfeet" -> "蹒跚";
            case "wonderskin" -> "奇迹皮肤";
            case "gluttony" -> "贪吃鬼";
            case "stickyhold" -> "黏着";
            case "rivalry" -> "斗争心";
            case "illuminate" -> "发光";
            case "cloudnine" -> "无关天气";
            case "airlock" -> "气闸";
            case "dryskin" -> "干燥皮肤";
            case "hydration" -> "湿润之躯";
            case "raindish" -> "雨盘";
            case "icebody" -> "冰冻之躯";
            case "leafguard" -> "叶子防守";
            case "sandveil" -> "沙隐";
            case "sandforce" -> "沙之力";
            case "sandrush" -> "拨沙";
            case "snowcloak" -> "雪隐";
            case "solarpower" -> "太阳之力";
            case "swiftswim" -> "悠游自如";
            case "harvest" -> "收获";
            case "healer" -> "回合末有概率治愈自身异常（本服规则）";
            case "friendguard" -> "友情防守";
            case "imposter" -> "冒牌货";
            case "neutralizinggas" -> "化学变化气体";
            case "armortail" -> "尾甲";
            case "dazzling" -> "鲜艳之躯";
            case "queenlymajesty" -> "女王的威严";
            case "goodasgold" -> "黄金之躯";
            case "purifyingsalt" -> "洁净之盐";
            case "eartheater" -> "食土";
            case "thermalexchange" -> "热交换";
            case "electromorphosis" -> "电力转换";
            case "toxicdebris" -> "毒满地";
            case "sharpness" -> "锋锐";
            case "rockypayload" -> "岩石载荷";
            case "electricsurge" -> "电气制造者";
            case "grassysurge" -> "青草制造者";
            case "mistysurge" -> "薄雾制造者";
            case "psychicsurge" -> "精神制造者";
            case "hadronengine" -> "强子引擎";
            case "orichalcumpulse" -> "绯红脉动";
            case "protean" -> "变幻自如";
            case "libero" -> "自由者";
            case "quarkdrive" -> "夸克充能";
            case "protosynthesis" -> "古代活性";
            case "supremeoverlord" -> "大将";
            case "magicbounce" -> "魔法镜";
            case "strongjaw" -> "强壮之颚";
            case "punkrock" -> "庞克摇滚";
            case "speedboost" -> "加速";
            case "stamina" -> "持久力";
            case "gooey" -> "黏滑";
            case "tanglinghair" -> "卷发";
            case "cottondown" -> "棉絮";
            case "wellbakedbody" -> "焦香之躯";
            case "surgesurfer" -> "冲浪之尾";
            case "beastboost" -> "异兽提升";
            case "grimneigh" -> "黑色嘶鸣";
            case "chillingneigh" -> "苍白嘶鸣";
            case "dragonsmaw" -> "龙颚";
            case "transistor" -> "电晶体";
            case "aerilate" -> "飞行皮肤";
            case "pixilate" -> "妖精皮肤";
            case "refrigerate" -> "冰冻皮肤";
            case "galvanize" -> "电气皮肤";
            case "bulletproof" -> "防弹";
            case "toughclaws" -> "硬爪";
            case "triage" -> "先行治疗";
            case "stakeout" -> "蹲守";
            case "steelyspirit" -> "钢之意志";
            case "steelworker" -> "钢能力者";
            case "megalauncher" -> "超级发射器";
            case "victorystar" -> "胜利之星";
            case "defeatist" -> "软弱";
            case "shadowshield" -> "幻影防守";
            case "watercompaction" -> "遇水凝固";
            case "steamengine" -> "蒸汽机";
            case "sandspit" -> "吐沙";
            case "seedsower" -> "掉出种子";
            case "berserk" -> "怒火冲天";
            case "perishbody" -> "灭亡之躯";
            case "poisonpuppeteer" -> "毒傀儡";
            case "toxicchain" -> "毒锁链";
            case "windpower" -> "风力发电";
            case "waterbubble" -> "水泡";
            case "fullmetalbody" -> "金属防护";
            case "sweetveil" -> "甜幕";
            case "pastelveil" -> "粉彩护幕";
            case "fluffy" -> "毛茸茸";
            case "icescales" -> "冰鳞粉";
            case "merciless" -> "不仁不义";
            case "corrosion" -> "腐蚀";
            case "mirrorarmor" -> "镜甲";
            case "ripen" -> "熟成";
            case "baddreams" -> "梦魇";
            case "moody" -> "心情不定";
            case "soulheart" -> "魂心";
            case "wonderguard" -> "神奇守护";
            case "unseenfist" -> "无形拳";
            case "aromaveil" -> "芳香幕";
            case "colorchange" -> "变色";
            case "comatose" -> "绝对睡眠";
            case "contrary" -> "唱反调";
            case "darkaura" -> "暗黑气场";
            case "dauntlessshield" -> "不屈之盾";
            case "fairyaura" -> "妖精气场";
            case "flowergift" -> "花之礼";
            case "flowerveil" -> "花幕";
            case "galewings" -> "疾风之翼";
            case "guarddog" -> "看门犬";
            case "intrepidsword" -> "不挠之剑";
            case "lingeringaroma" -> "甩不掉的气味";
            case "longreach" -> "远隔";
            case "magmaarmor" -> "岩浆铠甲";
            case "minus" -> "负电";
            case "mummy" -> "木乃伊";
            case "neuroforce" -> "脑核之力";
            case "normalize" -> "一般皮肤";
            case "opportunist" -> "跟风";
            case "plus" -> "正电";
            case "rebound" -> "魔法反射";
            case "slowstart" -> "慢启动";
            case "suctioncups" -> "吸盘";
            case "superluck" -> "超幸运";
            case "truant" -> "懒惰";
            case "beadsofruin" -> "灾祸之玉";
            case "swordofruin" -> "灾祸之剑";
            case "tabletsofruin" -> "灾祸之简";
            case "vesselofruin" -> "灾祸之鼎";
            case "aurabreak" -> "气场破坏";
            case "ballfetch" -> "捡球";
            case "battery" -> "蓄电池";
            case "cheekpouch" -> "颊囊";
            case "curiousmedicine" -> "怪药";
            case "emergencyexit" -> "危险回避";
            case "gorillatactics" -> "一猩一意";
            case "grasspelt" -> "草之毛皮";
            case "heavymetal" -> "重金属";
            case "lightmetal" -> "轻金属";
            case "honeygather" -> "采蜜";
            case "hospitality" -> "款待";
            case "klutz" -> "笨拙";
            case "liquidvoice" -> "湿润之声";
            case "mimicry" -> "拟态";
            case "mountaineer" -> "登山者";
            case "myceliummight" -> "菌丝之力";
            case "persistent" -> "持久性";
            case "pickpocket" -> "扒手";
            case "powerspot" -> "能量点";
            case "powerofalchemy" -> "化学之力";
            case "propellertail" -> "螺旋尾鳍";
            case "quickdraw" -> "速击";
            case "receiver" -> "接球手";
            case "screencleaner" -> "除障";
            case "stall" -> "后出";
            case "stalwart" -> "坚毅";
            case "supersweetsyrup" -> "甘甜蜜浆";
            case "symbiosis" -> "共生";
            case "telepathy" -> "心灵感应";
            case "wimpout" -> "跃跃欲逃";
            default -> prettifyAbilityId(id);
        };
    }

    /** Short one-line description used in GUI. */
    public static String shortDescription(String abilityId) {
        if (abilityId == null) return "";
        String id = norm(abilityId);
        return switch (id) {
            case "pressure" -> "对手使用招式时 PP 额外消耗 1";
            case "intimidate" -> "出场时降低对手攻击";
            case "levitate" -> "免疫地面属性招式";
            case "flashfire" -> "免疫火并强化火系招式";
            case "waterabsorb" -> "免疫水并回复HP";
            case "voltabsorb" -> "免疫电并回复HP";
            case "lightningrod" -> "免疫电并回复HP（简化）";
            case "sturdy" -> "满HP时不会被一击打倒";
            case "rockhead" -> "不受反作用力伤害";
            case "shielddust" -> "不会受到招式追加效果影响";
            case "serenegrace" -> "追加效果的概率翻倍";
            case "sheerforce" -> "移除追加效果并提高伤害";
            case "magicguard" -> "不受间接伤害";
            case "poisonheal" -> "中毒时改为回血";
            case "shadowtag" -> "对手无法换下场（幽灵除外）";
            case "arenatrap" -> "对手无法换下场（飞行/飘浮除外）";
            case "magnetpull" -> "钢系无法换下场";
            case "trace" -> "出场时复制对手特性";
            case "noguard" -> "招式不会miss（双方）";
            case "moldbreaker" -> "无视对手部分特性";
            case "skilllink" -> "连续攻击类招式必定5次";
            case "unaware" -> "无视对手能力变化";
            case "multiscale" -> "满HP时减半所受伤害";
            case "poisontouch" -> "接触招式可能使对手中毒";
            case "cursedbody" -> "受击后可能封印对手招式";
            case "moxie" -> "击倒对手后攻击提升";
            case "defiant" -> "能力被降低时攻击大幅提升";
            case "competitive" -> "能力被降低时特攻大幅提升";
            case "reckless" -> "反作用力招式威力提高";
            case "naturalcure" -> "换下场时治愈异常状态";
            case "regenerator" -> "换下场时回复 1/3 最大HP";
            case "overcoat" -> "免疫粉末类招式";
            case "pickup" -> "战斗后可能捡到道具";
            case "angerpoint" -> "被击中要害时攻击最大化";
            case "steadfast" -> "畏缩时速度提升";
            case "rattled" -> "被虫/恶/幽灵命中或被威吓时速度提升";
            // overcoat already covered above; keep only one label to avoid duplicate case.
            case "oblivious" -> "不会被迷人/着迷；可无视爱河";
            case "chlorophyll" -> "晴天下速度提高。";
            case "effectspore" -> "接触时可能让对手异常";
            case "weakarmor" -> "受物理攻击时防御下降速度大幅提升";
            case "scrappy" -> "一般/格斗招式可打幽灵";
            case "infiltrator" -> "无视对手替身（部分）";
            case "ironfist" -> "拳类招式威力提升";
            case "justified" -> "被恶系命中时攻击提升";
            case "tangledfeet" -> "混乱时更难被命中";
            case "wonderskin" -> "对手变化招式命中降至50%";
            case "dryskin" -> "免疫水并回复HP，火伤更疼（简化）";
            case "gluttony" -> "更早吃树果（待补更多树果规则）";
            case "harvest" -> "回合末可能重新获得已吃掉的树果";
            case "imposter" -> "出场时立刻变身";
            case "stickyhold" -> "道具不易被夺走（待补相关招式）";
            case "illuminate" -> "发光：小幅提升命中率（本服规则）";
            case "armortail" -> "阻止对手的先制招式";
            case "dazzling" -> "阻止对手的先制招式";
            case "queenlymajesty" -> "阻止对手的先制招式";
            case "goodasgold" -> "免疫对手的变化招式";
            case "purifyingsalt" -> "不会陷入异常，且受幽灵招式伤害减半";
            case "eartheater" -> "免疫地面并回复HP";
            case "thermalexchange" -> "受火系招式命中时攻击提升，不会灼伤";
            case "electromorphosis" -> "受击后进入蓄电状态";
            case "toxicdebris" -> "受到物理攻击时在对手场地撒下毒菱";
            case "sharpness" -> "斩击类招式威力提高";
            case "rockypayload" -> "岩石属性招式威力提高";
            case "electricsurge" -> "出场时展开电气场地";
            case "grassysurge" -> "出场时展开青草场地";
            case "mistysurge" -> "出场时展开薄雾场地";
            case "psychicsurge" -> "出场时展开精神场地";
            case "hadronengine" -> "出场时展开电气场地（其余强化待补）";
            case "orichalcumpulse" -> "出场时唤起大晴天，并在晴天强化攻击";
            case "protean" -> "出招前变为该招式属性（每次上场限一次）";
            case "libero" -> "出招前变为该招式属性（每次上场限一次）";
            case "quarkdrive" -> "电气场地激活时强化最高能力（先实现攻/特攻/速度）";
            case "protosynthesis" -> "大晴天激活时强化最高能力（先实现攻/特攻/速度）";
            case "supremeoverlord" -> "根据已倒下的队友数量强化招式威力";
            case "magicbounce" -> "将可反弹的变化招式反弹回去";
            case "strongjaw" -> "啃咬类招式威力提升";
            case "punkrock" -> "声音招式威力提升";
            case "speedboost" -> "每回合结束时速度提升";
            case "stamina" -> "受到攻击时防御提升";
            case "gooey" -> "接触到自己时使对手速度下降";
            case "tanglinghair" -> "接触到自己时使对手速度下降";
            case "cottondown" -> "受到攻击时使对手速度下降";
            case "wellbakedbody" -> "免疫火属性招式并大幅提升防御";
            case "surgesurfer" -> "电气场地下速度翻倍";
            case "beastboost" -> "击倒对手后最高能力提升";
            case "grimneigh" -> "击倒对手后特攻提升";
            case "chillingneigh" -> "击倒对手后攻击提升";
            case "dragonsmaw" -> "龙属性招式威力提升";
            case "transistor" -> "电属性招式威力提升";
            case "aerilate" -> "一般属性招式变为飞行属性并增强";
            case "pixilate" -> "一般属性招式变为妖精属性并增强";
            case "refrigerate" -> "一般属性招式变为冰属性并增强";
            case "galvanize" -> "一般属性招式变为电属性并增强";
            case "bulletproof" -> "免疫部分球弹/爆弹类招式";
            case "toughclaws" -> "接触类招式伤害提高";
            case "triage" -> "回复类招式优先度提高";
            case "stakeout" -> "攻击刚换上场的对手时伤害提高";
            case "steelyspirit", "steelworker" -> "钢属性招式伤害提高";
            case "megalauncher" -> "波动类招式伤害提高";
            case "victorystar" -> "自身招式命中率提高";
            case "defeatist" -> "HP低于一半时攻击和特攻减半";
            case "shadowshield" -> "满HP时减半所受伤害";
            case "watercompaction" -> "受到水属性招式攻击时防御大幅提升";
            case "steamengine" -> "受到火或水属性招式攻击时速度巨幅提升";
            case "sandspit" -> "受到攻击后扬起沙暴";
            case "seedsower" -> "受到攻击后展开青草场地";
            case "berserk" -> "HP被打到一半以下时特攻提升";
            case "perishbody" -> "接触时双方陷入灭亡之歌状态";
            case "poisonpuppeteer" -> "让中毒的对手陷入混乱";
            case "toxicchain" -> "攻击时可能让对手陷入剧毒";
            case "windpower" -> "受到风招式攻击时开始蓄电";
            case "waterbubble" -> "水属性招式伤害提高且不会灼伤";
            case "fullmetalbody" -> "能力不会被对手降低";
            case "sweetveil" -> "不会陷入睡眠";
            case "pastelveil" -> "不会陷入中毒";
            case "aromaveil" -> "不会受到挑衅/再来一次/回复封锁等精神类招式影响";
            case "colorchange" -> "受到攻击后变为该招式属性";
            case "comatose" -> "视为睡眠但不会真的睡着；不会陷入其他异常";
            case "contrary" -> "能力变化方向反转";
            case "darkaura" -> "全场恶属性招式威力提高";
            case "dauntlessshield" -> "出场时防御提升";
            case "fairyaura" -> "全场妖精属性招式威力提高";
            case "flowergift" -> "晴天时攻击和特防提高";
            case "flowerveil" -> "草属性不会陷入异常且能力不会被对手降低";
            case "galewings" -> "满HP时飞行招式优先度提高";
            case "guarddog" -> "免疫威吓，受威吓时攻击提升";
            case "intrepidsword" -> "出场时攻击提升";
            case "lingeringaroma" -> "接触到自己时对手的特性会变为甩不掉的气味";
            case "longreach" -> "使用接触招式时不会发生接触";
            case "magmaarmor" -> "不会陷入冰冻";
            case "minus", "plus" -> "单打中视为特攻提升";
            case "mummy" -> "接触到自己时对手的特性会变为木乃伊";
            case "neuroforce" -> "克制对手时伤害提高";
            case "normalize" -> "自己的招式变为一般属性并略微增强";
            case "opportunist" -> "对手能力提升时自己也会跟着提升";
            case "rebound" -> "将可反弹的变化招式反弹回去";
            case "slowstart" -> "出场后的5回合内攻击和速度减半";
            case "suctioncups" -> "不会被吹飞/吼叫之类招式强制换下";
            case "superluck" -> "更容易击中要害";
            case "truant" -> "每隔一回合会偷懒无法行动";
            case "beadsofruin" -> "场上其他宝可梦的特防降低";
            case "swordofruin" -> "场上其他宝可梦的防御降低";
            case "tabletsofruin" -> "场上其他宝可梦的攻击降低";
            case "vesselofruin" -> "场上其他宝可梦的特攻降低";
            default -> "";
        };
    }

        private static LangManager lang() {
        try {
            return PokeDemoPlugin.INSTANCE == null ? null : PokeDemoPlugin.INSTANCE.getLang();
        } catch (Exception e) {
            return null;
        }
    }

    private static String logAbility(String suffix, String fallbackText) {
        LangManager l = lang();
        return l == null ? fallbackText : l.ui("battle.log.ability." + suffix, fallbackText);
    }

    private static String logAbilityFmt(String suffix, String fallbackText, java.util.Map<String, String> vars) {
        LangManager l = lang();
        return l == null ? fallbackText : l.uiFmt("battle.log.ability." + suffix, fallbackText, vars);
    }
    private static String displayTypeName(String typeId) {
        LangManager l = lang();
        return l == null ? typeId : l.typeName(typeId);
    }

    private static String statDisplayName(String statId) {
        LangManager l = lang();
        return l == null ? statId : l.statName(statId);
    }


private static boolean isSlicingMove(String moveId) {
        if (moveId == null) return false;
        String id = moveId.toLowerCase(java.util.Locale.ROOT).replace("-", "").replace("_", "").replace(" ", "");
        return java.util.Set.of(
                "aircutter","airslash","behemothblade","bitterblade","ceaselessedge","crosspoison",
                "cut","furycutter","kowtowcleave","leafblade","nightslash","populationbomb",
                "psyblade","razorleaf","razorshell","sacredsword",
                "slash","solarblade","stoneaxe","xscissor"
        ).contains(id);
    }

private static boolean isPunchMove(String moveId) {
        if (moveId == null) return false;
        String id = moveId.toLowerCase(java.util.Locale.ROOT);
        // Common punch moves (covers Gen1 + a few later additions for compatibility)
        return id.equals("firepunch") || id.equals("icepunch") || id.equals("thunderpunch")
                || id.equals("megapunch") || id.equals("cometpunch") || id.equals("dizzypunch")
                || id.equals("dynamicpunch") || id.equals("machpunch") || id.equals("bulletpunch")
                || id.equals("drainpunch") || id.equals("focuspunch");
    }

private static boolean isBitingMove(String moveId) {
        if (moveId == null) return false;
        String id = moveId.toLowerCase(java.util.Locale.ROOT).replace("_", "").replace("-", "");
        return java.util.Set.of("bite","crunch","firefang","icefang","thunderfang","hyperfang","psychicfangs","poisonfang","jawlock","fishiousrend").contains(id);
    }

private static boolean isBulletMove(String moveId) {
        if (moveId == null) return false;
        String id = moveId.toLowerCase(java.util.Locale.ROOT).replace("_", "").replace("-", "");
        return java.util.Set.of("acidspray","aurasphere","beakblast","bulletseed","egg bomb","eggbomb","electroball","energyball","focusblast","gyroball","iceball","magnetbomb","mistball","mudbomb","octazooka","pollenpuff","pyroball","rockwrecker","seedbomb","shadowball","sludgebomb","weatherball","zapcannon").contains(id);
    }

private static boolean isSoundMove(String moveId) {
        if (moveId == null) return false;
        String id = moveId.toLowerCase(java.util.Locale.ROOT).replace("_", "").replace("-", "").replace(" ", "");
        return java.util.Set.of("boomburst","bugbuzz","chatter","clangingscales","clanging soul","clangoroussoul","confide","disarmingvoice","echoedvoice","eerieimpulse","growl","healbell","howl","hypervoice","metal sound","metalsound","nobleroar","overdrive","partingshot","perishsong","relicsong","roar","round","screech","shadowpanic","sing","snarl","snore","sparklingaria","supersonic","torchsong","uproar").contains(id);
    }

private static boolean isWindMove(String moveId) {
        if (moveId == null) return false;
        String id = moveId.toLowerCase(java.util.Locale.ROOT).replace("_", "").replace("-", "");
        return java.util.Set.of("gust","whirlwind","twister","razorwind","airslash","hurricane","heatwave","icywind","bleakwindstorm","wildboltstorm","sandsearstorm","springtidestorm","tailwind").contains(id);
    }

private static boolean isHealingMove(String moveId) {
        if (moveId == null) return false;
        String id = moveId.toLowerCase(java.util.Locale.ROOT).replace("_", "").replace("-", "").replace(" ", "");
        return java.util.Set.of("recover","roost","softboiled","milkdrink","healorder","shoreup","slackoff","synthesis","moonlight","morningsun","rest","drainingkiss","drainpunch","gigadrain","hornleech","oblivionwing","paraboliccharge","junglehealing","lunarblessing","floralhealing","pollenpuff","lifedew").contains(id);
    }

private static boolean isPulseMove(String moveId) {
        if (moveId == null) return false;
        String id = moveId.toLowerCase(java.util.Locale.ROOT).replace("_", "").replace("-", "").replace(" ", "");
        return java.util.Set.of("aurasphere","darkpulse","dragonpulse","healpulse","originpulse","terrainpulse","waterpulse").contains(id);
    }

private static boolean isParadoxAbilityActive(PokemonInstance p, String ability) {
        if (p == null) return false;
        BattleSession sctx = contextSession();
        if (sctx == null) return p.paradoxBoostedByItem;
        if ("quarkdrive".equals(ability)) return (sctx.terrainTurns > 0 && "electric".equalsIgnoreCase(sctx.terrain)) || p.paradoxBoostedByItem;
        if ("protosynthesis".equals(ability)) return WeatherSystem.effectiveWeather(sctx) == WeatherType.SUN || p.paradoxBoostedByItem;
        return false;
    }

private static String highestParadoxStat(PokemonInstance p) {
        if (p == null || PokeDemoPlugin.INSTANCE == null || PokeDemoPlugin.INSTANCE.getDex() == null) return null;
        Species sp = PokeDemoPlugin.INSTANCE.getDex().getSpeciesFlexible(p.effectiveSpeciesId());
        if (sp == null) return null;
        int atk = p.calcStat(sp, "atk", p.ivAtk, p.evAtk, false);
        int def = p.calcStat(sp, "def", p.ivDef, p.evDef, false);
        int spa = p.calcStat(sp, "spa", p.ivSpa, p.evSpa, false);
        int spd = p.calcStat(sp, "spd", p.ivSpd, p.evSpd, false);
        int spe = p.calcStat(sp, "spe", p.ivSpe, p.evSpe, false);
        int best = Math.max(atk, Math.max(def, Math.max(spa, Math.max(spd, spe))));
        if (spe == best) return "spe";
        if (atk == best) return "atk";
        if (def == best) return "def";
        if (spa == best) return "spa";
        return "spd";
    }

private static boolean hasAnyActiveAbility(BattleSession s, String abilityNorm) {
        if (s == null || abilityNorm == null) return false;
        return has(s.playerMon, abilityNorm) || has(s.wildMon, abilityNorm);
    }

    public static boolean hasUnnerveLike(PokemonInstance p) {
        if (p == null) return false;
        return has(p, "unnerve") || has(p, "asoneglastrier") || has(p, "asonespectrier");
    }

    private static boolean activeDesolateLand() {
        return hasAnyActiveAbility(contextSession(), "desolateland");
    }

    private static boolean activePrimordialSea() {
        return hasAnyActiveAbility(contextSession(), "primordialsea");
    }

    private static boolean activeDeltaStream() {
        return hasAnyActiveAbility(contextSession(), "deltastream");
    }

    private static String plateType(String itemId) {
        if (itemId == null) return null;
        return switch (itemId.toLowerCase(Locale.ROOT)) {
            case "draco_plate" -> "dragon";
            case "dread_plate" -> "dark";
            case "earth_plate" -> "ground";
            case "fist_plate" -> "fighting";
            case "flame_plate" -> "fire";
            case "icicle_plate" -> "ice";
            case "insect_plate" -> "bug";
            case "iron_plate" -> "steel";
            case "meadow_plate" -> "grass";
            case "mind_plate" -> "psychic";
            case "pixie_plate" -> "fairy";
            case "sky_plate" -> "flying";
            case "splash_plate" -> "water";
            case "spooky_plate" -> "ghost";
            case "stone_plate" -> "rock";
            case "toxic_plate" -> "poison";
            case "zap_plate" -> "electric";
            default -> null;
        };
    }

    private static String memoryType(String itemId) {
        if (itemId == null) return null;
        return switch (itemId.toLowerCase(Locale.ROOT)) {
            case "bug_memory" -> "bug";
            case "dark_memory" -> "dark";
            case "dragon_memory" -> "dragon";
            case "electric_memory" -> "electric";
            case "fairy_memory" -> "fairy";
            case "fighting_memory" -> "fighting";
            case "fire_memory" -> "fire";
            case "flying_memory" -> "flying";
            case "ghost_memory" -> "ghost";
            case "grass_memory" -> "grass";
            case "ground_memory" -> "ground";
            case "ice_memory" -> "ice";
            case "poison_memory" -> "poison";
            case "psychic_memory" -> "psychic";
            case "rock_memory" -> "rock";
            case "steel_memory" -> "steel";
            case "water_memory" -> "water";
            default -> null;
        };
    }

    public static void refreshBattleForm(PokemonInstance mon, Species sp, String monName, java.util.List<String> out) {
        if (mon == null || sp == null) return;
        String sid = mon.speciesId == null ? "" : mon.speciesId.toLowerCase(Locale.ROOT);
        String a = norm(mon.abilityId);
        BattleSession s = contextSession();
        if ("forecast".equals(a) && sid.startsWith("castform")) {
            WeatherType w = s == null ? WeatherType.NONE : WeatherSystem.effectiveWeather(s);
            String form = switch (w) {
                case SUN -> "castformsunny";
                case RAIN -> "castformrainy";
                case HAIL -> "castformsnowy";
                default -> sid;
            };
            mon.overrideSpeciesId = form.equals(sid) ? null : form;
        }
        if ("schooling".equals(a) && sid.equals("wishiwashi") && mon.level >= 20) {
            boolean school = mon.currentHp > Math.max(1, mon.maxHp(sp) / 4);
            mon.overrideSpeciesId = school ? "wishiwashischool" : null;
        }
        if ("zenmode".equals(a) && sid.startsWith("darmanitan")) {
            boolean zen = mon.currentHp > 0 && mon.currentHp <= Math.max(1, mon.maxHp(sp) / 2);
            mon.overrideSpeciesId = sid.equals("darmanitangalar") ? (zen ? "darmanitangalarzen" : null) : (zen ? "darmanitanzen" : null);
        }
        if ("powerconstruct".equals(a) && sid.equals("zygarde") && !mon.powerConstructUsed && mon.currentHp > 0 && mon.currentHp <= Math.max(1, mon.maxHp(sp) / 2)) {
            mon.overrideSpeciesId = "zygardecomplete";
            mon.powerConstructUsed = true;
            if (out != null) out.add("§6【群聚变形】§e" + monName + " 变成了完全体形态！");
        }
        if ("shieldsdown".equals(a) && sid.startsWith("minior")) {
            boolean core = mon.currentHp > 0 && mon.currentHp <= Math.max(1, mon.maxHp(sp) / 2);
            mon.overrideSpeciesId = core ? "minior" : "miniormeteor";
        }
        if ("iceface".equals(a) && sid.equals("eiscue")) mon.overrideSpeciesId = mon.iceFaceBroken ? "eiscuenoice" : null;
        if ("disguise".equals(a) && sid.equals("mimikyu")) mon.overrideSpeciesId = mon.disguiseBroken ? "mimikyubusted" : null;
        if (("multitype".equals(a) || "rkssystem".equals(a)) && mon.heldItemId != null) {
            String t = "multitype".equals(a) ? plateType(mon.heldItemId) : memoryType(mon.heldItemId);
            mon.overrideType1 = t;
            mon.overrideType2 = null;
        }
    }

    private static String prettifyAbilityId(String abilityId) {
        if (abilityId == null) return "";
        String s = abilityId.trim();
        if (s.isEmpty()) return s;
        return Character.toUpperCase(s.charAt(0)) + s.substring(1);
    }

    public static String norm(String abilityId) {
        if (abilityId == null) return "";
        return abilityId.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]", "");
    }

    public static boolean has(PokemonInstance p, String abilityNorm) {
        if (p == null) return false;
        String mine = norm(p.abilityId);
        if (!mine.equals(abilityNorm)) return false;
        if (p.abilitySuppressed) return false;
        // Neutralizing Gas suppresses other abilities while any active Pokémon has it.
        if ("neutralizinggas".equals(mine)) return true;
        if (isAbilitySuppressedByNeutralizingGas(p)) return false;
        return true;
    }

    /** Whether this Pokémon's ability is currently suppressed by Neutralizing Gas. */
    public static boolean isAbilitySuppressedByNeutralizingGas(PokemonInstance p) {
        if (p == null) return false;
        String mine = norm(p.abilityId);
        if (mine.isEmpty() || "neutralizinggas".equals(mine)) return false;

        BattleSession s = contextSession();
        if (s == null) return false;
        PokemonInstance a = s.playerMon;
        PokemonInstance b = s.wildMon;
        boolean ng = (a != null && "neutralizinggas".equals(norm(a.abilityId)))
                || (b != null && "neutralizinggas".equals(norm(b.abilityId)));
        return ng;
    }

    /** Abilities that ignore the target's ability for most defensive checks (Mold Breaker family). */
    public static boolean ignoresDefenderAbility(PokemonInstance attacker) {
        if (attacker == null) return false;
        String a = norm(attacker.abilityId);
        return "moldbreaker".equals(a) || "teravolt".equals(a) || "turboblaze".equals(a);
    }

    /** No Guard: moves used by or against the user never miss (accuracy/evasion ignored). */
    public static boolean noGuardActive(PokemonInstance atk, PokemonInstance def) {
        return has(atk, "noguard") || has(def, "noguard");
    }

    /** Called when a Pokémon switches in (including at battle start). */
    public static List<String> onSwitchIn(PokeDemoPlugin plugin,
                                         Player viewer,
                                         BattleSession s,
                                         PokemonInstance self, Species selfS,
                                         PokemonInstance foe,  Species foeS,
                                         String selfName, String foeName,
                                         boolean selfIsPlayer) {
        List<String> out = new ArrayList<>();
        if (self == null || selfS == null) return out;
        String a = norm(self.abilityId);
        if (a.isEmpty()) return out;

        switch (a) {
            case "intimidate" -> {
                if (foe != null && foe.currentHp > 0) {
                    int before = foe.stageAtk;
                    if (has(foe, "guarddog")) {
                        int atkBefore = foe.stageAtk;
                        foe.applyStage("atk", 1);
                        if (foe.stageAtk != atkBefore) out.add("§6【看门犬】§e" + foeName + " 的攻击提升了！");
                    } else {
                        foe.applyStage("atk", -1);
                    }
                    if (!has(foe, "guarddog") && foe.stageAtk != before) {
                        LangManager lang = lang();
        out.add((lang==null?("§6【威吓】§e"+selfName+" 让 "+foeName+" 的攻击下降了！"):lang.uiFmt("battle.log.ability.intimidate", "§6【威吓】§e{self} 让 {foe} 的攻击下降了！", java.util.Map.of("self", selfName, "foe", foeName))));
                    } else if (!has(foe, "guarddog")) {
                        LangManager lang2 = lang();
        out.add(lang2==null?"§6【威吓】§7但是没有效果！":lang2.ui("battle.log.ability.intimidate_no_effect","§6【威吓】§7但是没有效果！"));
                    }
                    // Rattled: triggered by Intimidate.
                    if (has(foe, "rattled")) {
                        int b = foe.stageSpe;
                        foe.applyStage("spe", 1);
                        if (foe.stageSpe != b) {
            LangManager lang3 = lang();
            out.add(lang3==null?("§6【胆怯】§e"+foeName+" 的速度提升了！"):lang3.uiFmt("battle.log.ability.rattled","§6【胆怯】§e{mon} 的速度提升了！", java.util.Map.of("mon", foeName)));
        }
                    }
                }
            }
            case "download" -> {
                if (foe != null && foeS != null && foe.currentHp > 0) {
                    int def = foe.calcStat(foeS, "def", foe.ivDef, foe.evDef, false);
                    int spd = foe.calcStat(foeS, "spd", foe.ivSpd, foe.evSpd, false);
                    if (def <= spd) {
                        self.applyStage("atk", 1);
                        LangManager lang4 = lang();
        out.add(lang4==null?("§6【下载】§e"+selfName+" 的攻击提升了！"):lang4.uiFmt("battle.log.ability.download_atk","§6【下载】§e{mon} 的攻击提升了！", java.util.Map.of("mon", selfName)));
                    } else {
                        self.applyStage("spa", 1);
                        LangManager lang5 = lang();
        out.add(lang5==null?("§6【下载】§e"+selfName+" 的特攻提升了！"):lang5.uiFmt("battle.log.ability.download_spa","§6【下载】§e{mon} 的特攻提升了！", java.util.Map.of("mon", selfName)));
                    }
                }
            }
            case "frisk" -> {
                if (foe != null && foe.heldItemId != null && !foe.heldItemId.isBlank()) {
                    LangManager lang6 = lang();
        String itemId = foe.heldItemId;
        String itemName = (lang6==null? itemId : lang6.item(itemId, itemId));
        out.add(lang6==null?("§6【察觉】§e"+selfName+" 发现对手携带了 §f"+itemId+"§e！"):lang6.uiFmt("battle.log.ability.frisk","§6【察觉】§e{self} 发现对手携带了 §f{item}§e！", java.util.Map.of("self", selfName, "item", itemName)));
                }
            }
            case "drizzle", "drought", "sandstream", "snowwarning" -> {
                WeatherType type = switch (a) {
                    case "drizzle" -> WeatherType.RAIN;
                    case "drought" -> WeatherType.SUN;
                    case "sandstream" -> WeatherType.SAND;
                    case "snowwarning" -> WeatherType.HAIL;
                    default -> WeatherType.NONE;
                };
                int turns = WeatherSystem.durationForSource(type, self);
                WeatherSystem.setWeather(s, type, turns);
                String w = switch (type) {
                    case RAIN -> "开始下雨了！";
                    case SUN -> "阳光变得强烈了！";
                    case SAND -> "沙暴刮起来了！";
                    case HAIL -> "开始下冰雹了！";
                    default -> "";
                };
                if (!w.isBlank()) out.add(logAbilityFmt("weather_start", "§6【{ab}】§e{msg} §7({turns} turns)", java.util.Map.of("ab", abilityDisplay(a), "msg", w, "turns", String.valueOf(turns))));
            }
            case "electricsurge", "grassysurge", "mistysurge", "psychicsurge" -> {
                String terr = switch (a) {
                    case "electricsurge" -> "electric";
                    case "grassysurge" -> "grassy";
                    case "mistysurge" -> "misty";
                    case "psychicsurge" -> "psychic";
                    default -> null;
                };
                if (terr != null) {
                    s.terrain = terr;
                    s.terrainTurns = 5;
                    String msg = switch (terr) {
                        case "electric" -> "电气场地展开了！";
                        case "grassy" -> "青草场地展开了！";
                        case "misty" -> "薄雾场地展开了！";
                        case "psychic" -> "精神场地展开了！";
                        default -> "场地发生了变化！";
                    };
                    out.add(logAbilityFmt("terrain_start", "§6【{ab}】§e{msg}", java.util.Map.of("ab", abilityDisplay(a), "msg", msg)));
                }
            }
            case "orichalcumpulse" -> {
                int turns = WeatherSystem.durationForSource(WeatherType.SUN, self);
                WeatherSystem.setWeather(s, WeatherType.SUN, turns);
                out.add(logAbilityFmt("orichalcum_pulse", "§6【{ab}】§eSunlight turned harsh! §7({turns} turns)", java.util.Map.of("ab", abilityDisplay(a), "turns", String.valueOf(turns))));
            }
            case "hadronengine" -> {
                s.terrain = "electric";
                s.terrainTurns = 5;
                out.add(logAbilityFmt("hadron_engine", "§6【{ab}】§eElectric Terrain spread across the field!", java.util.Map.of("ab", abilityDisplay(a))));
            }
            case "intrepidsword" -> {
                int before = self.stageAtk;
                self.applyStage("atk", 1);
                if (self.stageAtk != before) out.add(logAbilityFmt("intrepid_sword", "§6【{ab}】§e{mon}'s Attack rose!", java.util.Map.of("ab", abilityDisplay(a), "mon", selfName)));
            }
            case "dauntlessshield" -> {
                int before = self.stageDef;
                self.applyStage("def", 1);
                if (self.stageDef != before) out.add(logAbilityFmt("dauntless_shield", "§6【{ab}】§e{mon}'s Defense rose!", java.util.Map.of("ab", abilityDisplay(a), "mon", selfName)));
            }
            case "slowstart" -> {
                self.slowStartTurns = 5;
                out.add(logAbilityFmt("slow_start", "§6【{ab}】§e{mon} can't get it going!", java.util.Map.of("ab", abilityDisplay(a), "mon", selfName)));
            }
            case "screencleaner" -> {
                s.playerReflectTurns = s.playerLightScreenTurns = s.playerAuroraVeilTurns = 0;
                s.wildReflectTurns = s.wildLightScreenTurns = s.wildAuroraVeilTurns = 0;
                out.add(logAbility("screen_cleaner", "§6【Screen Cleaner】§eAll barriers were cleared away!"));
            }
            case "supersweetsyrup" -> {
                if (foe != null && foe.currentHp > 0) {
                    int before = foe.stageEvasion;
                    foe.applyStage("evasion", -1);
                    if (foe.stageEvasion != before) out.add(logAbilityFmt("supersweet_syrup", "§6【Supersweet Syrup】§e{mon}'s evasiveness fell!", java.util.Map.of("mon", foeName)));
                }
            }
            case "mimicry" -> {
                if (s != null && s.terrainTurns > 0 && s.terrain != null) {
                    String t = switch (s.terrain.toLowerCase(java.util.Locale.ROOT)) {
                        case "electric" -> "electric";
                        case "grassy" -> "grass";
                        case "misty" -> "fairy";
                        case "psychic" -> "psychic";
                        default -> null;
                    };
                    if (t != null) {
                        self.overrideType1 = t;
                        self.overrideType2 = null;
                        out.add(logAbilityFmt("mimicry", "§6【Mimicry】§e{mon} transformed into the {type} type!", java.util.Map.of("mon", selfName, "type", displayTypeName(t))));
                    }
                }
            }
            case "curiousmedicine", "receiver", "powerofalchemy", "hospitality", "ballfetch", "honeygather", "symbiosis", "telepathy", "propellertail", "stalwart" -> {
                // Singles-compatible no-op / passive abilities.
            }
            case "anticipation" -> {
                LangManager lang7 = lang();
        out.add(lang7==null?("§6【预知危险】§e"+selfName+" 预感到了危险！"):lang7.uiFmt("battle.log.ability.anticipation","§6【预知危险】§e{mon} 预感到了危险！", java.util.Map.of("mon", selfName)));
            }
            case "forewarn" -> {
                LangManager lang8 = lang();
        out.add(lang8==null?("§6【预知梦】§e"+selfName+" 变得警觉起来！"):lang8.uiFmt("battle.log.ability.forewarn","§6【预知梦】§e{mon} 变得警觉起来！", java.util.Map.of("mon", selfName)));
            }
            case "pressure" -> {
                LangManager lang9 = lang();
        out.add(lang9==null?("§6【压迫感】§e"+selfName+" 施加了压力！"):lang9.uiFmt("battle.log.ability.pressure","§6【压迫感】§e{mon} 施加了压力！", java.util.Map.of("mon", selfName)));
            }
            case "trace" -> {
                if (foe != null && foeS != null && foe.currentHp > 0) {
                    String copied = norm(foe.abilityId);
                    if (!copied.isEmpty() && !"trace".equals(copied)) {
                        // Battle-only override: remember original and restore on battle end / switch out.
                        if (self.traceOriginalAbilityId == null) {
                            self.traceOriginalAbilityId = self.abilityId;
                        }
                        self.tracedAbilityId = copied;
                        self.abilityId = copied;
                        LangManager lang10 = lang();
        String abName = displayName(copied);
        out.add(lang10==null?("§6【复制】§e"+selfName+" 复制了 "+foeName+" 的特性：§f"+abName+"§e！"):lang10.uiFmt("battle.log.ability.trace","§6【复制】§e{self} 复制了 {foe} 的特性：§f{ab}§e！", java.util.Map.of("self", selfName, "foe", foeName, "ab", abName)));
                    }
                }
            }
            case "imposter" -> {
                if (foe != null && foeS != null && foe.currentHp > 0) {
                    // Imposter: automatically Transform on switch-in.
                    // Behaves like Gen1 Transform for our battle engine: HP does not change.
                    if (self.overrideSpeciesId == null) {
                        try {
                            Species baseS = plugin.getDex().getSpecies(self.speciesId);
                            if (baseS != null) {
                                self.lockedMaxHp = self.maxHp(baseS);
                                self.currentHp = Math.min(self.currentHp, self.lockedMaxHp);
                            }
                        } catch (Exception ignore) {}

                        self.overrideSpeciesId = foe.effectiveSpeciesId();

                        try {
                            java.util.List<String> tps = foeS.types();
                            String t1 = (foe.overrideType1 != null) ? foe.overrideType1 : (tps.size() >= 1 ? tps.get(0) : null);
                            String t2 = (foe.overrideType2 != null) ? foe.overrideType2 : (tps.size() >= 2 ? tps.get(1) : null);
                            self.overrideType1 = t1;
                            self.overrideType2 = t2;
                        } catch (Exception ignore) {
                            self.overrideType1 = foe.overrideType1;
                            self.overrideType2 = foe.overrideType2;
                        }

                        self.stageAtk = foe.stageAtk;
                        self.stageDef = foe.stageDef;
                        self.stageSpa = foe.stageSpa;
                        self.stageSpd = foe.stageSpd;
                        self.stageSpe = foe.stageSpe;
                        self.stageAccuracy = foe.stageAccuracy;
                        self.stageEvasion = foe.stageEvasion;

                        self.overrideMoves = PokemonInstance.deepCopyMoveSlots(foe.effectiveMoves());
                        while (self.overrideMoves.size() < 4) self.overrideMoves.add(null);
                        for (int i = 0; i < self.overrideMoves.size(); i++) {
                            MoveSlot ms = self.overrideMoves.get(i);
                            if (ms == null) continue;
                            ms.basePp = 5;
                            ms.ppUpsUsed = 0;
                            ms.recalcMaxPp();
                            ms.pp = 5;
                        }

                        LangManager lang11 = lang();
        out.add(lang11==null?("§6【冒牌货】§b"+selfName+" 变身成了 "+foeName+"！"):lang11.uiFmt("battle.log.ability.imposter","§6【冒牌货】§b{self} 变身成了 {foe}！", java.util.Map.of("self", selfName, "foe", foeName)));
                    }
                }
            }
            case "neutralizinggas" -> {
                LangManager lang12 = lang();
        out.add(lang12==null?"§6【化学变化气体】§e场上的其他宝可梦特性被抑制了！":lang12.ui("battle.log.ability.neutralizing_gas","§6【化学变化气体】§e场上的其他宝可梦特性被抑制了！"));
            }
            case "deltastream" -> {
                WeatherSystem.setWeather(s, WeatherType.SAND, 5);
                out.add(logAbility("delta_stream", "§6【Delta Stream】§eMysterious strong winds are protecting Flying-type Pokémon!"));
            }
            case "desolateland" -> {
                WeatherSystem.setWeather(s, WeatherType.SUN, 5);
                out.add(logAbility("desolate_land", "§6【Desolate Land】§eThe sunlight turned extremely harsh!"));
            }
            case "primordialsea" -> {
                WeatherSystem.setWeather(s, WeatherType.RAIN, 5);
                out.add(logAbility("primordial_sea", "§6【Primordial Sea】§eA heavy rain began to fall!"));
            }
            case "costar" -> {
                if (foe != null) {
                    self.stageAtk = foe.stageAtk; self.stageDef = foe.stageDef; self.stageSpa = foe.stageSpa;
                    self.stageSpd = foe.stageSpd; self.stageSpe = foe.stageSpe; self.stageAccuracy = foe.stageAccuracy; self.stageEvasion = foe.stageEvasion;
                    out.add(logAbilityFmt("costar", "§6【Costar】§e{mon} copied the foe's stat changes!", java.util.Map.of("mon", selfName)));
                }
            }
            case "commander", "dancer" -> {
                // Singles-compatible simplified no-op.
            }
            case "illusion" -> {
                if (viewer != null && plugin != null && plugin.getStorage() != null) {
                    try {
                        PlayerProfile prof = plugin.getStorage().getProfile(viewer.getUniqueId());
                        if (prof != null && prof.party != null) {
                            PokemonInstance last = null;
                            for (PokemonInstance p2 : prof.party) {
                                if (p2 == null || p2.currentHp <= 0 || p2.isEgg) continue;
                                if (self.uuid != null && self.uuid.equals(p2.uuid)) continue;
                                last = p2;
                            }
                            if (last != null) {
                                self.overrideSpeciesId = last.speciesId;
                                self.illusionActive = true;
                            }
                        }
                    } catch (Throwable ignored) {}
                }
            }
            case "airlock", "cloudnine" -> {
                out.add(logAbilityFmt("airlock", "§6【{ab}】§e{mon} makes the effects of weather disappear!", java.util.Map.of("ab", abilityDisplay(a), "mon", selfName)));
            }
            case "forecast", "schooling", "zenmode", "multitype", "rkssystem", "terashift" -> {
                if ("terashift".equals(a) && (self.speciesId != null && self.speciesId.equalsIgnoreCase("terapagos"))) {
                    self.overrideSpeciesId = "terapagosterastal";
                    out.add(logAbilityFmt("tera_shift", "§6【Tera Shift】§e{mon} transformed!", java.util.Map.of("mon", selfName)));
                }
                refreshBattleForm(self, selfS, selfName, out);
            }
            case "embodyaspectteal" -> {
                int b = self.stageSpe; self.applyStage("spe", 1); if (self.stageSpe != b) out.add(logAbilityFmt("embody_aspect_speed", "§6【Embody Aspect】§e{mon}'s Speed rose!", java.util.Map.of("mon", selfName)));
            }
            case "embodyaspecthearthflame" -> {
                int b = self.stageAtk; self.applyStage("atk", 1); if (self.stageAtk != b) out.add(logAbilityFmt("embody_aspect_atk", "§6【Embody Aspect】§e{mon}'s Attack rose!", java.util.Map.of("mon", selfName)));
            }
            case "embodyaspectwellspring" -> {
                int b = self.stageSpd; self.applyStage("spd", 1); if (self.stageSpd != b) out.add(logAbilityFmt("embody_aspect_spd", "§6【Embody Aspect】§e{mon}'s Sp. Def rose!", java.util.Map.of("mon", selfName)));
            }
            case "embodyaspectcornerstone" -> {
                int b = self.stageDef; self.applyStage("def", 1); if (self.stageDef != b) out.add(logAbilityFmt("embody_aspect_def", "§6【Embody Aspect】§e{mon}'s Defense rose!", java.util.Map.of("mon", selfName)));
            }
            case "teraformzero" -> {
                if (s != null) { s.weather = WeatherType.NONE; s.weatherTurns = 0; s.terrain = null; s.terrainTurns = 0; }
                out.add(logAbility("teraform_zero", "§6【Teraform Zero】§eThe effects of weather and terrain disappeared!"));
            }
            case "quarkdrive", "protosynthesis" -> {
                if (self.heldItemId != null && self.heldItemId.equalsIgnoreCase("booster_energy") && !self.paradoxBoostedByItem) {
                    self.paradoxBoostedByItem = true;
                    self.heldItemId = null;
                    out.add(logAbilityFmt("paradox_boost", "§6【{ab}】§e{mon}'s highest stat was heightened!", java.util.Map.of("ab", abilityDisplay(a), "mon", selfName)));
                }
            }
            case "asoneglastrier", "asonespectrier" -> out.add(logAbilityFmt("as_one", "§6【{ab}】§e{mon} radiates an overwhelming presence!", java.util.Map.of("ab", abilityDisplay(a), "mon", selfName)));
            case "zerotohero" -> {
                if (self.zeroToHeroPrimed && self.speciesId != null && self.speciesId.equalsIgnoreCase("palafin")) {
                    self.overrideSpeciesId = "palafinhero";
                    out.add(logAbilityFmt("zero_to_hero", "§6【Zero to Hero】§e{mon} transformed into its Hero Form!", java.util.Map.of("mon", selfName)));
                }
            }
            case "terashell" -> refreshBattleForm(self, selfS, selfName, out);
            default -> { /* no switch-in effect */ }
        }

        return out;
    }

    /** Called when a Pokémon switches out (player switching / forced switch / escape). */
    public static List<String> onSwitchOut(PokeDemoPlugin plugin,
                                          PokemonInstance self, Species selfS,
                                          String selfName) {
        List<String> out = new ArrayList<>();
        if (self == null || selfS == null) return out;
        String a = norm(self.abilityId);
        if (a.isEmpty()) return out;

        switch (a) {
            case "naturalcure" -> {
                if (self.status != null && !"none".equalsIgnoreCase(self.status)) {
                    self.status = "none";
                    self.sleepTurns = 0;
                    self.toxicCounter = 0;
                    LangManager lang13 = lang();
        out.add(lang13==null?("§6【自然回复】§e"+selfName+" 的异常状态被治愈了！"):lang13.uiFmt("battle.log.ability.natural_cure","§6【自然回复】§e{mon} 的异常状态被治愈了！", java.util.Map.of("mon", selfName)));
                }
            }
            case "regenerator" -> {
                int max = Math.max(1, self.maxHp(selfS));
                int heal = Math.max(1, max / 3);
                int before = self.currentHp;
                self.currentHp = Math.min(max, self.currentHp + heal);
                if (self.currentHp > before) {
            LangManager lang14 = lang();
            String n = String.valueOf(self.currentHp - before);
            out.add(lang14==null?("§6【再生力】§a"+selfName+" 回复了 §c"+n+"§a 点体力！"):lang14.uiFmt("battle.log.ability.regenerator","§6【再生力】§a{mon} 回复了 §c{n}§a 点体力！", java.util.Map.of("mon", selfName, "n", n)));
        }
            }
            case "zerotohero" -> self.zeroToHeroPrimed = true;
            default -> {}
        }
        return out;
    }

    /** Powder moves list (shared behavior with Safety Goggles). */
    private static final java.util.Set<String> POWDER_MOVES = java.util.Set.of(
            "sleep_powder","stun_spore","poison_powder","spore","rage_powder","cotton_spore"
    );

    /** Overcoat: blocks powder moves. */
    public static boolean blocksPowderMoves(PokemonInstance defender, Move move) {
        if (defender == null || move == null) return false;
        if (!has(defender, "overcoat")) return false;
        return POWDER_MOVES.contains(norm(move.id()));
    }

    /**
     * Sticky Hold: prevents the holder's item from being removed/knocked off/stolen by opponent effects.
     *
     * The current move pool/ruleset does not yet implement Knock Off / Thief / Trick / Switcheroo etc.
     * This helper exists so those effects can call it later without refactors.
     */
    public static boolean preventsItemRemoval(PokemonInstance holder) {
        return holder != null && has(holder, "stickyhold");
    }

    /** Apply ability-based priority bonus. */
    public static int priorityBonus(PokemonInstance user, Move move) {
        if (user == null || move == null) return 0;
        String a = norm(user.abilityId);
        // Prankster: +1 priority for status moves
        if ("prankster".equals(a) && "status".equalsIgnoreCase(move.category())) return 1;
        if ("galewings".equals(a) && move.type() != null && "flying".equalsIgnoreCase(move.type()) && pFullHp(user)) return 1;
        if ("triage".equals(a) && isHealingMove(move.id())) return 3;
        return 0;
    }

    private static boolean pFullHp(PokemonInstance p) {
        if (p == null) return false;
        try {
            return p.currentHp >= Math.max(1, p.maxHp(null));
        } catch (Throwable t) {
            return false;
        }
    }

    /** Speed multiplier contributed by abilities (non-weather). */
    public static double speedMultiplier(PokemonInstance p) {
        if (p == null) return 1.0;
        String a = norm(p.abilityId);
        if ("quickfeet".equals(a)) {
            if (p.status != null && !"none".equalsIgnoreCase(p.status)) return 1.5;
        }
        if ("unburden".equals(a)) {
            // When an item is consumed, plugin sets p.unburdenActive = true.
            if (p.unburdenActive) return 2.0;
        }
        if (("quarkdrive".equals(a) || "protosynthesis".equals(a)) && isParadoxAbilityActive(p, a)) {
            if ("spe".equals(highestParadoxStat(p))) return 1.5;
        }
        if ("slowstart".equals(a) && p.slowStartTurns > 0) return 0.5;
        return 1.0;
    }

    /** Speed multiplier contributed by abilities (including weather-dependent ones). */
    public static double speedMultiplier(PokemonInstance p, BattleSession s) {
        double base = speedMultiplier(p);
        if (p == null || s == null) return base;
        WeatherType w = WeatherSystem.effectiveWeather(s);
        if (w == WeatherType.NONE) return base;
        String a = norm(p.abilityId);
        // Weather speed abilities
        if (w == WeatherType.SUN && "chlorophyll".equals(a)) return base * 2.0;
        if (w == WeatherType.RAIN && "swiftswim".equals(a)) return base * 2.0;
        if (w == WeatherType.SAND && "sandrush".equals(a)) return base * 2.0;
        if (w == WeatherType.HAIL && "slushrush".equals(a)) return base * 2.0;
        if (s.terrainTurns > 0 && "electric".equalsIgnoreCase(s.terrain) && "surgesurfer".equals(a)) return base * 2.0;
        return base;
    }

    /** Leaf Guard: in sun, prevents major status. */
    public static boolean blocksMajorStatus(PokemonInstance defender, BattleSession s) {
        if (defender == null) return false;
        if (has(defender, "purifyingsalt") || has(defender, "comatose")) return true;
        if (has(defender, "flowerveil")) {
            Species sp = currentSpeciesFor(defender);
            if (hasType(sp, "grass")) return true;
        }
        if (s == null) return false;
        if (!has(defender, "leafguard")) return false;
        return WeatherSystem.effectiveWeather(s) == WeatherType.SUN;
    }

    
    /** Weather-dependent damage multiplier on attacker side (Solar Power / Sand Force etc). */
    public static double weatherAttackerDamageMultiplier(PokemonInstance atk, Move move, BattleSession s, String moveTypeLower) {
        if (atk == null || move == null || s == null) return 1.0;
        if (move.power() <= 0) return 1.0;
        WeatherType w = WeatherSystem.effectiveWeather(s);
        if (w == WeatherType.NONE) return 1.0;
        String a = norm(atk.abilityId);
        boolean special = "special".equalsIgnoreCase(move.category());
        if (w == WeatherType.SUN && "solarpower".equals(a) && special) {
            return 1.5;
        }
        if (w == WeatherType.SAND && "sandforce".equals(a) && moveTypeLower != null) {
            String t = moveTypeLower.toLowerCase(Locale.ROOT);
            if ("rock".equals(t) || "ground".equals(t) || "steel".equals(t)) return 1.3;
        }
        return 1.0;
    }

    /** Weather-dependent defensive multiplier (e.g., Rock types get 1.5x SpD in sand). Returns multiplier applied to damage (<=1). */
    public static double weatherDefenderDamageMultiplier(PokemonInstance def, Species defS, Move move, BattleSession s) {
        if (def == null || defS == null || move == null || s == null) return 1.0;
        if (move.power() <= 0) return 1.0;
        WeatherType w = WeatherSystem.effectiveWeather(s);
        if (w == WeatherType.SAND && "special".equalsIgnoreCase(move.category())) {
            if (WeatherSystem.isType(defS, "rock")) {
                return 1.0 / 1.5; // damage reduced
            }
        }
        return 1.0;
    }

/** Accuracy multiplier (attacker side). */
    public static double accuracyMultiplier(PokemonInstance user) {
        if (user == null) return 1.0;
        String a = norm(user.abilityId);
        if ("compoundeyes".equals(a)) return 1.3;
        // Illuminate has no direct battle effect in main-series 1v1; we give it a small accuracy boost
        // so it is still meaningful in this plugin's ruleset.
        if ("illuminate".equals(a)) return 1.1;
        if ("hustle".equals(a)) return 0.8; // physical only handled by caller
        if ("victorystar".equals(a)) return 1.1;
        return 1.0;
    }

    /** Damage multiplier applied on attacker side. */
    public static double attackerDamageMultiplier(PokemonInstance atk, PokemonInstance def, Move move, String moveType, double effectiveness) {
        if (atk == null || move == null) return 1.0;
        String a = norm(atk.abilityId);

        // Blaze/Torrent/Overgrow/Swarm - <= 1/3 HP
        if (atk.currentHp > 0) {
            int max = Math.max(1, atk.lockedMaxHp != null ? atk.lockedMaxHp : atk.maxHp(null));
            // maxHp(null) is safe but may be off if species unknown; caller should pass true species where possible.
        }

        // Ability-based physical/special boosts
        boolean special = "special".equalsIgnoreCase(move.category());
        boolean physical = "physical".equalsIgnoreCase(move.category());

        if (("hugepower".equals(a) || "purepower".equals(a)) && physical) return 2.0;
        if ("technician".equals(a) && move.power() > 0 && move.power() <= 60) return 1.5;
        if ("adaptability".equals(a)) {
            // STAB handled elsewhere; we approximate by boosting STAB from 1.5->2.0.
            // Caller will apply this by checking STAB and multiplying extra 4/3.
            return 1.0;
        }
        if ("analytic".equals(a)) {
            // We don't track full turn order here. BattleManager sets atk.actedLastThisTurn when relevant.
            if (atk.actedLastThisTurn) return 1.3;
        }
        if ("sniper".equals(a)) {
            // Crit multiplier handled elsewhere; skip.
        }

        // Critical status-based boosts
        if ("guts".equals(a) && physical) {
            if (atk.status != null && !"none".equalsIgnoreCase(atk.status)) return 1.5;
        }
        if ("flareboost".equals(a) && special) {
            if ("burn".equalsIgnoreCase(atk.status)) return 1.5;
        }
        if ("toxicboost".equals(a) && physical) {
            if ("poison".equalsIgnoreCase(atk.status) || "toxic".equalsIgnoreCase(atk.status)) return 1.5;
        }

        // Type-boost abilities based on HP threshold.
        if (atk.currentHp > 0 && moveType != null) {
            int maxHp = Math.max(1, atk.maxHp(null));
            boolean low = atk.currentHp * 3 <= maxHp;
            if (low) {
                switch (a) {
                    case "blaze" -> { if ("fire".equalsIgnoreCase(moveType)) return 1.5; }
                    case "torrent" -> { if ("water".equalsIgnoreCase(moveType)) return 1.5; }
                    case "overgrow" -> { if ("grass".equalsIgnoreCase(moveType)) return 1.5; }
                    case "swarm" -> { if ("bug".equalsIgnoreCase(moveType)) return 1.5; }
                }
            }
        }

        // Tinted Lens: doubles damage for resisted hits (<1x)
        if ("tintedlens".equals(a) && effectiveness > 0.0 && effectiveness < 1.0) return 2.0;

        // Rivalry: damage varies by gender matchup (approximation).
        // If genders are unknown, treat as neutral.
        if ("rivalry".equals(a) && def != null) {
            String g1 = atk.gender == null ? "" : atk.gender;
            String g2 = def.gender == null ? "" : def.gender;
            if (("M".equalsIgnoreCase(g1) || "F".equalsIgnoreCase(g1))
                    && ("M".equalsIgnoreCase(g2) || "F".equalsIgnoreCase(g2))) {
                if (g1.equalsIgnoreCase(g2)) return 1.25;
                else return 0.75;
            }
        }

        // Iron Fist: boost punching moves.
        if ("ironfist".equals(a) && isPunchMove(move.id())) return 1.2;

        // Reckless: boost recoil moves.
        if ("reckless".equals(a)) {
            for (var ef : move.effectsSafe()) {
                if (ef == null) continue;
                Object idObj = ef.get("id");
                if (idObj != null && "recoil".equalsIgnoreCase(String.valueOf(idObj))) {
                    return 1.2;
                }
            }
        }

        if ("sharpness".equals(a) && isSlicingMove(move.id())) return 1.5;
        if ("rockypayload".equals(a) && "rock".equalsIgnoreCase(moveType)) return 1.5;
        if ("strongjaw".equals(a) && isBitingMove(move.id())) return 1.5;
        if ("punkrock".equals(a) && isSoundMove(move.id())) return 1.3;
        if (("aerilate".equals(a) || "pixilate".equals(a) || "refrigerate".equals(a) || "galvanize".equals(a)) && move.type() != null && "normal".equalsIgnoreCase(move.type()) && move.power() > 0) return 1.2;
        if ("dragonsmaw".equals(a) && "dragon".equalsIgnoreCase(moveType)) return 1.5;
        if ("transistor".equals(a) && "electric".equalsIgnoreCase(moveType)) return 1.3;
        BattleSession sctx = contextSession();
        if (sctx != null) {
            if ("hadronengine".equals(a) && sctx.terrainTurns > 0 && "electric".equalsIgnoreCase(sctx.terrain) && special) return 1.333333333;
            if ("orichalcumpulse".equals(a) && WeatherSystem.effectiveWeather(sctx) == WeatherType.SUN && physical) return 1.333333333;
            if ("supremeoverlord".equals(a)) {
                int allies = (atk == sctx.playerMon) ? sctx.playerPartyFaintedCount : (atk == sctx.wildMon ? sctx.wildPartyFaintedCount : 0);
                allies = Math.max(0, Math.min(5, allies));
                if (allies > 0) return 1.0 + allies * 0.1;
            }
        }
        if (("quarkdrive".equals(a) || "protosynthesis".equals(a)) && isParadoxAbilityActive(atk, a)) {
            String best = highestParadoxStat(atk);
            if (special && "spa".equals(best)) return 1.3;
            if (physical && "atk".equals(best)) return 1.3;
        }
        if ("parentalbond".equals(a) && move.power() > 0) return 1.25;
        if (("asoneglastrier".equals(a) || "asonespectrier".equals(a)) && move.power() > 0) return 1.0;
        if ("toughclaws".equals(a) && physical && move.power() > 0) return 1.3;
        if ("battery".equals(a) && special && move.power() > 0) return 1.3;
        if ("powerspot".equals(a) && move.power() > 0) return 1.3;
        if ("gorillatactics".equals(a) && physical && move.power() > 0) return 1.5;
        if ("liquidvoice".equals(a) && isSoundMove(move.id()) && "water".equalsIgnoreCase(moveType) && move.power() > 0) return 1.2;
        if ("stakeout".equals(a) && def != null && def.justSwitchedIn) return 2.0;
        if (("steelyspirit".equals(a) || "steelworker".equals(a)) && "steel".equalsIgnoreCase(moveType)) return 1.5;
        if ("megalauncher".equals(a) && isPulseMove(move.id())) return 1.5;
        if ("waterbubble".equals(a) && "water".equalsIgnoreCase(moveType)) return 2.0;
        if ("defeatist".equals(a)) {
            int maxHp = Math.max(1, atk.maxHp(null));
            if (atk.currentHp * 2 <= maxHp && (physical || special)) return 0.5;
        }
        if (("plus".equals(a) || "minus".equals(a)) && special) return 1.5;
        if ("normalize".equals(a) && move.power() > 0) return 1.2;
        if ("neuroforce".equals(a) && effectiveness > 1.0) return 1.25;
        BattleSession auraCtx = contextSession();
        if (auraCtx != null) {
            PokemonInstance player = auraCtx.playerMon;
            PokemonInstance wild = auraCtx.wildMon;
            boolean auraBreak = has(player, "aurabreak") || has(wild, "aurabreak");
            if ((has(player, "darkaura") || has(wild, "darkaura")) && "dark".equalsIgnoreCase(moveType)) return auraBreak ? 0.75 : 1.333333333;
            if ((has(player, "fairyaura") || has(wild, "fairyaura")) && "fairy".equalsIgnoreCase(moveType)) return auraBreak ? 0.75 : 1.333333333;
            if ("flowergift".equals(a) && WeatherSystem.effectiveWeather(auraCtx) == WeatherType.SUN && physical) return 1.5;
            if ("slowstart".equals(a) && atk.slowStartTurns > 0 && physical) return 0.5;
            if ("beadsofruin".equals(a) && special) return 1.333333333;
            if ("swordofruin".equals(a) && physical) return 1.333333333;
        }

        return 1.0;
    }

    /** Triggered when the opponent lowers this Pokémon's stats (Defiant/Competitive). */
    public static void onOpponentStatLowered(PokemonInstance victim, String victimName, List<String> out) {
        if (victim == null || out == null) return;
        String a = norm(victim.abilityId);
        if ("defiant".equals(a)) {
            victim.applyStage("atk", 2);
            LangManager lang15 = lang();
        out.add(lang15==null?("§6【不服输】§e"+victimName+" 的攻击大幅提升了！"):lang15.uiFmt("battle.log.ability.defiant","§6【不服输】§e{mon} 的攻击大幅提升了！", java.util.Map.of("mon", victimName)));
        } else if ("competitive".equals(a)) {
            victim.applyStage("spa", 2);
            LangManager lang16 = lang();
        out.add(lang16==null?("§6【好胜】§e"+victimName+" 的特攻大幅提升了！"):lang16.uiFmt("battle.log.ability.competitive","§6【好胜】§e{mon} 的特攻大幅提升了！", java.util.Map.of("mon", victimName)));
        }
    }

    public static boolean blocksWonderGuardDamage(PokemonInstance def, PokemonInstance attacker, Move move, String moveType, double effectiveness) {
        if (def == null || move == null) return false;
        if (!has(def, "wonderguard")) return false;
        if (ignoresDefenderAbility(attacker)) return false;
        String mid = move.id() == null ? "" : norm(move.id());
        if ("struggle".equals(mid)) return false;
        String cat = move.category() == null ? "" : move.category().toLowerCase(java.util.Locale.ROOT);
        if ("status".equals(cat)) return false;
        return effectiveness <= 1.0;
    }

    /** Wonder Guard cannot be copied or overwritten by ordinary ability-changing effects. */
    public static boolean isAbilityChangeBlocked(String abilityId) {
        return "wonderguard".equals(norm(abilityId));
    }

    /** Damage multiplier applied on defender side. */
    public static double defenderDamageMultiplier(PokemonInstance def, Species defS, String moveType, double effectiveness, PokemonInstance attacker) {
        if (def == null) return 1.0;
        String a = norm(def.abilityId);

        // Mold Breaker family ignores most defensive abilities.
        if (ignoresDefenderAbility(attacker)) {
            // Still allow purely-stat based reductions (e.g., Thick Fat) to be ignored as well.
            // For our simplified ruleset we ignore ALL ability-based damage reductions here.
            return 1.0;
        }

        if ("thickfat".equals(a) && moveType != null) {
            if ("fire".equalsIgnoreCase(moveType) || "ice".equalsIgnoreCase(moveType)) return 0.5;
        }
        if ("heatproof".equals(a) && "fire".equalsIgnoreCase(moveType)) return 0.5;
        if ("purifyingsalt".equals(a) && "ghost".equalsIgnoreCase(moveType)) return 0.5;

        // Wonder Guard: only super-effective damaging hits can connect.
        if ("wonderguard".equals(a) && effectiveness <= 1.0 && effectiveness > 0.0) return 0.0;

        // Dry Skin: takes extra damage from Fire.
        if ("dryskin".equals(a) && "fire".equalsIgnoreCase(moveType)) return 1.25;
        if ("furcoat".equals(a)) {
            // Caller will apply only for physical; we don't know here.
            return 1.0;
        }
        if (("filter".equals(a) || "solidrock".equals(a) || "prismarmor".equals(a)) && effectiveness > 1.0) return 0.75;

        // Friend Guard is normally a doubles-only ally damage reduction.
        // This project is mostly 1v1, so we apply it as a self damage reduction to keep the ability useful.
        if ("friendguard".equals(a)) return 0.75;
        if ("icescales".equals(a) && attacker != null) {
            // handled elsewhere for special only
        }
        if ("terashell".equals(a) && defS != null) {
            int max = Math.max(1, def.maxHp(defS));
            if (def.currentHp >= max) return 0.5;
        }
        if (activeDeltaStream() && defS != null && hasType(defS, "flying") && effectiveness > 1.0 && moveType != null) {
            if ("electric".equalsIgnoreCase(moveType) || "ice".equalsIgnoreCase(moveType) || "rock".equalsIgnoreCase(moveType)) return 0.5;
        }
        // Multiscale: halve damage at full HP.
        if (("multiscale".equals(a) || "shadowshield".equals(a)) && defS != null) {
            int max = Math.max(1, def.maxHp(defS));
            if (def.currentHp >= max) return 0.5;
        }

        // Marvel Scale: if status, boost defense (handled in Damage by increasing D)
        return 1.0;
    }

    /** Whether defender is immune to a move type due to ability (and may heal). */
    public static AbilityImmunityResult immunity(PokemonInstance def, String moveType) {
        if (def == null || moveType == null) return AbilityImmunityResult.none();
        String a = norm(def.abilityId);
        moveType = moveType.toLowerCase(Locale.ROOT);

        // Levitate
        if ("levitate".equals(a) && "ground".equals(moveType)) return AbilityImmunityResult.immune(logAbility("levitate_immune", "§6【Levitate】§eIt doesn't affect the target!"));
        if ("eartheater".equals(a) && "ground".equals(moveType)) return AbilityImmunityResult.heal(logAbility("earth_eater", "§6【Earth Eater】§aIt ate the earth and restored HP!"));
        // Flash Fire / Water Absorb / Volt Absorb / Lightning Rod / Motor Drive / Sap Sipper
        if ("flashfire".equals(a) && "fire".equals(moveType)) return AbilityImmunityResult.boostOrHeal(logAbility("flash_fire", "§6【Flash Fire】§eIt absorbed the fire!"), AbilityImmunityResult.Kind.FLASH_FIRE);
        if ("waterabsorb".equals(a) && "water".equals(moveType)) return AbilityImmunityResult.heal(logAbility("water_absorb", "§6【Water Absorb】§aIt restored HP!"));
        if ("voltabsorb".equals(a) && "electric".equals(moveType)) return AbilityImmunityResult.heal(logAbility("volt_absorb", "§6【Volt Absorb】§aIt restored HP!"));
        if ("lightningrod".equals(a) && "electric".equals(moveType)) return AbilityImmunityResult.heal(logAbility("lightning_rod", "§6【Lightning Rod】§aIt drew in the electric move!"));
        if ("motordrive".equals(a) && "electric".equals(moveType)) return AbilityImmunityResult.boost(logAbility("motor_drive", "§6【Motor Drive】§eIts Speed rose!"), "spe", 1);
        if ("sapsipper".equals(a) && "grass".equals(moveType)) return AbilityImmunityResult.boost(logAbility("sap_sipper", "§6【Sap Sipper】§eIts Attack rose!"), "atk", 1);
        if ("stormdrain".equals(a) && "water".equals(moveType)) return AbilityImmunityResult.boost(logAbility("storm_drain", "§6【Storm Drain】§eIts Sp. Atk rose!"), "spa", 1);
        if ("wellbakedbody".equals(a) && "fire".equals(moveType)) return AbilityImmunityResult.boost(logAbility("well_baked_body", "§6【Well-Baked Body】§eIts Defense rose sharply!"), "def", 2);
        if (activeDesolateLand() && "water".equals(moveType)) return AbilityImmunityResult.immune(logAbility("desolate_land_block", "§6【Desolate Land】§eThe Water-type move evaporated!"));
        if (activePrimordialSea() && "fire".equals(moveType)) return AbilityImmunityResult.immune(logAbility("primordial_sea_block", "§6【Primordial Sea】§eThe Fire-type move fizzled out in the heavy rain!"));

        // Dry Skin: absorbs Water to heal (simplified to same heal amount as Water Absorb).
        if ("dryskin".equals(a) && "water".equals(moveType)) return AbilityImmunityResult.heal(logAbility("dry_skin", "§6【Dry Skin】§aIt restored HP!"));

        return AbilityImmunityResult.none();
    }

    /** Status immunity by ability. */
    public static boolean isStatusImmune(PokemonInstance target, String status) {
        if (target == null || status == null) return false;
        String a = norm(target.abilityId);
        status = status.toLowerCase(Locale.ROOT);
        return switch (status) {
            case "sleep" -> "insomnia".equals(a) || "vitalspirit".equals(a) || "sweetveil".equals(a) || "comatose".equals(a);
            case "burn" -> "waterveil".equals(a) || "waterbubble".equals(a) || "thermalexchange".equals(a) || "magmaarmor".equals(a);
            case "paralyze" -> "limber".equals(a);
            case "poison", "toxic" -> "immunity".equals(a) || "pastelveil".equals(a) || "comatose".equals(a);
            default -> false;
        };
    }

    /** Called when status is successfully applied to target. */
    public static List<String> onStatusApplied(PokemonInstance target, Species targetS, String targetName,
                                              PokemonInstance source, Species sourceS, String sourceName,
                                              String status) {
        List<String> out = new ArrayList<>();
        if (target == null) return out;
        String a = norm(target.abilityId);
        status = status == null ? "" : status.toLowerCase(Locale.ROOT);

        if (source != null && ("poison".equals(status) || "toxic".equals(status)) && has(source, "poisonpuppeteer") && target.currentHp > 0) {
            if (target.confusionTurns <= 0) {
                target.confusionTurns = 2 + Util.RND.nextInt(3);
                out.add(logAbilityFmt("poison_puppeteer", "§6【Poison Puppeteer】§d{target} became confused!", java.util.Map.of("target", targetName)));
            }
        }

        // Synchronize: reflect poison/burn/paralyze back to source
        if ("synchronize".equals(a) && source != null && source.currentHp > 0) {
            boolean reflect = status.equals("poison") || status.equals("toxic") || status.equals("burn") || status.equals("paralyze");
            if (reflect) {
                if (!isStatusImmune(source, status) && (source.status == null || "none".equalsIgnoreCase(source.status))) {
                    source.status = status;
                    if ("toxic".equals(status)) source.toxicCounter = 1;
                    LangManager lang17 = lang();
        out.add(lang17==null?("§6【同步】§e"+targetName+" 将异常状态同步给了 "+sourceName+"！"):lang17.uiFmt("battle.log.ability.synchronize","§6【同步】§e{target} 将异常状态同步给了 {source}！", java.util.Map.of("target", targetName, "source", sourceName)));
                }
            }
        }
        return out;
    }

    /** Called after a damaging move hits (for contact/status abilities etc.). */
    public static List<String> onAfterDamagingHit(PokemonInstance atk, Species atkS, String atkName,
                                                 PokemonInstance def, Species defS, String defName,
                                                 Move move, int damageDealt,
                                                 boolean treatedAsContact) {
        List<String> out = new ArrayList<>();
        if (atk == null || def == null || move == null) return out;
        if (damageDealt <= 0) return out;
        if (atk.currentHp <= 0 || def.currentHp <= 0) return out;

        String defA = norm(def.abilityId);

        // Rough Skin / Iron Barbs: recoil on contact
        if (treatedAsContact && ("roughskin".equals(defA) || "ironbarbs".equals(defA))) {
            // Magic Guard: no indirect damage.
            if (has(atk, "magicguard")) return out;
            int max = Math.max(1, atk.maxHp(atkS));
            int recoil = Math.max(1, max / 8);
            int before = atk.currentHp;
            atk.currentHp = Math.max(0, atk.currentHp - recoil);
            if (atk.currentHp < before) {
            LangManager lang18 = lang();
            String n = String.valueOf(before - atk.currentHp);
            String ab = abilityDisplay(defA);
            out.add(lang18==null?("§6【"+ab+"】§c"+atkName+" 受到了反伤！ §7(-"+n+")"):lang18.uiFmt("battle.log.ability.contact_recoil","§6【{ab}】§c{atk} 受到了反伤！ §7(-{n})", java.util.Map.of("ab", ab, "atk", atkName, "n", n)));
        }
        }

        // Static / Flame Body / Poison Point / Effect Spore: 30% inflict
        if (treatedAsContact) {
            double roll = Util.RND.nextDouble();
            if ("static".equals(defA) && roll < 0.30) maybeInflict(atk, atkName, "paralyze", out);
            if ("flamebody".equals(defA) && roll < 0.30) maybeInflict(atk, atkName, "burn", out);
            if ("poisonpoint".equals(defA) && roll < 0.30) maybeInflict(atk, atkName, "poison", out);
            if ("effectspore".equals(defA) && roll < 0.30) {
                // random: poison/paralyze/sleep (simplified)
                int r = Util.RND.nextInt(3);
                String st = r==0?"poison":(r==1?"paralyze":"sleep");
                maybeInflict(atk, atkName, st, out);
            }
        }

        // Cute Charm: 30% infatuate attacker on contact (simplified; ignores gender). Oblivious prevents it.
        if (treatedAsContact && "cutecharm".equals(defA) && Util.RND.nextDouble() < 0.30) {
            if (!AbilityEffects.has(atk, "oblivious")) {
                if (atk != null && !atk.infatuated) {
                    atk.infatuated = true;
                    LangManager lang19 = lang();
        out.add(lang19==null?("§6【迷人之躯】§d"+atkName+" 陷入了爱河！"):lang19.uiFmt("battle.log.ability.cute_charm","§6【迷人之躯】§d{atk} 陷入了爱河！", java.util.Map.of("atk", atkName)));
                }
            } else {
                LangManager lang20 = lang();
        out.add(lang20==null?("§6【迷人之躯】§e"+atkName+" 没有被影响（迟钝）"):lang20.uiFmt("battle.log.ability.cute_charm_oblivious","§6【迷人之躯】§e{atk} 没有被影响（迟钝）", java.util.Map.of("atk", atkName)));
            }
        }
        // Aftermath: when defender faints from a contact move, attacker takes 1/4 max HP.
        // This is handled in onFaint, see below.

        // Poison Touch: 30% to poison on contact.
        if (treatedAsContact && has(atk, "poisontouch") && defS != null) {
            if ((def.status == null || "none".equalsIgnoreCase(def.status) || def.status.isBlank())
                    && !isStatusImmune(def, "poison")
                    && defS.types().stream().noneMatch(t -> "poison".equalsIgnoreCase(t) || "steel".equalsIgnoreCase(t))) {
                if (Util.RND.nextDouble() < 0.30) {
                    def.status = "poison";
                    LangManager lang21 = lang();
        out.add(lang21==null?("§6【毒手】§d"+atkName+" 让 "+defName+" 中毒了！"):lang21.uiFmt("battle.log.ability.poison_touch","§6【毒手】§d{atk} 让 {def} 中毒了！", java.util.Map.of("atk", atkName, "def", defName)));
                    out.addAll(onStatusApplied(def, defS, defName, atk, atkS, atkName, "poison"));
                }
            }
        }

        // Rattled: Speed +1 when hit by Bug/Dark/Ghost moves.
        if (has(def, "rattled")) {
            String mt = move.type();
            if (mt != null) {
                mt = mt.toLowerCase(java.util.Locale.ROOT);
                if (("bug".equals(mt) || "dark".equals(mt) || "ghost".equals(mt)) && def.currentHp > 0) {
                    int before = def.stageSpe;
                    def.applyStage("spe", 1);
                    if (def.stageSpe != before) {
            LangManager lang22 = lang();
            out.add(lang22==null?("§6【胆怯】§e"+defName+" 的速度提升了！"):lang22.uiFmt("battle.log.ability.rattled","§6【胆怯】§e{mon} 的速度提升了！", java.util.Map.of("mon", defName)));
        }
                }
            }
        }

        // Weak Armor: when hit by a physical move, Defense -1 and Speed +2.
        if (has(def, "weakarmor") && "physical".equalsIgnoreCase(move.category()) && def.currentHp > 0) {
            int beforeDef = def.stageDef;
            int beforeSpe = def.stageSpe;
            def.applyStage("def", -1);
            def.applyStage("spe", 2);
            if (def.stageDef != beforeDef || def.stageSpe != beforeSpe) {
                LangManager lang23 = lang();
        out.add(lang23==null?("§6【碎甲】§e"+defName+" 的防御下降了，速度大幅提升！"):lang23.uiFmt("battle.log.ability.weak_armor","§6【碎甲】§e{def} 的防御下降了，速度大幅提升！", java.util.Map.of("def", defName)));
            }
        }

        if (has(def, "thermalexchange")) {
            String mt = move.type();
            if (mt != null && "fire".equalsIgnoreCase(mt) && def.currentHp > 0) {
                int beforeAtk = def.stageAtk;
                def.applyStage("atk", 1);
                if (def.stageAtk != beforeAtk) {
                    out.add(logAbilityFmt("thermal_exchange", "§6【Thermal Exchange】§e{def}'s Attack rose!", java.util.Map.of("def", defName)));
                }
            }
        }

        if (has(def, "electromorphosis") && def.currentHp > 0) {
            def.chargeActive = true;
            out.add(logAbilityFmt("electromorphosis", "§6【Electromorphosis】§e{def} became charged with power!", java.util.Map.of("def", defName)));
        }

        if (has(def, "toxicdebris") && "physical".equalsIgnoreCase(move.category()) && def.currentHp > 0) {
            BattleSession ctx = contextSession();
            if (ctx != null) {
                boolean defenderIsPlayer = ctx.playerMon == def;
                int beforeLayers = defenderIsPlayer ? ctx.wildToxicSpikesLayers : ctx.playerToxicSpikesLayers;
                int afterLayers = Math.min(2, beforeLayers + 1);
                if (defenderIsPlayer) ctx.wildToxicSpikesLayers = afterLayers; else ctx.playerToxicSpikesLayers = afterLayers;
                if (afterLayers > beforeLayers) {
                    out.add(logAbility("toxic_debris", "§6【Toxic Debris】§ePoison spikes were scattered on the opposing side!"));
                }
            }
        }
        if (has(def, "watercompaction")) {
            String mt = move.type();
            if (mt != null && "water".equalsIgnoreCase(mt) && def.currentHp > 0) {
                int beforeDef = def.stageDef;
                def.applyStage("def", 2);
                if (def.stageDef != beforeDef) out.add(logAbilityFmt("water_compaction", "§6【Water Compaction】§e{def}'s Defense rose sharply!", java.util.Map.of("def", defName)));
            }
        }

        if (has(def, "steamengine")) {
            String mt = move.type();
            if (mt != null && ("water".equalsIgnoreCase(mt) || "fire".equalsIgnoreCase(mt)) && def.currentHp > 0) {
                int beforeSpe = def.stageSpe;
                def.applyStage("spe", 6);
                if (def.stageSpe != beforeSpe) out.add(logAbilityFmt("steam_engine", "§6【Steam Engine】§e{def}'s Speed rose drastically!", java.util.Map.of("def", defName)));
            }
        }

        if (has(def, "berserk") && damageDealt > 0 && "special".equalsIgnoreCase(move.category()) && defS != null && def.currentHp > 0) {
            int max = Math.max(1, def.maxHp(defS));
            if (def.currentHp * 2 <= max) {
                int beforeSpa = def.stageSpa;
                def.applyStage("spa", 1);
                if (def.stageSpa != beforeSpa) out.add(logAbilityFmt("berserk", "§6【Berserk】§e{def}'s Sp. Atk rose!", java.util.Map.of("def", defName)));
            }
        }
        if (has(def, "angershell") && defS != null && def.currentHp > 0 && !def.angerShellUsed) {
            int max = Math.max(1, def.maxHp(defS));
            if (def.currentHp * 2 <= max) {
                def.angerShellUsed = true;
                def.applyStage("atk", 1); def.applyStage("spa", 1); def.applyStage("spe", 1);
                def.applyStage("def", -1); def.applyStage("spd", -1);
                out.add(logAbilityFmt("anger_shell", "§6【Anger Shell】§e{def}'s stats changed!", java.util.Map.of("def", defName)));
            }
        }
        if ((has(def, "wimpout") || has(def, "emergencyexit")) && defS != null && def.currentHp > 0) {
            int max = Math.max(1, def.maxHp(defS));
            if (def.currentHp * 2 <= max) {
                def.emergencyExitTriggered = true;
                out.add(logAbilityFmt("emergency_exit", "§6【{ab}】§e{def} is about to be withdrawn!", java.util.Map.of("ab", abilityDisplay(norm(def.abilityId)), "def", defName)));
            }
        }
        if (has(atk, "magician") && atk.currentHp > 0 && (atk.heldItemId == null || atk.heldItemId.isBlank())
                && def.heldItemId != null && !def.heldItemId.isBlank() && !preventsItemRemoval(def)) {
            atk.heldItemId = def.heldItemId;
            def.heldItemId = null;
            out.add(logAbilityFmt("magician", "§6【Magician】§e{atk} stole {def}'s item!", java.util.Map.of("atk", atkName, "def", defName)));
        }
        if (treatedAsContact && has(def, "wanderingspirit") && atk.currentHp > 0 && def.currentHp > 0) {
            String atkAb = atk.abilityId;
            atk.abilityId = def.abilityId;
            def.abilityId = atkAb;
            out.add(logAbilityFmt("wandering_spirit", "§6【Wandering Spirit】§e{atk} swapped Abilities with {def}!", java.util.Map.of("atk", atkName, "def", defName)));
        }
        if (has(def, "gulpmissile") && def.currentHp > 0 && def.overrideSpeciesId != null
                && ("cramorantgulping".equalsIgnoreCase(def.overrideSpeciesId) || "cramorantgorging".equalsIgnoreCase(def.overrideSpeciesId))) {
            int maxAtk = Math.max(1, atk.maxHp(atkS));
            int chip = Math.max(1, maxAtk / 4);
            atk.currentHp = Math.max(0, atk.currentHp - chip);
            if ("cramorantgulping".equalsIgnoreCase(def.overrideSpeciesId)) maybeInflict(atk, atkName, "paralyze", out);
            else atk.applyStage("def", -1);
            def.overrideSpeciesId = null;
            out.add(logAbilityFmt("gulp_missile", "§6【Gulp Missile】§e{def} launched the thing in its mouth!", java.util.Map.of("def", defName)));
        }

        BattleSession ctx2 = contextSession();
        if (ctx2 != null && has(def, "sandspit") && damageDealt > 0 && def.currentHp > 0) {
            int turns = WeatherSystem.durationForSource(WeatherType.SAND, def);
            WeatherSystem.setWeather(ctx2, WeatherType.SAND, turns);
            out.add(logAbilityFmt("sand_spit", "§6【Sand Spit】§eA sandstorm kicked up! §7({turns} turns)", java.util.Map.of("turns", String.valueOf(turns))));
        }
        if (ctx2 != null && has(def, "seedsower") && damageDealt > 0 && def.currentHp > 0) {
            ctx2.terrain = "grassy";
            ctx2.terrainTurns = 5;
            out.add(logAbility("seed_sower", "§6【Seed Sower】§eGrassy Terrain spread across the field!"));
        }
        if (has(def, "windpower") && damageDealt > 0) {
            String mid = norm(move.id());
            if (mid != null && !mid.isBlank() && java.util.Set.of("gust","whirlwind","razorwind","tailwind","twister","hurricane","bleakwindstorm","fairywind","heatwave","icywind","ominouswind","petalblizzard","airslash").contains(mid)) {
                def.chargeActive = true;
                out.add(logAbilityFmt("wind_power", "§6【Wind Power】§e{def} became charged with power!", java.util.Map.of("def", defName)));
            }
        }
        if (treatedAsContact && has(def, "perishbody") && atk.currentHp > 0 && def.currentHp > 0) {
            if (atk.perishSongTurns <= 0) atk.perishSongTurns = 4;
            if (def.perishSongTurns <= 0) def.perishSongTurns = 4;
            out.add(logAbility("perish_body", "§6【Perish Body】§eBoth Pokémon were subjected to Perish Song!"));
        }
        if (has(atk, "toxicchain") && damageDealt > 0 && def.currentHp > 0) {
            boolean canPoison = (def.status == null || "none".equalsIgnoreCase(def.status) || def.status.isBlank()) && !isStatusImmune(def, "poison");
            if (canPoison && Util.RND.nextDouble() < 0.30) {
                def.status = "toxic";
                out.add(logAbilityFmt("toxic_chain", "§6【Toxic Chain】§d{atk} badly poisoned {def}!", java.util.Map.of("atk", atkName, "def", defName)));
                out.addAll(onStatusApplied(def, defS, defName, atk, atkS, atkName, "toxic"));
            }
        }

        if (has(def, "stamina") && def.currentHp > 0) {
            int beforeDef = def.stageDef;
            def.applyStage("def", 1);
            if (def.stageDef != beforeDef) out.add(logAbilityFmt("stamina", "§6【Stamina】§e{def}'s Defense rose!", java.util.Map.of("def", defName)));
        }

        if (treatedAsContact && (has(def, "gooey") || has(def, "tanglinghair")) && atk.currentHp > 0) {
            int beforeSpe = atk.stageSpe;
            atk.applyStage("spe", -1);
            if (atk.stageSpe != beforeSpe) out.add(logAbilityFmt("speed_drop_contact", "§6【{ab}】§e{atk}'s Speed fell!", java.util.Map.of("ab", abilityDisplay(norm(def.abilityId)), "atk", atkName)));
        }

        if (has(def, "cottondown") && atk.currentHp > 0) {
            int beforeSpe = atk.stageSpe;
            atk.applyStage("spe", -1);
            if (atk.stageSpe != beforeSpe) out.add(logAbilityFmt("cotton_down", "§6【Cotton Down】§e{atk}'s Speed fell!", java.util.Map.of("atk", atkName)));
        }

        if (treatedAsContact && has(def, "pickpocket") && (def.heldItemId == null || def.heldItemId.isBlank())
                && atk.heldItemId != null && !atk.heldItemId.isBlank()
                && !preventsItemRemoval(atk) && !contactBlockedByLongReach(atk)) {
            String stolen = atk.heldItemId;
            atk.heldItemId = null;
            def.heldItemId = stolen;
            out.add(logAbilityFmt("pickpocket", "§6【Pickpocket】§e{def} stole {atk}'s item!", java.util.Map.of("def", defName, "atk", atkName)));
        }

        // Cursed Body: 30% chance to disable the attacker's used move.
        if (has(def, "cursedbody")) {
            String mid = move.id();
            if (mid != null && !mid.isBlank() && Util.RND.nextDouble() < 0.30) {
                atk.disabledMoveId = mid.toLowerCase(Locale.ROOT);
                atk.disabledTurns = 4;
                LangManager lang24 = lang();
        out.add(lang24==null?("§6【诅咒之躯】§d"+defName+" 封印了 "+atkName+" 的招式！"):lang24.uiFmt("battle.log.ability.cursed_body","§6【诅咒之躯】§d{def} 封印了 {atk} 的招式！", java.util.Map.of("def", defName, "atk", atkName)));
                if (atk != null && "mental_herb".equalsIgnoreCase(atk.heldItemId)) {
                    atk.heldItemId = null;
                    atk.disabledMoveId = null;
                    atk.disabledTurns = 0;
                    LangManager lang25 = lang();
        out.add(lang25==null?("§e"+atkName+" 的§f心灵香草§e发动了！定身法效果被解除！"):lang25.uiFmt("battle.log.item.mental_herb_disable","§e{atk} 的§f心灵香草§e发动了！定身法效果被解除！", java.util.Map.of("atk", atkName)));
                }
            }
        }

        return out;
    }

    /** Called when the attacker knocks out the defender with a damaging move. */
    public static List<String> onKnockOut(PokemonInstance attacker, Species atkS, String atkName,
                                         PokemonInstance fainted, Species faintedS, String faintedName) {
        List<String> out = new ArrayList<>();
        if (attacker == null || attacker.currentHp <= 0) return out;
        String a = norm(attacker.abilityId);
        if ("moxie".equals(a)) {
            attacker.applyStage("atk", 1);
            LangManager lang26 = lang();
        out.add(lang26==null?("§6【自信过度】§e"+atkName+" 的攻击提升了！"):lang26.uiFmt("battle.log.ability.moxie","§6【自信过度】§e{atk} 的攻击提升了！", java.util.Map.of("atk", atkName)));
        }
        if ("chillingneigh".equals(a) || "asoneglastrier".equals(a)) {
            attacker.applyStage("atk", 1);
            out.add(logAbilityFmt("chilling_neigh", "§6【Chilling Neigh】§e{atk}'s Attack rose!", java.util.Map.of("atk", atkName)));
        }
        if ("grimneigh".equals(a) || "asonespectrier".equals(a)) {
            attacker.applyStage("spa", 1);
            out.add(logAbilityFmt("grim_neigh", "§6【Grim Neigh】§e{atk}'s Sp. Atk rose!", java.util.Map.of("atk", atkName)));
        }
        if ("soulheart".equals(a)) {
            attacker.applyStage("spa", 1);
            out.add(logAbilityFmt("soul_heart", "§6【Soul-Heart】§e{atk}'s Sp. Atk rose!", java.util.Map.of("atk", atkName)));
        }
        if ("beastboost".equals(a)) {
            String best = highestParadoxStat(attacker);
            if (best != null) {
                int beforeAtk = attacker.stageAtk, beforeDef = attacker.stageDef, beforeSpa = attacker.stageSpa, beforeSpd = attacker.stageSpd, beforeSpe = attacker.stageSpe;
                attacker.applyStage(best, 1);
                boolean changed = attacker.stageAtk != beforeAtk || attacker.stageDef != beforeDef || attacker.stageSpa != beforeSpa || attacker.stageSpd != beforeSpd || attacker.stageSpe != beforeSpe;
                if (changed) out.add(logAbilityFmt("beast_boost", "§6【Beast Boost】§e{atk}'s {stat} rose!", java.util.Map.of("atk", atkName, "stat", statDisplayName(best))));
            }
        }
        if ("battlebond".equals(a) && attacker.speciesId != null && attacker.speciesId.equalsIgnoreCase("greninja") && !attacker.battleBondUsed) {
            attacker.overrideSpeciesId = "greninjaash";
            attacker.battleBondUsed = true;
            out.add(logAbilityFmt("battle_bond", "§6【Battle Bond】§e{atk} transformed!", java.util.Map.of("atk", atkName)));
        }
        return out;
    }

    public static List<String> onFaint(PokemonInstance fainted, Species faintedS, String faintedName,
                                       PokemonInstance killer, Species killerS, String killerName,
                                       Move lastMove, boolean treatedAsContact) {
        List<String> out = new ArrayList<>();
        if (fainted == null || faintedS == null) return out;
        String a = norm(fainted.abilityId);
        if ("aftermath".equals(a) && killer != null && killer.currentHp > 0 && treatedAsContact) {
            if (has(killer, "magicguard")) return out;
            int max = Math.max(1, killer.maxHp(killerS));
            int dmg = Math.max(1, max / 4);
            int before = killer.currentHp;
            killer.currentHp = Math.max(0, killer.currentHp - dmg);
            if (killer.currentHp < before) {
            LangManager lang27 = lang();
            String n = String.valueOf(before - killer.currentHp);
            out.add(lang27==null?("§6【余波】§c"+killerName+" 受到了爆炸伤害！ §7(-"+n+")"):lang27.uiFmt("battle.log.ability.aftermath","§6【余波】§c{killer} 受到了爆炸伤害！ §7(-{n})", java.util.Map.of("killer", killerName, "n", n)));
        }
        }
        if ("innardsout".equals(a) && killer != null && killer.currentHp > 0 && fainted.lastDamageTaken > 0) {
            int dmg = Math.min(killer.currentHp, fainted.lastDamageTaken);
            killer.currentHp = Math.max(0, killer.currentHp - dmg);
            out.add(logAbilityFmt("innards_out", "§6【Innards Out】§c{killer} was hurt! §7(-{dmg})", java.util.Map.of("killer", killerName, "dmg", String.valueOf(dmg))));
        }
        return out;
    }

    /** Prevent recoil damage from recoil moves. */
    public static boolean preventsRecoil(PokemonInstance p) {
        if (p == null) return false;
        String a = norm(p.abilityId);
        return "rockhead".equals(a) || "magicguard".equals(a);
    }

    /** Modify base Defense/SpD stats for abilities. */
    public static double defenseStatMultiplier(PokemonInstance def, boolean special) {
        if (def == null) return 1.0;
        String a = norm(def.abilityId);
        if ("marvelscale".equals(a) && def.status != null && !"none".equalsIgnoreCase(def.status)) {
            if (!special) return 1.5;
        }
        BattleSession gs = contextSession();
        if ("grasspelt".equals(a) && !special && gs != null && gs.terrainTurns > 0 && "grassy".equalsIgnoreCase(gs.terrain)) return 1.5;
        if ("furcoat".equals(a) && !special) return 2.0;
        if ("flowergift".equals(a) && special) {
            BattleSession sctx = contextSession();
            if (sctx != null && WeatherSystem.effectiveWeather(sctx) == WeatherType.SUN) return 1.5;
        }
        if (("quarkdrive".equals(a) || "protosynthesis".equals(a)) && isParadoxAbilityActive(def, a)) {
            String best = highestParadoxStat(def);
            if (!special && "def".equals(best)) return 1.3;
            if (special && "spd".equals(best)) return 1.3;
        }
        return 1.0;
    }

    /** Apply ability-based survival (Sturdy). */
    public static boolean sturdySaves(PokemonInstance target, Species targetS, int incomingDamage) {
        if (target == null || targetS == null) return false;
        if (!"sturdy".equals(norm(target.abilityId))) return false;
        int max = Math.max(1, target.maxHp(targetS));
        return target.currentHp == max && incomingDamage >= target.currentHp;
    }

    /** Pressure: extra PP drain on attacker (handled by caller). */
    public static boolean hasPressure(PokemonInstance p) {
        return p != null && "pressure".equals(norm(p.abilityId));
    }

    public static boolean allowsCorrosion(PokemonInstance p) {
        return p != null && "corrosion".equals(norm(p.abilityId));
    }

    public static boolean reflectsStatDrops(PokemonInstance p) {
        return p != null && "mirrorarmor".equals(norm(p.abilityId));
    }

    public static boolean bypassesProtect(PokemonInstance attacker, Move move) {
        if (attacker == null || move == null) return false;
        if (!"unseenfist".equals(norm(attacker.abilityId))) return false;
        return "physical".equalsIgnoreCase(move.category()) && move.power() > 0;
    }

    public static boolean hasRipen(PokemonInstance p) {
        return p != null && "ripen".equals(norm(p.abilityId));
    }


    /** Whether an ability blocks secondary effects (追加效果) from damaging moves. */
    public static boolean blocksSecondary(PokemonInstance def) {
        return def != null && "shielddust".equals(norm(def.abilityId));
    }

    /** Serene Grace: doubles secondary-effect chances on damaging moves. */
    public static double secondaryChanceMultiplier(PokemonInstance atk, Move move) {
        if (atk == null || move == null) return 1.0;
        if (!"serenegrace".equals(norm(atk.abilityId))) return 1.0;
        // Only secondary effects of damaging moves are boosted.
        if ("status".equalsIgnoreCase(move.category())) return 1.0;
        return 2.0;
    }

    /** Sheer Force: removes secondary effects and boosts damage. */
    public static boolean hasSheerForce(PokemonInstance atk) {
        return atk != null && "sheerforce".equals(norm(atk.abilityId));
    }

    /**
     * Determine whether a move has secondary effects that Sheer Force / Serene Grace would affect.
     *
     * We treat effects with chance < 1.0 on a damaging move as "secondary".
     */
    public static boolean moveHasSecondaryEffects(Move move) {
        if (move == null) return false;
        if ("status".equalsIgnoreCase(move.category())) return false;
        try {
            for (java.util.Map<String, Object> ef : move.effectsSafe()) {
                if (ef == null) continue;
                String id = String.valueOf(ef.getOrDefault("id", "")).toLowerCase(java.util.Locale.ROOT);
                double chance = 1.0;
                if (ef.containsKey("chance")) {
                    try { chance = Double.parseDouble(String.valueOf(ef.get("chance"))); } catch (Exception ignored) {}
                }
                if (chance >= 0.999) continue;
                if ("set_status".equals(id) || "stat_stage".equals(id) || "flinch".equals(id) || "confusion".equals(id)) {
                    return true;
                }
            }
        } catch (Throwable ignored) {}
        try {
            java.util.Map<String, Object> ef = move.effect();
            if (ef != null && ef.containsKey("chance")) {
                double chance = Double.parseDouble(String.valueOf(ef.get("chance")));
                if (chance < 0.999) return true;
            }
        } catch (Throwable ignored) {}
        return false;
    }

    /**
     * Trapping abilities (Shadow Tag / Arena Trap / Magnet Pull).
     *
     * <p>We implement a modern, simplified ruleset:</p>
     * <ul>
     *   <li>Shadow Tag does not trap Ghost-types.</li>
     *   <li>Arena Trap does not trap Flying-types or Levitate users.</li>
     *   <li>Magnet Pull traps Steel-types.</li>
     *   <li>Shed Shell always allows switching.</li>
     * </ul>
     *
     * <p>This is used by the battle UI switch flow to prevent player switching.</p>
     */
    public static String trappedReason(PokemonInstance self, Species selfS,
                                       PokemonInstance foe, Species foeS) {
        if (self == null || selfS == null || foe == null || foeS == null) return null;

        // Shed Shell escapes all traps.
        if (self.heldItemId != null && self.heldItemId.equalsIgnoreCase("shed_shell")) return null;

        String foeA = norm(foe.abilityId);
        if (foeA.isEmpty()) return null;

        boolean selfGhost = hasType(selfS, "ghost");
        boolean selfFlying = hasType(selfS, "flying");
        boolean selfSteel = hasType(selfS, "steel");
        boolean selfLevitate = "levitate".equals(norm(self.abilityId));

        // Shadow Tag
        if ("shadowtag".equals(foeA)) {
            if (!selfGhost) return "§6【踩影】§e对手的特性阻止了你换下场！";
        }
        // Arena Trap
        if ("arenatrap".equals(foeA)) {
            if (!selfFlying && !selfLevitate) return "§6【沙穴】§e对手的特性阻止了你换下场！";
        }
        // Magnet Pull
        if ("magnetpull".equals(foeA)) {
            if (selfSteel) return "§6【磁力】§e对手的特性阻止了你换下场！";
        }

        return null;
    }

    private static boolean hasType(Species s, String type) {
        if (s == null || s.types() == null) return false;
        for (String t : s.types()) {
            if (t != null && t.equalsIgnoreCase(type)) return true;
        }
        return false;
    }

    public static boolean blocksMentalMove(PokemonInstance defender, String effectId) {
        if (defender == null || effectId == null) return false;
        if (!has(defender, "aromaveil")) return false;
        String id = effectId.toLowerCase(java.util.Locale.ROOT);
        return "disable".equals(id) || "encore".equals(id) || "taunt".equals(id) || "torment".equals(id) || "heal_block".equals(id);
    }

    public static boolean preventsStatDrops(PokemonInstance target) {
        if (target == null) return false;
        if (has(target, "clearbody") || has(target, "whitesmoke") || has(target, "fullmetalbody")) return true;
        Species sp = currentSpeciesFor(target);
        return has(target, "flowerveil") && hasType(sp, "grass");
    }

    public static Species currentSpeciesFor(PokemonInstance p) {
        if (p == null || PokeDemoPlugin.INSTANCE == null) return null;
        try { return PokeDemoPlugin.INSTANCE.getDex().getSpecies(p.effectiveSpeciesId()); } catch (Exception e) { return null; }
    }

    public static boolean contactBlockedByLongReach(PokemonInstance attacker) {
        return attacker != null && has(attacker, "longreach");
    }

    public static boolean preventsPhazing(PokemonInstance target) {
        return target != null && has(target, "suctioncups");
    }

    public static boolean isItemSuppressedByKlutz(PokemonInstance mon) {
        if (mon == null) return false;
        if (!has(mon, "klutz")) return false;
        String held = mon.heldItemId == null ? "" : mon.heldItemId.toLowerCase(java.util.Locale.ROOT);
        return !"iron_ball".equals(held);
    }

    public static boolean alwaysMovesLast(PokemonInstance mon, Move move) {
        if (mon == null || move == null) return false;
        if (has(mon, "stall")) return true;
        return has(mon, "myceliummight") && "status".equalsIgnoreCase(move.category());
    }

    public static boolean quickDrawTriggers(PokemonInstance mon, Move move) {
        if (mon == null || move == null) return false;
        if (!has(mon, "quickdraw")) return false;
        if (!"physical".equalsIgnoreCase(move.category()) && !"special".equalsIgnoreCase(move.category())) return false;
        return Util.RND.nextDouble() < 0.30;
    }

    public static boolean blocksPriorityMove(PokemonInstance defender, Move move) {
        if (defender == null || move == null) return false;
        if (move.priority() <= 0) return false;
        return has(defender, "armortail") || has(defender, "dazzling") || has(defender, "queenlymajesty");
    }

    public static boolean blocksStatusMove(PokemonInstance defender, Move move, PokemonInstance attacker) {
        if (defender == null || move == null) return false;
        if (!"status".equalsIgnoreCase(move.category())) return false;
        if (attacker == null || attacker == defender) return false;
        if (ignoresDefenderAbility(attacker)) return false;
        return has(defender, "goodasgold");
    }

    private static void maybeInflict(PokemonInstance tgt, String tgtName, String status, List<String> out) {
        if (tgt.status != null && !"none".equalsIgnoreCase(tgt.status)) return;
        if (isStatusImmune(tgt, status)) return;
        tgt.status = status;
        if ("sleep".equalsIgnoreCase(status)) tgt.sleepTurns = 2;
        LangManager lang28 = lang();
        out.add(lang28==null?("§6【特性】§e"+tgtName+" 陷入了 "+status+" 状态！"):lang28.uiFmt("battle.log.ability.inflict_status","§6【特性】§e{tgt} 陷入了 {status} 状态！", java.util.Map.of("tgt", tgtName, "status", status)));
    }

    private static String abilityDisplay(String norm) {
        // norm is already normalized ability id.
        return displayName(norm);
    }

    /** Result for immunity processing. */
    public static final class AbilityImmunityResult {
        public enum Kind { NONE, IMMUNE, HEAL, BOOST, FLASH_FIRE }
        public final Kind kind;
        public final String message;
        public final String boostStat;
        public final int boostStages;

        private AbilityImmunityResult(Kind kind, String message, String boostStat, int boostStages) {
            this.kind = kind;
            this.message = message;
            this.boostStat = boostStat;
            this.boostStages = boostStages;
        }

        public static AbilityImmunityResult none() { return new AbilityImmunityResult(Kind.NONE, null, null, 0); }
        public static AbilityImmunityResult immune(String msg) { return new AbilityImmunityResult(Kind.IMMUNE, msg, null, 0); }
        public static AbilityImmunityResult heal(String msg) { return new AbilityImmunityResult(Kind.HEAL, msg, null, 0); }
        public static AbilityImmunityResult boost(String msg, String stat, int stages) { return new AbilityImmunityResult(Kind.BOOST, msg, stat, stages); }
        public static AbilityImmunityResult boostOrHeal(String msg, Kind kind) { return new AbilityImmunityResult(kind, msg, null, 0); }
    }
}