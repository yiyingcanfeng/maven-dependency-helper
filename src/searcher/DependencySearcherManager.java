package searcher;

import actions.AppSettingsState;
import actions.MavenDependencyHelperAction;
import actions.MyInvocationHandler;
import com.intellij.notification.*;
import com.intellij.openapi.application.ApplicationInfo;
import com.intellij.openapi.project.Project;
import org.jsoup.Jsoup;
import searcher.impl.AliyunMavenDependencySearcher;
import searcher.impl.MvnRepositoryComDependencySearcher;
import searcher.impl.SearchMavenOrgDependencySearcher;

import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Proxy;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * @author yiyingcanfneg
 */
public class DependencySearcherManager {

    private static NotificationGroup NOTIFICATION_GROUP = null;

    private static final HashMap<Class<? extends DependencySearcher>, DependencySearcher> pool = new HashMap<>();

    public static ThreadPoolExecutor threadPool = MavenDependencyHelperAction.threadPool;
    public static ScheduledThreadPoolExecutor scheduledThreadPoolExecutor = MavenDependencyHelperAction.scheduledThreadPoolExecutor;
    private static ScheduledFuture<?> scheduledFuture;

    public static final int TIMEOUT = 3000;
    /**
     * 测速间隔
     */
    private static int speedTestInterval = AppSettingsState.getInstance().speedDetectInterval;

    static {
        String fullVersion = ApplicationInfo.getInstance().getFullVersion();
        // 'NotificationGroup(java.lang.String, com.intellij.notification.NotificationDisplayType, boolean)' is deprecated is deprecated after 2020.2.4
        if (Integer.parseInt(fullVersion.replace(".", "")) <= 202024) {
            try {
                Class<?> aClass = Class.forName("com.intellij.notification.NotificationGroup");
                NOTIFICATION_GROUP  = (NotificationGroup) aClass.getDeclaredConstructor(
                        String.class, NotificationDisplayType.class, boolean.class
                ).newInstance("MavenDependencyHelper", NotificationDisplayType.NONE, true);
            } catch (ClassNotFoundException | NoSuchMethodException | IllegalAccessException | InstantiationException | InvocationTargetException e) {
                e.printStackTrace();
            }
            //NOTIFICATION_GROUP = new NotificationGroup("MavenDependencyHelper", NotificationDisplayType.NONE, true);
        } else {
            try {
                Class<?> aClass = Class.forName("com.intellij.notification.NotificationGroupManager");
                Class<?> aClass1 = Class.forName("com.intellij.notification.impl.NotificationGroupManagerImpl");
                Object o = aClass1.getDeclaredConstructor().newInstance();
                Object instance = Proxy.newProxyInstance(
                        aClass.getClassLoader(),
                        new Class[]{aClass},
                        new MyInvocationHandler(o)
                );
                Object invoke = aClass.getMethod("getInstance").invoke(instance);
                NOTIFICATION_GROUP = (NotificationGroup) invoke.getClass().getMethod("getNotificationGroup", String.class).invoke(o, "MavenDependencyHelper");
            } catch (ClassNotFoundException | NoSuchMethodException | IllegalAccessException | InstantiationException | InvocationTargetException e) {
                e.printStackTrace();
            }
            //NOTIFICATION_GROUP = NotificationGroupManager.getInstance().getNotificationGroup("MavenDependencyHelper");
        }
        pool.put(AliyunMavenDependencySearcher.class, new AliyunMavenDependencySearcher());
        pool.put(MvnRepositoryComDependencySearcher.class, new MvnRepositoryComDependencySearcher());
        pool.put(SearchMavenOrgDependencySearcher.class, new SearchMavenOrgDependencySearcher());
        // 定时对搜索源进行测速，用以选择访问速度最佳的搜索源
        if (scheduledFuture == null) {
            scheduledFuture = scheduledThreadPoolExecutor.scheduleAtFixedRate(DependencySearcherManager::task, 0, speedTestInterval, TimeUnit.SECONDS);
        }

    }

    private static void task() {
        websiteSpeedTest();
        MavenDependencyHelperAction.dependencySearcher = getFastest();

        StringBuilder stringBuilder = new StringBuilder("MavenDependencyHelper search source speed:\n");
        for (Map.Entry<String, Class<? extends DependencySearcher>> entry : MavenDependencyHelperAction.textSearcherMap.entrySet()) {
            int speed = pool.get(entry.getValue()).getSpeed();
            stringBuilder.append(entry.getKey()).append(": ").append(speed);
            if (speed == TIMEOUT) {
                stringBuilder.append(" (timeout)");
            }
            stringBuilder.append("\n");
        }
        stringBuilder.append("detect speed interval: ").append(speedTestInterval).append("s\n");
        stringBuilder.append("next detect time: ").append(new SimpleDateFormat("HH:mm").format(new Date(System.currentTimeMillis() + speedTestInterval * 1000)));
        notify(stringBuilder.toString());
    }

    public static void setSpeedTestInterval(int i) {
        speedTestInterval = i;
        scheduledFuture.cancel(false);
        scheduledFuture = scheduledThreadPoolExecutor.scheduleAtFixedRate(DependencySearcherManager::task, 0, speedTestInterval, TimeUnit.SECONDS);
    }

    public static DependencySearcher get(Class<? extends DependencySearcher> clazz) {
        return pool.get(clazz);
    }

    public static DependencySearcher getFastest() {
        return pool.values().stream().sorted(Comparator.comparingInt(DependencySearcher::getSpeed)).collect(Collectors.toList()).get(0);
    }

    public static void websiteSpeedTest() {
        for (DependencySearcher value : pool.values()) {
            threadPool.execute(() -> {
                try {
                    long start = System.currentTimeMillis();
                    Jsoup.connect(value.getDetectSpeedUrl()).timeout(TIMEOUT).ignoreContentType(true).execute();
                    long end = System.currentTimeMillis();
                    value.setSpeed((int) (end - start));
                } catch (IOException e) {
                    value.setSpeed(TIMEOUT);
                    e.printStackTrace();
                }
            });
        }
    }

    public static Notification notify(String content) {
        return notify(null, content);
    }

    public static Notification notify(Project project, String content) {
        final Notification notification = NOTIFICATION_GROUP.createNotification(content, NotificationType.INFORMATION);
        notification.notify(project);
        return notification;
    }

}
