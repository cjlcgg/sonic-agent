package com.sonic.agent.tests.android;

import com.alibaba.fastjson.JSONObject;
import com.sonic.agent.tools.H264SPSPaser;
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

                for (int cursor = 0 ; cursor < buffer.length - 5;){
                    boolean startFlag = (buffer[cursor] & 0xff) == 0 && (buffer[cursor + 1] & 0xff) == 0
                            && (buffer[cursor + 2] & 0xff) == 0 && (buffer[cursor + 3] & 0xff) == 1
                            && (buffer[cursor + 4] & 0xff) != 104;
                    if (!sendFlag && startFlag){ // 先判断是否结束
                        if (body.length > 1){
                            body = addBytes(body, subByteArray(buffer, 0, cursor));
                            sendMessage(body);
                            body = new byte[0];
                        }
                    }
                    if (startFlag){ // 找到开始
                        sendFlag = false;
                        int start = cursor;
                        for ( cursor += 4; cursor < buffer.length - 5; cursor ++){
                            boolean endFlag = (buffer[cursor] & 0xff) == 0 && (buffer[cursor + 1] & 0xff) == 0
                                    && (buffer[cursor + 2] & 0xff) == 0 && (buffer[cursor + 3] & 0xff) == 1
                                    && (buffer[cursor + 4] & 0xff) != 104;
                            if (endFlag) {  // 找到结束
                                body = subByteArray(buffer,start, cursor);
                                sendMessage(body);
                                body = new byte[0];
                                sendFlag = true;
                                break;
                            }
                        }
                        if (sendFlag) continue;
                        body = addBytes(body, subByteArray(buffer, start, buffer.length));
                        continue;
                    }
                    cursor ++;
                }
            }
        }
    }

    public void sendMessage(byte[] body){
        if (body.length < 5) return;
        if ((body[4] & 0xff) == 103){ // 根据SPS帧解析分辨率
            H264SPSPaser h264SPSPaser = new H264SPSPaser();
            h264SPSPaser.parse(subByteArray(body,4,body.length));
            JSONObject size = new JSONObject();
            size.put("size", "msg");
            size.put("height", h264SPSPaser.getHeight());
            size.put("width", h264SPSPaser.getWidth());
            sendText(session, size.toJSONString());
        }
        sendByte(session, filterEndSpace(body));
    }

    public byte[] filterEndSpace(byte[] body){ // 过滤末尾的0位
        if (body.length < 1) return body;
        int length = body.length;
        for (int cursor = body.length-1; cursor < body.length; cursor --){
            if ((body[cursor] & 0xff) == 0) {
                length--;
                continue;
            }
            break;
        }
        if (length != body.length){
            body = subByteArray(body,0,length);
        }
        return body;
    }


}