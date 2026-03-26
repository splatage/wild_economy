package com.splatage.wild_economy.gui;

import java.net.MalformedURLException;
import java.net.URI;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;

public final class MenuHeadFactory {

    private static final Map<Character, String> TEXTURE_HASHES = Map.ofEntries(
            Map.entry('A', "4e41748121626f22ae16a4c664c7301a9f8ea591bf4d29888957682a9fdaf"),
            Map.entry('C', "62a5876113322f39aa2bbef4bd6b79ec6b52a97bb6fab674bddbd7b6eab3ba"),
            Map.entry('E', "1aeef88e2c928b466c6ed5deaa4e1975a9436c2b1b498f9f7cbf92a9b599a6"),
            Map.entry('G', "220c3b2bbfa1ed3ac8c35b3dd38247456563c92acefd5926b125ccc67d7d5fd"),
            Map.entry('H', "7ba9c33a95fa1e519f85a41ca56799384db41fe7e1d7a791751ece9bbae5d27f"),
            Map.entry('N', "da221e4f96bee6261752396a3265ffa4dedf8ff4839abd14f49edee1e53092"),
            Map.entry('O', "cbb1d17cebc5f0ecc987b80efc03e32ecb1cb40dbc5bce2faf3e60542a40"),
            Map.entry('R', "78a81efdae47bcb480a25ed91ff6de9772b07ae87c3c4e277705abbbd3419"),
            Map.entry('S', "d710138416528889815548b4623d28d86bbbae5619d69cd9dbc5ad6b43744"),
            Map.entry('T', "a3fb50fe7559bc99f13c47356cce97fda3aa923557fb5bfb17c825abf4b1d19"),
            Map.entry('X', "1d1a3c96562348527d5798f291609281f72e16d611f1a76c0fa7abe043665")
    );

    public void placeWord(final Inventory inventory, final int startSlot, final String word) {
        Objects.requireNonNull(inventory, "inventory");
        Objects.requireNonNull(word, "word");

        if (startSlot < 0 || startSlot + word.length() > 9) {
            throw new IllegalArgumentException(
                    "Title word '" + word + "' does not fit on the top row starting at slot " + startSlot
            );
        }

        for (int index = 0; index < word.length(); index++) {
            inventory.setItem(startSlot + index, this.letter(word.charAt(index)));
        }
    }

    public ItemStack letter(final char letter) {
        final char upper = Character.toUpperCase(letter);
        final String textureHash = TEXTURE_HASHES.get(upper);
        if (textureHash == null) {
            throw new IllegalArgumentException("Unsupported title letter: " + letter);
        }

        final ItemStack stack = new ItemStack(Material.PLAYER_HEAD);
        final ItemMeta rawMeta = stack.getItemMeta();
        if (!(rawMeta instanceof SkullMeta meta)) {
            return stack;
        }

        final UUID profileId = UUID.nameUUIDFromBytes(
                ("wild_economy:menu_head:" + upper).getBytes(StandardCharsets.UTF_8)
        );
        final var profile = Bukkit.createProfileExact(profileId, "we_title_" + upper);
        profile.getTextures().setSkin(this.textureUrl(textureHash));

        meta.setOwnerProfile(profile);
        meta.setDisplayName(" ");
        stack.setItemMeta(meta);
        return stack;
    }

    private URL textureUrl(final String textureHash) {
        try {
            return URI.create("http://textures.minecraft.net/texture/" + textureHash).toURL();
        } catch (final MalformedURLException exception) {
            throw new IllegalStateException("Invalid menu head texture URL for hash: " + textureHash, exception);
        }
    }
}
