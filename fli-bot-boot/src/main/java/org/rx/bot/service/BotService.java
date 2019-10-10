package org.rx.bot.service;

import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.rx.bot.common.BotConfig;
import org.rx.core.InvalidOperationException;
import org.rx.core.NQuery;
import org.rx.core.dto.bot.BotType;
import org.rx.core.dto.bot.MessageInfo;
import org.rx.socks.tcp.RemotingFactor;
import org.rx.util.validator.EnableValid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import javax.validation.constraints.NotNull;
import java.util.Collections;
import java.util.List;

@EnableValid
@Service
@Slf4j
public class BotService {
//    @Getter
//    private WxBot wxBot;
    @Getter
    private WxMobileBot wxMobileBot;
    @Getter
    private BotConfig config;

    @Autowired
    public BotService(BotConfig config
//            , WxBot wxBot
    ) {
        this.config = config;
//        this.wxBot = wxBot;
//        RemotingFactor.listen(wxBot, config.getWxService().getRemotingPort(), config.getWxService().getTimeout());

        try {
            BotConfig.WxMobileConfig mConfig = config.getWxMobile();
            wxMobileBot = new WxMobileBot(mConfig.getCapturePeriod(), mConfig.getMaxCheckMessageCount(), mConfig.getMaxCaptureMessageCount(), mConfig.getMaxScrollMessageCount());
            wxMobileBot.setAutoResetWindow(config.getWxMobile().isAutoResetWindow());
            wxMobileBot.setAdminOpenId(config.getWxMobile().getAdminId());
            wxMobileBot.getServiceOpenIds().addAll(NQuery.of(mConfig.getServiceIds()).toSet());
            wxMobileBot.getGroupOpenIds().addAll(NQuery.of(mConfig.getGroupIds()).toSet());
            wxMobileBot.start();
            RemotingFactor.listen(wxMobileBot, mConfig.getRemotingPort(), mConfig.getTimeout());
        } catch (InvalidOperationException e) {
            log.warn("BotService", e);
        }
    }

    public void pushMessages(@NotNull MessageInfo message) {
        pushMessages(Collections.singletonList(message));
    }

    public void pushMessages(@NotNull List<MessageInfo> messages) {
        NQuery<MessageInfo> query = NQuery.of(messages);
        if (wxMobileBot != null) {
            wxMobileBot.sendMessage(query.where(p -> p.getBotType() == BotType.Wx).toList());
        }
//        wxBot.sendMessage(query.where(p -> p.getBotType() == BotType.WxService).toList());
    }
}
