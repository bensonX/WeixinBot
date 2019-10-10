package org.rx.bot.service;

import org.rx.beans.Tuple;
import org.rx.core.NQuery;
import org.rx.core.StringBuilder;

import java.util.ArrayList;
import java.util.List;

import static org.rx.core.Contract.isNull;
import static org.rx.core.Contract.require;

public class SensitiveService {
    public static final SensitiveService instance = new SensitiveService();
    private List<Tuple<String, String>> words;

    private SensitiveService() {
        words = new ArrayList<>();
        addWord("返利", "返現");
        addWord("返现", "返現");
        addWord("淘宝", "淘寶");
        addWord("天猫", "天貓");
        addWord("支付宝", "吱付寶");
        // v2
        addWord("口令", "口҉令҉");
        addWord("红包", "紅包");
        addWord("提现", "提現");
        addWord("到账", "到賬");
        addWord("金额", "金額");
        addWord("补贴", "補貼");
    }

    public void addWord(String source) {
        addWord(source, null);
    }

    public void addWord(String source, String target) {
        require(source);

        words.add(Tuple.of(source, target));
    }

    public String wrap(String message) {
        StringBuilder buf = new StringBuilder(message);
        for (Tuple<String, String> tuple : NQuery.of(words).toList()) {
            buf.replace(tuple.left, isNull(tuple.right, ""));
        }
        return buf.toString();
    }
}
