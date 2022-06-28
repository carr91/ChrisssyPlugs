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
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDependency;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.plugins.iutils.*;
import net.runelite.client.plugins.iutils.game.Game;
import net.runelite.client.plugins.iutils.scripts.ReflectBreakHandler;
import net.runelite.client.plugins.iutils.iUtils;
import net.runelite.client.plugins.iutils.scripts.iScript;
import net.runelite.client.plugins.iutils.util.LegacyInventoryAssistant;
import net.runelite.client.ui.overlay.OverlayManager;
import org.apache.commons.lang3.StringUtils;
import org.pf4j.Extension;

import javax.inject.Inject;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ExecutorService;

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
        if (iterating) {
            return CCWinterDaddyState.ITERATING;
        }
        if (playerUtils.isMoving(beforeLoc)) {
            return CCWinterDaddyState.MOVING;
        }
        return CCWinterDaddyState.CLAIM_RUNES;
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
            log.info(String.valueOf(timeout));
            switch (state) {
                case TIMEOUT:
                    timeout--;
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
                case WAIT_COMBAT: {
                    new TimeoutUntil(
                            () -> playerUtils.isAnimating(),
                            () -> playerUtils.isMoving(),
                            3);
                }
                break;
                case IN_COMBAT:
                    timeout = tickDelay();
                    break;
                case HANDLE_BREAK:
                    chinBreakHandler.startBreak(this);
                    timeout = 10;
                    break;
                case LOG_OUT:
                    if (player.getInteracting() == null) {
                        interfaceUtils.logout();
                    } else {
                        timeout = 5;
                    }
                    shutDown();
                    break;
            }
            beforeLoc = player.getLocalLocation();
        }
    }

    private void claim_runes(){
        log.info("testing moving");
        WorldPoint tutor = new WorldPoint(3216,3238,0);
        walk.sceneWalk(tutor, 3, sleepDelay());

        if (tutor.distanceTo(player.getWorldLocation()) > (1)) {
            new TimeoutUntil(
                    () -> tutor.distanceTo(player.getWorldLocation()) > (1),
                    () -> playerUtils.isMoving(),
                    3);
            return;
        }
        List<TileItem> loot = new ArrayList<>();
        LocalPoint ploc = player.getLocalLocation();
        if (inventory.containsItem(ItemID.AIR_RUNE) || inventory.containsItem(ItemID.MIND_RUNE)){
            Set<Integer> dropRunes = Set.of(ItemID.AIR_RUNE,ItemID.MIND_RUNE);
            inventory.dropItems(dropRunes,false, config.sleepMin(), config.sleepMax());
        }

        //TALK TO TUTOR IN HERE

        lootAir();
        lootMind();
    }


    private void lootAir() {
            TileItem air = object.getGroundItem(ItemID.AIR_RUNE);
        if (air != null) {
            lootItem(air);
        }
    }
    private void lootMind() {
        TileItem mind = object.getGroundItem(ItemID.MIND_RUNE);
        if (mind != null) {
            lootItem(mind);
        }
    }
    private void lootItem(TileItem itemToLoot) {
        menu.setEntry(new LegacyMenuEntry("", "", itemToLoot.getId(), MenuAction.GROUND_ITEM_THIRD_OPTION.getId(), itemToLoot.getTile().getSceneLocation().getX(), itemToLoot.getTile().getSceneLocation().getY(), false));
        mouse.delayMouseClick(itemToLoot.getTile().getItemLayer().getCanvasTilePoly().getBounds(), sleepDelay()+90);
    }
    private void burn_logs() {
    }

    private void chop_tree() {
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
        if (startBot && (event.getType() == ChatMessageType.SPAM || event.getType() == ChatMessageType.GAMEMESSAGE)) {
            if (event.getMessage().contains("I'm already under attack") && event.getType() == ChatMessageType.SPAM) {
                log.debug("We already have a target. Waiting to auto-retaliate new target");
                //! If we are underattack, probably are not in safespot --> prioritize returning to safety
                return;
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
