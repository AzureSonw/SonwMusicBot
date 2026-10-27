package com.jagrosh.jmusicbot.audio;

import com.github.parrotxray.lavabili.source.BilibiliAudioTrack;
import com.sedmelluq.discord.lavaplayer.container.mpeg.MpegAudioTrack;
import com.sedmelluq.discord.lavaplayer.track.AudioTrack;
import com.sedmelluq.discord.lavaplayer.track.DelegatedAudioTrack;
import com.sedmelluq.discord.lavaplayer.track.playback.LocalAudioTrackExecutor;

/** Keeps the official metadata and wire format, replacing only the audio transport. */
final class SonwBilibiliAudioTrack extends DelegatedAudioTrack
{
    final BilibiliAudioTrack original;
    private final BilibiliSourceWithLists source;

    SonwBilibiliAudioTrack(BilibiliAudioTrack original, BilibiliSourceWithLists source)
    {
        super(original.getInfo());
        this.original = original;
        this.source = source;
    }

    @Override public void process(LocalAudioTrackExecutor executor) throws Exception
    {
        var urls = source.playbackUrls(original);
        try (var http = source.mediaInterface(); var stream = new BilibiliHttpStream(http, urls))
        {
            processDelegate(new MpegAudioTrack(trackInfo, stream), executor);
        }
    }

    @Override protected AudioTrack makeShallowClone() { return new SonwBilibiliAudioTrack(original, source); }
    @Override public BilibiliSourceWithLists getSourceManager() { return source; }
}
