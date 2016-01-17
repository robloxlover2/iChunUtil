package me.ichun.mods.ichunutil.client.core.event;

import me.ichun.mods.ichunutil.client.keybind.KeyBind;
import me.ichun.mods.ichunutil.client.module.eula.WindowAnnoy;
import me.ichun.mods.ichunutil.client.module.patron.LayerPatronEffect;
import me.ichun.mods.ichunutil.client.render.RendererHelper;
import me.ichun.mods.ichunutil.client.render.item.ItemRenderingHelper;
import me.ichun.mods.ichunutil.common.core.config.ConfigBase;
import me.ichun.mods.ichunutil.common.core.config.ConfigHandler;
import me.ichun.mods.ichunutil.common.core.util.ObfHelper;
import me.ichun.mods.ichunutil.common.iChunUtil;
import me.ichun.mods.ichunutil.common.module.patron.PatronInfo;
import me.ichun.mods.ichunutil.common.packet.mod.PacketPatronInfo;
import net.minecraft.client.Minecraft;
import net.minecraft.client.audio.PositionedSoundRecord;
import net.minecraft.client.gui.GuiMainMenu;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.client.renderer.entity.RenderPlayer;
import net.minecraft.client.settings.KeyBinding;
import net.minecraft.client.shader.Framebuffer;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.client.event.GuiOpenEvent;
import net.minecraftforge.client.event.GuiScreenEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.common.Loader;
import net.minecraftforge.fml.common.eventhandler.EventPriority;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import net.minecraftforge.fml.common.network.FMLNetworkEvent;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;
import org.apache.commons.lang3.RandomStringUtils;
import org.lwjgl.input.Mouse;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.Random;

public class EventHandlerClient
{
    public boolean hasShownFirstGui;
    public boolean connectingToServer;

    public int ticks;
    public float renderTick;

    public int screenWidth;
    public int screenHeight;

    public ArrayList<KeyBind> keyBindList = new ArrayList<KeyBind>();
    public HashMap<KeyBinding, KeyBind> mcKeyBindList = new HashMap<KeyBinding, KeyBind>();

    //Module stuff
    //Ding module
    public boolean dingPlayedSound;

    //EULA module
    public boolean eulaDrawEulaNotice = !iChunUtil.config.eulaAcknowledged.equals(RandomStringUtils.random(20, 32, 127, false, false, null, (new Random(Math.abs(Minecraft.getMinecraft().getSession().getPlayerID().replaceAll("-", "").hashCode() + (Math.abs("iChunUtilEULA".hashCode()))))))) && !ObfHelper.obfuscated();
    public WindowAnnoy eulaWindow = new WindowAnnoy();

    //Patron module
    public boolean patronUpdateServerAsPatron;
    public ArrayList<PatronInfo> patrons = new ArrayList<PatronInfo>();
    //End Module Stuff

    public EventHandlerClient()
    {
        Minecraft mc = Minecraft.getMinecraft();
        screenWidth = mc.displayWidth;
        screenHeight = mc.displayHeight;
    }

    @SideOnly(Side.CLIENT)
    @SubscribeEvent
    public void onRendererSafeCompatibility(RendererSafeCompatibilityEvent event)
    {
        RenderPlayer renderPlayer = Minecraft.getMinecraft().getRenderManager().skinMap.get("default");
        renderPlayer.addLayer(new LayerPatronEffect(renderPlayer));
        renderPlayer = Minecraft.getMinecraft().getRenderManager().skinMap.get("slim");
        renderPlayer.addLayer(new LayerPatronEffect(renderPlayer));
    }

    @SubscribeEvent
    public void onRenderTick(TickEvent.RenderTickEvent event)
    {
        Minecraft mc = Minecraft.getMinecraft();

        renderTick = event.renderTickTime;
        if(event.phase == TickEvent.Phase.START)
        {
            if(screenWidth != mc.displayWidth || screenHeight != mc.displayHeight)
            {
                screenWidth = mc.displayWidth;
                screenHeight = mc.displayHeight;

                for(Framebuffer buffer : RendererHelper.frameBuffers)
                {
                    buffer.createBindFramebuffer(screenWidth, screenHeight);
                }
            }

            ItemRenderingHelper.handlePreRender(mc);
        }
        else
        {
            if(eulaDrawEulaNotice)
            {
                ScaledResolution reso = new ScaledResolution(mc);
                int i = Mouse.getX() * reso.getScaledWidth() / mc.displayWidth;
                int j = reso.getScaledHeight() - Mouse.getY() * reso.getScaledHeight() / mc.displayHeight - 1;

                eulaWindow.posX = reso.getScaledWidth() - eulaWindow.width;
                eulaWindow.posY = 0;
                if(eulaWindow.workspace.getFontRenderer() == null)
                {
                    eulaWindow.workspace.setWorldAndResolution(mc, mc.displayWidth, mc.displayHeight);
                    eulaWindow.workspace.initGui();
                }
                eulaWindow.draw(i - eulaWindow.posX, j - eulaWindow.posY);
                if(Mouse.isButtonDown(0))
                {
                    eulaWindow.onClick(i - eulaWindow.posX, j - eulaWindow.posY, 0);
                }
            }
        }
    }

    @SubscribeEvent
    public void onClientTick(TickEvent.ClientTickEvent event)
    {
        Minecraft mc = Minecraft.getMinecraft();
        if(event.phase.equals(TickEvent.Phase.END))
        {
            if(mc.theWorld != null)
            {
                if(connectingToServer)
                {
                    connectingToServer = false;
                    MinecraftForge.EVENT_BUS.post(new ServerPacketableEvent());
                }
                if(patronUpdateServerAsPatron)
                {
                    patronUpdateServerAsPatron = false;
                    iChunUtil.channel.sendToServer(new PacketPatronInfo(iChunUtil.proxy.getPlayerId(), iChunUtil.config.patronRewardType, iChunUtil.config.showPatronReward == 1));
                }
                for(KeyBind bind : keyBindList)
                {
                    bind.tick();
                }
                for(Map.Entry<KeyBinding, KeyBind> e : mcKeyBindList.entrySet())
                {
                    if(e.getValue().keyIndex != e.getKey().getKeyCode())
                    {
                        e.setValue(new KeyBind(e.getKey().getKeyCode()));
                    }
                    e.getValue().tick();
                }
            }
            ticks++;
        }
    }

    @SubscribeEvent
    public void onPlayerTick(TickEvent.PlayerTickEvent event)
    {
        if(event.side.isClient() && event.phase == TickEvent.Phase.END)
        {
            Minecraft mc = Minecraft.getMinecraft();

            ItemRenderingHelper.handlePlayerTick(mc, event.player);
        }
    }

    @SubscribeEvent
    public void onClientConnection(FMLNetworkEvent.ClientConnectedToServerEvent event)
    {
        connectingToServer = true;

        if(iChunUtil.userIsPatron)
        {
            patronUpdateServerAsPatron = true;
        }

        for(ConfigBase conf : ConfigHandler.configs)
        {
            conf.storeSession();
        }
    }

    @SubscribeEvent
    public void onClientDisconnect(FMLNetworkEvent.ClientDisconnectionFromServerEvent event)
    {
        patrons.clear();

        for(ConfigBase conf : ConfigHandler.configs)
        {
            conf.resetSession();
        }
    }

    @SubscribeEvent
    public void onGuiOpen(GuiOpenEvent event)
    {
        if(event.gui instanceof GuiMainMenu && !dingPlayedSound)
        {
            dingPlayedSound = true;
            if(iChunUtil.config.dingEnabled == 1 && !Loader.isModLoaded("Ding"))
            {
                Minecraft.getMinecraft().getSoundHandler().playSound(PositionedSoundRecord.create(new ResourceLocation(iChunUtil.config.dingSoundName), (iChunUtil.config.dingSoundPitch / 100F)));
            }
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public void onInitGuiPost(GuiScreenEvent.InitGuiEvent.Post event)
    {
        if(!hasShownFirstGui)
        {
            hasShownFirstGui = true;
            MinecraftForge.EVENT_BUS.post(new RendererSafeCompatibilityEvent());
        }
    }

    public PatronInfo getPatronInfo(EntityPlayer player)
    {
        for(PatronInfo info : patrons)
        {
            if(info.id.equalsIgnoreCase(player.getGameProfile().getId().toString().replaceAll("-", "")))
            {
                return info;
            }
        }
        return null;
    }
}