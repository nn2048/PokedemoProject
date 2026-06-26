package win.pokedemo;

import org.bukkit.inventory.ItemStack;
import org.bukkit.util.io.BukkitObjectInputStream;
import org.bukkit.util.io.BukkitObjectOutputStream;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.ArrayList;
import java.util.List;

/**
 * Serialize/deserialize ItemStacks for storing loot in a chest TileState PDC.
 */
public final class PokeChestStorage {
    private PokeChestStorage() {}

    public static byte[] serializeItemStacks(List<ItemStack> stacks) {
        try {
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            BukkitObjectOutputStream out = new BukkitObjectOutputStream(baos);
            out.writeInt(stacks.size());
            for (ItemStack s : stacks) {
                out.writeObject(s);
            }
            out.close();
            return baos.toByteArray();
        } catch (Exception e) {
            return new byte[0];
        }
    }

    public static List<ItemStack> deserializeItemStacks(byte[] bytes) {
        List<ItemStack> out = new ArrayList<>();
        if (bytes == null || bytes.length == 0) return out;
        try {
            ByteArrayInputStream bais = new ByteArrayInputStream(bytes);
            BukkitObjectInputStream in = new BukkitObjectInputStream(bais);
            int n = in.readInt();
            for (int i = 0; i < n; i++) {
                Object o = in.readObject();
                if (o instanceof ItemStack s && s.getType() != null) {
                    out.add(s);
                }
            }
            in.close();
        } catch (Exception ignored) {}
        return out;
    }
}
