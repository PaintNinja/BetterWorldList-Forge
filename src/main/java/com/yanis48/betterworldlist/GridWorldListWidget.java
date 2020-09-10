package com.yanis48.betterworldlist;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Supplier;

import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.Validate;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.google.common.hash.Hashing;
import com.mojang.blaze3d.systems.RenderSystem;
import com.yanis48.betterworldlist.mixin.EntryListWidgetAccessor;
import com.yanis48.betterworldlist.mixin.SelectWorldScreenAccessor;

import net.minecraftforge.api.distmarker.Dist; // import net.fabricmc.api.EnvType;
import net.minecraftforge.api.distmarker.OnlyIn; // import net.fabricmc.api.Environment;

import net.minecraft.util.SharedConstants; // import net.minecraft.SharedConstants;
import net.minecraft.client.Minecraft; // import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.FontRenderer; // import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.AbstractGui; // import net.minecraft.client.gui.DrawableHelper;
import net.minecraft.client.gui.screen.ConfirmBackupScreen; // import net.minecraft.client.gui.screen.BackupPromptScreen;
import net.minecraft.client.gui.screen.ConfirmScreen;
import net.minecraft.client.gui.screen.ErrorScreen; // import net.minecraft.client.gui.screen.FatalErrorScreen;
import net.minecraft.client.gui.screen.AlertScreen; // import net.minecraft.client.gui.screen.AlertScreen;
import net.minecraft.client.gui.screen.WorkingScreen; // import net.minecraft.client.gui.screen.ProgressScreen;
import net.minecraft.client.gui.screen.DirtMessageScreen; // import net.minecraft.client.gui.screen.SaveLevelScreen;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.DialogTexts; // import net.minecraft.client.gui.screen.ScreenTexts;
import net.minecraft.client.gui.screen.CreateWorldScreen; // import net.minecraft.client.gui.screen.world.CreateWorldScreen;
import net.minecraft.client.gui.screen.EditWorldScreen; // import net.minecraft.client.gui.screen.world.EditWorldScreen;
import net.minecraft.client.gui.screen.WorldSelectionScreen; // import net.minecraft.client.gui.screen.world.SelectWorldScreen;
import net.minecraft.client.gui.widget.list.ExtendedList; // import net.minecraft.client.gui.widget.AlwaysSelectedEntryListWidget;
import net.minecraft.client.gui.widget.list.AbstractList; // import net.minecraft.client.gui.widget.EntryListWidget;
import net.minecraft.client.renderer.BufferBuilder; // import net.minecraft.client.render.BufferBuilder;
import net.minecraft.client.renderer.Tessellator; // import net.minecraft.client.render.Tessellator;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats; // import net.minecraft.client.render.VertexFormats;
import net.minecraft.client.resources.I18n; // import net.minecraft.client.resource.language.I18n;
import net.minecraft.client.audio.SimpleSound; // import net.minecraft.client.sound.PositionedSoundInstance;
import net.minecraft.client.renderer.texture.NativeImage; // import net.minecraft.client.texture.NativeImage;
import net.minecraft.client.renderer.texture.DynamicTexture; // import net.minecraft.client.texture.NativeImageBackedTexture;
import net.minecraft.client.gui.toasts.SystemToast; // import net.minecraft.client.toast.SystemToast;
import net.minecraft.client.gui.chat.NarratorChatListener; // import net.minecraft.client.util.NarratorManager;
import com.mojang.blaze3d.matrix.MatrixStack; // import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.datafix.codec.DatapackCodec; // import net.minecraft.resource.DataPackSettings;
import net.minecraft.util.SoundEvents; // import net.minecraft.sound.SoundEvents;
import net.minecraft.util.text.StringTextComponent; // import net.minecraft.text.LiteralText;
import net.minecraft.util.text.IFormattableTextComponent; // import net.minecraft.text.MutableText;
import net.minecraft.util.text.ITextComponent; // import net.minecraft.text.Text;
import net.minecraft.util.text.TranslationTextComponent; // import net.minecraft.text.TranslatableText;
import net.minecraft.util.text.TextFormatting; // import net.minecraft.util.Formatting;
import net.minecraft.util.ResourceLocation; // import net.minecraft.util.Identifier;
import net.minecraft.util.Util;
import net.minecraft.world.storage.FolderName; // import net.minecraft.util.WorldSavePath;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.registry.DynamicRegistries; // import net.minecraft.util.registry.DynamicRegistryManager;
import net.minecraft.world.gen.settings.DimensionGeneratorSettings; // import net.minecraft.world.gen.GeneratorOptions;
import net.minecraft.world.WorldSettings; // import net.minecraft.world.level.LevelInfo;
import net.minecraft.world.storage.SaveFormat; // import net.minecraft.world.level.storage.LevelStorage;
import net.minecraft.client.AnvilConverterException; // import net.minecraft.world.level.storage.LevelStorageException;
import net.minecraft.world.storage.WorldSummary; // import net.minecraft.world.level.storage.LevelSummary;

@OnlyIn(Dist.CLIENT) // @Environment(EnvType.CLIENT)
public class GridWorldListWidget extends ExtendedList<GridWorldListWidget.Entry> {
	private static final Logger LOGGER = LogManager.getLogger();
	private static final DateFormat DATE_FORMAT = new SimpleDateFormat();
	private static final ResourceLocation UNKNOWN_SERVER_TEXTURE = new ResourceLocation("textures/misc/unknown_server.png");
	private static final ResourceLocation WORLD_SELECTION_TEXTURE = new ResourceLocation("textures/gui/world_selection.png");
	private final WorldSelectionScreen parent;
	private List<WorldSummary> levels;
	private boolean scrolling;

	public GridWorldListWidget(WorldSelectionScreen parent, Minecraft client, int width, int height, int top, int bottom, int itemHeight, Supplier<String> searchFilter, GridWorldListWidget list) {
		super(client, width, height, top, bottom, itemHeight);
		this.parent = parent;

		if (list != null) {
			this.levels = list.levels;
		}

		this.filter(searchFilter, false);
	}

	public void filter(Supplier<String> supplier, boolean load) {
		this.clearEntries();
		SaveFormat levelStorage = this.minecraft.getSaveLoader();
		if (this.levels == null || load) {
			try {
				this.levels = levelStorage.getSaveList();
			} catch (AnvilConverterException e) {
				LOGGER.error("Couldn't load level list", e);
				this.minecraft.displayGuiScreen(new ErrorScreen(new TranslationTextComponent("selectWorld.unable_to_load"), new StringTextComponent(e.getMessage())));
				return;
			}

			Collections.sort(this.levels);
		}

		if (this.levels.isEmpty()) {
			this.minecraft.displayGuiScreen(CreateWorldScreen.func_243425_a((Screen)null));
		} else {
			String string = supplier.get().toLowerCase(Locale.ROOT);
			Iterator<?> var5 = this.levels.iterator();

			while(true) {
				WorldSummary levelSummary;
				do {
					if (!var5.hasNext()) {
						return;
					}

					levelSummary = (WorldSummary)var5.next();
				} while(!levelSummary.getDisplayName().toLowerCase(Locale.ROOT).contains(string) && !levelSummary.getFileName().toLowerCase(Locale.ROOT).contains(string));

				this.addEntry(new GridWorldListWidget.Entry(this, levelSummary, this.minecraft.getSaveLoader()));
			}
		}
	}

	@Override
	protected void renderList(MatrixStack matrices, int x, int y, int mouseX, int mouseY, float delta) {
		int count = this.getItemCount();
		Tessellator tessellator = Tessellator.getInstance();
		BufferBuilder bufferBuilder = tessellator.getBuffer();

		for (int index = 0; index < count; index++) {
			int rowTop = this.getRowTop(index);
			int rowBottom = this.getRowBottom(index);
			if (rowBottom >= this.y0 && rowTop <= this.y1) {
				int q;
				if (index % 2 == 0) {
					q = y + (index / 2) * this.itemHeight + this.headerHeight;
				} else {
					q = y + ((index - 1) / 2) * this.itemHeight + this.headerHeight;
				}
				int height = this.itemHeight - 4;
				GridWorldListWidget.Entry entry = this.getEntry(index);
				int width = this.getRowWidth();
				int v;
				int u;
				if (((EntryListWidgetAccessor) this).getRenderSelection() && this.isSelectedItem(index)) {
					if (index % 2 == 0) {
						v = this.x0 + this.width / 2 - width / 2;
						u = this.x0 + this.width / 2 - 4;
					} else {
						v = this.x0 + this.width / 2 + 4;
						u = this.x0 + this.width / 2 + width / 2;
					}
					RenderSystem.disableTexture();
					float f = this.isFocused() ? 1.0F : 0.5F;
					RenderSystem.color4f(f, f, f, 1.0F);
					bufferBuilder.begin(7, DefaultVertexFormats.POSITION);
					bufferBuilder.pos(v, q + height + 2, 0.0D).endVertex();
					bufferBuilder.pos(u, q + height + 2, 0.0D).endVertex();
					bufferBuilder.pos(u, q - 2, 0.0D).endVertex();
					bufferBuilder.pos(v, q - 2, 0.0D).endVertex();
					tessellator.draw();
					RenderSystem.color4f(0.0F, 0.0F, 0.0F, 1.0F);
					bufferBuilder.begin(7, DefaultVertexFormats.POSITION);
					bufferBuilder.pos(v + 1, q + height + 1, 0.0D).endVertex();
					bufferBuilder.pos(u - 1, q + height + 1, 0.0D).endVertex();
					bufferBuilder.pos(u - 1, q - 1, 0.0D).endVertex();
					bufferBuilder.pos(v + 1, q - 1, 0.0D).endVertex();
					tessellator.draw();
					RenderSystem.enableTexture();
				}

				v = this.getRowLeft(index);
				entry.render(matrices, index, rowTop, v, width, height, mouseX, mouseY, this.isMouseOver(mouseX, mouseY) && Objects.equals(this.getEntryAt(mouseX, mouseY), entry), delta);
			}
		}
	}

	protected int getRowLeft(int index) {
		int rowLeft;
		if (index % 2 == 0) {
			rowLeft = this.x0 + this.width / 2 - this.getRowWidth() / 2 + 2;
		} else {
			rowLeft = this.x0 + this.width / 2 + 6;
		}
		return rowLeft;
	}

	@Override
	protected int getRowTop(int index) {
		int rowTop;
		if (index % 2 == 0) {
			rowTop = this.y0 + 4 - (int) this.getScrollAmount() + (index / 2) * this.itemHeight + this.headerHeight;
		} else {
			rowTop = this.y0 + 4 - (int) this.getScrollAmount() + ((index - 1) / 2) * this.itemHeight + this.headerHeight;
		}
		return rowTop;
	}

	private int getRowBottom(int i) {
		return this.getRowTop(i) + this.itemHeight;
	}

	@Override
	public int getRowWidth() {
		return super.getRowWidth() + 52;
	}

	@Override
	protected int getScrollbarPosition() {
		return super.getScrollbarPosition() + 20;
	}

	@Override
	protected int getMaxPosition() {
		if (this.getItemCount() % 2 == 0) {
			return (this.getItemCount() / 2) * this.itemHeight + this.headerHeight;
		} else {
			return (this.getItemCount() / 2 + 1) * this.itemHeight + this.headerHeight;
		}
	}

	@Override
	public boolean mouseClicked(double mouseX, double mouseY, int button) {
		this.updateScrollingState(mouseX, mouseY, button);
		if (!this.isMouseOver(mouseX, mouseY)) {
			return false;
		} else {
			GridWorldListWidget.Entry entry = this.getEntryAt(mouseX, mouseY);
			if (entry != null) {
				if (entry.mouseClicked(mouseX, mouseY, button)) {
					this.setListener(entry);
					this.setDragging(true);
					return true;
				}
			} else if (button == 0) {
				this.clickedHeader((int)(mouseX - (this.x0 + this.width / 2 - this.getRowWidth() / 2)), (int)(mouseY - this.y0) + (int)this.getScrollAmount() - 4);
				return true;
			}
			return this.scrolling;
		}
	}

	protected final GridWorldListWidget.Entry getEntryAt(double x, double y) {
		int rowMiddle = this.getRowWidth() / 2;
		int middle = this.x0 + this.width / 2;
		int left = middle - rowMiddle;
		int right = middle + rowMiddle;
		int height = MathHelper.floor(y - this.y0) - this.headerHeight + (int) this.getScrollAmount() - 4;
		int n = height / this.itemHeight;

		if (x < this.getScrollbarPosition() && n >= 0 && height >= 0) {
			if (x >= left && x <= middle) {
				return (n + n) < this.getItemCount() ? (GridWorldListWidget.Entry) this.getEventListeners().get(n * 2) : null;
			} else if (x >= middle && x <= right) {
				return (n + n + 1) < this.getItemCount() ? (GridWorldListWidget.Entry) this.getEventListeners().get(n * 2 + 1) : null;
			}
		}
		return null;
	}

	@Override
	protected boolean isFocused() {
		return this.parent.getListener() == this;
	}

	@Override
	public void setSelected(GridWorldListWidget.Entry entry) {
		super.setSelected(entry);
		if (entry != null) {
			WorldSummary levelSummary = entry.level;
			NarratorChatListener.INSTANCE.say((new TranslationTextComponent("narrator.select", new Object[]{new TranslationTextComponent("narrator.select.world", new Object[]{levelSummary.getDisplayName(), new Date(levelSummary.getLastTimePlayed()), levelSummary.isHardcoreModeEnabled() ? new TranslationTextComponent("gameMode.hardcore") : new TranslationTextComponent("gameMode." + levelSummary.getEnumGameType().getName()), levelSummary.getCheatsEnabled() ? new TranslationTextComponent("selectWorld.cheats") : StringTextComponent.EMPTY, levelSummary.func_237313_j_()})})).getString());
		}
	}

	@Override
	protected void moveSelection(AbstractList.Ordering direction) {
		this.func_241572_a_(direction, (entry) -> {
			return !entry.level.func_237315_o_();
		});
	}

	public Optional<GridWorldListWidget.Entry> method_20159() {
		return Optional.ofNullable(this.getSelected());
	}

	public WorldSelectionScreen getParent() {
		return this.parent;
	}

	@OnlyIn(Dist.CLIENT) // @Environment(EnvType.CLIENT)
	public final class Entry extends ExtendedList.AbstractListEntry<GridWorldListWidget.Entry> implements AutoCloseable {
		private final Minecraft minecraft;
		private final WorldSelectionScreen screen;
		private final WorldSummary level;
		private final ResourceLocation iconLocation;
		private File iconFile;
		private final DynamicTexture icon;
		private long time;

		public Entry(GridWorldListWidget levelList, WorldSummary level, SaveFormat levelStorage) {
			this.screen = levelList.getParent();
			this.level = level;
			this.minecraft = Minecraft.getInstance();
			this.iconLocation = new ResourceLocation("worlds/" + Hashing.sha1().hashUnencodedChars(level.getFileName()) + "/icon");
			this.iconFile = level.func_237312_c_();
			if (!this.iconFile.isFile()) {
				this.iconFile = null;
			}

			this.icon = this.getIconTexture();
		}

		@Override
		public boolean isMouseOver(double mouseX, double mouseY) {
			return Objects.equals(GridWorldListWidget.this.getEntryAt(mouseX, mouseY), this);
		}

		@Override
		public void render(MatrixStack matrices, int index, int y, int x, int entryWidth, int entryHeight, int mouseX, int mouseY, boolean hovered, float tickDelta) {
			String displayName = this.level.getDisplayName();
			String name = this.level.getFileName();
			String lastPlayed = "(" + GridWorldListWidget.DATE_FORMAT.format(new Date(this.level.getLastTimePlayed())) + ")";
			ITextComponent gameMode = level.isHardcoreModeEnabled() ? new TranslationTextComponent("gameMode.hardcore").mergeStyle(TextFormatting.DARK_RED) : level.getEnumGameType().getDisplayName();
			ITextComponent cheats = new TranslationTextComponent("selectWorld.cheats").mergeStyle(TextFormatting.LIGHT_PURPLE);
			ITextComponent version = this.getVersionText();

			if (StringUtils.isEmpty(displayName)) {
				displayName = I18n.format("selectWorld.world") + " " + (index + 1);
			}

			RenderSystem.color4f(1.0F, 1.0F, 1.0F, 1.0F);
			this.minecraft.getTextureManager().bindTexture(this.icon != null ? this.iconLocation : GridWorldListWidget.UNKNOWN_SERVER_TEXTURE);
			RenderSystem.enableBlend();
			AbstractGui.blit(matrices, x, y, 0.0F, 0.0F, 128, 64, 128, 64);
			RenderSystem.disableBlend();

			FontRenderer textRenderer = this.minecraft.fontRenderer;

			int x2 = x + 3;
			int y2 = y + 64 + 3;
			textRenderer.func_243248_b(matrices, ITextComponent.func_244388_a(displayName), x2, y2, 16777215);

			y2 += 9;
			textRenderer.func_243248_b(matrices, ITextComponent.func_244388_a(name), x2, y2, 8421504);

			y2 += 9;
			textRenderer.func_243248_b(matrices, ITextComponent.func_244388_a(lastPlayed), x2, y2, 8421504);

			y2 += 9;
			textRenderer.func_243248_b(matrices, gameMode, x2, y2, 16777045);

			if (level.getCheatsEnabled()) {
				y2 += 9;
				textRenderer.func_243248_b(matrices, cheats, x2, y2, 8421504);
			}

			y2 += 9;
			textRenderer.func_243248_b(matrices, version, x2, y2, 8421504);

			if (this.minecraft.gameSettings.touchscreen || hovered) {
				this.minecraft.getTextureManager().bindTexture(GridWorldListWidget.WORLD_SELECTION_TEXTURE);

				int arrowX = mouseX - x;
				int arrowY = mouseY - y;
				// Checks if the mouse hovers the arrow
				boolean arrowHovered = (arrowX > 0) && (arrowX < 32) && (arrowY > 0) && (arrowY < 32);
				// If it does, the arrow texture is blue instead of gray
				int k = arrowHovered ? 32 : 0;

				if (this.level.func_237315_o_()) {
					AbstractGui.blit(matrices, x, y, 96.0F, k, 32, 32, 256, 256);
					if (arrowHovered) {
						ITextComponent tooltipText = new TranslationTextComponent("selectWorld.locked").mergeStyle(TextFormatting.RED);
						this.screen.func_239026_b_(this.minecraft.fontRenderer.func_238425_b_(tooltipText, 175));
					}
				} else if (this.level.markVersionInList()) {
					AbstractGui.blit(matrices, x, y, 32.0F, k, 32, 32, 256, 256);
					if (this.level.askToOpenWorld()) {
						AbstractGui.blit(matrices, x, y, 96.0F, k, 32, 32, 256, 256);
						if (arrowHovered) {
							this.screen.func_239026_b_(Arrays.asList(new TranslationTextComponent("selectWorld.tooltip.fromNewerVersion1").mergeStyle(TextFormatting.RED).func_241878_f(), new TranslationTextComponent("selectWorld.tooltip.fromNewerVersion2").mergeStyle(TextFormatting.RED).func_241878_f()));
						}
					} else if (!SharedConstants.getVersion().isStable()) {
						AbstractGui.blit(matrices, x, y, 64.0F, k, 32, 32, 256, 256);
						if (arrowHovered) {
							this.screen.func_239026_b_(Arrays.asList(new TranslationTextComponent("selectWorld.tooltip.snapshot1").mergeStyle(TextFormatting.GOLD).func_241878_f(), new TranslationTextComponent("selectWorld.tooltip.snapshot2").mergeStyle(TextFormatting.GOLD).func_241878_f()));
						}
					}
				} else {
					AbstractGui.blit(matrices, x, y, 0.0F, k, 32, 32, 256, 256);
				}
			}
		}

		private IFormattableTextComponent getVersionText() {
			IFormattableTextComponent version = level.func_237313_j_();
			IFormattableTextComponent text = new TranslationTextComponent("selectWorld.version").append(ITextComponent.func_244388_a(" "));

			if (level.markVersionInList()) {
				text.append(version.mergeStyle(level.askToOpenWorld() ? TextFormatting.RED : TextFormatting.AQUA, TextFormatting.ITALIC));
			} else {
				text.append(version.mergeStyle(TextFormatting.AQUA));
			}

			return text;
		}

		@Override
		public boolean mouseClicked(double mouseX, double mouseY, int button) {
			if (this.level.func_237315_o_()) {
				return true;
			} else {
				GridWorldListWidget.this.setSelected(this);
				this.screen.func_214324_a(GridWorldListWidget.this.method_20159().isPresent());

				int rowMiddle = GridWorldListWidget.this.getRowWidth() / 2;
				int middle = GridWorldListWidget.this.x0 + GridWorldListWidget.this.width / 2;
				int left = middle - rowMiddle;
				int right = middle + rowMiddle;
				int height = MathHelper.floor(mouseY - GridWorldListWidget.this.y0) - GridWorldListWidget.this.headerHeight + (int) GridWorldListWidget.this.getScrollAmount() - 4;
				int n = height / GridWorldListWidget.this.itemHeight;
				boolean validX = false;
				boolean validY = false;

				if (mouseX >= left && mouseX <= middle) {
					validX = mouseX - GridWorldListWidget.this.getRowLeft(n * 2) <= 32.0D;
					validY = mouseY - GridWorldListWidget.this.getRowTop(n * 2) <= 32.0D;
				} else if (mouseX >= middle && mouseX <= right) {
					validX = mouseX - GridWorldListWidget.this.getRowLeft(n * 2 + 1) <= 32.0D;
					validY = mouseY - GridWorldListWidget.this.getRowTop(n * 2 + 1) <= 32.0D;
				}

				if (validX && validY) {
					this.play();
					return true;
				} else if (Util.milliTime() - this.time < 250L) {
					this.play();
					return true;
				} else {
					this.time = Util.milliTime();
					return false;
				}
			}
		}

		public void play() {
			if (!this.level.func_237315_o_()) {
				if (this.level.func_197731_n()) {
					ITextComponent text = new TranslationTextComponent("selectWorld.backupQuestion");
					ITextComponent text2 = new TranslationTextComponent("selectWorld.backupWarning", new Object[]{this.level.func_237313_j_(), SharedConstants.getVersion().getName()});
					this.minecraft.displayGuiScreen(new ConfirmBackupScreen(this.screen, (bl, bl2) -> {
						if (bl) {
							String string = this.level.getFileName();

							try {
								SaveFormat.LevelSave session = this.minecraft.getSaveLoader().func_237274_c_(string);
								Throwable var5 = null;

								try {
									EditWorldScreen.func_239019_a_(session);
								} catch (Throwable e) {
									var5 = e;
									throw e;
								} finally {
									if (session != null) {
										if (var5 != null) {
											try {
												session.close();
											} catch (Throwable var14) {
												var5.addSuppressed(var14);
											}
										} else {
											session.close();
										}
									}
								}
							} catch (IOException e) {
								SystemToast.func_238535_a_(this.minecraft, string);
								GridWorldListWidget.LOGGER.error("Failed to backup level {}", string, e);
							}
						}

						this.start();
					}, text, text2, false));
				} else if (this.level.askToOpenWorld()) {
					this.minecraft.displayGuiScreen(new ConfirmScreen((bl) -> {
						if (bl) {
							try {
								this.start();
							} catch (Exception e) {
								GridWorldListWidget.LOGGER.error("Failure to open 'future world'", e);
								this.minecraft.displayGuiScreen(new AlertScreen(() -> {
									this.minecraft.displayGuiScreen(this.screen);
								}, new TranslationTextComponent("selectWorld.futureworld.error.title"), new TranslationTextComponent("selectWorld.futureworld.error.text")));
							}
						} else {
							this.minecraft.displayGuiScreen(this.screen);
						}
					}, new TranslationTextComponent("selectWorld.versionQuestion"), new TranslationTextComponent("selectWorld.versionWarning", new Object[]{this.level.func_237313_j_(), new TranslationTextComponent("selectWorld.versionJoinButton"), DialogTexts.field_240633_d_})));
				} else {
					this.start();
				}
			}
		}

		public void delete() {
			this.minecraft.displayGuiScreen(new ConfirmScreen((bl) -> {
				if (bl) {
					this.minecraft.displayGuiScreen(new WorkingScreen());
					SaveFormat levelStorage = this.minecraft.getSaveLoader();
					String string = this.level.getFileName();

					try {
						SaveFormat.LevelSave session = levelStorage.func_237274_c_(string);
						Throwable var5 = null;

						try {
							session.func_237299_g_();
						} catch (Throwable var15) {
							var5 = var15;
							throw var15;
						} finally {
							if (session != null) {
								if (var5 != null) {
									try {
										session.close();
									} catch (Throwable e) {
										var5.addSuppressed(e);
									}
								} else {
									session.close();
								}
							}
						}
					} catch (IOException e) {
						SystemToast.func_238538_b_(this.minecraft, string);
						GridWorldListWidget.LOGGER.error("Failed to delete world {}", string, e);
					}

					GridWorldListWidget.this.filter(() -> {
						return ((SelectWorldScreenAccessor) this.screen).getSearchBox().getText();
					}, true);
				}

				this.minecraft.displayGuiScreen(this.screen);
			}, new TranslationTextComponent("selectWorld.deleteQuestion"), new TranslationTextComponent("selectWorld.deleteWarning", new Object[]{this.level.getDisplayName()}), new TranslationTextComponent("selectWorld.deleteButton"), DialogTexts.field_240633_d_));
		}

		public void edit() {
			String levelName = this.level.getFileName();

			try {
				SaveFormat.LevelSave session = this.minecraft.getSaveLoader().func_237274_c_(levelName);
				this.minecraft.displayGuiScreen(new EditWorldScreen((bl) -> {
					try {
						session.close();
					} catch (IOException e) {
						GridWorldListWidget.LOGGER.error("Failed to unlock level {}", levelName, e);
					}

					if (bl) {
						GridWorldListWidget.this.filter(() -> {
							return ((SelectWorldScreenAccessor) this.screen).getSearchBox().getText();
						}, true);
					}

					this.minecraft.displayGuiScreen(this.screen);
				}, session));
			} catch (IOException e) {
				SystemToast.func_238535_a_(this.minecraft, levelName);
				GridWorldListWidget.LOGGER.error("Failed to access level {}", levelName, e);
				GridWorldListWidget.this.filter(() -> {
					return ((SelectWorldScreenAccessor) this.screen).getSearchBox().getText();
				}, true);
			}
		}

		public void recreate() {
			this.func_241653_f_();
			DynamicRegistries.Impl registryManager = DynamicRegistries.func_239770_b_();

			try {
				SaveFormat.LevelSave session = this.minecraft.getSaveLoader().func_237274_c_(this.level.getFileName());
				Throwable var3 = null;

				try {
					Minecraft.PackManager integratedResourceManager = this.minecraft.func_238189_a_(registryManager, Minecraft::func_238180_a_, Minecraft::func_238181_a_, false, session);
					Throwable var5 = null;
					
					try {
						WorldSettings levelInfo = integratedResourceManager.func_238226_c_().func_230408_H_();
						DatapackCodec dataPackSettings = levelInfo.getDatapackCodec();
						DimensionGeneratorSettings generatorOptions = integratedResourceManager.func_238226_c_().getDimensionGeneratorSettings();
						Path path = CreateWorldScreen.func_238943_a_(session.func_237285_a_(FolderName.field_237251_g_), this.minecraft);
						if (generatorOptions.func_236229_j_()) {
							this.minecraft.displayGuiScreen(new ConfirmScreen((bl) -> {
								this.minecraft.displayGuiScreen((Screen)(bl ? new CreateWorldScreen(this.screen, levelInfo, generatorOptions, path, dataPackSettings, registryManager) : this.screen));
							}, new TranslationTextComponent("selectWorld.recreate.customized.title"), new TranslationTextComponent("selectWorld.recreate.customized.text"), DialogTexts.field_240636_g_, DialogTexts.field_240633_d_));
						} else {
							this.minecraft.displayGuiScreen(new CreateWorldScreen(this.screen, levelInfo, generatorOptions, path, dataPackSettings, registryManager));
						}
					} catch (Throwable e) {
						var5 = e;
						throw e;
					} finally {
						if (integratedResourceManager != null) {
							if (var5 != null) {
								try {
									integratedResourceManager.close();
								} catch (Throwable var31) {
									var5.addSuppressed(var31);
								}
							} else {
								integratedResourceManager.close();
							}
						}
					}
				} catch (Throwable e) {
					var3 = e;
					throw e;
				} finally {
					if (session != null) {
						if (var3 != null) {
							try {
								session.close();
							} catch (Throwable e) {
								var3.addSuppressed(e);
							}
						} else {
							session.close();
						}
					}
				}
			} catch (Exception e) {
				GridWorldListWidget.LOGGER.error("Unable to recreate world", e);
				this.minecraft.displayGuiScreen(new AlertScreen(() -> {
					this.minecraft.displayGuiScreen(this.screen);
				}, new TranslationTextComponent("selectWorld.recreate.error.title"), new TranslationTextComponent("selectWorld.recreate.error.text")));
			}
		}
		
		private void start() {
			this.minecraft.getSoundHandler().play(SimpleSound.master(SoundEvents.UI_BUTTON_CLICK, 1.0F));
			if (this.minecraft.getSaveLoader().canLoadWorld(this.level.getFileName())) {
				this.func_241653_f_();
				this.minecraft.func_238191_a_(this.level.getFileName());
			}
		}
		
		private void func_241653_f_() {
			this.minecraft.func_241562_c_(new DirtMessageScreen(new TranslationTextComponent("selectWorld.data_read")));
		}
		
		private DynamicTexture getIconTexture() {
			boolean validFile = this.iconFile != null && this.iconFile.isFile();
			if (validFile) {
				try {
					InputStream inputStream = new FileInputStream(this.iconFile);
					Throwable var3 = null;
					
					DynamicTexture var6;
					try {
						NativeImage nativeImage = NativeImage.read(inputStream);
						Validate.validState(nativeImage.getWidth() == 64, "Must be 64 pixels wide", new Object[0]);
						Validate.validState(nativeImage.getHeight() == 64, "Must be 64 pixels high", new Object[0]);
						DynamicTexture nativeImageBackedTexture = new DynamicTexture(nativeImage);
						this.minecraft.getTextureManager().loadTexture(this.iconLocation, nativeImageBackedTexture);
						var6 = nativeImageBackedTexture;
					} catch (Throwable var16) {
						var3 = var16;
						throw var16;
					} finally {
						if (inputStream != null) {
							if (var3 != null) {
								try {
									inputStream.close();
								} catch (Throwable var15) {
									var3.addSuppressed(var15);
								}
							} else {
								inputStream.close();
							}
						}
					}
					
					return var6;
				} catch (Throwable var18) {
					GridWorldListWidget.LOGGER.error("Invalid icon for world {}", this.level.getFileName(), var18);
					this.iconFile = null;
					return null;
				}
			} else {
				this.minecraft.getTextureManager().deleteTexture(this.iconLocation);
				return null;
			}
		}
		
		@Override
		public void close() {
			if (this.icon != null) {
				this.icon.close();
			}
		}
	}
}
