package com.jagrosh.jmusicbot.utils;

import com.jagrosh.jmusicbot.Bot;
import com.jagrosh.jmusicbot.audio.AudioHandler;
import com.jagrosh.jmusicbot.audio.NowPlayingInfo;
import com.jagrosh.jmusicbot.audio.RequestMetadata;
import com.sedmelluq.discord.lavaplayer.source.youtube.YoutubeAudioTrack;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.utils.messages.MessageCreateBuilder;
import net.dv8tion.jda.api.utils.messages.MessageCreateData;

public class MessageFormatter {

    public static MessageCreateData buildNowPlayingMessage(Bot bot, NowPlayingInfo info) {
        if (info.track == null)
            return buildNoMusicPlayingMessage(bot, info);

        MessageCreateBuilder mb = new MessageCreateBuilder();
        mb.addContent(FormatUtil.filter(bot.getConfig().getSuccess() + " **我在 " + info.guild.getSelfMember().getVoiceState().getChannel().getAsMention() + " 里放音乐呢~**"));

        EmbedBuilder eb = new EmbedBuilder();
        eb.setColor(info.guild.getSelfMember().getColors().getPrimary());

        RequestMetadata rm = info.track.getUserData(RequestMetadata.class);
        if (rm != null && rm.getOwner() != 0L) {
            User u = info.guild.getJDA().getUserById(rm.user.id);
            if (u == null)
                eb.setAuthor(FormatUtil.formatUsername(rm.user), null, rm.user.avatar);
            else
                eb.setAuthor(FormatUtil.formatUsername(u), null, u.getEffectiveAvatarUrl());
        }

        try {
            eb.setTitle(info.track.getInfo().title, info.track.getInfo().uri);
        } catch (Exception e) {
            eb.setTitle(info.track.getInfo().title);
        }

        if (info.track instanceof YoutubeAudioTrack && bot.getConfig().useNPImages()) {
            eb.setThumbnail("https://img.youtube.com/vi/" + info.track.getIdentifier() + "/mqdefault.jpg");
        }

        if (info.track.getInfo().author != null && !info.track.getInfo().author.isEmpty())
            eb.setFooter("来源: " + info.track.getInfo().author, null);

        double progress = (double) info.position / info.duration;
        String statusEmoji = info.isPaused ? AudioHandler.PAUSE_EMOJI : AudioHandler.PLAY_EMOJI;

        eb.setDescription(statusEmoji
                + " " + FormatUtil.progressBar(progress)
                + " `[" + TimeUtil.formatTime(info.position) + "/" + TimeUtil.formatTime(info.duration) + "]` "
                + FormatUtil.volumeIcon(info.volume));

        return mb.setEmbeds(eb.build()).build();
    }

    public static MessageCreateData buildNoMusicPlayingMessage(Bot bot, NowPlayingInfo info) {
        return new MessageCreateBuilder()
                .setContent(FormatUtil.filter(bot.getConfig().getSuccess() + " **正在播放...**"))
                .setEmbeds(new EmbedBuilder()
                        .setTitle("没有东西在播放~")
                        .setDescription(AudioHandler.STOP_EMOJI + " " + FormatUtil.progressBar(-1) + " " + FormatUtil.volumeIcon(info.volume))
                        .setColor(info.guild.getSelfMember().getColors().getPrimary())
                        .build()).build();
    }
}
