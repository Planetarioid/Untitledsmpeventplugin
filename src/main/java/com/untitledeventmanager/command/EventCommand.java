package com.untitledeventmanager.command;

import com.untitledeventmanager.UntitledEventManager;
import com.untitledeventmanager.gui.EventGui;
import com.untitledeventmanager.service.EventService;
import com.untitledeventmanager.util.EventRules;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.command.*;
import org.bukkit.entity.Player;

import java.util.*;
import java.util.stream.Collectors;

public final class EventCommand implements CommandExecutor, TabCompleter {
    private final UntitledEventManager plugin; private final EventService events; private final EventGui gui;
    public EventCommand(UntitledEventManager plugin, EventService events, EventGui gui) { this.plugin=plugin; this.events=events; this.gui=gui; }
    private void msg(CommandSender s, String m) { s.sendMessage(ChatColor.translateAlternateColorCodes('&', "&6[Event] &f" + m)); }
    private boolean allowed(CommandSender s, String perm) { if (plugin.permissions().has(s,perm)) return true; msg(s,"You do not have permission to do that."); return false; }
    private Player player(CommandSender s) { if (s instanceof Player p) return p; msg(s,"This command must be run in-game."); return null; }
    @Override public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length==0) { if (!allowed(sender,"eventmanager.gui")) return true; if (sender instanceof Player p) gui.home(p); else msg(sender,"Status: "+events.status()+" | Participants: "+events.participantCount()+"/"+events.capacity()); return true; }
        String sub=args[0].toLowerCase(Locale.ROOT);
        if (sub.equals("join")) { if (!allowed(sender,"eventmanager.join")) return true; Player p=player(sender); if(p!=null) msg(sender,events.join(p)); return true; }
        if (sub.equals("status")) { if(!allowed(sender,"eventmanager.status"))return true; if(args.length==1){msg(sender,"Current status: "+events.status());return true;} try{events.setStatus(EventService.Status.valueOf(args[1].toUpperCase(Locale.ROOT)));msg(sender,"Status set to "+args[1].toUpperCase(Locale.ROOT)+".");}catch(IllegalArgumentException ex){msg(sender,"Use OPEN, CLOSED, or ENDED.");}return true; }
        if (sub.equals("setspawn")||sub.equals("setlobby")) { if(!allowed(sender,"eventmanager.locations"))return true; Player p=player(sender);if(p!=null){if(sub.equals("setspawn"))events.setSpawn(p.getLocation());else events.setLobby(p.getLocation());msg(sender,sub.equals("setspawn")?"Event spawn saved.":"Lobby placeholder location saved.");}return true; }
        if (sub.equals("limit")) { if(!allowed(sender,"eventmanager.playerlimit"))return true;if(args.length!=2){msg(sender,"Usage: /event limit <positive number>");return true;}try{int n=Integer.parseInt(args[1]);if(n<1||n>plugin.getConfig().getInt("event.max-capacity",500))throw new NumberFormatException();events.setCapacity(n);msg(sender,"Capacity set to "+n+". Existing participants were not removed.");}catch(NumberFormatException ex){msg(sender,"Enter a positive whole number within the configured maximum.");}return true; }
        if(sub.equals("createkit")||sub.equals("updatekit")){String perm=sub.equals("createkit")?"eventmanager.kits.create":"eventmanager.kits.update";if(!allowed(sender,perm))return true;Player p=player(sender);if(p==null)return true;if(args.length!=2){msg(sender,"Usage: /event "+sub+" <name>");return true;}if(sub.equals("createkit")){boolean ok=events.createKit(args[1],p);msg(sender,ok?"Kit created.":"Invalid name or a kit with that name already exists.");}else{gui.confirmUpdate(p,args[1]);}return true;}
        if(sub.equals("deletekit")){if(!allowed(sender,"eventmanager.kits.delete"))return true;Player p=player(sender);if(p==null)return true;if(args.length!=2){msg(sender,"Usage: /event deletekit <name>");return true;}gui.confirmDelete(p,args[1]);return true;}
        if(sub.equals("givekit")){if(!allowed(sender,"eventmanager.kits.give"))return true;if(args.length!=3){msg(sender,"Usage: /event givekit <player> <kit>");return true;}Player target=Bukkit.getPlayerExact(args[1]);if(target==null){msg(sender,"That player is not online.");return true;}msg(sender,events.giveKit(target,args[2])?"Kit given to "+target.getName()+".":"That kit does not exist.");return true;}
        if(sub.equals("kits")){if(!allowed(sender,"eventmanager.kits"))return true;msg(sender,"Saved kits: "+(events.kitNames().isEmpty()?"none":String.join(", ",events.kitNames())));return true;}
        if(sub.equals("defaultkit")){if(!allowed(sender,"eventmanager.settings"))return true;if(args.length!=2){msg(sender,"Usage: /event defaultkit <name>");return true;}msg(sender,events.setDefaultKit(args[1])?"Default kit set.":"That kit does not exist.");return true;}
        if(sub.equals("random")){if(!allowed(sender,"eventmanager.settings"))return true;if(args.length==2&&args[1].equalsIgnoreCase("toggle")){events.setRandomEnabled(!events.randomEnabled());msg(sender,"Random kit assignment: "+events.randomEnabled());}else if(args.length==3&&args[1].equalsIgnoreCase("add")){events.addRandomKit(args[2]);msg(sender,"Added kit if it exists. Pool: "+String.join(", ",events.randomPool()));}else if(args.length==3&&args[1].equalsIgnoreCase("remove")){events.removeRandomKit(args[2]);msg(sender,"Removed kit from pool.");}else msg(sender,"Usage: /event random <toggle|add|remove> [kit]");return true;}
        if(sub.equals("participants")){if(!allowed(sender,"eventmanager.participants"))return true;if(sender instanceof Player p)gui.participants(p,0);else msg(sender,"Participants: "+events.participants().values().stream().collect(Collectors.joining(", ")));return true;}
        if(sub.equals("participantkit")){if(!allowed(sender,"eventmanager.participants"))return true;if(args.length!=3){msg(sender,"Usage: /event participantkit <player> <kit>");return true;}Player target=Bukkit.getPlayerExact(args[1]);if(target==null){msg(sender,"Player must be online.");return true;}if(!events.setParticipantKit(target.getUniqueId(),args[2])){msg(sender,"Player is not registered or kit does not exist.");return true;}events.giveKit(target,args[2]);msg(sender,"Participant kit changed and re-given.");return true;}
        if(sub.equals("regive")){if(!allowed(sender,"eventmanager.participants"))return true;if(args.length!=2){msg(sender,"Usage: /event regive <player>");return true;}Player target=Bukkit.getPlayerExact(args[1]);if(target==null){msg(sender,"Player must be online.");return true;}msg(sender,events.reGiveParticipantKit(target.getUniqueId())?"Assigned kit re-given.":"Player is not a participant.");return true;}
        if(sub.equals("remove")){if(!allowed(sender,"eventmanager.participants"))return true;if(args.length!=2){msg(sender,"Usage: /event remove <player>");return true;}Player target=Bukkit.getPlayerExact(args[1]);if(target==null){msg(sender,"Player must be online.");return true;}msg(sender,events.removeParticipant(target.getUniqueId())?"Participant unregistered. Inventory and Ender Chest were cleared.":"Player is not registered.");return true;}
        if(sub.equals("endcommand")){if(!allowed(sender,"eventmanager.endcommands"))return true;if(args.length<3){msg(sender,"Usage: /event endcommand <player|global> <add|remove|list> [index or command]");return true;}boolean per=args[1].equalsIgnoreCase("player");if(!per&&!args[1].equalsIgnoreCase("global")){msg(sender,"Use player or global.");return true;}String action=args[2].toLowerCase(Locale.ROOT);if(action.equals("list")){List<String> list=events.endCommands(per);for(int i=0;i<list.size();i++)msg(sender,i+": "+list.get(i));}else if(action.equals("add")&&args.length>=4){String value=String.join(" ",Arrays.copyOfRange(args,3,args.length));if(!EventRules.validEndCommand(value)){msg(sender,"Invalid command. Use supported placeholders and keep it under 256 characters.");return true;}events.addEndCommand(per,value);msg(sender,"End command added.");}else if(action.equals("remove")&&args.length==4){try{events.removeEndCommand(per,Integer.parseInt(args[3]));msg(sender,"End command removed if that index existed.");}catch(NumberFormatException ex){msg(sender,"Index must be a number.");}}else msg(sender,"Usage: /event endcommand <player|global> <add|remove|list> [index or command]");return true;}
        msg(sender,"Unknown subcommand. Use /event for the management GUI.");return true;
    }
    @Override public List<String> onTabComplete(CommandSender sender,Command command,String alias,String[] args){
        if(args.length==1)return List.of("join","status","setspawn","setlobby","limit","createkit","updatekit","deletekit","givekit","kits","defaultkit","random","participants","participantkit","regive","remove","endcommand").stream().filter(x->x.startsWith(args[0].toLowerCase(Locale.ROOT))).toList();
        if(args.length==2&&args[0].equalsIgnoreCase("status"))return List.of("OPEN","CLOSED","ENDED");
        if(args.length==2&&Set.of("updatekit","deletekit","defaultkit").contains(args[0].toLowerCase(Locale.ROOT)))return events.kitNames().stream().toList();
        if(args.length==2&&Set.of("givekit","participantkit","regive","remove").contains(args[0].toLowerCase(Locale.ROOT)))return Bukkit.getOnlinePlayers().stream().map(Player::getName).toList();
        if(args.length==3&&Set.of("givekit","participantkit").contains(args[0].toLowerCase(Locale.ROOT)))return events.kitNames().stream().toList();
        if(args.length==2&&args[0].equalsIgnoreCase("random"))return List.of("toggle","add","remove");
        if(args.length==2&&args[0].equalsIgnoreCase("endcommand"))return List.of("player","global");
        if(args.length==3&&args[0].equalsIgnoreCase("endcommand"))return List.of("add","remove","list");
        return Collections.emptyList();
    }
}
