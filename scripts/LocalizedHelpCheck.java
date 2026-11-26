package com.jagrosh.jmusicbot.commands;

import com.jagrosh.jdautilities.command.Command;
import com.jagrosh.jdautilities.command.CommandEvent;
import com.jagrosh.jdautilities.examples.command.AboutCommand;
import com.jagrosh.jdautilities.examples.command.PingCommand;
import com.jagrosh.jmusicbot.utils.OtherUtil;
import java.awt.Color;
import java.util.List;

public class LocalizedHelpCheck {
    private static class Entry extends Command {
        Entry(String command, String args, boolean owner, boolean hide, Category group) {
            name = command;
            arguments = args;
            ownerCommand = owner;
            hidden = hide;
            category = group;
            help = "说明";
        }
        @Override protected void execute(CommandEvent ignored) {}
    }
    public static void main(String[] args) {
        var general = List.of(new AboutCommand(Color.BLUE, "description", new String[0]), new PingCommand());
        String generalHelp = HelpMessage.format("Bot", "!", general, false, null, null);
        String expectedGeneral = "**Bot**指令:\n\n`!about` - 机器人信息\n`!ping` - 检查机器人延迟";
        if (!expectedGeneral.equals(generalHelp)) throw new AssertionError(generalHelp);

        var group = new Command.Category("音乐");
        List<Command> commands = List.of(new Entry("play", "<歌名>", false, false, group),
                new Entry("playlist", null, true, false, group), new Entry("hidden", null, false, true, group));
        String ordinary = HelpMessage.format("Bot", "?", commands, false, null, null);
        String expected = "**Bot**指令:\n\n\n  __音乐__:\n\n`?play <歌名>` - 说明";
        if (!expected.equals(ordinary)) throw new AssertionError(ordinary);
        String owner = HelpMessage.format("Bot", "?", commands, true, "Owner", "invite");
        if (!(expected + "\n`?playlist` - 说明\n\n有问题请找 **Owner** or join invite").equals(owner))
            throw new AssertionError(owner);
        String update = String.format(OtherUtil.NEW_VERSION_AVAILABLE, "1.0", "2.0");
        if (!update.equals("有更新啦~ \n现版本: 1.0\n新版本: 2.0\n\n请前往 "
                + OtherUtil.PROJECT_PAGE_URL + " 下载最新版本~")) throw new AssertionError(update);
        System.out.println("Localized help visibility, prefixes, owner contact and update template: PASS");
    }
}
