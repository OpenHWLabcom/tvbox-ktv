#!/usr/bin/env python3
# -*- coding: utf-8 -*-
# KTV 在线点歌后端 (网易云音乐) —— 供 com.openhardwarelab.ktv 用。
# 纯标准库, 监听 127.0.0.1:8089。接口:
#   GET /search?q=<kw>&n=24  -> JSON {"songs":[{id,name,artist,dur,playable}]}
#   GET /hot?n=40            -> JSON 热歌榜(同上结构)  (进在线模式的默认内容)
#   GET /lrc?id=<n>          -> text/plain 歌词(lrc)
#   GET /play?id=<n>         -> 302 跳转到可直接播放的音频直链 (不可播返回 404)
#   GET /ping                -> ok
# 放在盒子 chroot /root/ktvnet.py; 由 /data/startlinux.sh 拉起 /root/ktvnet-watch.sh 守护。

import json, ssl, socket, urllib.parse, urllib.request
import http.server, socketserver
from concurrent.futures import ThreadPoolExecutor

PORT = 8089
UA = 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36'
REF = 'https://music.163.com/'
HOT_PLAYLIST = 3778678   # 网易云「热歌榜」
socket.setdefaulttimeout(12)
CTX = ssl.create_default_context()
CTX.check_hostname = False
CTX.verify_mode = ssl.CERT_NONE


def _req(url, method='GET'):
    r = urllib.request.Request(url, headers={'User-Agent': UA, 'Referer': REF}, method=method)
    return urllib.request.urlopen(r, context=CTX)


def _songs_from_tracks(tracks, n):
    out = []
    for t in tracks[:n]:
        arts = t.get('artists') or []
        out.append({
            'id': t.get('id'),
            'name': t.get('name'),
            'artist': '/'.join((a.get('name') or '') for a in arts),
            'dur': t.get('duration', 0),
        })
    return out


def _probe(items):
    def probe(it):
        try:
            r = _req('https://music.163.com/song/media/outer/url?id=%d.mp3' % it['id'], 'HEAD')
            it['playable'] = ('audio' in (r.headers.get('Content-Type') or ''))
            r.close()
        except Exception:
            it['playable'] = False
        return it
    with ThreadPoolExecutor(max_workers=6) as ex:
        return list(ex.map(probe, items))


def search(kw, n=24):
    u = ('https://music.163.com/api/search/get/web?csrf_token=&type=1&offset=0&limit=%d&s=%s'
         % (n, urllib.parse.quote(kw)))
    d = json.loads(_req(u).read().decode('utf-8', 'replace'))
    songs = (d.get('result') or {}).get('songs') or []
    return _probe(_songs_from_tracks(songs, n))


def hot(n=40):
    u = 'https://music.163.com/api/playlist/detail?id=%d' % HOT_PLAYLIST
    d = json.loads(_req(u).read().decode('utf-8', 'replace'))
    tracks = ((d.get('result') or {}).get('tracks')) or []
    return _probe(_songs_from_tracks(tracks, n))


def resolve(pid):
    try:
        r = _req('https://music.163.com/song/media/outer/url?id=%d.mp3' % pid)
        ct = r.headers.get('Content-Type') or ''
        final = r.geturl()
        r.close()
        if 'audio' in ct:
            return final
    except Exception:
        pass
    return None


def lyric(pid):
    u = 'https://music.163.com/api/song/lyric?os=pc&id=%d&lv=1&kv=1&tv=-1' % pid
    d = json.loads(_req(u).read().decode('utf-8', 'replace'))
    return (d.get('lrc') or {}).get('lyric') or ''


class H(http.server.BaseHTTPRequestHandler):
    protocol_version = 'HTTP/1.1'

    def log_message(self, *a):
        pass

    def _send(self, code, ctype, body):
        if isinstance(body, str):
            body = body.encode('utf-8')
        self.send_response(code)
        self.send_header('Content-Type', ctype)
        self.send_header('Content-Length', str(len(body)))
        self.send_header('Cache-Control', 'no-store')
        self.end_headers()
        try:
            self.wfile.write(body)
        except Exception:
            pass

    def do_GET(self):
        p = urllib.parse.urlparse(self.path)
        qs = urllib.parse.parse_qs(p.query)
        try:
            if p.path == '/ping':
                return self._send(200, 'text/plain', 'ok')
            if p.path == '/search':
                kw = (qs.get('q') or [''])[0].strip()
                n = int((qs.get('n') or ['24'])[0])
                if not kw:
                    return self._send(200, 'application/json', '{"songs":[]}')
                return self._send(200, 'application/json',
                                  json.dumps({'songs': search(kw, n)}, ensure_ascii=False))
            if p.path == '/hot':
                n = int((qs.get('n') or ['40'])[0])
                return self._send(200, 'application/json',
                                  json.dumps({'songs': hot(n)}, ensure_ascii=False))
            if p.path == '/lrc':
                pid = int((qs.get('id') or ['0'])[0])
                return self._send(200, 'text/plain; charset=utf-8', lyric(pid))
            if p.path == '/play':
                pid = int((qs.get('id') or ['0'])[0])
                url = resolve(pid)
                if not url:
                    return self._send(404, 'text/plain', 'not playable')
                self.send_response(302)
                self.send_header('Location', url)
                self.send_header('Content-Length', '0')
                self.end_headers()
                return
            return self._send(404, 'text/plain', 'not found')
        except Exception as e:
            try:
                return self._send(500, 'text/plain', 'err: %r' % e)
            except Exception:
                return


class Server(socketserver.ThreadingMixIn, http.server.HTTPServer):
    daemon_threads = True
    allow_reuse_address = True


if __name__ == '__main__':
    srv = Server(('127.0.0.1', PORT), H)
    print('ktvnet listening on :%d' % PORT, flush=True)
    srv.serve_forever()
