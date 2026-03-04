# AutoShorts

YouTube Shorts を自動的に次の動画へ進める Android アプリ。

## セットアップ手順

### 1. Android Studio でビルド
1. Android Studio でこのフォルダを開く
2. `Build > Make Project` でビルド
3. `app-debug.apk` を生成 (`app/build/outputs/apk/debug/`)

### またはコマンドラインビルド（Windows）
```
gradlew.bat assembleDebug
```

### 2. APK のインストール
```
adb install app/build/outputs/apk/debug/app-debug.apk
```

---

## 使い方

1. **アプリを起動**
2. **STEP 1**: 「オーバーレイ許可を開く」→ AutoShorts を ON
3. **STEP 2**: 「アクセシビリティ設定を開く」→ AutoShorts → ON
4. **STEP 3**: タイマー秒数をスライダーで設定（デフォルト60秒）
5. **「オーバーレイボタンを表示」** をタップ
6. YouTube を開くと画面上に **[AUTO]** ボタンが表示される
7. **[AUTO]** をタップ → **[AUTO ON]**（緑）になり、設定秒数後に自動スワイプ開始

---

## ファイル構成

```
app/src/main/java/com/autoshorts/
├── MainActivity.kt       # パーミッション要求・設定画面
├── OverlayService.kt     # フローティングオーバーレイボタン
├── AutoSwipeService.kt   # アクセシビリティサービス（スワイプ実行）
└── SettingsManager.kt    # タイマー設定の保存
```

---

## 注意事項
- **アクセシビリティサービス**は毎回手動で有効化が必要（Android 仕様）
- **オーバーレイ許可**は初回のみ手動設定が必要
- YouTube の UI 変更でスワイプ座標がずれる場合は `AutoSwipeService.kt` の座標比率を調整
- minSdk = 26 (Android 8.0 以上)
