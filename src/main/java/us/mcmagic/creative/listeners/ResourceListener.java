package us.mcmagic.creative.listeners;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import us.mcmagic.mcmagiccore.MCMagicCore;
import us.mcmagic.mcmagiccore.player.User;
import us.mcmagic.mcmagiccore.resource.CurrentPackReceivedEvent;
import us.mcmagic.mcmagiccore.resource.ResourceStatusEvent;

/**
 * Created by Marc on 6/6/15
 */
public class ResourceListener implements Listener {

    @EventHandler
    public void onCurrentPackReceive(CurrentPackReceivedEvent event) {
        User user = event.getUser();
        Player player = Bukkit.getPlayer(user.getUniqueId());
        String current = event.getPacks();
        String preferred = user.getPreferredPack();
        if (preferred.equals("none") || preferred.equals("NoPrefer")) {
            if (!current.equals("none")) {
                MCMagicCore.resourceManager.sendPack(player, "Blank");
            }
            user.setPreferredPack("none");
            MCMagicCore.resourceManager.setCurrentPack(user, "none");
            return;
        }
        if (!current.equals(preferred)) {
            MCMagicCore.resourceManager.sendPack(player, preferred);
        }
    }

    @EventHandler
    public void onResourceStatus(ResourceStatusEvent event) {
        Player player = event.getPlayer();
        switch (event.getStatus()) {
            case ACCEPTED:
                player.sendMessage(ChatColor.GREEN + "Resource Pack accepted! Downloading now...");
                break;
            case LOADED:
                player.sendMessage(ChatColor.GREEN + "Resource Pack loaded!");
                break;
            case FAILED:
                player.sendMessage(ChatColor.RED + "Download failed! Please report this to a Staff Member. (Error Code 101)");
                break;
            case DECLINED:
                for (int i = 0; i < 5; i++) {
                    player.sendMessage(" ");
                }
                player.sendMessage(ChatColor.RED + "You have declined the Resource Pack!");
                player.sendMessage(ChatColor.YELLOW + "For help with this, visit: " + ChatColor.AQUA +
                        "http://mcmagic.us/rphelp");
                break;
            default:
                player.sendMessage(ChatColor.RED + "Download failed! Please report this to a Staff Member. (Error Code 101)");
        }
    }
}
