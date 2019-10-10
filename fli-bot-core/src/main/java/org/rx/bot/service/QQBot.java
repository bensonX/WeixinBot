package org.rx.bot.service;

import org.rx.core.dto.bot.BotType;
import org.rx.core.dto.bot.MessageInfo;
import org.rx.core.api.Bot;

import java.util.List;

public class QQBot implements Bot {
    @Override
    public BotType getType() {
        return BotType.QQ;
    }

    @Override
    public void sendMessage(List<MessageInfo> messages) {

    }
}
