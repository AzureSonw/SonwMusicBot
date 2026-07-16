package dev.arbjerg.lavalink.api;

import com.sedmelluq.discord.lavaplayer.player.DefaultAudioPlayerManager;

@FunctionalInterface
public interface AudioPlayerManagerConfiguration {
    void configure(DefaultAudioPlayerManager manager);
}
