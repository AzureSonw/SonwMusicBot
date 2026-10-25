package com.jagrosh.jmusicbot.audio;

import com.sedmelluq.discord.lavaplayer.tools.JsonBrowser;
import com.sedmelluq.discord.lavaplayer.track.*;
import org.apache.http.client.utils.URLEncodedUtils;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.regex.Pattern;

/** Loads current signed detail responses without depending on webpage server-side rendering. */
final class BilibiliVideoLoader
{
    private static final Pattern VIDEO_PATH = Pattern.compile("^/video/(BV[0-9A-Za-z]{10}|av[0-9]+)/?$");

    static AudioItem load(String url, Function<Map<String, String>, JsonBrowser> fetchDetail,
                          BiFunction<AudioTrackInfo, Long, AudioTrack> createTrack)
    {
        URI uri = parseUrl(url);
        if (uri == null || !Set.of("bilibili.com", "www.bilibili.com", "m.bilibili.com").contains(uri.getHost().toLowerCase(Locale.ROOT)))
            return null;
        var match = VIDEO_PATH.matcher(uri.getPath());
        if (!match.matches()) return null;
        String id = match.group(1);
        int selectedPage = 0;
        try
        {
            for (var param : URLEncodedUtils.parse(uri, StandardCharsets.UTF_8))
                if (param.getName().equals("p"))
                {
                    selectedPage = Integer.parseInt(param.getValue());
                    if (selectedPage < 1) throw new NumberFormatException();
                }
        }
        catch (IllegalArgumentException e) { throw BilibiliErrors.problem("Bilibili 分 P 参数必须是大于 0 的整数。"); }

        Map<String, String> query = Map.of("platform", "web", id.startsWith("av") ? "aid" : "bvid",
            id.startsWith("av") ? id.substring(2) : id);
        JsonBrowser data = BilibiliErrors.data(fetchDetail.apply(query)).get("View");
        String bvid = data.get("bvid").text();
        if (bvid == null || !bvid.matches("BV[0-9A-Za-z]{10}")
            || (id.startsWith("BV") && !id.equals(bvid))
            || (id.startsWith("av") && !id.substring(2).equals(data.get("aid").text())))
            throw BilibiliErrors.problem("Bilibili 返回的视频与请求不一致，已停止加载以避免播放错误歌曲。");

        List<JsonBrowser> pages = data.get("pages").values();
        String title = data.get("title").textOrDefault("Bilibili 视频");
        String author = data.get("owner").get("name").textOrDefault("未知作者");
        String artwork = data.get("pic").text();
        if (pages.isEmpty())
        {
            if (selectedPage > 1) throw BilibiliErrors.problem("这个 Bilibili 视频没有所选分 P。");
            return track(data, title, author, bvid, artwork, 1, createTrack);
        }
        if (selectedPage > pages.size()) throw BilibiliErrors.problem("这个 Bilibili 视频没有所选分 P。");
        if (selectedPage > 0)
        {
            JsonBrowser page = pages.get(selectedPage - 1);
            return track(page, pages.size() == 1 ? title : page.get("part").textOrDefault(title),
                author, bvid, artwork, selectedPage, createTrack);
        }
        if (pages.size() == 1) return track(pages.get(0), title, author, bvid, artwork, 1, createTrack);
        List<AudioTrack> tracks = new ArrayList<>();
        for (int i = 0; i < pages.size(); i++)
            tracks.add(track(pages.get(i), pages.get(i).get("part").textOrDefault(title), author, bvid, artwork, i + 1, createTrack));
        return new BasicAudioPlaylist(title, tracks, tracks.get(0), false);
    }

    private static AudioTrack track(JsonBrowser page, String title, String author, String bvid, String artwork,
                                    int part, BiFunction<AudioTrackInfo, Long, AudioTrack> createTrack)
    {
        long cid = page.get("cid").asLong(0);
        if (cid <= 0) throw BilibiliErrors.problem("Bilibili 返回的视频缺少音频标识，无法播放。");
        return createTrack.apply(new AudioTrackInfo(title, author, page.get("duration").asLong(0) * 1000,
            bvid, false, "https://www.bilibili.com/video/" + bvid + "?p=" + part, artwork, null), cid);
    }

    static URI parseUrl(String url)
    {
        if (url == null) return null;
        try
        {
            URI uri = URI.create(url);
            if (!("https".equalsIgnoreCase(uri.getScheme()) || "http".equalsIgnoreCase(uri.getScheme()))
                || uri.getHost() == null || uri.getRawUserInfo() != null || uri.getPort() != -1)
                return null;
            return uri;
        }
        catch (IllegalArgumentException e) { return null; }
    }
}
