package net.griefergames.labymod.gamemode;

import net.griefergames.labymod.GrieferGamesAddon;
import net.labymod.api.client.chat.ChatMessage;
import net.labymod.api.client.chat.ChatTrustLevel;
import net.labymod.api.client.component.Component;
import net.labymod.api.client.component.TextComponent;
import net.labymod.api.client.gui.icon.Icon;
import net.labymod.api.client.network.server.ServerData;
import net.labymod.api.client.network.server.global.PublicServerData;
import net.labymod.api.client.network.server.storage.ServerResourcePackStatus;
import net.labymod.api.client.options.ChatVisibility;
import net.labymod.api.client.scoreboard.TabList;
import net.labymod.api.event.Subscribe;
import net.labymod.api.event.client.network.server.ServerDisconnectEvent;
import net.labymod.api.event.client.network.server.ServerJoinEvent;
import net.labymod.api.event.client.network.server.ServerSwitchEvent;
import net.labymod.api.event.client.network.server.SubServerSwitchEvent;
import net.labymod.api.event.client.scoreboard.ScoreboardTeamUpdateEvent;
import net.labymod.api.event.client.scoreboard.TabListUpdateEvent;
import net.labymod.api.notification.Notification;
import net.labymod.api.notification.Notification.Type;
import net.labymod.api.thirdparty.discord.DiscordActivity;
import net.labymod.api.util.I18n;
import net.labymod.core.labyconnect.DefaultLabyConnect;
import net.labymod.core.labyconnect.protocol.packets.PacketPlayServerStatusUpdate;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import java.util.Arrays;
import java.util.List;
import java.util.Timer;
import java.util.TimerTask;
import java.util.regex.Pattern;

public class GamemodeController {

  private static final Pattern STRIP_COLOR_PATTERN = Pattern.compile("(?i)" + String.valueOf('§') + "[0-9A-FK-OR]");

  private final List<String> oldConnectionAddresses = List.of(
      "116.griefergames.net",
      "119.griefergames.net"
  );
  private final String cloudConnectionAddress = "cloud.griefergames.net";

  private final GrieferGamesAddon addon;

  private boolean isOnCloud = false;
  private String currentRegion = null;
  private CloudRegionType currentRegionType = null;
  private String lastSentStatusSubName = null;

  public GamemodeController(GrieferGamesAddon addon) {
    this.addon = addon;
  }

  @Subscribe
  public void on(ServerJoinEvent event) {
    isOnCloud = isConnectionDirectCloud(event.serverData());
    if(isOnCloud) {
      if(this.addon.configuration().enableSubServerDisplay.getOrDefault() == SubServerDisplayType.NONE) {
        new Timer().schedule(new TimerTask() {
          @Override
          public void run() {
            updateLabyStatusToPlayingCloud(null);
          }
        }, 2000);
      }
    }else{
      currentRegion = null;
      currentRegionType = null;
    }
  }

  @Subscribe
  public void on(ServerSwitchEvent event) {
    isOnCloud = isConnectionDirectCloud(event.newServerData());
    if(isOnCloud) {
      if(this.addon.configuration().enableSubServerDisplay.getOrDefault() == SubServerDisplayType.NONE) {
        new Timer().schedule(new TimerTask() {
          @Override
          public void run() {
            updateLabyStatusToPlayingCloud(null);
          }
        }, 2000);
      }
    }else{
      currentRegion = null;
      currentRegionType = null;
    }
  }

  @Subscribe
  public void on(ServerDisconnectEvent event) {
    isOnCloud = false;
    currentRegion = null;
    currentRegionType = null;
  }

  @Subscribe
  public void on(SubServerSwitchEvent event) {
    boolean wasOnCloud = isOnCloud;
    isOnCloud = isConnectionDirectCloud(event.serverData());
    if(isOnCloud && !wasOnCloud) {
      if(this.addon.configuration().enableSubServerDisplay.getOrDefault() == SubServerDisplayType.NONE) {
        updateLabyStatusToPlayingCloud(null);
      }
    }else if(wasOnCloud && !isOnCloud) {
      currentRegion = null;
      currentRegionType = null;
    }
  }

  @Subscribe
  public void on(TabListUpdateEvent event) {
    if(!isOnCloud) return; // Only check if the user is at ggcloud
    if(!isAnySubServerUpdateEnabled()) return; // only check if any subserver update is enabled
    TabList tablist = this.addon.labyAPI().minecraft().getTabList();
    if(tablist == null) return;
    try {
      Component tablistHeader = tablist.header();
      if (tablistHeader == null)
        return;
      String serverName = CloudRegionType.extractServerNameFromComponent(tablistHeader);
      if (serverName != null) {
        if (serverName.equals(currentRegion))
          return;
        CloudRegionType regionType = CloudRegionType.getRegionType(serverName);
        if (regionType != null) {
          currentRegion = serverName;
          currentRegionType = regionType;
          switch (this.addon.configuration().enableSubServerDisplay.getOrDefault()) {
            case ONLY_TYPE -> {
              updateLabyStatusToPlayingCloud(
                  I18n.translate("griefergames.region_type." + regionType.name().toLowerCase()));
            }
            case WITH_NAME -> {
              updateLabyStatusToPlayingCloud(
                  I18n.translate("griefergames.region_type.with_name." + regionType.name().toLowerCase(),
                      regionType.onlyName(serverName)
                  )
              );
            }
          }
        } else {
          this.addon.logger().warn("Invalid Region TYPE for " + serverName);
        }
      }
    }catch (Exception ex) {
      ex.printStackTrace();
    }
  }

  @Subscribe
  public void on(ScoreboardTeamUpdateEvent event) {
    if(!isOnCloud) return; // Only check if the user is at ggcloud
    if(!isAnySubServerUpdateEnabled()) return; // only check if any subserver update is enabled
    if(currentRegionType == CloudRegionType.MINIGAME && this.addon.configuration().enableMinigameDisplay.getOrDefault()) {
      if(event.team().getTeamName().trim().equalsIgnoreCase("money_value")) {
        String minigameName = getNameFromTeamComponent(event.team().getPrefix());
        if(minigameName != null) {
          updateLabyStatusToPlayingCloud("§6"+minigameName);
        }
      }
    }
    if(currentRegionType == CloudRegionType.EVENT) {
      if(event.team().getTeamName().trim().equalsIgnoreCase("money_value")) {
        String eventName = getNameFromTeamComponent(event.team().getPrefix());
        if(eventName != null) {
          updateLabyStatusToPlayingCloud("§a"+eventName);
        }
      }
    }
  }

  /**
   * Fetches the name from the team component of the scorebaord
   * @param component Component
   * @return name
   */
  private String getNameFromTeamComponent(Component component) {
    if(component.getChildren().size() > 0) {
      return getNameFromTeamComponent(component.getChildren().get(0));
    }
    if(component instanceof TextComponent) {
      //@TODO Check if color is possible
      return ((TextComponent) component).getText().trim();
    }
    return null;
  }

  /**
   * Checks if the connected server is a direct connect to the cloud
   * @param serverData ServerData of the connected server
   * @return is Cloud
   */
  private boolean isConnectionDirectCloud(ServerData serverData) {
    // Check old server address of cloud server
    if(serverData.actualAddress().getHost().equalsIgnoreCase(cloudConnectionAddress)) {
      return true;
    }

    // Check for old connection addresses
    if(oldConnectionAddresses.stream().anyMatch(it -> it.equalsIgnoreCase(serverData.actualAddress().getHost()))) {
      // Send old connection address warning
      this.addon.labyAPI().notificationController()
          .push(Notification.builder()
              .title(Component.translatable("griefergames.connection_check.old_address.notification.title"))
              .text(Component.translatable("griefergames.connection_check.old_address.notification.text"))
              .icon(Icon.head(this.addon.labyAPI().getUniqueId()))
              .type(Type.SYSTEM)
              .duration(10000)
            .build());
      this.addon.labyAPI().chatProvider().chatController().addMessage(ChatMessage.builder().component(
          Component.translatable("griefergames.connection_check.old_address.chat_message")
          ).visibility(ChatVisibility.SHOWN).trustLevel(ChatTrustLevel.SECURE).build()
      );
      return true;
    }
    return false;
  }

  /**
   * Checks if any sub server update is enabled in the configuration
   * @return is enabled
   */
  private boolean isAnySubServerUpdateEnabled() {
    return this.addon.configuration().enableSubServerDisplay.getOrDefault() != SubServerDisplayType.NONE ||
      this.addon.configuration().enableMinigameDisplay.getOrDefault();
  }

  /**
   * Updates the LabyMod status to "Playing on GrieferGames Cloud"
   * @param subServerOrGameMode Server or Gamemode to display
   */
  public void updateLabyStatusToPlayingCloud(@Nullable String subServerOrGameMode) {
    if(!this.addon.configuration().enabled().getOrDefault()) return;
    if(lastSentStatusSubName != null && subServerOrGameMode != null && lastSentStatusSubName.equalsIgnoreCase(subServerOrGameMode)) return;
    lastSentStatusSubName = subServerOrGameMode;

    // Update the discord presence status for the given server or gamemode
    if(this.addon.configuration().updateDiscordStatus.getOrDefault()) {
      try {
        if (this.addon.labyAPI().thirdPartyService().discord().isRunning()) {
          this.addon.labyAPI().thirdPartyService().discord().displayActivity(
              DiscordActivity.builder(this)
                  //.start(this.addon.labyAPI().thirdPartyService().discord().getDisplayedActivity().getStartTime())
                  .state(subServerOrGameMode == null ? I18n.getTranslation("griefergames.discord_rpc.state") : stripColor(subServerOrGameMode))
                  .details(I18n.getTranslation("griefergames.discord_rpc.server_name"))
                  .build()
          );
        }
      } catch (Exception ex) {
        ex.printStackTrace();
      }
    }

    if(this.addon.labyAPI().labyConnect().isConnected()) {
      String labyModGameModeString = subServerOrGameMode == null ? "GrieferGames Cloud" : "GrieferGames Cloud: " + subServerOrGameMode;
//      Objects.requireNonNull(this.addon.labyAPI().labyConnect().getSession())
//        .sendCurrentServer(
//          getCloudServerData(),
//            labyModGameModeString,
//          false
//        );
      ServerData serverData = getCloudServerData();
      ((DefaultLabyConnect) this.addon.labyAPI().labyConnect()).sendPacket(
          new PacketPlayServerStatusUpdate(
              serverData.address().getHost(),
              serverData.address().getPort(),
              labyModGameModeString,
              false
          )
      );
    }
  }

  /**
   * Creates a standard Cloud ServerData object
   * @return ServerData
   */
  private ServerData getCloudServerData() {
    return PublicServerData.builder()
        .name("GrieferGames Cloud")
        .address("griefergames.net")
        .resourcePackStatus(ServerResourcePackStatus.ENABLED)
        .lan(false)
        .build();
  }

  private static String stripColor(String input) {
    return input == null ? null : STRIP_COLOR_PATTERN.matcher(input).replaceAll("");
  }

  static enum CloudRegionType {
    CITYBUILD("cb1-"),
    FARM("farm-"),
    JAIL("jail-"),
    MINIGAME("minigame-"),
    EVENT("event-");

    private final String prefix;

    CloudRegionType(String prefix) {
      this.prefix = prefix;
    }

    public String getPrefix() {
      return prefix;
    }

    public String onlyName(String text) {
      return text.replace(prefix, "");
    }

    /**
     * Checks if the string contains a server name prefix
     * @param text Text to check
     * @return contains server prefix
     */
    public static boolean containsServerName(String text) {
      return Arrays.stream(CloudRegionType.values()).anyMatch(it -> text.contains(it.getPrefix()));
    }

    /**
     * Extracts the server name from a component
     * @param component component
     * @return server name or null
     */
    public static String extractServerNameFromComponent(@NotNull Component component) {
      if (component.getChildren().size() > 0) {
        for (Component child : component.getChildren()) {
          String serverName = extractServerNameFromComponent(child);
          if (serverName != null) {
            return serverName;
          }
        }
      } else if (component instanceof TextComponent) {
        String text = ((TextComponent) component).getText();
        if (containsServerName(text)) {
          if(text.contains(" ")) {
            for(String part : text.split(" ")) {
              if(containsServerName(part)) {
                return part;
              }
            }
          }
          return text;
        }
      }
      return null;
    }

    public static CloudRegionType getRegionType(String serverName) {
      return Arrays.stream(CloudRegionType.values()).filter(it -> serverName.startsWith(it.getPrefix())).findFirst().orElse(null);
    }

  }

}
