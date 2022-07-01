/*
 * Copyright (c) 2018, SomeoneWithAnInternetConnection
 * Copyright (c) 2018, oplosthee <https://github.com/oplosthee>
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *
 * 1. Redistributions of source code must retain the above copyright notice, this
 *    list of conditions and the following disclaimer.
 * 2. Redistributions in binary form must reproduce the above copyright notice,
 *    this list of conditions and the following disclaimer in the documentation
 *    and/or other materials provided with the distribution.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND
 * ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED
 * WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
 * DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT OWNER OR CONTRIBUTORS BE LIABLE FOR
 * ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES
 * (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES;
 * LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND
 * ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
 * (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS
 * SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */
package net.runelite.client.plugins.ccWinterDaddy;

import com.google.inject.Provides;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.*;
import net.runelite.api.coords.LocalPoint;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.events.*;
import net.runelite.api.widgets.WidgetInfo;
import net.runelite.api.widgets.WidgetItem;
import net.runelite.api.widgets.Widget;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDependency;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.plugins.iutils.*;
import net.runelite.client.plugins.iutils.game.Game;
import net.runelite.client.plugins.iutils.game.iGroundItem;
import net.runelite.client.plugins.iutils.scripts.ReflectBreakHandler;
import net.runelite.client.plugins.iutils.iUtils;
import net.runelite.client.plugins.iutils.scripts.UtilsScript;
import net.runelite.client.plugins.iutils.util.LegacyInventoryAssistant;
import net.runelite.client.ui.overlay.OverlayManager;
import org.apache.commons.lang3.StringUtils;
import org.pf4j.Extension;
///iUtils
import net.runelite.client.plugins.iutils.*;
import net.runelite.client.plugins.iutils.ActionQueue;
import net.runelite.client.plugins.iutils.BankUtils;
import net.runelite.client.plugins.iutils.InventoryUtils;
import net.runelite.client.plugins.iutils.CalculationUtils;
import net.runelite.client.plugins.iutils.MenuUtils;
import net.runelite.client.plugins.iutils.MouseUtils;
import net.runelite.client.plugins.iutils.ObjectUtils;
import net.runelite.client.plugins.iutils.PlayerUtils;

import javax.inject.Inject;
import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ExecutorService;

import static net.runelite.api.MenuAction.WIDGET_TARGET_ON_WIDGET;
import static net.runelite.client.plugins.iutils.iUtils.iterating;
import static net.runelite.client.plugins.iutils.iUtils.sleep;


@Extension
@PluginDependency(iUtils.class)
@PluginDescriptor(
        name = "CCWinterDaddy Plugin",
        enabledByDefault = false,
        description = "CCWinterDaddy Plugin",
        tags = {"illumine", "combat", "ranged", "magic", "bot"}
)
@Slf4j
public class CCWinterDaddyPlugin extends Plugin {
    @Inject
    private CCWinterDaddyConfig config;

    @Inject
    private OverlayManager overlayManager;

    @Inject
    private CCWinterDaddyOverlay overlay;

    @Inject
    private iUtils utils;
    @Inject
    private Client client;
    @Inject
    private Game game;


    @Inject
    private MouseUtils mouse;

    @Inject
    private PlayerUtils playerUtils;

    @Inject
    private InventoryUtils inventory;

    @Inject
    private InterfaceUtils interfaceUtils;

    @Inject
    private CalculationUtils calc;
    @Inject
    private ObjectUtils object;
    @Inject
    private MenuUtils menu;

    @Inject
    private NPCUtils npc;

    @Inject
    private WalkUtils walk;

    @Inject
    private ConfigManager configManager;

    @Inject
    private ExecutorService executorService;

    @Inject
    private ReflectBreakHandler chinBreakHandler;

    @Inject
    private LegacyInventoryAssistant inventoryAssistant;

    NPC currentNPC;
    List<TileItem> loot = new ArrayList<>();
    List<TileItem> ammoLoot = new ArrayList<>();
    List<String> lootableItems = new ArrayList<>();
    List<Item> alchLoot = new ArrayList<>();
    Instant botTimer;
    Instant claimTimer;
    Instant newLoot;
    LegacyMenuEntry targetMenu;
    Player player;
    CCWinterDaddyState state;
    Instant lootTimer;
    LocalPoint beforeLoc = new LocalPoint(0, 0);
    WorldPoint startLoc;
    boolean startBot;
    boolean menuFight;
    String npcName;
    long sleepLength;
    int tickLength;
    int timeout;
    int killCount;
    boolean logsToBurn = false;
    boolean Burning = false;
    boolean Chopping = false;

    WorldPoint tutor = new WorldPoint(3225, 3237, 0);

    Tile runeDropTile = null;
    boolean claimed = false;
    @Provides
    CCWinterDaddyConfig provideConfig(ConfigManager configManager) {
        return configManager.getConfig(CCWinterDaddyConfig.class);
    }

    @Override
    protected void startUp() {
        chinBreakHandler.registerPlugin(this);
    }

    @Override
    protected void shutDown() {
        resetVals();
        chinBreakHandler.unregisterPlugin(this);
    }

    private void resetVals() {
        log.debug("stopping CCWinterDaddy Plugin");
        overlayManager.remove(overlay);
        menuFight = false;
        chinBreakHandler.stopPlugin(this);
        startBot = false;
        botTimer = null;
        claimTimer = null;
        newLoot = null;
        lootTimer = null;
        loot.clear();
        ammoLoot.clear();
        lootableItems.clear();
        alchLoot.clear();
        currentNPC = null;
        state = null;
    }

    private void start() {
        log.info("starting WD plugin");
        if (client == null || client.getLocalPlayer() == null || client.getGameState() != GameState.LOGGED_IN) {
            log.info("startup failed, log in before starting");
            return;
        }
        startBot = true;
        chinBreakHandler.startPlugin(this);
        timeout = 0;
        state = null;
        botTimer = Instant.now();
        claimTimer = Instant.now();
        overlayManager.add(overlay);
        updateConfigValues();
        beforeLoc = client.getLocalPlayer().getLocalLocation();
    }

    @Subscribe
    private void onConfigButtonPressed(ConfigButtonClicked configButtonClicked) {
        if (!configButtonClicked.getGroup().equalsIgnoreCase("CCWinterDaddy")) {
            return;
        }
        if (configButtonClicked.getKey().equals("startButton")) {
            if (!startBot) {
                start();
            } else {
                resetVals();
            }
        }
    }

    private void updateConfigValues() {
    }

    private long sleepDelay() {
        sleepLength = calc.randomDelay(config.sleepWeightedDistribution(), config.sleepMin(), config.sleepMax(), config.sleepDeviation(), config.sleepTarget());
        return calc.randomDelay(config.sleepWeightedDistribution(), config.sleepMin(), config.sleepMax(), config.sleepDeviation(), config.sleepTarget());
    }

    private int tickDelay() {
        tickLength = (int) calc.randomDelay(config.tickDelayWeightedDistribution(), config.tickDelayMin(), config.tickDelayMax(), config.tickDelayDeviation(), config.tickDelayTarget());
        log.debug("tick delay for {} ticks", tickLength);
        return tickLength;
    }

    private TileItem getNearestTileItem(List<TileItem> tileItems) {
        int currentDistance;
        TileItem closestTileItem = tileItems.get(0);
        int closestDistance = closestTileItem.getTile().getWorldLocation().distanceTo(player.getWorldLocation());
        for (TileItem tileItem : tileItems) {
            currentDistance = tileItem.getTile().getWorldLocation().distanceTo(player.getWorldLocation());
            if (currentDistance < closestDistance) {
                closestTileItem = tileItem;
                closestDistance = currentDistance;
            }
        }
        return closestTileItem;
    }


    private CCWinterDaddyState getState() {

        if (timeout > 0) {
            playerUtils.handleRun(20, 20);
            return CCWinterDaddyState.TIMEOUT;
        }
        if(player.getAnimation()!=-1){
            timeout=tickDelay();
            return CCWinterDaddyState.ANIMATING;
        }
        if (chinBreakHandler.shouldBreak(this)) {
            return CCWinterDaddyState.HANDLE_BREAK;
        }
        if (iterating) {
            return CCWinterDaddyState.ITERATING;
        }
        if (playerUtils.isMoving(beforeLoc)) {
            return CCWinterDaddyState.MOVING;
        }

        Duration timeSinceClaim = Duration.between(claimTimer, Instant.now());
        if (timeSinceClaim.toMinutes()>30){
            return CCWinterDaddyState.CLAIM_RUNES;
        }
        if (!inventory.isFull() && !Burning) {
            return CCWinterDaddyState.CHOP_TREE;
        }
        if (logsToBurn) {
            return  CCWinterDaddyState.BURN_LOGS;
        }
        return CCWinterDaddyState.TIMEOUT;
    }
    @Subscribe
    private void onGameTick(GameTick event) {
        if (!startBot || chinBreakHandler.isBreakActive(this)) {
            return;
        }
        player = client.getLocalPlayer();
        if (client != null && player != null && client.getGameState() == GameState.LOGGED_IN) {
            if (!client.isResized()) {
                utils.sendGameMessage("illu - client must be set to resizable");
                startBot = false;
                return;
            }
            state = getState();
            switch (state) {
                case TIMEOUT:
                    timeout--;
                    break;
                case ANIMATING:
                    break;
                case ITERATING:
                    break;
                case CLAIM_RUNES:
                    claim_runes();
                    break;
                case BURN_LOGS:
                    burn_logs();
                    break;
                case CHOP_TREE:
                    chop_tree();
                    break;
                case HANDLE_BREAK:
                    chinBreakHandler.startBreak(this);
                    timeout = 10;
                    break;
                case WAIT_COMBAT: {
                    new TimeoutUntil(
                            () -> playerUtils.isAnimating(),
                            () -> playerUtils.isMoving(),
                            3);
                    break;
                }
            }
            beforeLoc = player.getLocalLocation();
        }
    }

    private void claim_runes(){

        if (!claimed) {
            if (tutor.distanceTo(player.getWorldLocation()) > (1)) {
                walk.sceneWalk(tutor, 0, sleepDelay());
                new TimeoutUntil(
                        () -> tutor.distanceTo(player.getWorldLocation()) > (1),
                        () -> playerUtils.isMoving(),
                        5);
                return;
            }
            List<TileItem> loot = new ArrayList<>();

            if (inventory.containsItem(ItemID.AIR_RUNE) || inventory.containsItem(ItemID.MIND_RUNE)) {

                runeDropTile = WPtoTile(client.getLocalPlayer().getWorldLocation());
                Set<Integer> dropRunes = Set.of(ItemID.MIND_RUNE, ItemID.AIR_RUNE);
                inventory.dropItems(dropRunes, false, config.sleepMin(), config.sleepMax());
                return;
            }
        }
        if (!claimed) {
            game.npcs().withName("Magic combat tutor").withAction("Claim").nearest().interact("Claim");
            claimed = true;
            return;
        }
        if (claimed){
            List<TileItem> I = runeDropTile.getGroundItems();
            if (I != null) {
                TileItem lootItem = getNearestTileItem(I);
                targetMenu = new LegacyMenuEntry("", "", lootItem.getId(), MenuAction.GROUND_ITEM_THIRD_OPTION.getId(),
                        lootItem.getTile().getSceneLocation().getX(), lootItem.getTile().getSceneLocation().getY(), false);
                menu.setEntry(targetMenu);
                mouse.delayMouseClick(lootItem.getTile().getItemLayer().getCanvasTilePoly().getBounds(), sleepDelay());
            } else {
                claimTimer = Instant.now();
                claimed = false;
            }
        }
    }
    public Tile WPtoTile(WorldPoint worldPoint) {
        LocalPoint sourceLp = LocalPoint.fromWorld(client, worldPoint.getX(), worldPoint.getY());
        if (sourceLp == null) {
            return null;
        }

        int plane = worldPoint.getPlane();
        int thisX = sourceLp.getSceneX();
        int thisY = sourceLp.getSceneY();

        Tile[][][] tiles = client.getScene().getTiles();
        return tiles[plane][thisX][thisY];
    }
    private void setSelectedInventoryItem(Widget item) {
        client.setSelectedSpellWidget(WidgetInfo.INVENTORY.getId());
        client.setSelectedSpellChildIndex(item.getIndex());
        client.setSelectedSpellItemId(item.getItemId());
    }
    private Widget getInventoryItem(int id) {
        Widget inventoryWidget = client.getWidget(WidgetInfo.INVENTORY);
        Widget bankInventoryWidget = client.getWidget(WidgetInfo.BANK_INVENTORY_ITEMS_CONTAINER);
        if (inventoryWidget!=null && !inventoryWidget.isHidden())
        {
            return getWidgetItem(inventoryWidget,id);
        }
        if (bankInventoryWidget!=null && !bankInventoryWidget.isHidden())
        {
            return getWidgetItem(bankInventoryWidget,id);
        }
        return null;
    }
    private Widget getWidgetItem(Widget widget,int id) {
        for (Widget item : widget.getDynamicChildren())
        {
            if (item.getItemId() == id)
            {
                return item;
            }
        }
        return null;
    }
    private Widget burnable() {
        Widget rawM = getInventoryItem(ItemID.LOGS);

        Widget rawL = getInventoryItem(ItemID.LOGS);
        Widget rawOL = getInventoryItem(ItemID.OAK_LOGS);
        Widget rawWL = getInventoryItem(ItemID.WILLOW_LOGS);

        if (rawWL != null) {
            rawM = rawWL;
        }
        if (rawOL != null) {
            rawM = rawOL;
        }
        if (rawL != null) {
            rawM = rawL;
        }
        return rawM;
    }
    private void burn_logs() {

        Widget tool1 = getInventoryItem(590);
        Widget rawM = burnable();
        if(tool1 == null|| rawM == null){
            logsToBurn = false;
            Burning = false;
            System.out.println("OutOfLogs");
            if (tutor.distanceTo(player.getWorldLocation()) > (1)) {
                walk.sceneWalk(tutor, 0, sleepDelay());
                new TimeoutUntil(
                        () -> tutor.distanceTo(player.getWorldLocation()) > (1),
                        () -> playerUtils.isMoving(),
                        5);
                return;
            }
            return;
        }

        if (!Burning){
            if (tutor.distanceTo(player.getWorldLocation()) > (1)) {
                walk.sceneWalk(tutor, 0, sleepDelay());
                new TimeoutUntil(
                        () -> tutor.distanceTo(player.getWorldLocation()) > (1),
                        () -> playerUtils.isMoving(),
                        5);
                return;
            }
            Burning = true;
        }

        setSelectedInventoryItem(tool1);
        targetMenu = new LegacyMenuEntry("","",0,WIDGET_TARGET_ON_WIDGET,rawM.getIndex(),9764864,true);
        utils.doInvokeMsTime(targetMenu,sleepDelay());
        timeout +=2;
    }

    private void chop_tree() {
        log.info("IM CHOPPING WOOD");
        Chopping = true;
        GameObject tree = object.findNearestGameObject(1276, 1277, 1278);

        if (client.getBoostedSkillLevel(Skill.WOODCUTTING) <15){
            tree = object.findNearestGameObject(1276, 1277, 1278);
        }
        else if (client.getBoostedSkillLevel(Skill.WOODCUTTING) <30){
            log.info("CHOPPING OAKS");
            tree = object.findNearestGameObject(10820);
        }
        else if (client.getBoostedSkillLevel(Skill.WOODCUTTING) <95){
            log.info("CHOPPING WILLOW");
            tree = object.findNearestGameObject(10819);
        }

        utils.doGameObjectActionMsTime(tree, MenuAction.GAME_OBJECT_FIRST_OPTION.getId(), sleepDelay());
        logsToBurn = true;
        return;
    }

    @Subscribe
    public void onItemContainerChanged(ItemContainerChanged event) {
        if (!startBot || client.getLocalPlayer() == null || event.getContainerId() != InventoryID.INVENTORY.getId()) {
            return;
        }
        log.info("Processing inventory change");
        final ItemContainer inventoryContainer = client.getItemContainer(InventoryID.INVENTORY);
        if (inventoryContainer == null) {
            return;
        }
        List<Item> currentInventory = List.of(inventoryContainer.getItems());
    }

    @Subscribe
    private void onChatMessage(ChatMessage event) {
        if (startBot && (event.getType() == ChatMessageType.SPAM || event.getType() == ChatMessageType.GAMEMESSAGE)){
            if (event.getMessage().contains("can't light")){
                Burning = false;
                logsToBurn = true;
            }
        }
    }

    @Subscribe
    private void onGameStateChanged(GameStateChanged event) {
        if (!startBot || event.getGameState() != GameState.LOGGED_IN) {
            return;
        }
        log.debug("GameState changed to logged in, clearing loot and npc");
        loot.clear();
        ammoLoot.clear();
        alchLoot.clear();
        currentNPC = null;
        state = CCWinterDaddyState.TIMEOUT;
        timeout = 2;
    }

}
