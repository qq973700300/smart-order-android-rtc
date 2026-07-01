package com.example.test5.device;

import android.util.Log;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 纯文本 TCP 客户端，用于塔石料仓、越疆机械臂等 ASCII 协议设备。
 */
public final class TcpTextClient {

    public interface Listener {
        void onMessage(String line);

        void onDisconnected(String reason);
    }

    private static final String TAG = "TcpTextClient";
    private static final Charset ASCII = StandardCharsets.US_ASCII;

    private final CopyOnWriteArrayList<Listener> listeners = new CopyOnWriteArrayList<>();
    private final AtomicBoolean receiving = new AtomicBoolean(false);
    private final StringBuilder receiveBuffer = new StringBuilder();

    private Socket socket;
    private OutputStream outputStream;
    private Thread receiveThread;

    public void addListener(Listener listener) {
        listeners.add(listener);
    }

    public void removeListener(Listener listener) {
        listeners.remove(listener);
    }

    public boolean isConnected() {
        return socket != null && socket.isConnected() && !socket.isClosed();
    }

    public synchronized void connect(String host, int port, int timeoutMs) throws IOException {
        disconnect();
        Socket s = new Socket();
        s.connect(new InetSocketAddress(host.trim(), port), timeoutMs);
        s.setTcpNoDelay(true);
        s.setSoTimeout(0);
        socket = s;
        outputStream = s.getOutputStream();
        startReceiveLoop(s.getInputStream());
        Log.i(TAG, "Connected " + host + ":" + port);
    }

    public synchronized void disconnect() {
        receiving.set(false);
        if (receiveThread != null) {
            receiveThread.interrupt();
            receiveThread = null;
        }
        if (socket != null) {
            try {
                socket.close();
            } catch (IOException ignored) {
            }
            socket = null;
            outputStream = null;
        }
    }

    public synchronized SendResult send(String text) throws IOException {
        if (!isConnected() || outputStream == null) {
            throw new IOException("未连接");
        }
        byte[] data = text.getBytes(ASCII);
        outputStream.write(data);
        outputStream.flush();
        Log.i(TAG, "TX: " + text);
        return new SendResult(text, data.length);
    }

    public String drainReceivedText() {
        synchronized (receiveBuffer) {
            String text = receiveBuffer.toString();
            receiveBuffer.setLength(0);
            return text;
        }
    }

    private void startReceiveLoop(InputStream inputStream) {
        receiving.set(true);
        receiveThread = new Thread(() -> {
            byte[] buf = new byte[1024];
            try {
                while (receiving.get() && !Thread.currentThread().isInterrupted()) {
                    int read = inputStream.read(buf);
                    if (read < 0) {
                        notifyDisconnected("连接已关闭");
                        break;
                    }
                    if (read > 0) {
                        String chunk = new String(buf, 0, read, ASCII);
                        appendReceived(chunk);
                        Log.i(TAG, "RX: " + chunk);
                        for (Listener listener : listeners) {
                            listener.onMessage(chunk);
                        }
                    }
                }
            } catch (IOException e) {
                if (receiving.get()) {
                    notifyDisconnected(e.getMessage());
                }
            }
        }, "TcpTextReceive");
        receiveThread.setDaemon(true);
        receiveThread.start();
    }

    private void appendReceived(String chunk) {
        synchronized (receiveBuffer) {
            receiveBuffer.append(chunk);
        }
    }

    private void notifyDisconnected(String reason) {
        receiving.set(false);
        for (Listener listener : listeners) {
            listener.onDisconnected(reason != null ? reason : "");
        }
    }

    public static final class SendResult {
        public final String text;
        public final int bytesSent;

        SendResult(String text, int bytesSent) {
            this.text = text;
            this.bytesSent = bytesSent;
        }
    }
}
