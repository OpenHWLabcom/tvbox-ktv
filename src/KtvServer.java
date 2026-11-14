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
        String addNet(String id, String n, String a, String src);
        String nowJson();                   // {cur,n}
        String queueJson();                 // [{i,t,cur}]
        String nextSong();                  // 切歌
        String jumpSong(String i);          // 播放第 i 首
        String delSong(String i);           // 删除第 i 首
    }

    private static final String PAGE =
        "<!doctype html><html><head><meta charset='utf-8'><meta name='viewport' content='width=device-width,initial-scale=1'>" +
        "<title>KTV 点歌</title><style>" +
        "body{font-family:-apple-system,sans-serif;margin:0;padding:12px 12px 64px;background:#0f1220;color:#eef}" +
        "h3{margin:4px 0}" +
        "#q{width:100%;box-sizing:border-box;padding:12px;font-size:16px;border-radius:8px;border:1px solid #45506e;background:#1b2033;color:#eef}" +
        ".bar{display:flex;gap:8px;margin:10px 0}" +
        ".bar button{flex:1;padding:11px;font-size:15px;border:0;border-radius:8px;background:#2a3350;color:#cfd8f0}" +
        ".bar button.on{background:#0aa88a;color:#fff}" +
        ".row{display:flex;align-items:center;padding:11px 4px;border-bottom:1px solid #222c44}" +
        ".row .t{flex:1;padding-right:8px;line-height:1.35}" +
        ".row button{padding:9px 14px;border:0;border-radius:8px;background:#c43c5c;color:#fff;font-size:15px;margin-left:6px}" +
        ".row button.p{background:#0aa88a}" +
        "#now{position:fixed;left:0;right:0;bottom:0;display:flex;align-items:center;gap:8px;padding:9px 12px;background:#0b3d36;color:#fff;font-size:13px}" +
        "#now .c{flex:1;overflow:hidden;text-overflow:ellipsis;white-space:nowrap}" +
        "#now button{border:0;border-radius:8px;padding:10px 14px;font-size:14px;background:#0aa88a;color:#fff}" +
        "#now button.g{background:#2a3350}" +
        "#panel{position:fixed;left:0;right:0;top:0;bottom:0;background:#0f1220;padding:12px;overflow:auto;display:none}" +
        "#panel h3{display:flex;justify-content:space-between;align-items:center}" +
        "#panel .x{background:#2a3350;border:0;color:#cfd8f0;border-radius:8px;padding:8px 14px;font-size:14px}" +
        ".cur{color:#27e0b8;font-weight:bold}" +
        "</style></head><body>" +
        "<h3>&#127908; KTV 点歌</h3>" +
        "<div class='bar'><button id='bl' onclick='showLocal()'>本地曲库</button>" +
        "<button id='bo' onclick='showOnline()'>在线点歌</button></div>" +
        "<input id='q' placeholder='搜歌名/歌手（在线）' oninput='onInput()'>" +
        "<div id='list'></div>" +
        "<div id='now'><div class='c' id='nowt'>—</div>" +
        "<button onclick='qNext()'>切歌</button>" +
        "<button class='g' onclick='toggleQueue()'>已点</button></div>" +
        "<div id='panel'><h3><span>已点列表</span><button class='x' onclick='toggleQueue()'>关闭</button></h3>" +
        "<div id='ql'></div></div>" +
        "<script>" +
        "var mode='online',tm=null,qOpen=false;" +
        "function el(id){return document.getElementById(id)}" +
        "function onInput(){mode='online';setTab();clearTimeout(tm);tm=setTimeout(search,400)}" +
        "function search(){var q=el('q').value;fetch('/api/search?q='+encodeURIComponent(q)).then(function(r){return r.json()}).then(function(d){render(d)}).catch(function(){el('list').innerHTML='<div class=row>load fail</div>'})}" +
        "function showLocal(){mode='local';setTab();el('q').value='';fetch('/api/local').then(function(r){return r.json()}).then(function(d){render(d)}).catch(function(){})}" +
        "function showOnline(){mode='online';setTab();var q=el('q').value;if(!q){fetch('/api/hot').then(function(r){return r.json()}).then(function(d){render(d)}).catch(function(){})}else{search()}}" +
        "function setTab(){el('bl').className=(mode=='local'?'on':'');el('bo').className=(mode=='online'?'on':'')}" +
        "function render(d){var L=el('list');L.innerHTML='';if(!d.length){L.innerHTML='<div class=row>(no result)</div>';return}for(var i=0;i<d.length;i++){var s=d[i];var row=document.createElement('div');row.className='row';var sp=document.createElement('div');sp.className='t';sp.textContent=s.t;row.appendChild(sp);var b=document.createElement('button');b.textContent='点歌';b.setAttribute('data-k',s.k||'');b.setAttribute('data-i',s.id||'');b.setAttribute('data-n',s.n||'');b.setAttribute('data-a',s.a||'');b.setAttribute('data-g',s.g||'');b.onclick=function(){add(this)};row.appendChild(b);L.appendChild(row)}}" +
        "function add(b){var u;if(b.getAttribute('data-k')){u='/api/add?k='+encodeURIComponent(b.getAttribute('data-k'))}else{u='/api/add?id='+encodeURIComponent(b.getAttribute('data-i'))+'&n='+encodeURIComponent(b.getAttribute('data-n'))+'&a='+encodeURIComponent(b.getAttribute('data-a'))+'&s='+encodeURIComponent(b.getAttribute('data-g'))}fetch(u).then(function(r){return r.text()}).then(function(x){el('nowt').textContent=x;poll()})}" +
        "function qNext(){fetch('/api/next').then(function(r){return r.text()}).then(function(){setTimeout(poll,600)})}" +
        "function qPlay(i){fetch('/api/jump?i='+i).then(function(){setTimeout(loadQueue,400)})}" +
        "function qDel(i){fetch('/api/del?i='+i).then(function(){setTimeout(loadQueue,300)})}" +
        "function loadQueue(){fetch('/api/queue').then(function(r){return r.json()}).then(function(d){var L=el('ql');L.innerHTML='';if(!d.length){L.innerHTML='<div class=row>还没点歌</div>';return}for(var i=0;i<d.length;i++){var it=d[i];var row=document.createElement('div');row.className='row';var sp=document.createElement('div');sp.className='t'+(it.cur?' cur':'');sp.textContent=(i+1)+'. '+(it.cur?'\u25b6 ':'')+it.t;row.appendChild(sp);var p=document.createElement('button');p.className='p';p.textContent='播放';p.onclick=(function(x){return function(){qPlay(x)}})(it.i);row.appendChild(p);var x=document.createElement('button');x.textContent='删除';x.onclick=(function(y){return function(){qDel(y)}})(it.i);row.appendChild(x);L.appendChild(row)}}).catch(function(){})}" +
        "function toggleQueue(){qOpen=!qOpen;el('panel').style.display=qOpen?'block':'none';if(qOpen)loadQueue()}" +
        "function poll(){fetch('/api/now').then(function(r){return r.json()}).then(function(d){el('nowt').textContent='\u25b6 '+d.cur+'    \u5df2\u70b9 '+d.n+' \u9996';if(qOpen)loadQueue()}).catch(function(){})}" +
        "setInterval(poll,4000);poll();showOnline();" +
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
        if (path.equals("/api/queue")) { send(s, 200, "application/json", h.queueJson()); return; }
        if (path.equals("/api/next")) { send(s, 200, "text/plain; charset=utf-8", h.nextSong()); return; }
        if (path.equals("/api/jump")) { send(s, 200, "text/plain; charset=utf-8", h.jumpSong(pm.get("i"))); return; }
        if (path.equals("/api/del")) { send(s, 200, "text/plain; charset=utf-8", h.delSong(pm.get("i"))); return; }
        if (path.equals("/api/add")) {
            String key = pm.get("k");
            String txt;
            if (key != null && key.length() > 0) txt = h.addLocal(key);
            else txt = h.addNet(pm.get("id"), pm.get("n"), pm.get("a"), pm.get("s"));
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
