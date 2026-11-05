# 微信系統相簿

LSPosed / Xposed 模組。微信（`com.tencent.mm`）打開內建相簿時，改開 Android 系統相片選擇器（`MediaStore.ACTION_PICK_IMAGES`）。Google 相簿裡還沒下載到手機的雲端照片會出現在清單中，用法接近 iPhone 選照片時看得到 iCloud。

選完之後，模組把 `content://` 轉成微信自己目錄裡的檔案，再用微信原本就在讀的 Intent extra 把路徑交回去。不做登入、不讀聊天、不改付款。

目標機是 Jacky 的 Redmi K100 Pro Max（HyperOS）。這支手機上的微信是 **8.0.79（versionCode 3182）**，已安裝 Google 相簿。`ACTION_PICK_IMAGES` 解析到 HyperOS 相片選擇器：

`com.android.photopicker/com.android.photopicker.hyper.HyperMainActivity`

（`dumpsys` 常寫成 `com.android.photopicker/.hyper.HyperMainActivity`。）

模組若發現預設處理器就是這個套件，會把 Intent 留在 `com.android.photopicker`，不會改指到別的相簿。Google 相簿是這個選擇器裡的雲端媒體應用程式，不是另一個要取代它的 Activity。選完照片能否被微信 8.0.79 收下，仍要以這支手機的實機結果為準；若沒送出，看 LSPosed 日誌裡的 `WeChatSysPicker`。

## 它做什麼

1. 攔截 `Instrumentation.execStartActivity`。目標元件是 `com.tencent.mm.plugin.gallery.ui.AlbumPreviewUI`（類名以 `AlbumPreviewUI` 結尾也算），或 DexKit 在 `com.tencent.mm.plugin.gallery` 裡找到、同時引用 `max_select_count` 與 `CropImage_OutputPath_List` 的相簿類。
2. 換成系統 Photo Picker，多選上限沿用原本的 `max_select_count`（沒寫時預設 9，最高 100，並受系統 `getPickImagesMaxLimit()` 限制）。`query_media_type` 為 1 只看圖片、2 只看影片、3 兩者都要。沒帶這個 extra 時，單選（大頭貼、裁切）只開圖片，多選則圖片加影片。`query_source_type` 是業務來源，不是媒體種類，故意不拿來過濾。
3. 在 `Activity.dispatchActivityResult` 收到選擇器結果後，於背景把 URI 複製到  
   `/storage/emulated/0/Android/data/com.tencent.mm/files/syspicker/`  
   （外接儲存不可用時改到微信 cache）。已經在共用儲存上的檔案直接沿用路徑。HEIC/HEIF 會轉成 JPEG。超過約 1.5GB 的檔案會略過。
4. 把路徑放進結果 Intent，再把原本的 `dispatchActivityResult` 重播給呼叫端。圖片走 `CropImage_OutputPath_List`、`preview_image_list`、`CropImage_OutputPath`。影片走 `key_select_video_list`；只有一支影片、沒有圖片時另外帶 `K_SEGMENTVIDEOPATH`、`KSightPath`、`KSightThumbPath`、`sight_md5`。圖片加影片同時存在才帶 `key_select_mix_media_list`，避免有的版本把同一張圖送兩次。同一份清單裡的重複路徑會去掉（微信去重後有陣列越界的舊問題）。
5. 若攔截沒吃到（類名改了，或這次啟動不是 `startActivityForResult`），微信相簿底部會多一顆「系統相簿」。點下去一樣開系統選擇器，選完直接 `setResult` 並關閉相簿。

`CropImage_Compress_Img` 預設為壓縮（微信原本的行為）。設定頁的「回傳時標記為原圖」會改送 `send_raw_img=true`。呼叫端若本來就帶了原圖標記，也會保留。`GalleryUI_ToUser` 這類字串會從開啟相簿的 Intent 抄回結果。

朋友圈影片有時還要 parcelable 的 `KMMSightExtInfo`。這個模組只填路徑、縮圖與 MD5，填不出那個物件。純圖片與聊天傳圖是主要路徑。

## 安裝

1. 手機已有 Magisk / KernelSU 與 LSPosed（Zygisk）。模組的 `xposedminversion` 是 93。
2. 安裝 debug APK：`dist/WeChatSysPicker-debug.apk`，或自行 `./gradlew assembleDebug`。
3. LSPosed → 模組 → 啟用「微信系統相簿」。作用域**只勾微信 `com.tencent.mm`**。不要勾系統框架、Google 相簿、`com.android.photopicker` 或其他套件。這個模組要跑在微信行程裡，勾多了沒有幫助。
4. 強制停止微信。不必重開手機，但 LSPosed 若提示重啟作用域，照它的做。
5. 打開「微信系統相簿」這個 App。頁首若寫 LSPosed 已接管設定檔，開關才會傳進微信。若仍寫尚未接管，先完成第 3 步再重開這個頁面，然後把開關再撥一次。預設是：取代內建相簿，並保留備用按鈕。

## 讓 Google 相簿的雲端項目出現

這支手機已經安裝 Google 相簿。系統選擇器不會自動列出雲端照片，還要把雲端媒體應用程式指到 Google 相簿：

1. 打開 Google 相簿並登入，開啟備份與同步。
2. 在微信裡打開選圖。應出現 HyperOS 相片選擇器（`HyperMainActivity`），而不是微信自己的相簿格。
3. 選擇器右上角選單（⋮ 或齒輪）→ **雲端媒體應用程式 / Cloud media app** → 選 **Google 相簿**。
4. 回到相簿分頁。雲端照片通常帶雲朵或下載標記；點選之後系統會先下載，模組再把檔案複製給微信。

若預設選擇器不是 `com.android.photopicker`，模組才會改試 `com.google.android.providers.media.module`，其次 `com.android.providers.media.module`。K100 Pro Max 上不需要這一步。Android 12 以下沒有 `ACTION_PICK_IMAGES` 時，會退回 `ACTION_GET_CONTENT`。

## 日誌

```text
adb logcat | grep WeChatSysPicker
```

LSPosed 管理器裡的模組日誌也有同樣的行，tag 是 `WeChatSysPicker`。成功時會看到 `replaced ...AlbumPreviewUI`、`copied item`、`replayed album result`。DexKit 掃到的類名會以 `dexkit gallery class` 開頭。

## 已知會壞的情況

- 微信改版後若不再用 `AlbumPreviewUI`，也不再讀上面的路徑 extra，而改吃內部 media id，這個模組就只剩備用按鈕，而且按鈕也要那個 Activity 還在。先看日誌再對類名。
- 朋友圈「發影片」若走拍攝頁而不是相簿 Activity，不會被換成系統選擇器。
- 微信相簿裡的編輯、貼圖、拍攝入口會被跳過，因為整個畫面被系統選擇器取代。
- 大檔影片複製時，聊天畫面會蓋一層「正在匯入」。雲端影片要等系統下完才開始複製。
- HEIC 會轉成 JPEG。GIF / PNG / WebP 維持原檔。
- 單選大頭貼若選到影片，結果裡不會有圖片路徑，裁切頁可能沒反應。這種啟動預設只允許圖片。
- 選擇器一次最多跟系統上限（通常 100，微信自己多半是 9）。
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

LSPosed module, scoped only to `com.tencent.mm`. When WeChat opens `AlbumPreviewUI` (or a DexKit match in the gallery package), the module launches the system photo picker (`ACTION_PICK_IMAGES`, multi-select, honoring `max_select_count` and `query_media_type`). Picked `content://` URIs are copied into WeChat's external files dir and returned with `CropImage_OutputPath_List`, `key_select_video_list`, and the single-video `K_SEGMENTVIDEOPATH` / `KSightPath` extras. A floating「系統相簿」button is injected if the stock album still opens.

Target phone: Redmi K100 Pro Max (HyperOS), WeChat **8.0.79 (versionCode 3182)**, Google Photos installed. `ACTION_PICK_IMAGES` resolves to `com.android.photopicker/.hyper.HyperMainActivity`. The module keeps that package instead of replacing it.

LSPosed scope must be **`com.tencent.mm` only**. Do not also scope the system framework or Google Photos. In the Hyper photo picker menu, set the cloud media app to Google Photos. Whether WeChat 8.0.79 accepts the returned paths still has to be confirmed on that phone; check logcat for `WeChatSysPicker`. The module does not read chats, credentials, or payments.
