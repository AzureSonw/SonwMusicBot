package com.jagrosh.jmusicbot.audio;

import com.sedmelluq.discord.lavaplayer.tools.JsonBrowser;
import java.net.URI;
import java.util.*;

/** Select one AAC representation, retaining only its API-provided, signed CDN URLs. */
final class BilibiliPlaybackUrls
{
    static final int MAX_URLS = 4;

    static List<URI> video(JsonBrowser response)
    {
        JsonBrowser data = BilibiliErrors.data(response);
        List<JsonBrowser> audio = new ArrayList<>(data.get("dash").get("audio").values());
        audio.removeIf(a -> !a.get("codecs").safeText().toLowerCase(Locale.ROOT).startsWith("mp4a."));
        audio.sort(Comparator.comparingLong((JsonBrowser a) -> a.get("bandwidth").asLong(0)).reversed());
        for (JsonBrowser representation : audio)
        {
            LinkedHashSet<URI> urls = new LinkedHashSet<>();
            add(urls, representation.get("baseUrl").text());
            add(urls, representation.get("base_url").text());
            for (String field : List.of("backupUrl", "backup_url"))
                for (JsonBrowser url : representation.get(field).values()) add(urls, url.text());
            if (!urls.isEmpty()) return List.copyOf(urls);
        }
        throw BilibiliErrors.problem("Bilibili 没有返回可播放的 AAC 音频地址，内容可能需要登录或暂不支持。");
    }

    static List<URI> audio(JsonBrowser response)
    {
        LinkedHashSet<URI> urls = new LinkedHashSet<>();
        for (JsonBrowser url : BilibiliErrors.data(response).get("cdns").values()) add(urls, url.text());
        if (urls.isEmpty()) throw BilibiliErrors.problem("Bilibili 没有返回可用的音频地址。");
        return List.copyOf(urls);
    }

    private static void add(Set<URI> urls, String value)
    {
        if (urls.size() >= MAX_URLS || value == null) return;
        try
        {
            URI uri = URI.create(value);
            String host = uri.getHost() == null ? "" : uri.getHost().toLowerCase(Locale.ROOT);
            if (!"https".equalsIgnoreCase(uri.getScheme()) || uri.getRawUserInfo() != null
                || uri.getPort() != -1 || uri.getRawFragment() != null) return;
            if (host.endsWith(".bilivideo.com") || host.endsWith(".bilivideo.cn")
                || host.equals("upos-hz-mirrorakam.akamaized.net")) urls.add(uri);
        }
        catch (IllegalArgumentException ignored) { }
    }
}
