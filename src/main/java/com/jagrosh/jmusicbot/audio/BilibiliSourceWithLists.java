package com.jagrosh.jmusicbot.audio;

import com.github.parrotxray.lavabili.source.BilibiliAudioSourceManager;
import com.sedmelluq.discord.lavaplayer.player.AudioPlayerManager;
import com.sedmelluq.discord.lavaplayer.source.AudioSourceManager;
import com.sedmelluq.discord.lavaplayer.tools.FriendlyException;
import com.sedmelluq.discord.lavaplayer.tools.JsonBrowser;
import com.sedmelluq.discord.lavaplayer.track.*;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;

import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;

/** Keeps SonwMusicBot's list URLs while using the unmodified official plugin for every track. */
final class BilibiliSourceWithLists implements AudioSourceManager
{
    private final BilibiliAudioSourceManager delegate;
    private final int pageLimit;

    BilibiliSourceWithLists(BilibiliAudioSourceManager delegate, int pageLimit)
    {
        this.delegate = delegate;
        this.pageLimit = pageLimit;
    }

    @Override
    public AudioItem loadItem(AudioPlayerManager manager, AudioReference reference)
    {
        AudioPlaylist list = BilibiliPlaylistLoader.load(reference.identifier, pageLimit, this::fetch,
            url -> delegate.loadItem(manager, new AudioReference(url, null)));
        return list != null ? list : delegate.loadItem(manager, reference);
    }

    private JsonBrowser fetch(String url)
    {
        // Reuse the updated plugin's HTTP context, including its device/cookie handling.
        try (CloseableHttpResponse response = delegate.getHttpInterface().execute(new HttpGet(url)))
        {
            int status = response.getStatusLine().getStatusCode();
            if (status != 200)
                throw new IOException("Bilibili HTTP " + status);
            return JsonBrowser.parse(response.getEntity().getContent());
        }
        catch (IOException e)
        {
            throw new FriendlyException("无法读取 Bilibili 列表，请稍后重试。",
                FriendlyException.Severity.COMMON, e);
        }
    }

    @Override public String getSourceName() { return delegate.getSourceName(); }
    @Override public boolean isTrackEncodable(AudioTrack track) { return delegate.isTrackEncodable(track); }
    @Override public void encodeTrack(AudioTrack track, DataOutput output) throws IOException { delegate.encodeTrack(track, output); }
    @Override public AudioTrack decodeTrack(AudioTrackInfo info, DataInput input) throws IOException { return delegate.decodeTrack(info, input); }
    @Override public void shutdown() { delegate.shutdown(); }
}
