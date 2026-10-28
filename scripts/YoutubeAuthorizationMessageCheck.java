package com.jagrosh.jmusicbot;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.core.spi.FilterReply;
import com.jagrosh.jmusicbot.utils.YoutubeOauth2TokenHandler;
import org.slf4j.LoggerFactory;

/** Offline prompt-only regression. No bot startup, token handling or external messages. */
public class YoutubeAuthorizationMessageCheck
{
    private static int checks;

    public static void main(String[] args)
    {
        YoutubeOauth2TokenHandler handler = new YoutubeOauth2TokenHandler();
        Logger logger = (Logger) LoggerFactory.getLogger("dev.lavalink.youtube.http.YoutubeOauth2Handler");
        String format = "OAUTH INTEGRATION: To give youtube-source access to your account, go to {} and enter code {}";
        String url = "https://www.google.com/device";
        require(handler.getData() == null, "no authorization data before a prompt");

        String previousCode = null;
        for (String code : new String[] { "TEST-CODE-ONE", "TEST-CODE-TWO" })
        {
            FilterReply reply = handler.decide(null, logger, Level.INFO, format, new Object[] { url, code }, null);
            require(reply == FilterReply.NEUTRAL, "upstream prompt capture remains unchanged");
            require(handler.getData().getAuthorisationUrl().equals(url), "authorization URL is preserved");
            require(handler.getData().getCode().equals(code), "current code is captured unchanged");
            String message = Listener.youtubeAuthorizationMessage(handler.getData());
            String expected = "# 请不要用您的主账户登录！#\n\n"
                    + "## 创建或使用一个小号登录！##\n\n"
                    + "打开 https://www.google.com/device 登录后输入代码 **" + code + "**\n\n"
                    + "验证完后请重启机器人";
            require(message.equals(expected), "exact requested Chinese prompt and markers");
            require(!message.contains("JMusicBot") && !message.contains("SonwMusicBot 授权提示"), "no extra branding title");
            require(message.contains("**" + code + "**"), "current code is bold");
            require(!message.contains("##" + code + "##"), "old code markers are removed");
            require(!message.contains("DO NOT AUTHORISE") && !message.contains("burner"), "old English warning removed");
            require(message.indexOf(code) == message.lastIndexOf(code), "code appears exactly once");
            if (previousCode != null) require(!message.contains(previousCode), "renewed prompt does not reuse the old code");
            previousCode = code;
        }
        System.out.println("YOUTUBE AUTHORIZATION MESSAGE OFFLINE PASS: " + checks + " assertions");
    }

    private static void require(boolean condition, String label)
    {
        if (!condition) throw new AssertionError(label);
        checks++;
    }
}
