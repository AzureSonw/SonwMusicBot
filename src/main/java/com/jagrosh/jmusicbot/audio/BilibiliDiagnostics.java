package com.jagrosh.jmusicbot.audio;

import com.sedmelluq.discord.lavaplayer.tools.FriendlyException;
import com.sedmelluq.discord.lavaplayer.tools.JsonBrowser;
import org.apache.http.client.utils.URLEncodedUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.*;

/** Allowlisted request context only: never log full URLs, signatures, cookies or response text. */
final class BilibiliDiagnostics
{
    private static final Logger log = LoggerFactory.getLogger(BilibiliDiagnostics.class);
    private static final Map<String, String> STAGES = Map.of(
        "/x/web-interface/wbi/view/detail", "video-detail",
        "/x/player/wbi/playurl", "playback-url",
        "/audio/music-service-c/web/url", "audio-url",
        "/x/v3/fav/resource/list", "favorite-page",
        "/x/polymer/web-space/seasons_archives_list", "season-page",
        "/x/series/archives", "series-page",
        "/x/series/series", "series-owner");

    static String context(String url)
    {
        try
        {
            URI uri = URI.create(url);
            if (uri.getRawUserInfo() != null || uri.getPort() != -1 || !"https".equalsIgnoreCase(uri.getScheme()))
                return "stage=unknown endpoint=redacted";
            if (Set.of("b23.tv", "www.b23.tv").contains(uri.getHost())) return "stage=short-link endpoint=b23.tv";
            if (!"api.bilibili.com".equals(uri.getHost()) || !STAGES.containsKey(uri.getPath()))
                return "stage=unknown endpoint=redacted";
            Map<String, String> values = new HashMap<>();
            URLEncodedUtils.parse(uri, StandardCharsets.UTF_8).forEach(p -> values.putIfAbsent(p.getName(), p.getValue()));
            StringBuilder result = new StringBuilder("stage=").append(STAGES.get(uri.getPath())).append(" endpoint=").append(uri.getPath());
            for (String key : List.of("bvid", "aid", "cid", "sid", "mid", "media_id", "season_id", "series_id", "pn", "page_num"))
            {
                String value = values.get(key);
                if (value != null && value.matches(key.equals("bvid") ? "BV[0-9A-Za-z]{10}" : "[0-9]{1,20}"))
                    result.append(' ').append(key).append('=').append(value);
            }
            return result.toString();
        }
        catch (IllegalArgumentException | NullPointerException e) { return "stage=unknown endpoint=redacted"; }
    }

    static void checkHttp(String url, int status)
    {
        try { BilibiliErrors.checkHttp(status); }
        catch (FriendlyException e)
        {
            log.warn("Bilibili request failed: {} HTTP={} API=not-read", context(url), status);
            throw e;
        }
    }

    static JsonBrowser checkApi(String url, int status, JsonBrowser response)
    {
        try { BilibiliErrors.data(response); return response; }
        catch (FriendlyException e)
        {
            long code = response == null ? Long.MIN_VALUE : response.get("code").asLong(Long.MIN_VALUE);
            log.warn("Bilibili request failed: {} HTTP={} API={}", context(url), status,
                code == Long.MIN_VALUE ? "missing" : Long.toString(code));
            throw e;
        }
    }

    static void networkFailure(String url)
    {
        networkFailure(url, -1);
    }

    static void networkFailure(String url, int status)
    {
        log.warn("Bilibili request failed: {} HTTP={} API=unparsed error=network-or-invalid-json", context(url),
            status < 0 ? "not-received" : Integer.toString(status));
    }

    static void signingFailure(String url)
    {
        log.warn("Bilibili request failed: {} error=signing-unavailable", context(url));
    }
}
