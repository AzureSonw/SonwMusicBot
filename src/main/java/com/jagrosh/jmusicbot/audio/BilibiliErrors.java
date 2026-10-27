package com.jagrosh.jmusicbot.audio;

import com.sedmelluq.discord.lavaplayer.tools.FriendlyException;
import com.sedmelluq.discord.lavaplayer.tools.JsonBrowser;

/** Stable user-facing errors; never include response bodies or authentication values. */
final class BilibiliErrors
{
    /** Only an explicit missing-resource response may be skipped inside a playlist. */
    static final class ResourceUnavailable extends FriendlyException
    {
        final String reason;

        private ResourceUnavailable(String reason)
        {
            super("找不到这个 Bilibili 视频或列表，可能已被删除。", Severity.COMMON, null);
            this.reason = reason;
        }
    }

    static FriendlyException restricted()
    {
        return restricted("412/风控");
    }

    private static FriendlyException restricted(String code)
    {
        return problem("Bilibili 暂时限制了机器人的请求（" + code + "）。已停止本次加载，请勿连续重试；具体失败步骤见控制台。若持续发生，可检查本机 config.txt 的 playback.bilibili.auth 配置；请勿把 Cookie 发到聊天中。");
    }

    static void checkHttp(int status)
    {
        if (status == 412) throw restricted("HTTP 412");
        if (status == 429) throw problem("Bilibili 请求过于频繁（HTTP 429），请稍后重试。");
        if (status == 401 || status == 403) throw problem("Bilibili 拒绝访问，请检查本机登录配置是否有效，以及账号是否有权访问该内容。");
        if (status == 404) throw new ResourceUnavailable("HTTP 404");
        if (status != 200) throw problem("Bilibili 请求失败（HTTP " + status + "），请稍后重试。");
    }

    static JsonBrowser data(JsonBrowser response)
    {
        long code = response == null ? Long.MIN_VALUE : response.get("code").asLong(Long.MIN_VALUE);
        if (code == -412 || code == -352) throw restricted("API " + code);
        if (code == -101 || code == -403) throw problem("Bilibili 要求有效登录或内容访问权限，请检查本机 playback.bilibili.auth 配置。");
        if (code == -404 || code == 62002) throw new ResourceUnavailable("API " + code);
        if (code != 0 || response.get("data").isNull()) throw problem("Bilibili 返回了无效的视频或列表信息，请稍后重试。");
        return response.get("data");
    }

    static FriendlyException problem(String message)
    {
        return new FriendlyException(message, FriendlyException.Severity.COMMON, null);
    }
}
