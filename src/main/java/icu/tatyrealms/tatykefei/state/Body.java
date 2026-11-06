package icu.tatyrealms.tatykefei.state;

import com.google.gson.JsonElement;
import java.util.ArrayList;
import java.util.List;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public final class Body {
    public UUID id;
    public UUID owner;
    public String name;
    public JsonElement profile;
    public UUID occupant;
    public UUID entityId;
    public UUID packedToken;
    public UUID zipperCarrier;
    public boolean skinned;
    public boolean skinPlaced;
    public UUID skinToken;
    public UUID skinDropEntity;
    public long skinRecoveryAt;
    public boolean skinnableHypnotized;
    public boolean skinnableDowned;
    public String dimension;
    public double x;
    public double y;
    public double z;
    public float yaw;
    public float health = 20;
    public int food = 20;
    public float saturation = 5;
    public NatureTank natureTank;
    public int selectedSlot;
    public List<JsonElement> inventory = new ArrayList<>();
    public List<JsonElement> skinLayers = new ArrayList<>();
    public List<UUID> skinOrder = new ArrayList<>();
    public Map<String, JsonElement> accessories = new HashMap<>();
}
