package com.jagrosh.jmusicbot.config;

import com.jagrosh.jmusicbot.config.diagnostics.ConfigDiagnostics;
import com.jagrosh.jmusicbot.config.io.ConfigIO;
import com.jagrosh.jmusicbot.config.loader.ConfigLoader;
import com.jagrosh.jmusicbot.config.model.ConfigOption;
import com.jagrosh.jmusicbot.config.model.ConfigUpdateType;
import com.jagrosh.jmusicbot.config.render.ConfigRenderer;
import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;
import java.nio.file.Files;
import java.nio.file.Path;

/** Offline release-JAR checks. No bot startup, credentials or network requests. */
public class YoutubeOAuthConfigCheck
{
    private static final String KEY = "playback.youtube.useOAuth";
    private static int checks;

    public static void main(String[] args) throws Exception
    {
        Config defaults = ConfigIO.loadDefaults();
        require(defaults.getBoolean(KEY), "runtime default enables OAuth");
        require(ConfigFactory.parseString(ConfigIO.loadDefaultConfig()).getBoolean(KEY), "new config template enables OAuth");
        require(ConfigFactory.parseString(ConfigIO.loadReferenceConfigAsDocument().render()).getBoolean(KEY), "document template enables OAuth");
        require(!ConfigOption.USE_YOUTUBE_OAUTH.isRequired(), "OAuth remains an optional config key, not a required setting");

        checkConfig("", true, defaults);
        checkConfig("meta.configVersion=1", true, defaults);
        checkConfig("meta.configVersion=1\n" + KEY + "=false", false, defaults);
        checkConfig("meta.configVersion=1\n" + KEY + "=true", true, defaults);
        checkConfig("useyoutubeoauth=false", false, defaults);
        checkConfig("useyoutubeoauth=true", true, defaults);
        checkConfig("prefix=\"!\"", true, defaults);

        // Exercise the same template substitution and file writer as first-time setup.
        String newConfig = ConfigIO.loadDefaultConfig()
            .replace("BOT_TOKEN_HERE", "offline-fixture-not-a-token")
            .replace("0 # OWNER ID", "123456789012345678").trim();
        Path temporaryConfig = Files.createTempFile("sonwmusicbot-oauth-config-", ".conf");
        try
        {
            ConfigIO.writeConfigFile(temporaryConfig, newConfig);
            Config written = ConfigFactory.parseFile(temporaryConfig.toFile());
            require(written.getBoolean(KEY), "new config written to disk enables OAuth");
            require(written.getString("discord.token").equals("offline-fixture-not-a-token"), "template token substitution preserved");
            require(written.getLong("discord.owner") == 123456789012345678L, "template owner substitution preserved");
            require(ConfigLoader.loadMergedConfig(temporaryConfig).getBoolean(KEY), "generated file loads with OAuth enabled");
        }
        finally { Files.deleteIfExists(temporaryConfig); }
        System.out.println("YOUTUBE OAUTH CONFIG OFFLINE PASS: " + checks + " assertions");
    }

    private static void checkConfig(String source, boolean expected, Config defaults)
    {
        Config migrated = ConfigLoader.loadMigratedUserConfig(ConfigFactory.parseString(source), defaults);
        Config merged = ConfigLoader.mergeWithDefaults(migrated, defaults);
        require(ConfigOption.USE_YOUTUBE_OAUTH.getBoolean(merged) == expected, "merged setting preserves explicit values or uses default");
        var report = ConfigDiagnostics.analyze(migrated, merged, defaults);
        for (ConfigUpdateType type : ConfigUpdateType.values())
        {
            String rendered = ConfigRenderer.generateConfigContent(migrated, report, type);
            require(ConfigFactory.parseString(rendered).getBoolean(KEY) == expected, "template rendering preserves setting: " + type);
            String fallback = ConfigRenderer.generateConfigContentFallback(migrated, report, type);
            require(ConfigFactory.parseString(fallback).getBoolean(KEY) == expected, "fallback rendering preserves setting: " + type);
        }
    }

    private static void require(boolean condition, String label)
    {
        if (!condition) throw new AssertionError(label);
        checks++;
    }
}
