package com.penikq.invcheck;

import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.registry.Registries;
import net.minecraft.util.Identifier;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class Checker {
    public static final int GREEN = 0xFF55E67A;
    public static final int ORANGE = 0xFFFFA93A;
    public static final int RED = 0xFFFF5A5A;

    public static class Row {
        public final boolean header;
        public final ItemStack icon;
        public final String name;
        public final String info;
        public final String note;
        public final int level; // 0 ok, 1 warn, 2 bad, 3 extra

        public Row(boolean header, ItemStack icon, String name, String info, String note, int level) {
            this.header = header;
            this.icon = icon;
            this.name = name;
            this.info = info;
            this.note = note;
            this.level = level;
        }

        public static Row header(String text) {
            return new Row(true, ItemStack.EMPTY, text, "", "", -1);
        }

        public static Row item(ItemStack icon, String name, String info, String note, int level) {
            return new Row(false, icon, name, info, note, level);
        }

        public int color() {
            switch (level) {
                case 0: return GREEN;
                case 2: return RED;
                default: return ORANGE;
            }
        }
    }

    public static class Result {
        public final List<Row> rows = new ArrayList<>();
        public int bad = 0;
        public int warn = 0;
        public int extra = 0;
    }

    public static Result compute(SavedData data, PlayerInventory inv) {
        Result res = new Result();
        int size = inv.size();
        ItemStack[] cur = new ItemStack[size];
        for (int i = 0; i < size; i++) cur[i] = inv.getStack(i);
        boolean[] used = new boolean[size];

        Set<String> savedIds = new HashSet<>();
        List<SavedData.SlotData> dmg = new ArrayList<>();
        Map<String, List<SavedData.SlotData>> stackable = new LinkedHashMap<>();
        for (SavedData.SlotData s : data.slots) {
            savedIds.add(s.id);
            if (s.maxDamage > 0) dmg.add(s);
            else stackable.computeIfAbsent(s.id, k -> new ArrayList<>()).add(s);
        }

        // Броня сверху (шлем, нагрудник, поножи, ботинки), потом остальное
        dmg.sort(Comparator.comparingInt((SavedData.SlotData s) -> (s.slot >= 36 && s.slot <= 39) ? -1000 - s.slot : s.slot));

        // ---- Броня и инструменты (по слотам) ----
        int[] match = new int[dmg.size()];
        Arrays.fill(match, -1);
        for (int k = 0; k < dmg.size(); k++) {
            SavedData.SlotData s = dmg.get(k);
            if (s.slot >= 0 && s.slot < size && !cur[s.slot].isEmpty() && !used[s.slot] && idOf(cur[s.slot]).equals(s.id)) {
                match[k] = s.slot;
                used[s.slot] = true;
            }
        }
        for (int k = 0; k < dmg.size(); k++) {
            if (match[k] >= 0) continue;
            SavedData.SlotData s = dmg.get(k);
            for (int i = 0; i < size; i++) {
                if (!used[i] && !cur[i].isEmpty() && idOf(cur[i]).equals(s.id)) {
                    match[k] = i;
                    used[i] = true;
                    break;
                }
            }
        }

        List<Row> dmgRows = new ArrayList<>();
        for (int k = 0; k < dmg.size(); k++) {
            SavedData.SlotData s = dmg.get(k);
            ItemStack icon = stackFor(s.id);
            String name = nameOf(icon, s.id);
            if (match[k] < 0) {
                dmgRows.add(Row.item(icon, name, "нет", "", 2));
                res.bad++;
                continue;
            }
            ItemStack c = cur[match[k]];
            int max = c.getMaxDamage();
            int pct = max > 0 ? (int) Math.round((max - c.getDamage()) * 100.0 / max) : 100;
            pct = Math.max(0, Math.min(100, pct));
            int level = pct >= data.greenAbovePercent ? 0 : (pct >= data.redBelowPercent ? 1 : 2);
            String note = "";
            if (match[k] != s.slot) {
                note = "не на месте, нужен: " + slotLabel(s.slot);
                if (level == 0) level = 1;
            }
            dmgRows.add(Row.item(icon, name, pct + "%", note, level));
            if (level == 2) res.bad++;
            else if (level == 1) res.warn++;
        }

        // ---- Обычные предметы (по количеству) ----
        Map<String, Integer> have = new HashMap<>();
        for (int i = 0; i < size; i++) {
            if (!cur[i].isEmpty()) have.merge(idOf(cur[i]), cur[i].getCount(), Integer::sum);
        }

        List<Row> itemRows = new ArrayList<>();
        for (Map.Entry<String, List<SavedData.SlotData>> e : stackable.entrySet()) {
            String id = e.getKey();
            int need = 0;
            int inPlace = 0;
            String firstMis = null;
            for (SavedData.SlotData s : e.getValue()) {
                need += s.count;
                ItemStack c = (s.slot >= 0 && s.slot < size) ? cur[s.slot] : ItemStack.EMPTY;
                int here = (!c.isEmpty() && idOf(c).equals(id)) ? c.getCount() : 0;
                inPlace += Math.min(s.count, here);
                if (here < s.count && firstMis == null) firstMis = slotLabel(s.slot);
            }
            int h = have.getOrDefault(id, 0);
            int misplaced = Math.max(0, Math.min(h, need) - inPlace);

            ItemStack icon = stackFor(id);
            String name = nameOf(icon, id);
            String info;
            String note = "";
            int level;
            if (h < need) {
                level = 2;
                info = h + "/" + need + " (-" + (need - h) + ")";
                res.bad++;
            } else if (h > need) {
                level = 1;
                info = h + "/" + need + " (+" + (h - need) + ")";
                res.warn++;
            } else if (misplaced > 0) {
                level = 1;
                info = h + "/" + need;
                res.warn++;
            } else {
                level = 0;
                info = h + "/" + need;
            }
            if (misplaced > 0) {
                note = "не на месте: " + misplaced + " шт., нужен: " + firstMis;
            }
            itemRows.add(Row.item(icon, name, info, note, level));
        }
        itemRows.sort(Comparator.comparingInt((Row r) -> -r.level));

        // ---- Лишнее ----
        Map<String, Integer> extra = new LinkedHashMap<>();
        for (int i = 0; i < size; i++) {
            if (cur[i].isEmpty() || used[i]) continue;
            String id = idOf(cur[i]);
            if (!savedIds.contains(id)) extra.merge(id, cur[i].getCount(), Integer::sum);
        }
        res.extra = extra.size();

        if (!dmgRows.isEmpty()) {
            res.rows.add(Row.header("Броня и инструменты"));
            res.rows.addAll(dmgRows);
        }
        if (!itemRows.isEmpty()) {
            res.rows.add(Row.header("Предметы"));
            res.rows.addAll(itemRows);
        }
        if (!extra.isEmpty()) {
            res.rows.add(Row.header("Лишнее"));
            int shown = 0;
            for (Map.Entry<String, Integer> e : extra.entrySet()) {
                if (shown >= 10) break;
                ItemStack icon = stackFor(e.getKey());
                res.rows.add(Row.item(icon, nameOf(icon, e.getKey()), "x" + e.getValue(), "лишнее", 3));
                shown++;
            }
            if (extra.size() > shown) {
                res.rows.add(Row.header("... и ещё " + (extra.size() - shown)));
            }
        }
        return res;
    }

    public static String idOf(ItemStack st) {
        return Registries.ITEM.getId(st.getItem()).toString();
    }

    public static ItemStack stackFor(String id) {
        Identifier ident = Identifier.tryParse(id);
        if (ident != null && Registries.ITEM.containsId(ident)) {
            Item item = Registries.ITEM.get(ident);
            return new ItemStack(item);
        }
        return new ItemStack(Items.BARRIER);
    }

    public static String nameOf(ItemStack icon, String id) {
        if (icon.isOf(Items.BARRIER) && !id.equals("minecraft:barrier")) return id;
        return icon.getName().getString();
    }

    public static String slotLabel(int slot) {
        if (slot >= 0 && slot <= 8) return "хотбар " + (slot + 1);
        if (slot >= 9 && slot <= 35) return "инвентарь " + (slot - 8);
        switch (slot) {
            case 36: return "ботинки";
            case 37: return "поножи";
            case 38: return "нагрудник";
            case 39: return "шлем";
            case 40: return "левая рука";
            default: return "слот " + slot;
        }
    }
}
