/*
 * This file is part of LUtils.
 *
 * LUtils is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This file is based on Meteor Client's IntSetting (GPL-3.0).
 */

package com.lutils.settings;

import meteordevelopment.meteorclient.settings.IVisible;
import meteordevelopment.meteorclient.settings.Setting;
import net.minecraft.nbt.NbtCompound;

import java.util.function.Consumer;

public class LongSetting extends Setting<Long> {
    public final long min, max;

    private LongSetting(String name, String description, long defaultValue, Consumer<Long> onChanged, Consumer<Setting<Long>> onModuleActivated, IVisible visible, long min, long max) {
        super(name, description, defaultValue, onChanged, onModuleActivated, visible);

        this.min = min;
        this.max = max;
    }

    @Override
    protected Long parseImpl(String str) {
        try {
            return Long.parseLong(str.trim());
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    @Override
    protected boolean isValueValid(Long value) {
        return value >= min && value <= max;
    }

    @Override
    public NbtCompound save(NbtCompound tag) {
        tag.putLong("value", get());

        return tag;
    }

    @Override
    public Long load(NbtCompound tag) {
        set(tag.getLong("value", 0L));

        return get();
    }

    public static class Builder extends SettingBuilder<Builder, Long, LongSetting> {
        private long min = Long.MIN_VALUE, max = Long.MAX_VALUE;

        public Builder() {
            super(0L);
        }

        public Builder min(long min) {
            this.min = min;
            return this;
        }

        public Builder max(long max) {
            this.max = max;
            return this;
        }

        public Builder range(long min, long max) {
            this.min = Math.min(min, max);
            this.max = Math.max(min, max);
            return this;
        }

        @Override
        public LongSetting build() {
            return new LongSetting(name, description, defaultValue, onChanged, onModuleActivated, visible, min, max);
        }
    }
}
