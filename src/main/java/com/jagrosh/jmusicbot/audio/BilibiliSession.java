package com.jagrosh.jmusicbot.audio;

import com.github.parrotxray.lavabili.plugin.BilibiliConfig;
import org.apache.http.client.CookieStore;
import org.apache.http.client.protocol.HttpClientContext;
import org.apache.http.impl.client.BasicCookieStore;
import org.apache.http.impl.cookie.BasicClientCookie;

import java.util.Map;

/** Optional, process-local Bilibili session. Never stores credentials in the bot config. */
public final class BilibiliSession
{
    private final String sessdata, biliJct, dedeUserId, buvid3, buvid4;

    public BilibiliSession(Map<String, String> environment)
    {
        sessdata = value(environment, "BILIBILI_SESSDATA");
        biliJct = value(environment, "BILIBILI_BILI_JCT");
        dedeUserId = value(environment, "BILIBILI_DEDE_USER_ID");
        buvid3 = value(environment, "BILIBILI_BUVID3");
        buvid4 = value(environment, "BILIBILI_BUVID4");
    }

    public boolean hasSession() { return !sessdata.isEmpty(); }

    public void configureDevice(BilibiliConfig config)
    {
        if (!hasSession()) return;
        if (!buvid3.isEmpty()) config.getAuth().setBuvid3(buvid3);
        if (!buvid4.isEmpty()) config.getAuth().setBuvid4(buvid4);
        // Do not set upstream auth.enabled/SESSDATA: its filter adds login cookies
        // to every request, including short-link redirects and external media CDNs.
        // HttpClient's cookie store below enforces domain and HTTPS restrictions.
    }

    public void applyTo(HttpClientContext context)
    {
        if (!hasSession()) return;
        CookieStore store = context.getCookieStore();
        if (store == null)
        {
            store = new BasicCookieStore();
            context.setCookieStore(store);
        }
        add(store, "SESSDATA", sessdata);
        add(store, "bili_jct", biliJct);
        add(store, "DedeUserID", dedeUserId);
    }

    private static void add(CookieStore store, String name, String value)
    {
        if (value.isEmpty()) return;
        BasicClientCookie cookie = new BasicClientCookie(name, value);
        cookie.setDomain(".bilibili.com");
        cookie.setAttribute("domain", ".bilibili.com");
        cookie.setPath("/");
        cookie.setSecure(true);
        store.addCookie(cookie);
    }

    private static String value(Map<String, String> environment, String name)
    {
        String value = environment.getOrDefault(name, "").strip();
        if (value.chars().anyMatch(c -> c <= 0x20 || c >= 0x7f || c == ';'))
            throw new IllegalArgumentException(name + " must contain only the cookie value, without spaces, control characters or semicolons");
        return value;
    }
}
