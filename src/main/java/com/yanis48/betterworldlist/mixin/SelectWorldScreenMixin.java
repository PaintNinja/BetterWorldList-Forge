package com.yanis48.betterworldlist.mixin;

import java.util.List;

import net.minecraft.util.IReorderingProcessor;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.yanis48.betterworldlist.GridWorldListWidget;

import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.DialogTexts; // import net.minecraft.client.gui.screen.ScreenTexts;
import net.minecraft.client.gui.screen.CreateWorldScreen; // import net.minecraft.client.gui.screen.world.CreateWorldScreen;
import net.minecraft.client.gui.screen.WorldSelectionScreen; // import net.minecraft.client.gui.screen.world.SelectWorldScreen;
import net.minecraft.client.gui.screen.WorldSelectionList; // import net.minecraft.client.gui.screen.world.WorldListWidget;
import net.minecraft.client.gui.widget.button.Button; // import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.client.gui.widget.button.ImageButton; // import net.minecraft.client.gui.widget.TexturedButtonWidget;
import com.mojang.blaze3d.matrix.MatrixStack; // import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.text.ITextComponent; // import net.minecraft.text.Text;
import net.minecraft.util.text.TranslationTextComponent; // import net.minecraft.text.TranslatableText;
import net.minecraft.util.ResourceLocation; // import net.minecraft.util.Identifier;

@Mixin(WorldSelectionScreen.class)
public class SelectWorldScreenMixin extends Screen {
	private final WorldSelectionScreen selectWorldScreen = (WorldSelectionScreen) (Object) this;
	private static final ResourceLocation GRID_ICON_TEXTURE = new ResourceLocation("better-world-list", "textures/grid.png");
	private static final ResourceLocation HORIZONTAL_ICON_TEXTURE = new ResourceLocation("better-world-list", "textures/horizontal.png");
	private static boolean grid = false;
	@Shadow protected Screen prevScreen;
	@Shadow private List<ITextComponent> worldVersTooltip;
	@Shadow private Button deleteButton;
	@Shadow private Button selectButton;
	@Shadow private Button renameButton;
	@Shadow private Button copyButton;
	@Shadow protected TextFieldWidget searchField;
	@Shadow private WorldSelectionList selectionList;
	private GridWorldListWidget gridLevelList;
	
	protected SelectWorldScreenMixin() {
		super(null);
	}
	
	@Inject(method = "init", at = @At(value = "HEAD"), cancellable = true)
	private void init(CallbackInfo ci) {
		WorldSelectionScreen selectWorldScreen = (WorldSelectionScreen) (Object) this;
		
		this.minecraft.keyboardListener.enableRepeatEvents(true);
		
		// Layout button
		this.addButton(new ImageButton(this.width / 2 - 126, 22, 20, 20, 0, 0, 20, grid ? HORIZONTAL_ICON_TEXTURE : GRID_ICON_TEXTURE, 32, 64, (buttonWidget) -> {
			this.refreshScreen();
		}, null));
		
		// Search box
		this.searchField = new TextFieldWidget(this.font, this.width / 2 - 100, 22, 200, 20, this.searchField, new TranslationTextComponent("selectWorld.search"));
		this.searchField.setResponder((string) -> {
			if (grid) {
				this.gridLevelList.filter(() -> {
					return string;
				}, false);
			} else {
				this.selectionList.func_212330_a(() -> {
					return string;
				}, false);
			}
		});
		this.children.add(this.searchField);

		// World List widget
		if (grid) {
			this.gridLevelList = new GridWorldListWidget(selectWorldScreen, this.minecraft, this.width, this.height, 48, this.height - 64, 128, () -> {
				return this.searchField.getText();
			}, this.gridLevelList);
			this.children.add(this.gridLevelList);
		} else {
			this.selectionList = new WorldSelectionList(selectWorldScreen, this.minecraft, this.width, this.height, 48, this.height - 64, 36, () -> {
				return this.searchField.getText();
			}, this.selectionList);
			this.children.add(this.selectionList);
		}

		// Play Selected World button
		this.selectButton = this.addButton(new Button(this.width / 2 - 154, this.height - 52, 150, 20, new TranslationTextComponent("selectWorld.select"), (buttonWidget) -> {
			if (grid) {
				this.gridLevelList.method_20159().ifPresent(GridWorldListWidget.Entry::play);
			} else {
				this.selectionList.func_214376_a().ifPresent(WorldSelectionList.Entry::func_214438_a);
			}
		}));

		// Create New World button
		this.addButton(new Button(this.width / 2 + 4, this.height - 52, 150, 20, new TranslationTextComponent("selectWorld.create"), (buttonWidget) -> {
			this.minecraft.displayGuiScreen(CreateWorldScreen.func_243425_a(this));
		}));

		// Edit button
		this.renameButton = this.addButton(new Button(this.width / 2 - 154, this.height - 28, 72, 20, new TranslationTextComponent("selectWorld.edit"), (buttonWidget) -> {
			if (grid) {
				this.gridLevelList.method_20159().ifPresent(GridWorldListWidget.Entry::edit);
			} else {
				this.selectionList.func_214376_a().ifPresent(WorldSelectionList.Entry::func_214444_c);
			}
		}));

		// Delete button
		this.deleteButton = this.addButton(new Button(this.width / 2 - 76, this.height - 28, 72, 20, new TranslationTextComponent("selectWorld.delete"), (buttonWidget) -> {
			if (grid) {
				this.gridLevelList.method_20159().ifPresent(GridWorldListWidget.Entry::delete);
			} else {
				this.selectionList.func_214376_a().ifPresent(WorldSelectionList.Entry::func_214442_b);
			}
		}));

		// Recreate button
		this.copyButton = this.addButton(new Button(this.width / 2 + 4, this.height - 28, 72, 20, new TranslationTextComponent("selectWorld.recreate"), (buttonWidget) -> {
			if (grid) {
				this.gridLevelList.method_20159().ifPresent(GridWorldListWidget.Entry::recreate);
			} else {
				this.selectionList.func_214376_a().ifPresent(WorldSelectionList.Entry::func_214445_d);
			}
		}));

		// Cancel button
		this.addButton(new Button(this.width / 2 + 82, this.height - 28, 72, 20, DialogTexts.field_240633_d_, (buttonWidget) -> {
			this.minecraft.displayGuiScreen(this.prevScreen);
		}));

		selectWorldScreen.func_214324_a(false);
		this.setFocusedDefault(this.searchField);

		ci.cancel();
	}

	private void refreshScreen() {
		grid = !grid;
		this.minecraft.displayGuiScreen(this.prevScreen);
		this.minecraft.displayGuiScreen(selectWorldScreen);
	}

	@Inject(method = "render", at = @At(value = "HEAD"), cancellable = true)
	private void render(MatrixStack matrices, int mouseX, int mouseY, float delta, CallbackInfo ci) {
		this.worldVersTooltip = null;
		ITextComponent gridLayout = new TranslationTextComponent("better-world-list.layout.grid");
		ITextComponent horizontalLayout = new TranslationTextComponent("better-world-list.layout.horizontal");

		if (grid) {
			this.gridLevelList.render(matrices, mouseX, mouseY, delta);
		} else {
			this.selectionList.render(matrices, mouseX, mouseY, delta);
		}

		this.searchField.render(matrices, mouseX, mouseY, delta);
		drawCenteredString(matrices, this.font, this.title, this.width / 2, 8, 16777215);
		super.render(matrices, mouseX, mouseY, delta);

		/* Todo: fix this in the forge port
		if (this.worldVersTooltip != null) {
			this.renderTooltip(matrices, this.worldVersTooltip, mouseX, mouseY);
		} */
		
		int x = this.width / 2 - 126;
		int y = 22;
		if ((mouseX >= x) && (mouseX <= x + 19) && (mouseY >= y) && (mouseY <= y + 19)) {
			this.renderTooltip(matrices, grid ? horizontalLayout : gridLayout, mouseX, mouseY);
		}
		
		ci.cancel();
	}
	
	@Inject(method = "onClose", at = @At(value = "HEAD"), cancellable = true)
	private void removed(CallbackInfo ci) {
		if (grid) {
			if (this.gridLevelList != null) {
				this.gridLevelList.getEventListeners().forEach(GridWorldListWidget.Entry::close);
			}
		} else {
			if (this.selectionList != null) {
				this.selectionList.getEventListeners().forEach(WorldSelectionList.Entry::close);
			}
		}
		
		ci.cancel();
	}
}
