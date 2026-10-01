package de.jeff_media.chestsort.handlers;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import de.jeff_media.chestsort.ChestSortPlugin;
import org.bukkit.entity.Player;
import org.bukkit.permissions.PermissionAttachment;

/**
 * Grants the basic permissions when use-permissions is false. Methods must run on the player's thread.
 */
public class ChestSortPermissionsHandler {
	
	private final Map<UUID,PermissionAttachment> permissions = new ConcurrentHashMap<>();
	private final ChestSortPlugin plugin;
	
	public ChestSortPermissionsHandler(ChestSortPlugin plugin) {
		this.plugin = plugin;
	}
	
	public void addPermissions(Player p) {
		if(plugin.getConfig().getBoolean("use-permissions")) return;
		if(permissions.containsKey(p.getUniqueId())) return;
		PermissionAttachment attachment = p.addAttachment(plugin);
		attachment.setPermission("chestsort.use", true);
		attachment.setPermission("chestsort.use.inventory", true);
		permissions.put(p.getUniqueId(), attachment);
	}
	
	public void removePermissions(Player p) {
		// Remove regardless of the current config, which may have changed since the attachment was added
		PermissionAttachment attachment = permissions.remove(p.getUniqueId());
		if(attachment == null) return;
		attachment.unsetPermission("chestsort.use");
		attachment.unsetPermission("chestsort.use.inventory");
		p.removeAttachment(attachment);
	}

}
