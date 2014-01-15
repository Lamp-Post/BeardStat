package com.tehbeard.beardstat.utils;

import net.dragonzone.promise.Promise;

import org.bukkit.block.Block;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Skeleton;
import org.bukkit.entity.Zombie;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffect;

import com.tehbeard.beardstat.BeardStat;
import com.tehbeard.beardstat.containers.EntityStatBlob;
import com.tehbeard.beardstat.dataproviders.identifier.IdentifierService;
import com.tehbeard.beardstat.listeners.defer.DelegateDecrement;
import com.tehbeard.beardstat.listeners.defer.DelegateIncrement;
import com.tehbeard.beardstat.manager.EntityStatManager;

/**
 * Provides helper methods for recording stats
 * @author James
 *
 */
@SuppressWarnings("deprecation")
public class StatUtils {

    private static EntityStatManager manager = null;

    public static void setManager(EntityStatManager manager){
        StatUtils.manager = manager;
    }
    
    public static void statPotion(Player player,String category,PotionEffect effect, int amount){
        statPlayer(player, category, IdentifierService.getIdForPotionEffect(effect), amount);
    }

    public static void statEntity(Player player, String category, Entity entity, int amount){
        statPlayer(player, category, IdentifierService.getIdForEntity(entity), amount);
        //TODO -  Deprecate or add api handle?
        if (entity instanceof Skeleton) {
            statPlayer(player, category, ((Skeleton) entity).getSkeletonType().toString().toLowerCase()+ "_skeleton",amount);
        }

        if (entity instanceof Zombie) {
            if (((Zombie) entity).isVillager()) {
                statPlayer(player, category, "villager_zombie", amount);
            }
        }
    }

    public static void statPlayer(Player player,String category, String statistic, int amount){
        modifyStat(
                player.getName(), 
                BeardStat.DEFAULT_DOMAIN, 
                player.getWorld().getName(), 
                category, 
                statistic,
                null, 
                amount);
    }

    public static void statItem(Player player,  String category, ItemStack item, int amount){
        statItem(player.getName(),
                BeardStat.DEFAULT_DOMAIN,
                player.getWorld().getName(),
                category,
                item,
                amount);
    }

    /**
     * Helper method 
     * @param player
     * @param domain
     * @param world
     * @param category
     * @param item
     * @param amount
     */
    public static void statItem(String player,String domain, String world, String category, ItemStack item, int amount){
        String baseId = IdentifierService.getIdForItemStack(item);
        String metaId = IdentifierService.getIdForItemStackWithMeta(item);
        modifyStat(player, domain, world, category, baseId, metaId, amount);
    }

    /**
     * Quick helper method for blocks
     * @param player
     * @param category
     * @param block
     * @param amount
     */
    public static void statBlock(Player player, String category, Block block, int amount){
        statBlock(player.getName(),
                BeardStat.DEFAULT_DOMAIN,
                player.getWorld().getName(),
                category,
                block,
                amount);
    }

    /**
     * Helper method for block stats
     * @param player
     * @param domain
     * @param world
     * @param category
     * @param block
     * @param amount
     */

    public static void statBlock(String player,String domain, String world, String category, Block block, int amount){
        String baseId = IdentifierService.getIdForMaterial(block.getType());
        String metaId = IdentifierService.getIdForMaterial(block.getType(),block.getData());
        modifyStat(player, domain, world, category, baseId, metaId, amount);
    }


    public static void modifyStat(String player,String domain, String world, String category, String baseId, String metaId, int amount){
        boolean inc = (amount > 0);
        int am = Math.abs(amount);

        if(inc){
            increment(player, domain, world, category, baseId, am);
            if(metaId != null){
                increment(player, domain, world, category, metaId, am);
            }
        }
        else
        {
            decrement(player, domain, world, category, baseId, am);
            if(metaId != null){
                decrement(player, domain, world, category, metaId, am);
            }
        }
    }

    public static void increment(String player,String domain, String world, String category, String statistic, int amount){
        Promise<EntityStatBlob> blob = manager.getOrCreatePlayerStatBlob(player);
        blob.onResolve(new DelegateIncrement(domain,world,category,statistic,amount));
    }

    public static void decrement(String player,String domain, String world, String category, String statistic, int amount){
        Promise<EntityStatBlob> blob = manager.getOrCreatePlayerStatBlob(player);
        blob.onResolve(new DelegateDecrement(domain,world,category,statistic,amount));
    }

}