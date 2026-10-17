package com.kclucas.packtoggle.client.onboarding;

import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.util.FormattedCharSequence;
import org.jspecify.annotations.Nullable;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;

/**
 * Erst eine Übersicht mit allen Mods, die etwas zu zeigen haben (bei mehreren kann man auswählen),
 * danach nacheinander: bei neuen Mods die Tour, bei aktualisierten eine Seite "Was ist neu" –
 * mit Zurück, Weiter und einem Überspringen-Knopf.
 */
public class OnboardingScreen extends Screen {

	/** Tour-Bilder liegen als 960x540 (16:9) vor. */
	private static final int IMAGE_TEXTURE_WIDTH = 960;
	private static final int IMAGE_TEXTURE_HEIGHT = 540;
	private static final int ICON_TEXTURE_SIZE = 512;

	private static final int COLOR_DIM = 0xA0080A10;
	private static final int COLOR_ACCENT = 0xFF3CFF5A;
	private static final int COLOR_GOLD = 0xFFFFD75E;
	private static final int COLOR_TEXT = 0xFFFFFFFF;
	private static final int COLOR_MUTED = 0xFFB4BBC6;
	private static final int COLOR_FAINT = 0xFF7A828E;
	private static final int COLOR_CARD = 0xB0141925;
	private static final int COLOR_CARD_HOVER = 0xC8202838;
	private static final int COLOR_BORDER = 0xFF3A465E;

	private static final int CONTENT_WIDTH = 420;
	private static final int MAX_IMAGE_HEIGHT = 270;
	private static final int CARD_HEIGHT = 40;
	private static final int CARD_GAP = 4;
	private static final int BUTTON_Y_OFFSET = 28;

	/** Eine Seite: eine Tour-Seite ({@code page} gesetzt) oder die Neuigkeiten einer Mod ({@code page} leer). */
	private record Step(Onboarding.Pending pending, int modIndex, Onboarding.@Nullable Page page, int pageIndex,
			int pageCount) {
	}

	private final @Nullable Screen parent;
	private final List<Onboarding.Pending> pending;
	private final boolean[] selected;
	private final boolean showWelcome;
	private List<Step> steps = List.of();
	/** -1 ist die Übersicht, sonst der Index in {@link #steps}. */
	private int index;

	private int cardsLeft;
	private int cardsTop;
	private int cardsWidth;

	public OnboardingScreen(@Nullable Screen parent, List<Onboarding.Pending> pending, boolean showWelcome) {
		super(Component.translatable("kclucas.onboarding.title"));
		this.parent = parent;
		this.pending = List.copyOf(pending);
		this.selected = new boolean[this.pending.size()];
		java.util.Arrays.fill(this.selected, true);
		this.showWelcome = showWelcome;
		this.index = showWelcome ? -1 : 0;
		rebuildSteps();
	}

	/** Die aktuelle Seite: -1 ist die Übersicht, danach zählen die Seiten ab 0. */
	public int getStepIndex() {
		return this.index;
	}

	/** Für den Test: Ist die aktuelle Seite eine "Was ist neu"-Seite? */
	public boolean isNewsStep() {
		return this.index >= 0 && this.steps.get(this.index).page() == null;
	}

	private boolean onlyUpdates() {
		return this.pending.stream().allMatch(Onboarding.Pending::isUpdate);
	}

	private void rebuildSteps() {
		List<Step> list = new ArrayList<>();
		int modIndex = 0;
		for (int i = 0; i < this.pending.size(); i++) {
			if (!this.selected[i]) {
				continue;
			}
			Onboarding.Pending entry = this.pending.get(i);
			if (entry.isUpdate()) {
				list.add(new Step(entry, modIndex, null, 0, 1));
			} else {
				List<Onboarding.Page> pages = entry.tutorial().pages();
				for (int p = 0; p < pages.size(); p++) {
					list.add(new Step(entry, modIndex, pages.get(p), p, pages.size()));
				}
			}
			modIndex++;
		}
		this.steps = list;
	}

	private int selectedCount() {
		int count = 0;
		for (boolean value : this.selected) {
			if (value) {
				count++;
			}
		}
		return count;
	}

	// ---- Knöpfe ----

	@Override
	protected void init() {
		int y = this.height - BUTTON_Y_OFFSET;
		// Die Knöpfe stehen bündig unter dem Inhalt, nicht verloren in den Bildschirmecken.
		int margin = Math.max(8, (this.width - CONTENT_WIDTH) / 2);

		// Überspringen steht immer an derselben Stelle unten links und ist farblich abgesetzt.
		Component skipLabel = Component.translatable(
				this.index < 0 || isNewsStep() ? "kclucas.onboarding.skip" : "kclucas.onboarding.skip_tutorial")
				.withStyle(ChatFormatting.YELLOW);
		addRenderableWidget(Button.builder(skipLabel, button -> finish()).bounds(margin, y, 110, 20).build());

		if (this.index < 0) {
			int count = selectedCount();
			Component label;
			if (onlyUpdates()) {
				label = Component.translatable("kclucas.onboarding.show_news");
			} else if (this.pending.size() > 1) {
				label = Component.translatable("kclucas.onboarding.start_count", count);
			} else {
				label = Component.translatable("kclucas.onboarding.start");
			}
			Button start = Button.builder(label, button -> next())
					.bounds(this.width - margin - 150, y, 150, 20).build();
			start.active = count > 0;
			addRenderableWidget(start);
			return;
		}

		Button back = Button.builder(Component.translatable("kclucas.onboarding.back"), button -> back())
				.bounds(this.width - margin - 190, y, 60, 20).build();
		back.active = this.index > 0 || this.showWelcome;
		addRenderableWidget(back);
		addRenderableWidget(Button.builder(nextLabel(), button -> next())
				.bounds(this.width - margin - 126, y, 126, 20).build());
	}

	private Component nextLabel() {
		if (this.index >= this.steps.size() - 1) {
			return CommonComponents.GUI_DONE;
		}
		Step current = this.steps.get(this.index);
		Step following = this.steps.get(this.index + 1);
		if (following.pending() != current.pending()) {
			return Component.translatable("kclucas.onboarding.next_mod", following.pending().tutorial().name());
		}
		return Component.translatable("kclucas.onboarding.next");
	}

	private void next() {
		if (this.index < 0) {
			rebuildSteps();
			if (this.steps.isEmpty()) {
				return;
			}
		}
		if (this.index >= this.steps.size() - 1) {
			finish();
			return;
		}
		this.index++;
		rebuildWidgets();
	}

	private void back() {
		if (this.index > 0 || (this.index == 0 && this.showWelcome)) {
			this.index--;
			rebuildWidgets();
		}
	}

	/** Fertig oder übersprungen: In beiden Fällen gilt die installierte Version jeder Mod als gesehen. */
	private void finish() {
		Onboarding.markSeen(this.pending.stream().map(Onboarding.Pending::tutorial).toList());
		this.minecraft.setScreen(this.parent);
	}

	@Override
	public void onClose() {
		finish();
	}

	@Override
	public boolean keyPressed(KeyEvent event) {
		switch (event.key()) {
			case GLFW.GLFW_KEY_RIGHT, GLFW.GLFW_KEY_ENTER, GLFW.GLFW_KEY_KP_ENTER -> {
				if (this.index >= 0 || selectedCount() > 0) {
					next();
				}
				return true;
			}
			case GLFW.GLFW_KEY_LEFT -> {
				back();
				return true;
			}
			default -> {
				return super.keyPressed(event);
			}
		}
	}

	@Override
	public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
		if (super.mouseClicked(event, doubleClick)) {
			return true;
		}
		// Auf der Übersicht wählt ein Klick auf eine Mod-Karte sie an oder ab.
		if (this.index < 0 && this.pending.size() > 1 && event.button() == 0) {
			int card = cardAt(event.x(), event.y());
			if (card >= 0) {
				this.selected[card] = !this.selected[card];
				rebuildWidgets();
				return true;
			}
		}
		return false;
	}

	private int cardAt(double x, double y) {
		if (x < this.cardsLeft || x >= this.cardsLeft + this.cardsWidth || y < this.cardsTop) {
			return -1;
		}
		int relative = (int) (y - this.cardsTop);
		int slot = relative / (CARD_HEIGHT + CARD_GAP);
		boolean inGap = relative % (CARD_HEIGHT + CARD_GAP) >= CARD_HEIGHT;
		return slot < this.pending.size() && !inGap ? slot : -1;
	}

	// ---- Zeichnen ----

	@Override
	public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
		graphics.fill(0, 0, this.width, this.height, COLOR_DIM);
		super.render(graphics, mouseX, mouseY, partialTick);
		if (this.index < 0) {
			renderWelcome(graphics, mouseX, mouseY);
			return;
		}
		Step step = this.steps.get(this.index);
		renderHeader(graphics, step);
		if (step.page() != null) {
			renderPage(graphics, step.page());
		} else {
			renderNews(graphics, step.pending());
		}
	}

	private void renderWelcome(GuiGraphics graphics, int mouseX, int mouseY) {
		boolean multiple = this.pending.size() > 1;
		boolean updates = onlyUpdates();
		int panelWidth = Math.min(this.width - 16, CONTENT_WIDTH);
		Component heading = Component.translatable(updates
				? "kclucas.onboarding.update.title" : "kclucas.onboarding.welcome.title");
		boolean mixed = !updates && this.pending.stream().anyMatch(Onboarding.Pending::isUpdate);
		Component intro = Component.translatable(updates ? "kclucas.onboarding.update.intro"
				: mixed ? "kclucas.onboarding.welcome.intro_mixed"
				: multiple ? "kclucas.onboarding.welcome.intro_many" : "kclucas.onboarding.welcome.intro_one");
		List<FormattedCharSequence> introLines = this.font.split(intro, panelWidth);

		float headingScale = this.font.width(heading) * 1.5F <= this.width - 24 ? 1.5F : 1.0F;
		int headingHeight = Math.round(9 * headingScale);
		int cardsHeight = this.pending.size() * (CARD_HEIGHT + CARD_GAP) - CARD_GAP;
		int contentHeight = headingHeight + 8 + introLines.size() * 10 + 10 + cardsHeight + (multiple ? 16 : 0);
		int y = Math.max(10, (this.height - BUTTON_Y_OFFSET - 8 - contentHeight) / 2);

		drawScaledCentered(graphics, heading.copy().withStyle(ChatFormatting.BOLD), this.width / 2, y, headingScale, COLOR_GOLD);
		y += headingHeight + 8;
		for (FormattedCharSequence line : introLines) {
			graphics.drawCenteredString(this.font, line, this.width / 2, y, COLOR_MUTED);
			y += 10;
		}
		y += 10;

		this.cardsLeft = (this.width - panelWidth) / 2;
		this.cardsTop = y;
		this.cardsWidth = panelWidth;
		int hovered = multiple ? cardAt(mouseX, mouseY) : -1;
		for (int i = 0; i < this.pending.size(); i++) {
			renderCard(graphics, this.pending.get(i), this.cardsLeft, y, panelWidth, multiple, this.selected[i], hovered == i);
			y += CARD_HEIGHT + CARD_GAP;
		}

		if (multiple) {
			graphics.drawCenteredString(this.font, Component.translatable("kclucas.onboarding.welcome.pick"),
					this.width / 2, y + 4, COLOR_FAINT);
		}
	}

	private void renderCard(GuiGraphics graphics, Onboarding.Pending entry, int x, int y, int width,
			boolean selectable, boolean isSelected, boolean hovered) {
		Onboarding.Tutorial tutorial = entry.tutorial();
		int border = selectable && isSelected ? COLOR_ACCENT : COLOR_BORDER;
		graphics.fill(x - 1, y - 1, x + width + 1, y + CARD_HEIGHT + 1, border);
		graphics.fill(x, y, x + width, y + CARD_HEIGHT, 0xFF0C0F16);
		graphics.fill(x, y, x + width, y + CARD_HEIGHT, hovered ? COLOR_CARD_HOVER : COLOR_CARD);

		drawIcon(graphics, tutorial.icon(), x + 4, y + 4, 32);

		int textLeft = x + 44;
		int textWidth = width - 44 - (selectable ? 28 : 8);
		int nameColor = !selectable || isSelected ? COLOR_TEXT : COLOR_FAINT;
		graphics.drawString(this.font, tutorial.name().copy().withStyle(ChatFormatting.BOLD), textLeft, y + 6, nameColor, true);
		if (entry.isUpdate()) {
			// Bei Updates statt der Beschreibung: von welcher auf welche Version.
			Component update = Component.translatable("kclucas.onboarding.updated_to", tutorial.modVersion());
			graphics.drawString(this.font, update, textLeft, y + 18, COLOR_GOLD, false);
		} else {
			List<FormattedCharSequence> lines = this.font.split(tutorial.description(), textWidth);
			for (int i = 0; i < Math.min(2, lines.size()); i++) {
				graphics.drawString(this.font, lines.get(i), textLeft, y + 18 + i * 9, COLOR_MUTED, false);
			}
		}

		if (selectable) {
			int boxX = x + width - 20;
			int boxY = y + (CARD_HEIGHT - 12) / 2;
			graphics.fill(boxX, boxY, boxX + 12, boxY + 12, isSelected ? COLOR_ACCENT : COLOR_BORDER);
			graphics.fill(boxX + 1, boxY + 1, boxX + 11, boxY + 11, 0xFF0C0F16);
			if (isSelected) {
				drawCheckMark(graphics, boxX + 2, boxY + 3);
			}
		}
	}

	private static final String[] CHECK_MARK = {
			".......#",
			"......##",
			"#....##.",
			"##..##..",
			".####...",
			"..##....",
	};

	private static void drawCheckMark(GuiGraphics graphics, int x, int y) {
		for (int row = 0; row < CHECK_MARK.length; row++) {
			for (int col = 0; col < CHECK_MARK[row].length(); col++) {
				if (CHECK_MARK[row].charAt(col) == '#') {
					graphics.fill(x + col, y + row, x + col + 1, y + row + 1, COLOR_ACCENT);
				}
			}
		}
	}

	/** Kopfzeile: links die Mod, rechts wo man gerade ist, darunter ein Fortschrittsbalken. */
	private void renderHeader(GuiGraphics graphics, Step step) {
		Onboarding.Tutorial tutorial = step.pending().tutorial();
		int panelWidth = Math.min(this.width - 16, CONTENT_WIDTH);
		int left = (this.width - panelWidth) / 2;
		int right = left + panelWidth;

		drawIcon(graphics, tutorial.icon(), left, 8, 16);
		graphics.drawString(this.font, tutorial.name().copy().withStyle(ChatFormatting.BOLD), left + 22, 12, COLOR_TEXT, true);
		Component position = step.page() == null
				? Component.translatable("kclucas.onboarding.news")
				: Component.translatable("kclucas.onboarding.step", step.pageIndex() + 1, step.pageCount());
		if (selectedCount() > 1 && this.pending.size() > 1) {
			position = Component.translatable("kclucas.onboarding.mod_and", step.modIndex() + 1, selectedCount(), position);
		}
		graphics.drawString(this.font, position, right - this.font.width(position), 12, COLOR_FAINT, false);
		graphics.fill(left, 28, right, 29, COLOR_BORDER);
		graphics.fill(left, 28, left + panelWidth * (step.pageIndex() + 1) / step.pageCount(), 29, COLOR_ACCENT);
	}

	private void renderPage(GuiGraphics graphics, Onboarding.Page page) {
		int panelWidth = Math.min(this.width - 16, CONTENT_WIDTH);
		int textWidth = Math.min(panelWidth, 380);
		List<FormattedCharSequence> lines = this.font.split(page.text(), textWidth);
		int shownLines = Math.min(lines.size(), 5);
		int textBlock = 14 + shownLines * 10;

		int top = 36;
		int bottom = this.height - BUTTON_Y_OFFSET - 8;
		int y;
		if (page.image() != null) {
			int imageHeight = Math.max(40, Math.min(MAX_IMAGE_HEIGHT, bottom - top - textBlock - 12));
			int imageWidth = imageHeight * 16 / 9;
			if (imageWidth > this.width - 32) {
				imageWidth = this.width - 32;
				imageHeight = imageWidth * 9 / 16;
			}
			int used = imageHeight + 12 + textBlock;
			y = top + Math.max(0, (bottom - top - used) / 2);
			int imageX = (this.width - imageWidth) / 2;
			graphics.fill(imageX - 1, y - 1, imageX + imageWidth + 1, y + imageHeight + 1, COLOR_BORDER);
			graphics.blit(RenderPipelines.GUI_TEXTURED, page.image(), imageX, y, 0.0F, 0.0F, imageWidth, imageHeight,
					IMAGE_TEXTURE_WIDTH, IMAGE_TEXTURE_HEIGHT, IMAGE_TEXTURE_WIDTH, IMAGE_TEXTURE_HEIGHT);
			y += imageHeight + 12;
		} else {
			y = top + Math.max(0, (bottom - top - textBlock) / 2);
		}

		graphics.drawCenteredString(this.font, page.title().copy().withStyle(ChatFormatting.BOLD), this.width / 2, y, COLOR_ACCENT);
		y += 14;
		for (int i = 0; i < shownLines; i++) {
			graphics.drawCenteredString(this.font, lines.get(i), this.width / 2, y, COLOR_TEXT);
			y += 10;
		}
	}

	/** "Was ist neu": großes Icon, Überschrift, darunter je Version die Stichpunkte. */
	private void renderNews(GuiGraphics graphics, Onboarding.Pending entry) {
		int panelWidth = Math.min(this.width - 16, CONTENT_WIDTH);
		int left = (this.width - panelWidth) / 2;
		int textWidth = panelWidth - 24;

		// Erst alles in Zeilen zerlegen, um den Block senkrecht mittig zu setzen.
		record Line(FormattedCharSequence text, int color, int indent, int gapBefore) {
		}
		List<Line> lines = new ArrayList<>();
		boolean several = entry.news().size() > 1;
		for (Onboarding.Release release : entry.news()) {
			if (several) {
				lines.add(new Line(Component.translatable("kclucas.onboarding.version", release.version())
						.withStyle(ChatFormatting.BOLD).getVisualOrderText(), COLOR_GOLD, 0, lines.isEmpty() ? 0 : 6));
			}
			for (Component note : release.notes()) {
				List<FormattedCharSequence> wrapped = this.font.split(note, textWidth - 10);
				for (int i = 0; i < wrapped.size(); i++) {
					lines.add(new Line(wrapped.get(i), COLOR_TEXT, 10, i == 0 ? 3 : 0));
				}
			}
		}

		int iconSize = 48;
		int blockHeight = iconSize + 10 + 14;
		for (Line line : lines) {
			blockHeight += 10 + line.gapBefore();
		}
		int top = 36;
		int bottom = this.height - BUTTON_Y_OFFSET - 8;
		int y = top + Math.max(0, (bottom - top - blockHeight) / 2);

		drawIcon(graphics, entry.tutorial().icon(), (this.width - iconSize) / 2, y, iconSize);
		y += iconSize + 10;
		Component title = several
				? Component.translatable("kclucas.onboarding.news_since", entry.seenVersion())
				: Component.translatable("kclucas.onboarding.news_title", entry.news().getFirst().version());
		graphics.drawCenteredString(this.font, title.copy().withStyle(ChatFormatting.BOLD), this.width / 2, y, COLOR_ACCENT);
		y += 14;

		for (Line line : lines) {
			y += line.gapBefore();
			int x = left + 12 + line.indent();
			if (line.indent() > 0 && line.gapBefore() > 0) {
				graphics.fill(x - 7, y + 3, x - 4, y + 6, COLOR_ACCENT);
			}
			graphics.drawString(this.font, line.text(), x, y, line.color(), false);
			y += 10;
		}
	}

	private void drawIcon(GuiGraphics graphics, Identifier icon, int x, int y, int size) {
		graphics.blit(RenderPipelines.GUI_TEXTURED, icon, x, y, 0.0F, 0.0F, size, size,
				ICON_TEXTURE_SIZE, ICON_TEXTURE_SIZE, ICON_TEXTURE_SIZE, ICON_TEXTURE_SIZE);
	}

	private void drawScaledCentered(GuiGraphics graphics, Component text, int centerX, int y, float scale, int color) {
		graphics.pose().pushMatrix();
		graphics.pose().translate(centerX, y);
		graphics.pose().scale(scale, scale);
		graphics.drawCenteredString(this.font, text, 0, 0, color);
		graphics.pose().popMatrix();
	}
}
