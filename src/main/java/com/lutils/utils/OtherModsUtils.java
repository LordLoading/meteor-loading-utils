package com.lutils.utils;

public class OtherModsUtils {
    public static boolean litematicaLoaded() {
        try {
            Class.forName("fi.dy.masa.litematica");
            return true;
        } catch (ClassNotFoundException e) {
            return false;
        }
    }

    public static boolean malilibLoaded() {
        try {
            Class.forName("fi.dy.masa.malilib");
            return true;
        } catch (ClassNotFoundException e) {
            return false;
        }
    }
}
