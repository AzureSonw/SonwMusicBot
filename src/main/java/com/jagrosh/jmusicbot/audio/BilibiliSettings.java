package com.jagrosh.jmusicbot.audio;

import com.github.parrotxray.lavabili.plugin.BilibiliConfig;
import com.typesafe.config.Config;
import com.typesafe.config.ConfigException;

/** Optional, explicit local authentication. Does not inspect browsers or log credentials. */
public final class BilibiliSettings
{
    private BilibiliSettings() { }

    public static BilibiliConfig create(Config authConfig, int pageLimit)
    {
        BilibiliConfig config = new BilibiliConfig();
        config.setEnabled(true);
        config.setAllowSearch(true);
        config.setPlaylistPageCount(pageLimit);
        if (authConfig == null || !authConfig.hasPath("enabled") || !authConfig.getBoolean("enabled")) return config;

        var auth = config.getAuth();
        auth.setSessdata(cookie(authConfig, "sessdata"));
        if (auth.getSessdata().isEmpty()) throw invalid("sessdata", "启用 Bilibili 登录时必须填写 SESSDATA；请仅在本机配置。");
        auth.setBiliJct(cookie(authConfig, "biliJct"));
        auth.setDedeUserId(cookie(authConfig, "dedeUserId"));
        auth.setBuvid3(cookie(authConfig, "buvid3"));
        auth.setBuvid4(cookie(authConfig, "buvid4"));
        auth.setEnabled(true);
        // Do not enable upstream cookie refresh: it changes account state and logs refreshed secrets.
        return config;
    }

    private static String cookie(Config config, String key)
    {
        if (!config.hasPath(key)) return "";
        String value;
        try { value = config.getString(key).trim(); }
        catch (ConfigException e) { throw invalid(key, "Cookie 值必须是字符串（不会显示填写内容）。"); }
        if (value.indexOf(';') >= 0 || value.chars().anyMatch(c -> c < 32 || c == 127))
            throw invalid(key, "请只填写单个 Cookie 的值，不要粘贴整个 Cookie 请求头或换行。");
        return value;
    }

    private static ConfigException.BadValue invalid(String key, String message)
    {
        return new ConfigException.BadValue("playback.bilibili.auth." + key, message);
    }
}
