package org.rx.test;

import com.alibaba.fastjson.JSON;
import lombok.SneakyThrows;
import okhttp3.HttpUrl;
import org.junit.Test;
import org.rx.core.NQuery;
import org.rx.core.util.ImageUtil;
import org.rx.bot.service.WxMobileBot;
import org.rx.bot.util.AwtBot;
import org.rx.socks.tcp.RemotingFactor;

import java.awt.*;
import java.util.List;

public class BotTests {
    @SneakyThrows
    @Test
    public void normal() {
        HttpUrl httpUrl = HttpUrl.get("https://item.m.jd.com/product/28675451965.html?dl_abtest=o&shareRuleType=1&shareActivityId=17949&shareToken=67115d98eebd03b72d46f3911b206e6d&shareType=1&adod=0&cu=true&utm_source=h5.m.jd.com&utm_medium=tuiguang&utm_campaign=t_1000151393_youngcoder&utm_term=4128b5df882c497ba1bc8c6e5eb51b0e");
        System.out.println(httpUrl.encodedPath());
        System.out.println(httpUrl.encodedPathSegments());
        System.out.println(httpUrl.host());
        System.out.println(httpUrl.query());
        System.out.println(httpUrl.topPrivateDomain());
        System.out.println(httpUrl.redact());
        System.out.println(httpUrl.encodedFragment());
        System.out.println(httpUrl.toString());

//        String url = "https://szsupport.weixin.qq.com/cgi-bin/mmsupport-bin/readtemplate?t=w_redirect_taobao&url=https%3A%2F%2Fitem.taobao.com%2Fitem.htm%3Fspm%3Da230r.1.14.164.436b3078xt4nB5%26id%3D14312037600%26ns%3D1%26abbucket%3D1%23detail&lang=zh_CN";
//        HttpUrl httpUrl = HttpUrl.get(url);
//        for (String name : httpUrl.queryParameterNames()) {
//            System.out.println("name:" + name);
//            System.out.println("value:" + httpUrl.queryParameter(name));
//        }

//        HttpClient client = new HttpClient();
//        String text = client.post("http://openapi.tuling123.com/openapi/api/v2", "{\n" +
//                "\t\"reqType\":0,\n" +
//                "    \"perception\": {\n" +
//                "        \"inputText\": {\n" +
//                "            \"text\": \"小米8手机\"\n" +
//                "        },\n" +
//                "        \"selfInfo\": {\n" +
//                "            \"location\": {\n" +
//                "                \"city\": \"苏州\",\n" +
//                "            }\n" +
//                "        }\n" +
//                "    },\n" +
//                "    \"userInfo\": {\n" +
//                "        \"apiKey\": \"77d6148abbfc450fa7c8eae53f74fb82\",\n" +
//                "        \"userId\": \"163653\"\n" +
//                "    }\n" +
//                "}");
//        System.out.println(text);
//        System.in.read();
    }

    @SneakyThrows
    @Test
    public void wxBotHost() {
        WxMobileBot bot = new WxMobileBot(500, 2, 1, 1);
        bot.getServiceOpenIds().add("duoduojinbao");
        bot.onReceiveMessage = (s, e) -> {
            System.out.println(JSON.toJSONString(e.getValue()));
        };
        bot.start();
        RemotingFactor.listen(bot, 8072);
        System.out.println("start...");

        Thread.sleep(2000);
        bot.customPdd("duoduojinbao");
        System.in.read();
    }

    @SneakyThrows
    @Test
    public void wxBot() {
        WxMobileBot bot = new WxMobileBot(500, 2, 1, 1);
        bot.onReceiveMessage = (s, e) -> {
            System.out.println(JSON.toJSONString(e.getValue()));
            e.setReplyList(NQuery.of(e.getValue()).select(msg -> "已收到消息：" + msg).toList());
        };
        bot.start();
        System.out.println("start...");

        Thread.sleep(8000);
        System.out.println("test...");
        bot.getMessageRectangle();
        System.out.println(bot.findScreenPoints(WxMobileBot.KeyImages.Msg2));

        System.in.read();
    }

    @SneakyThrows
    @Test
    public void getScreenPoint() {
        Class owner = AwtBot.class;
        AwtBot bot = AwtBot.getBot();
        List<Point> points = bot.findScreenPoints(ImageUtil.getImageFromResource(owner, "/bot/wxUnread0.png"));
        System.out.println(points);
        points = bot.findScreenPoints(ImageUtil.getImageFromResource(owner, "/bot/wxUnread1.png"));
//        points = bot.findScreenPoints(ImageUtil.loadImage("D:\\1.png"));
        System.out.println(points);
    }
}
