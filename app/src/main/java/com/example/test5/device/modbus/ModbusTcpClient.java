package com.example.test5.device.modbus;

import android.util.Log;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Modbus TCP 客户端（功能码 0x03 读 / 0x06 写单寄存器）。
 */
public final class ModbusTcpClient {

    private static final String TAG = "ModbusTcp";
    private static final AtomicInteger TRANSACTION_ID = new AtomicInteger(1);

    private Socket socket;
    private OutputStream outputStream;
    private InputStream inputStream;
    private int unitId = 1;

    public synchronized void connect(String host, int port, int unitId, int timeoutMs) throws IOException {
        disconnect();
        this.unitId = unitId;
        Socket s = new Socket();
        s.connect(new InetSocketAddress(host.trim(), port), timeoutMs);
        s.setSoTimeout(5000);
        socket = s;
        outputStream = s.getOutputStream();
        inputStream = s.getInputStream();
        Log.i(TAG, "Connected " + host + ":" + port + " unitId=" + unitId);
    }

    public synchronized void disconnect() {
        if (socket != null) {
            try {
                socket.close();
            } catch (IOException ignored) {
            }
            socket = null;
            outputStream = null;
            inputStream = null;
        }
    }

    public boolean isConnected() {
        return socket != null && socket.isConnected() && !socket.isClosed();
    }

    public int getUnitId() {
        return unitId;
    }

    public synchronized ModbusResult readHoldingRegister(int address) throws IOException {
        int txId = TRANSACTION_ID.getAndIncrement() & 0xFFFF;
        byte[] request = buildReadRequest(txId, unitId, address, 1);
        logHex("TX", request);
        write(request);
        byte[] response = readResponse(9);
        logHex("RX", response);
        parseException(response, 0x03);
        int value = ((response[9] & 0xFF) << 8) | (response[10] & 0xFF);
        return ModbusResult.ok("read", address, value, toHex(request), toHex(response));
    }

    public synchronized ModbusResult writeSingleRegister(int address, int value) throws IOException {
        int txId = TRANSACTION_ID.getAndIncrement() & 0xFFFF;
        int registerValue = value & 0xFFFF;
        byte[] request = buildWriteRequest(txId, unitId, address, registerValue);
        logHex("TX", request);
        write(request);
        byte[] response = readResponse(12);
        logHex("RX", response);
        parseException(response, 0x06);
        return ModbusResult.ok("write", address, registerValue, toHex(request), toHex(response));
    }

    /** 与上位机 TcpModbusHelper.ConvertIntToUShort 一致：负数按 ushort 编码。 */
    public static int intToRegisterValue(int value) {
        return value & 0xFFFF;
    }

    private void write(byte[] data) throws IOException {
        if (!isConnected() || outputStream == null) {
            throw new IOException("未连接");
        }
        outputStream.write(data);
        outputStream.flush();
    }

    private byte[] readResponse(int expectedLength) throws IOException {
        if (inputStream == null) {
            throw new IOException("未连接");
        }
        byte[] buffer = new byte[expectedLength];
        int offset = 0;
        while (offset < expectedLength) {
            int read = inputStream.read(buffer, offset, expectedLength - offset);
            if (read < 0) {
                throw new IOException("连接已关闭");
            }
            offset += read;
        }
        return buffer;
    }

    private static byte[] buildReadRequest(int transactionId, int unitId, int address, int quantity) {
        byte[] pdu = new byte[5];
        pdu[0] = 0x03;
        pdu[1] = (byte) ((address >> 8) & 0xFF);
        pdu[2] = (byte) (address & 0xFF);
        pdu[3] = (byte) ((quantity >> 8) & 0xFF);
        pdu[4] = (byte) (quantity & 0xFF);
        return wrapMbap(transactionId, unitId, pdu);
    }

    private static byte[] buildWriteRequest(int transactionId, int unitId, int address, int value) {
        byte[] pdu = new byte[5];
        pdu[0] = 0x06;
        pdu[1] = (byte) ((address >> 8) & 0xFF);
        pdu[2] = (byte) (address & 0xFF);
        pdu[3] = (byte) ((value >> 8) & 0xFF);
        pdu[4] = (byte) (value & 0xFF);
        return wrapMbap(transactionId, unitId, pdu);
    }

    private static byte[] wrapMbap(int transactionId, int unitId, byte[] pdu) {
        byte[] frame = new byte[7 + pdu.length];
        frame[0] = (byte) ((transactionId >> 8) & 0xFF);
        frame[1] = (byte) (transactionId & 0xFF);
        frame[2] = 0;
        frame[3] = 0;
        int length = pdu.length + 1;
        frame[4] = (byte) ((length >> 8) & 0xFF);
        frame[5] = (byte) (length & 0xFF);
        frame[6] = (byte) (unitId & 0xFF);
        System.arraycopy(pdu, 0, frame, 7, pdu.length);
        return frame;
    }

    private static void parseException(byte[] response, int expectedFunction) throws IOException {
        if (response.length < 9) {
            throw new IOException("响应长度不足");
        }
        int function = response[7] & 0xFF;
        if ((function & 0x80) != 0) {
            int code = response[8] & 0xFF;
            throw new IOException("Modbus 异常码 " + code);
        }
        if (function != expectedFunction) {
            throw new IOException("功能码不匹配: " + function);
        }
    }

    private static void logHex(String prefix, byte[] data) {
        Log.i(TAG, prefix + ": " + toHex(data));
    }

    private static String toHex(byte[] data) {
        StringBuilder builder = new StringBuilder();
        for (byte b : data) {
            builder.append(String.format("%02X ", b));
        }
        return builder.toString().trim();
    }

    public static final class ModbusResult {
        public final boolean success;
        public final String action;
        public final int address;
        public final int value;
        public final String requestHex;
        public final String responseHex;
        public final String errorMessage;

        private ModbusResult(
                boolean success,
                String action,
                int address,
                int value,
                String requestHex,
                String responseHex,
                String errorMessage
        ) {
            this.success = success;
            this.action = action;
            this.address = address;
            this.value = value;
            this.requestHex = requestHex;
            this.responseHex = responseHex;
            this.errorMessage = errorMessage;
        }

        static ModbusResult ok(String action, int address, int value, String requestHex, String responseHex) {
            return new ModbusResult(true, action, address, value, requestHex, responseHex, "");
        }

        static ModbusResult failure(String action, int address, int value, String errorMessage) {
            return new ModbusResult(false, action, address, value, "", "", errorMessage);
        }

        public String formatForDisplay() {
            StringBuilder builder = new StringBuilder();
            builder.append("action=").append(action).append('\n');
            builder.append("address=").append(address).append('\n');
            builder.append("value=").append(value);
            if (value >= 32768) {
                builder.append(" (signed=").append((short) value).append(')');
            }
            builder.append('\n');
            builder.append("success=").append(success).append('\n');
            if (!requestHex.isEmpty()) {
                builder.append("\nRequest\n").append(requestHex).append('\n');
            }
            if (!responseHex.isEmpty()) {
                builder.append("\nResponse\n").append(responseHex).append('\n');
            }
            if (!success && errorMessage != null && !errorMessage.isEmpty()) {
                builder.append("\nerror=").append(errorMessage);
            }
            return builder.toString();
        }
    }
}
