package com.openhardwarelab.ktv;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.URLDecoder;
import java.util.Collections;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Map;

/** App 内置的「手机扫码点歌」HTTP 服务(默认 :8081)。 */
public class KtvServer {

    public interface H {
        String localJson();                 // [{k,t}]
        String searchJson(String q);        // [{id,n,a,t}]
        String hotJson();                   // 热歌榜 [{id,n,a,t}]
        String addLocal(String key);        // 返回提示文本
        String addNet(String id, String n, String a);
        String nowJson();                   // {cur,n}
    }

    private static final String PAGE =
        "<!doctype html><html><head><meta charset='utf-8'><meta name='viewport' content='width=device-width,initial-scale=1'>" +
        "<title>KTV 点歌</title><style>" +
        "body{font-family:sans-serif;margin:0;padding:12px 12px 58px;background:#0f1220;color:#eef}" +
        "h3{margin:4px 0}" +
        "#q{width:100%;box-sizing:border-box;padding:12px;font-size:16px;border-radius:8px;border:1px solid #45506e;background:#1b2033;color:#eef}" +
        ".bar{display:flex;gap:8px;margin:10px 0}" +
        ".bar button{flex:1;padding:11px;font-size:15px;border:0;border-radius:8px;background:#2a3350;color:#cfd8f0}" +
        ".bar button.on{background:#0aa88a;color:#fff}" +
        ".row{display:flex;align-items:center;padding:11px 4px;border-bottom:1px solid #222c44}" +
        ".row .t{flex:1;padding-right:8px}" +
        ".row button{padding:9px 16px;border:0;border-radius:8px;background:#c43c5c;color:#fff;font-size:15px}" +
        "#now{position:fixed;left:0;right:0;bottom:0;padding:11px 12px;background:#0aa88a;color:#fff;font-size:13px}" +
        "</style></head><body>" +
        "<h3>&#127908; KTV 点歌</h3>" +
        "<div class='bar'><button id='bl' onclick='showLocal()'>&#26412;&#22320;&#26354;&#24211;</button>" +
        "<button id='bo' onclick='showOnline()'>&#22312;&#32447;&#28857;&#27468;</button></div>" +
        "<input id='q' placeholder='&#25628;&#27468;&#21517;/&#27468;&#25163;&#65288;&#22312;&#32447;&#65289;' oninput='onInput()'>" +
        "<div id='list'></div><div id='now'>&#8212;</div><script>" +
        "var mode='online',tm=null;" +
        "function onInput(){mode='online';setTab();clearTimeout(tm);tm=setTimeout(search,400)}" +
        "function search(){var q=document.getElementById('q').value;fetch('/api/search?q='+encodeURIComponent(q)).then(function(r){return r.json()}).then(function(d){render(d,'online')}).catch(function(){document.getElementById('list').innerHTML='<div class=row>load fail</div>'})}" +
        "function showLocal(){mode='local';setTab();document.getElementById('q').value='';fetch('/api/local').then(function(r){return r.json()}).then(function(d){render(d,'local')}).catch(function(){})}" +
        "function showOnline(){mode='online';setTab();var q=document.getElementById('q').value;if(!q){fetch('/api/hot').then(function(r){return r.json()}).then(function(d){render(d,'online')}).catch(function(){})}else{search()}}" +
        "function setTab(){document.getElementById('bl').className=(mode=='local'?'on':'');document.getElementById('bo').className=(mode=='online'?'on':'')}" +
        "function render(d,t){var L=document.getElementById('list');L.innerHTML='';if(!d.length){L.innerHTML='<div class=row>(no result)</div>';return}for(var i=0;i<d.length;i++){var s=d[i];var row=document.createElement('div');row.className='row';var sp=document.createElement('div');sp.className='t';sp.textContent=s.t;row.appendChild(sp);var b=document.createElement('button');b.textContent='\\u70b9\\u6b4c';b.setAttribute('data-k',s.k||'');b.setAttribute('data-i',s.id||'');b.setAttribute('data-n',s.n||'');b.setAttribute('data-a',s.a||'');b.setAttribute('data-t',t);b.onclick=function(){add(this)};row.appendChild(b);L.appendChild(row)}}" +
        "function add(b){var u;if(b.getAttribute('data-t')=='local'){u='/api/add?k='+encodeURIComponent(b.getAttribute('data-k'))}else{u='/api/add?id='+b.getAttribute('data-i')+'&n='+encodeURIComponent(b.getAttribute('data-n'))+'&a='+encodeURIComponent(b.getAttribute('data-a'))}fetch(u).then(function(r){return r.text()}).then(function(x){document.getElementById('now').textContent='OK '+x})}" +
        "function poll(){fetch('/api/now').then(function(r){return r.json()}).then(function(d){document.getElementById('now').textContent='\\u25b6 '+d.cur+'    '+d.n+' \\u9996\\u5df2\\u70b9'}).catch(function(){})}" +
        "setInterval(poll,5000);poll();showOnline();" +
        "</script></body></html>";

    private final H h;
    private final int port;
    private volatile boolean run = false;
    private ServerSocket ss;

    public KtvServer(H handler, int port) { this.h = handler; this.port = port; }

    public void start() {
        if (run) return;
        run = true;
        new Thread(new Runnable() {
            public void run() {
                try {
                    ss = new ServerSocket(port);
                    ss.setReuseAddress(true);
                    while (run) {
                        Socket s = ss.accept();
                        new Thread(new Conn(s)).start();
                    }
                } catch (Exception e) {}
            }
        }).start();
    }

    public void stop() {
        run = false;
        try { if (ss != null) ss.close(); } catch (Exception e) {}
    }

    private class Conn implements Runnable {
        private final Socket s;
        Conn(Socket x) { s = x; }
        public void run() {
            try { handle(s); } catch (Exception e) {} finally { try { s.close(); } catch (Exception e) {} }
        }
    }

    private void handle(Socket s) throws Exception {
        s.setTcpNoDelay(true);
        BufferedReader br = new BufferedReader(new InputStreamReader(s.getInputStream(), "UTF-8"));
        String line = br.readLine();
        if (line == null) return;
        String[] parts = line.split(" ");
        String path = parts.length > 1 ? parts[1] : "/";
        String q = "";
        int qi = path.indexOf('?');
        if (qi >= 0) { q = path.substring(qi + 1); path = path.substring(0, qi); }
        Map<String, String> pm = parse(q);

        if (path.equals("/") || path.equals("/index.html")) { send(s, 200, "text/html; charset=utf-8", PAGE); return; }
        if (path.equals("/api/local")) { send(s, 200, "application/json", h.localJson()); return; }
        if (path.equals("/api/search")) { send(s, 200, "application/json", h.searchJson(pm.get("q"))); return; }
        if (path.equals("/api/hot")) { send(s, 200, "application/json", h.hotJson()); return; }
        if (path.equals("/api/now")) { send(s, 200, "application/json", h.nowJson()); return; }
        if (path.equals("/api/add")) {
            String key = pm.get("k");
            String txt;
            if (key != null && key.length() > 0) txt = h.addLocal(key);
            else txt = h.addNet(pm.get("id"), pm.get("n"), pm.get("a"));
            send(s, 200, "text/plain; charset=utf-8", txt == null ? "fail" : txt);
            return;
        }
        send(s, 404, "text/plain", "not found");
    }

    private void send(Socket s, int code, String type, String body) {
        try {
            if (body == null) body = "";
            byte[] b = body.getBytes("UTF-8");
            StringBuilder sb = new StringBuilder();
            sb.append("HTTP/1.1 ").append(code).append(" OK\r\n");
            sb.append("Content-Type: ").append(type).append("\r\n");
            sb.append("Content-Length: ").append(b.length).append("\r\n");
            sb.append("Cache-Control: no-store\r\n");
            sb.append("Connection: close\r\n\r\n");
            OutputStream os = s.getOutputStream();
            os.write(sb.toString().getBytes("UTF-8"));
            os.write(b);
            os.flush();
        } catch (Exception e) {}
    }

    private static Map<String, String> parse(String qs) {
        Map<String, String> m = new HashMap<String, String>();
        if (qs == null || qs.length() == 0) return m;
        String[] ps = qs.split("&");
        for (int i = 0; i < ps.length; i++) {
            int e = ps[i].indexOf('=');
            try {
                if (e < 0) m.put(dec(ps[i]), "");
                else m.put(dec(ps[i].substring(0, e)), dec(ps[i].substring(e + 1)));
            } catch (Exception ex) {}
        }
        return m;
    }

    private static String dec(String s) throws Exception {
        return URLDecoder.decode(s, "UTF-8");
    }

    /** 本机局域网 IPv4。 */
    public static String localIp() {
        try {
            Enumeration<NetworkInterface> nis = NetworkInterface.getNetworkInterfaces();
            for (NetworkInterface ni : Collections.list(nis)) {
                if (ni.isLoopback() || !ni.isUp()) continue;
                for (InetAddress a : Collections.list(ni.getInetAddresses())) {
                    if (a instanceof Inet4Address && a.isSiteLocalAddress()) return a.getHostAddress();
                }
            }
        } catch (Exception e) {}
        return "127.0.0.1";
    }
}
