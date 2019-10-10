package org.rx.bot.web;

import org.rx.bot.service.BotService;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.context.request.async.WebAsyncTask;

import javax.annotation.Resource;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

@RestController
public class BotController {
    @Resource
    private BotService botService;

//    @RequestMapping("/wx")
//    public WebAsyncTask wx(HttpServletRequest request, HttpServletResponse response) {
//        return new WebAsyncTask<>(() -> {
//            botService.getWxBot().handleCallback(request, response);
//            return null;
//        });
//    }
}
