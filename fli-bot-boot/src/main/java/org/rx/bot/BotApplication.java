package org.rx.bot;

import org.rx.bot.util.AwtBot;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.ImportResource;
import springfox.documentation.swagger2.annotations.EnableSwagger2;

@SpringBootApplication
@ImportResource("classpath:applicationContext.xml")
@EnableSwagger2
public class BotApplication {
    public static void main(String[] args) {
        AwtBot.getBot();
        SpringApplication.run(BotApplication.class, args);
    }
}
