package fr.leashall;

import cpw.mods.fml.common.Mod;
import cpw.mods.fml.common.event.FMLInitializationEvent;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import net.minecraft.entity.EntityLiving;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ChatComponentText;
import net.minecraft.world.World;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.entity.player.EntityInteractEvent;

import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;

@Mod(modid = "leashall", name = "Leash All", version = "1.1", acceptableRemoteVersions = "*")
public class LeashAll {

    // Duree pendant laquelle un crocodile nourri attend un partenaire (en ticks)
    private static final long LOVE_TICKS = 1200; // 60 s
    // Delai avant de pouvoir se reproduire a nouveau
    private static final long COOLDOWN_TICKS = 6000; // 5 min
    // Distance max entre les deux crocodiles
    private static final double RANGE = 12.0;

    private final Map<EntityLiving, Long> inLove = new WeakHashMap<EntityLiving, Long>();
    private final Map<EntityLiving, Long> cooldown = new WeakHashMap<EntityLiving, Long>();

    @Mod.EventHandler
    public void init(FMLInitializationEvent event) {
        MinecraftForge.EVENT_BUS.register(this);
    }

    private static boolean isLotrCrocodile(Object o) {
        String name = o.getClass().getName().toLowerCase();
        return name.contains("lotr") && name.contains("crocodile");
    }

    private static boolean isZebraMeat(ItemStack stack) {
        return stack.getUnlocalizedName().toLowerCase().contains("zebra");
    }

    @SubscribeEvent
    public void onInteract(EntityInteractEvent event) {
        EntityPlayer player = event.entityPlayer;
        if (player.worldObj.isRemote) return;
        ItemStack stack = player.getHeldItem();
        if (stack == null) return;
        if (!(event.target instanceof EntityLiving)) return;
        EntityLiving mob = (EntityLiving) event.target;

        // 1) Laisse sur tous les mobs
        if (stack.getItem() == Items.lead) {
            if (mob.getLeashed() || mob.allowLeashing()) return; // le jeu gere
            mob.setLeashedToEntity(player, true);
            if (!player.capabilities.isCreativeMode) stack.stackSize--;
            event.setCanceled(true);
            return;
        }

        // 2) Reproduction des crocodiles LOTR avec de la viande de zebre
        if (isLotrCrocodile(mob) && isZebraMeat(stack)) {
            World world = mob.worldObj;
            long now = world.getTotalWorldTime();

            Long cd = cooldown.get(mob);
            if (cd != null && cd > now) {
                player.addChatMessage(new ChatComponentText("Ce crocodile n'est pas pret a se reproduire."));
                event.setCanceled(true);
                return;
            }

            if (!player.capabilities.isCreativeMode) stack.stackSize--;
            event.setCanceled(true);

            // Cherche un autre crocodile deja nourri a proximite
            EntityLiving partner = null;
            List list = world.getEntitiesWithinAABB(EntityLiving.class, mob.boundingBox.expand(RANGE, 4.0, RANGE));
            for (Object o : list) {
                EntityLiving other = (EntityLiving) o;
                if (other == mob || !isLotrCrocodile(other)) continue;
                Long love = inLove.get(other);
                if (love != null && love > now) {
                    partner = other;
                    break;
                }
            }

            if (partner == null) {
                inLove.put(mob, now + LOVE_TICKS);
                player.addChatMessage(new ChatComponentText("Crocodile nourri. Nourris-en un deuxieme a proximite."));
                return;
            }

            // Naissance
            try {
                EntityLiving baby = mob.getClass().getConstructor(World.class).newInstance(world);
                baby.setLocationAndAngles(mob.posX, mob.posY, mob.posZ, world.rand.nextFloat() * 360F, 0F);
                world.spawnEntityInWorld(baby);
                player.addChatMessage(new ChatComponentText("Un bebe crocodile est ne !"));
            } catch (Exception e) {
                player.addChatMessage(new ChatComponentText("Reproduction impossible : " + e));
            }
            inLove.remove(mob);
            inLove.remove(partner);
            cooldown.put(mob, now + COOLDOWN_TICKS);
            cooldown.put(partner, now + COOLDOWN_TICKS);
        }
    }
}
