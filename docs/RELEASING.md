# 發佈流程（Release）

本專案的發佈已完全自動化，由 `.github/workflows/release.yml` 執行。
**任何人（包含 AI agent）都只需要改版本號並 push 到 `main`，不需要 token、不需要 `gh`、不需要操作 Xposed 倉庫。**

## 你唯一要做的事

1. 編輯 `app/build.gradle`，把版本號往上加：

   ```gradle
   versionCode 8
   versionName "1.3.4"
   ```

   - `versionCode` 必須是整數且比上一版大
   - `versionName` 使用語意化版本（`x.y.z`）

2. Commit 並 push 到 `main`。

3. 等 3～5 分鐘。

## 之後會自動發生什麼

| 步驟 | 說明 |
|---|---|
| 1 | 讀取 `app/build.gradle` 的 `versionCode` / `versionName` |
| 2 | 檢查兩個倉庫是否都已有此版本；若都有則**整條流程跳過** |
| 3 | 執行 `./gradlew testDebugUnitTest assembleDebug`（測試失敗就中止，不會發佈） |
| 4 | 在 `Jackyhu32/wechat-google-photos-picker` 建立 Release `v{versionName}` |
| 5 | 把 `main` 分支同步到 `Xposed-Modules-Repo/io.github.Jackyhu32.wechat.picker` |
| 6 | 在 Xposed 倉庫建立 Release `{versionCode}-{versionName}` |

發佈後的連結：

- 主倉庫：`https://github.com/Jackyhu32/wechat-google-photos-picker/releases/tag/v{versionName}`
- Xposed：`https://github.com/Xposed-Modules-Repo/io.github.Jackyhu32.wechat.picker/releases/tag/{versionCode}-{versionName}`

## 規則與注意事項

- **Xposed 的 tag 格式是硬性規定**：必須是 `VersionCode-VersionName`（例：`8-1.3.4`）。格式錯誤的 release 會被 XposedBot 判定無效，模組不會出現在 LSPosed 網站或 Vector。
- **不要手動建立 tag 來觸發**會有額外驗證：tag 必須等於 `v{versionName}`，不符會直接失敗。一般情況直接 push `main` 就好。
- **不要用 API 修改 Xposed 倉庫的 description**。maintain 角色無 admin 權限，`PATCH /repos/...` 一律回 404，只能在 GitHub 網頁的 About 區塊編輯。目前內容為：
  `微信启用Google相册相片 (WeChat Google Photos Picker)`
- **只替換 asset 不會觸發 XposedBot**。若真的需要重發同一版，要同時修改 release 的說明內容。
- **不要使用 `dist/WeChatSysPicker-debug.apk`**，那是舊產物（v1.3.0）。CI 只認 `app/build/outputs/apk/debug/app-debug.apk`。
- 若只是改文件、改註解，`app/build.gradle` 沒變，就不會觸發發佈（workflow 有 path 過濾）。

## 手動觸發（例外情況）

版本號沒變但要重跑時，請有權限的人執行：

```bash
gh workflow run Release --repo Jackyhu32/wechat-google-photos-picker --ref main
```

## 需要的設定（僅一次性，已設定完成）

`XPOSED_PAT` repository secret：一組 **classic** PAT，需具備 `repo` 與 `workflow` scope。

- 必須是 classic，fine-grained PAT 無法存取 `Xposed-Modules-Repo` 組織的倉庫。
- `workflow` scope 是必要的，因為同步 `main` 時會推送 `.github/workflows/` 下的檔案。
- 建議設定到期日並定期更換。
