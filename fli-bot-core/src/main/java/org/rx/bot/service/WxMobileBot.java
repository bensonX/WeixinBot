package org.rx.bot.service;

import com.alibaba.fastjson.JSON;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import okhttp3.HttpUrl;
import org.apache.commons.collections4.CollectionUtils;
import org.rx.core.*;
import org.rx.core.api.IWxMobileBot;
import org.rx.core.api.SerializedImage;
import org.rx.core.dto.bot.BotType;
import org.rx.core.dto.bot.MessageInfo;
import org.rx.core.dto.bot.OpenIdInfo;
import org.rx.core.api.Bot;
import org.rx.core.dto.media.AdvCode;
import org.rx.core.dto.media.GoodsInfo;
import org.rx.core.util.FliUtil;
import org.rx.core.util.ImageUtil;
import org.rx.beans.$;
import org.rx.beans.DateTime;
import org.rx.beans.Tuple;
import org.rx.bot.util.AwtBot;
import org.rx.util.function.Action;

import java.awt.*;
import java.awt.image.BufferedImage;
import java.util.*;
import java.util.List;
import java.util.concurrent.Future;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Predicate;

import static org.rx.beans.$.$;
import static org.rx.core.AsyncTask.TaskFactory;
import static org.rx.core.Contract.*;

@Slf4j
public class WxMobileBot implements IWxMobileBot {
    //region nestedTypes
    public interface KeyImages {
        BufferedImage KeyNew = ImageUtil.getImageFromResource(WxMobileBot.class, "/bot/wxKey.png");
        BufferedImage Unread0 = ImageUtil.getImageFromResource(WxMobileBot.class, "/bot/wxUnread0.png");
        BufferedImage Unread1 = ImageUtil.getImageFromResource(WxMobileBot.class, "/bot/wxUnread1.png");
        BufferedImage Msg = ImageUtil.getImageFromResource(WxMobileBot.class, "/bot/wxMsg.png");
        BufferedImage Msg2 = ImageUtil.getImageFromResource(WxMobileBot.class, "/bot/wxMsg2.png");
        BufferedImage Browser = ImageUtil.getImageFromResource(WxMobileBot.class, "/bot/wxBrowser.png");
        BufferedImage wxFix0 = ImageUtil.getImageFromResource(WxMobileBot.class, "/bot/wxFix0.png");
        BufferedImage wxLogin = ImageUtil.getImageFromResource(WxMobileBot.class, "/bot/wxLogin.png");
        BufferedImage NewUser = ImageUtil.getImageFromResource(WxMobileBot.class, "/bot/wxNewUser.png");
        BufferedImage NewUser2 = ImageUtil.getImageFromResource(WxMobileBot.class, "/bot/wxNewUser2.png");
    }
    //endregion

    //region fields
    private static final int delay50 = 50, delay100 = 100;
    private static final NQuery<String> skipOpenIds = NQuery.of("weixin", "filehelper");
    private static final Point[] openIdPoints = new Point[]{new Point(94, 72), new Point(96, 86)};

    public volatile BiConsumer<Bot, ReceiveMessageArgs> onReceiveMessage;
    private AwtBot bot;
    private int mockAutoDelay;
    private DateTime lastTime;
    private Point windowPoint;
    private int capturePeriod, maxCheckMessageCount, maxCaptureMessageCount, maxScrollMessageCount;
    private final ReentrantLock locker;
    private volatile BufferedImage lastMonitor;
    private volatile DateTime lastStandby;
    private volatile HttpUrl lastUrl;
    private volatile int captureFlag;
    private volatile Future captureFuture;
    @Getter
    @Setter
    private boolean autoResetWindow;
    @Getter
    @Setter
    private String adminOpenId;
    //群
    @Getter
    private final Set<String> groupOpenIds;
    //服务号
    @Getter
    private final Set<String> serviceOpenIds;

    @Override
    public BotType getType() {
        return BotType.Wx;
    }

    private Point getWindowPoint() {
        if (DateTime.now().subtract(lastTime).getTotalMinutes() > 1) {
            lastTime = DateTime.now();
            windowPoint = null;
        }
        if (windowPoint == null) {
            Point point = bot.findScreenPoint(KeyImages.KeyNew);
            if (point == null) {
                if (autoResetWindow) {
                    resetWindow();
                    point = bot.findScreenPoint(KeyImages.KeyNew);
                }

                if (point == null) {
                    bot.saveScreen(KeyImages.KeyNew, "WxMobile");
                    throw new InvalidOperationException("WxMobile window not found");
                }
            }
            int x = point.x - 21, y = point.y - 469;
            // 18 25 -> 2 2
            windowPoint = new Point(x, y);
        }
        return windowPoint;
    }
    //endregion

    //region init
    public WxMobileBot(int capturePeriod, int maxCheckMessageCount, int maxCaptureMessageCount, int maxScrollMessageCount) {
        bot = AwtBot.getBot();
        bot.setAutoDelay(10);
        mockAutoDelay = 2000;
        lastTime = DateTime.now();
        getWindowPoint();

        this.capturePeriod = capturePeriod;
        this.maxCheckMessageCount = maxCheckMessageCount;
        this.maxCaptureMessageCount = maxCaptureMessageCount;
        this.maxScrollMessageCount = maxScrollMessageCount;
        locker = new ReentrantLock(true);
        groupOpenIds = Collections.synchronizedSet(new HashSet<>());
        serviceOpenIds = Collections.synchronizedSet(new HashSet<>());
    }

    private void resetWindow() {
        bot.pressCtrlAltW();
        bot.delay(400);
        bot.pressCtrlAltW();
        bot.delay(800);
        Point screenPoint = bot.findScreenPoint(KeyImages.KeyNew);
        if (screenPoint != null) {
            return;
        }
        bot.pressCtrlAltW();
        bot.delay(400);
    }

    @Override
    public void start() {
        if (captureFuture != null) {
            return;
        }

        lastMonitor = bot.captureScreen(getMonitorNewUserRectangle());
        lastStandby = DateTime.now();
        //抛异常会卡住
        captureFuture = TaskFactory.schedule(() -> App.catchCall(this::captureUsers), capturePeriod);
    }

    @Override
    public void stop() {
        if (captureFuture == null) {
            return;
        }

        captureFuture.cancel(false);
        captureFuture = null;
    }
    //endregion

    //region points
    @Override
    public List<Point> findScreenPoints(BufferedImage image) {
        return bot.findScreenPoints(image);
    }

    @Override
    public Point getAbsolutePoint(int relativeX, int relativeY) {
        Point windowPoint = getWindowPoint();
        return new Point(windowPoint.x + relativeX, windowPoint.y + relativeY);
    }

    @Override
    public Rectangle getUserRectangle() {
        Point point = getAbsolutePoint(61, 63);
        return new Rectangle(point, new Dimension(250, 438));
    }

    @Override
    public Rectangle getMessageRectangle() {
        Point point = getAbsolutePoint(311, 63);
        return new Rectangle(point, new Dimension(400, 294));
    }

    private Rectangle getFix0Rectangle() {
        Point point = getAbsolutePoint(311, 63);
        return new Rectangle(point, new Dimension(400, 438));
    }

    private Rectangle getMonitorNewUserRectangle() {
        Point point = getAbsolutePoint(61, 63);
        return new Rectangle(point, new Dimension(206, 64));
    }

    private Rectangle getNewUserRectangle() {
        Point point = getAbsolutePoint(311, 63);
        return new Rectangle(point, new Dimension(400, 58));
    }

    private Rectangle getNewUser2Rectangle() {
        Point point = getAbsolutePoint(177, 252);
        return new Rectangle(point, new Dimension(360, 112));
    }

    private Point getChatPoint() {
        return getAbsolutePoint(30, 92);
    }

    private synchronized void setStandbyPoint() {
        DateTime now = DateTime.now();
        if (now.subtract(lastStandby).getTotalMilliseconds() >= capturePeriod) {
            bot.mouseLeftClick(getAbsolutePoint(93, 414));
            lastStandby = now;
        }
    }

    private void mockDelay() {
        bot.delay(mockAutoDelay);
    }
    //endregion

    private void captureUsers() {
        locker.lock();
        try {
            final Rectangle userRect = getUserRectangle();
            log.debug("captureUsers at {}", userRect);
            Action fixScroll = () -> {
                log.debug("fixScroll");
                setStandbyPoint();
                bot.mouseWheel(-5);
                bot.delay(500);
            };
            boolean captured = false;
            int checkCount = 0;
            do {
                for (BufferedImage partImg : new BufferedImage[]{KeyImages.Unread0, KeyImages.Unread1}) {
                    Point screenPoint;
                    while ((screenPoint = bot.findScreenPoint(partImg, userRect)) != null) {
                        captured = true;
                        checkCount = 0;
                        log.info("step1 capture user at {}", screenPoint);
                        bot.mouseRelease();

                        bot.mouseLeftClick(screenPoint.x, screenPoint.y + 20);
                        bot.delay(delay100);

                        Rectangle msgRect = getMessageRectangle();
                        bot.mouseMove(msgRect.x + 20, msgRect.y + 20);

                        MessageInfo messageInfo = new MessageInfo();
                        messageInfo.setBotType(this.getType());
                        Set<String> msgList = new LinkedHashSet<>();
                        int scrollMessageCount = 0;
                        boolean doLoop = true;
                        do {
                            if (scrollMessageCount == 0) {
                                //只跳过最新消息为转账的
                                int height = 120;
                                Point point2 = bot.findScreenPoint(KeyImages.Msg2, new Rectangle(msgRect.x, msgRect.y + msgRect.height - height, msgRect.width, height));
                                if (point2 != null) {
                                    log.info("step2 capture transfer and return");
                                    doLoop = false;
                                    break;
                                }
                            }
                            List<Point> points = bot.findScreenPoints(KeyImages.Msg, msgRect);
                            log.info("step2 captureMessages {}", points.size());
                            for (int i = points.size() - 1; i >= 0; i--) {
                                Point p = points.get(i);
                                if (messageInfo.getOpenId() == null) {
                                    int x = p.x - 22, y = p.y + 12;
                                    bot.mouseLeftClick(x, y);

                                    fillOpenId(messageInfo, new Point(x, y));
                                    if (Strings.isNullOrEmpty(messageInfo.getOpenId())) {
                                        doLoop = false;
                                        break;
                                    }

                                    bot.mouseLeftClick(msgRect.x + 10, msgRect.y + 10);
                                    bot.delay(delay100);
                                }
                                String msg;
                                int x = p.x + KeyImages.Msg.getWidth() + 10, y = p.y + KeyImages.Msg.getHeight() / 2;

                                bot.mouseLeftClick(x, y);
                                //微信浏览器会卡一会才弹出页面
                                bot.delay(300);
                                $<Point> pCopy$ = $();
                                msg = bot.waitClipboardText(s -> {
                                    pCopy$.v = bot.clickByImage(KeyImages.Browser, new Rectangle(getAbsolutePoint(311, 2), new Dimension(265, 70)), false);
                                    //不能小于8，否则会失效
                                    if (pCopy$.v == null && s.getCheckCount() >= 8) {
                                        log.warn("waitClipboardText break");
                                        return false;
                                    }
                                    return true;
                                }, copyUrl -> {
                                    try {
                                        HttpUrl httpUrl = HttpUrl.get(copyUrl);
                                        httpUrl = httpUrl.newBuilder().removeAllQueryParameters("ShareTm").build();
                                        return !eq(lastUrl, httpUrl);
                                    } catch (Exception e) {
                                        log.error("copyUrl", e);
                                        return true;
                                    }
                                });
                                if (!Strings.isNullOrEmpty(msg)) {
                                    log.info("step2-2 capture url last={} current={}", lastUrl, msg);
                                    HttpUrl httpUrl = HttpUrl.get(msg);
                                    lastUrl = httpUrl.newBuilder().removeAllQueryParameters("ShareTm").build();
                                } else {
                                    bot.mouseDoubleLeftClick(x, y);
                                    bot.delay(delay50);
                                    msg = bot.copyAndGetText();
                                }
                                log.info("step2-2 capture msg {}", msg);
                                msgList.add(msg);
                                if (msgList.size() >= maxCaptureMessageCount) {
                                    break;
                                }
                            }

                            if (doLoop && msgList.size() < maxCaptureMessageCount) {
                                bot.mouseWheel(-5);
                                bot.delay(1000);
                                scrollMessageCount++;
                            }
                        }
                        while (doLoop && scrollMessageCount <= maxScrollMessageCount && msgList.size() < maxCaptureMessageCount);
                        if (!doLoop) {
                            continue;
                        }
                        if (msgList.isEmpty()) {
                            if (Strings.isNullOrEmpty(messageInfo.getOpenId())) {
                                fillOpenIdByTab(messageInfo, serviceOpenIds.contains(messageInfo.getOpenId()));
                            }
                            messageInfo.setContent(Bot.SubscribeContent);
                        } else {
                            messageInfo.setContent(NQuery.of(msgList).first());
                        }
                        asyncReply(messageInfo);
                    }
                }
                checkCount++;
            } while (checkCount < maxCheckMessageCount);

            BufferedImage currentMonitor = bot.captureScreen(getMonitorNewUserRectangle());
            if (captured) {
                fixScroll.invoke();
                log.info("step1 monitor reset by captured");
                captureFlag = 0;
            } else {
                if (!ImageUtil.partEquals(lastMonitor, 0, 0, currentMonitor)) {
                    if (captureFlag == 0) {
                        log.info("step1 monitor user incoming");
                        fixScroll.invoke();
                        captureFlag++; //需要滑倒顶部完全显示未读消息红点
                    } else if (captureFlag == 1) {
                        if (bot.findScreenPoint(KeyImages.Unread0, userRect) == null) {
                            log.info("step2 monitor user incoming ok");
                            bot.mouseRelease();

                            bot.mouseLeftClick(getAbsolutePoint(118, 95));
                            bot.delay(delay100);
                            BufferedImage img = KeyImages.NewUser, img2 = KeyImages.NewUser2;
                            Point pNewUser = bot.findScreenPoint(img, getNewUserRectangle());
                            if (pNewUser != null) {
                                bot.mouseLeftClick(pNewUser.x + img.getWidth(), pNewUser.y + img.getHeight() / 2);
                                log.info("[rx] newUser step1 ok");
                                for (int i = 0; i < 8; i++) {
                                    Point pNewUser2 = bot.findScreenPoint(img2, getNewUser2Rectangle());
                                    if (pNewUser2 == null) {
                                        log.info("[rx] newUser step2-1 wait..");
                                        bot.delay(400);
                                        continue;
                                    }
                                    log.info("[rx] newUser step2-1 {} ok", pNewUser2);
                                    int x = pNewUser2.x + img2.getWidth(), y = pNewUser2.y + img2.getHeight() / 2;
                                    bot.mouseLeftClick(x, y);
                                    log.info("[rx] newUser step2-2 {},{} ok", x, y);
                                    break;
                                }
                                bot.delay(delay50);
                                MessageInfo messageInfo = new MessageInfo();
                                messageInfo.setBotType(this.getType());
                                fillOpenIdByTab(messageInfo, false);
                                messageInfo.setContent(Bot.SubscribeContent);
                                asyncReply(messageInfo);
                            }
                        }
                        fixScroll.invoke();
                        captureFlag = 0;
                    }
                } else {
                    log.debug("step1 monitor reset");
                    captureFlag = 0;
                }
            }
            //必须放这
            lastMonitor = currentMonitor;

            checkFix0();
        } finally {
            locker.unlock();
        }
    }

    private void fillOpenIdByTab(MessageInfo message, boolean isServiceOpenId) {
        Point point = getAbsolutePoint(686, 40);
        bot.mouseLeftClick(point);
        if (isServiceOpenId) {
            log.debug("fill serviceOpenId");
            bot.delay(delay100);
        } else {
            bot.delay(500); //多100
            point = getAbsolutePoint(808, 52);
            bot.mouseLeftClick(point);
        }
        fillOpenId(message, point);
    }

    //自带delay
    private void fillOpenId(MessageInfo message, Point point) {
        bot.delay(delay100);
        String openId = null;
        for (Point openIdPoint : openIdPoints) {
            bot.mouseDoubleLeftClick(point.x + openIdPoint.x, point.y + openIdPoint.y);
            openId = bot.copyAndGetText();
            log.info("step2-1 capture openId {}", openId);
            if (!Strings.isNullOrEmpty(openId)) {
                break;
            }
            bot.delay(delay50);
        }
        if (Strings.isNullOrEmpty(openId) || skipOpenIds.contains(openId)) {
            log.warn("Can not found openId {}", openId);
            return;
        }
        message.setOpenId(openId);
        if (serviceOpenIds.contains(message.getOpenId())) {
            return;
        }

        //获取remarkName后会卡住，故先也把nickname获取了
        bot.mouseDoubleLeftClick(point.x + 42, point.y + 45);
        String nickname = bot.copyAndGetText();
        bot.getClipboard().resetLastText("0");  //防止卡住
        bot.mouseLeftClick(point.x + 100, point.y + 146);
        bot.pressCtrlA();
        String remarkName = bot.copyAndGetText();
        if (!Strings.startsWith(remarkName, "fl_")) {
            if (message.getOpenId().startsWith("wxid_") && !Strings.isEmpty(remarkName)) {
                nickname = remarkName;
            }
        } else {
            nickname = remarkName;
        }
        log.info("step2-1 capture nickname {}", nickname);
        if (Strings.isNullOrEmpty(nickname)) {
            log.warn("Can not found nickname");
            return;
        }
        message.setNickname(nickname);

        message.setAvatar(new SerializedImage(bot.captureScreen(point.x + 205, point.y + 31, 60, 60)));
    }

    private void asyncReply(MessageInfo messageInfo) {
        if (onReceiveMessage == null) {
            return;
        }
        TaskFactory.run(() -> {
            Bot.ReceiveMessageArgs args = new Bot.ReceiveMessageArgs(messageInfo);
            raiseEvent(onReceiveMessage, args);
            List<String> toMsgs = args.getReplyList();
            log.debug("Bot server get reply {}", toJsonString(toMsgs));
            if (CollectionUtils.isEmpty(toMsgs)) {
                return;
            }
            toMsgs = NQuery.of(toMsgs).where(p -> !Strings.isNullOrEmpty(p)).toList();
            if (toMsgs.isEmpty()) {
                return;
            }
            sendMessage(messageInfo, toMsgs, null);
        });
    }

    private boolean checkFix0() {
        BufferedImage iFix0 = KeyImages.wxFix0;
        Point pFix0 = bot.findScreenPoint(iFix0, getFix0Rectangle());
        if (pFix0 != null) {
            bot.mouseLeftClick(pFix0.x + iFix0.getWidth() + 30, pFix0.y + iFix0.getHeight() / 2);
            log.debug("check fix0 ok");
            bot.delay(delay100);
            return true;
        }
        return false;
    }

    @Override
    public void sendMessage(List<MessageInfo> messages) {
        for (Tuple<OpenIdInfo, List<String>> tuple : NQuery.of(messages)
                .groupBy(p -> p.getBotType().getValue() + p.getOpenId(),
                        p -> Tuple.of((OpenIdInfo) p.right.first(), p.right.select(p2 -> p2.getContent()).toList()))) {
            sendMessage(tuple.left, tuple.right, null);
        }
    }

    private void sendMessage(OpenIdInfo userOpenId, List<String> contents, Consumer<AwtBot> onSend) {
        require(userOpenId);
        if (skipOpenIds.contains(userOpenId.getOpenId())) {
            return;
        }

        locker.lock();
        try {
            $<String> $openId = $();
            if (!unsafeOpenChat(userOpenId, $openId)) {
                return;
            }

            Point point = getAbsolutePoint(360, 408);
            bot.mouseLeftClick(point.x, point.y);
            bot.delay(delay50);
            bot.mouseLeftClick(point.x, point.y);
            bot.delay(delay100);

            for (String msg : contents) {
                mockDelay();
                bot.pressCtrlA();
                bot.pressDelete();

                if (msg.startsWith(ImageUtil.HtmlBase64Prefix)) {
                    try {
                        Image image = ImageUtil.loadImageBase64(msg);
                        bot.setImageAndParse(image);
                    } catch (Exception e) {
                        log.error("sendMessage", e);
                    }
                } else {
                    bot.setTextAndParse(SensitiveService.instance.wrap(msg));
                }

                bot.pressEnter();
                bot.delay(200);
                log.info("step2 send msg {} to user {}", msg.startsWith(ImageUtil.HtmlBase64Prefix) ? "[Base64Image]" : msg, $openId.v);
            }

            if (onSend != null) {
                onSend.accept(bot);
            }
            //双击会复现bug
            setStandbyPoint();
        } finally {
            locker.unlock();
        }
    }

    @Override
    public boolean unsafeOpenChat(OpenIdInfo userOpenId, $<String> $openId) {
        require(userOpenId, userOpenId.getOpenId());

        bot.mouseRelease();
        String openId = userOpenId.getOpenId().startsWith("wxid_") ? userOpenId.getNickname() : userOpenId.getOpenId();
        if ($openId != null) {
            $openId.v = openId;
        }
        boolean isFix0 = false, isGroup = groupOpenIds.contains(userOpenId.getOpenId());
        int checkCount = 0;
        MessageInfo check = new MessageInfo();
        do {
            if (!isFix0 && checkCount > 0) {
                resetWindow();
                isFix0 = false;
            }

            bot.mouseLeftClick(getChatPoint());
            bot.delay(delay100);
            bot.mouseLeftClick(getAbsolutePoint(110, 38));
            bot.delay(delay100);
            log.info("step1 focus input ok");

            bot.setTextAndParse(openId);
            bot.delay(1100); //多200
            log.info("step1-1 input openId {}", openId);

            bot.mouseLeftClick(getAbsolutePoint(166, 130));
            bot.delay(delay100);
            log.info("step1-2 click user {}", openId);

            if (isFix0 = checkFix0()) {
                continue;
            }
            if (isGroup) {
                break;
            }
            log.debug("check is serviceId: {}->{} {}", JSON.toJSONString(serviceOpenIds), userOpenId.getOpenId(), serviceOpenIds.contains(userOpenId.getOpenId()));
            fillOpenIdByTab(check, serviceOpenIds.contains(userOpenId.getOpenId()));
            checkCount++;
        }
        while (checkCount < maxCheckMessageCount && !userOpenId.getOpenId().equals(check.getOpenId()));
        if (!isGroup && !userOpenId.getOpenId().equals(check.getOpenId())) {
            log.info("message openId {} not equals {}", userOpenId.getOpenId(), check.getOpenId());
            return false;
        }
        return true;
    }

    @Override
    public void checkBrowserLogin() {
        Point point = bot.findScreenPoint(KeyImages.wxLogin, new Rectangle(310, 192, 484, 348));
        if (point == null) {
            return;
        }
        bot.mouseLeftClick(point);
    }

    @Override
    public void unsafeOpenBrowser(String link) {
        if (Strings.isEmpty(adminOpenId)) {
            throw new InvalidOperationException("adminOpenId is null");
        }
        OpenIdInfo openIdInfo = new OpenIdInfo();
        openIdInfo.setBotType(BotType.Wx);
        openIdInfo.setOpenId(adminOpenId);
        sendMessage(openIdInfo, Collections.singletonList(link), bot -> {
            bot.delay(1200); //多200
            bot.mouseLeftClick(getAbsolutePoint(460, 306));
        });
    }

    //region custom
    @Override
    public void customPdd(String wxId) {
        require(wxId);

        locker.lock();
        try {
            OpenIdInfo userOpenId = new OpenIdInfo();
            userOpenId.setBotType(BotType.Wx);
            userOpenId.setOpenId(wxId);
            if (!unsafeOpenChat(userOpenId, null)) {
                log.warn("Open chat {} fail..", userOpenId.getOpenId());
                return;
            }

            bot.mouseLeftClick(getAbsolutePoint(372, 478));
            bot.delay(1000);
        } finally {
            locker.unlock();
        }
    }

    @Override
    public AdvCode customKaola(String wxId, GoodsInfo goodsInfo) {
        require(wxId, goodsInfo);

        locker.lock();
        try {
            OpenIdInfo userOpenId = new OpenIdInfo();
            userOpenId.setBotType(BotType.Wx);
            userOpenId.setOpenId(wxId);
            if (!unsafeOpenChat(userOpenId, null)) {
                log.warn("Open chat {} fail..", userOpenId.getOpenId());
                return null;
            }

            //close tab
            bot.mouseLeftClick(getAbsolutePoint(614, 478));
            bot.delay(1000);

            bot.mouseLeftClick(getAbsolutePoint(614, 478));
            bot.delay(500); //多100
//            bot.mouseLeftClick(getAbsolutePoint(614, 478 - 44));
            bot.mouseLeftClick(getAbsolutePoint(614, 478 - 74));
            bot.delay(1500);

            FluentWait waiter = FluentWait.newInstance(4000, 800).retryMills(1600).throwOnFail(false);
            Point point = getAbsolutePoint(311, 2);
            Predicate<FluentWait.UntilState> actionSelectAll = s -> {
                checkBrowserLogin();
                bot.mouseDoubleLeftClick(point.x + 55, point.y + 102);
                bot.pressCtrlA();
                return true;
            };
            actionSelectAll.test(FluentWait.NULL());
            Tuple<String, Boolean> tupleSelectAll = waiter.until(s -> {
                String text = bot.copyAndGetText();
                return Tuple.of(text, Strings.contains(text, "一键复制"));
            }, actionSelectAll);
            if (!tupleSelectAll.right) {
                log.warn("Wait page fail, source is {}", tupleSelectAll.left);
                return null;
            }

            bot.mouseLeftClick(point.x + 50, point.y + 160);
            bot.setTextAndParse(String.format("https://goods.kaola.com/product/%s.html", goodsInfo.getId()));

            Predicate<FluentWait.UntilState> actionClick = s -> {
                bot.mouseLeftClick(point.x + 170, point.y + 338);
                return true;
            };
            actionClick.test(FluentWait.NULL());
            Tuple<String, Boolean> tupleClick = waiter.until(s -> {
                actionSelectAll.test(FluentWait.NULL());
                String text = bot.copyAndGetText();
                return Tuple.of(text, Strings.contains(text, "佣金比例"));
            }, actionClick);
            if (!tupleClick.right) {
                log.warn("Wait code fail, source is {}", tupleClick.left);
                return null;
            }
            String callback = tupleClick.left;
            log.debug("step callback: {}", callback);
            int s = callback.lastIndexOf("佣金比例"), e = callback.lastIndexOf("（最");
            log.debug("step ratio index {}-{}", s, e);
            goodsInfo.setRebateRatio(FliUtil.convertToMoney(tupleClick.left.substring(s + 5, e)));
            if (goodsInfo.getRebateRatio() <= 0) {
                //无佣金
                return null;
            }
            goodsInfo.setRebateAmount(goodsInfo.getPrice() * goodsInfo.getRebateRatio() / 100d);
            log.debug("step set goods ratio {}", JSON.toJSONString(goodsInfo));

            Predicate<FluentWait.UntilState> actionCopy = i -> {
                bot.mouseLeftClick(point.x + 64, point.y + 450);
                bot.pressCtrlA();
                return true;
            };
            bot.delay(100);
            actionCopy.test(FluentWait.NULL());
            Tuple<String, Boolean> tupleCopy = waiter.until(x -> {
                String text = bot.copyAndGetText();
                return Tuple.of(text, Strings.containsIgnoreCase(text, "http"));
            }, actionCopy);
            AdvCode code = new AdvCode();
            code.setUrl(tupleCopy.left);
            return code;
        } finally {
            locker.unlock();
        }
    }
    //endregion
}
