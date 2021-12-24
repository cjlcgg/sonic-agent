package com.sonic.agent.tools;

import com.android.ddmlib.IDevice;
import com.sonic.agent.bridge.android.AndroidDeviceBridgeTool;
import com.sonic.agent.tests.TaskManager;
import com.sonic.agent.tests.android.AndroidTestTaskBootThread;
import com.sonic.agent.tests.android.ScrcpyInputSocketThread;
import com.sonic.agent.tests.android.ScrcpyLocalThread;
import com.sonic.agent.tests.android.ScrcpyOutputSocketThread;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.websocket.Session;
import java.util.List;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.atomic.AtomicReference;

import static com.sonic.agent.tests.android.AndroidTestTaskBootThread.ANDROID_TEST_TASK_BOOT_PRE;

public class ScrcpyServerTool {
    private final Logger logger = LoggerFactory.getLogger(ScrcpyServerTool.class);

    public Thread start(
            String udId,
            AtomicReference<String[]> deviceInfo,
            AtomicReference<List<byte[]>> imgList,
            String pic,
            int tor,
            Session session
    ) {
        // 这里的AndroidTestTaskBootThread仅作为data bean使用，不会启动
        return start(udId, deviceInfo, imgList, pic, tor, session, new AndroidTestTaskBootThread().setUdId(udId));
    }


    public Thread start(
            String udId,
            AtomicReference<String[]> deviceInfo,
            AtomicReference<List<byte[]>> imgList,
            String pic,
            int tor,
            Session session,
            AndroidTestTaskBootThread androidTestTaskBootThread
    ) {
        IDevice iDevice = AndroidDeviceBridgeTool.getIDeviceByUdId(udId);
        String key = androidTestTaskBootThread.formatThreadName(ANDROID_TEST_TASK_BOOT_PRE);
        int s;
        if (tor == -1) {
            s = AndroidDeviceBridgeTool.getScreen(AndroidDeviceBridgeTool.getIDeviceByUdId(udId));
        } else {
            s = tor;
        }
        // 启动scrcpy服务
        ScrcpyLocalThread scrcpyThread = new ScrcpyLocalThread(iDevice, pic, s * 90, session, androidTestTaskBootThread);
        TaskManager.startChildThread(key, scrcpyThread);

        // 等待启动
        int wait = 0;
        while (!scrcpyThread.getIsFinish().tryAcquire()) {
            wait++;
            try {
                Thread.sleep(500);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
            // 启动失败了，强行跳过，保证其它服务可用
            if (wait > 8) {
                break;
            }
        }
        // 启动输入流
        ScrcpyInputSocketThread scrcpyInputSocketThread = new ScrcpyInputSocketThread(iDevice, new LinkedBlockingQueue<>(), scrcpyThread, session);
        // 启动输出流
        ScrcpyOutputSocketThread scrcpyOutputSocketThread = new ScrcpyOutputSocketThread(scrcpyInputSocketThread, deviceInfo, imgList, session, pic );
        TaskManager.startChildThread(key, scrcpyInputSocketThread, scrcpyOutputSocketThread);
        return scrcpyThread; // server线程
    }

}
