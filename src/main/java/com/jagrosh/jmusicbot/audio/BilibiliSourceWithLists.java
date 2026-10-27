package com.jagrosh.jmusicbot.audio;

import com.github.parrotxray.lavabili.source.BilibiliAudioSourceManager;
import com.github.parrotxray.lavabili.source.BilibiliAudioTrack;
import com.sedmelluq.discord.lavaplayer.player.AudioPlayerManager;
import com.sedmelluq.discord.lavaplayer.source.AudioSourceManager;
import com.sedmelluq.discord.lavaplayer.tools.JsonBrowser;
import com.sedmelluq.discord.lavaplayer.track.*;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.config.RequestConfig;
import com.sedmelluq.discord.lavaplayer.tools.io.HttpClientTools;
import com.sedmelluq.discord.lavaplayer.tools.io.HttpInterface;
import com.sedmelluq.discord.lavaplayer.tools.io.HttpInterfaceManager;

import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;
import java.net.URI;
import java.util.*;

/** Official metadata/signing with SonwMusicBot's list support and bounded audio transport. */
final class BilibiliSourceWithLists implements AudioSourceManager
{
    private final BilibiliAudioSourceManager delegate;
    private final int pageLimit;
    private final HttpInterfaceManager media = HttpClientTools.createCookielessThreadLocalManager();

    BilibiliSourceWithLists(BilibiliAudioSourceManager delegate, int pageLimit)
    {
        this.delegate = delegate;
        this.pageLimit = pageLimit;
        media.configureBuilder(builder -> builder.disableAutomaticRetries().disableRedirectHandling());
    }

    @Override
    public AudioItem loadItem(AudioPlayerManager manager, AudioReference reference)
    {
        String url = resolveShortLink(reference.identifier);
        AudioPlaylist list = BilibiliPlaylistLoader.load(url, pageLimit, this::fetch,
            video -> loadSingle(manager, new AudioReference(video, null)));
        return list != null ? list : loadSingle(manager, new AudioReference(url, reference.title));
    }

    private AudioItem loadSingle(AudioPlayerManager manager, AudioReference reference)
    {
        URI uri = BilibiliVideoLoader.parseUrl(reference.identifier);
        if (reference.identifier == null || (!reference.identifier.startsWith("bilisearch:")
            && (uri == null || !Set.of("bilibili.com", "www.bilibili.com", "m.bilibili.com")
                .contains(uri.getHost().toLowerCase(Locale.ROOT))))) return null;
        AudioItem video = BilibiliVideoLoader.load(reference.identifier, this::fetchDetail,
            (info, cid) -> new BilibiliAudioTrack(info, BilibiliAudioTrack.TrackType.VIDEO, info.identifier, cid, delegate));
        return wrap(video != null ? video : delegate.loadItem(manager, reference));
    }

    AudioItem wrap(AudioItem item)
    {
        if (item instanceof BilibiliAudioTrack track) return new SonwBilibiliAudioTrack(track, this);
        if (item instanceof AudioPlaylist list)
        {
            List<AudioTrack> tracks = new ArrayList<>();
            AudioTrack selected = null;
            for (AudioTrack track : list.getTracks())
            {
                AudioTrack wrapped = (AudioTrack) wrap(track);
                tracks.add(wrapped);
                if (track == list.getSelectedTrack()) selected = wrapped;
            }
            return new BasicAudioPlaylist(list.getName(), tracks, selected, list.isSearchResult());
        }
        return item;
    }

    HttpInterface mediaInterface() { return media.getInterface(); }

    List<URI> playbackUrls(BilibiliAudioTrack track)
    {
        if (track.getType() == BilibiliAudioTrack.TrackType.AUDIO)
        {
            if (!track.getId().matches("[0-9]+")) throw BilibiliErrors.problem("Bilibili 音频编号无效。");
            return BilibiliPlaybackUrls.audio(fetch("https://api.bilibili.com/audio/music-service-c/web/url?sid="
                + track.getId() + "&privilege=2&quality=2"));
        }
        if (track.getCid() == null || track.getCid() <= 0) throw BilibiliErrors.problem("Bilibili 视频分 P 编号无效。");
        Map<String, String> parameters = new HashMap<>(delegate.dmParams$lavabili_plugin());
        parameters.putAll(Map.of("bvid", track.getId(), "cid", track.getCid().toString(), "fnval", "16", "qn", "120"));
        String query;
        try { query = delegate.signWbi$lavabili_plugin(parameters); }
        catch (IllegalStateException e) { throw BilibiliErrors.problem("无法获取 Bilibili 音频请求签名，请稍后重试。"); }
        return BilibiliPlaybackUrls.video(fetch("https://api.bilibili.com/x/player/wbi/playurl?" + query));
    }

    private JsonBrowser fetchDetail(Map<String, String> parameters)
    {
        // Reuse the plugin's signer and session, but use the current detail endpoint.
        // Unlike the old unsigned /view fallback this also works without page-embedded JSON.
        String query;
        try { query = delegate.signWbi$lavabili_plugin(parameters); }
        catch (IllegalStateException e) { throw BilibiliErrors.problem("无法获取 Bilibili 请求签名，可能请求受限，请稍后重试。"); }
        return fetch("https://api.bilibili.com/x/web-interface/wbi/view/detail?" + query);
    }

    private String resolveShortLink(String url)
    {
        URI uri = BilibiliVideoLoader.parseUrl(url);
        if (uri == null || !Set.of("b23.tv", "www.b23.tv").contains(uri.getHost().toLowerCase(Locale.ROOT))) return url;
        if ("http".equalsIgnoreCase(uri.getScheme())) uri = URI.create("https" + uri.toString().substring(4));
        for (int redirects = 0; redirects < 5; redirects++)
        {
            try (CloseableHttpResponse response = delegate.getHttpInterface().execute(request(uri.toString())))
            {
                int status = response.getStatusLine().getStatusCode();
                if (Set.of(301, 302, 303, 307, 308).contains(status) && response.getFirstHeader("Location") != null)
                {
                    URI next = uri.resolve(response.getFirstHeader("Location").getValue());
                    if (!safeRedirect(next.toString())) throw BilibiliErrors.problem("Bilibili 短链接跳转到了不支持的地址，已停止加载。");
                    if (!next.getHost().toLowerCase(Locale.ROOT).endsWith("b23.tv")) return next.toString();
                    uri = next;
                    continue;
                }
                BilibiliErrors.checkHttp(status);
                throw BilibiliErrors.problem("无法解析 Bilibili 短链接，请使用完整视频链接。");
            }
            catch (IOException | IllegalArgumentException e) { throw BilibiliErrors.problem("无法读取 Bilibili 短链接，请稍后重试。"); }
        }
        throw BilibiliErrors.problem("Bilibili 短链接跳转次数过多，已停止加载。");
    }

    static boolean safeRedirect(String url)
    {
        URI uri = BilibiliVideoLoader.parseUrl(url);
        return uri != null && "https".equalsIgnoreCase(uri.getScheme())
            && Set.of("b23.tv", "www.b23.tv", "bilibili.com", "www.bilibili.com", "m.bilibili.com", "space.bilibili.com")
                .contains(uri.getHost().toLowerCase(Locale.ROOT));
    }

    private static HttpGet request(String url)
    {
        HttpGet request = new HttpGet(url);
        request.setConfig(RequestConfig.custom().setConnectTimeout(10000).setSocketTimeout(15000)
            .setConnectionRequestTimeout(10000).setRedirectsEnabled(false).build());
        return request;
    }

    private JsonBrowser fetch(String url)
    {
        // Reuse the updated plugin's HTTP context, including its device/cookie handling.
        try (CloseableHttpResponse response = delegate.getHttpInterface().execute(request(url)))
        {
            int status = response.getStatusLine().getStatusCode();
            BilibiliErrors.checkHttp(status);
            return JsonBrowser.parse(response.getEntity().getContent());
        }
        catch (IOException e)
        {
            throw BilibiliErrors.problem("无法读取 Bilibili 信息，请稍后重试。");
        }
    }

    @Override public String getSourceName() { return delegate.getSourceName(); }
    private AudioTrack unwrap(AudioTrack track) { return track instanceof SonwBilibiliAudioTrack sonw ? sonw.original : track; }
    @Override public boolean isTrackEncodable(AudioTrack track) { return delegate.isTrackEncodable(unwrap(track)); }
    @Override public void encodeTrack(AudioTrack track, DataOutput output) throws IOException { delegate.encodeTrack(unwrap(track), output); }
    @Override public AudioTrack decodeTrack(AudioTrackInfo info, DataInput input) throws IOException { return (AudioTrack) wrap(delegate.decodeTrack(info, input)); }
    @Override public void shutdown()
    {
        try { media.close(); }
        catch (IOException ignored) { }
        finally { delegate.shutdown(); }
    }
}
