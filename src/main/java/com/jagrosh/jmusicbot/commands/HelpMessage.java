package com.jagrosh.jmusicbot.commands;

import com.jagrosh.jdautilities.command.Command;
import com.jagrosh.jdautilities.command.CommandClient;
import com.jagrosh.jdautilities.command.CommandEvent;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.entities.channel.ChannelType;

import java.util.Objects;

final class HelpMessage {
    private HelpMessage() {}

    static void send(CommandEvent event, boolean mentionPrefix) {
        CommandClient client = event.getClient();
        String prefix = client.getTextualPrefix() + (mentionPrefix ? " " : "");
        User owner = event.getJDA().getUserById(client.getOwnerId());
        String text = format(event.getSelfUser().getName(), prefix, client.getCommands(), event.isOwner(),
                owner == null ? null : owner.getName(), client.getServerInvite());
        event.replyInDm(text, message -> {
            if (event.isFromType(ChannelType.TEXT)) event.reactSuccess();
        }, failure -> event.replyWarning("发不了机器人指令帮助页！你把私信关掉了！"));
    }

    static String format(String bot, String prefix, Iterable<Command> commands, boolean isOwner,
                         String owner, String invite) {
        StringBuilder text = new StringBuilder("**").append(bot).append("**指令:\n");
        Command.Category previous = null;
        for (Command command : commands) {
            if (command.isHidden() || (command.isOwnerCommand() && !isOwner)) continue;
            Command.Category category = command.getCategory();
            if (!Objects.equals(category, previous)) {
                text.append("\n\n  __").append(category == null ? "No Category" : category.getName()).append("__:\n");
                previous = category;
            }
            text.append("\n`").append(prefix).append(command.getName());
            if (command.getArguments() != null) text.append(' ').append(command.getArguments());
            text.append("` - ").append(switch (command.getName()) {
                case "about" -> "机器人信息";
                case "ping" -> "检查机器人延迟";
                default -> command.getHelp();
            });
        }
        if (owner != null) {
            text.append("\n\n有问题请找 **").append(owner).append("**");
            if (invite != null) text.append(" or join ").append(invite);
        }
        return text.toString();
    }
}
