package com.jagrosh.jmusicbot.audio;

import com.sedmelluq.discord.lavaplayer.tools.JsonBrowser;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.function.LongSupplier;
import java.util.function.Supplier;

/** Short-lived, per-source metadata cache; never caches failed requests or signed playback URLs. */
final class BilibiliDetailCache
{
    private record Entry(long created, String json) {}
    private final LinkedHashMap<String, Entry> entries = new LinkedHashMap<>(16, 0.75f, true);
    private final int capacity;
    private final long ttl;
    private final LongSupplier clock;
    private long hits;

    BilibiliDetailCache() { this(256, TimeUnit.MINUTES.toNanos(5), System::nanoTime); }

    BilibiliDetailCache(int capacity, long ttl, LongSupplier clock)
    {
        if (capacity < 1 || ttl < 1) throw new IllegalArgumentException("Invalid cache bounds");
        this.capacity = capacity;
        this.ttl = ttl;
        this.clock = clock;
    }

    // Coalesce identical concurrent requests and serialize cache misses for this source instance.
    synchronized JsonBrowser load(Map<String, String> parameters, Supplier<JsonBrowser> fetch)
    {
        String key = key(parameters);
        if (key == null) return fetch.get();
        long now = clock.getAsLong();
        entries.values().removeIf(entry -> now - entry.created >= ttl);
        Entry cached = entries.get(key);
        if (cached != null)
        {
            try { JsonBrowser result = JsonBrowser.parse(cached.json); hits++; return result; }
            catch (IOException e) { entries.remove(key); }
        }
        JsonBrowser response = fetch.get(); // Errors propagate unchanged and are never retained.
        String snapshot = snapshot(parameters, response);
        if (snapshot != null)
        {
            entries.put(key, new Entry(clock.getAsLong(), snapshot));
            while (entries.size() > capacity) entries.remove(entries.keySet().iterator().next());
        }
        return response;
    }

    synchronized long hitCount() { return hits; }
    synchronized void clear() { entries.clear(); }

    private static String key(Map<String, String> parameters)
    {
        if (!"web".equals(parameters.get("platform")) || parameters.size() != 2) return null;
        String bv = parameters.get("bvid"), aid = parameters.get("aid");
        if (bv != null && bv.matches("BV[0-9A-Za-z]{10}")) return "bv:" + bv;
        if (aid != null && aid.matches("[0-9]{1,20}")) return "av:" + aid;
        return null;
    }

    private static String snapshot(Map<String, String> parameters, JsonBrowser response)
    {
        if (response == null || response.get("code").asLong(-1) != 0) return null;
        JsonBrowser view = response.get("data").get("View");
        String bv = view.get("bvid").text();
        if (bv == null || !bv.matches("BV[0-9A-Za-z]{10}")
            || (parameters.containsKey("bvid") && !bv.equals(parameters.get("bvid")))
            || (parameters.containsKey("aid") && !parameters.get("aid").equals(view.get("aid").text()))) return null;
        List<Map<String, Object>> pages = new ArrayList<>();
        if (view.get("pages").values().size() > 512) return null;
        for (JsonBrowser page : view.get("pages").values())
        {
            if (page.get("cid").asLong(0) <= 0) return null;
            Map<String, Object> part = new HashMap<>();
            part.put("cid", page.get("cid").asLong(0));
            part.put("duration", page.get("duration").asLong(0));
            part.put("part", page.get("part").text());
            pages.add(part);
        }
        if (pages.isEmpty() && view.get("cid").asLong(0) <= 0) return null;
        Map<String, Object> safe = new HashMap<>();
        safe.put("bvid", bv);
        safe.put("aid", view.get("aid").asLong(0));
        safe.put("cid", view.get("cid").asLong(0));
        safe.put("duration", view.get("duration").asLong(0));
        safe.put("title", view.get("title").text());
        safe.put("pic", view.get("pic").text());
        safe.put("owner", Map.of("name", view.get("owner").get("name").textOrDefault("未知作者")));
        safe.put("pages", pages);
        try
        {
            JsonBrowser result = JsonBrowser.newMap();
            result.put("code", 0);
            result.put("data", Map.of("View", safe));
            String json = result.format();
            return json.length() <= 65536 ? json : null;
        }
        catch (IOException e) { return null; }
    }
}
