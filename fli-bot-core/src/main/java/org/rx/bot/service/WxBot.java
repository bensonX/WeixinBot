//package org.rx.bot.service;
//
//import com.google.common.cache.Cache;
//import com.google.common.cache.CacheBuilder;
//import lombok.Data;
//import lombok.SneakyThrows;
//import lombok.extern.slf4j.Slf4j;
//import org.apache.commons.collections4.CollectionUtils;
//import org.rx.core.App;
//import org.rx.core.ManualResetEvent;
//import org.rx.core.NQuery;
//import org.rx.core.dto.bot.BotType;
//import org.rx.core.dto.bot.MessageInfo;
//import org.rx.core.api.Bot;
//import org.rx.core.util.ImageUtil;
//import org.springframework.stereotype.Component;
//import weixin.popular.bean.message.EventMessage;
//import weixin.popular.bean.xmlmessage.XMLMessage;
//import weixin.popular.bean.xmlmessage.XMLTextMessage;
//import weixin.popular.util.SignatureUtil;
//import weixin.popular.util.XMLConverUtil;
//
//import javax.servlet.ServletInputStream;
//import javax.servlet.ServletOutputStream;
//import javax.servlet.http.HttpServletRequest;
//import javax.servlet.http.HttpServletResponse;
//import java.io.OutputStream;
//import java.nio.charset.StandardCharsets;
//import java.util.List;
//import java.util.concurrent.TimeUnit;
//import java.util.function.BiConsumer;
//
//import static org.rx.core.Contract.toJsonString;
//
//@Component
//@Slf4j
//public class WxBot implements Bot {
//    private static final String token = "wangyoufan";
//    private static final Cache<String, Object> cache = CacheBuilder.newBuilder()
//            .expireAfterAccess(2, TimeUnit.MINUTES).build();
//
//    @Data
//    private static class CacheItem {
//        private final ManualResetEvent waiter;
//        private String value;
//
//        public CacheItem() {
//            waiter = new ManualResetEvent();
//        }
//    }
//
//    public volatile BiConsumer<Bot, Bot.ReceiveMessageArgs> onReceiveMessage;
//
//    @Override
//    public BotType getType() {
//        return BotType.WxService;
//    }
//
//    @Override
//    public void sendMessage(List<MessageInfo> messages) {
//        log.debug("Not supported");
//    }
//
//    @SneakyThrows
//    public void handleCallback(HttpServletRequest request, HttpServletResponse response) {
//        ServletInputStream in = request.getInputStream();
//        ServletOutputStream out = response.getOutputStream();
//        String signature = request.getParameter("signature");
//        String timestamp = request.getParameter("timestamp");
//        String nonce = request.getParameter("nonce");
//        String echostr = request.getParameter("echostr");
//        if (echostr != null) {
//            log.debug("首次请求申请验证,返回echostr");
//            outWrite(out, echostr);
//            return;
//        }
//        if (timestamp == null || nonce == null || in == null) {
//            log.debug("Request params is empty");
//            outWrite(out, "");
//            return;
//        }
//        //验证请求签名
//        if (!signature.equals(SignatureUtil.generateEventMessageSignature(token, timestamp, nonce))) {
//            log.debug("Request signature is invalid");
//            outWrite(out, "Request signature is invalid");
//            return;
//        }
//
//        String toMsg = "";
//        //转换XML
//        EventMessage eventMessage = XMLConverUtil.convertToObject(EventMessage.class, in, StandardCharsets.UTF_8);
//        String key = App.cacheKey(eventMessage.getFromUserName() + "__"
//                + eventMessage.getToUserName() + "__"
//                + eventMessage.getMsgId() + "__"
//                + eventMessage.getCreateTime());
//        CacheItem cacheItem = (CacheItem) cache.getIfPresent(key);
//        boolean isProduce = false;
//        if (cacheItem == null) {
//            synchronized (this) {
//                if ((cacheItem = (CacheItem) cache.getIfPresent(key)) == null) {
//                    cache.put(key, cacheItem = new CacheItem());
//                    isProduce = true;
//                    log.info("callCache produce {}", key);
//                }
//            }
//        }
//        if (isProduce) {
//            log.info("recv: {}", toJsonString(eventMessage));
//            MessageInfo messageInfo = new MessageInfo();
//            messageInfo.setBotType(this.getType());
//            messageInfo.setOpenId(eventMessage.getFromUserName());
//            if ("subscribe".equalsIgnoreCase(eventMessage.getEvent())) {
//                messageInfo.setContent(Bot.SubscribeContent);
//            } else if ("text".equals(eventMessage.getMsgType())) {
//                messageInfo.setContent(eventMessage.getContent());
//            }
//            Bot.ReceiveMessageArgs args = new Bot.ReceiveMessageArgs(messageInfo);
//            raiseEvent(onReceiveMessage, args);
//            if (!CollectionUtils.isEmpty(args.getReplyList())) {
//                NQuery<String> apply = NQuery.of(args.getReplyList()).select(p -> {
//                    if (p.startsWith(ImageUtil.HtmlBase64Prefix)) {
//                        return "[图片]";
//                    }
//                    return SensitiveService.instance.wrap(p);
//                });
//                cacheItem.setValue(toMsg = String.join("\n", apply));
//                cacheItem.waiter.set();
//            }
//        } else {
//            log.info("callCache consumer {}", key);
//            cacheItem.waiter.waitOne();
//        }
//        if (toMsg.isEmpty()) {
//            toMsg = cacheItem.getValue();
//        }
//
//        log.info("send: {}", toMsg);
//        //创建回复
//        XMLMessage xmlTextMessage = new XMLTextMessage(eventMessage.getFromUserName(), eventMessage.getToUserName(), toMsg);
//        xmlTextMessage.outputStreamWrite(out);
//    }
//
//    @SneakyThrows
//    private void outWrite(OutputStream out, String text) {
//        out.write(text.getBytes(StandardCharsets.UTF_8));
//    }
//}
