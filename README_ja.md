# 🏃 Minecraft Manhunt Latest

[![Modrinth Downloads](https://img.shields.io/modrinth/dt/man-hunt-latest?label=ダウンロード数&logo=modrinth)](https://modrinth.com/plugin/man-hunt-latest)
[![GitHub Release](https://img.shields.io/github/v/release/yourusername/minecraft_manhunt_latest?label=最新リリース)](https://github.com/yourusername/minecraft_manhunt_latest/releases)
[![Minecraft Version](https://img.shields.io/badge/Minecraft-1.21.4-green.svg)](https://www.minecraft.net/)
[![Platform](https://img.shields.io/badge/プラットフォーム-Spigot%20%7C%20Paper-orange.svg)](https://papermc.io/)

高度な追跡システム、経済システム統合、完全な国際化対応を備えた包括的なマルチプレイヤーManhuntプラグインです。

## ✨ 機能

### 🎯 コアとなるManhuntゲームプレイ
- **動的な役割システム**: プレイヤーはハンター、ランナー、観戦者になれます
- **高度なコンパス追跡**: 物理コンパスベースの追跡システム（ターゲット切り替え対応）
- **近接警告システム**: ハンターが接近した際のランナー向け段階的警告
- **自動ゲーム管理**: 再接続対応を含むスマートなゲーム状態管理

### 💰 経済・ショップシステム
- **ゲーム内通貨**: ゲームプレイを通じてコインを獲得
- **包括的なショップ**: 6カテゴリー40種類以上のアイテム（武器、防具、ツール、消耗品、食料、特殊）
- **役割別報酬**: ハンターとランナーで異なる獲得方法
- **購入制限**: アイテム制限、クールダウン、役割別制限

### 🌍 完全な国際化対応
- **多言語サポート**: 完全な英語・日本語ローカライゼーション
- **プレイヤー別言語設定**: 個人の言語設定が可能
- **ローカライズされたショップアイテム**: 全アイテムと説明が完全翻訳済み
- **動的UI**: 全インターフェースがプレイヤーの言語設定に適応

### 📊 高度な統計・結果表示
- **詳細なプレイヤー統計**: パフォーマンス、収入、実績を追跡
- **MVPシステム**: トッププレイヤーの自動認識
- **包括的なゲーム結果**: 視覚効果を伴う多段階結果表示
- **チームランキング**: パフォーマンスベースのチーム・個人ランキング

### 🎮 強化されたユーザー体験
- **役割選択GUI**: 直感的なグラフィカル役割選択メニュー
- **観戦者メニュー**: 観戦者向けの簡単なテレポートシステム
- **チームコミュニケーション**: プライベートチームチャットと座標共有
- **バディーシステム**: チームメイトとのペア機能で連携を強化
- **リアルタイムUI**: 動的なスコアボード、アクションバー、ボスバー表示

## 🎯 ゲーム目標

- **🏃 ランナー**: エンダードラゴンを倒して勝利を達成
- **🗡 ハンター**: ランナーが目標を達成する前に全員を排除
- **👁 観戦者**: 完全な移動性とテレポート機能でゲームを観戦

## 🛠 インストール

1. [Modrinth](https://modrinth.com/plugin/man-hunt-latest)から最新リリースをダウンロード
2. JARファイルをサーバーの`plugins`フォルダに配置
3. サーバーを再起動
4. `config.yml`を編集後、`/manhunt reload`で設定を適用

## 📋 必要要件

- **Minecraftバージョン**: 1.21.4
- **サーバーソフトウェア**: SpigotまたはPaper
- **Javaバージョン**: 21以上
- **最小プレイヤー数**: 2人（設定可能）

## 🎮 クイックスタート

1. サーバーに参加して `/manhunt join` を実行
2. `/manhunt roles` で役割を選択（GUIメニュー）
3. 最小プレイヤー数が揃うと自動的にゲーム開始
4. `/manhunt help` で利用可能な全コマンドを確認

## ⚙️ 設定

`config.yml`で高度にカスタマイズ可能：

```yaml
# 言語設定
language:
  default: "ja"                    # デフォルト言語 (ja/en)
  per-player: true                 # プレイヤー別言語設定

# ゲーム設定
game:
  min-players: 2                   # 開始に必要な最小人数
  start-countdown: 10              # ゲーム開始前のカウントダウン

# 経済設定
economy:
  starting-balance: 0              # 開始時の所持金 (0G)
  currency-unit: "G"               # 通貨記号
  hunter:
    damage-reward: 5               # ダメージごとの報酬
    kill-reward: 150               # キルごとの報酬
  runner:
    survival-bonus: 0.05           # 秒あたりの生存ボーナス
    nether-reward: 200             # ネザー到達報酬
```

## 🎯 コマンド

### プレイヤーコマンド
- `/manhunt role <runner|hunter|spectator>` - 役割変更（待機中のみ）
- `/manhunt roles` - 役割選択GUIを開く
- `/manhunt status` - ゲーム状況を確認
- `/manhunt compass` - 追跡コンパスを取得（ハンターのみ）
- `/manhunt spectate` - 観戦メニューを開く（観戦者のみ）
- `/r <メッセージ>` - チームチャット
- `/pos` - チームに座標を共有
- `/shop` - ショップメニューを開く
- `/shop balance` - 残高確認
- `/buddy <サブコマンド>` - バディーシステムコマンド（ゲーム中のみ）

### 管理者コマンド
- `/manhunt start` - ゲームを強制開始
- `/manhunt stop` または `/manhunt end` - ゲームを強制終了
- `/manhunt sethunter <プレイヤー>` - ハンター役を割り当て
- `/manhunt setrunner <プレイヤー>` - ランナー役を割り当て
- `/manhunt setspectator <プレイヤー>` - 観戦者役を割り当て
- `/manhunt minplayers <数>` - 最小プレイヤー数を設定
- `/manhunt reload [config|shop|all]` - 設定をリロード
- `/manhunt ui <toggle|status>` - UI表示を制御
- `/manhunt respawntime <プレイヤー> <秒>` - カスタムリスポン時間を設定
- `/manhunt reset` - ゲーム強制リセット（ゲーム終了後のみ）
- `/manhunt give <プレイヤー> <金額>` - プレイヤーにお金を付与
- `/manhunt validate-messages` - メッセージファイルの整合性チェック
- `/manhunt diagnose` - 診断情報を出力

## 🏆 経済システム

### 獲得方法

**🗡 ハンター:**
- ランナーへのダメージごとに5G
- ランナー撃破ごとに150G
- 30秒ごとに1.5G（時間ボーナス）

**🏃 ランナー:**
- 30秒ごとに1.5G（生存ボーナス）
- ネザー到達で200G
- 要塞発見で300G
- エンド到達で500G
- ダイヤモンド収集ごとに25G
- 逃走成功で20G

### ショップカテゴリー
- **⚔️ 武器**: 剣、斧、トライデント
- **🛡️ 防具**: 各種素材のフル装備セット
- **🔧 ツール**: つるはし、シャベル、バケツ
- **🧪 消耗品**: ポーション、金のリンゴ
- **🍖 食料**: 各種食べ物
- **✨ 特殊**: エリトラ、エンダーパール、エメラルド

## 🌟 高度な機能

### スポーン配置システム
- **動的配置範囲**: プレイヤー総数に応じて自動調整
  - 2人: 最大500m
  - 20人: 最大2000m
  - 20人以上: 20人ごとに500m追加
- **敵対距離保証**: 敵チーム間の最小距離を確保（デフォルト: 500m）
- **チーム人数比による調整**:
  - 少数派チーム: 近くに集まって配置
  - 多数派チーム: 人数比に応じて分散配置
- **安全な配置**: 危険な場所を避けて地上に配置

### 近接警告システム
- **赤色警告**: ハンターが1チャンク以内
- **橙色警告**: ハンターが2チャンク以内
- **黄色警告**: ハンターが3チャンク以内

### コンパス追跡システム
- 追跡には物理コンパスが必要
- 右クリックでターゲット切り替え
- 距離と方向の表示
- 異次元間追跡対応

### チーム連携
- `/r <メッセージ>` でプライベートチームチャット
- `/pos` で座標共有
- Tabリストでチームメイトの位置をリアルタイム表示
- 強化された連携のためのバディーシステム

### バディーシステム
- 1人のチームメイトとパートナー関係を構築
- スコアボードにバディーの位置をリアルタイム表示
- プレイヤーリストでオレンジ色のハイライト表示
- コマンド: `/buddy invite`, `accept`, `decline`, `remove`, `status`
- バディー関係には相互の同意が必要
- ゲーム終了時やプレイヤー切断時に自動解除

## 🔧 技術詳細

- **言語**: Kotlin 1.9.24
- **ビルドツール**: Gradle 8.8
- **ターゲットJVM**: Java 21
- **API**: Spigot/Paper 1.21.4
- **アーキテクチャ**: 包括的なマネージャークラスを持つイベント駆動型

## 📝 更新履歴

詳細なバージョン履歴は[CHANGELOG.md](CHANGELOG.md)をご覧ください。

## 🤝 貢献

以下の形での貢献を歓迎します：
- GitHub Issuesでのバグ報告
- 新機能の提案
- プルリクエストの送信
- 翻訳への協力

## 📄 ライセンス

このプロジェクトはMITライセンスの下でライセンスされています - 詳細は[LICENSE](LICENSE)ファイルをご覧ください。

## 🔗 リンク

- [Modrinthページ](https://modrinth.com/plugin/man-hunt-latest)
- [GitHubリポジトリ](https://github.com/yourusername/minecraft_manhunt_latest)
- [Issue Tracker](https://github.com/yourusername/minecraft_manhunt_latest/issues)

---

**🎮 Manhuntの冒険を始める準備はできましたか？今すぐダウンロードして究極の追跡ゲームを体験しよう！**