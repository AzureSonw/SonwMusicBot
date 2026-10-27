package com.jagrosh.jmusicbot.audio;

import com.sedmelluq.discord.lavaplayer.tools.FriendlyException;
import com.sedmelluq.discord.lavaplayer.tools.JsonBrowser;
import com.sedmelluq.discord.lavaplayer.tools.io.HttpInterface;
import org.apache.http.client.methods.*;
import org.apache.http.entity.ByteArrayEntity;
import org.apache.http.message.BasicHttpResponse;
import org.apache.http.ProtocolVersion;
import java.io.*;
import java.net.URI;
import java.net.SocketTimeoutException;
import java.util.*;

/** Offline transport fixtures; no account, browser, Discord or public network access. */
public final class Bilibili567Check
{
    private static int checks;
    private static final URI PRIMARY = URI.create("https://primary.bilivideo.com/a.m4s?signature=fixture-secret");
    private static final URI BACKUP = URI.create("https://backup.bilivideo.com/a.m4s?signature=fixture-secret");

    public static void main(String[] args) throws Exception
    {
        urls();
        transport();
        System.out.println("OFFLINE 567 PASS: " + checks + " assertions");
    }

    private static void urls() throws Exception
    {
        var response = json("{\"code\":0,\"data\":{\"dash\":{\"audio\":["
            + "{\"id\":30280,\"bandwidth\":100,\"codecs\":\"mp4a.40.2\",\"baseUrl\":\"https://low.bilivideo.com/a\"},"
            + "{\"id\":30216,\"bandwidth\":200,\"codecs\":\"mp4a.40.2\",\"baseUrl\":\"" + PRIMARY + "\","
            + "\"base_url\":\"" + PRIMARY + "\",\"backupUrl\":[\"" + BACKUP + "\"],"
            + "\"backup_url\":[\"" + BACKUP + "\",\"https://upos-hz-mirrorakam.akamaized.net/a\"]},"
            + "{\"id\":30251,\"bandwidth\":1000,\"codecs\":\"fLaC\",\"baseUrl\":\"https://lossless.bilivideo.com/a\"}]}}}");
        var urls = BilibiliPlaybackUrls.video(response);
        require(urls.size() == 3, "camel/snake case backup arrays deduplicated");
        require(urls.get(0).equals(PRIMARY) && urls.get(1).equals(BACKUP), "highest AAC bandwidth, primary order and signature retained");
        require(urls.get(2).getHost().equals("upos-hz-mirrorakam.akamaized.net"), "official Akamai mirror allowed");
        for (String bad : List.of("https://bilivideo.com.evil.invalid/a", "https://evilbilivideo.com/a", "https://127.0.0.1/a",
            "https://[::1]/a", "http://primary.bilivideo.com/a", "https://user@primary.bilivideo.com/a",
            "https://primary.bilivideo.com:444/a", "https://primary.bilivideo.com/a#fragment", "file:///a", "bad url",
            "https://unrelated.akamaized.net/a"))
            expectFriendly(() -> BilibiliPlaybackUrls.audio(json("{\"code\":0,\"data\":{\"cdns\":[\"" + bad + "\"]}}")));
        var bounded = BilibiliPlaybackUrls.audio(json("{\"code\":0,\"data\":{\"cdns\":["
            + "\"https://a.bilivideo.cn/1\",\"https://a.bilivideo.cn/2\",\"https://a.bilivideo.cn/3\","
            + "\"https://a.bilivideo.cn/4\",\"https://a.bilivideo.cn/5\"]}}"));
        require(bounded.size() == 4, "bounded CDN list");
        expectFriendly(() -> BilibiliPlaybackUrls.video(json("{\"code\":0,\"data\":{\"dash\":{\"audio\":[]}}}")));
        expectFriendly(() -> BilibiliPlaybackUrls.video(json("{\"code\":-403,\"data\":{}}")));
        expectFriendly(() -> BilibiliPlaybackUrls.video(json("{\"code\":-412,\"data\":{}}")));
    }

    private static void transport() throws Exception
    {
        Response rejected = response(567, null, 8), good = response(206, "bytes 0-7/8", 8);
        FakeHttp http = new FakeHttp(rejected, good);
        try (var stream = new BilibiliHttpStream(http, List.of(PRIMARY, BACKUP)))
        {
            require(stream.read() == 0, "backup audio starts at byte zero");
            require(stream.getContentLength() == 8, "error body length cannot contaminate audio length");
            require(http.urls.equals(List.of(PRIMARY, BACKUP)), "each API URL attempted once, in order");
            require(rejected.closed, "failed response closed before fallback");
        }
        require(good.closed, "successful response closed");

        http = new FakeHttp(response(200, null, 8));
        try (var stream = new BilibiliHttpStream(http, List.of(PRIMARY)))
        {
            require(stream.read() == 0 && stream.getContentLength() == 8, "200 accepted only from start");
        }

        Response first = response(206, "bytes 0-999999/1000000", 1000000);
        http = new FakeHttp(first, response(206, "bytes 700000-999999/1000000", 300000));
        try (var stream = new BilibiliHttpStream(http, List.of(PRIMARY, BACKUP)))
        {
            stream.read();
            stream.seek(700000);
            require(stream.read() == 0, "seek opens response");
            require(http.ranges.equals(List.of("bytes=0-", "bytes=700000-")), "initial and seek range headers");
            require(http.urls.equals(List.of(PRIMARY, PRIMARY)), "seek remains on selected representation/CDN");
            require(stream.getContentLength() == 1000000 && stream.getPosition() == 700001, "range total and position retained");
            require(first.closed, "seek closes previous response");
        }

        for (int status : List.of(401, 403, 404, 412, 429, 301, 302, 511))
        {
            var denied = new FakeHttp(response(status, null, 8));
            try (var stream = new BilibiliHttpStream(denied, List.of(PRIMARY, BACKUP)))
            {
                expectFriendly(() -> stream.read());
                require(denied.urls.size() == 1, "access/redirect response must not trigger fallback: " + status);
            }
        }
        for (int status : List.of(500, 502, 503, 504, 567))
        {
            var unavailable = new FakeHttp(response(status, null, 8), response(status, null, 8));
            try (var stream = new BilibiliHttpStream(unavailable, List.of(PRIMARY, BACKUP)))
            {
                expectFriendly(() -> stream.read());
                require(unavailable.urls.size() == 2, "bounded startup failover: " + status);
                require(unavailable.responses.stream().allMatch(r -> r.closed), "all failed responses closed");
            }
        }
        for (String range : Arrays.asList(null, "bytes 1-7/8", "bytes 0-8/8", "bytes 7-0/8", "bytes 0-7/*",
            "bytes 0-6/8", "bytes 0-6/7", "bytes 0-7/999999999999999999999"))
        {
            var invalid = new FakeHttp(response(206, range, 8));
            try (var stream = new BilibiliHttpStream(invalid, List.of(PRIMARY, BACKUP)))
            {
                expectFriendly(() -> stream.read());
                require(invalid.urls.size() == 1, "malformed segment cannot silently switch audio");
            }
        }
        for (Response later : List.of(response(200, null, 1000000), response(567, null, 8),
            response(206, "bytes 700000-999999/1000001", 300000)))
        {
            var invalidSeek = new FakeHttp(response(206, "bytes 0-999999/1000000", 1000000), later);
            try (var stream = new BilibiliHttpStream(invalidSeek, List.of(PRIMARY, BACKUP)))
            {
                stream.read();
                stream.seek(700000);
                expectFriendly(() -> stream.read());
                require(invalidSeek.urls.equals(List.of(PRIMARY, PRIMARY)), "no replay/failover after playback starts");
            }
        }
        Response html = response(200, null, 8);
        html.setHeader("Content-Type", "text/html; charset=UTF-8");
        try (var stream = new BilibiliHttpStream(new FakeHttp(html), List.of(PRIMARY))) { expectFriendly(() -> stream.read()); }
        Response compressed = response(206, "bytes 0-7/8", 8);
        compressed.setHeader("Content-Encoding", "gzip");
        try (var stream = new BilibiliHttpStream(new FakeHttp(compressed), List.of(PRIMARY))) { expectFriendly(() -> stream.read()); }
        Response invalidLength = response(200, null, 8);
        invalidLength.setHeader("Content-Length", "fixture-secret");
        try (var stream = new BilibiliHttpStream(new FakeHttp(invalidLength), List.of(PRIMARY))) { expectFriendly(() -> stream.read()); }
        require(invalidLength.closed, "malformed length response closed");
        FakeHttp timeout = new FakeHttp(new SocketTimeoutException("fixture-secret"), response(206, "bytes 0-7/8", 8));
        try (var stream = new BilibiliHttpStream(timeout, List.of(PRIMARY, BACKUP)))
        {
            require(stream.read() == 0 && timeout.urls.size() == 2, "startup timeout uses bounded official backup");
        }
        FakeHttp interrupted = new FakeHttp(new InterruptedIOException("cancelled"));
        try (var stream = new BilibiliHttpStream(interrupted, List.of(PRIMARY, BACKUP)))
        {
            try { stream.read(); throw new AssertionError("Expected cancellation"); }
            catch (InterruptedIOException expected) { require(interrupted.urls.size() == 1, "cancellation is not retried"); }
        }
    }

    private static Response response(int code, String range, int length)
    {
        Response r = new Response(code);
        r.setEntity(new ByteArrayEntity(new byte[length]));
        r.setHeader("Content-Length", Integer.toString(length));
        if (range != null) r.setHeader("Content-Range", range);
        return r;
    }
    private static final class Response extends BasicHttpResponse implements CloseableHttpResponse
    {
        boolean closed;
        Response(int status) { super(new ProtocolVersion("HTTP", 1, 1), status, "fixture"); }
        @Override public void close() { closed = true; }
    }
    private static final class FakeHttp extends HttpInterface
    {
        final Deque<Object> replies = new ArrayDeque<>();
        final List<URI> urls = new ArrayList<>();
        final List<String> ranges = new ArrayList<>();
        final List<Response> responses = new ArrayList<>();
        FakeHttp(Object... replies) { super(null, null, false, null); this.replies.addAll(Arrays.asList(replies)); }
        @Override public CloseableHttpResponse execute(HttpUriRequest request) throws IOException
        {
            require(request.getFirstHeader("Cookie") == null, "no credentials in media requests");
            require(request.getFirstHeader("User-Agent") != null, "media user agent");
            require("https://www.bilibili.com/".equals(request.getFirstHeader("Referer").getValue()), "media referer");
            require("identity".equals(request.getFirstHeader("Accept-Encoding").getValue()), "uncompressed byte ranges");
            require(!((HttpGet) request).getConfig().isRedirectsEnabled(), "no arbitrary CDN redirects");
            urls.add(request.getURI());
            ranges.add(request.getFirstHeader("Range").getValue());
            Object reply = replies.removeFirst();
            if (reply instanceof IOException e) throw e;
            responses.add((Response) reply);
            return (Response) reply;
        }
    }
    private static JsonBrowser json(String text) throws IOException { return JsonBrowser.parse(text); }
    private static void require(boolean value, String message) { if (!value) throw new AssertionError(message); checks++; }
    @FunctionalInterface private interface Checked { void run() throws Exception; }
    private static void expectFriendly(Checked action) throws Exception
    {
        try { action.run(); }
        catch (FriendlyException expected)
        {
            require(!expected.toString().contains("fixture-secret") && !expected.toString().contains("https://"), "error redacts signed URLs");
            require(expected.getCause() == null, "error does not leak raw request through cause");
            return;
        }
        throw new AssertionError("Expected a friendly failure");
    }
}
