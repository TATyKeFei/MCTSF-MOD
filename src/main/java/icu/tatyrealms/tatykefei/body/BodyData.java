package icu.tatyrealms.tatykefei.body;

import com.google.gson.JsonElement;
import icu.tatyrealms.tatykefei.compat.needsofnature.NatureTank;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** 本体、其他肉体和离体形态共用的数据结构，字段沿用旧存档名称。 */
public class BodyData {
    public boolean playerDataInitialized;
    public float health = 20;
    public int food = 20;
    public float saturation = 5;
    public float experienceProgress;
    public int experienceLevel;
    public int totalExperience;
    public int enchantmentSeed;
    public int selectedSlot;
    public List<JsonElement> inventory = new ArrayList<>();
    public Map<String, JsonElement> accessories = new HashMap<>();
    public JsonElement foodData;
    public JsonElement attributes;
    public JsonElement effects;
    public float absorption;
    public int air = 300;
    public int fireTicks;
    public int frozenTicks;
    public int hurtTime;
    public int damageCooldownTime;
    public int takeXpDelay;
    public double fallDistance;
    public Map<String, Integer> itemCooldowns = new HashMap<>();
    public NatureTank natureTank;

    public static BodyData detached() {
        BodyData data = new BodyData();
        data.health = 8;
        return data;
    }
}
