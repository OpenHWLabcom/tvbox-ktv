#!/usr/bin/env python3
# -*- coding: utf-8 -*-
# KTV 在线点歌后端（多音源：网易云 / 酷我 / 酷狗）—— 供 com.openhardwarelab.ktv 用。
# 纯标准库, 监听 127.0.0.1:8089。
#   GET /search?q=<kw>&n=40  -> JSON {"songs":[{id,src,name,artist,dur,playable}]}
#   GET /hot?n=80            -> JSON 热歌榜(网易云)
#   GET /lrc?src=&id=<n>     -> text/plain 歌词(仅网易云)
#   GET /play?src=&id=<x>    -> 302 音频直链（不可播 404）
#   GET /ping                -> ok

import ast, hashlib, html, json, re, socket, ssl, urllib.parse, urllib.request
import http.server

PORT = 8089
UA = 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36'
REF = 'https://music.163.com/'
HOT_PLAYLISTS = [3778678, 3779629, 19723756, 2884035]   # 热歌榜/新歌榜/飙升榜/原创榜

socket.setdefaulttimeout(10)
CTX = ssl.create_default_context()
CTX.check_hostname = False
CTX.verify_mode = ssl.CERT_NONE


def _req(url, method='GET', ref=None):
    h = {'User-Agent': UA}
    if ref:
        h['Referer'] = ref
    r = urllib.request.Request(url, headers=h, method=method)
    return urllib.request.urlopen(r, context=CTX)


def _clean(t):
    t = html.unescape(t or '')
    t = t.replace('\\u0026', '&').replace('\xa0', ' ')
    return re.sub(r'\s+', ' ', t).strip()


def _json(url, ref=None):
    return json.loads(_req(url, ref=ref).read().decode('utf-8', 'replace'))


# ---------------- 网易云 ----------------
def _ne_outer(pid):
    return 'https://music.163.com/song/media/outer/url?id=%s.mp3' % pid


def ne_search(kw, n=24):
    u = ('https://music.163.com/api/search/get/web?csrf_token=&type=1&offset=0&limit=%d&s=%s'
         % (n, urllib.parse.quote(kw)))
    d = _json(u, REF)
    out = []
    for t in ((d.get('result') or {}).get('songs') or [])[:n]:
        arts = t.get('artists') or []
        out.append({'id': str(t.get('id')), 'src': 'ne', 'name': _clean(t.get('name')),
                    'artist': _clean('/'.join((a.get('name') or '') for a in arts)),
                    'dur': t.get('duration', 0), 'playable': False})
    return out


def ne_hot(n=80):
    tracks = []
    for pid in HOT_PLAYLISTS:
        try:
            d = _json('https://music.163.com/api/playlist/detail?id=%d' % pid, REF)
            tracks += ((d.get('result') or {}).get('tracks')) or []
        except Exception:
            continue
    out = []
    for t in tracks[:max(n, 120)]:
        arts = t.get('artists') or []
        out.append({'id': str(t.get('id')), 'src': 'ne', 'name': _clean(t.get('name')),
                    'artist': _clean('/'.join((a.get('name') or '') for a in arts)),
                    'dur': t.get('duration', 0), 'playable': False})
    return out


def ne_probe(it):
    try:
        r = _req(_ne_outer(it['id']), 'HEAD', REF)
        it['playable'] = ('audio' in (r.headers.get('Content-Type') or ''))
        r.close()
    except Exception:
        it['playable'] = False
    return it


def ne_resolve(pid):
    try:
        r = _req(_ne_outer(pid), ref=REF)
        ct = r.headers.get('Content-Type') or ''
        final = r.geturl()
        r.close()
        if 'audio' in ct:
            return final
    except Exception:
        pass
    return None


def ne_lyric(pid):
    u = 'https://music.163.com/api/song/lyric?os=pc&id=%s&lv=1&kv=1&tv=-1' % pid
    d = _json(u, REF)
    return (d.get('lrc') or {}).get('lyric') or ''


# ---------------- 酷我 ----------------
def kw_search(kw, n=16):
    u = ('http://search.kuwo.cn/r.s?all=%s&ft=music&itemset=web_2013&client=kt&pn=0&rn=%d'
         '&rformat=json&encoding=utf8' % (urllib.parse.quote(kw), n))
    raw = _req(u).read().decode('utf-8', 'replace')
    try:
        d = ast.literal_eval(raw)
    except Exception:
        try:
            d = json.loads(raw.replace("'", '"'))
        except Exception:
            return []
    out = []
    for t in (d.get('abslist') or [])[:n]:
        rid = str(t.get('MUSICRID') or t.get('DC_TARGETID') or '').replace('MUSIC_', '')
        if not rid:
            continue
        out.append({'id': rid, 'src': 'kw', 'name': _clean(t.get('NAME') or t.get('SONGNAME')),
                    'artist': _clean(t.get('ARTIST')), 'dur': 0, 'playable': True})
    return out


def kw_resolve(rid):
    try:
        u = ('http://antiserver.kuwo.cn/anti.s?type=convert_url&format=mp3&response=url'
             '&rid=MUSIC_%s' % rid)
        txt = _req(u).read().decode('utf-8', 'replace').strip()
        if txt.startswith('http'):
            # 老 Android(4.4/OpenSSL) 握手不了现代 TLS：能走 http 就走 http
            if txt.startswith('https://'):
                h = 'http://' + txt[8:]
                try:
                    r = _req(h, 'HEAD')
                    if r.status == 200:
                        r.close()
                        return h
                except Exception:
                    pass
            return txt
    except Exception:
        pass
    return None


def kw_bang(bid, n=60):
    try:
        u = ('http://kbangserver.kuwo.cn/ksong.s?from=pc&fmt=json&pn=0&rn=%d&type=bang'
             '&data=content&id=%d&show_copyright_off=0&pcmp4=1&isbang=1' % (n, bid))
        d = _json(u)
    except Exception:
        return []
    out = []
    for t in (d.get('musiclist') or [])[:n]:
        rid = str(t.get('id') or '')
        if not rid:
            continue
        out.append({'id': rid, 'src': 'kw', 'name': _clean(t.get('name')),
                    'artist': _clean(t.get('artist')), 'dur': 0, 'playable': True})
    return out


# ---------------- 酷狗 ----------------
def kg_search(kw, n=12):
    u = ('http://mobilecdn.kugou.com/api/v3/search/song?format=json&keyword=%s&page=1&pagesize=%d'
         % (urllib.parse.quote(kw), n))
    try:
        d = _json(u)
    except Exception:
        return []
    out = []
    for t in ((d.get('data') or {}).get('info') or [])[:n]:
        h = t.get('hash')
        if not h:
            continue
        out.append({'id': h, 'src': 'kg', 'name': _clean(t.get('songname')),
                    'artist': _clean(t.get('singername')), 'dur': (t.get('duration') or 0) * 1000,
                    'playable': False, 'album': t.get('album_id') or ''})
    return out


def kg_rank(rankid=8888, n=40):
    try:
        d = _json('http://mobilecdn.kugou.com/api/v3/rank/song?rankid=%d&page=1&pagesize=%d&format=json'
                  % (rankid, n))
    except Exception:
        return []
    out = []
    for t in ((d.get('data') or {}).get('info') or [])[:n]:
        h = t.get('hash')
        if not h:
            continue
        nm = t.get('songname') or ''
        au = t.get('singername')
        if not au:
            a = t.get('authors') or []
            au = a[0].get('author_name') if a else ''
        out.append({'id': h, 'src': 'kg', 'name': _clean(nm), 'artist': _clean(au),
                    'dur': (t.get('duration') or 0) * 1000, 'playable': False})
    return out


def kg_resolve(h):
    key = hashlib.md5((h.lower() + 'kgcloudv2').encode()).hexdigest()
    for br in ('hq', 'p'):
        try:
            u = ('http://trackercdn.kugou.com/i/v2/?key=%s&hash=%s&br=%s&appid=1005&pid=2'
                 '&cmd=25&behavior=play' % (key, h.lower(), br))
            d = _json(u)
            url = d.get('url')
            if isinstance(url, list):
                url = url[0] if url else None
            if not url:
                urls = d.get('urls') or []
                url = urls[0] if urls else None
            if d.get('status') == 1 and url:
                return url
        except Exception:
            continue
    return None


def kg_probe(it):
    it['playable'] = bool(kg_resolve(it['id']))
    return it


# ---------------- 汇总 ----------------
def probe_all(items, fn, workers=8):
    if not items:
        return items
    from concurrent.futures import ThreadPoolExecutor
    with ThreadPoolExecutor(max_workers=workers) as ex:
        return list(ex.map(fn, items))


def dedupe(items):
    seen = set()
    out = []
    for it in items:
        k = (re.sub(r'\s+', '', it.get('name') or ''), re.sub(r'\s+', '', it.get('artist') or ''))
        if k in seen:
            continue
        seen.add(k)
        out.append(it)
    return out


def search_all(kw, n=40):
    ne = probe_all(ne_search(kw, min(n, 30)), ne_probe)
    ne = [x for x in ne if x['playable']]
    kg_ = probe_all(kg_search(kw, 12), kg_probe)
    kg_ = [x for x in kg_ if x['playable']]
    out = dedupe(ne + kg_)
    return out[:n]


def hot_all(n=80):
    items = probe_all(ne_hot(max(n, 120)), ne_probe)
    ne = [x for x in items if x['playable']]
    try:
        kgr = probe_all(kg_rank(8888, 80), kg_probe)
        kgr = [x for x in kgr if x['playable']]
    except Exception:
        kgr = []
    return dedupe(ne + kgr)[:n]


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
                n = int((qs.get('n') or ['40'])[0])
                if not kw:
                    return self._send(200, 'application/json', '{"songs":[]}')
                return self._send(200, 'application/json',
                                  json.dumps({'songs': search_all(kw, n)}, ensure_ascii=False))
            if p.path == '/hot':
                n = int((qs.get('n') or ['80'])[0])
                return self._send(200, 'application/json',
                                  json.dumps({'songs': hot_all(n)}, ensure_ascii=False))
            if p.path == '/lrc':
                src = (qs.get('src') or ['ne'])[0]
                pid = (qs.get('id') or ['0'])[0]
                if src != 'ne':
                    return self._send(200, 'text/plain; charset=utf-8', '')
                return self._send(200, 'text/plain; charset=utf-8', ne_lyric(pid))
            if p.path == '/play':
                src = (qs.get('src') or ['ne'])[0]
                pid = (qs.get('id') or [''])[0]
                if not pid:
                    return self._send(404, 'text/plain', 'bad id')
                url = None
                if src == 'kw':
                    url = kw_resolve(pid)
                elif src == 'kg':
                    url = kg_resolve(pid)
                else:
                    url = ne_resolve(pid)
                if not url:
                    return self._send(404, 'text/plain', 'not playable')
                self.send_response(302)
                self.send_header('Location', url)
                self.send_header('Content-Length', '0')
                self.end_headers()
                return
            return self._send(404, 'text/plain', 'not found')
        except Exception as e:
            return self._send(500, 'text/plain', 'err: %s' % e)


class S(http.server.ThreadingHTTPServer):
    daemon_threads = True


if __name__ == '__main__':
    print('ktvnet listening on :%d (multi-source)' % PORT, flush=True)
    S(('127.0.0.1', PORT), H).serve_forever()
