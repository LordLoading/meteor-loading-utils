package com.lutils.utils;

import net.fabricmc.loader.api.FabricLoader;

public class OtherModsUtils {
    public static boolean litematicaLoaded() {
        return isModLoaded("litematica");
    }

    public static boolean malilibLoaded() {
        return isModLoaded("malilib");
    }

    private static boolean isModLoaded(String modId) {
        return FabricLoader.getInstance().getModContainer(modId).isPresent();
    }
}
