package com.jagrosh.jmusicbot.audio;

import com.github.parrotxray.lavabili.plugin.BilibiliConfig;
import com.github.parrotxray.lavabili.source.BilibiliAudioSourceManager;
import com.sedmelluq.discord.lavaplayer.player.DefaultAudioPlayerManager;
import com.sedmelluq.discord.lavaplayer.player.event.AudioEventAdapter;
import com.sedmelluq.discord.lavaplayer.tools.FriendlyException;
import com.sedmelluq.discord.lavaplayer.tools.JsonBrowser;
import com.sedmelluq.discord.lavaplayer.track.*;
import com.sedmelluq.discord.lavaplayer.track.playback.LocalAudioTrackExecutor;
import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import org.slf4j.LoggerFactory;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicReference;

/** Missing-entry regression fixtures and optional anonymous test of the reported public season. */
public final class BilibiliPlaylistCheck
{
    private static int checks;
    private static final String GOOD = "BV1yj411f7f8", MISSING = "BV1D86dBrEaE", LATER = "BV1ZrJi6UEwN";
    private static final String SEASON = "https://space.bilibili.com/3546569288714792/lists/2136390?type=season";
    private static final Logger LOG = (Logger) LoggerFactory.getLogger(BilibiliPlaylistLoader.class);

    public static void main(String[] args) throws Exception
    {
        ((Logger) LoggerFactory.getLogger(Logger.ROOT_LOGGER_NAME)).setLevel(Level.OFF);
        LOG.setLevel(Level.WARN);
        LOG.setAdditive(false);
        offline();
        System.out.println("PLAYLIST OFFLINE PASS: " + checks + " assertions");
        if (args.length > 0 && args[0].equals("--online")) online(args.length > 1 ? Integer.parseInt(args[1]) : 6);
    }

    private static void offline()
    {
        for (String route : List.of(SEASON, "https://space.bilibili.com/123/lists/456?type=series",
            "https://space.bilibili.com/123/favlist?fid=456"))
        {
            for (int code : List.of(-404, 62002, 404))
            {
                ListAppender<ILoggingEvent> logs = capture();
                try
                {
                    List<String> loaded = new ArrayList<>();
                    AudioPlaylist list = BilibiliPlaylistLoader.load(route + "&fixture-secret=never-log", 1,
                        url -> entries(route, 3, GOOD, MISSING, LATER), url -> {
                            loaded.add(url);
                            if (url.contains(MISSING)) throw missing(code);
                            return track(url);
                        });
                    require(list.getTracks().stream().map(AudioTrack::getIdentifier).toList().equals(List.of(GOOD, LATER)), "only missing entry skipped; order preserved");
                    require(loaded.size() == 3 && !list.isSearchResult() && list.getSelectedTrack() == null, "whole playlist returned after failure");
                    require(logs.list.size() == 2 && logs.list.get(0).getLevel() == Level.WARN, "visible per-entry and summary warnings");
                    String text = logs.list.toString();
                    require(logs.list.get(0).getFormattedMessage().contains("https://www.bilibili.com/video/" + MISSING + "?p=1"), "canonical skipped link logged");
                    require(logs.list.get(0).getFormattedMessage().contains(Integer.toString(code)), "stable reason logged");
                    require(logs.list.get(1).getFormattedMessage().contains("loaded 2 tracks, skipped 1"), "accurate summary");
                    require(!text.contains("fixture-secret") && logs.list.stream().allMatch(e -> e.getThrowableProxy() == null), "no response body/query/stack trace in logs");
                }
                finally { LOG.detachAppender(logs); logs.stop(); }
            }
        }

        for (List<String> order : List.of(List.of(MISSING, GOOD), List.of(GOOD, MISSING), List.of(MISSING, MISSING, GOOD)))
        {
            List<String> loaded = new ArrayList<>();
            AudioPlaylist list = BilibiliPlaylistLoader.load(SEASON, 1, u -> entries(SEASON, order.size(), order.toArray(String[]::new)), u -> {
                loaded.add(u);
                if (u.contains(MISSING)) throw missing(-404);
                return track(u);
            });
            require(list.getTracks().size() == 1 && list.getTracks().get(0).getIdentifier().equals(GOOD), "first/last/duplicate missing entries");
            require(loaded.size() == 2, "unavailable entries are deduplicated, not retried");
        }
        List<String> pages = new ArrayList<>();
        AudioPlaylist later = BilibiliPlaylistLoader.load(SEASON, 2, url -> {
            pages.add(url);
            return entries(SEASON, 31, pages.size() == 1 ? MISSING : LATER);
        }, url -> { if (url.contains(MISSING)) throw missing(-404); return track(url); });
        require(pages.size() == 2 && later.getTracks().get(0).getIdentifier().equals(LATER), "fully unavailable page does not hide later pages");
        pages.clear();
        FriendlyException allMissing = failure(() -> BilibiliPlaylistLoader.load(SEASON, 1, url -> {
            pages.add(url); return entries(SEASON, 31, MISSING);
        }, url -> { throw missing(-404); }));
        require(pages.size() == 1 && allMissing.getMessage().contains("本次加载范围") && allMissing.getMessage().contains("已跳过 1"), "page limit retained; all-missing range explained");

        for (int code : List.of(-101, -403, -412, -352, -500))
            propagates(() -> BilibiliErrors.data(json("{\"code\":" + code + "}")), "API " + code);
        for (int status : List.of(401, 403, 412, 429, 500, 567))
            propagates(() -> BilibiliErrors.checkHttp(status), "HTTP " + status);
        propagates(() -> { throw BilibiliErrors.problem("network timeout"); }, "network error");
        propagates(() -> { throw BilibiliErrors.problem("找不到这个 Bilibili 视频或列表，可能已被删除。"); }, "message matching must not classify errors");
        RuntimeException unexpected = new IllegalStateException("unexpected fixture");
        try { BilibiliPlaylistLoader.load(SEASON, 1, u -> entries(SEASON, 1, GOOD), u -> { throw unexpected; }); throw new AssertionError("Expected unexpected error"); }
        catch (RuntimeException e) { require(e == unexpected, "unexpected exceptions not swallowed"); }
        for (int code : List.of(-404, 62002, 404))
        {
            var error = missing(code);
            FriendlyException result = failure(() -> BilibiliPlaylistLoader.load(SEASON, 6, u -> { throw error; }, u -> { throw new AssertionError(); }));
            require(result == error, "missing list itself is not skipped");
        }
        pages.clear();
        FriendlyException limited = BilibiliErrors.restricted();
        FriendlyException pageFailure = failure(() -> BilibiliPlaylistLoader.load(SEASON, 2, u -> {
            pages.add(u);
            if (pages.size() == 2) throw limited;
            return entries(SEASON, 31, GOOD);
        }, BilibiliPlaylistCheck::track));
        require(pageFailure == limited && pages.size() == 2, "later list-page failure does not return partial success");
        var error = missing(-404);
        require(failure(() -> BilibiliVideoLoader.load("https://www.bilibili.com/video/" + MISSING,
            q -> { throw error; }, (info, cid) -> { throw new AssertionError(); })) == error, "direct missing track still reports failure");
    }

    private static void propagates(Runnable failing, String label)
    {
        FriendlyException original = failure(failing);
        List<String> loaded = new ArrayList<>();
        FriendlyException result = failure(() -> BilibiliPlaylistLoader.load(SEASON, 1, u -> entries(SEASON, 3, GOOD, MISSING, LATER), u -> {
            loaded.add(u);
            if (u.contains(MISSING)) throw original;
            return track(u);
        }));
        require(result == original && loaded.size() == 2, label + " stops, no partial success");
    }

    private static void online(int pageLimit) throws Exception
    {
        DefaultAudioPlayerManager manager = new DefaultAudioPlayerManager();
        var player = manager.createPlayer();
        ListAppender<ILoggingEvent> logs = capture();
        try
        {
            BilibiliConfig config = new BilibiliConfig();
            config.setEnabled(true);
            manager.registerSourceManager(new BilibiliSourceWithLists(new BilibiliAudioSourceManager(config), pageLimit));
            long start = System.nanoTime();
            AudioItem item = manager.loadItemSync(new AudioReference(SEASON, null));
            require(item instanceof AudioPlaylist, "public season loads as playlist");
            AudioPlaylist list = (AudioPlaylist) item;
            require(list.getTracks().size() > 43 && list.getSelectedTrack() == null, "continues beyond reported failure");
            require(list.getTracks().stream().allMatch(t -> t instanceof SonwBilibiliAudioTrack), "all entries keep fixed audio transport");
            require(list.getTracks().stream().noneMatch(t -> MISSING.equals(t.getIdentifier())), "known unavailable video skipped");
            require(logs.list.stream().anyMatch(e -> e.getFormattedMessage().contains(MISSING)), "unavailable link logged in real run");
            long skipped = logs.list.stream().filter(e -> e.getFormattedMessage().startsWith("Skipping unavailable")).count();
            require(list.getTracks().stream().map(AudioTrack::getIdentifier).distinct().count() == list.getTracks().size(), "no duplicated tracks");
            System.out.println("ONLINE season PASS: loaded=" + list.getTracks().size() + ", skipped=" + skipped
                + ", pageLimit=" + pageLimit + ", elapsedSeconds=" + TimeUnit.NANOSECONDS.toSeconds(System.nanoTime() - start));
            logs.list.forEach(e -> System.out.println(e.getFormattedMessage()));
            AudioTrack afterFailure = list.getTracks().get(42);
            AtomicReference<FriendlyException> failure = new AtomicReference<>();
            player.addListener(new AudioEventAdapter() {
                @Override public void onTrackException(com.sedmelluq.discord.lavaplayer.player.AudioPlayer p, AudioTrack t, FriendlyException e) { failure.set(e); }
            });
            player.playTrack(afterFailure.makeClone());
            int frames = 0;
            long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(45);
            while (frames < 100 && System.nanoTime() < deadline)
            {
                if (failure.get() != null) throw failure.get();
                try {
                    var frame = player.provide(1, TimeUnit.SECONDS);
                    if (frame != null && !frame.isTerminator() && frame.getDataLength() > 0) frames++;
                } catch (TimeoutException ignored) { }
            }
            require(frames == 100, "playlist entry after failure plays");
            System.out.println("ONLINE later-entry playback PASS: " + afterFailure.getIdentifier() + ", 100 audio frames");
        }
        catch (FriendlyException e)
        {
            logs.list.forEach(event -> System.out.println(event.getFormattedMessage()));
            System.out.println("ONLINE season NOT VERIFIED: " + e.getMessage());
            throw e;
        }
        finally { player.destroy(); manager.shutdown(); LOG.detachAppender(logs); logs.stop(); }
    }

    private static ListAppender<ILoggingEvent> capture()
    {
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start(); LOG.addAppender(appender); return appender;
    }
    private static JsonBrowser entries(String route, int total, String... videos)
    {
        String entries = Arrays.stream(videos).map(v -> "{\"bvid\":\"" + v + "\",\"type\":2}").reduce((a,b) -> a+","+b).orElse("");
        return route.contains("favlist")
            ? json("{\"code\":0,\"data\":{\"info\":{\"title\":\"Test\",\"media_count\":" + total + "},\"medias\":[" + entries + "]}}")
            : json("{\"code\":0,\"data\":{\"meta\":{\"name\":\"Test\"},\"page\":{\"total\":" + total + "},\"archives\":[" + entries + "]}}");
    }
    private static BilibiliErrors.ResourceUnavailable missing(int code)
    {
        return (BilibiliErrors.ResourceUnavailable) failure(() -> {
            if (code == 404) BilibiliErrors.checkHttp(code);
            else BilibiliErrors.data(json("{\"code\":" + code + ",\"message\":\"fixture-secret\"}"));
        });
    }
    private static FriendlyException failure(Runnable run)
    {
        try { run.run(); } catch (FriendlyException expected) { return expected; }
        throw new AssertionError("Expected FriendlyException");
    }
    private static AudioTrack track(String url)
    {
        String bv = url.substring(url.indexOf("/video/") + 7, url.indexOf('?'));
        return new BaseAudioTrack(new AudioTrackInfo(bv, "fixture", 1000, bv, false, url)) {
            @Override public void process(LocalAudioTrackExecutor e) { throw new AssertionError("No offline playback"); }
        };
    }
    private static JsonBrowser json(String text) { try { return JsonBrowser.parse(text); } catch (Exception e) { throw new AssertionError(e); } }
    private static void require(boolean condition, String label) { if (!condition) throw new AssertionError(label); checks++; }
}
