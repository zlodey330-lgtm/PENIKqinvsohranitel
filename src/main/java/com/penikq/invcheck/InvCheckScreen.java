package com.penikq.invcheck;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.text.Text;

public class InvCheckScreen extends Screen {
    private static final int W = 320;
    private static final int ROW_H = 24;
    private static final int BW = 145;
    private static final int BH = 20;

    private int px, py, ph;
    private Checker.Result result;
    private int scroll = 0;
    private boolean confirmDelete = false;
    private String status = "";
    private int statusTicks = 0;
    private int tickCounter = 0;

    public InvCheckScreen() {
        super(Text.literal("Проверка инвентаря"));
    }

    @Override
    protected void init() {
        ph = Math.min(340, this.height - 20);
        px = (this.width - W) / 2;
        py = (this.height - ph) / 2;
        refresh();
    }

    private void refresh() {
        if (client == null || client.player == null) return;
        SavedData d = SavedData.get();
        result = d.hasSave() ? Checker.compute(d, client.player.getInventory()) : null;
        clampScroll();
    }

    // ---- геометрия ----
    private int leftBtnX() { return px + 10; }
    private int rightBtnX() { return px + 10 + BW + 10; }
    private int btnY() { return py + 24; }
    private int listTop() { return py + 66; }
    private int listBottom() { return py + ph - 8; }
    private int contentHeight() { return result == null ? 0 : result.rows.size() * ROW_H + 4; }

    private void clampScroll() {
        int max = Math.max(0, contentHeight() - (listBottom() - listTop()));
        scroll = Math.max(0, Math.min(scroll, max));
    }

    private boolean inside(double mx, double my, int x, int y, int w, int h) {
        return mx >= x && mx < x + w && my >= y && my < y + h;
    }

    // ---- рисование ----
    @Override
    public void renderBackground(DrawContext ctx, int mouseX, int mouseY, float delta) {
        // без размытия, фон рисуем сами в render()
    }

    private void fillRound(DrawContext c, int x1, int y1, int x2, int y2, int col) {
        c.fill(x1 + 2, y1, x2 - 2, y2, col);
        c.fill(x1 + 1, y1 + 1, x1 + 2, y2 - 1, col);
        c.fill(x2 - 2, y1 + 1, x2 - 1, y2 - 1, col);
        c.fill(x1, y1 + 2, x1 + 1, y2 - 2, col);
        c.fill(x2 - 1, y1 + 2, x2, y2 - 2, col);
    }

    private void drawButton(DrawContext c, int x, int y, String label, boolean enabled, boolean danger, int mx, int my) {
        boolean hover = enabled && inside(mx, my, x, y, BW, BH);
        int border;
        int fill;
        int text;
        if (!enabled) {
            border = 0xFF3B4466;
            fill = 0xFF232B45;
            text = 0xFF7A8299;
        } else if (danger) {
            border = hover ? 0xFFFF8A8A : 0xFFD05A5A;
            fill = hover ? 0xFFB23A3A : 0xFF8E2B2B;
            text = 0xFFFFFFFF;
        } else {
            border = hover ? 0xFF8DB6FF : 0xFF5E94F0;
            fill = hover ? 0xFF2F6BD6 : 0xFF1E4FA8;
            text = 0xFFFFFFFF;
        }
        fillRound(c, x, y, x + BW, y + BH, border);
        fillRound(c, x + 1, y + 1, x + BW - 1, y + BH - 1, fill);
        c.drawCenteredTextWithShadow(textRenderer, label, x + BW / 2, y + (BH - 8) / 2, text);
    }

    @Override
    public void render(DrawContext ctx, int mouseX, int mouseY, float delta) {
        SavedData data = SavedData.get();

        // затемнение, тень и панель
        ctx.fill(0, 0, this.width, this.height, 0x88000510);
        ctx.fill(px + 3, py + 3, px + W + 3, py + ph + 3, 0x66000000);
        ctx.fillGradient(px, py, px + W, py + ph, 0xF00B1A3C, 0xF0122A5C);
        ctx.drawBorder(px, py, W, ph, 0xFF3C6FD1);

        ctx.drawCenteredTextWithShadow(textRenderer, "Проверка инвентаря", px + W / 2, py + 8, 0xFF8EC5FF);

        // кнопки
        if (confirmDelete) {
            drawButton(ctx, leftBtnX(), btnY(), "Да, удалить", true, true, mouseX, mouseY);
            drawButton(ctx, rightBtnX(), btnY(), "Отмена", true, false, mouseX, mouseY);
        } else {
            drawButton(ctx, leftBtnX(), btnY(), "Сохранить инвентарь", true, false, mouseX, mouseY);
            drawButton(ctx, rightBtnX(), btnY(), "Удалить инвентарь", data.hasSave(), false, mouseX, mouseY);
        }

        // строка-итог
        String sum;
        int sumCol;
        if (confirmDelete) {
            sum = "Точно удалить сохранённый инвентарь?";
            sumCol = Checker.ORANGE;
        } else if (statusTicks > 0) {
            sum = status;
            sumCol = Checker.GREEN;
        } else if (result == null) {
            sum = "Сохранения нет";
            sumCol = 0xFF8FA3C8;
        } else if (result.bad == 0 && result.warn == 0) {
            sum = "Всё на месте" + (result.extra > 0 ? ", лишнее: " + result.extra : "");
            sumCol = Checker.GREEN;
        } else {
            sum = "Не хватает/сломано: " + result.bad + " | Проверить: " + result.warn;
            sumCol = result.bad > 0 ? Checker.RED : Checker.ORANGE;
        }
        ctx.drawCenteredTextWithShadow(textRenderer, sum, px + W / 2, py + 50, sumCol);

        // список
        int lx1 = px + 8, ly1 = listTop(), lx2 = px + W - 8, ly2 = listBottom();
        ctx.fill(lx1, ly1, lx2, ly2, 0x55000A1E);

        if (result == null) {
            int cy = ly1 + 40;
            ctx.drawCenteredTextWithShadow(textRenderer, "Здесь пока пусто.", px + W / 2, cy, 0xFFB5C8EE);
            ctx.drawCenteredTextWithShadow(textRenderer, "Надень и собери всё нужное,", px + W / 2, cy + 16, 0xFF8FA3C8);
            ctx.drawCenteredTextWithShadow(textRenderer, "потом нажми «Сохранить инвентарь».", px + W / 2, cy + 28, 0xFF8FA3C8);
        } else {
            ctx.enableScissor(lx1, ly1, lx2, ly2);
            int y = ly1 + 2 - scroll;
            int idx = 0;
            for (Checker.Row r : result.rows) {
                int ry = y;
                y += ROW_H;
                idx++;
                if (ry + ROW_H < ly1 || ry > ly2) continue;
                if (r.header) {
                    ctx.drawTextWithShadow(textRenderer, r.name, lx1 + 6, ry + 8, 0xFF7FA8E8);
                    ctx.fill(lx1 + 6, ry + ROW_H - 3, lx2 - 6, ry + ROW_H - 2, 0x553C6FD1);
                    continue;
                }
                if (idx % 2 == 0) ctx.fill(lx1, ry, lx2, ry + ROW_H, 0x1AFFFFFF);
                int col = r.color();
                ctx.fill(lx1, ry + 2, lx1 + 2, ry + ROW_H - 2, col);
                ctx.drawItem(r.icon, lx1 + 6, ry + 4);

                boolean hasNote = !r.note.isEmpty();
                int infoW = textRenderer.getWidth(r.info);
                String nm = textRenderer.trimToWidth(r.name, (lx2 - lx1) - 30 - infoW - 18);
                int ty = hasNote ? ry + 4 : ry + 8;
                ctx.drawTextWithShadow(textRenderer, nm, lx1 + 28, ty, 0xFFE6EEFF);
                ctx.drawTextWithShadow(textRenderer, r.info, lx2 - 8 - infoW, ty, col);
                if (hasNote) {
                    String note = textRenderer.trimToWidth(r.note, (lx2 - lx1) - 28 - 12);
                    ctx.drawTextWithShadow(textRenderer, note, lx1 + 28, ry + 14, col);
                }
            }
            ctx.disableScissor();

            // полоса прокрутки
            int visible = ly2 - ly1;
            int content = contentHeight();
            if (content > visible) {
                int barH = Math.max(14, visible * visible / content);
                int maxScroll = content - visible;
                int barY = ly1 + (int) ((long) (visible - barH) * scroll / maxScroll);
                ctx.fill(lx2 - 3, ly1, lx2 - 1, ly2, 0x33FFFFFF);
                ctx.fill(lx2 - 3, barY, lx2 - 1, barY + barH, 0xFF5E94F0);
            }
        }

        super.render(ctx, mouseX, mouseY, delta);
    }

    // ---- ввод ----
    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        if (button == 0) {
            SavedData data = SavedData.get();
            boolean left = inside(mx, my, leftBtnX(), btnY(), BW, BH);
            boolean right = inside(mx, my, rightBtnX(), btnY(), BW, BH);

            if (confirmDelete) {
                if (left) {
                    data.clear();
                    confirmDelete = false;
                    status = "Инвентарь удалён";
                    statusTicks = 40;
                    scroll = 0;
                    refresh();
                    return true;
                }
                if (right) {
                    confirmDelete = false;
                    return true;
                }
            } else {
                if (left) {
                    if (client != null && client.player != null) {
                        data.saveFrom(client.player.getInventory());
                        status = data.hasSave() ? "Инвентарь сохранён" : "Инвентарь пуст, нечего сохранять";
                        statusTicks = 40;
                        scroll = 0;
                        refresh();
                    }
                    return true;
                }
                if (right && data.hasSave()) {
                    confirmDelete = true;
                    return true;
                }
            }
        }
        return super.mouseClicked(mx, my, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        scroll -= (int) (verticalAmount * 16);
        clampScroll();
        return true;
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (InvCheckClient.OPEN_KEY != null && InvCheckClient.OPEN_KEY.matchesKey(keyCode, scanCode)) {
            this.close();
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public void tick() {
        tickCounter++;
        if (statusTicks > 0) statusTicks--;
        if (tickCounter % 5 == 0) refresh();
    }
}
