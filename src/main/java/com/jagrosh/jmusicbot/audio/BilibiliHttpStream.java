package com.jagrosh.jmusicbot.audio;

import com.sedmelluq.discord.lavaplayer.tools.io.HttpInterface;
import com.sedmelluq.discord.lavaplayer.tools.io.PersistentHttpStream;
import org.apache.http.HttpResponse;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.client.methods.HttpGet;
import java.io.IOException;
import java.io.InterruptedIOException;
import java.net.URI;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

/** Bounded startup failover only: never restart partially played audio on another CDN. */
final class BilibiliHttpStream extends PersistentHttpStream
{
    private static final Pattern RANGE = Pattern.compile("bytes (\\d+)-(\\d+)/(\\d+)");
    private final List<URI> urls;
    private int candidate;
    private boolean opened;

    BilibiliHttpStream(HttpInterface http, List<URI> urls)
    {
        super(http, urls.get(0), null);
        this.urls = List.copyOf(urls.subList(0, Math.min(urls.size(), BilibiliPlaybackUrls.MAX_URLS)));
    }

    @Override protected URI getConnectUrl() { return urls.get(candidate); }

    @Override protected HttpGet getConnectRequest()
    {
        HttpGet request = super.getConnectRequest();
        // Lavaplayer normally omits Range at position zero. Some official Bilibili CDNs require it.
        request.setHeader("Range", "bytes=" + position + "-");
        request.setHeader("Referer", "https://www.bilibili.com/");
        request.setHeader("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36");
        request.setHeader("Accept", "*/*");
        request.setHeader("Accept-Encoding", "identity");
        request.setConfig(RequestConfig.custom().setConnectTimeout(10000).setSocketTimeout(15000)
            .setConnectionRequestTimeout(10000).setRedirectsEnabled(false).build());
        return request;
    }

    @Override protected void connect(boolean skipStatusCheck) throws IOException
    {
        if (getCurrentResponse() != null) return;
        while (true)
        {
            int status;
            try
            {
                // Inspect the status ourselves: do not repeat a failed URL or read its error body as MP4.
                super.connect(true);
                status = getCurrentResponse().getStatusLine().getStatusCode();
            }
            catch (IOException e)
            {
                close();
                if (Thread.currentThread().isInterrupted()
                    || (e instanceof InterruptedIOException && !(e instanceof java.net.SocketTimeoutException))) throw e;
                if (next()) continue;
                throw BilibiliErrors.problem("无法连接 Bilibili 音频节点，请稍后重新点播。");
            }
            catch (NumberFormatException e)
            {
                close();
                throw BilibiliErrors.problem("Bilibili 返回了无效的音频长度信息。");
            }
            if (status == 200 || status == 206)
            {
                try { validateContent(getCurrentResponse(), status); }
                catch (RuntimeException e) { close(); throw e; }
                opened = true;
                return;
            }
            close();
            if (Set.of(500, 502, 503, 504, 567).contains(status) && next()) continue;
            if (status == 567)
                throw BilibiliErrors.problem(opened
                    ? "播放期间 Bilibili 音频节点拒绝了请求（HTTP 567），已停止以避免重复播放，请稍后重新点播。"
                    : "Bilibili 音频节点拒绝了请求（HTTP 567），可用的官方地址均未成功，请稍后重试。");
            BilibiliErrors.checkHttp(status);
            throw BilibiliErrors.problem("Bilibili 音频响应无效，请稍后重试。");
        }
    }

    private boolean next()
    {
        if (opened || position != 0 || candidate + 1 >= urls.size()) return false;
        candidate++;
        // An error response's Content-Length must not become the audio length.
        contentLength = Long.MAX_VALUE;
        return true;
    }

    private void validateContent(HttpResponse response, int status)
    {
        String type = response.getFirstHeader("Content-Type") == null ? "" : response.getFirstHeader("Content-Type").getValue();
        if (response.getEntity() == null || type.toLowerCase(java.util.Locale.ROOT).contains("text/html"))
            throw BilibiliErrors.problem("Bilibili 返回的不是有效音频。");
        var encoding = response.getFirstHeader("Content-Encoding");
        if (encoding != null && !"identity".equalsIgnoreCase(encoding.getValue()))
            throw BilibiliErrors.problem("Bilibili 返回了不支持的音频传输编码。");
        if (status == 206)
        {
            var header = response.getFirstHeader("Content-Range");
            var range = RANGE.matcher(header == null ? "" : header.getValue());
            try
            {
                if (!range.matches()) throw new IllegalArgumentException();
                long start = Long.parseLong(range.group(1)), end = Long.parseLong(range.group(2)), total = Long.parseLong(range.group(3));
                var length = response.getFirstHeader("Content-Length");
                if (start != position || end < start || total <= end || end != total - 1
                    || (length != null && Long.parseLong(length.getValue()) != end - start + 1)
                    || (opened && contentLength != Long.MAX_VALUE && contentLength != total)) throw new IllegalArgumentException();
                contentLength = total;
            }
            catch (IllegalArgumentException e) { throw BilibiliErrors.problem("Bilibili 音频分段响应不匹配，已停止播放以避免音频错位。"); }
        }
        else if (position != 0 || contentLength == 0)
            throw BilibiliErrors.problem("Bilibili 音频节点不支持本次定位请求。");
    }
}
