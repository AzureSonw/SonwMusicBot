package com.jagrosh.jmusicbot.unit.audio;

import com.github.parrotxray.lavabili.plugin.BilibiliConfig;
import com.github.parrotxray.lavabili.source.BilibiliHttpContextFilter;
import com.jagrosh.jmusicbot.audio.BilibiliSession;
import org.apache.http.HttpHost;
import org.apache.http.client.config.CookieSpecs;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.protocol.HttpClientContext;
import org.apache.http.client.protocol.RequestAddCookies;
import org.apache.http.config.RegistryBuilder;
import org.apache.http.conn.routing.HttpRoute;
import org.apache.http.cookie.CookieSpecProvider;
import org.apache.http.impl.client.BasicCookieStore;
import org.apache.http.impl.cookie.BasicClientCookie;
import org.apache.http.impl.cookie.DefaultCookieSpecProvider;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Map;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

class BilibiliSessionTest
{
    @Test
    void missingSessionPreservesAnonymousMode()
    {
        BilibiliSession session = new BilibiliSession(Map.of("BILIBILI_BUVID3", "device"));
        BilibiliConfig config = new BilibiliConfig();
        HttpClientContext context = HttpClientContext.create();
        session.configureDevice(config);
        session.applyTo(context);
        assertFalse(session.hasSession());
        assertNull(context.getCookieStore());
        assertEquals("", config.getAuth().getBuvid3());
    }

    @Test
    void credentialsReachHttpsBilibiliAlongsideUpstreamDeviceCookies() throws Exception
    {
        HttpGet request = requestWithSession("https://api.bilibili.com/x/web-interface/view");
        String cookies = cookies(request);
        assertTrue(cookies.contains("SESSDATA=test%2Csession"));
        assertTrue(cookies.contains("bili_jct=test-csrf"));
        assertTrue(cookies.contains("DedeUserID=123"));
        assertTrue(cookies.contains("buvid3=device3"));
        assertTrue(cookies.contains("buvid4=device4"));
        assertTrue(cookies(requestWithSession("https://www.bilibili.com/video/BV1NVWxeeEVJ/"))
            .contains("SESSDATA=test%2Csession"));
    }

    @Test
    void loginCookiesNeverReachShortLinksCdnsOtherHostsOrHttp() throws Exception
    {
        for (String url : new String[] {
            "https://b23.tv/example", "https://cdn.bilivideo.com/audio.m4s",
            "https://bilibili.com.evil.invalid/video/1", "https://evilbilibili.com/",
            "http://api.bilibili.com/x/web-interface/view"
        })
        {
            String cookies = cookies(requestWithSession(url));
            assertFalse(cookies.contains("test%2Csession"), url);
            assertFalse(cookies.contains("test-csrf"), url);
            assertFalse(cookies.contains("DedeUserID="), url);
        }
    }

    @Test
    void existingCookiesSurviveAndOnlyProvidedSessionFieldsAreAdded()
    {
        HttpClientContext context = HttpClientContext.create();
        BasicCookieStore existing = new BasicCookieStore();
        existing.addCookie(new BasicClientCookie("existing", "value"));
        context.setCookieStore(existing);
        new BilibiliSession(Map.of("BILIBILI_SESSDATA", "  test-session  ")).applyTo(context);
        assertSame(existing, context.getCookieStore());
        assertEquals(2, existing.getCookies().size());
        assertTrue(existing.getCookies().stream().anyMatch(c -> c.getValue().equals("test-session")));
    }

    @Test
    void credentialsNeverEnterUpstreamGlobalHeaderOrAutoRefreshConfiguration()
    {
        BilibiliConfig config = new BilibiliConfig();
        session().configureDevice(config);
        assertEquals("device3", config.getAuth().getBuvid3());
        assertEquals("device4", config.getAuth().getBuvid4());
        assertFalse(config.isAuthenticated());
        assertFalse(config.getCanRefreshCookies());
        assertEquals("", config.getAuth().getSessdata());
    }

    @Test
    void malformedCookieValuesFailWithoutExposingTheirContents()
    {
        for (String value : new String[] {"secret; injected=value", "secret\r\ninjected", "secret value"})
        {
            var error = assertThrows(IllegalArgumentException.class,
                () -> new BilibiliSession(Map.of("BILIBILI_SESSDATA", value)));
            assertTrue(error.getMessage().contains("BILIBILI_SESSDATA"));
            assertFalse(error.getMessage().contains("secret"));
        }
    }

    private static BilibiliSession session()
    {
        return new BilibiliSession(Map.of("BILIBILI_SESSDATA", "test%2Csession",
            "BILIBILI_BILI_JCT", "test-csrf", "BILIBILI_DEDE_USER_ID", "123",
            "BILIBILI_BUVID3", "device3", "BILIBILI_BUVID4", "device4"));
    }

    private static HttpGet requestWithSession(String url) throws Exception
    {
        HttpClientContext context = HttpClientContext.create();
        BilibiliConfig config = new BilibiliConfig();
        BilibiliSession session = session();
        session.configureDevice(config);
        session.applyTo(context);
        HttpGet request = new HttpGet(url);
        var target = new HttpHost(request.getURI().getHost(), -1, request.getURI().getScheme());
        context.setTargetHost(target);
        context.setAttribute(HttpClientContext.HTTP_ROUTE, new HttpRoute(target, null, "https".equals(target.getSchemeName())));
        context.setCookieSpecRegistry(RegistryBuilder.<CookieSpecProvider>create()
            .register(CookieSpecs.DEFAULT, new DefaultCookieSpecProvider()).build());
        new BilibiliHttpContextFilter(config, "device3", "device4", "fingerprint", false, null)
            .onRequest(context, request, false);
        new RequestAddCookies().process(request, context);
        return request;
    }

    private static String cookies(HttpGet request)
    {
        return Arrays.stream(request.getHeaders("Cookie")).map(h -> h.getValue()).collect(Collectors.joining("; "));
    }
}
