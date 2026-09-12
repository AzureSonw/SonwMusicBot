package com.jagrosh.jmusicbot.audio;

import com.sedmelluq.discord.lavaplayer.tools.FriendlyException;
import com.sedmelluq.discord.lavaplayer.tools.JsonBrowser;
import com.sedmelluq.discord.lavaplayer.track.*;
import org.apache.http.client.utils.URLEncodedUtils;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.function.Function;

/** Favorites, seasons and series previously supplied by the custom 1.3.1-lists binary. */
final class BilibiliPlaylistLoader
{
    private static final String API = "https://api.bilibili.com/";
    private record ListId(String kind, String owner, String id) {}

    static AudioPlaylist load(String url, int pageLimit, Function<String, JsonBrowser> fetch,
                             Function<String, AudioItem> loadVideo)
    {
        ListId list = parse(url);
        if (list == null)
            return null;
        String title = "Bilibili " + list.kind;
        if (list.kind.equals("series") && list.owner.isEmpty())
        {
            JsonBrowser meta = data(fetch.apply(API + "x/series/series?series_id=" + list.id)).get("meta");
            String owner = meta.get("mid").text();
            if (!numeric(owner))
                throw invalid("无法读取 Bilibili 列表的创建者");
            title = textOr(meta.get("name"), title);
            list = new ListId(list.kind, owner, list.id);
        }

        int size = list.kind.equals("favorite") ? 20 : 30;
        int limit = pageLimit > 0 ? pageLimit : Integer.MAX_VALUE;
        List<AudioTrack> tracks = new ArrayList<>();
        Set<String> visited = new HashSet<>();
        Set<String> visitedPages = new HashSet<>();
        for (int page = 1; page <= limit; page++)
        {
            JsonBrowser content = data(fetch.apply(endpoint(list, page)));
            JsonBrowser entries;
            long total;
            if (list.kind.equals("favorite"))
            {
                title = textOr(content.get("info").get("title"), title);
                entries = content.get("medias");
                total = content.get("info").get("media_count").asLong(-1);
            }
            else
            {
                title = textOr(content.get("meta").get("name"), title);
                entries = content.get("archives");
                total = content.get("page").get("total").asLong(-1);
            }
            if (entries.values().isEmpty() || !visitedPages.add(entries.format()))
                break;
            for (JsonBrowser entry : entries.values())
            {
                // Favorites may also contain articles or other non-video resources.
                if (list.kind.equals("favorite") && entry.get("type").asLong(2) != 2)
                    continue;
                String video = entry.get("bvid").text();
                if (video == null || !video.matches("BV[0-9A-Za-z]{10}"))
                {
                    String aid = entry.get(list.kind.equals("favorite") ? "id" : "aid").text();
                    if (!numeric(aid))
                        continue;
                    video = "av" + aid;
                }
                if (!visited.add(video))
                    continue;
                // A list entry represents one video, even when that video has multiple parts.
                AudioItem item = loadVideo.apply("https://www.bilibili.com/video/" + video + "?p=1");
                if (item instanceof AudioTrack track)
                    tracks.add(track);
                else if (item instanceof AudioPlaylist playlist && !playlist.getTracks().isEmpty())
                    tracks.add(playlist.getSelectedTrack() != null ? playlist.getSelectedTrack() : playlist.getTracks().get(0));
            }
            if ((total >= 0 && (long) page * size >= total)
                || (total < 0 && entries.values().size() < size))
                break;
        }
        return new BasicAudioPlaylist(title, tracks, null, false);
    }

    private static String endpoint(ListId list, int page)
    {
        return API + switch (list.kind)
        {
            case "favorite" -> "x/v3/fav/resource/list?media_id=" + list.id + "&pn=" + page
                + "&ps=20&order=mtime&type=0&platform=web";
            case "season" -> "x/polymer/web-space/seasons_archives_list?mid=" + list.owner
                + "&season_id=" + list.id + "&page_num=" + page + "&page_size=30&sort_reverse=false";
            default -> "x/series/archives?mid=" + list.owner + "&series_id=" + list.id
                + "&only_normal=true&sort=desc&pn=" + page + "&ps=30";
        };
    }

    private static ListId parse(String url)
    {
        if (url == null)
            return null;
        URI uri;
        try { uri = URI.create(url); }
        catch (IllegalArgumentException e) { return null; }
        if (!"https".equalsIgnoreCase(uri.getScheme()) && !"http".equalsIgnoreCase(uri.getScheme()))
            return null;
        String host = uri.getHost();
        if (host == null || !(host.equalsIgnoreCase("www.bilibili.com")
            || host.equalsIgnoreCase("bilibili.com") || host.equalsIgnoreCase("space.bilibili.com")))
            return null;
        Map<String, String> query = new HashMap<>();
        try { URLEncodedUtils.parse(uri, StandardCharsets.UTF_8).forEach(p -> query.putIfAbsent(p.getName(), p.getValue())); }
        catch (IllegalArgumentException e) { throw invalid("Bilibili 列表参数无效"); }
        String path = uri.getPath();
        if (path == null)
            return null;
        String[] parts = path.replaceAll("/+$", "").split("/");
        if (host.equalsIgnoreCase("space.bilibili.com") && parts.length >= 3 && numeric(parts[1]))
        {
            if (parts.length == 3 && parts[2].equals("favlist"))
                return checked("favorite", parts[1], first(query, "fid", "media_id"));
            if (parts.length == 4 && parts[2].equals("lists"))
                return checked("series".equals(query.get("type")) ? "series" : "season", parts[1], parts[3]);
        }
        if (!host.equalsIgnoreCase("space.bilibili.com"))
        {
            if (parts.length == 4 && parts[1].equals("medialist") && parts[2].equals("play") && numeric(parts[3]))
            {
                String kind = "space_series".equals(query.get("business")) || query.containsKey("series_id") ? "series" : "season";
                return checked(kind, parts[3], first(query, "business_id", "season_id", "series_id"));
            }
            if (parts.length == 3 && parts[1].equals("list"))
                return checked("series", "", parts[2]);
        }
        return null;
    }

    private static ListId checked(String kind, String owner, String id)
    {
        if (!numeric(id))
            throw invalid("Bilibili 列表链接缺少有效的列表 ID");
        return new ListId(kind, owner, id);
    }
    private static String first(Map<String, String> values, String... keys)
    {
        for (String key : keys)
            if (values.get(key) != null)
                return values.get(key);
        return null;
    }
    private static boolean numeric(String value) { return value != null && value.matches("[0-9]+"); }
    private static String textOr(JsonBrowser value, String fallback) { return value.isNull() ? fallback : value.text(); }
    private static JsonBrowser data(JsonBrowser response)
    {
        if (response == null || response.get("code").asLong(-1) != 0 || response.get("data").isNull())
            throw invalid("Bilibili 列表无法访问（可能已删除、设为私密或请求受限）");
        return response.get("data");
    }
    private static FriendlyException invalid(String message)
    {
        return new FriendlyException(message, FriendlyException.Severity.COMMON, null);
    }
}
