package com.jagrosh.jmusicbot.audio;

import com.github.parrotxray.lavabili.plugin.BilibiliConfig;
import com.sedmelluq.discord.lavaplayer.tools.FriendlyException;
import com.sedmelluq.discord.lavaplayer.tools.JsonBrowser;
import com.sedmelluq.discord.lavaplayer.track.*;
import com.sedmelluq.discord.lavaplayer.track.playback.LocalAudioTrackExecutor;
import com.typesafe.config.ConfigException;
import com.typesafe.config.ConfigFactory;
import java.util.*;
import java.util.function.BiFunction;
import java.util.function.Function;

/** Offline metadata/auth/error regression checks. Fixtures contain no real credentials. */
public class Bilibili412Check
{
    private static int checks;
    private static final String BV = "BV1ZrJi6UEwN";
    private static final String URL = "https://www.bilibili.com/video/" + BV;
    private static final BiFunction<AudioTrackInfo, Long, AudioTrack> TRACK = (info, cid) -> new StubTrack(info, cid);

    public static void main(String[] args)
    {
        video();
        authentication();
        errors();
        System.out.println("412 REGRESSION PASS: " + checks + " assertions");
    }

    private static void video()
    {
        Function<Map<String, String>, JsonBrowser> detail = query -> {
            require("web".equals(query.get("platform")), "web platform");
            require(BV.equals(query.get("bvid")), "requested BV is sent unchanged");
            return detail(BV, 77, true);
        };
        AudioPlaylist list = (AudioPlaylist) BilibiliVideoLoader.load(URL, detail, TRACK);
        require(list.getTracks().size() == 2 && !list.isSearchResult(), "multipart playlist");
        require(list.getTracks().get(0).getInfo().title.equals("Part 1"), "part title");
        require(list.getTracks().get(1).getInfo().uri.endsWith("?p=2"), "part URL");
        StubTrack part = (StubTrack) BilibiliVideoLoader.load(URL + "?p=2&share_source=test", detail, TRACK);
        require(part.cid == 202 && part.getDuration() == 20000, "correct part's CID and duration");
        AudioTrack first = (AudioTrack) BilibiliVideoLoader.load(URL + "?p=1", detail, TRACK);
        require(first.getInfo().identifier.equals(BV), "stable BV for encoding");
        require(BilibiliVideoLoader.load("https://m.bilibili.com/video/" + BV + "/?p=1", detail, TRACK) instanceof AudioTrack, "mobile URL");
        AudioTrack av = (AudioTrack) BilibiliVideoLoader.load("https://www.bilibili.com/video/av77?p=1", query -> {
            require("77".equals(query.get("aid")) && !query.containsKey("bvid"), "AV query");
            return detail(BV, 77, true);
        }, TRACK);
        require(av.getInfo().uri.contains(BV), "AV normalizes to returned BV");
        AudioTrack single = (AudioTrack) BilibiliVideoLoader.load(URL, q -> detail(BV, 77, false), TRACK);
        require(single.getInfo().title.equals("Video title") && ((StubTrack)single).cid == 101, "single video without page list");
        for (String wrong : Arrays.asList(null, "bilisearch:music", "https://evil.invalid/video/" + BV,
            "https://www.bilibili.com.evil.invalid/video/" + BV, "https://u@www.bilibili.com/video/" + BV,
            "https://www.bilibili.com:444/video/" + BV, "https://space.bilibili.com/123/favlist?fid=123"))
            require(BilibiliVideoLoader.load(wrong, q -> { throw new AssertionError("unexpected request"); }, TRACK) == null, "non-video URL not fetched");
        for (String page : List.of("0", "-1", "xyz", "", "999999999999999"))
            friendly(() -> BilibiliVideoLoader.load(URL + "?p=" + page, q -> { throw new AssertionError("invalid page fetched"); }, TRACK), "分 P");
        friendly(() -> BilibiliVideoLoader.load(URL+"?p=3", detail, TRACK), "分 P");
        friendly(() -> BilibiliVideoLoader.load(URL, q -> detail("BV1NVWxeeEVJ", 77, true), TRACK), "不一致");
        friendly(() -> BilibiliVideoLoader.load("https://www.bilibili.com/video/av78", q -> detail(BV, 77, true), TRACK), "不一致");
        friendly(() -> BilibiliVideoLoader.load(URL, q -> json("{\"code\":0,\"data\":{\"View\":{\"bvid\":\""+BV+"\"}}}"), TRACK), "音频标识");
        require(BilibiliSourceWithLists.safeRedirect(URL), "canonical redirect allowed");
        for (String wrong : List.of("https://evil.invalid/", "http://www.bilibili.com/", "https://www.bilibili.com.evil.invalid/", "https://user@www.bilibili.com/", "https://www.bilibili.com:444/"))
            require(!BilibiliSourceWithLists.safeRedirect(wrong), "unsafe redirect rejected");
    }

    private static void authentication()
    {
        var defaults = ConfigFactory.parseResources("reference.conf");
        BilibiliConfig anonymous = BilibiliSettings.create(defaults.getConfig("playback.bilibili.auth"), 6);
        require(!anonymous.isAuthenticated() && anonymous.getPlaylistPageCount()==6, "legacy/default anonymous settings");
        var user = ConfigFactory.parseString("playback.bilibili.auth { enabled=true, sessdata=\"fixture-secret\", biliJct=\"csrf-fixture\", dedeUserId=\"42\", buvid3=\"device3\", buvid4=\"device4\" }");
        var merged = user.withFallback(defaults).resolve();
        var report = com.jagrosh.jmusicbot.config.diagnostics.ConfigDiagnostics.analyze(user, merged, defaults);
        String rendered = com.jagrosh.jmusicbot.config.render.ConfigRenderer.generateConfigContent(user, report,
            com.jagrosh.jmusicbot.config.model.ConfigUpdateType.REPAIR);
        var rewritten = ConfigFactory.parseString(rendered);
        require(rewritten.getString("playback.bilibili.auth.sessdata").equals("fixture-secret")
            && rewritten.getBoolean("playback.bilibili.auth.enabled"), "config updates preserve explicit authentication");
        BilibiliConfig configured = BilibiliSettings.create(merged.getConfig("playback.bilibili.auth"), 3);
        require(configured.isAuthenticated() && configured.getAuth().getSessdata().equals("fixture-secret"), "explicit login configuration");
        require(configured.getAuth().getBiliJct().equals("csrf-fixture") && configured.getAuth().getDedeUserId().equals("42"), "login fields mapped");
        require(configured.getAuth().getBuvid3().equals("device3") && configured.getAuth().getBuvid4().equals("device4"), "optional device cookies");
        require(!configured.getCanRefreshCookies() && configured.getAuth().getAcTimeValue().isEmpty(), "no automatic account changes or refreshed-secret logging");
        var disabled = ConfigFactory.parseString("enabled=false, sessdata=\"fixture-secret\"");
        require(BilibiliSettings.create(disabled, 6).getAuth().getSessdata().isEmpty(), "disabled auth does not send stored secrets");
        for (String invalid : List.of("enabled=true, sessdata=\"\"", "enabled=true, sessdata=\"fixture-secret; other=x\"", "enabled=true, sessdata=\"fixture-secret\\r\\nInjected: x\""))
        {
            try { BilibiliSettings.create(ConfigFactory.parseString(invalid), 6); throw new AssertionError("invalid cookie accepted"); }
            catch (ConfigException expected) { require(!expected.getMessage().contains("fixture-secret"), "validation errors redact credentials"); }
        }
    }

    private static void errors()
    {
        BilibiliErrors.checkHttp(200);
        friendly(() -> BilibiliErrors.checkHttp(412), "412");
        friendly(() -> BilibiliErrors.checkHttp(429), "429");
        friendly(() -> BilibiliErrors.checkHttp(403), "拒绝访问");
        friendly(() -> BilibiliErrors.checkHttp(404), "删除");
        friendly(() -> BilibiliErrors.checkHttp(500), "500");
        for (int code : List.of(-412, -352))
            friendly(() -> BilibiliVideoLoader.load(URL, q -> json("{\"code\":"+code+",\"message\":\"fixture-secret\"}"), TRACK), "playback.bilibili.auth");
        friendly(() -> BilibiliErrors.data(null), "无效");
        friendly(() -> BilibiliErrors.data(json("{\"code\":-101}")), "登录");
    }

    private static JsonBrowser detail(String bv, long aid, boolean multipart)
    {
        return json("{\"code\":0,\"data\":{\"View\":{\"bvid\":\""+bv+"\",\"aid\":"+aid
            +",\"title\":\"Video title\",\"owner\":{\"name\":\"Test\"},\"duration\":10,\"cid\":101,\"pages\":"
            +(multipart ? "[{\"page\":1,\"cid\":101,\"duration\":10,\"part\":\"Part 1\"},{\"page\":2,\"cid\":202,\"duration\":20,\"part\":\"Part 2\"}]" : "[]") +"}}}");
    }
    private static JsonBrowser json(String text) { try { return JsonBrowser.parse(text); } catch(Exception e) { throw new AssertionError(e); } }
    private static void require(boolean value, String label) { if(!value) throw new AssertionError(label); checks++; }
    private static void friendly(Runnable run, String expected)
    {
        try { run.run(); }
        catch(FriendlyException e) { require(e.getMessage().contains(expected) && !e.getMessage().contains("fixture-secret"), "actionable, redacted error: "+expected); return; }
        throw new AssertionError("Expected FriendlyException: " + expected);
    }
    private static class StubTrack extends BaseAudioTrack
    {
        final long cid;
        StubTrack(AudioTrackInfo info, long cid) { super(info); this.cid=cid; }
        @Override public void process(LocalAudioTrackExecutor executor) { throw new AssertionError("No network/audio in offline test"); }
    }
}
