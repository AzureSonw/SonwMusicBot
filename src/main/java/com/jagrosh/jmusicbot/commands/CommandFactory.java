package com.jagrosh.jmusicbot.commands;

import com.jagrosh.jdautilities.command.CommandClient;
import com.jagrosh.jdautilities.command.CommandClientBuilder;
import com.jagrosh.jdautilities.examples.command.AboutCommand;
import com.jagrosh.jdautilities.examples.command.PingCommand;
import com.jagrosh.jmusicbot.Bot;
import com.jagrosh.jmusicbot.BotConfig;
import com.jagrosh.jmusicbot.JMusicBot;
import com.jagrosh.jmusicbot.commands.admin.*;
import com.jagrosh.jmusicbot.commands.dj.*;
import com.jagrosh.jmusicbot.commands.general.SettingsCmd;
import com.jagrosh.jmusicbot.commands.music.*;
import com.jagrosh.jmusicbot.commands.owner.*;
import com.jagrosh.jmusicbot.settings.SettingsManager;
import com.jagrosh.jmusicbot.utils.OtherUtil;
import net.dv8tion.jda.api.OnlineStatus;
import org.jetbrains.annotations.NotNull;

import java.awt.*;

public class CommandFactory {

    public static CommandClient createCommandClient(BotConfig config, SettingsManager settings, Bot bot) {
        AboutCommand aboutCommand = createAboutCommand();

        CommandClientBuilder cb = new CommandClientBuilder()
            .setPrefix(config.getPrefix())
            .setAlternativePrefix(config.getAltPrefix())
            .setOwnerId(Long.toString(config.getOwnerId()))
            .setEmojis(config.getSuccess(), config.getWarning(), config.getError())
            .setHelpWord(config.getHelp())
            .setLinkedCacheSize(200)
            .setGuildSettingsManager(settings)
            .addCommands(aboutCommand,
                    new PingCommand(),
                    new SettingsCmd(bot),

                    // Lyrics functionality removed - JLyrics dependency removed
                    // new LyricsCmd(bot),
                    new NowPlayingCmd(bot),
                    new PlayCmd(bot),
                    new PlaylistsCmd(bot),
                    new QueueCmd(bot),
                    new RemoveCmd(bot),
                    new SearchCmd(bot),
                    new SCSearchCmd(bot),
                    new SeekCmd(bot),
                    new ShuffleCmd(bot),
                    new SkipCmd(bot),

                    new ForceRemoveCmd(bot),
                    new ForceskipCmd(bot),
                    new MoveTrackCmd(bot),
                    new PauseCmd(bot),
                    new PlaynextCmd(bot),
                    new RepeatCmd(bot),
                    new SkiptoCmd(bot),
                    new StopCmd(bot),
                    new VolumeCmd(bot),

                    new PrefixCmd(bot),
                    new QueueTypeCmd(bot),
                    new SetdjCmd(bot),
                    new SkipratioCmd(bot),
                    new SettcCmd(bot),
                    new SetvcCmd(bot),

                    new AutoplaylistCmd(bot),
                    new DebugCmd(bot),
                    new PlaylistCmd(bot),
                    new SetavatarCmd(bot),
                    new SetgameCmd(bot),
                    new SetnameCmd(bot),
                    new SetstatusCmd(bot),
                    new ShutdownCmd(bot)
            ).setManualUpsert(true);

        if (config.useEval())
            cb.addCommand(new EvalCmd(bot));

        if (config.getStatus() != OnlineStatus.UNKNOWN)
            cb.setStatus(config.getStatus());

        if (config.getGame() == null)
            cb.useDefaultGame();
        else if (config.isGameNone())
            cb.setActivity(null);
        else
            cb.setActivity(config.getGame());

        return cb.build();
    }

    private static @NotNull AboutCommand createAboutCommand() {
        AboutCommand aboutCommand = new AboutCommand(Color.BLUE.brighter(),
                "a music bot that is [easy to host yourself!](" + OtherUtil.PROJECT_PAGE_URL + ") (v" + OtherUtil.getCurrentVersion() + ")",
                new String[]{"High-quality music playback", "FairQueue™ Technology", "Easy to host yourself"},
                JMusicBot.RECOMMENDED_PERMS);
        aboutCommand.setIsAuthor(false);
        aboutCommand.setReplacementCharacter("\uD83C\uDFB6");
        return aboutCommand;
    }
}
