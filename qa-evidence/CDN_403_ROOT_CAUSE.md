# media-b-shell 真机无法播放 — 根因报告

**时间 (Asia/Shanghai):** 2026-10-27 15:19  
**设备:** OPPO YDSW6XBU45ROEMP7 (CPH2481) Wi‑Fi CloudRelam-Wi-Fi_5G  
**出口 IP (Digsure 同网):** 见下方 curl ipify  
**安装包 MD5:** b62565b605da68be953d2fb4c4153ca2

## 根因（一句话）
CDN `bcdn.hakunaymatata.com` 对本网出口在已正确附带 outside/links 返回的 `Accept`+`User-Agent` 时仍返回 **HTTP 403**；客户端无缺头/漏键，探活失败导致 `no_alive_links`，ExoPlayer 未起播。

## 证据

### 1. API 返回的 headers（仅两键）
`GET https://stage.hdvideoplay.top/api/media/outside/links/?media_id=10000` → 200  
Line 9 唯一源：
- url: `https://bcdn.hakunaymatata.com/resource/b958e5628a48ddd2e9ee6902ab1fdc93.mp4?sign=...`
- headers: `accept: */*`, `user-agent: com.community.mbox.in.geobypass (Linux; Android)`  
（无 origin / referer）

### 2. 真机 PlaySourceProbe（包 b62565…）
```
okhttp --> GET .../outside/links/?media_id=10000  <-- 200
PlaySourceProbe: probe via=GET-after-Range-403 code=403 ok=false hdr=[Accept,User-Agent]
PlaySourceProbe: probe_H Accept=*/*
PlaySourceProbe: probe_H User-Agent=com.community.mbox.in.geobypass (Linux; Android)
```
→ 探活在 Range 403 后 plain GET 仍 403；随后 PlayerViewModel `no_alive_links`，不会进入 playSource/MediaCdnHttp。

### 3. 对照 curl（同网 Digsure / 外网 box）
- 带 API headers → **403**
- 不带 / 错 UA → 历史上 **428 ACCESS DENIED**（说明 headers 有校验，但对本 IP 正确头仍不够）

### 4. 客户端路径核对（无漏）
- DTO `PlaySourceDto.headers` → `CdnRequestHeaders.build` → `MediaCdnHeaders.set` + OkHttpDataSource `setDefaultRequestProperties` + interceptor 每跳重写
- Probe 独立 per-Request 带头，日志已打印 Accept/User-Agent 与 API 一致
- 非 HTTPS 证书问题（TLS 通，业务码 403）
- 非 Redirect 丢头（无 3xx；403 即终态）

## 给后端的诉求
1. 确认 `bcdn.hakunaymatata.com` 对出口 IP（同网 Digsure/手机）是否地理/IP 黑名单；白名单或换可播线路  
2. media_id=10000 目前只回 Line 9 一条；请提供本区域可 200/206 的备用线（或补 origin/referer 若 CDN 需要）  
3. 请用同出口复现：带上述 UA+Accept 的 Range GET 应得 206，现为 403

## 客户端侧
已出诊断包 MD5 `b62565b605da68be953d2fb4c4153ca2`（Desktop `media-b-shell-debug.apk`），真机已装。  
客户端 header 链路完整，**无法靠再补客户端头修复本 403**；需后端/CDN 侧处理。可选产品改动：探活失败时 soft-fail 仍尝试 Exo（对本案无实质帮助）。
