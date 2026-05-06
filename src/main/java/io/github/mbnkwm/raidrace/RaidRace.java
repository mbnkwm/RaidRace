package io.github.mbnkwm.raidrace;

import io.github.mbnkwm.raidrace.event.ContainerEvents;
import com.mojang.brigadier.context.CommandContext;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandManager;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.*;
import net.minecraft.network.chat.contents.PlainTextContents;
import net.minecraft.util.Util;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import org.jspecify.annotations.NonNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public class RaidRace implements ClientModInitializer, ContainerEvents.CloseEvent,
        ContainerEvents.SetContentEvent, ClientReceiveMessageEvents.Game {
    public static final Logger LOGGER = LoggerFactory.getLogger(RaidRace.class);

    private static final String REWARD_CONTAINER_TITLE = "󏿪";
    private static final TextColor MYTHIC_ASPECT_COLOUR = TextColor.fromRgb(0xAA00AA);
    private static final int REOPEN_THRESHOLD = 2500;
    private static final int FIRST_REWARD_SLOT = 27;
    private static final int LAST_REWARD_SLOT = 53;
    private static final int FIRST_ASPECT_SLOT = 11;
    private static final int LAST_ASPECT_SLOT = 15;

    private static final Map<String, BlockPos> RAID_CHESTS = Map.of(
            "Nest of the Grootslangs", new BlockPos(10342, 41, 3111),
            "Orphion's Nexus of Light", new BlockPos(11005, 58, 2909),
            "The Canyon Colossus", new BlockPos(10817, 45, 3901),
            "The Nameless Anomaly", new BlockPos(24489, 8, -23878),
            "The Wartorn Palace", new BlockPos(-19066, 125, -1821)
    );
    private static final double CHEST_RANGE = 50;

    private Path dataPath;
    private long lastClosedAt = -1;
    private int currentRewardContainerId = -1;
    private int lastMatchedAspectPulls = -1;
    private int lastMatchedRewardPulls = -1;
    private int sessionPulls;

    @Override
    public void onInitializeClient() {
        dataPath = FabricLoader.getInstance().getGameDir().resolve("wynn-analytics").resolve("raid-race.csv");

        try {
            setupDataFile();
        } catch (IOException e) {
            LOGGER.error("Could not create the storage file for the raid race data!", e);
        }

        ContainerEvents.CLOSE.register(this);
        ContainerEvents.SET_CONTENT.register(this);
        ClientReceiveMessageEvents.GAME.register(this);
        ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) -> dispatcher.register(
                ClientCommandManager.literal("raidrace")
                        .then(ClientCommandManager.literal("pulls").executes(this::pullsCommand))
                        .then(ClientCommandManager.literal("file").executes(this::fileCommand))
                        .executes(this::helpCommand)));
    }

    @Override
    public void onContainerClosed(int containerId) {
        if (containerId == currentRewardContainerId) {
            lastClosedAt = System.currentTimeMillis();
        }

        currentRewardContainerId = -1;
    }

    @Override
    public void onSetContent(@NonNull AbstractContainerMenu menu, @NonNull List<ItemStack> items) {
        Minecraft mc = Minecraft.getInstance();

        if (mc.screen != null && mc.screen.getTitle().getString().equals(REWARD_CONTAINER_TITLE)) {
            long timestamp = System.currentTimeMillis();

            // we only want the initial contents, not every update when players move items around
            if (menu.containerId == currentRewardContainerId) {
                return;
            }

            // you can press E or Escape to briefly close it before wynn forces it open again with a new id
            if (timestamp - lastClosedAt <= REOPEN_THRESHOLD) {
                LOGGER.warn("Raid rewards reopened too quickly ({}ms) after previous one closing, skipping.",
                        timestamp - lastClosedAt);

                currentRewardContainerId = menu.containerId;

                return;
            }

            Optional<String> raid = matchRaid(mc.player.blockPosition());

            if (raid.isEmpty()) {
                LOGGER.error("Unmatched chest with player at {}", mc.player.blockPosition());

                mc.player.displayClientMessage(Component.translatable("text.raid-race.error.unmatched-chest"), false);

                return;
            }

            currentRewardContainerId = menu.containerId;

            StringBuilder builder = new StringBuilder();

            builder.append(raid.get()).append(",")
                    .append(timestamp).append(",")
                    .append(lastMatchedAspectPulls).append(",");

            int mythicAspectCount = 0;

            for (int i = FIRST_ASPECT_SLOT; i <= LAST_ASPECT_SLOT; i++) {
                ItemStack aspect = items.get(i);

                if (!aspect.isEmpty() && aspect.has(DataComponents.LORE)
                        && MYTHIC_ASPECT_COLOUR.equals(aspect.get(DataComponents.LORE).lines().getLast().getSiblings()
                                .getFirst().getStyle().getColor())) {
                    mythicAspectCount++;
                }
            }

            builder.append(mythicAspectCount).append(",")
                    .append(lastMatchedRewardPulls).append(",");

            for (int i = FIRST_REWARD_SLOT; i <= LAST_REWARD_SLOT; i++) {
                ItemStack item = items.get(i);

                builder.append(cleanupName(item.getHoverName().getString())).append(",")
                        .append(item.getCount());

                if (i == LAST_REWARD_SLOT) {
                    builder.append(System.lineSeparator());
                } else {
                    builder.append(",");
                }
            }


            try {
                setupDataFile();

                Files.writeString(dataPath, builder, StandardOpenOption.APPEND);

                sessionPulls += lastMatchedRewardPulls;

                LOGGER.info("Saved raid reward data to file at: {}", dataPath.toAbsolutePath());

                mc.player.displayClientMessage(Component.translatable("text.raid-race.pulls-logged",
                        lastMatchedRewardPulls, sessionPulls), false);
            } catch (IOException e) {
                LOGGER.error("Could not write raid reward data to file!", e);

                mc.player.displayClientMessage(Component.translatable("text.raid-race.error.write",
                        lastMatchedRewardPulls, sessionPulls), false);
            }
        }
    }

    @Override
    public void onReceiveGameMessage(@NonNull Component message, boolean overlay) {
        if (overlay) {
            return;
        }

        String joined = message.getString();

        if (joined.startsWith("§7") && joined.endsWith("Pulls") && message.getSiblings().size() > 1) {
            Component last = message.getSiblings().getLast();

            if (!last.getSiblings().isEmpty()
                    && last.getSiblings().getFirst().getContents() instanceof PlainTextContents contents) {
                if (joined.endsWith("Reward Pulls")) {
                    lastMatchedRewardPulls = Integer.parseInt(contents.text());
                } else if (joined.endsWith("Aspect Pulls")) {
                    lastMatchedAspectPulls = Integer.parseInt(contents.text());
                }
            }
        }
    }

    private int helpCommand(CommandContext<FabricClientCommandSource> context) {
        context.getSource().sendFeedback(Component.literal("/raidrace <")
                .append(Component.literal("pulls").withStyle(Style.EMPTY.withUnderlined(true)
                        .withClickEvent(new ClickEvent.SuggestCommand("/raidrace pulls"))
                        .withHoverEvent(new HoverEvent.ShowText(Component.translatable("text.raid-race.help.pulls")))))
                .append(Component.literal(" | "))
                .append(Component.literal("file").withStyle(Style.EMPTY.withUnderlined(true)
                        .withClickEvent(new ClickEvent.SuggestCommand("/raidrace file"))
                        .withHoverEvent(new HoverEvent.ShowText(Component.translatable("text.raid-race.help.file")))))
                .append(">"));

        return 1;
    }

    private int fileCommand(CommandContext<FabricClientCommandSource> context) {
        Util.getPlatform().openFile(dataPath.getParent().toFile());

        return 1;
    }

    private int pullsCommand(CommandContext<FabricClientCommandSource> context) {
        int total = getTotalPulls();

        if (total == -1) {
            context.getSource().sendError(Component.translatable("text.raid-race.error.read"));

            return 0;
        }

        context.getSource().sendFeedback(Component.translatable("text.raid-race.pulls-progress", total, sessionPulls));

        return 1;
    }

    private int getTotalPulls() {
        try {
            setupDataFile();

            List<String> lines = Files.readAllLines(dataPath);

            if (lines.size() <= 1) {
                return 0;
            }

            lines = lines.subList(1, lines.size());

            return lines.stream()
                    .map(s -> s.split(",")[4])
                    .mapToInt(Integer::parseInt)
                    .sum();
        } catch (IOException e) {
            LOGGER.error("Could not read the storage file for the raid race data!", e);

            return -1;
        }
    }

    private String cleanupName(String itemName) {
        return itemName.replace("\uDAFC\uDC00\uE008\uDB00\uDC02", "") // unid icon
                .replace("\uDAFC\uDC00", "") // suffix
                .replace("\uE000 ", "") // the element icons on powders
                .replace("\uE001 ", "")
                .replace("\uE002 ", "")
                .replace("\uE003 ", "")
                .replace("\uE004 ", "");
    }

    private void setupDataFile() throws IOException {
        if (Files.notExists(dataPath)) {
            Files.createDirectories(dataPath.getParent());
            Files.createFile(dataPath);

            StringBuilder header = new StringBuilder("Raid,Timestamp,Total Aspect Pulls,Mythic Aspects Pulled,Total Reward Pulls,");

            for (int i = 1; i <= 27; i++) {
                header.append("Pull ").append(i).append(" Item,Pull ").append(i).append(" Quantity");

                if (i != 27) {
                    header.append(',');
                }
            }

            header.append(System.lineSeparator());

            Files.writeString(dataPath, header.toString());
        }
    }

    private Optional<String> matchRaid(BlockPos playerPosition) {
        for (Map.Entry<String, BlockPos> entry : RAID_CHESTS.entrySet()) {
            if (playerPosition.closerThan(entry.getValue(), CHEST_RANGE)) {
                return Optional.of(entry.getKey());
            }
        }

        return Optional.empty();
    }
}
