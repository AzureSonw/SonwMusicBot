package com.jagrosh.jmusicbot.audio;

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
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Supplier;

/** Completely offline diagnostic/redaction and metadata-cache tests. No real HTTP or bot startup. */
public final class BilibiliDiagnosticCheck
{
    private static int checks;
    private static final String BV = "BV1yj411f7f8", OTHER = "BV1ZrJi6UEwN";
    private static final Map<String, String> QUERY = Map.of("platform", "web", "bvid", BV);
    private static final String DETAIL = "https://api.bilibili.com/x/web-interface/wbi/view/detail?bvid=" + BV
        + "&platform=web&w_rid=fixture-secret&SESSDATA=fixture-secret";

    public static void main(String[] args) throws Exception
    {
        ((Logger) LoggerFactory.getLogger(Logger.ROOT_LOGGER_NAME)).setLevel(Level.OFF);
        diagnostics();
        cache();
        System.out.println("DIAGNOSTIC/CACHE OFFLINE PASS: " + checks + " assertions");
    }

    private static void diagnostics()
    {
        Logger log = (Logger) LoggerFactory.getLogger(BilibiliDiagnostics.class);
        log.setLevel(Level.WARN); log.setAdditive(false);
        ListAppender<ILoggingEvent> captured = new ListAppender<>();
        captured.start(); log.addAppender(captured);
        try
        {
            String detail = BilibiliDiagnostics.context(DETAIL);
            require(detail.contains("stage=video-detail") && detail.contains("bvid=" + BV), "detail context");
            require(!detail.contains("fixture-secret") && !detail.contains("w_rid") && !detail.contains("https://"), "query secrets and raw URLs excluded");
            for (String pair : List.of("/x/v3/fav/resource/list?media_id=123&pn=2|favorite-page",
                "/x/polymer/web-space/seasons_archives_list?season_id=2136390&page_num=2|season-page",
                "/x/series/archives?series_id=456&pn=3|series-page", "/x/series/series?series_id=456|series-owner",
                "/x/player/wbi/playurl?bvid=" + BV + "&cid=77|playback-url", "/audio/music-service-c/web/url?sid=42|audio-url"))
            {
                String[] parts = pair.split("\\|");
                String context = BilibiliDiagnostics.context("https://api.bilibili.com" + parts[0]);
                require(context.contains("stage=" + parts[1]) && context.contains("="), "endpoint context " + parts[1]);
            }
            for (String bad : Arrays.asList(null, "not a url fixture-secret", "https://fixture-secret@api.bilibili.com/x/series/series",
                "https://api.bilibili.com:123/x/series/series?fixture-secret", "http://api.bilibili.com/x/series/series",
                "https://evil.invalid/fixture-secret", "https://api.bilibili.com/fixture-secret",
                "https://api.bilibili.com/x/series/series?series_id=fixture-secret%0AInjected"))
                require(!BilibiliDiagnostics.context(bad).contains("fixture-secret"), "untrusted context redacted");
            require(BilibiliDiagnostics.context("https://b23.tv/fixture-secret?cookie=fixture-secret").equals("stage=short-link endpoint=b23.tv"), "short-link token redacted");
            FriendlyException http = failure(() -> BilibiliDiagnostics.checkHttp(DETAIL, 412));
            require(http.getMessage().contains("HTTP 412") && !http.getMessage().contains("fixture-secret"), "Discord distinguishes HTTP 412");
            for (int code : List.of(-352, -412))
            {
                FriendlyException api = failure(() -> BilibiliDiagnostics.checkApi(DETAIL, 200, json("{\"code\":" + code + ",\"message\":\"fixture-secret\"}")));
                require(api.getMessage().contains("API " + code) && !api.getMessage().contains("HTTP 412"), "Discord distinguishes API " + code);
                require(last(captured).contains("HTTP=200 API=" + code), "actual HTTP/API pair logged");
            }
            failure(() -> BilibiliDiagnostics.checkApi(DETAIL, 200, json("{\"data\":\"fixture-secret\"}")));
            require(last(captured).contains("API=missing"), "missing code identified");
            failure(() -> BilibiliDiagnostics.checkApi(DETAIL, 200, json("{\"code\":0}")));
            require(last(captured).contains("API=0"), "malformed success keeps actual code");
            BilibiliDiagnostics.networkFailure(DETAIL, 200);
            require(last(captured).contains("HTTP=200 API=unparsed"), "invalid JSON retains HTTP status");
            BilibiliDiagnostics.networkFailure(DETAIL);
            require(last(captured).contains("HTTP=not-received"), "transport failure has no invented HTTP status");
            BilibiliDiagnostics.signingFailure(DETAIL);
            require(last(captured).contains("error=signing-unavailable"), "signing stage");
            int before = captured.list.size();
            BilibiliDiagnostics.checkHttp(DETAIL, 200);
            BilibiliDiagnostics.checkApi(DETAIL, 200, success(BV, 77));
            require(captured.list.size() == before, "successful requests do not emit warning spam");
            for (ILoggingEvent event : captured.list)
                require(!event.getFormattedMessage().contains("fixture-secret") && event.getThrowableProxy() == null, "no credential/body/exception leakage");
        }
        finally { log.detachAppender(captured); captured.stop(); }

        Logger lists = (Logger) LoggerFactory.getLogger(BilibiliPlaylistLoader.class);
        lists.setLevel(Level.WARN); lists.setAdditive(false);
        captured = new ListAppender<>(); captured.start(); lists.addAppender(captured);
        try
        {
            String url = "https://space.bilibili.com/123/lists/456?type=season&cookie=fixture-secret";
            FriendlyException restricted = failure(() -> BilibiliErrors.data(json("{\"code\":-352}")));
            failure(() -> BilibiliPlaylistLoader.load(url, 1, u -> json("{\"code\":0,\"data\":{\"page\":{\"total\":1},\"archives\":[{\"bvid\":\"" + BV + "\"}]}}"),
                u -> { throw restricted; }));
            require(last(captured).contains("page=1 item=1 video=" + BV) && last(captured).contains("loaded=0 skipped=0"), "entry failure identifies list page/item/BV");
            failure(() -> BilibiliPlaylistLoader.load(url, 1, u -> { throw restricted; }, u -> { throw new AssertionError(); }));
            require(last(captured).contains("page=1 stage=list-page"), "list endpoint failure is distinct");
            require(captured.list.stream().noneMatch(e -> e.getFormattedMessage().contains("fixture-secret")), "playlist diagnostics redact user query");
        }
        finally { lists.detachAppender(captured); captured.stop(); }
    }

    private static void cache() throws Exception
    {
        AtomicLong now = new AtomicLong();
        AtomicInteger calls = new AtomicInteger();
        BilibiliDetailCache cache = new BilibiliDetailCache(2, 100, now::get);
        Supplier<JsonBrowser> fetch = () -> { calls.incrementAndGet(); return success(BV, 77); };
        JsonBrowser first = cache.load(QUERY, fetch);
        first.get("data").get("View").put("title", "changed by consumer");
        JsonBrowser second = cache.load(QUERY, fetch);
        require(calls.get() == 1 && cache.hitCount() == 1, "repeat details request coalesced");
        require(second.get("data").get("View").get("title").text().equals("Fixture"), "miss result cannot mutate snapshot");
        require(!second.format().contains("fixture-secret"), "only necessary metadata retained");
        second.get("data").get("View").put("title", "changed again");
        require(cache.load(QUERY, fetch).get("data").get("View").get("title").text().equals("Fixture"), "hits return independent parsed metadata");
        now.set(99); cache.load(QUERY, fetch);
        require(calls.get() == 1, "TTL not extended by reads");
        now.set(100); cache.load(QUERY, fetch);
        require(calls.get() == 2, "TTL expires at boundary");
        cache.clear(); cache.load(QUERY, fetch);
        require(calls.get() == 3, "shutdown/clear removes entries");

        for (String response : List.of("{\"code\":-404}", "{\"code\":-352}", "{\"code\":-412}", "{\"code\":0,\"data\":{}}"))
        {
            cache.clear(); int before = calls.get();
            Supplier<JsonBrowser> invalid = () -> { calls.incrementAndGet(); return json(response); };
            cache.load(QUERY, invalid); cache.load(QUERY, invalid);
            require(calls.get() == before + 2, "failed or malformed response not cached");
        }
        cache.clear(); int before = calls.get();
        Supplier<JsonBrowser> mismatch = () -> { calls.incrementAndGet(); return success(OTHER, 78); };
        cache.load(QUERY, mismatch); cache.load(QUERY, mismatch);
        require(calls.get() == before + 2, "mismatched identity not cached");
        FriendlyException error = BilibiliErrors.restricted();
        cache.clear();
        for (int n = 0; n < 2; n++)
            require(failure(() -> cache.load(QUERY, () -> { throw error; })) == error, "failures propagate unchanged");
        JsonBrowser large = success(BV, 77);
        large.get("data").get("View").put("title", "x".repeat(70000));
        before = calls.get();
        Supplier<JsonBrowser> oversized = () -> { calls.incrementAndGet(); return large; };
        cache.load(QUERY, oversized); cache.load(QUERY, oversized);
        require(calls.get() == before + 2, "oversized metadata not retained");

        cache.clear();
        cache.load(QUERY, fetch);
        cache.load(Map.of("platform", "web", "bvid", OTHER), () -> success(OTHER, 78));
        cache.load(QUERY, fetch); // Keep BV most recently used.
        cache.load(Map.of("platform", "web", "aid", "79"), () -> success("BV1NVWxeeEVJ", 79));
        AtomicInteger evicted = new AtomicInteger();
        cache.load(Map.of("platform", "web", "bvid", OTHER), () -> { evicted.incrementAndGet(); return success(OTHER, 78); });
        require(evicted.get() == 1, "LRU capacity bound");

        cache.clear();
        Map<String, String> extraParameter = Map.of("platform", "web", "bvid", BV, "mode", "other");
        before = calls.get(); cache.load(extraParameter, fetch); cache.load(extraParameter, fetch);
        require(calls.get() == before + 2, "unknown query semantics do not reuse cache");
        Map<String, String> av = Map.of("platform", "web", "aid", "77");
        cache.load(av, fetch);
        JsonBrowser avHit = cache.load(av, fetch);
        AudioTrack selected = (AudioTrack) BilibiliVideoLoader.load("https://www.bilibili.com/video/av77?p=2", q -> avHit, (info, cid) -> new Stub(info, cid));
        require(((Stub) selected).cid == 102 && selected.getDuration() == 20000 && selected.getInfo().title.equals("Part two"), "cached AV metadata retains requested part/CID/duration");
        AudioPlaylist parts = (AudioPlaylist) BilibiliVideoLoader.load("https://www.bilibili.com/video/" + BV, q -> avHit, (info, cid) -> new Stub(info, cid));
        require(parts.getTracks().size() == 2 && parts.getTracks().get(0) == parts.getSelectedTrack(), "cached multipart selection preserved");

        // Simulate the reported 201-entry list twice, without making HTTP requests.
        BilibiliDetailCache batch = new BilibiliDetailCache(256, 1000, now::get);
        AtomicInteger batchCalls = new AtomicInteger();
        for (int pass = 0; pass < 2; pass++)
            for (int i = 0; i < 201; i++)
            {
                long aid = 1000 + i;
                String bv = "BV" + String.format(java.util.Locale.ROOT, "%010d", i);
                batch.load(Map.of("platform", "web", "bvid", bv), () -> { batchCalls.incrementAndGet(); return success(bv, aid); });
            }
        require(batchCalls.get() == 201 && batch.hitCount() == 201, "201 repeated requests eliminated while cache remains valid");

        BilibiliDetailCache concurrent = new BilibiliDetailCache(2, 1000, now::get);
        AtomicInteger simultaneous = new AtomicInteger();
        ExecutorService pool = Executors.newFixedThreadPool(4);
        try
        {
            List<Future<JsonBrowser>> tasks = new ArrayList<>();
            for (int i = 0; i < 4; i++) tasks.add(pool.submit(() -> concurrent.load(QUERY, () -> { simultaneous.incrementAndGet(); return success(BV, 77); })));
            for (Future<JsonBrowser> task : tasks) require(task.get(5, TimeUnit.SECONDS).get("code").asLong(-1) == 0, "concurrent result");
            require(simultaneous.get() == 1, "simultaneous identical misses fetch once");
        }
        finally { pool.shutdownNow(); }
    }

    private static JsonBrowser success(String bv, long aid)
    {
        return json("{\"code\":0,\"message\":\"fixture-secret\",\"data\":{\"private\":\"fixture-secret\",\"View\":{\"bvid\":\"" + bv + "\",\"aid\":" + aid
            + ",\"title\":\"Fixture\",\"duration\":30,\"cid\":101,\"owner\":{\"name\":\"Author\"},\"pages\":["
            + "{\"cid\":101,\"duration\":10,\"part\":\"Part one\"},{\"cid\":102,\"duration\":20,\"part\":\"Part two\"}]}}}");
    }
    private static String last(ListAppender<ILoggingEvent> logs) { return logs.list.get(logs.list.size() - 1).getFormattedMessage(); }
    private static FriendlyException failure(Runnable run) { try { run.run(); } catch (FriendlyException e) { return e; } throw new AssertionError("Expected failure"); }
    private static JsonBrowser json(String text) { try { return JsonBrowser.parse(text); } catch (Exception e) { throw new AssertionError(e); } }
    private static void require(boolean value, String message) { if (!value) throw new AssertionError(message); checks++; }
    private static final class Stub extends BaseAudioTrack
    {
        final long cid;
        Stub(AudioTrackInfo info, long cid) { super(info); this.cid = cid; }
        @Override public void process(LocalAudioTrackExecutor e) { throw new AssertionError("Offline only"); }
    }
}
