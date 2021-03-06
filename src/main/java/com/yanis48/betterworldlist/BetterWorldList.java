package com.yanis48.betterworldlist;

import net.minecraftforge.fml.common.Mod;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

// The value here should match an entry in the META-INF/mods.toml file
@Mod("betterworldlist")
public final class BetterWorldList {
    // Directly reference a log4j logger.
    private static final Logger LOGGER = LogManager.getLogger();

    public BetterWorldList() {
        LOGGER.info("Starting BetterWorldList...");
    }
}
