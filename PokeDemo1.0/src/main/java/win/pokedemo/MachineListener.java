package win.pokedemo;

import org.bukkit.Instrument;
import org.bukkit.Material;
import org.bukkit.Note;
import org.bukkit.block.Block;
import org.bukkit.Location;
import org.bukkit.Bukkit;
import org.bukkit.block.data.type.NoteBlock;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPhysicsEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * Minimal overworld machines system.
 * - Place via special items (pc_machine / healer_machine)
 * - Convert into NOTE_BLOCK with a unique blockstate
 * - Right-click runs /pc or /heal
 * - Break returns the machine item
 */
public class MachineListener implements Listener {

    private final JavaPlugin plugin;
    private final ItemFactory items;
    private final ItemRegistry itemRegistry;
    private final LangManager lang;
    private final MachineRegistry machineRegistry;

    public MachineListener(JavaPlugin plugin, ItemFactory items, ItemRegistry itemRegistry, LangManager lang, MachineRegistry machineRegistry) {
        this.plugin = plugin;
        this.items = items;
        this.itemRegistry = itemRegistry;
        this.lang = lang;
        this.machineRegistry = machineRegistry;
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.HIGHEST)
    public void onPlace(BlockPlaceEvent e) {
        ItemStack hand = e.getItemInHand();
        String itemId = items.getItemId(hand);
        MachineType type = MachineType.fromId(itemId);
        if (type == null) return;

        // IMPORTANT:
        // Do NOT cancel the place event, otherwise the client spams "can't place".
        // Let the head place normally, then replace the placed block with our NOTE_BLOCK machine next tick.
        Block placed = e.getBlockPlaced();
        Player p = e.getPlayer();
        plugin.getServer().getScheduler().runTask(plugin, () -> {
            if (placed.getType() == Material.AIR) return;

            if (type == MachineType.PC) {
                // Two-block-tall PC: lower + invisible upper.
                Block lower = placed;
                Block upper = placed.getRelative(0, 1, 0);

                // If there is no space above, refuse placement (revert) and return item.
                if (!upper.getType().isAir()) {
                    lower.setType(Material.AIR, false);
                    ItemDef def = itemRegistry.get("pc_machine");
                    if (def != null) {
                        ItemStack give = items.createItem(def, lang, 1);
                        lower.getWorld().dropItemNaturally(lower.getLocation().add(0.5, 0.5, 0.5), give);
                    }
                    p.sendMessage(lang.ui("machine.pc.no_space", "§c上方空间不足，无法放置PC。\n§7请清空上方一格再放置。"));
                    return;
                }

                setPcLowerNoteBlock(lower);
                setPcUpperNoteBlock(upper);
                machineRegistry.put(lower.getLocation(), type);
                machineRegistry.put(upper.getLocation(), type);
            } else {
                if (type == MachineType.HEALER) {
                    setHealerNoteBlock(placed);
                } else {
                    setPastureNoteBlock(placed);
                }
                machineRegistry.put(placed.getLocation(), type);
            }
        });

        if (type == MachineType.PC) p.sendMessage(lang.ui("machine.pc.placed", "§b放置了PC"));
        else if (type == MachineType.HEALER) p.sendMessage(lang.ui("machine.healer.placed", "§a放置了治疗仪"));
        else p.sendMessage(lang.ui("machine.pasture.placed", "§d放置了精灵牧场"));
    }

    @EventHandler(ignoreCancelled = true)
    public void onInteract(PlayerInteractEvent e) {
        if (e.getAction() == null) return;

        // Avoid double-firing for offhand interactions (common cause of duplicated messages).
        if (e.getHand() != null && e.getHand() != EquipmentSlot.HAND) return;

        // Right click handling: either interact with an existing machine, or place a machine from the held item.
        if (e.getAction() != Action.RIGHT_CLICK_BLOCK) return;
        if (e.getClickedBlock() == null) return;

        Player p = e.getPlayer();
        ItemStack hand = p.getInventory().getItemInMainHand();
        String itemId = items.getItemId(hand);
        MachineType heldType = MachineType.fromId(itemId);

        // Migration/robustness: older machine items may have outdated CustomModelData,
        // which makes them render as TM/HM. If we recognize the machine by our PDC id,
        // refresh the model data in-place.
        if (heldType != null) {
            ItemDef def = itemRegistry.get(itemId);
            if (def != null) {
                items.ensureModelData(hand, def.customModelData);
            }
        }

        Block clicked = e.getClickedBlock();

        // 2a) Interact with an existing machine.
        // IMPORTANT: datapack-generated machines (e.g. healer placed in a village structure)
        // are not registered via BlockPlaceEvent, so we must also recognize machines by NOTE_BLOCK state.
        if (clicked.getType() == Material.NOTE_BLOCK) {
            MachineType type = machineRegistry.get(clicked.getLocation());
            if (type == null) {
                type = inferMachineTypeByBlockState(clicked);
                if (type != null) {
                    // Lazily register so break/physics handling will work from now on.
                    machineRegistry.put(clicked.getLocation(), type);
                }
            }

            if (type != null) {
                // Prevent vanilla NOTE_BLOCK tuning (right-click changes the note), which would
                // make the block "morph" into a different custom block in our resource pack.
                e.setCancelled(true);
                // Also force-resend the block state next tick to avoid any client-side desync flicker.
                Bukkit.getScheduler().runTask(plugin, () -> {
                    if (clicked.getType() == Material.NOTE_BLOCK) {
                        p.sendBlockChange(clicked.getLocation(), clicked.getBlockData());
                    }
                });

                if (type == MachineType.PC) {
                    p.performCommand("pc");
                } else if (type == MachineType.HEALER) {
                    // Healer machine should work for normal players (no admin heal permission).
                    if (plugin instanceof PokeDemoPlugin pd) {
                        int healed = pd.healParty(p, true);
                        if (healed > 0) p.sendMessage(lang.ui("machine.healer.done", "§a治疗机已恢复你的队伍：回满血、清除异常并恢复PP。"));
                    } else {
                        p.performCommand("heal");
                    }
                } else if (type == MachineType.PASTURE) {
                    // Pasture UI (breeding ranch)
                    if (plugin instanceof PokeDemoPlugin pd) {
                        if (!pd.getConfig().getBoolean("breeding.pasture.enabled", true)) {
                            p.sendMessage(lang.ui("machine.pasture.disabled", "§c精灵牧场已被管理员禁用。"));
                            return;
                        }
                        PastureGui.open(p, pd, clicked.getLocation());
                    }
                } else if (type == MachineType.FOSSIL) {
                    if (plugin instanceof PokeDemoPlugin pd) {
                        FossilGui.open(p, pd, clicked.getLocation());
                    }
                } else if (type == MachineType.FOSSIL_ANALYZER) {
                    if (plugin instanceof PokeDemoPlugin pd) {
                        FossilAnalyzerGui.open(p, pd, clicked.getLocation());
                    }
                } else if (type == MachineType.TRADE) {
                    if (plugin instanceof PokeDemoPlugin pd) {
                        TradeGui.open(p, pd, clicked.getLocation());
                    } else {
                        p.sendMessage(lang.ui("machine.trade.plugin_bad", "§e宝可梦交换机：§c插件实例异常"));
                    }
                } else if (type == MachineType.CLONE) {
                    if (plugin instanceof PokeDemoPlugin pd) {
                        CloneGui.open(p, pd, clicked.getLocation());
                    }
                }
                return;
            }
        }

        // 2b) Place a machine (PAPER carrier items are not placeable via BlockPlaceEvent).
        if (heldType == null) return;

        Block target = clicked.getRelative(e.getBlockFace());
        if (!target.getType().isAir()) {
            p.sendMessage(lang.ui("machine.common.cannot_place", "§c这里不能放置。"));
            return;
        }

        e.setCancelled(true);

        if (heldType == MachineType.PC) {
            Block lower = target;
            Block upper = target.getRelative(0, 1, 0);
            if (!upper.getType().isAir()) {
                p.sendMessage(lang.ui("machine.pc.no_space", "§c上方空间不足，无法放置PC。\n§7请清空上方一格再放置。"));
                return;
            }
            setPcLowerNoteBlock(lower);
            setPcUpperNoteBlock(upper);
            machineRegistry.put(lower.getLocation(), heldType);
            machineRegistry.put(upper.getLocation(), heldType);
        } else {
            if (heldType == MachineType.HEALER) setHealerNoteBlock(target);
            else if (heldType == MachineType.PASTURE) setPastureNoteBlock(target);
            else if (heldType == MachineType.FOSSIL) setFossilNoteBlock(target);
            else if (heldType == MachineType.FOSSIL_ANALYZER) setFossilAnalyzerNoteBlock(target);
            else if (heldType == MachineType.TRADE) setTradeNoteBlock(target);
            else setCloneNoteBlock(target);
            machineRegistry.put(target.getLocation(), heldType);
        }

        // consume 1 in survival/adventure
        if (p.getGameMode() != org.bukkit.GameMode.CREATIVE) {
            if (hand.getAmount() <= 1) p.getInventory().setItemInMainHand(null);
            else hand.setAmount(hand.getAmount() - 1);
        }

        if (heldType == MachineType.PC) p.sendMessage(lang.ui("machine.pc.placed", "§b放置了PC"));
        else if (heldType == MachineType.HEALER) p.sendMessage(lang.ui("machine.healer.placed", "§a放置了治疗仪"));
        else if (heldType == MachineType.PASTURE) p.sendMessage(lang.ui("machine.pasture.placed", "§d放置了精灵牧场"));
        else if (heldType == MachineType.FOSSIL) p.sendMessage(lang.ui("machine.fossil.placed", "§6放置了化石复活机"));
        else if (heldType == MachineType.FOSSIL_ANALYZER) p.sendMessage(lang.ui("machine.analyzer.placed", "§6放置了化石解析仪"));
        else if (heldType == MachineType.TRADE) p.sendMessage(lang.ui("machine.trade.placed", "§e放置了宝可梦交换机"));
        else p.sendMessage(lang.ui("machine.clone.placed", "§d放置了宝可梦克隆仪"));
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.HIGHEST)
    public void onBreak(BlockBreakEvent e) {
        Block b = e.getBlock();
        if (b.getType() != Material.NOTE_BLOCK) return;
        MachineType type = machineRegistry.get(b.getLocation());
        if (type == null) {
            type = inferMachineTypeByBlockState(b);
            if (type != null) {
                // Register lazily so future interactions are consistent.
                machineRegistry.put(b.getLocation(), type);
            } else {
                return;
            }
        }

        e.setDropItems(false);
        if (type == MachineType.PC) {
            // Breaking either half removes both halves and drops one item.
            // IMPORTANT: PCs generated by datapacks won't be in machineRegistry.
            // Use block-state inference to find the other half so we don't dupe drops.
            Block lower = b;
            Block below = b.getRelative(0, -1, 0);
            if (isPcNoteBlock(below)) {
                lower = below;
            }
            Block upper = lower.getRelative(0, 1, 0);

            machineRegistry.remove(lower.getLocation());
            machineRegistry.remove(upper.getLocation());

            // Remove both halves if present.
            if (lower.getType() == Material.NOTE_BLOCK) lower.setType(Material.AIR, false);
            if (isPcNoteBlock(upper)) upper.setType(Material.AIR, false);

            ItemDef def = itemRegistry.get("pc_machine");
            if (def != null) {
                ItemStack drop = items.createItem(def, lang, 1);
                lower.getWorld().dropItemNaturally(lower.getLocation().add(0.5, 0.5, 0.5), drop);
            }
            e.getPlayer().sendMessage(lang.ui("machine.pc.broken", "§b拆除了PC"));
            return;
        }

        if (type == MachineType.FOSSIL) {
            machineRegistry.remove(b.getLocation());
            b.setType(Material.AIR, false);

            // Clear internal state (do NOT drop stored fossil/fuel).
            if (plugin instanceof PokeDemoPlugin pd) {
                FossilMachineManager fm = pd.getFossilMachineManager();
                if (fm != null) fm.onMachineBroken(b.getLocation());
            }

            ItemDef def = itemRegistry.get("fossil_machine");
            if (def != null) {
                ItemStack drop = items.createItem(def, lang, 1);
                b.getWorld().dropItemNaturally(b.getLocation().add(0.5, 0.5, 0.5), drop);
            }
            return;
        }

        if (type == MachineType.FOSSIL_ANALYZER) {
            machineRegistry.remove(b.getLocation());
            b.setType(Material.AIR, false);

            if (plugin instanceof PokeDemoPlugin pd) {
                FossilAnalyzerManager am = pd.getFossilAnalyzerManager();
                if (am != null) am.onBroken(b.getLocation());
            }

            ItemDef def = itemRegistry.get("fossil_analyzer");
            if (def != null) {
                ItemStack drop = items.createItem(def, lang, 1);
                b.getWorld().dropItemNaturally(b.getLocation().add(0.5, 0.5, 0.5), drop);
            }
            return;
        }

        if (type == MachineType.TRADE || type == MachineType.CLONE) {
            machineRegistry.remove(b.getLocation());
            b.setType(Material.AIR, false);

            if (type == MachineType.TRADE && plugin instanceof PokeDemoPlugin pd) {
                TradeManager tm = pd.getTradeManager();
                if (tm != null) tm.onMachineBroken(b.getLocation());
            }

            if (type == MachineType.CLONE && plugin instanceof PokeDemoPlugin pd) {
                CloneManager cm = pd.getCloneManager();
                if (cm != null) cm.onMachineBroken(b.getLocation());
            }

            String id = (type == MachineType.TRADE) ? "trade_machine" : "clone_machine";
            ItemDef def = itemRegistry.get(id);
            if (def != null) {
                ItemStack drop = items.createItem(def, lang, 1);
                b.getWorld().dropItemNaturally(b.getLocation().add(0.5, 0.5, 0.5), drop);
            }
            return;
        }

        if (type == MachineType.HEALER) {
            // healer: break removes the machine but does NOT drop an item (to avoid free infinite healing).
            machineRegistry.remove(b.getLocation());
            b.setType(Material.AIR, false);
            e.getPlayer().sendMessage(lang.ui("machine.healer.broken", "§a拆除了治疗仪"));
            return;
        }

        // pasture: break removes the machine and drops an item (breeding facility).
        machineRegistry.remove(b.getLocation());
        if (plugin instanceof PokeDemoPlugin pd) {
            PastureManager pm = pd.getPastureManager();
            if (pm != null) pm.onPastureBroken(b.getLocation());
        }
        b.setType(Material.AIR, false);
        ItemDef def = itemRegistry.get("pasture_machine");
        if (def != null) {
            ItemStack drop = items.createItem(def, lang, 1);
            b.getWorld().dropItemNaturally(b.getLocation().add(0.5, 0.5, 0.5), drop);
        }
        e.getPlayer().sendMessage(lang.ui("machine.pasture.broken", "§d拆除了精灵牧场"));
    }

    
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPhysics(BlockPhysicsEvent event) {
        Block b = event.getBlock();
        if (b.getType() != Material.NOTE_BLOCK) return;

        MachineType type = machineRegistry.get(b.getLocation());
        if (type == null) return;

        // NOTE: NoteBlock instrument/note can change due to neighbor updates.
        // We lock the machine's blockstate back to our expected values on next tick.
        Bukkit.getScheduler().runTask(plugin, () -> {
            // block might have been broken already
            if (b.getType() != Material.NOTE_BLOCK) return;

            if (type == MachineType.PC) {
                Location loc = b.getLocation();
                MachineType above = machineRegistry.get(loc.clone().add(0, 1, 0));
                MachineType below = machineRegistry.get(loc.clone().add(0, -1, 0));

                if (above == MachineType.PC) {
                    // this is lower half
                    setPcLowerNoteBlock(b);
                } else if (below == MachineType.PC) {
                    // this is upper half
                    setPcUpperNoteBlock(b);
                } else {
                    // fallback (single PC block)
                    setPcLowerNoteBlock(b);
                }
            } else if (type == MachineType.HEALER) {
                setHealerNoteBlock(b);
            } else if (type == MachineType.PASTURE) {
                setPastureNoteBlock(b);
            } else if (type == MachineType.FOSSIL) {
                setFossilNoteBlock(b);
            } else if (type == MachineType.FOSSIL_ANALYZER) {
                setFossilAnalyzerNoteBlock(b);
            } else if (type == MachineType.TRADE) {
                setTradeNoteBlock(b);
            } else if (type == MachineType.CLONE) {
                setCloneNoteBlock(b);
            }
        });
    }

/** PC lower: this one gets the visible model. */
    private void setPcLowerNoteBlock(Block b) {
        b.setType(Material.NOTE_BLOCK, false);
        NoteBlock nb = (NoteBlock) b.getBlockData();
        // Placeholder instrument/state; we can change to deepslate-like and spawner-like later.
        nb.setInstrument(Instrument.PLING);
        nb.setNote(new Note(7));
        // "powered" is tied to redstone and may flip back automatically if not receiving power.
        // Keep it false so the blockstate stays stable.
        nb.setPowered(false);
        b.setBlockData(nb, false);
    }

    /** PC upper: this one is an invisible/blank model in the resource pack. */
    private void setPcUpperNoteBlock(Block b) {
        b.setType(Material.NOTE_BLOCK, false);
        NoteBlock nb = (NoteBlock) b.getBlockData();
        nb.setInstrument(Instrument.PLING);
        nb.setNote(new Note(8));
        nb.setPowered(false);
        b.setBlockData(nb, false);
    }

    private void setHealerNoteBlock(Block b) {
        b.setType(Material.NOTE_BLOCK, false);
        NoteBlock nb = (NoteBlock) b.getBlockData();
        // Use PLING + a different note to avoid version-specific instrument names.
        nb.setInstrument(Instrument.PLING);
        nb.setNote(new Note(13));
        nb.setPowered(false);
        b.setBlockData(nb, false);
    }

    private void setPastureNoteBlock(Block b) {
        b.setType(Material.NOTE_BLOCK, false);
        NoteBlock nb = (NoteBlock) b.getBlockData();
        nb.setInstrument(Instrument.PLING);
        nb.setNote(new Note(14));
        nb.setPowered(false);
        b.setBlockData(nb, false);
    }

    private void setFossilNoteBlock(Block b) {
        b.setType(Material.NOTE_BLOCK, false);
        if (!(b.getBlockData() instanceof org.bukkit.block.data.type.NoteBlock nb)) return;
        // Use a very uncommon state to reduce conflicts with other custom-block packs.
        nb.setInstrument(org.bukkit.Instrument.IRON_XYLOPHONE);
        nb.setNote(new org.bukkit.Note(24));
        nb.setPowered(false);
        b.setBlockData(nb, false);
    }

    private void setFossilAnalyzerNoteBlock(Block b) {
        b.setType(Material.NOTE_BLOCK, false);
        if (!(b.getBlockData() instanceof org.bukkit.block.data.type.NoteBlock nb)) return;
        // Another uncommon state (different instrument) for the analyzer.
        nb.setInstrument(org.bukkit.Instrument.BELL);
        nb.setNote(new org.bukkit.Note(24));
        nb.setPowered(false);
        b.setBlockData(nb, false);
    }

    private void setTradeNoteBlock(Block b) {
        b.setType(Material.NOTE_BLOCK, false);
        if (!(b.getBlockData() instanceof org.bukkit.block.data.type.NoteBlock nb)) return;
        nb.setInstrument(org.bukkit.Instrument.PLING);
        nb.setNote(new org.bukkit.Note(23));
        nb.setPowered(false);
        b.setBlockData(nb, false);
    }

    private void setCloneNoteBlock(Block b) {
        b.setType(Material.NOTE_BLOCK, false);
        if (!(b.getBlockData() instanceof org.bukkit.block.data.type.NoteBlock nb)) return;
        nb.setInstrument(org.bukkit.Instrument.PLING);
        nb.setNote(new org.bukkit.Note(22));
        nb.setPowered(false);
        b.setBlockData(nb, false);
    }

    /**
     * Infer machine type purely from NOTE_BLOCK blockstate.
     * This is required for datapack-generated structures where machines are placed without going through
     * BlockPlaceEvent (thus no entry in MachineRegistry).
     */
    private MachineType inferMachineTypeByBlockState(Block b) {
        if (b.getType() != Material.NOTE_BLOCK) return null;
        if (!(b.getBlockData() instanceof org.bukkit.block.data.type.NoteBlock nb)) return null;

        org.bukkit.Instrument ins = nb.getInstrument();
        int note = nb.getNote().getId(); // 0-24

        // PC lower/upper (two notes)
        if (ins == org.bukkit.Instrument.PLING && (note == 7 || note == 8)) return MachineType.PC;

        // Healer / Pasture
        if (ins == org.bukkit.Instrument.PLING && note == 13) return MachineType.HEALER;
        if (ins == org.bukkit.Instrument.PLING && note == 14) return MachineType.PASTURE;

        // Fossil machines (use uncommon instruments at note 24)
        if (ins == org.bukkit.Instrument.IRON_XYLOPHONE && note == 24) return MachineType.FOSSIL;
        if (ins == org.bukkit.Instrument.BELL && note == 24) return MachineType.FOSSIL_ANALYZER;

        // Trade / Clone
        if (ins == org.bukkit.Instrument.PLING && note == 23) return MachineType.TRADE;
        if (ins == org.bukkit.Instrument.PLING && note == 22) return MachineType.CLONE;

        return null;
    }

    private boolean isPcNoteBlock(Block b) {
        if (b == null || b.getType() != Material.NOTE_BLOCK) return false;
        return inferMachineTypeByBlockState(b) == MachineType.PC;
    }
}
