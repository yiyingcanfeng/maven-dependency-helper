package searcher;

import actions.AppSettingsState;
import actions.MavenDependencyHelperAction;
import com.intellij.notification.Notification;
import com.intellij.notification.NotificationDisplayType;
import com.intellij.notification.NotificationGroup;
import com.intellij.notification.NotificationType;
import com.intellij.openapi.application.ApplicationInfo;
import com.intellij.openapi.project.Project;
import org.apache.maven.artifact.versioning.DefaultArtifactVersion;
import org.jsoup.Jsoup;
import searcher.impl.AliyunMavenDependencySearcher;
import searcher.impl.MvnRepositoryComDependencySearcher;
import searcher.impl.SearchMavenOrgDependencySearcher;
import util.OkHttpUtils;

import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.text.SimpleDateFormat;
import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
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
        // 'NotificationGroup(java.lang.String, com.intellij.notification.NotificationDisplayType, boolean)' is deprecated after 2020.2.4
        DefaultArtifactVersion version202024 = new DefaultArtifactVersion("2020.2.4");
        DefaultArtifactVersion versionCurrent = new DefaultArtifactVersion(ApplicationInfo.getInstance().getFullVersion());
        if (versionCurrent.compareTo(version202024) <= 0) {
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
                Class<?> notificationGroupManagerClass = Class.forName("com.intellij.notification.NotificationGroupManager");
                Object notificationGroupManager = notificationGroupManagerClass.getMethod("getInstance").invoke(null);

                NOTIFICATION_GROUP = (NotificationGroup) notificationGroupManagerClass.getMethod("getNotificationGroup", String.class).invoke(notificationGroupManager, "MavenDependencyHelper");
            } catch (ClassNotFoundException | NoSuchMethodException | IllegalAccessException | InvocationTargetException e) {
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
        stringBuilder.append("next detect time: ").append(new SimpleDateFormat("HH:mm").format(new Date(System.currentTimeMillis() + speedTestInterval * 1000L)));
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
                long start = System.currentTimeMillis();
                String detectSpeedUrl = value.getDetectSpeedUrl();
                OkHttpUtils.get(detectSpeedUrl);
                //Jsoup.connect(detectSpeedUrl).timeout(TIMEOUT).ignoreContentType(true).execute();
                long end = System.currentTimeMillis();
                value.setSpeed((int) (end - start));
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
