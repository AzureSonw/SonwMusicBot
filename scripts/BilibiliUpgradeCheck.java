package com.jagrosh.jmusicbot.audio;

import com.github.parrotxray.lavabili.plugin.BilibiliConfig;
import com.github.parrotxray.lavabili.source.BilibiliAudioSourceManager;
import com.sedmelluq.discord.lavaplayer.player.DefaultAudioPlayerManager;
import com.sedmelluq.discord.lavaplayer.player.event.AudioEventAdapter;
import com.sedmelluq.discord.lavaplayer.player.AudioPlayer;
import com.sedmelluq.discord.lavaplayer.tools.FriendlyException;
import com.sedmelluq.discord.lavaplayer.tools.JsonBrowser;
import com.sedmelluq.discord.lavaplayer.track.*;
import com.sedmelluq.discord.lavaplayer.track.playback.LocalAudioTrackExecutor;
import org.slf4j.LoggerFactory;
import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;

import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;

/** Standalone regression checks against the actual shaded release JAR; no Discord credentials. */
public final class BilibiliUpgradeCheck
{
    private static int checks;
    private static final String BV = "BV1NVWxeeEVJ";
    private static final Function<String, AudioItem> VIDEO = url -> {
        require(url.endsWith("?p=1"), "list entries select their first part");
        return new StubTrack(url);
    };

    public static void main(String[] args) throws Exception
    {
        ((Logger) LoggerFactory.getLogger(Logger.ROOT_LOGGER_NAME)).setLevel(Level.OFF);
        offline();
        System.out.println("OFFLINE PASS: " + checks + " assertions");
        if (args.length > 0 && args[0].equals("--online"))
            online();
    }

    private static void offline()
    {
        for (String url : Arrays.asList(null, "bilisearch:music", "https://www.bilibili.com/video/" + BV,
            "https://b23.tv/example", "https://example.com/123/favlist?fid=456",
            "https://space.bilibili.com.evil.invalid/123/favlist?fid=456", "not a url"))
            require(BilibiliPlaylistLoader.load(url, 6, u -> { throw new AssertionError(u); }, VIDEO) == null,
                "unrelated URLs delegate unchanged");

        List<String> requests = new ArrayList<>();
        AudioPlaylist favorite = BilibiliPlaylistLoader.load("https://space.bilibili.com/123/favlist?fid=456", 6, u -> {
            requests.add(u);
            require(u.contains("media_id=456") && u.contains("pn=1"), "favorite API route");
            return json("{\"code\":0,\"data\":{\"info\":{\"title\":\"Favorites\",\"media_count\":4},\"medias\":["
                + "{\"bvid\":\"" + BV + "\",\"type\":2},{\"bvid\":\"" + BV + "\",\"type\":2},"
                + "{\"id\":987,\"type\":2},{\"id\":123,\"type\":12}]}}");
        }, VIDEO);
        require(favorite.getName().equals("Favorites"), "favorite title");
        require(favorite.getTracks().size() == 2 && requests.size() == 1, "deduplication, AV fallback, articles skipped");
        require(favorite.getTracks().get(1).getIdentifier().contains("av987"), "AV identifier retained");

        String[] routes = {
            "https://space.bilibili.com/123/lists/456?type=season",
            "https://www.bilibili.com/medialist/play/123?business=space_collection&business_id=456",
            "https://space.bilibili.com/123/lists/456?type=series",
            "https://www.bilibili.com/medialist/play/123?business=space_series&business_id=456"
        };
        for (String route : routes)
        {
            boolean series = route.contains("series");
            AudioPlaylist list = BilibiliPlaylistLoader.load(route, 1, u -> {
                require(u.contains(series ? "x/series/archives" : "seasons_archives_list"), "collection API route");
                require(u.contains("mid=123") && u.contains("_id=456"), "collection identifiers");
                return archive("Collection", 1, BV);
            }, VIDEO);
            require(list.getTracks().size() == 1 && list.getName().equals("Collection"), "collection loaded");
        }

        requests.clear();
        AudioPlaylist series = BilibiliPlaylistLoader.load("https://www.bilibili.com/list/456", 1, u -> {
            requests.add(u);
            if (u.contains("x/series/series?"))
                return json("{\"code\":0,\"data\":{\"meta\":{\"mid\":123,\"name\":\"Series\"}}}");
            require(u.contains("mid=123"), "series owner lookup");
            return json("{\"code\":0,\"data\":{\"page\":{\"total\":1},\"archives\":[{\"aid\":987}]}}");
        }, VIDEO);
        require(requests.size() == 2 && series.getName().equals("Series"), "short series URL");

        requests.clear();
        BilibiliPlaylistLoader.load(routes[0], 1, u -> {
            requests.add(u);
            return archive("Limited", 100, BV);
        }, VIDEO);
        require(requests.size() == 1, "configured page limit");
        requests.clear();
        AudioPlaylist repeated = BilibiliPlaylistLoader.load(routes[0], 0, u -> {
            requests.add(u);
            return archive("Repeated", 1000, BV);
        }, VIDEO);
        require(requests.size() == 2 && repeated.getTracks().size() == 1, "repeated pages cannot loop forever");

        requests.clear();
        AudioPlaylist laterVideo = BilibiliPlaylistLoader.load("https://space.bilibili.com/123/favlist?media_id=456", 6, u -> {
            requests.add(u);
            String entries = requests.size() == 1 ? "{\"id\":123,\"type\":12}" : "{\"id\":987,\"type\":2}";
            return json("{\"code\":0,\"data\":{\"info\":{\"media_count\":21},\"medias\":[" + entries + "]}}");
        }, VIDEO);
        require(requests.size() == 2 && laterVideo.getTracks().size() == 1, "non-video page does not hide later videos");

        AudioTrack first = new StubTrack("first"), selected = new StubTrack("selected");
        AudioPlaylist selectedPart = BilibiliPlaylistLoader.load(routes[0], 1, u -> archive("Parts", 1, BV),
            u -> new BasicAudioPlaylist("Parts", List.of(first, selected), selected, false));
        require(selectedPart.getTracks().equals(List.of(selected)), "selected part retained");
        expectFriendly(() -> BilibiliPlaylistLoader.load(routes[0], 1, u -> json("{\"code\":-412}"), VIDEO));
        expectFriendly(() -> BilibiliPlaylistLoader.load("https://space.bilibili.com/123/favlist", 1,
            u -> { throw new AssertionError("invalid ID must not make a request"); }, VIDEO));
    }

    private static void online() throws Exception
    {
        DefaultAudioPlayerManager manager = new DefaultAudioPlayerManager();
        AudioPlayer player = manager.createPlayer();
        try
        {
            BilibiliConfig config = new BilibiliConfig();
            config.setEnabled(true);
            config.setAllowSearch(true);
            manager.registerSourceManager(new BilibiliSourceWithLists(new BilibiliAudioSourceManager(config), 6));
            AudioTrack track = firstTrack(manager.loadItemSync(new AudioReference("https://www.bilibili.com/video/" + BV + "?p=1", null)));
            require(track.getInfo().uri.contains(BV), "direct link resolves requested BV");
            System.out.println("ONLINE metadata PASS: " + track.getInfo().title);
            AudioTrack decoded = manager.decodeTrackDetails(track.getInfo(), manager.encodeTrackDetails(track));
            require(decoded != null && decoded.getIdentifier().equals(track.getIdentifier()), "source registration/serialization");
            AtomicReference<FriendlyException> error = new AtomicReference<>();
            player.addListener(new AudioEventAdapter() {
                @Override public void onTrackException(AudioPlayer p, AudioTrack t, FriendlyException e) { error.set(e); }
            });
            player.playTrack(track);
            int frames = 0;
            long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(45);
            while (frames < 100 && System.nanoTime() < deadline)
            {
                if (error.get() != null)
                    throw error.get();
                try {
                    var frame = player.provide(1, TimeUnit.SECONDS);
                    if (frame != null && !frame.isTerminator() && frame.getDataLength() > 0)
                        frames++;
                } catch (TimeoutException ignored) { }
            }
            require(frames == 100, "100 decoded audio frames");
            System.out.println("ONLINE playback PASS: " + frames + " audio frames");
            AudioItem search = manager.loadItemSync(new AudioReference("bilisearch:音乐", null));
            require(search instanceof AudioPlaylist && !((AudioPlaylist) search).getTracks().isEmpty(), "search results");
            System.out.println("ONLINE search PASS");
        }
        finally { player.destroy(); manager.shutdown(); }
    }

    private static AudioTrack firstTrack(AudioItem item)
    {
        if (item instanceof AudioTrack track) return track;
        if (item instanceof AudioPlaylist list && !list.getTracks().isEmpty())
            return list.getSelectedTrack() != null ? list.getSelectedTrack() : list.getTracks().get(0);
        throw new AssertionError("No audio track returned");
    }
    private static JsonBrowser archive(String title, int total, String bvid)
    {
        return json("{\"code\":0,\"data\":{\"meta\":{\"name\":\"" + title + "\"},\"page\":{\"total\":" + total
            + "},\"archives\":[{\"bvid\":\"" + bvid + "\"}]}}");
    }
    private static JsonBrowser json(String text)
    {
        try { return JsonBrowser.parse(text); }
        catch (Exception e) { throw new AssertionError(e); }
    }
    private static void require(boolean condition, String message)
    {
        if (!condition) throw new AssertionError(message);
        checks++;
    }
    private static void expectFriendly(Runnable action)
    {
        try { action.run(); }
        catch (FriendlyException expected) { checks++; return; }
        throw new AssertionError("Expected a user-readable error");
    }
    private static final class StubTrack extends BaseAudioTrack
    {
        StubTrack(String url) { super(new AudioTrackInfo(url, "test", 1000, url, false, url)); }
        @Override public void process(LocalAudioTrackExecutor executor) { throw new AssertionError("Offline test must not play audio"); }
    }
}
