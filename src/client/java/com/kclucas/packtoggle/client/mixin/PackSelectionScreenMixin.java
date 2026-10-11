package com.kclucas.packtoggle.client.mixin;

import com.kclucas.packtoggle.client.PackManager;
import com.kclucas.packtoggle.client.PackToggleClient;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.layouts.HeaderAndFooterLayout;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.packs.PackSelectionModel;
import net.minecraft.client.gui.screens.packs.PackSelectionScreen;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.ArrayList;
import java.util.List;

/**
 * Fügt dem Ressourcenpaket-Menü eine Knopfreihe hinzu: Speichern, Entfernen, Hinzufügen.
 * Die Knöpfe verschieben die Pakete nur zwischen den beiden Listen – übernommen wird wie gewohnt mit „Fertig".
 */
@Mixin(PackSelectionScreen.class)
public abstract class PackSelectionScreenMixin extends Screen {

	/** Fußzeile mit Platz für zwei Knopfreihen statt einer. */
	@Unique
	private static final int FOOTER_HEIGHT = 58;
	@Unique
	private static final int BUTTON_WIDTH = 100;
	@Unique
	private static final int BUTTON_GAP = 4;

	@Shadow
	@Final
	private HeaderAndFooterLayout layout;
	@Shadow
	@Final
	private PackSelectionModel model;

	@Unique
	private Button packtoggle$saveButton;
	@Unique
	private Button packtoggle$removeButton;
	@Unique
	private Button packtoggle$restoreButton;

	protected PackSelectionScreenMixin(Component title) {
		super(title);
	}

	@Inject(method = "init", at = @At("HEAD"))
	private void packtoggle$growFooter(CallbackInfo ci) {
		this.layout.setFooterHeight(FOOTER_HEIGHT);
	}

	@Inject(method = "init", at = @At("RETURN"))
	private void packtoggle$addButtons(CallbackInfo ci) {
		this.packtoggle$saveButton = addRenderableWidget(Button.builder(
						Component.translatable("packtoggle.button.save"), button -> packtoggle$save())
				.width(BUTTON_WIDTH)
				.tooltip(Tooltip.create(Component.translatable("packtoggle.button.save.tooltip")))
				.build());
		this.packtoggle$removeButton = addRenderableWidget(Button.builder(
						Component.translatable("packtoggle.button.remove"), button -> packtoggle$remove())
				.width(BUTTON_WIDTH)
				.tooltip(Tooltip.create(Component.translatable("packtoggle.button.remove.tooltip")))
				.build());
		this.packtoggle$restoreButton = addRenderableWidget(Button.builder(
						Component.translatable("packtoggle.button.restore"), button -> packtoggle$restore())
				.width(BUTTON_WIDTH)
				.build());
		packtoggle$updateRestoreButton();
		packtoggle$positionButtons();
	}

	@Inject(method = "repositionElements", at = @At("RETURN"))
	private void packtoggle$afterReposition(CallbackInfo ci) {
		packtoggle$positionButtons();
	}

	/** Eigene Reihe oben in der Fußzeile, die Vanilla-Knöpfe rutschen in die Reihe darunter. */
	@Unique
	private void packtoggle$positionButtons() {
		if (this.packtoggle$saveButton == null) {
			return;
		}
		int footerTop = this.height - FOOTER_HEIGHT;
		for (GuiEventListener child : children()) {
			if (child instanceof Button button && !packtoggle$isOwn(button) && button.getY() >= footerTop) {
				button.setY(this.height - 26);
			}
		}

		int rowWidth = BUTTON_WIDTH * 3 + BUTTON_GAP * 2;
		int x = (this.width - rowWidth) / 2;
		int y = footerTop + 6;
		for (AbstractWidget button : List.of(
				this.packtoggle$saveButton, this.packtoggle$removeButton, this.packtoggle$restoreButton)) {
			button.setX(x);
			button.setY(y);
			x += BUTTON_WIDTH + BUTTON_GAP;
		}
	}

	@Unique
	private boolean packtoggle$isOwn(Button button) {
		return button == this.packtoggle$saveButton || button == this.packtoggle$removeButton
				|| button == this.packtoggle$restoreButton;
	}

	@Unique
	private void packtoggle$updateRestoreButton() {
		boolean hasSaved = !PackToggleClient.getConfig().savedPacks.isEmpty();
		this.packtoggle$restoreButton.active = hasSaved;
		this.packtoggle$restoreButton.setTooltip(Tooltip.create(Component.translatable(
				hasSaved ? "packtoggle.button.restore.tooltip" : "packtoggle.button.restore.none")));
	}

	/** Die Pakete der rechten Liste, die der Spieler selbst herausnehmen darf – von oben nach unten. */
	@Unique
	private List<PackSelectionModel.Entry> packtoggle$removableEntries() {
		return this.model.getSelected()
				.filter(entry -> entry.canUnselect() && !entry.isFixedPosition())
				.toList();
	}

	@Unique
	private void packtoggle$save() {
		// Die Liste im Menü steht von oben nach unten, gespeichert wird von unten nach oben.
		List<String> ids = new ArrayList<>();
		for (PackSelectionModel.Entry entry : packtoggle$removableEntries().reversed()) {
			ids.add(entry.getId());
		}
		if (ids.isEmpty()) {
			PackManager.toast(this.minecraft, Component.translatable("packtoggle.toast.nothing_active"));
			return;
		}
		PackManager.store(ids);
		packtoggle$updateRestoreButton();
		PackManager.toast(this.minecraft, Component.translatable("packtoggle.toast.saved", ids.size()));
	}

	@Unique
	private void packtoggle$remove() {
		packtoggle$removableEntries().forEach(PackSelectionModel.Entry::unselect);
	}

	@Unique
	private void packtoggle$restore() {
		packtoggle$remove();
		// Jedes Paket landet beim Auswählen ganz oben – von unten nach oben eingefügt stimmt am Ende die Reihenfolge.
		int missing = 0;
		for (String id : PackToggleClient.getConfig().savedPacks) {
			PackSelectionModel.Entry entry = this.model.getUnselected()
					.filter(candidate -> candidate.getId().equals(id))
					.findFirst().orElse(null);
			if (entry != null) {
				entry.select();
			} else {
				missing++;
			}
		}
		if (missing > 0) {
			int restored = PackToggleClient.getConfig().savedPacks.size() - missing;
			PackManager.toast(this.minecraft,
					Component.translatable("packtoggle.toast.restored_missing", restored, missing));
		}
	}
}
