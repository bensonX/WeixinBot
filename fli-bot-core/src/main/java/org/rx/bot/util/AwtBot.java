package org.rx.bot.util;

import lombok.Getter;
import lombok.Setter;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.rx.core.*;
import org.rx.core.util.ImageUtil;
import org.rx.beans.DateTime;
import org.rx.io.Files;
import org.rx.util.function.Action;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.awt.image.BufferedImage;
import java.io.File;
import java.util.List;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Predicate;

import static org.rx.core.AsyncTask.TaskFactory;
import static org.rx.core.Contract.isNull;
import static org.rx.core.Contract.require;

@Slf4j
public class AwtBot {
    private static final String debugPath = App.readSetting("app.bot.debugPath");
    private static AwtBot awtBot;

    @SneakyThrows
    public static AwtBot getBot() {
        System.setProperty("java.awt.headless", "false");

        if (awtBot == null) {
            awtBot = new AwtBot();
            if (!Strings.isNullOrEmpty(debugPath)) {
                TaskFactory.schedule(() -> awtBot.saveScreen(null, null), 10 * 1000);
            }
        }
        return awtBot;
    }

    private final Robot bot;
    private final ReentrantLock clickLocker, pressLocker;
    @Getter
    private final AwtClipboard clipboard;
    @Getter
    @Setter
    private volatile int autoDelay;
    private volatile Rectangle screenRectangle;

    public Rectangle getScreenRectangle() {
        return isNull(screenRectangle, new Rectangle(Toolkit.getDefaultToolkit().getScreenSize()));
    }

    public void setScreenRectangle(Rectangle screenRectangle) {
        require(screenRectangle);

        this.screenRectangle = screenRectangle;
    }

    public String getClipboardText() {
        return clipboard.getText();
    }

    public void setClipboardText(String text) {
        clipboard.setContent(text);
    }

    @SneakyThrows
    private AwtBot() {
        this.autoDelay = 10;
        bot = new Robot();
//        bot.setAutoWaitForIdle(true);
        clickLocker = new ReentrantLock(true);
        pressLocker = new ReentrantLock(true);
        clipboard = new AwtClipboard();
    }

    public void delay(int delay) {
        bot.delay(delay);
    }

    public void saveScreen(BufferedImage key, String msg) {
        saveScreen(key, msg, debugPath);
    }

    public void saveScreen(BufferedImage key, String msg, String debugPath) {
        if (Strings.isNullOrEmpty(debugPath)) {
            return;
        }

        try {
            Dimension screenSize = Toolkit.getDefaultToolkit().getScreenSize();
            log.debug("ScreenSize: {}", screenSize);
            Files.createDirectory(Files.path(debugPath));

            String fileName = DateTime.now().toString(DateTime.Formats.skip(2).first());
            BufferedImage screenCapture = bot.createScreenCapture(new Rectangle(screenSize));
            ImageUtil.saveImage(screenCapture, String.format("%s/%s.png", debugPath, fileName));

            if (key == null) {
                return;
            }
            ImageUtil.saveImage(key, String.format("%s/%s_%s.png", debugPath, fileName, msg));
        } catch (Exception e) {
            log.warn("saveScreen", e);
        }
    }

    public Point clickByImage(BufferedImage keyImg) {
        return clickByImage(keyImg, getScreenRectangle());
    }

    public Point clickByImage(BufferedImage keyImg, Rectangle screenRectangle) {
        return clickByImage(keyImg, screenRectangle, true);
    }

    @SneakyThrows
    public Point clickByImage(BufferedImage keyImg, Rectangle screenRectangle, boolean throwOnEmpty) {
        require(keyImg);

        Point point = findScreenPoint(keyImg, screenRectangle);
        if (point == null) {
            if (!throwOnEmpty) {
                return null;
            }
            saveScreen(keyImg, "clickByImage");
            throw new InvalidOperationException("Can not found point");
        }

        point.x += keyImg.getWidth() / 2;
        point.y += keyImg.getHeight() / 2;
        mouseLeftClick(point);
        return point;
    }

    @SneakyThrows
    public Point findScreenPoint(String partImageFile) {
        require(partImageFile);

        return findScreenPoint(ImageIO.read(new File(partImageFile)));
    }

    public Point findScreenPoint(BufferedImage partImage) {
        return findScreenPoint(partImage, getScreenRectangle());
    }

    public Point findScreenPoint(BufferedImage partImage, Rectangle screenRectangle) {
        require(partImage, screenRectangle);

        Point point = ImageUtil.findPoint(captureScreen(screenRectangle), partImage, false);
        if (point != null) {
            point.x += screenRectangle.x;
            point.y += screenRectangle.y;
        }
        return point;
    }

    public List<Point> findScreenPoints(BufferedImage partImage) {
        return findScreenPoints(partImage, getScreenRectangle());
    }

    public List<Point> findScreenPoints(BufferedImage partImage, Rectangle screenRectangle) {
        require(partImage, screenRectangle);

        return NQuery.of(ImageUtil.findPoints(captureScreen(screenRectangle), partImage)).select(p -> {
            p.x += screenRectangle.x;
            p.y += screenRectangle.y;
            return p;
        }).toList();
    }

    public BufferedImage captureScreen() {
        return captureScreen(getScreenRectangle());
    }

    public BufferedImage captureScreen(int x, int y, int width, int height) {
        return captureScreen(new Rectangle(x, y, width, height));
    }

    public BufferedImage captureScreen(Rectangle rectangle) {
        require(rectangle);

        return bot.createScreenCapture(rectangle);
    }

    //region combo
    public String waitClipboardText(Predicate<FluentWait.UntilState> action, Predicate<String> resultCheck) {
        return clipboard.getText(action, resultCheck);
    }

    public String copyAndGetText() {
        try {
            return clipboard.getText(s -> {
                pressCtrlC();
                return true;
            }, null);
        } finally {
            bot.delay(autoDelay);
        }
    }

    public void setTextAndParse(String text) {
        clipboard.lock(() -> {
            clipboard.setContent(text);
            pressCtrlV();
            bot.delay(autoDelay);
            return null;
        });
    }

    public void setImageAndParse(Image image) {
        clipboard.lock(() -> {
            clipboard.setContent(image);
            pressCtrlV();
            bot.delay(autoDelay);
            return null;
        });
    }

    public void clickAndAltF4(int x, int y) {
        clickInvoke(() -> pressInvoke(() -> {
            mouseLeftClick(x, y);
            bot.delay(1000);
            mouseLeftClick(x, y);
            bot.keyPress(KeyEvent.VK_ALT);
            bot.keyPress(KeyEvent.VK_F4);
            bot.keyRelease(KeyEvent.VK_F4);
            bot.keyRelease(KeyEvent.VK_ALT);
            bot.delay(autoDelay);
        }));
    }
    //endregion

    //region mouse
    public Point getMouseLocation() {
        return MouseInfo.getPointerInfo().getLocation();
    }

    public void mouseRelease() {
        clickInvoke(() -> {
            bot.mouseRelease(InputEvent.BUTTON1_MASK);
            bot.delay(autoDelay);
        });
    }

    public void mouseMove(Point point) {
        require(point);

        mouseMove(point.x, point.y);
    }

    public void mouseMove(int x, int y) {
        clickInvoke(() -> {
            bot.mouseMove(x, y);
            bot.delay(autoDelay);
        });
    }

    public void mouseLeftClick(Point point) {
        require(point);

        mouseLeftClick(point.x, point.y);
    }

    public void mouseLeftClick(int x, int y) {
        clickInvoke(() -> {
            bot.mouseMove(x, y);
            bot.delay(5);
            bot.mousePress(InputEvent.BUTTON1_MASK);
            bot.delay(5);
            bot.mouseRelease(InputEvent.BUTTON1_MASK);
            bot.delay(autoDelay);
        });
    }

    public void mouseDoubleLeftClick(Point point) {
        require(point);

        mouseDoubleLeftClick(point.x, point.y);
    }

    public void mouseDoubleLeftClick(int x, int y) {
        clickInvoke(() -> {
            bot.mouseMove(x, y);
            bot.mousePress(InputEvent.BUTTON1_MASK);
            bot.mouseRelease(InputEvent.BUTTON1_MASK);
            bot.delay(10);
            bot.waitForIdle();
            bot.mousePress(InputEvent.BUTTON1_MASK);
            bot.mouseRelease(InputEvent.BUTTON1_MASK);
            bot.delay(autoDelay);
        });
    }

    public void mouseRightClick(Point point) {
        require(point);

        mouseRightClick(point.x, point.y);
    }

    public void mouseRightClick(int x, int y) {
        clickInvoke(() -> {
            bot.mouseMove(x, y);
            bot.mousePress(InputEvent.BUTTON3_MASK);
            bot.mouseRelease(InputEvent.BUTTON3_MASK);
            bot.delay(autoDelay);
        });
    }

    public void mouseWheel(int wheelAmt) {
        clickInvoke(() -> {
            bot.mouseWheel(wheelAmt);
            bot.delay(autoDelay);
        });
    }

    private void clickInvoke(Action action) {
        clickLocker.lock();
        try {
            action.invoke();
        } finally {
            clickLocker.unlock();
        }
    }
    //endregion

    //region keyboard
    public void pressCtrlAltW() {
        pressCtrlAlt(KeyEvent.VK_W);
    }

    private void pressCtrlAlt(int key) {
        pressInvoke(() -> {
            bot.keyPress(KeyEvent.VK_CONTROL);
            bot.keyPress(KeyEvent.VK_ALT);
            bot.keyPress(key);
            bot.keyRelease(key);
            bot.keyRelease(KeyEvent.VK_ALT);
            bot.keyRelease(KeyEvent.VK_CONTROL);
            bot.delay(autoDelay);
        });
    }

    public void pressCtrlF() {
        pressCtrl(KeyEvent.VK_F);
    }

    public void pressCtrlA() {
        pressCtrl(KeyEvent.VK_A);
    }

    public void pressCtrlC() {
        pressCtrl(KeyEvent.VK_C);
    }

    public void pressCtrlV() {
        pressCtrl(KeyEvent.VK_V);
    }

    private void pressCtrl(int key) {
        pressInvoke(() -> {
            bot.keyPress(KeyEvent.VK_CONTROL);
            bot.keyPress(key);
            bot.keyRelease(key);
            bot.keyRelease(KeyEvent.VK_CONTROL);
            bot.delay(autoDelay);
        });
    }

    public void pressSpace() {
        press(KeyEvent.VK_SPACE);
    }

    public void pressDelete() {
        press(KeyEvent.VK_DELETE);
    }

    public void pressEnter() {
        press(KeyEvent.VK_ENTER);
    }

    public void press(int key) {
        pressInvoke(() -> {
            bot.keyPress(key);
            bot.keyRelease(key);
            bot.delay(autoDelay);
        });
    }

    private void pressInvoke(Action action) {
        pressLocker.lock();
        try {
            action.invoke();
        } finally {
            pressLocker.unlock();
        }
    }
    //endregion
}
