package org.rx.bot.common;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;

@Data
@Component
@ConfigurationProperties(prefix = "app.bot")
public class BotConfig {
    @Data
    @Component
    @ConfigurationProperties(prefix = "app.bot.wx-service")
    public class WxServiceConfig {
        private int remotingPort;
        private int timeout;
    }

    @Data
    @Component
    @ConfigurationProperties(prefix = "app.bot.wx-mobile")
    public class WxMobileConfig {
        private int remotingPort;
        private int timeout;
        private int capturePeriod;
        private int maxCheckMessageCount;
        private int maxCaptureMessageCount;
        private int maxScrollMessageCount;
        private boolean autoResetWindow;
        private String adminId;
        private String[] serviceIds;
        private String[] groupIds;
    }

    @Resource
    private WxServiceConfig wxService;
    @Resource
    private WxMobileConfig wxMobile;
}
