package com.sonic.agent.tests.android;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.websocket.Session;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.atomic.AtomicReference;

import static com.sonic.agent.tools.AgentTool.*;

/**
 * 视频流输出线程
 */
public class ScrcpyOutputSocketThread extends Thread {

    private final Logger log = LoggerFactory.getLogger(ScrcpyOutputSocketThread.class);

    /**
     * 占用符逻辑参考：{@link AndroidTestTaskBootThread#ANDROID_TEST_TASK_BOOT_PRE}
     */
    public final static String ANDROID_OUTPUT_SOCKET_PRE = "android-output-socket-task-%s-%s-%s";

    private ScrcpyInputSocketThread scrcpyInputSocketThread;

    private AtomicReference<String[]> deviceInfo;

    private AtomicReference<List<byte[]>> imgList;

    private Session session;

    private String pic;

    private String udId;

    private AndroidTestTaskBootThread androidTestTaskBootThread;

    public ScrcpyOutputSocketThread(
            ScrcpyInputSocketThread scrcpyInputSocketThread,
            AtomicReference<String[]> deviceInfo,
            AtomicReference<List<byte[]>> imgList,
            Session session,
            String pic
    ) {
        this.scrcpyInputSocketThread = scrcpyInputSocketThread;
        this.deviceInfo = deviceInfo;
        this.imgList = imgList;
        this.session = session;
        this.pic = pic;
        this.androidTestTaskBootThread = scrcpyInputSocketThread.getAndroidTestTaskBootThread();
        this.setDaemon(true);
        this.setName(androidTestTaskBootThread.formatThreadName(ANDROID_OUTPUT_SOCKET_PRE));
    }

    @Override
    public void run() {
        boolean sendFlag = false;
        byte[] body = new byte[0];
        while (scrcpyInputSocketThread.isAlive()) {
            Queue<byte[]> dataQueue = scrcpyInputSocketThread.getDataQueue();
            while (!dataQueue.isEmpty()){
                byte[] buffer = dataQueue.poll();
//                log.info("长度=================>{}",buffer.length);
                for (int cursor = 0 ; cursor < buffer.length - 5;){
                    boolean first = (buffer[cursor] & 0xff) == 0;
                    boolean second = (buffer[cursor + 1] & 0xff) == 0;
                    boolean third = (buffer[cursor + 2] & 0xff) == 0;
                    boolean fourth =  (buffer[cursor + 3] & 0xff) == 1;
                    boolean fifth =  (buffer[cursor + 4] & 0xff) == 65;
                    if (first && second){ // 先判断是否结束
                        if (body.length > 1){
//                            log.info("执行直接发送body，并重置内容");
                            body = addBytes(body, subByteArray(buffer, 0, cursor));
                            sendByte(session, body);
                            body = new byte[0];
                        }
                    }
                    if (first && second && third && fourth && fifth){ // 找到开始
                        sendFlag = false;
                        int start = cursor;
//                        log.info("起始坐标=================>{}", start);
                        for ( cursor += 4; cursor < buffer.length - 5; cursor ++){
                            first = (buffer[cursor] & 0xff) == 0;
                            second = (buffer[cursor + 1] & 0xff) == 0;
                            if (first && second ) {  // 找到结束
//                                log.info("结束坐标=================>{}", cursor);
                                body = subByteArray(buffer,start, cursor);
//                                log.info("同一个数组中I帧，执行直接发送截取内容，跳出循环，开始起始位置查找");
                                sendByte(session, body);
                                body = new byte[0];
                                sendFlag = true;
                                break;
                            }
                        }
                        if (sendFlag) continue;
//                        log.info("本次队列，未找到结束点，先拷贝");
                        body = addBytes(body, subByteArray(buffer, start, cursor));
                        continue;
                    }
                    cursor ++;
                }
            }
        }
    }
}