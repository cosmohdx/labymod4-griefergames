package net.griefergames.labymod;

import net.griefergames.labymod.gamemode.SubServerDisplayType;
import net.labymod.api.addon.AddonConfig;
import net.labymod.api.client.gui.screen.widget.widgets.input.SwitchWidget.SwitchSetting;
import net.labymod.api.client.gui.screen.widget.widgets.input.dropdown.DropdownWidget.DropdownSetting;
import net.labymod.api.configuration.loader.annotation.ConfigName;
import net.labymod.api.configuration.loader.property.ConfigProperty;
import net.labymod.api.configuration.settings.annotation.SettingSection;

@ConfigName("settings")
public class GrieferGamesConfiguration extends AddonConfig {

  @SwitchSetting
  private final ConfigProperty<Boolean> enabled = new ConfigProperty<>(true);

  @SettingSection("labymodstatus")
  @DropdownSetting
  public final ConfigProperty<SubServerDisplayType> enableSubServerDisplay = new ConfigProperty<>(SubServerDisplayType.ONLY_TYPE);

  @SwitchSetting
  public final ConfigProperty<Boolean> enableMinigameDisplay = new ConfigProperty<>(true);

  @SwitchSetting
  public final ConfigProperty<Boolean> updateDiscordStatus = new ConfigProperty<>(true);

  @Override
  public ConfigProperty<Boolean> enabled() {
    return this.enabled;
  }

}
