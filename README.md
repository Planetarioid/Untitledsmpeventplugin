# UntitledEventManager

A Paper event-management plugin for **Minecraft 1.21.11 / Java 21**. It provides a single-event lifecycle, persistent kits, participant management, controlled inventory cleanup, optional LuckPerms permission checks, and configurable event-ending commands.

> **Build status:** This source archive has not been compiled against Paper in this environment because Maven and the Paper API dependency are unavailable here. The GitHub Actions workflow is configured to run the real Maven build. Do not install it on a live event server until that workflow succeeds and it has been smoke-tested on a staging server.

## Requirements

- Paper 1.21.11
- Java 21
- Maven 3.9+ to build locally
- LuckPerms 5.5 is optional

Paper API and LuckPerms API are `provided` dependencies. Neither is bundled into the plugin JAR.

## Build

From the project root:

```bash
mvn --batch-mode clean verify
```

The plugin JAR will be created at:

```text
target/UntitledEventManager.jar
```

GitHub Actions runs the same verification command on pushes, pull requests, and manual dispatches, then uploads the JAR as a workflow artifact if the build succeeds.

## Install and first setup

1. Build the project or download the JAR from a successful GitHub Actions run.
2. Place `UntitledEventManager.jar` in the Paper server's `plugins/` folder.
3. Start or restart the server.
4. Stand at the desired event destination and run `/event setspawn`.
5. Hold the complete desired kit and run `/event createkit default`.
6. Run `/event defaultkit default`.
7. Optionally stand at the lobby reference point and run `/event setlobby`.
8. Run `/event status OPEN` when ready to accept players.
9. Configure a FancyNPCs click action to execute `/event join` **as the clicking player**. No direct FancyNPCs API dependency is required; the command and NPC action use the same join handler.

The lobby is only a stored location for ending-command placeholders. The plugin never automatically teleports players to it.

## Commands

| Command | Permission | Purpose |
|---|---|---|
| `/event join` | `eventmanager.join` | Join the open event |
| `/event` | `eventmanager.gui` | Open the admin dashboard |
| `/event status` | `eventmanager.status` | Show the event state |
| `/event status <OPEN\|CLOSED\|ENDED>` | `eventmanager.status` | Change state; ENDED runs cleanup |
| `/event setspawn` | `eventmanager.locations` | Save event destination |
| `/event setlobby` | `eventmanager.locations` | Save lobby placeholders location |
| `/event limit <number>` | `eventmanager.playerlimit` | Set maximum participant count |
| `/event createkit <name>` | `eventmanager.kits.create` | Save your current inventory as a new kit |
| `/event updatekit <name>` | `eventmanager.kits.update` | Replace an existing kit with your current inventory |
| `/event deletekit <name>` | `eventmanager.kits.delete` | Delete a kit if it is not referenced |
| `/event givekit <player> <kit>` | `eventmanager.kits.give` | Replace an online player's inventory with a kit |
| `/event kits` | `eventmanager.kits` | List saved kits |
| `/event defaultkit <name>` | `eventmanager.settings` | Select the default kit |
| `/event random toggle` | `eventmanager.settings` | Enable or disable random assignment |
| `/event random add <kit>` | `eventmanager.settings` | Add a kit to the random pool |
| `/event random remove <kit>` | `eventmanager.settings` | Remove a kit from the random pool |
| `/event participants` | `eventmanager.participants` | Open participant management |
| `/event participantkit <player> <kit>` | `eventmanager.participants` | Change and re-give an online participant's kit |
| `/event regive <player>` | `eventmanager.participants` | Re-give the assigned kit to an online participant |
| `/event remove <player>` | `eventmanager.participants` | Remove and clean up a participant |
| `/event endcommand <player\|global> list` | `eventmanager.endcommands` | List ending commands |
| `/event endcommand <player\|global> add <command>` | `eventmanager.endcommands` | Add an ending console command |
| `/event endcommand <player\|global> remove <index>` | `eventmanager.endcommands` | Remove an ending command by its zero-based index |

Kit names must contain 1-32 letters, digits, underscores, or hyphens.

## Event lifecycle

There is one event with exactly three states: `OPEN`, `CLOSED`, and `ENDED`.

- **OPEN:** accepts new participants if all join checks pass.
- **CLOSED:** blocks new joins but keeps current participants registered.
- **ENDED:** snapshots the active participant UUIDs, performs cleanup for that snapshot, and preserves the event configuration and kit library for the next run.

After ending, an administrator can set the state to `OPEN` again. The participant registry is empty after a completed ending operation. A second ENDED request without reopening does not repeat the previous cleanup or ending commands.

## Joining and kit behavior

Joining checks the event state, duplicate registration, capacity, configured event spawn, and availability of a valid kit. A successful join registers the player's UUID and last-known username, assigns a kit, clears main inventory/armor/offhand, applies the kit, and teleports to the event spawn. The Ender Chest is not touched during joining.

Delivery-pending and teleport-pending markers are saved before applying the kit. If the server stops during joining, the next login retries the interrupted kit delivery and/or event-spawn teleport. Once each operation is marked complete, ordinary reconnects do not re-issue the kit, teleport the player again, or clear their inventory.

Kits preserve Bukkit-serializable ItemStack metadata, storage contents, armor, and offhand. The Ender Chest is never part of a kit. Kit management menus provide configurable (1-45 items per page) paginated browsing, an Anvil-based kit-creation input, left-click management, right-click read-only preview, player selection for giving kits, update confirmation, and delete confirmation. Participant kit selection is also paginated.

The preview shows the hotbar along the bottom row, main inventory above it, armor from boots through helmet, and offhand separately.

## Random assignment

The Event Kit Setup GUI lets administrators select the default kit and toggle random assignment. Kits are added to or removed from the random pool through each kit's management menu.

- Random mode disabled: all new participants receive the default kit.
- Random mode enabled with a nonempty pool: a persisted shuffled bag assigns pool kits without replacement until the bag is exhausted.
- Random mode enabled with an empty/invalid pool: the default kit is used as fallback; joining is rejected if no valid default exists.

Changing the pool resets the bag for future joins only. Existing participants keep their assigned kits.

## Participant removal and death

The participant GUI displays player heads, usernames, assigned kits, and online/offline status. Its participant menu supports viewing the assigned kit, changing it, re-giving it, and confirmed removal.

Manual removal clears main inventory, armor, offhand, and Ender Chest, then unregisters the participant. It does not teleport them.

On participant death, vanilla handles normal death and item drops. The plugin only clears that participant's Ender Chest and removes them from the active registry. It does not clear their main inventory, hotbar, armor, or offhand. Eliminated players are therefore excluded from a later ending snapshot.

## Event ending and offline cleanup

When the event ends, the plugin persists a snapshot of currently active participants before cleanup. It cleans only those UUIDs, never every online player. Online participants have main inventory, armor, offhand, and Ender Chest cleared. Offline participants are queued by UUID and cleaned on their next login before the join handler completes. A pending cleanup record is removed only after cleanup succeeds.

No automatic teleport, nickname reset, skin reset, permission removal, or inventory restoration is performed.

### Ending commands

Administrators can manage commands through the dashboard's **Ending commands** screen or `/event endcommand`.

- Per-player commands run once for every UUID in the ending snapshot, with `{player}` replaced by the participant's last-known username.
- Global commands run once per event ending.
- Lobby placeholders: `{lobby_world}`, `{lobby_x}`, `{lobby_y}`, `{lobby_z}`, `{lobby_yaw}`, `{lobby_pitch}`.
- Only these placeholders are accepted. Commands are limited to 256 characters and newline injection is rejected.
- If a command uses a lobby placeholder without a configured lobby location, that command is skipped and logged.
- Commands execute as console only during the ENDED transition.

**Crash semantics:** the command progress marker is persisted before dispatch. This gives at-most-once dispatch behavior, not guaranteed exactly-once execution. A crash after saving the marker but before dispatch can cause that command to be skipped. Arbitrary third-party commands cannot be made transactional by this plugin.

## Permissions and LuckPerms

Supported nodes:

- `eventmanager.admin`
- `eventmanager.gui`
- `eventmanager.join`
- `eventmanager.status`
- `eventmanager.settings`
- `eventmanager.playerlimit`
- `eventmanager.kits`
- `eventmanager.kits.create`
- `eventmanager.kits.update`
- `eventmanager.kits.delete`
- `eventmanager.kits.give`
- `eventmanager.participants`
- `eventmanager.endcommands`
- `eventmanager.locations`

All administrative nodes default to OP. `eventmanager.join` defaults to true. OPs receive administrative access. When LuckPerms is enabled, the plugin checks the LuckPerms 5.x public API and treats `eventmanager.admin` as an aggregate administrative grant. If LuckPerms is absent or its API cannot initialize, the plugin logs a warning and falls back to Bukkit permission checks. The plugin does not crash solely because LuckPerms is missing.

Non-OP players have `/event join` by default; administrative commands require their individual permission nodes or the aggregate admin node.

## Persistence and safety

`plugins/UntitledEventManager/data.yml` stores event status, capacity, locations, serialized kits, kit assignment settings, shuffled bag state, participant UUIDs/usernames/kit assignments, pending kit delivery, ending snapshots/progress, end commands, and pending offline cleanup.

The storage layer writes to a temporary file and attempts an atomic replacement. If an existing `data.yml` cannot be parsed, plugin initialization fails rather than silently replacing the file with defaults. Keep regular backups of the plugin data folder.

Menu inventories use a custom `InventoryHolder` identity tied to the viewing player's UUID, rather than relying on titles alone. Menu clicks, drags, hotbar swaps, offhand swaps, and drops are cancelled. Permissions and relevant state are checked again when menu actions execute.

## Project layout

```text
UntitledEventManager/
├── .github/workflows/build.yml
├── README.md
├── pom.xml
└── src/
    ├── main/
    │   ├── java/com/untitledeventmanager/
    │   │   ├── UntitledEventManager.java
    │   │   ├── command/EventCommand.java
    │   │   ├── gui/EventGui.java
    │   │   ├── gui/MenuHolder.java
    │   │   ├── listener/ParticipantListener.java
    │   │   ├── permission/PermissionService.java
    │   │   ├── service/EventService.java
    │   │   ├── storage/EventStorage.java
    │   │   └── util/EventRules.java
    │   └── resources/
    │       ├── config.yml
    │       └── plugin.yml
    └── test/java/com/untitledeventmanager/util/EventRulesTest.java
```

## Verification status

The pure validation rules were compiled and exercised with a local Java 21 smoke test. Java source parsing found no syntax diagnostics. However, this workspace does not contain Maven or cached Paper/LuckPerms API JARs, and outbound repository access is unavailable. Therefore a real `mvn clean verify`, JAR build, and live Paper 1.21.11 test have **not** been performed here. The included CI workflow is the next real dependency-resolution and compilation check. Treat the plugin as a development build until CI succeeds and the manual staging checklist below passes.

## Staging smoke-test checklist

1. Start Paper 1.21.11 on Java 21 and confirm clean plugin enable.
2. Start once without LuckPerms, then with LuckPerms 5.5; confirm both modes work.
3. Create a kit containing armor, offhand, hotbar, main inventory, enchantments, names, lore, and potion items; restart and verify the preview and issued kit.
4. Test OPEN/CLOSED/ENDED transitions, capacity boundary values, duplicate joins, and join from an NPC configured to run as the player.
5. Disconnect and reconnect a participant; verify no duplicate kit or inventory clearing.
6. Test manual removal, death handling, and event ending separately. Death must leave normal inventory/drop handling to vanilla.
7. End with both online and offline participants; reconnect offline participants and verify queued cleanup.
8. Configure player/global ending commands and lobby placeholders; verify each command runs according to its documented at-most-once semantics.
9. Test every GUI button, page control, confirmation, anvil input, drag, shift-click, number key, offhand swap, and drop attempt.
10. Restart during an ending operation on a disposable staging server and verify recovery.
