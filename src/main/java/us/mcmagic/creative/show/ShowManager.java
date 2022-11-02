package us.mcmagic.creative.show;

import com.intellectualcrafters.plot.api.PlotAPI;
import com.intellectualcrafters.plot.object.Plot;
import com.intellectualcrafters.plot.object.PlotPlayer;
import com.plotsquared.bukkit.util.BukkitUtil;
import org.bukkit.*;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.metadata.FixedMetadataValue;
import org.bukkit.util.Vector;
import us.mcmagic.creative.Creative;
import us.mcmagic.creative.handlers.CreativeInventoryType;
import us.mcmagic.creative.handlers.ShowColor;
import us.mcmagic.creative.handlers.ShowFireworkData;
import us.mcmagic.creative.show.actions.FireworkAction;
import us.mcmagic.creative.show.actions.ParticleAction;
import us.mcmagic.creative.show.actions.ShowAction;
import us.mcmagic.creative.show.actions.TextAction;
import us.mcmagic.creative.show.handlers.AudioTrack;
import us.mcmagic.creative.show.handlers.PlotArea;
import us.mcmagic.creative.show.ticker.TickEvent;
import us.mcmagic.creative.show.ticker.Ticker;
import us.mcmagic.mcmagiccore.MCMagicCore;
import us.mcmagic.mcmagiccore.audioserver.AudioArea;
import us.mcmagic.mcmagiccore.itemcreator.ItemCreator;
import us.mcmagic.mcmagiccore.particles.ParticleEffect;
import us.mcmagic.mcmagiccore.title.TitleObject;

import java.io.File;
import java.io.IOException;
import java.util.*;

/**
 * Created by Marc on 12/11/15
 */
public class ShowManager implements Listener {
    public HashMap<UUID, Show> shows = new HashMap<>();
    private HashMap<UUID, Show> editSessions = new HashMap<>();
    private HashMap<UUID, AddAction> actions = new HashMap<>();
    private TreeMap<String, AudioTrack> audioTracks = new TreeMap<>();

    public ShowManager() {
        Bukkit.getScheduler().runTaskTimer(Creative.getInstance(), new Ticker(), 1, 1);
        loadTracks();
    }

    public void loadTracks() {
        audioTracks.clear();
        YamlConfiguration config = YamlConfiguration.loadConfiguration(new File("plugins/Creative/tracks.yml"));
        List<String> tracks = new ArrayList<>(config.getConfigurationSection("tracks").getKeys(false));
        Collections.sort(tracks);
        for (String s : tracks) {
            AudioTrack audioTrack = new AudioTrack(config.getString("tracks." + s + ".name"),
                    config.getString("tracks." + s + ".path"));
            audioTracks.put(s, audioTrack);
        }
    }

    @EventHandler
    public void onTick(TickEvent event) {
        for (Map.Entry<UUID, Show> entry : new HashSet<>(shows.entrySet())) {
            Show show = entry.getValue();
            if (show.update()) {
                Player player = Bukkit.getPlayer(show.getOwner());
                if (player != null) {
                    if (!show.getAudioTrack().equals("none")) {
                        for (AudioArea a : MCMagicCore.audioServer.getAudioAreas()) {
                            if (a instanceof PlotArea) {
                                PlotArea area = (PlotArea) a;
                                if (area.getAreaName().equals(player.getName())) {
                                    area.removeAllPlayers(true);
                                    MCMagicCore.audioServer.removeArea(area);
                                    break;
                                }
                            }
                        }
                    }
                    messagePlayer(player, "Your show " + ChatColor.AQUA + show.getNameColored() + ChatColor.GREEN +
                            " has ended!");
                }
                shows.remove(entry.getKey());
            }
        }
    }

    public void messagePlayer(Player tp, String msg) {
        tp.sendMessage(ChatColor.WHITE + "[" + ChatColor.BLUE + "Show" + ChatColor.WHITE + "] " + ChatColor.GREEN + msg);
    }

    public Location strToLoc(String string) {
        if (string.length() == 0) {
            return null;
        }
        String[] tokens = string.split(",");
        try {
            for (World cur : Bukkit.getWorlds()) {
                if (cur.getName().equalsIgnoreCase(tokens[0])) {
                    return new Location(cur, Double.parseDouble(tokens[1]),
                            Double.parseDouble(tokens[2]), Double.parseDouble(tokens[3]));
                }
            }
        } catch (Exception e) {
            return null;
        }
        return null;
    }

    public double offset(Location a, Location b) {
        return offset(a.toVector(), b.toVector());
    }

    public double offset(Vector a, Vector b) {
        return a.subtract(b).length();
    }

    @SuppressWarnings("deprecation")
    public Show startShow(Player player) {
        if (shows.containsKey(player.getUniqueId())) {
            messagePlayer(player, ChatColor.RED + "Your Show is already running!");
            return null;
        }
        File showFile = new File("plugins/Creative/shows/" + player.getUniqueId().toString() + ".show");
        if (!showFile.exists()) {
            return null;
        }
        PlotAPI api = new PlotAPI(Creative.getInstance());
        Plot plot = api.getPlot(player);
        boolean owns = false;
        for (Plot pl : api.getPlayerPlots(Bukkit.getWorld("plotworld"), player)) {
            if (plot.getId().equals(pl.getId())) {
                owns = true;
                break;
            }
        }
        if (!owns) {
            messagePlayer(player, ChatColor.RED + "You must start shows on your own Plot!");
            return null;
        }
        Show show = new Show(showFile, player, plot);
        if (show.getActions().isEmpty()) {
            return null;
        }
        if (!show.getAudioTrack().equals("none")) {
            PlotArea area = new PlotArea(plot.getId(), player, show.getAudioTrack(), player.getWorld());
            MCMagicCore.audioServer.addArea(area);
            for (Player p : Bukkit.getOnlinePlayers()) {
                Plot pl = api.getPlot(p);
                if (pl == null) {
                    continue;
                }
                if (pl.getId().equals(plot.getId())) {
                    area.triggerPlayer(p);
                }
            }
        }
        shows.put(player.getUniqueId(), show);
        return show;
    }

    public boolean stopShow(Player player) {
        Show show = shows.remove(player.getUniqueId());
        if (show == null) {
            return true;
        }
        if (!show.getAudioTrack().equals("none")) {
            PlotArea area = null;
            for (AudioArea a : MCMagicCore.audioServer.getAudioAreas()) {
                if (a.getAreaName().equals(player.getName())) {
                    area = (PlotArea) a;
                }
            }
            if (area != null) {
                area.removeAllPlayers(true);
            }
            MCMagicCore.audioServer.removeArea(area);
        }
        return show != null;
    }

    public void editShow(Player player) throws IOException {
        editShow(player, 1);
    }

    public void editShow(final Player player, int page) throws IOException {
        Show show = createSession(player);
        if (show == null) {
            return;
        }
        if (show.getActions().size() < (45 * (page - 1) + 1)) {
            page -= 1;
        }
        if (show.getActions().size() <= 1) {
            page = 1;
        }
        List<ShowAction> actions = show.getActions().subList(page > 1 ? (45 * (page - 1)) : 0, (show.getActions().size() - (45 * (page - 1))) > 45 ? (45 * page) : show.getActions().size());
        Inventory inv = Bukkit.createInventory(player, 54, ChatColor.BLUE + "Edit Show File Page " + page);
        int place = 0;
        for (ShowAction action : actions) {
            if (action.getItem() == null) {
                continue;
            }
            if (place >= 45) {
                break;
            }
            ItemStack item = new ItemStack(action.getItem());
            ItemMeta meta = item.getItemMeta();
            if (action.getDescription().contains("BREAK")) {
                String[] l = action.getDescription().split("BREAK");
                meta.setLore(Arrays.asList(l[0], l[1], " ", ChatColor.YELLOW + "Left-Click " + ChatColor.GREEN +
                        "to Edit this Action!", ChatColor.YELLOW + "Right-Click " + ChatColor.RED + "to Remove this Action!"));
            } else {
                meta.setLore(Arrays.asList(action.getDescription(), " ", ChatColor.YELLOW + "Left-Click " + ChatColor.GREEN +
                        "to Edit this Action!", ChatColor.YELLOW + "Right-Click " + ChatColor.RED + "to Remove this Action!"));
            }
            item.setItemMeta(meta);
            inv.setItem(place, item);
            place++;
        }
        if (page > 1) {
            inv.setItem(48, Creative.menuUtil.last);
        }
        int maxPage = 1;
        int n = show.getActions().size();
        while (true) {
            if (n - 45 > 0) {
                n -= 45;
                maxPage += 1;
            } else {
                break;
            }
        }
        if (show.getActions().size() > 45 && page < maxPage) {
            inv.setItem(50, Creative.menuUtil.next);
        }
        inv.setItem(53, new ItemCreator(Material.STAINED_CLAY, 1, (byte) 5, ChatColor.GREEN + "Add Action",
                Arrays.asList(ChatColor.GREEN + "Click to add a new Action!")));
        inv.setItem(49, Creative.menuUtil.back);
        player.openInventory(inv);
    }

    @SuppressWarnings("deprecation")
    private Show createSession(Player player) throws IOException {
        if (!player.getWorld().getName().equalsIgnoreCase("plotworld")) {
            messagePlayer(player, ChatColor.RED + "You must edit shows on your own Plot!");
            return null;
        }
        Show show;
        if (!editSessions.containsKey(player.getUniqueId())) {
            final File showFile = new File("plugins/Creative/shows/" + player.getUniqueId().toString() + ".show");
            PlotAPI api = new PlotAPI(Creative.getInstance());
            Plot plot = api.getPlot(player);
            boolean owns = false;
            if (plot != null) {
                for (Plot pl : api.getPlayerPlots(Bukkit.getWorld("plotworld"), player)) {
                    if (plot.getId().equals(pl.getId())) {
                        owns = true;
                        break;
                    }
                }
            }
            if (!owns) {
                player.closeInventory();
                messagePlayer(player, ChatColor.RED + "You must edit shows on your own Plot!");
                return null;
            }
            if (!showFile.exists()) {
                showFile.createNewFile();
                show = new Show(null, player, plot);
            } else {
                show = new Show(showFile, player, plot);
            }
            editSessions.put(player.getUniqueId(), show);
        } else {
            show = editSessions.get(player.getUniqueId());
        }
        return show;
    }

    public void cancelEdit(Player player) {
        cancelEdit(player, false);
    }

    public void cancelEdit(Player player, boolean silent) {
        if (editSessions.remove(player.getUniqueId()) != null && !silent) {
            messagePlayer(player, ChatColor.RED + "Your Show edit session has ended!");
        }
    }

    @SuppressWarnings("deprecation")
    public void handle(InventoryClickEvent event) throws IOException {
        final Player player = (Player) event.getWhoClicked();
        final PlotPlayer tp = BukkitUtil.getPlayer(player);
        ItemStack item = event.getCurrentItem();
        if (item == null) {
            return;
        }
        ItemMeta meta = item.getItemMeta();
        if (meta == null || meta.getDisplayName() == null) {
            return;
        }
        String invname = ChatColor.stripColor(event.getInventory().getName());
        String name = ChatColor.stripColor(meta.getDisplayName());
        boolean isBack = item.getType().equals(Material.ARROW);
        boolean right = event.isRightClick();
        event.setCancelled(true);
        if (invname.startsWith("Edit Show File Page ")) {
            int page = Integer.parseInt(invname.toLowerCase().replace("edit show file page ", ""));
            player.setMetadata("page", new FixedMetadataValue(Creative.getInstance(), page));
            if (name.contains("Next")) {
                editShow(player, page + 1);
                return;
            }
            if (name.contains("Last")) {
                editShow(player, page - 1);
                return;
            }
            if (isBack) {
                Creative.menuUtil.openMenu(player, CreativeInventoryType.MAIN);
                return;
            }
            switch (name) {
                case "Add Action": {
                    openAddAction(player);
                    break;
                }
                case "Text Action":
                case "Set Music":
                case "Firework Action":
                case "Particle Action": {
                    if (right) {
                        Show show = editSessions.get(player.getUniqueId());
                        show.actions.remove(event.getSlot() + (45 * (page - 1)));
                        show.saveFile();
                        editShow(player, page);
                        return;
                    }
                    editAction(player, event.getSlot() + (45 * (page - 1)));
                    break;
                }
            }
            return;
        }
        switch (invname) {
            case "Add Action": {
                if (isBack) {
                    int page;
                    if (player.hasMetadata("page")) {
                        page = player.getMetadata("page").get(0).asInt();
                    } else {
                        page = 1;
                    }
                    editShow(player, page);
                    return;
                }
                ItemStack setTime = new ItemCreator(Material.WATCH, ChatColor.GREEN + "Set Time",
                        Arrays.asList(ChatColor.YELLOW + "Time in seconds after start of", ChatColor.YELLOW +
                                "Show for an Action to execute."));
                switch (name) {
                    case "Text Action": {
                        Inventory inv = Bukkit.createInventory(player, 27, ChatColor.BLUE + "Add Text Action");
                        inv.setItem(11, setTime);
                        inv.setItem(15, new ItemCreator(Material.SIGN, ChatColor.GREEN + "Set Text",
                                Arrays.asList(ChatColor.YELLOW + "Supports Color Codes!")));
                        inv.setItem(22, Creative.menuUtil.back);
                        player.openInventory(inv);
                        break;
                    }
                    case "Set Music": {
                        Inventory inv = Bukkit.createInventory(player, 27, ChatColor.BLUE + "Set Music");
                        inv.setItem(13, new ItemCreator(Material.RECORD_4, ChatColor.GREEN + "Select Track"));
                        inv.setItem(22, Creative.menuUtil.back);
                        player.openInventory(inv);
                        break;
                    }
                    case "Particle Action": {
                        Inventory inv = Bukkit.createInventory(player, 27, ChatColor.BLUE + "Add Particle Action");
                        inv.setItem(11, setTime);
                        inv.setItem(15, new ItemCreator(Material.NETHER_STAR, ChatColor.GREEN + "Set Particle",
                                Arrays.asList(ChatColor.YELLOW + "Some Minecraft Particles are not allowed")));
                        inv.setItem(22, Creative.menuUtil.back);
                        player.openInventory(inv);
                        break;
                    }
                    case "Firework Action": {
                        Inventory inv = Bukkit.createInventory(player, 27, ChatColor.BLUE + "Add Firework Action");
                        inv.setItem(11, setTime);
                        inv.setItem(12, new ItemCreator(Material.FIREWORK_CHARGE, ChatColor.GREEN + "Select Type",
                                Arrays.asList(ChatColor.YELLOW + "Choose shape of the Firework!")));
                        inv.setItem(13, new ItemCreator(Material.WOOL, 1, (byte) 3, ChatColor.GREEN + "Select Color",
                                Arrays.asList(ChatColor.YELLOW + "The first color of the Firework")));
                        inv.setItem(14, new ItemCreator(Material.WOOL, 1, (byte) 14, ChatColor.GREEN + "Select Fade",
                                Arrays.asList(ChatColor.YELLOW + "The color the Firework fades to")));
                        inv.setItem(15, new ItemCreator(Material.FIREWORK, 1, ChatColor.GREEN + "Set Power",
                                Arrays.asList(ChatColor.YELLOW + "The power of the Firework")));
                        inv.setItem(22, Creative.menuUtil.back);
                        player.openInventory(inv);
                        break;
                    }
                }
            }
            case "Add Firework Action": {
                if (isBack) {
                    int page;
                    if (player.hasMetadata("page")) {
                        page = player.getMetadata("page").get(0).asInt();
                    } else {
                        page = 1;
                    }
                    editShow(player, page);
                    return;
                }
                switch (name) {
                    case "Set Time": {
                        player.closeInventory();
                        new TitleObject(ChatColor.GREEN + "Set a Time", ChatColor.GREEN +
                                "Enter a number for the action to execute at").setFadeIn(0).setFadeOut(0)
                                .setStay(200).send(player);
                        int id = player.getMetadata("actionid").get(0).asInt();
                        actions.put(player.getUniqueId(), new AddAction(id, Action.TIME));
                        Show show = editSessions.get(player.getUniqueId());
                        show.actions.add(new FireworkAction(id, show, null, player.getLocation(),
                                new ShowFireworkData(FireworkEffect.Type.BALL, ShowColor.BLACK, ShowColor.WHITE,
                                        false, true), 1));
                        break;
                    }
                    case "Select Type": {
                        Inventory inv = Bukkit.createInventory(player, 27, ChatColor.BLUE + "Select Type");
                        inv.setItem(9, new ItemCreator(Material.CLAY_BALL, ChatColor.GREEN + "Ball",
                                Arrays.asList(ChatColor.GRAY + "Click to Select!")));
                        inv.setItem(11, new ItemCreator(Material.SNOW_BALL, ChatColor.GREEN + "Large Ball",
                                Arrays.asList(ChatColor.GRAY + "Click to Select!")));
                        inv.setItem(13, new ItemCreator(Material.NETHER_STAR, ChatColor.GREEN + "Star",
                                Arrays.asList(ChatColor.GRAY + "Click to Select!")));
                        inv.setItem(15, new ItemCreator(Material.CLAY_BALL, ChatColor.GREEN + "Burst",
                                Arrays.asList(ChatColor.GRAY + "Click to Select!")));
                        inv.setItem(17, new ItemCreator(Material.SKULL_ITEM, 1, (byte) 4, ChatColor.GREEN + "Creeper",
                                Arrays.asList(ChatColor.GRAY + "Click to Select!")));
                        inv.setItem(22, Creative.menuUtil.back);
                        player.openInventory(inv);
                        break;
                    }
                    case "Select Color": {
                        Inventory inv = Bukkit.createInventory(player, 27, ChatColor.BLUE + "Select Color");
                        inv.setItem(0, new ItemCreator(Material.WOOL, 1, (byte) 14, ChatColor.DARK_RED + "Red",
                                Arrays.asList(ChatColor.GRAY + "Click to Select!")));
                        inv.setItem(1, new ItemCreator(Material.WOOL, 1, (byte) 1, ChatColor.GOLD + "Orange",
                                Arrays.asList(ChatColor.GRAY + "Click to Select!")));
                        inv.setItem(2, new ItemCreator(Material.WOOL, 1, (byte) 4, ChatColor.YELLOW + "Yellow",
                                Arrays.asList(ChatColor.GRAY + "Click to Select!")));
                        inv.setItem(3, new ItemCreator(Material.WOOL, 1, (byte) 5, ChatColor.GREEN + "Lime",
                                Arrays.asList(ChatColor.GRAY + "Click to Select!")));
                        inv.setItem(4, new ItemCreator(Material.WOOL, 1, (byte) 13, ChatColor.DARK_GREEN + "Green",
                                Arrays.asList(ChatColor.GRAY + "Click to Select!")));
                        inv.setItem(5, new ItemCreator(Material.WOOL, 1, (byte) 3, ChatColor.AQUA + "Aqua",
                                Arrays.asList(ChatColor.GRAY + "Click to Select!")));
                        inv.setItem(6, new ItemCreator(Material.WOOL, 1, (byte) 9, ChatColor.DARK_AQUA + "Cyan",
                                Arrays.asList(ChatColor.GRAY + "Click to Select!")));
                        inv.setItem(7, new ItemCreator(Material.WOOL, 1, (byte) 11, ChatColor.BLUE + "Blue",
                                Arrays.asList(ChatColor.GRAY + "Click to Select!")));
                        inv.setItem(8, new ItemCreator(Material.WOOL, 1, (byte) 10, ChatColor.DARK_PURPLE + "Purple",
                                Arrays.asList(ChatColor.GRAY + "Click to Select!")));
                        inv.setItem(10, new ItemCreator(Material.WOOL, 1, (byte) 2, ChatColor.LIGHT_PURPLE + "Magenta",
                                Arrays.asList(ChatColor.GRAY + "Click to Select!")));
                        inv.setItem(11, new ItemCreator(Material.WOOL, 1, (byte) 6, ChatColor.RED + "Pink",
                                Arrays.asList(ChatColor.GRAY + "Click to Select!")));
                        inv.setItem(12, new ItemCreator(Material.WOOL, 1, (byte) 0, ChatColor.WHITE + "White",
                                Arrays.asList(ChatColor.GRAY + "Click to Select!")));
                        inv.setItem(13, new ItemCreator(Material.WOOL, 1, (byte) 8, ChatColor.GRAY + "Silver",
                                Arrays.asList(ChatColor.GRAY + "Click to Select!")));
                        inv.setItem(14, new ItemCreator(Material.WOOL, 1, (byte) 7, ChatColor.DARK_GRAY + "Gray",
                                Arrays.asList(ChatColor.GRAY + "Click to Select!")));
                        inv.setItem(15, new ItemCreator(Material.WOOL, 1, (byte) 15, ChatColor.DARK_GRAY + "Black",
                                Arrays.asList(ChatColor.GRAY + "Click to Select!")));
                        inv.setItem(16, new ItemCreator(Material.WOOL, 1, (byte) 12, ChatColor.DARK_GRAY + "Brown",
                                Arrays.asList(ChatColor.GRAY + "Click to Select!")));
                        inv.setItem(22, Creative.menuUtil.back);
                        player.openInventory(inv);
                        break;
                    }
                    case "Select Fade": {
                        Inventory inv = Bukkit.createInventory(player, 27, ChatColor.BLUE + "Select Fade");
                        inv.setItem(0, new ItemCreator(Material.WOOL, 1, (byte) 14, ChatColor.DARK_RED + "Red",
                                Arrays.asList(ChatColor.GRAY + "Click to Select!")));
                        inv.setItem(1, new ItemCreator(Material.WOOL, 1, (byte) 1, ChatColor.GOLD + "Orange",
                                Arrays.asList(ChatColor.GRAY + "Click to Select!")));
                        inv.setItem(2, new ItemCreator(Material.WOOL, 1, (byte) 4, ChatColor.YELLOW + "Yellow",
                                Arrays.asList(ChatColor.GRAY + "Click to Select!")));
                        inv.setItem(3, new ItemCreator(Material.WOOL, 1, (byte) 5, ChatColor.GREEN + "Lime",
                                Arrays.asList(ChatColor.GRAY + "Click to Select!")));
                        inv.setItem(4, new ItemCreator(Material.WOOL, 1, (byte) 13, ChatColor.DARK_GREEN + "Green",
                                Arrays.asList(ChatColor.GRAY + "Click to Select!")));
                        inv.setItem(5, new ItemCreator(Material.WOOL, 1, (byte) 3, ChatColor.AQUA + "Aqua",
                                Arrays.asList(ChatColor.GRAY + "Click to Select!")));
                        inv.setItem(6, new ItemCreator(Material.WOOL, 1, (byte) 9, ChatColor.DARK_AQUA + "Cyan",
                                Arrays.asList(ChatColor.GRAY + "Click to Select!")));
                        inv.setItem(7, new ItemCreator(Material.WOOL, 1, (byte) 11, ChatColor.BLUE + "Blue",
                                Arrays.asList(ChatColor.GRAY + "Click to Select!")));
                        inv.setItem(8, new ItemCreator(Material.WOOL, 1, (byte) 10, ChatColor.DARK_PURPLE + "Purple",
                                Arrays.asList(ChatColor.GRAY + "Click to Select!")));
                        inv.setItem(10, new ItemCreator(Material.WOOL, 1, (byte) 2, ChatColor.LIGHT_PURPLE + "Magenta",
                                Arrays.asList(ChatColor.GRAY + "Click to Select!")));
                        inv.setItem(11, new ItemCreator(Material.WOOL, 1, (byte) 6, ChatColor.RED + "Pink",
                                Arrays.asList(ChatColor.GRAY + "Click to Select!")));
                        inv.setItem(12, new ItemCreator(Material.WOOL, 1, (byte) 0, ChatColor.WHITE + "White",
                                Arrays.asList(ChatColor.GRAY + "Click to Select!")));
                        inv.setItem(13, new ItemCreator(Material.WOOL, 1, (byte) 8, ChatColor.GRAY + "Silver",
                                Arrays.asList(ChatColor.GRAY + "Click to Select!")));
                        inv.setItem(14, new ItemCreator(Material.WOOL, 1, (byte) 7, ChatColor.DARK_GRAY + "Gray",
                                Arrays.asList(ChatColor.GRAY + "Click to Select!")));
                        inv.setItem(15, new ItemCreator(Material.WOOL, 1, (byte) 15, ChatColor.DARK_GRAY + "Black",
                                Arrays.asList(ChatColor.GRAY + "Click to Select!")));
                        inv.setItem(16, new ItemCreator(Material.WOOL, 1, (byte) 12, ChatColor.DARK_GRAY + "Brown",
                                Arrays.asList(ChatColor.GRAY + "Click to Select!")));
                        inv.setItem(22, Creative.menuUtil.back);
                        player.openInventory(inv);
                        break;
                    }
                    case "Set Power": {
                        Inventory inv = Bukkit.createInventory(player, 27, ChatColor.BLUE + "Set Power");
                        inv.setItem(10, new ItemCreator(Material.FIREWORK, 0, ChatColor.GREEN + "Power 0",
                                Arrays.asList(ChatColor.GRAY + "Click to Select!")));
                        inv.setItem(12, new ItemCreator(Material.FIREWORK, 1, ChatColor.GREEN + "Power 1",
                                Arrays.asList(ChatColor.GRAY + "Click to Select!")));
                        inv.setItem(14, new ItemCreator(Material.FIREWORK, 2, ChatColor.GREEN + "Power 2",
                                Arrays.asList(ChatColor.GRAY + "Click to Select!")));
                        inv.setItem(16, new ItemCreator(Material.FIREWORK, 3, ChatColor.GREEN + "Power 3",
                                Arrays.asList(ChatColor.GRAY + "Click to Select!")));
                        inv.setItem(22, Creative.menuUtil.back);
                        player.openInventory(inv);
                        break;
                    }
                }
                break;
            }
            case "Select Type": {
                int id = player.getMetadata("actionid").get(0).asInt();
                if (isBack) {
                    editAction(player, id);
                    return;
                }
                Show show = editSessions.get(player.getUniqueId());
                FireworkEffect.Type type = getType(name);
                if (show.getActions().size() <= id) {
                    show.actions.add(new FireworkAction(id, show, null, player.getLocation(),
                            new ShowFireworkData(type, ShowColor.BLACK, ShowColor.WHITE, false, true), 1));
                } else {
                    FireworkAction action = (FireworkAction) show.actions.get(id);
                    action.setType(type);
                }
                show.saveFile();
                editAction(player, id);
                break;
            }
            case "Select Color": {
                int id = player.getMetadata("actionid").get(0).asInt();
                if (isBack) {
                    editAction(player, id);
                    return;
                }
                Show show = editSessions.get(player.getUniqueId());
                ShowColor color = ShowColor.fromString(name);
                if (show.getActions().size() <= id) {
                    show.actions.add(new FireworkAction(id, show, null, player.getLocation(),
                            new ShowFireworkData(FireworkEffect.Type.BALL, color, ShowColor.WHITE, false, true), 1));
                } else {
                    FireworkAction action = (FireworkAction) show.actions.get(id);
                    action.setColor(color);
                }
                show.saveFile();
                editAction(player, id);
                break;
            }
            case "Select Fade": {
                int id = player.getMetadata("actionid").get(0).asInt();
                if (isBack) {
                    editAction(player, id);
                    return;
                }
                Show show = editSessions.get(player.getUniqueId());
                ShowColor fade = ShowColor.fromString(name);
                if (show.getActions().size() <= id) {
                    show.actions.add(new FireworkAction(id, show, null, player.getLocation(),
                            new ShowFireworkData(FireworkEffect.Type.BALL, ShowColor.BLACK, fade, false, true), 1));
                } else {
                    FireworkAction action = (FireworkAction) show.actions.get(id);
                    action.setFade(fade);
                }
                show.saveFile();
                editAction(player, id);
                break;
            }
            case "Set Power": {
                int id = player.getMetadata("actionid").get(0).asInt();
                if (isBack) {
                    editAction(player, id);
                    return;
                }
                Show show = editSessions.get(player.getUniqueId());
                int power = Integer.parseInt(name.replace("Power ", ""));
                if (show.getActions().size() <= id) {
                    show.actions.add(new FireworkAction(id, show, null, player.getLocation(),
                            new ShowFireworkData(FireworkEffect.Type.BALL, ShowColor.BLACK, ShowColor.WHITE, false, true), power));
                } else {
                    FireworkAction action = (FireworkAction) show.actions.get(id);
                    action.setPower(power);
                }
                show.saveFile();
                editAction(player, id);
                break;
            }
            case "Add Text Action": {
                if (isBack) {
                    int page;
                    if (player.hasMetadata("page")) {
                        page = player.getMetadata("page").get(0).asInt();
                    } else {
                        page = 1;
                    }
                    editShow(player, page);
                    return;
                }
                switch (name) {
                    case "Set Time": {
                        player.closeInventory();
                        new TitleObject(ChatColor.GREEN + "Set a Time", ChatColor.GREEN +
                                "Enter a number for the action to execute at").setFadeIn(0).setFadeOut(0)
                                .setStay(200).send(player);
                        int id = player.getMetadata("actionid").get(0).asInt();
                        actions.put(player.getUniqueId(), new AddAction(id, Action.TIME));
                        Show show = editSessions.get(player.getUniqueId());
                        show.actions.add(new TextAction(id, show, null, null));
                        break;
                    }
                    case "Set Text": {
                        player.closeInventory();
                        new TitleObject(ChatColor.GREEN + "Set Text Message", ChatColor.GREEN +
                                "Type a message to be displayed (Color Codes work!)").setFadeIn(0).setFadeOut(0)
                                .setStay(200).send(player);
                        Show show = editSessions.get(player.getUniqueId());
                        int id = player.getMetadata("actionid").get(0).asInt();
                        actions.put(player.getUniqueId(), new AddAction(id, Action.TEXT));
                        show.actions.add(new TextAction(id, show, null, null));
                        break;
                    }
                }
                break;
            }
            case "Set Music": {
                if (isBack) {
                    int page;
                    if (player.hasMetadata("page")) {
                        page = player.getMetadata("page").get(0).asInt();
                    } else {
                        page = 1;
                    }
                    editShow(player, page);
                    return;
                }
                switch (name) {
                    case "Select Track": {
                        Show show = editSessions.get(player.getUniqueId());
                        int id = player.getMetadata("actionid").get(0).asInt();
                        Inventory inv = Bukkit.createInventory(player, 36, ChatColor.BLUE + "Select Track");
                        int place = 0;
                        for (AudioTrack track : audioTracks.values()) {
                            if (place >= 27) {
                                break;
                            }
                            ItemStack i = new ItemCreator(track.getItem(), ChatColor.GREEN + track.getName());
                            inv.setItem(place, i);
                            place++;
                        }
                        inv.setItem(31, Creative.menuUtil.back);
                        player.openInventory(inv);
                        break;
                    }
                }
                break;
            }
            case "Add Particle Action": {
                if (isBack) {
                    int page;
                    if (player.hasMetadata("page")) {
                        page = player.getMetadata("page").get(0).asInt();
                    } else {
                        page = 1;
                    }
                    editShow(player, page);
                    return;
                }
                switch (name) {
                    case "Set Time": {
                        player.closeInventory();
                        new TitleObject(ChatColor.GREEN + "Set a Time", ChatColor.GREEN +
                                "Enter a number for the action to execute at").setFadeIn(0).setFadeOut(0)
                                .setStay(200).send(player);
                        int id = player.getMetadata("actionid").get(0).asInt();
                        actions.put(player.getUniqueId(), new AddAction(id, Action.TIME));
                        Show show = editSessions.get(player.getUniqueId());
                        show.actions.add(new ParticleAction(id, show, null, null, player.getLocation(),
                                .75f, .5f, .75f, 0, 20));
                        break;
                    }
                    case "Set Particle": {
                        Show show = editSessions.get(player.getUniqueId());
                        int id = player.getMetadata("actionid").get(0).asInt();
                        Inventory inv = Bukkit.createInventory(player, 27, ChatColor.BLUE + "Select Particle");
                        ChatColor c = ChatColor.GREEN;
                        inv.setItem(9, new ItemCreator(Material.POTION, 1, (byte) 16419, c + "Heart", new ArrayList<>()));
                        inv.setItem(10, new ItemCreator(Material.SNOW_BALL, 1, c + "Snow Shovel", new ArrayList<>()));
                        inv.setItem(11, new ItemCreator(Material.TNT, 1, c + "Explode", new ArrayList<>()));
                        inv.setItem(12, new ItemCreator(Material.NOTE_BLOCK, 1, c + "Note", new ArrayList<>()));
                        inv.setItem(13, new ItemCreator(Material.SNOW, 1, c + "Cloud", new ArrayList<>()));
                        inv.setItem(14, new ItemCreator(Material.FLINT_AND_STEEL, 1, c + "Flame", new ArrayList<>()));
                        inv.setItem(15, new ItemCreator(Material.REDSTONE, 1, c + "Red Dust", new ArrayList<>()));
                        inv.setItem(16, new ItemCreator(Material.LAVA_BUCKET, 1, c + "Lava", new ArrayList<>()));
                        inv.setItem(17, new ItemCreator(Material.FIREWORK, 1, (byte) 0, c + "Fireworks Spark",
                                new ArrayList<>()));
                        inv.setItem(22, Creative.menuUtil.back);
                        player.openInventory(inv);
                        break;
                    }
                }
                break;
            }
            case "Select Particle": {
                int id = player.getMetadata("actionid").get(0).asInt();
                if (isBack) {
                    editAction(player, id);
                    return;
                }
                Show show = editSessions.get(player.getUniqueId());
                ParticleEffect effect = ParticleEffect.fromString(ChatColor.stripColor(item.getItemMeta()
                        .getDisplayName().replace(" ", "").toLowerCase()));
                if (show.getActions().size() <= id) {
                    show.actions.add(new ParticleAction(id, show, null, effect, player.getLocation(),
                            .75f, .5f, .75f, 0, 20));
                } else {
                    ParticleAction action = (ParticleAction) show.actions.get(id);
                    action.setParticle(effect);
                }
                show.saveFile();
                editAction(player, id);
                break;
            }
            case "Select Track": {
                int id = player.getMetadata("actionid").get(0).asInt();
                if (isBack) {
                    editAction(player, id);
                    return;
                }
                Show show = editSessions.get(player.getUniqueId());
                AudioTrack track = null;
                for (AudioTrack t : new ArrayList<>(audioTracks.values())) {
                    if (t.getName().equalsIgnoreCase(name)) {
                        track = t;
                    }
                }
                if (track == null) {
                    break;
                }
                show.setAudioTrack(track.getAudioPath());
                show.saveFile();
                editAction(player, id);
                break;
            }
            case "Edit Text Action": {
                if (isBack) {
                    int page;
                    if (player.hasMetadata("page")) {
                        page = player.getMetadata("page").get(0).asInt();
                    } else {
                        page = 1;
                    }
                    editShow(player, page);
                    return;
                }
                switch (name) {
                    case "Set Time": {
                        Show show = editSessions.get(player.getUniqueId());
                        int id = player.getMetadata("actionid").get(0).asInt();
                        player.closeInventory();
                        new TitleObject(ChatColor.GREEN + "Set a Time", ChatColor.GREEN +
                                "Enter a number for the action to execute at").setFadeIn(0).setFadeOut(0)
                                .setStay(200).send(player);
                        actions.put(player.getUniqueId(), new AddAction(id, Action.TIME));
                        break;
                    }
                    case "Set Text": {
                        Show show = editSessions.get(player.getUniqueId());
                        int id = player.getMetadata("actionid").get(0).asInt();
                        player.closeInventory();
                        new TitleObject(ChatColor.GREEN + "Set Text Message", ChatColor.GREEN +
                                "Type a message to be displayed (Color Codes work!)").setFadeIn(0).setFadeOut(0)
                                .setStay(200).send(player);
                        actions.put(player.getUniqueId(), new AddAction(id, Action.TEXT));
                        break;
                    }
                }
                break;
            }
            case "Edit Particle Action": {
                if (isBack) {
                    int page;
                    if (player.hasMetadata("page")) {
                        page = player.getMetadata("page").get(0).asInt();
                    } else {
                        page = 1;
                    }
                    editShow(player, page);
                    return;
                }
                switch (name) {
                    case "Set Time": {
                        int id = player.getMetadata("actionid").get(0).asInt();
                        player.closeInventory();
                        new TitleObject(ChatColor.GREEN + "Set a Time", ChatColor.GREEN +
                                "Enter a number for the action to execute at").setFadeIn(0).setFadeOut(0)
                                .setStay(200).send(player);
                        actions.put(player.getUniqueId(), new AddAction(id, Action.TIME));
                        break;
                    }
                    case "Set Particle": {
                        Show show = editSessions.get(player.getUniqueId());
                        int id = player.getMetadata("actionid").get(0).asInt();
                        Inventory inv = Bukkit.createInventory(player, 27, ChatColor.BLUE + "Select Particle");
                        ChatColor c = ChatColor.GREEN;
                        inv.setItem(9, new ItemCreator(Material.POTION, 1, (byte) 16419, c + "Heart",
                                new ArrayList<>()));
                        inv.setItem(10, new ItemCreator(Material.SNOW_BALL, 1, c + "Snow Shovel", new ArrayList<>()));
                        inv.setItem(11, new ItemCreator(Material.TNT, 1, c + "Explode", new ArrayList<>()));
                        inv.setItem(12, new ItemCreator(Material.NOTE_BLOCK, 1, c + "Note", new ArrayList<>()));
                        inv.setItem(13, new ItemCreator(Material.SNOW, 1, c + "Cloud", new ArrayList<>()));
                        inv.setItem(14, new ItemCreator(Material.FLINT_AND_STEEL, 1, c + "Flame",
                                new ArrayList<>()));
                        inv.setItem(15, new ItemCreator(Material.REDSTONE, 1, c + "Red Dust", new ArrayList<>()));
                        inv.setItem(16, new ItemCreator(Material.LAVA_BUCKET, 1, c + "Lava", new ArrayList<>()));
                        inv.setItem(17, new ItemCreator(Material.FIREWORK, 1, (byte) 0, c + "Fireworks Spark",
                                new ArrayList<>()));
                        inv.setItem(22, Creative.menuUtil.back);
                        player.openInventory(inv);
                        break;
                    }
                }
                break;
            }
            case "Edit Firework Action": {
                if (isBack) {
                    int page;
                    if (player.hasMetadata("page")) {
                        page = player.getMetadata("page").get(0).asInt();
                    } else {
                        page = 1;
                    }
                    editShow(player, page);
                    return;
                }
                switch (name) {
                    case "Set Time": {
                        player.closeInventory();
                        new TitleObject(ChatColor.GREEN + "Set a Time", ChatColor.GREEN +
                                "Enter a number for the action to execute at").setFadeIn(0).setFadeOut(0)
                                .setStay(200).send(player);
                        int id = player.getMetadata("actionid").get(0).asInt();
                        actions.put(player.getUniqueId(), new AddAction(id, Action.TIME));
                        break;
                    }
                    case "Select Type": {
                        Inventory inv = Bukkit.createInventory(player, 27, ChatColor.BLUE + "Select Type");
                        inv.setItem(9, new ItemCreator(Material.CLAY_BALL, ChatColor.GREEN + "Ball",
                                Arrays.asList(ChatColor.GRAY + "Click to Select!")));
                        inv.setItem(11, new ItemCreator(Material.SNOW_BALL, ChatColor.GREEN + "Large Ball",
                                Arrays.asList(ChatColor.GRAY + "Click to Select!")));
                        inv.setItem(13, new ItemCreator(Material.NETHER_STAR, ChatColor.GREEN + "Star",
                                Arrays.asList(ChatColor.GRAY + "Click to Select!")));
                        inv.setItem(15, new ItemCreator(Material.CLAY_BALL, ChatColor.GREEN + "Burst",
                                Arrays.asList(ChatColor.GRAY + "Click to Select!")));
                        inv.setItem(17, new ItemCreator(Material.SKULL_ITEM, 1, (byte) 4, ChatColor.GREEN + "Creeper",
                                Arrays.asList(ChatColor.GRAY + "Click to Select!")));
                        inv.setItem(22, Creative.menuUtil.back);
                        player.openInventory(inv);
                        break;
                    }
                    case "Select Color": {
                        Inventory inv = Bukkit.createInventory(player, 27, ChatColor.BLUE + "Select Color");
                        inv.setItem(0, new ItemCreator(Material.WOOL, 1, (byte) 14, ChatColor.DARK_RED + "Red",
                                Arrays.asList(ChatColor.GRAY + "Click to Select!")));
                        inv.setItem(1, new ItemCreator(Material.WOOL, 1, (byte) 1, ChatColor.GOLD + "Orange",
                                Arrays.asList(ChatColor.GRAY + "Click to Select!")));
                        inv.setItem(2, new ItemCreator(Material.WOOL, 1, (byte) 4, ChatColor.YELLOW + "Yellow",
                                Arrays.asList(ChatColor.GRAY + "Click to Select!")));
                        inv.setItem(3, new ItemCreator(Material.WOOL, 1, (byte) 5, ChatColor.GREEN + "Lime",
                                Arrays.asList(ChatColor.GRAY + "Click to Select!")));
                        inv.setItem(4, new ItemCreator(Material.WOOL, 1, (byte) 13, ChatColor.DARK_GREEN + "Green",
                                Arrays.asList(ChatColor.GRAY + "Click to Select!")));
                        inv.setItem(5, new ItemCreator(Material.WOOL, 1, (byte) 3, ChatColor.AQUA + "Aqua",
                                Arrays.asList(ChatColor.GRAY + "Click to Select!")));
                        inv.setItem(6, new ItemCreator(Material.WOOL, 1, (byte) 9, ChatColor.DARK_AQUA + "Cyan",
                                Arrays.asList(ChatColor.GRAY + "Click to Select!")));
                        inv.setItem(7, new ItemCreator(Material.WOOL, 1, (byte) 11, ChatColor.BLUE + "Blue",
                                Arrays.asList(ChatColor.GRAY + "Click to Select!")));
                        inv.setItem(8, new ItemCreator(Material.WOOL, 1, (byte) 10, ChatColor.DARK_PURPLE + "Purple",
                                Arrays.asList(ChatColor.GRAY + "Click to Select!")));
                        inv.setItem(10, new ItemCreator(Material.WOOL, 1, (byte) 2, ChatColor.LIGHT_PURPLE + "Magenta",
                                Arrays.asList(ChatColor.GRAY + "Click to Select!")));
                        inv.setItem(11, new ItemCreator(Material.WOOL, 1, (byte) 6, ChatColor.RED + "Pink",
                                Arrays.asList(ChatColor.GRAY + "Click to Select!")));
                        inv.setItem(12, new ItemCreator(Material.WOOL, 1, (byte) 0, ChatColor.WHITE + "White",
                                Arrays.asList(ChatColor.GRAY + "Click to Select!")));
                        inv.setItem(13, new ItemCreator(Material.WOOL, 1, (byte) 8, ChatColor.GRAY + "Silver",
                                Arrays.asList(ChatColor.GRAY + "Click to Select!")));
                        inv.setItem(14, new ItemCreator(Material.WOOL, 1, (byte) 7, ChatColor.DARK_GRAY + "Gray",
                                Arrays.asList(ChatColor.GRAY + "Click to Select!")));
                        inv.setItem(15, new ItemCreator(Material.WOOL, 1, (byte) 15, ChatColor.DARK_GRAY + "Black",
                                Arrays.asList(ChatColor.GRAY + "Click to Select!")));
                        inv.setItem(16, new ItemCreator(Material.WOOL, 1, (byte) 12, ChatColor.DARK_GRAY + "Brown",
                                Arrays.asList(ChatColor.GRAY + "Click to Select!")));
                        inv.setItem(22, Creative.menuUtil.back);
                        player.openInventory(inv);
                        break;
                    }
                    case "Select Fade": {
                        Inventory inv = Bukkit.createInventory(player, 27, ChatColor.BLUE + "Select Fade");
                        inv.setItem(0, new ItemCreator(Material.WOOL, 1, (byte) 14, ChatColor.DARK_RED + "Red",
                                Arrays.asList(ChatColor.GRAY + "Click to Select!")));
                        inv.setItem(1, new ItemCreator(Material.WOOL, 1, (byte) 1, ChatColor.GOLD + "Orange",
                                Arrays.asList(ChatColor.GRAY + "Click to Select!")));
                        inv.setItem(2, new ItemCreator(Material.WOOL, 1, (byte) 4, ChatColor.YELLOW + "Yellow",
                                Arrays.asList(ChatColor.GRAY + "Click to Select!")));
                        inv.setItem(3, new ItemCreator(Material.WOOL, 1, (byte) 5, ChatColor.GREEN + "Lime",
                                Arrays.asList(ChatColor.GRAY + "Click to Select!")));
                        inv.setItem(4, new ItemCreator(Material.WOOL, 1, (byte) 13, ChatColor.DARK_GREEN + "Green",
                                Arrays.asList(ChatColor.GRAY + "Click to Select!")));
                        inv.setItem(5, new ItemCreator(Material.WOOL, 1, (byte) 3, ChatColor.AQUA + "Aqua",
                                Arrays.asList(ChatColor.GRAY + "Click to Select!")));
                        inv.setItem(6, new ItemCreator(Material.WOOL, 1, (byte) 9, ChatColor.DARK_AQUA + "Cyan",
                                Arrays.asList(ChatColor.GRAY + "Click to Select!")));
                        inv.setItem(7, new ItemCreator(Material.WOOL, 1, (byte) 11, ChatColor.BLUE + "Blue",
                                Arrays.asList(ChatColor.GRAY + "Click to Select!")));
                        inv.setItem(8, new ItemCreator(Material.WOOL, 1, (byte) 10, ChatColor.DARK_PURPLE + "Purple",
                                Arrays.asList(ChatColor.GRAY + "Click to Select!")));
                        inv.setItem(10, new ItemCreator(Material.WOOL, 1, (byte) 2, ChatColor.LIGHT_PURPLE + "Magenta",
                                Arrays.asList(ChatColor.GRAY + "Click to Select!")));
                        inv.setItem(11, new ItemCreator(Material.WOOL, 1, (byte) 6, ChatColor.RED + "Pink",
                                Arrays.asList(ChatColor.GRAY + "Click to Select!")));
                        inv.setItem(12, new ItemCreator(Material.WOOL, 1, (byte) 0, ChatColor.WHITE + "White",
                                Arrays.asList(ChatColor.GRAY + "Click to Select!")));
                        inv.setItem(13, new ItemCreator(Material.WOOL, 1, (byte) 8, ChatColor.GRAY + "Silver",
                                Arrays.asList(ChatColor.GRAY + "Click to Select!")));
                        inv.setItem(14, new ItemCreator(Material.WOOL, 1, (byte) 7, ChatColor.DARK_GRAY + "Gray",
                                Arrays.asList(ChatColor.GRAY + "Click to Select!")));
                        inv.setItem(15, new ItemCreator(Material.WOOL, 1, (byte) 15, ChatColor.DARK_GRAY + "Black",
                                Arrays.asList(ChatColor.GRAY + "Click to Select!")));
                        inv.setItem(16, new ItemCreator(Material.WOOL, 1, (byte) 12, ChatColor.DARK_GRAY + "Brown",
                                Arrays.asList(ChatColor.GRAY + "Click to Select!")));
                        inv.setItem(22, Creative.menuUtil.back);
                        player.openInventory(inv);
                        break;
                    }
                    case "Set Power": {
                        Inventory inv = Bukkit.createInventory(player, 27, ChatColor.BLUE + "Set Power");
                        inv.setItem(10, new ItemCreator(Material.FIREWORK, 0, ChatColor.GREEN + "Power 0",
                                Arrays.asList(ChatColor.GRAY + "Click to Select!")));
                        inv.setItem(12, new ItemCreator(Material.FIREWORK, 1, ChatColor.GREEN + "Power 1",
                                Arrays.asList(ChatColor.GRAY + "Click to Select!")));
                        inv.setItem(14, new ItemCreator(Material.FIREWORK, 2, ChatColor.GREEN + "Power 2",
                                Arrays.asList(ChatColor.GRAY + "Click to Select!")));
                        inv.setItem(16, new ItemCreator(Material.FIREWORK, 3, ChatColor.GREEN + "Power 3",
                                Arrays.asList(ChatColor.GRAY + "Click to Select!")));
                        inv.setItem(22, Creative.menuUtil.back);
                        player.openInventory(inv);
                        break;
                    }
                    case "Flicker": {
                        Show show = editSessions.get(player.getUniqueId());
                        int id = player.getMetadata("actionid").get(0).asInt();
                        FireworkAction action = (FireworkAction) show.actions.get(id);
                        action.getShowData().setFlicker(!action.isFlicker());
                        show.saveFile();
                        editAction(player, id);
                        break;
                    }
                    case "Trail": {
                        Show show = editSessions.get(player.getUniqueId());
                        int id = player.getMetadata("actionid").get(0).asInt();
                        FireworkAction action = (FireworkAction) show.actions.get(id);
                        action.getShowData().setTrail(!action.isTrail());
                        show.saveFile();
                        editAction(player, id);
                        break;
                    }
                }
                break;
            }
        }
    }

    public FireworkEffect.Type getType(String name) {
        switch (name.toLowerCase()) {
            case "ball":
                return FireworkEffect.Type.BALL;
            case "ball_large":
            case "large ball":
                return FireworkEffect.Type.BALL_LARGE;
            case "star":
                return FireworkEffect.Type.STAR;
            case "burst":
                return FireworkEffect.Type.BURST;
            case "creeper":
                return FireworkEffect.Type.CREEPER;
        }
        return FireworkEffect.Type.BALL;
    }

    private void editAction(Player player, int slot) {
        Show show = editSessions.get(player.getUniqueId());
        if (show.getActions().size() <= slot) {
            openAddAction(player);
            return;
        }
        ShowAction action = show.getActions().get(slot);
        ItemStack setTime = new ItemCreator(Material.WATCH, ChatColor.GREEN + "Set Time",
                Arrays.asList(ChatColor.YELLOW + "Time in seconds after start of", ChatColor.YELLOW +
                        "Show for an Action to execute."));
        if (action instanceof TextAction) {
            Inventory inv = Bukkit.createInventory(player, 27, ChatColor.BLUE + "Edit Text Action");
            inv.setItem(11, setTime);
            inv.setItem(15, new ItemCreator(Material.SIGN, ChatColor.GREEN + "Set Text",
                    Arrays.asList(ChatColor.YELLOW + "Supports Color Codes!")));
            inv.setItem(22, Creative.menuUtil.back);
            player.removeMetadata("actionid", Creative.getInstance());
            player.setMetadata("actionid", new FixedMetadataValue(Creative.getInstance(), slot));
            player.openInventory(inv);
        } else if (action instanceof ParticleAction) {
            Inventory inv = Bukkit.createInventory(player, 27, ChatColor.BLUE + "Edit Particle Action");
            inv.setItem(11, setTime);
            inv.setItem(15, new ItemCreator(Material.NETHER_STAR, ChatColor.GREEN + "Set Particle",
                    Arrays.asList(ChatColor.YELLOW + "Some Minecraft Particles are not allowed")));
            inv.setItem(22, Creative.menuUtil.back);
            player.removeMetadata("actionid", Creative.getInstance());
            player.setMetadata("actionid", new FixedMetadataValue(Creative.getInstance(), slot));
            player.openInventory(inv);
        } else if (action instanceof FireworkAction) {
            FireworkAction a = (FireworkAction) action;
            Inventory inv = Bukkit.createInventory(player, 27, ChatColor.BLUE + "Edit Firework Action");
            inv.setItem(10, setTime);
            inv.setItem(11, new ItemCreator(Material.FIREWORK_CHARGE, ChatColor.GREEN + "Select Type",
                    Arrays.asList(ChatColor.YELLOW + "Choose shape of the Firework!")));
            inv.setItem(12, new ItemCreator(Material.WOOL, 1, (byte) 3, ChatColor.GREEN + "Select Color",
                    Arrays.asList(ChatColor.YELLOW + "The first color of the Firework")));
            inv.setItem(13, new ItemCreator(Material.WOOL, 1, (byte) 14, ChatColor.GREEN + "Select Fade",
                    Arrays.asList(ChatColor.YELLOW + "The color the Firework fades to")));
            inv.setItem(14, new ItemCreator(Material.FIREWORK, 1, ChatColor.GREEN + "Set Power",
                    Arrays.asList(ChatColor.YELLOW + "The power of the Firework")));
            inv.setItem(15, new ItemCreator(Material.GLOWSTONE_DUST, 1, ChatColor.GREEN + "Flicker",
                    Arrays.asList(a.isFlicker() ? ChatColor.GREEN + "True" : ChatColor.RED + "False",
                            ChatColor.YELLOW + "Click to cycle options")));
            inv.setItem(16, new ItemCreator(Material.FEATHER, 1, ChatColor.GREEN + "Trail",
                    Arrays.asList(a.isTrail() ? ChatColor.GREEN + "True" : ChatColor.RED + "False",
                            ChatColor.YELLOW + "Click to cycle options")));
            inv.setItem(22, Creative.menuUtil.back);
            player.removeMetadata("actionid", Creative.getInstance());
            player.setMetadata("actionid", new FixedMetadataValue(Creative.getInstance(), slot));
            player.openInventory(inv);
        }
    }

    private void openAddAction(Player player) {
        Inventory inv = Bukkit.createInventory(player, 27, ChatColor.BLUE + "Add Action");
        ItemStack text = new ItemCreator(Material.SIGN, ChatColor.GREEN + "Text Action");
        ItemStack music = new ItemCreator(Material.RECORD_4, ChatColor.GREEN + "Set Music");
        ItemStack particle = new ItemCreator(Material.NETHER_STAR, ChatColor.GREEN + "Particle Action");
        ItemStack fw = new ItemCreator(Material.FIREWORK, ChatColor.GREEN + "Firework Action");
        inv.setItem(10, text);
        inv.setItem(12, music);
        inv.setItem(14, particle);
        inv.setItem(16, fw);
        inv.setItem(22, Creative.menuUtil.back);
        player.removeMetadata("actionid", Creative.getInstance());
        player.setMetadata("actionid", new FixedMetadataValue(Creative.getInstance(),
                editSessions.get(player.getUniqueId()).getActions().size()));
        player.openInventory(inv);
    }

    public boolean isEditing(UUID uuid) {
        return editSessions.containsKey(uuid);
    }

    public void handleChat(AsyncPlayerChatEvent event, Player player) {
        AddAction action = actions.remove(player.getUniqueId());
        if (action == null) {
            return;
        }
        switch (action.getAction()) {
            case TIME: {
                Show show = editSessions.get(player.getUniqueId());
                ShowAction act = show.actions.get(action.getId());
                try {
                    Double time = Double.parseDouble(event.getMessage());
                    if (time > 1200) {
                        messagePlayer(player, ChatColor.RED + "Shows cannot be longer than 20 Minutes!");
                        time = 1200.0;
                    }
                    try {
                        act.setTime(time);
                        show.saveFile();
                        cancelEdit(player, true);
                        messagePlayer(player, ChatColor.GREEN + "Set Time to " + ChatColor.YELLOW + time + "!");
                    } catch (Exception ignored) {
                        ignored.printStackTrace();
                        messagePlayer(player, ChatColor.RED + "There was an error! Please try again.");
                    }
                } catch (Exception ignored) {
                    show.actions.remove(action.getId());
                    messagePlayer(player, ChatColor.RED + event.getMessage() +
                            " is not a number! Please specify a number for the Action to execute at.");
                }
                break;
            }
            case TEXT: {
                try {
                    Show show = editSessions.get(player.getUniqueId());
                    TextAction act = (TextAction) show.actions.get(action.getId());
                    act.setText(event.getMessage());
                    show.saveFile();
                    cancelEdit(player, true);
                    messagePlayer(player, ChatColor.GREEN + "Set Text Message to " + ChatColor.YELLOW +
                            ChatColor.translateAlternateColorCodes('&', event.getMessage()) + ChatColor.YELLOW + "!");
                } catch (Exception ignored) {
                    ignored.printStackTrace();
                    messagePlayer(player, ChatColor.RED + "There was an error! Please try again.");
                }
                break;
            }
        }
    }

    public void setShowName(Player player, String name) {
        try {
            Show show = createSession(player);
            show.setName(name);
            show.saveFile();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public HashMap<String, AudioTrack> getAudioTracks() {
        return new HashMap<>(audioTracks);
    }

    public void logout(Player player) {
        Show show = shows.get(player.getUniqueId());
        if (show != null) {
            stopShow(player);
        }
    }

    public void syncMusic(Player player, Plot plot, Player owner) {
        for (Show s : shows.values()) {
            if (s.getOwner().equals(owner.getUniqueId())) {
                s.syncAudioForPlayer(player);
                break;
            }
        }
    }

    private class AddAction {
        private final Integer id;
        private final Action action;

        public AddAction(Integer id, Action action) {
            this.id = id;
            this.action = action;
        }

        public Integer getId() {
            return id;
        }

        public Action getAction() {
            return action;
        }
    }

    private enum Action {
        TIME, TEXT, POWER
    }
}