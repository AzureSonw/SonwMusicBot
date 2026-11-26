package com.jagrosh.jmusicbot.unit.commands;

import com.jagrosh.jdautilities.command.CommandEvent;
import com.jagrosh.jmusicbot.Bot;
import com.jagrosh.jmusicbot.commands.general.SettingsCmd;
import com.jagrosh.jmusicbot.commands.owner.DebugCmd;
import com.jagrosh.jmusicbot.commands.owner.PlaylistCmd;
import com.jagrosh.jmusicbot.utils.OtherUtil;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class LocalizedOutputTest {
    @Test void settingsHelpUsesCorrectLabel() {
        var command = new SettingsCmd(mock(Bot.class, RETURNS_DEEP_STUBS));
        assertEquals("settings", command.getName());
        assertEquals("机器人设置", command.getHelp());
    }

    @Test void playlistHelpKeepsExecutableSubcommands() {
        var bot = mock(Bot.class, RETURNS_DEEP_STUBS);
        var event = mock(CommandEvent.class, RETURNS_DEEP_STUBS);
        when(event.getClient().getPrefix()).thenReturn("?");
        when(event.getClient().getWarning()).thenReturn("!");
        new PlaylistCmd(bot).execute(event);
        var text = ArgumentCaptor.forClass(String.class);
        verify(event).reply(text.capture());
        String menu = text.getValue();
        assertTrue(menu.startsWith("! 播放列表指令:"));
        assertTrue(menu.contains("`?playlist make <播放列表名字>` - 创建新的播放列表"));
        assertTrue(menu.contains("`?playlist setdefault <播放列表名字|NONE>`"));
        assertTrue(menu.contains("`?playlist delete <name>`")); // untranslated argument
        assertTrue(menu.contains("`?playlist append "));
        assertTrue(menu.contains("`?playlist all "));
    }

    @Test void debugReportUsesDisplayBrandAndKeepsVersionAndUptime() throws Exception {
        var bot = mock(Bot.class, RETURNS_DEEP_STUBS);
        when(bot.getStartTime()).thenReturn(Instant.now());
        var command = new DebugCmd(bot);
        var method = DebugCmd.class.getDeclaredMethod("appendJMusicBotSection", StringBuilder.class);
        method.setAccessible(true);
        var output = new StringBuilder();
        try (var util = mockStatic(OtherUtil.class)) {
            util.when(OtherUtil::getCurrentVersion).thenReturn("0.6.9.2");
            method.invoke(command, output);
        }
        String text = output.toString();
        assertTrue(text.contains(" SONWMUSICBOT "));
        assertFalse(text.contains(" JMUSICBOT "));
        assertTrue(text.contains("Version: 0.6.9.2"));
        assertTrue(text.contains("Uptime: "));
        assertEquals("debug", command.getName());
    }
}
