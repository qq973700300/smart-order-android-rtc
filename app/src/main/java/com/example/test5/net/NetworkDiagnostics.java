package com.example.test5.net;

import android.content.Context;
import android.net.ConnectivityManager;
import android.net.LinkAddress;
import android.net.LinkProperties;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.RouteInfo;
import android.os.Build;
import android.util.Log;

import androidx.annotation.Nullable;

import java.net.InetAddress;
import java.net.URI;
import java.util.List;

/**
 * 双网卡诊断：插有线 + WiFi 时，记录系统默认网络、各接口 IP/网关/路由。
 * Logcat 过滤：NetRoute
 */
public final class NetworkDiagnostics {

    public static final String TAG = "NetRoute";

    @Nullable
    private static Context appContext;

    private NetworkDiagnostics() {
    }

    public static void init(Context context) {
        appContext = context.getApplicationContext();
    }

    public static void logSnapshot(String reason) {
        Context ctx = appContext;
        if (ctx == null) {
            Log.w(TAG, "[" + reason + "] appContext 未初始化");
            return;
        }
        ConnectivityManager cm = (ConnectivityManager) ctx.getSystemService(Context.CONNECTIVITY_SERVICE);
        if (cm == null) {
            Log.w(TAG, "[" + reason + "] ConnectivityManager=null");
            return;
        }

        Log.i(TAG, "======== " + reason + " ========");
        Network active = cm.getActiveNetwork();
        logNetwork(cm, active, "ACTIVE(default)");

        for (Network network : cm.getAllNetworks()) {
            if (network != null && !network.equals(active)) {
                logNetwork(cm, network, "other");
            }
        }
        Log.i(TAG, "======== end " + reason + " ========");
    }

    /** HTTP/云端请求前调用。 */
    public static void logBeforeCloudRequest(String action, String url) {
        String host = parseHost(url);
        Log.i(TAG, "[cloud] action=" + action + " url=" + url + " host=" + host);
        logSnapshot("cloud:" + action + " -> " + host);
    }

    /** 工控 TCP 连接前调用。 */
    public static void logBeforeDeviceTcp(String tag, String host, int port) {
        Log.i(TAG, "[" + tag + "] device TCP target " + host + ":" + port);
        logSnapshot("device_tcp:" + host);
    }

    /** Socket 已连接后，看实际从哪个本地 IP 出去。 */
    public static void logSocketBound(String tag, InetAddress local, InetAddress remote) {
        Log.i(TAG, "[" + tag + "] socket local=" + formatAddr(local)
                + " remote=" + formatAddr(remote)
                + " (local 在 192.168.2.x 说明走了有线)");
    }

    private static void logNetwork(ConnectivityManager cm, @Nullable Network network, String label) {
        if (network == null) {
            Log.i(TAG, "[" + label + "] network=null");
            return;
        }
        NetworkCapabilities caps = cm.getNetworkCapabilities(network);
        LinkProperties link = cm.getLinkProperties(network);
        String transports = describeTransports(caps);
        String iface = link != null ? link.getInterfaceName() : "?";
        Log.i(TAG, "[" + label + "] iface=" + iface + " transports=" + transports
                + " validated=" + (caps != null && caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
                || caps != null && caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)));

        if (link == null) {
            return;
        }
        List<LinkAddress> addrs = link.getLinkAddresses();
        if (addrs != null) {
            for (LinkAddress la : addrs) {
                Log.i(TAG, "  addr " + la.getAddress().getHostAddress()
                        + "/" + la.getPrefixLength());
            }
        }
        List<RouteInfo> routes = link.getRoutes();
        if (routes != null) {
            for (RouteInfo route : routes) {
                if (route.isDefaultRoute()) {
                    Log.i(TAG, "  DEFAULT_ROUTE gateway="
                            + formatAddr(route.getGateway()) + " iface=" + route.getInterface());
                }
            }
            for (RouteInfo route : routes) {
                if (!route.isDefaultRoute()) {
                    Log.i(TAG, "  route dest=" + route.getDestination()
                            + " gateway=" + formatAddr(route.getGateway()));
                }
            }
        }
        List<InetAddress> dns = link.getDnsServers();
        if (dns != null && !dns.isEmpty()) {
            StringBuilder sb = new StringBuilder();
            for (InetAddress d : dns) {
                if (sb.length() > 0) {
                    sb.append(", ");
                }
                sb.append(d.getHostAddress());
            }
            Log.i(TAG, "  dns " + sb);
        }
    }

    private static String describeTransports(@Nullable NetworkCapabilities caps) {
        if (caps == null) {
            return "?";
        }
        StringBuilder sb = new StringBuilder();
        if (caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)) {
            sb.append("WIFI ");
        }
        if (caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET)) {
            sb.append("ETHERNET ");
        }
        if (caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR)) {
            sb.append("CELLULAR ");
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
                && caps.hasTransport(NetworkCapabilities.TRANSPORT_VPN)) {
            sb.append("VPN ");
        }
        if (sb.length() == 0) {
            sb.append("UNKNOWN");
        }
        return sb.toString().trim();
    }

    private static String parseHost(String url) {
        try {
            URI uri = URI.create(url);
            return uri.getHost() != null ? uri.getHost() : url;
        } catch (Exception e) {
            return url;
        }
    }

    private static String formatAddr(@Nullable InetAddress addr) {
        return addr == null ? "null" : addr.getHostAddress();
    }
}
