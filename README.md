# 微信启用Google相册相片 (WeChat Google Photos Picker)

> **Credit:** Grok、Gemini ｜ **Idea:** Jacky

LSPosed / Xposed 模組（套件名稱：`io.github.Jackyhu32.wechat.picker`）。讓陸版手機上的微信（`com.tencent.mm`）在開啟相簿選圖、傳影片或掃一掃選圖時，**直接使用 Google 相簿（Google Photos）選圖畫面**，繞過陸版 HyperOS 系統相片選擇器封鎖 Google 相簿雲端媒體的限制。

本模組**不修改系統白名單**、**不重裝國際版系統相簿**、**不讀取聊天紀錄、帳號憑證或付款資訊**。

---

## 目錄
- [為什麼需要這個模組](#為什麼需要這個模組)
- [核心功能（v1.3.2）](#核心功能v132)
- [環境需求](#環境需求)
- [詳細安裝與使用步驟](#詳細安裝與使用步驟)
- [模組設定說明與診斷日誌](#模組設定說明與診斷日誌)
- [已知限制與注意事項](#已知限制與注意事項)
- [自行建置](#自行建置)
- [English Guide](#english-guide)

---

## 為什麼需要這個模組

大陸版 HyperOS（如小米 / Redmi）的系統設定會持續把雲端相片提供者白名單重寫為：

```text
mediaprovider/allowed_cloud_providers=com.miui.gallery
```

導致系統內建 Photo Picker（`com.android.photopicker/.hyper.HyperMainActivity`）無法顯示 Google 相簿的雲端相片與影片。本模組改為在微信開啟相簿時，直接啟動 Google 相簿自身的外部選圖元件：

```text
com.google.android.apps.photos/.picker.external.ExternalPickerActivity
```

選完後將雲端或本機媒體複製／對接到微信可讀取的路徑，再依微信原生協議交回預覽頁或掃一掃。

---

## 核心功能（v1.3.2）

1. **攔截與浮動入口雙重支援**：
   - 支援攔截 `AlbumPreviewUI`（含透過 DexKit 自動辨識改名後的相簿類）。
   - 當微信（如 8.0.79 / 8.0.80）以 `startActivity`（`rc=-1`）開啟相簿時，自動在相簿底部顯示藍色「**Google 相簿**」浮動按鈕。
2. **聊天圖片與影片完整預覽（不自動發送）**：
   - 選完圖片或影片後，自動提取影片 JPEG 縮圖、時長、方向修正寬高與 MIME，並寫入微信 `GalleryCore` 與 `preview_media_item_list`，正確顯示影片預覽而非黑屏圖片。
   - 停留在微信 `ImagePreviewUI` 供你預覽、裁切、標註或勾選原圖，**不會自動點擊發送**。
3. **微信掃一掃直接交回解碼**：
   - 從「掃一掃 → 相簿」選取 Google 相簿裡的二維碼圖片後，會直接設定相容微信 `AsyncObtainImage` 的 `Intent.setData` 並回傳給 `BaseScanUI` 解碼，不再誤跳聊天預覽或報錯。
4. **自動格式處理與防殘留**：
   - 雲端媒體會複製到微信私有 `syspicker/` 目錄（三天前的暫存檔自動清理）；HEIC/HEIF 自動轉為 JPEG；GIF / PNG / WebP 維持原檔；單檔上限約 1.5 GB。
5. **內建背景診斷日誌**：
   - 設定頁內建「開啟背景自動記錄日誌」開關，不必接電腦抓 `adb logcat`，即可一鍵複製或分享除錯日誌。

---

## 環境需求

- 已取得 Root 權限（Magisk / KernelSU / APatch）並安裝 **LSPosed**（建議 Zygisk 版本或 Vector，最低支援 `xposedminversion` 93）。
- 已安裝 **微信**（已於 8.0.79 / 8.0.80 實機驗證）。
- 已安裝並登入 **Google 相簿（Google Photos）**，且已開啟相簿備份。

---

## 詳細安裝與使用步驟

### 步驟 1：安裝模組 APK
1. 前往 [Releases](https://github.com/Jackyhu32/wechat-google-photos-picker/releases)（或 [Xposed Modules Repo](https://github.com/Xposed-Modules-Repo/io.github.Jackyhu32.wechat.picker/releases)）下載最新 `WeChatSysPicker-debug.apk`。
2. 在手機上安裝 APK（若曾安裝舊套件名稱 `com.androidopenbot.wechat.picker`，請先在 LSPosed 停用並解除安裝舊版）。

### 步驟 2：在 LSPosed 啟用模組與設定作用域
1. 打開 **LSPosed 管理器**（或 Vector）→ 進入「**模組**」頁面。
2. 找到並啟用「**微信系統相簿**」。
3. 在作用域（Scope）清單中，**只勾選微信 `com.tencent.mm`**。
   - **切勿勾選**系統框架、Google 相簿、`com.android.photopicker` 或其他任何 App。
4. **強制停止微信**（系統設定 → 應用程式 → 微信 → 強制停止），讓模組重新注入微信行程。

### 步驟 3：檢查模組設定頁狀態
1. 打開桌面上的「**微信系統相簿**」App。
2. 確認頂部狀態橫幅顯示「**LSPosed 已接管設定檔**」（若顯示尚未接管，請確認步驟 2 已勾選 `com.tencent.mm` 並重開此設定頁）。

### 步驟 4：在微信中使用
- **聊天 / 朋友圈傳圖與影片**：
  1. 在微信聊天視窗點「相簿」。
  2. 點擊底部藍色「**Google 相簿**」浮動按鈕（若該入口支援直接跳轉則會自動開啟 Google 相簿）。
  3. 在 Google 相簿選取一張或多張圖片／影片後按完成。
  4. 等待匯入提示完成後，畫面會停在微信預覽頁，確認無誤後自行點擊「發送」。
- **掃一掃辨識 Google 相簿二維碼**：
  1. 打開微信「掃一掃」→ 點右下角相簿圖示 → 點「**Google 相簿**」。
  2. 選取二維碼圖片後，會自動返回掃一掃畫面並完成解碼。

---

## 模組設定說明與診斷日誌

打開「微信系統相簿」App 可調整以下選項：
- **打開相簿時直接用 Google 相簿**：支援 `startActivityForResult` 的相簿入口會直接改開 Google 相簿。
- **仍進入微信相簿時顯示「Google 相簿」**：在相簿底部保留藍色浮動按鈕（微信 8.0.79+ 主要透過此按鈕觸發）。
- **回傳時標記為原圖**：強制附加 `send_raw_img=true` 並關閉壓縮標記。
- **診斷日誌（v1.3.2 新增）**：開啟「**開啟背景自動記錄日誌**」後，回到微信重現一次操作，再切回模組 App 即可預覽紀錄，並可點「**複製日誌**」或「**分享日誌**」用於回報問題。

---

## 已知限制與注意事項

- 若微信改版大幅更動 `AlbumPreviewUI` / `ImagePreviewUI` 結構，可開啟「診斷日誌」回報日誌以便更新。
- 朋友圈「發影片」若走內建拍攝頁而非相簿 Activity，不會被替換為 Google 相簿；部分朋友圈影片流程需額外 `KMMSightExtInfo` 物件，主要適用情境為聊天傳圖／影片與掃一掃。
- 大容量雲端影片需要先等 Google 相簿下載串流並複製進微信目錄，過程會顯示「正在匯入」遮罩。

---

## 自行建置

需要 JDK 17+ 與 Android SDK 35：

```bash
echo "sdk.dir=$ANDROID_HOME" > local.properties
./gradlew testDebugUnitTest assembleDebug
```

編譯產物位於 `app/build/outputs/apk/debug/app-debug.apk`。

---

## English Guide

> **Credit:** Grok, Gemini | **Idea:** Jacky

**WeChat Google Photos Picker** (`io.github.Jackyhu32.wechat.picker`) is an LSPosed / Xposed module scoped exclusively to WeChat (`com.tencent.mm`). On Mainland China HyperOS devices where `mediaprovider/allowed_cloud_providers` is locked to `com.miui.gallery`, this module lets you pick cloud photos and videos directly from **Google Photos** (`com.google.android.apps.photos/.picker.external.ExternalPickerActivity`) inside WeChat—without modifying system whitelists or reading chats/credentials.

### Features (v1.3.2)
- **Direct Picker & Floating Entry**: Hooks `AlbumPreviewUI` (with DexKit fallback for obfuscated gallery classes) and injects a floating「Google 相簿」button when WeChat opens the album with `rc=-1`.
- **Full Video & Image Preview**: Extracts JPEG thumbnails, duration, orientation-corrected dimensions, and MIME types for picked videos, seeds WeChat's `GalleryCore` and `preview_media_item_list`, and lands on `ImagePreviewUI` without auto-sending.
- **WeChat Scan (QR Code) Support**: Detects Scan/OCR callers and populates `Intent.setData` for WeChat's `AsyncObtainImage` so QR codes from Google Photos decode directly.
- **Format Handling**: Converts HEIC/HEIF to JPEG; keeps GIF/PNG/WebP intact; copies cloud streams to WeChat's `syspicker/` cache (auto-cleans files older than 3 days).
- **Built-in Log Recorder**: Toggle background diagnostic logging right inside the module app to copy or share logs without ADB.

### Installation & Setup
1. Install `WeChatSysPicker-debug.apk` from [Releases](https://github.com/Jackyhu32/wechat-google-photos-picker/releases).
2. Open **LSPosed / Vector** → **Modules** → enable **微信系統相簿** → select **only `com.tencent.mm`** in Scope (do **not** check System Framework, Google Photos, or `com.android.photopicker`).
3. **Force stop WeChat** from Android Settings.
4. Open the **微信系統相簿** app to verify `LSPosed 已接管設定檔` and customize toggles if needed.
5. In WeChat, open the album (in chat or Scan) and tap the blue「Google 相簿」button to pick photos or videos.

### Build from Source
```bash
echo "sdk.dir=$ANDROID_HOME" > local.properties
./gradlew testDebugUnitTest assembleDebug
```
