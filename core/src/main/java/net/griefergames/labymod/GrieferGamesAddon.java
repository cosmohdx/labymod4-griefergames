package net.griefergames.labymod;

import net.griefergames.labymod.gamemode.GamemodeController;
import net.labymod.api.addon.LabyAddon;
import net.labymod.api.models.addon.annotation.AddonMain;

@AddonMain
public class GrieferGamesAddon extends LabyAddon<GrieferGamesConfiguration> {

  @Override
  protected void enable() {
    this.registerSettingCategory();
    this.registerListener(new GamemodeController(this));
  }

  @Override
  protected Class<? extends GrieferGamesConfiguration> configurationClass() {
    return GrieferGamesConfiguration.class;
  }
}
