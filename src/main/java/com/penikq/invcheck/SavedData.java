package com.penikq.invcheck;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;

import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

public class SavedData {
    public static class SlotData {
        public int slot;
        public String id;
        public int count;
        public int maxDamage;
        public int damage;
    }

    // Пороги прочности (в процентах) можно менять прямо в config/penikqinvcheck.json
    public int greenAbovePercent = 75;
    public int redBelowPercent = 40;
    public List<SlotData> slots = new ArrayList<>();

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static SavedData instance;

    private static Path path() {
        return FabricLoader.getInstance().getConfigDir().resolve("penikqinvcheck.json");
    }

    public static SavedData get() {
        if (instance == null) instance = load();
        return instance;
    }

    private static SavedData load() {
        try {
            Path p = path();
            if (Files.exists(p)) {
                try (Reader r = Files.newBufferedReader(p, StandardCharsets.UTF_8)) {
                    SavedData d = GSON.fromJson(r, SavedData.class);
                    if (d != null) {
                        if (d.slots == null) d.slots = new ArrayList<>();
                        return d;
                    }
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        SavedData d = new SavedData();
        d.write();
        return d;
    }

    public void write() {
        try {
            try (Writer w = Files.newBufferedWriter(path(), StandardCharsets.UTF_8)) {
                GSON.toJson(this, w);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public boolean hasSave() {
        return slots != null && !slots.isEmpty();
    }

    public void saveFrom(PlayerInventory inv) {
        slots = new ArrayList<>();
        for (int i = 0; i < inv.size(); i++) {
            ItemStack st = inv.getStack(i);
            if (st.isEmpty()) continue;
            SlotData s = new SlotData();
            s.slot = i;
            s.id = Registries.ITEM.getId(st.getItem()).toString();
            s.count = st.getCount();
            s.maxDamage = st.getMaxDamage();
            s.damage = st.getDamage();
            slots.add(s);
        }
        write();
    }

    public void clear() {
        slots = new ArrayList<>();
        write();
    }
}
