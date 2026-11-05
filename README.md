# 微信系統相簿

LSPosed / Xposed 模組。微信（`com.tencent.mm`）打開內建相簿時，**直接開啟 Google 相簿的選圖畫面**，而不是 HyperOS 系統相片選擇器。選完之後，模組仍把 `content://` 複製到微信自己的目錄，再用微信原本就在讀的 Intent extra 把路徑交回去。不做登入、不讀聊天、不改付款。

目標機是 Redmi K100 Pro Max（HyperOS，大陸版），微信 **8.0.79（versionCode 3182）**，已安裝 Google 相簿。

## 為什麼不走系統相片選擇器

大陸版 HyperOS 的 `device_config`：

`mediaprovider/allowed_cloud_providers=com.miui.gallery`

這份白名單會被 `com.miui.securitycenter` 改回去。系統 Photo Picker（`com.android.photopicker/.hyper.HyperMainActivity`）因此不會把 Google 相簿當成雲端媒體來源，選單裡也設不了。這個模組**不去改那份白名單**（不做 Magisk 覆寫），也**不改裝國際版相簿**。

改法是跳過系統選擇器，直接啟動 Google 相簿自己的外部選圖 Activity。這組 Intent 已在這支手機上用 `adb am start` 對過：

```text
adb shell am start \
  -a android.intent.action.GET_CONTENT \
  -t 'image/*' \
  --ez android.intent.extra.ALLOW_MULTIPLE true \
  -n com.google.android.apps.photos/.picker.external.ExternalPickerActivity
```

從微信裡選照片時，應出現 **Google 相簿的選圖畫面**，而不是 `HyperMainActivity`。

## 它做什麼

1. 攔截 `Instrumentation.execStartActivity`。目標元件是 `com.tencent.mm.plugin.gallery.ui.AlbumPreviewUI`（類名以 `AlbumPreviewUI` 結尾也算），或 DexKit 在 `com.tencent.mm.plugin.gallery` 裡找到、同時引用 `max_select_count` 與 `CropImage_OutputPath_List` 的相簿類。這條攔截和備用按鈕走同一條啟動，不是只有按鈕才開 Google 相簿。
2. 優先開啟  
   `com.google.android.apps.photos/com.google.android.apps.photos.picker.external.ExternalPickerActivity`  
   - Action：`ACTION_GET_CONTENT`  
   - 類型依原本的 `query_media_type`：`1` 為 `image/*`、`2` 為 `video/*`、`3`（或沒帶類型的多選）為 `*/*`，並附上 `EXTRA_MIME_TYPES` = `image/*`、`video/*`。沒帶類型的單選（大頭貼、裁切）只開圖片。  
   - `max_select_count` 大於 1 時帶 `EXTRA_ALLOW_MULTIPLE`。沒寫上限時預設 9，模組最多帶回 100 個。  
   `query_source_type` 是業務來源，不是媒體種類，故意不拿來過濾。
3. 只有這個元件真的啟動失敗（找不到 Activity 或被系統拒絕）時，才退回 `ACTION_PICK_IMAGES`。那條舊路徑仍會留在 `com.android.photopicker`（`HyperMainActivity`）。微信行程常常因為套件可見度而 `resolveActivity` 不到 Google 相簿；這種情況**不算**沒安裝，仍會直接啟動上面的元件。
4. 在 `Activity.dispatchActivityResult` 收到結果後，於背景把 URI 複製到  
   `/storage/emulated/0/Android/data/com.tencent.mm/files/syspicker/`  
   （外接儲存不可用時改到微信 cache）。已經在共用儲存上的檔案直接沿用路徑。HEIC/HEIF 會轉成 JPEG。超過約 1.5GB 的檔案會略過。匯入仍走原來的 `MediaImporter` 與 `WeChatResultContract`。
5. 把路徑放進結果 Intent，再把原本的 `dispatchActivityResult` 重播給呼叫端。圖片走 `CropImage_OutputPath_List`、`preview_image_list`、`CropImage_OutputPath`。影片走 `key_select_video_list`；只有一支影片、沒有圖片時另外帶 `K_SEGMENTVIDEOPATH`、`KSightPath`、`KSightThumbPath`、`sight_md5`。圖片加影片同時存在才帶 `key_select_mix_media_list`。同一份清單裡的重複路徑會去掉。
6. 若攔截沒吃到（類名改了，或這次啟動不是 `startActivityForResult`），微信相簿底部會多一顆「Google 相簿」。點下去一樣開 Google 相簿外部選圖；打不開才退回系統選擇器。選完直接 `setResult` 並關閉相簿。

`CropImage_Compress_Img` 預設為壓縮（微信原本的行為）。設定頁的「回傳時標記為原圖」會改送 `send_raw_img=true`。呼叫端若本來就帶了原圖標記，也會保留。`GalleryUI_ToUser` 這類字串會從開啟相簿的 Intent 抄回結果。

朋友圈影片有時還要 parcelable 的 `KMMSightExtInfo`。這個模組只填路徑、縮圖與 MD5，填不出那個物件。純圖片與聊天傳圖是主要路徑。

## 安裝

1. 手機已有 Magisk / KernelSU 與 LSPosed（Zygisk）。模組的 `xposedminversion` 是 93。
2. 安裝 debug APK：`dist/WeChatSysPicker-debug.apk`，或自行 `./gradlew assembleDebug`。
3. LSPosed → 模組 → 啟用「微信系統相簿」。作用域**只勾微信 `com.tencent.mm`**。不要勾系統框架、Google 相簿、`com.android.photopicker` 或其他套件。這個模組要跑在微信行程裡，勾多了沒有幫助。
4. 強制停止微信。不必重開手機，但 LSPosed 若提示重啟作用域，照它的做。
5. 打開「微信系統相簿」這個 App。頁首若寫 LSPosed 已接管設定檔，開關才會傳進微信。若仍寫尚未接管，先完成第 3 步再重開這個頁面，然後把開關再撥一次。預設是：取代內建相簿，並保留備用按鈕。
6. 手機上要已安裝並登入 Google 相簿。不需要、也無法在 Hyper 選擇器裡把雲端媒體改成 Google 相簿。

## 日誌

```text
adb logcat | grep WeChatSysPicker
```

LSPosed 管理器裡的模組日誌也有同樣的行，tag 是 `WeChatSysPicker`。成功時會看到 `replaced ...AlbumPreviewUI with Google Photos ExternalPicker`、`copied item`、`replayed album result`。若看到 `falling back to ACTION_PICK_IMAGES`，代表 Google 相簿那個元件沒能啟動，畫面才會是 Hyper。DexKit 掃到的類名會以 `dexkit gallery class` 開頭。

## 已知會壞的情況

- 微信改版後若不再用 `AlbumPreviewUI`，也不再讀上面的路徑 extra，而改吃內部 media id，這個模組就只剩備用按鈕，而且按鈕也要那個 Activity 還在。先看日誌再對類名。
- 朋友圈「發影片」若走拍攝頁而不是相簿 Activity，不會被換成 Google 相簿。
- 微信相簿裡的編輯、貼圖、拍攝入口會被跳過，因為整個畫面被 Google 相簿取代。
- 大檔影片複製時，聊天畫面會蓋一層「正在匯入」。雲端影片要等 Google 相簿把檔案交給我們才開始複製。
- HEIC 會轉成 JPEG。GIF / PNG / WebP 維持原檔。
- 單選大頭貼若選到影片，結果裡不會有圖片路徑，裁切頁可能沒反應。這種啟動預設只允許圖片。
- Google 相簿的多選沒有系統 `PICK_IMAGES_MAX` 那種硬上限。選超過 `max_select_count` 時，模組只把前面的項目交回微信。
- 三天前複製進 `syspicker` 的暫存檔會在下次匯入時刪掉。
- 模組更新後若微信已在背景，先強制停止微信再試。

## 建置

需要 JDK 17 或以上（本專案在 JDK 21 上編過）與 Android SDK 35。

```bash
# 已安裝 SDK 時
echo "sdk.dir=$ANDROID_HOME" > local.properties
./gradlew assembleDebug testDebugUnitTest
```

APK：`app/build/outputs/apk/debug/app-debug.apk`。倉庫裡的 `dist/WeChatSysPicker-debug.apk` 是同一份 debug 包，若有成功編過。

Xposed API 只做編譯期依賴，不會打進 APK：

- `app/libs/api-82.jar`
- 來源：<https://github.com/rovo89/XposedBridge/tree/gh-pages/de/robv/android/xposed/api/82>（Apache-2.0）
- 若 jar 遺失，可從該路徑的 `api-82.jar` 放回 `app/libs/`。

DexKit `org.luckypray:dexkit:2.3.0`（LGPL-3.0）用來在微信 APK 裡找改名後的相簿類，失敗時仍會攔截字面上的 `AlbumPreviewUI`。只打包 `arm64-v8a` 與 `armeabi-v7a`。

## English

LSPosed module, scoped only to `com.tencent.mm`. When WeChat opens `AlbumPreviewUI` (or a DexKit match in the gallery package), the module launches Google Photos `ExternalPickerActivity` with `ACTION_GET_CONTENT` (`image/*` and/or `video/*` from `query_media_type`, `EXTRA_ALLOW_MULTIPLE` when multi-select). That is the primary path for both the album intercept and the floating「Google 相簿」button. `ACTION_PICK_IMAGES` / `HyperMainActivity` runs only if that component fails to start.

CN HyperOS rewrites `mediaprovider/allowed_cloud_providers` to `com.miui.gallery` via `com.miui.securitycenter`, so Google Photos cannot be the system picker's cloud provider. This module does not patch that whitelist and does not install an international gallery.

Picked `content://` URIs are still copied by `MediaImporter` and returned through `WeChatResultContract` (`CropImage_OutputPath_List`, `key_select_video_list`, and the single-video sight extras). LSPosed scope must stay **`com.tencent.mm` only**. Target phone: Redmi K100 Pro Max, WeChat **8.0.79 (versionCode 3182)**. Success looks like the Google Photos picker, not `HyperMainActivity`. The module does not read chats, credentials, or payments.
