package org.rx.bot.util;

import com.google.common.base.Stopwatch;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.rx.beans.$;
import org.rx.core.FluentWait;
import org.rx.core.Strings;
import org.rx.util.function.Func;

import java.awt.*;
import java.awt.datatransfer.*;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Predicate;

import static org.rx.core.Contract.eq;
import static org.rx.core.Contract.require;

/**
 * ClipboardOwner
 */
@Slf4j
public class AwtClipboard {
    private final Clipboard clipboard;
    private final ReentrantLock locker;
    private final FluentWait waiter;
    private volatile String lastText;

    public AwtClipboard() {
        clipboard = Toolkit.getDefaultToolkit().getSystemClipboard();
        locker = new ReentrantLock(true);
        waiter = FluentWait.newInstance(2600, 50).retryMills(200).throwOnFail(false);
    }

    private void beforeGet() {
        setContent(Strings.empty);
        String result = waiter.until(s -> {
            String text = getText();
            if (Strings.empty.equals(text)) {
                return text;
            }
            return null;
        });
        if (result == null) {
            log.warn("reset fail");
        }
    }

    //fix java.lang.IllegalStateException: cannot open system clipboard
    @SneakyThrows
    private void delay(int millis) {
        Thread.sleep(millis);
    }

    public <T> T lock(Func<T> action) {
        require(action);

        locker.lock();
        try {
            return action.invoke();
        } finally {
            locker.unlock();
        }
    }

    public String resetLastText(String text) {
        String last = lastText;
        lastText = text;
        return last;
    }

    public String getText() {
        return lock(() -> getText(false));
    }

    @SneakyThrows
    private String getText(boolean emptyOnNull) {
        String text;
        delay(2);
        Transferable t = clipboard.getContents(null);
        if (t == null || !t.isDataFlavorSupported(DataFlavor.stringFlavor)) {
            text = null;
        } else {
            text = (String) t.getTransferData(DataFlavor.stringFlavor);
        }
        if (text == null && emptyOnNull) {
            text = Strings.empty;
        }
        return text;
    }

    public String getText(Predicate<FluentWait.UntilState> setAction, Predicate<String> resultCheck) {
        require(setAction);

        return lock(() -> {
            Stopwatch watcher = Stopwatch.createStarted();
            beforeGet();
            setAction.test(FluentWait.NULL());
            if (lastText == null) {
                lastText = Strings.empty;
            }
            $<String> $txt = $.$();
            String result = waiter.until(s -> {
                $txt.v = getText(true);
                if (!Strings.empty.equals($txt.v)
                        && (resultCheck == null || resultCheck.test($txt.v))
                        && !eq(lastText, $txt.v)) {
                    return $txt.v;
                }
                return null;
            }, setAction);
            long ms = watcher.elapsed(TimeUnit.MILLISECONDS);
            if (result != null) {
                log.warn("getText fail, elapsed={}ms\nlast={}\ncurrent={}", ms, lastText, $txt.v);
            } else {
                log.info("getText ok, elapsed={}ms last={} current={}", ms, lastText, $txt.v);
            }
            return lastText = $txt.v;
        });
    }

    public void setContent(String text) {
        lock(() -> {
            clipboard.setContents(new StringSelection(text), null);
            return null;
        });
    }

    public void setContent(Image image) {
        lock(() -> {
            clipboard.setContents(new Transferable() {
                @Override
                public Object getTransferData(DataFlavor flavor) throws UnsupportedFlavorException {
                    if (!isDataFlavorSupported(flavor)) {
                        throw new UnsupportedFlavorException(flavor);
                    }
                    return image;
                }

                @Override
                public DataFlavor[] getTransferDataFlavors() {
                    return new DataFlavor[]{DataFlavor.imageFlavor};
                }

                @Override
                public boolean isDataFlavorSupported(DataFlavor flavor) {
                    return DataFlavor.imageFlavor.equals(flavor);
                }
            }, null);
            return null;
        });
    }
}
